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

"""Prepare only files under this isolated lab directory; never alter host settings."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
(root / 'etc/catalog').mkdir(parents=True, exist_ok=True)
(root / 'runtime/plugin').mkdir(parents=True, exist_ok=True)
for plugin in ('elasticsearch', 'jmx'):
    archive = root / f'source/plugin/trino-{plugin}/target/trino-{plugin}-484-SNAPSHOT.zip'
    destination = root / f'runtime/plugin/trino-{plugin}-484-SNAPSHOT'
    if not destination.exists():
        with ZipFile(archive) as bundle:
            bundle.extractall(root / 'runtime/plugin')
(root / 'etc/config.properties').write_text('''coordinator=true
node-scheduler.include-coordinator=true
http-server.http.port=8080
discovery.uri=http://localhost:8080
node.environment=query_shape_lab
node.id=query-shape-backport
plugin.dir=/runtime/plugin
catalog.config-dir=/etc/trino/catalog
query.max-memory=4GB
query.max-memory-per-node=2GB
memory.heap-headroom-per-node=1GB
''')
(root / 'etc/log.properties').write_text('io.trino=INFO\nio.trino.plugin.elasticsearch=INFO\n')
(root / 'etc/catalog/jmx.properties').write_text('connector.name=jmx\n')
for version in (7, 8):
    for strategy in ('scroll', 'pit'):
        name = f'es{version}_{strategy}'
        (root / f'etc/catalog/{name}.properties').write_text(f'''connector.name=elasticsearch
elasticsearch.host=es{version}
elasticsearch.port=9200
elasticsearch.default-schema-name=default
elasticsearch.ignore-publish-address=true
elasticsearch.statistics.enabled=false
elasticsearch.scroll-size=1000
elasticsearch.search-strategy={strategy.upper()}
elasticsearch.aggregation-page-size=10000
jmx.base-name=lab_{name}
''')
for size in (1, 2, 1000, 10000):
    name = f'es8_page{size}'
    text = (root / 'etc/catalog/es8_scroll.properties').read_text()
    text = text.replace('aggregation-page-size=10000', f'aggregation-page-size={size}')
    text = text.replace('lab_es8_scroll', f'lab_{name}')
    text = text.replace('elasticsearch.host=es8', 'elasticsearch.host=es_proxy')
    (root / f'etc/catalog/{name}.properties').write_text(text)
text = (root / 'etc/catalog/es8_scroll.properties').read_text()
text = text.replace('elasticsearch.host=es8', 'elasticsearch.host=es_proxy')
text = text.replace('lab_es8_scroll', 'lab_es8_proxy')
(root / 'etc/catalog/es8_proxy.properties').write_text(text)
print('Prepared isolated catalogs and runtime plugins')
