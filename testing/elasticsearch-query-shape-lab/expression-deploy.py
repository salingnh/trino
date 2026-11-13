#!/usr/bin/env python3
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Deploy isolated comparison containers using verified, explicitly staged artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
from urllib.request import urlopen
import zipfile

ROOT = Path('/opt/trino-expression-comparison-a2af4b7203e')
BASE = 'registry.icomm.vn/fusioncenter/trino:485-internal-9abc69bc79a-amd64'
BASE_ID = 'sha256:1f460d8da7efdd0cc88fe7c2829ede8126c4c61fcf9dff26faffe7da248dcc8a'
NETWORK = 'trino-harbor-verification_default'
VERSIONS = {
    'master': {'revision': '2162c006a7171d90305e21d1b2dd2fd47ef72b2a', 'port': 18091, 'proxy': 9200},
    'internal': {'revision': '9abc69bc79ad0de1563031d7d5dd533b9212b3b6', 'port': 18092, 'proxy': 9201},
    'current': {'revision': 'ffcf7b85a3b187b869cac8d8f7a56ec531b7630a', 'port': 18093, 'proxy': 9202},
}


def command(*args, capture=False):
    return subprocess.check_output(args, text=True).strip() if capture else subprocess.check_call(args)


def exists(name):
    return subprocess.run(['docker', 'container', 'inspect', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def create_container(name, image, args, command_args=None):
    if exists(name):
        desired = command('docker', 'image', 'inspect', image, '--format', '{{.Id}}', capture=True)
        running = command('docker', 'inspect', name, '--format', '{{.Image}}', capture=True)
        if desired != running:
            owner = command('docker', 'inspect', name, '--format', '{{index .Config.Labels "codex.lab"}}', capture=True)
            assert owner == 'trino-expression-comparison', 'Refusing to replace an unowned container'
            command('docker', 'stop', name)
            command('docker', 'rm', name)
        else:
            if command('docker', 'inspect', name, '--format', '{{.State.Running}}', capture=True) != 'true':
                command('docker', 'start', name)
            return
    command('docker', 'run', '-d', '--name', name, '--network', NETWORK, '--label', 'codex.lab=trino-expression-comparison', *args, image, *(command_args or []))


def build(variant):
    details = VERSIONS[variant]
    if variant == 'internal':
        return BASE, {}
    directory = ROOT / 'artifacts' / variant
    main = directory / ('trino-main.jar' if variant == 'master' else 'trino-main-485-SNAPSHOT.jar')
    plugin = directory / ('plugin.zip' if variant == 'master' else 'trino-elasticsearch-485-SNAPSHOT.zip')
    unpacked = directory / 'plugin'
    unpacked.mkdir(exist_ok=True)
    hashes = {'main': hashlib.sha256(main.read_bytes()).hexdigest(), 'plugin_archive': hashlib.sha256(plugin.read_bytes()).hexdigest()}
    with zipfile.ZipFile(plugin) as archive:
        for member in archive.infolist():
            relative = Path(*Path(member.filename).parts[1:])
            if not str(relative) or member.is_dir():
                continue
            assert not relative.is_absolute() and '..' not in relative.parts
            target = unpacked / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(member))
    jars = list(unpacked.glob('trino-elasticsearch-485-SNAPSHOT.jar'))
    assert len(jars) == 1
    hashes['connector'] = hashlib.sha256(jars[0].read_bytes()).hexdigest()
    hashes['launcher'] = hashlib.sha256((directory / 'server-main.jar').read_bytes()).hexdigest()
    with zipfile.ZipFile(directory / 'server-main.jar') as archive:
        hashes['launcher_manifest'] = archive.read('META-INF/MANIFEST.MF').decode()
    with zipfile.ZipFile(main) as archive:
        hashes['main_manifest'] = archive.read('META-INF/MANIFEST.MF').decode()
    with zipfile.ZipFile(jars[0]) as archive:
        names = archive.namelist()
        hashes['connector_manifest'] = archive.read('META-INF/MANIFEST.MF').decode()
        if variant == 'current':
            assert any(name.endswith('/ElasticsearchScalarPredicateTranslator.class') for name in names)
        else:
            assert not any(name.endswith('/ElasticsearchArrayPredicateTranslator.class') for name in names)
    lines = [f'FROM {BASE}', 'USER root', 'RUN rm -rf /usr/lib/trino/plugin/elasticsearch',
             'COPY --chown=trino:trino plugin/ /usr/lib/trino/plugin/elasticsearch/',
             'COPY --chown=trino:trino server-main.jar /usr/lib/trino/lib/io.trino_trino-server-main-485-SNAPSHOT.jar',
             f'COPY --chown=trino:trino {main.name} /usr/lib/trino/lib/io.trino_trino-main-485-SNAPSHOT.jar']
    if variant == 'current':
        spi = directory / 'trino-spi-485-SNAPSHOT.jar'
        hashes['spi'] = hashlib.sha256(spi.read_bytes()).hexdigest()
        lines.append(f'COPY --chown=trino:trino {spi.name} /usr/lib/trino/lib/io.trino_trino-spi-485-SNAPSHOT.jar')
    lines += [f'LABEL org.opencontainers.image.revision="{details["revision"]}"',
              f'LABEL comparison.variant="{variant}"', 'USER trino:trino']
    (directory / 'Dockerfile').write_text('\n'.join(lines) + '\n')
    (directory / '.dockerignore').write_text('*.zip\nSHA256SUMS\nSOURCE_REVISION\n')
    image = f'trino-expression-compare:{variant}-{details["revision"][:11]}'
    command('docker', 'build', '--pull=false', '-t', image, str(directory))
    return image, hashes


def configure(variant, es7):
    details = VERSIONS[variant]
    etc = ROOT / 'etc' / variant
    catalogs = etc / 'catalog'
    catalogs.mkdir(parents=True, exist_ok=True)
    (etc / 'node.properties').write_text(f'node.environment=expression_comparison\nnode.id=expr-{variant}-{details["revision"][:11]}\nnode.data-dir=/data/trino\n')
    (etc / 'jvm.config').write_text('-server\n-Xmx4G\n-XX:+UseG1GC\n-XX:+ExitOnOutOfMemoryError\n-XX:+EnableDynamicAgentLoading\n')
    (etc / 'config.properties').write_text('coordinator=true\nnode-scheduler.include-coordinator=true\nhttp-server.http.port=8080\ndiscovery.uri=http://localhost:8080\nquery.max-memory=2GB\nquery.max-memory-per-node=2GB\nmemory.heap-headroom-per-node=1GB\nquery.max-history=2000\nquery.min-expire-age=30m\n')
    (etc / 'log.properties').write_text('io.trino=INFO\n')
    for backend, offset in [('es8', 0)] + ([('es7', 10)] if es7 else []):
        text = f'connector.name=elasticsearch\nelasticsearch.host=trino-expr-proxy\nelasticsearch.port={details["proxy"] + offset}\nelasticsearch.default-schema-name=default\nelasticsearch.ignore-publish-address=true\nelasticsearch.scroll-size=1000\n'
        if variant != 'master':
            text += 'elasticsearch.statistics.enabled=false\nelasticsearch.search-strategy=SCROLL\n'
        if variant == 'current':
            text += 'elasticsearch.array-script-pushdown.enabled=true\nelasticsearch.scalar-script-pushdown.enabled=true\nelasticsearch.full-text-pushdown=SAFE\n'
        (catalogs / f'{backend}_compare.properties').write_text(text)
    return etc


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--variants', default='master,internal,current')
    parser.add_argument('--es7', action='store_true')
    args = parser.parse_args()
    assert command('docker', 'image', 'inspect', BASE, '--format', '{{.Id}}', capture=True) == BASE_ID
    ROOT.mkdir(exist_ok=True)
    if args.es7:
        create_container('trino-expr-es7', 'elasticsearch:7.17.27', ['--memory', '3g', '--cpus', '2',
                         '-p', '127.0.0.1:19215:9200', '-e', 'discovery.type=single-node', '-e', 'xpack.security.enabled=false',
                         '-e', 'node.store.allow_mmap=false', '-e', 'ES_JAVA_OPTS=-Xms1g -Xmx1g'])
    create_container('trino-expr-proxy', 'python:3.13-slim', ['--memory', '256m', '-p', '127.0.0.1:19214:9200',
                     '-v', str(ROOT / 'expression-proxy.py') + ':/proxy.py:ro', '--entrypoint', 'python'], ['-u', '/proxy.py'])
    manifest_path = ROOT / 'deployment.json'
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {'base_image': BASE, 'base_id': BASE_ID, 'versions': {}}
    for variant in args.variants.split(','):
        image, hashes = build(variant)
        etc = configure(variant, args.es7)
        name = 'trino-expr-' + variant
        details = VERSIONS[variant]
        create_container(name, image, ['--memory', '7g', '--cpus', '2', '-p', f'127.0.0.1:{details["port"]}:8080', '-v', str(etc) + ':/etc/trino:ro'])
        manifest['versions'][variant] = {**details, 'image': image, 'image_id': command('docker', 'image', 'inspect', image, '--format', '{{.Id}}', capture=True), 'container': name, 'hashes': hashes}
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(json.dumps(manifest, ensure_ascii=False, indent=2), flush=True)


if __name__ == '__main__':
    main()
