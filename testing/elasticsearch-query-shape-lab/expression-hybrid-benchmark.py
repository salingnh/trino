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

"""ES8 hybrid ablation on the SAME current Trino; restores the owned lab proxy."""
import argparse
from copy import deepcopy
from datetime import datetime, timezone
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import signal
import subprocess
import time

ROOT = Path('/opt/trino-expression-comparison-a2af4b7203e')
SPEC = importlib.util.spec_from_file_location('performance', ROOT / 'expression-performance.py')
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)
C = P.C
P.BENCHMARK_PREFIX = 'expr_es8_hybrid_20261118_'
ES = 'http://127.0.0.1:19211'
CONTROL = 'http://127.0.0.1:19214/__compare/hybrid-ablation'
MODES = ('hybrid', 'script', 'local')
FOCUS = (13, 14, 30, 32)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--smoke', action='store_true')
    args = parser.parse_args()
    output = ROOT / ('es8-hybrid-smoke-20261118' if args.smoke else 'es8-hybrid-20261118')
    output.mkdir(exist_ok=False)
    configs = [(1, 180)] if args.smoke else P.CONFIGURATIONS
    repeats = 1 if args.smoke else 4
    with gzip.open(ROOT / 'expression-es8-audit-input.json.gz', 'rt') as stream:
        packet = json.load(stream)
    queries = json.loads((ROOT / 'expression-hybrid-scripts.json').read_text())
    items = [item for item in packet['features'] if item['feature'] in FOCUS]
    containers = json.loads(subprocess.check_output(['docker', 'inspect', 'trino-expr-proxy', 'trino-harbor-verification-es8-1', 'trino-expr-current'], text=True))
    assert containers[0]['Config']['Labels']['codex.lab'] == 'trino-expression-comparison'
    assert 'current-e10be84' in containers[2]['Config']['Image']
    pid = containers[1]['State']['Pid']
    entry = next(line.split('::', 1)[1] for line in Path(f'/proc/{pid}/cgroup').read_text().splitlines() if line.startswith('0::'))
    cpu_path = Path('/sys/fs/cgroup') / entry.lstrip('/') / 'cpu.stat'
    def cpu():
        return {k: int(v) for k, v in (line.split() for line in cpu_path.read_text().splitlines())}
    original_query = C.query
    def counted_query(*a, **kw):
        start = cpu()
        value = original_query(*a, **kw)
        end = cpu()
        value.update(es_container_cpu_ms=(end['usage_usec'] - start['usage_usec']) / 1000,
                     cgroup_before=start, cgroup_after=end)
        return value
    C.query = counted_query
    assert not any(x['index'].startswith(P.BENCHMARK_PREFIX) for x in C.http(ES + '/_cat/indices?format=json&h=index'))
    fixture_before = P.templates('es8')
    assert fixture_before == packet['templates'], 'Original boundary fixtures changed'
    before = P.snapshot(('es8',))
    info, sessions = C.wait_and_sessions(('current',), ('es8',))
    references = P.oracle('es8', items)
    proxy_path = ROOT / 'expression-proxy.py'
    original = proxy_path.read_bytes()
    assert hashlib.sha256(original).hexdigest() == 'a1af4ec54a33b679614c005e0c14e7f44f0211e11ff1838c8b00945465d282c3'
    (output / 'proxy-original.py').write_bytes(original)
    (output / 'metadata.json').write_text(json.dumps({'started': datetime.now(timezone.utc).isoformat(), 'configs': configs,
        'repetitions_including_warmup': repeats, 'modes': MODES, 'features': items, 'references': references,
        'server_info': info, 'before': before, 'containers': containers, 'cpu_path': str(cpu_path),
        'original_proxy_sha256': hashlib.sha256(original).hexdigest()}, ensure_ascii=False, indent=2))
    for filename in ('expression-hybrid-benchmark.py', 'expression-hybrid-proxy.py', 'expression-hybrid-scripts.json',
                     'expression-performance.py', 'expression-compare.py', 'HybridScriptExporter.java.txt'):
        (output / filename).write_bytes((ROOT / filename).read_bytes())
    def ready():
        deadline = time.monotonic() + 30
        while True:
            try:
                return C.http('http://127.0.0.1:19214/__compare/control?variant=current&backend=es8')
            except Exception:
                if time.monotonic() > deadline:
                    raise
                time.sleep(.25)
    def interrupted(signum, frame):
        raise RuntimeError('Interrupted: ' + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    active = []
    records = []
    profiles = []
    try:
        # In-place write retains the inode used by Docker's file bind mount.
        proxy_path.write_bytes((ROOT / 'expression-hybrid-proxy.py').read_bytes())
        subprocess.run(['docker', 'restart', 'trino-expr-proxy'], check=True, stdout=subprocess.DEVNULL)
        ready()
        with (output / 'sql-results.jsonl').open('w') as stream:
            for shards, documents in configs:
                name = P.index_name(shards, documents, 'cases')
                active = [name]
                seeded = P.seed('es8', shards, documents, {'cases': packet['templates']['cases']})
                (output / f'index-s{shards}-n{documents}.json').write_text(json.dumps(seeded))
                for item in items:
                    sql = item['sql'].replace(C.PREFIX + '_cases', name)
                    enabled = P.settings_for('current', 'es8', item, sessions)
                    settings = {'hybrid': enabled, 'script': enabled,
                                'local': dict(enabled, allow_pushdown_into_connectors='false')}
                    C.http(CONTROL, {}, 'POST')
                    plans = {}
                    for mode in ('hybrid', 'local'):
                        answer = C.query('current', 'EXPLAIN (TYPE DISTRIBUTED) ' + sql, 'es8', settings[mode])
                        assert not answer.get('error'), answer
                        plans[mode] = '\n'.join(row[0] for row in answer['rows'])
                    plans['script'] = plans['hybrid']  # Wire-only ablation preserves planner/residual.
                    candidates = {}
                    pending_profiles = []
                    for repetition in range(repeats):
                        shift = (item['feature'] + repetition) % 3
                        order = MODES[shift:] + MODES[:shift]
                        for mode in order:
                            setting = {'index': name, 'mode': mode}
                            if mode == 'script':
                                setting['query'] = {'bool': {'filter': [queries[str(item['feature'])]]}}
                            C.http(CONTROL, setting, 'POST')
                            record = P.measure('current', 'es8', item, sql, settings[mode], plans[mode],
                                               references[item['id']], len(packet['templates']['cases']['values']), documents)
                            assert record['correct'], (item['id'], mode, record)
                            source_ids = []
                            for event in record['events']:
                                source_ids.extend(event.pop('source_ids', []))
                            assert all(isinstance(value, int) for value in source_ids), source_ids[:20]
                            candidate = P.row_summary([[value] for value in source_ids])
                            if mode != 'local':
                                candidates.setdefault(mode, candidate)
                                assert candidates[mode] == candidate
                            searches = [e for e in record['events'] if e['path'].split('?')[0] == '/' + name + '/_search']
                            assert len(searches) == shards, (len(searches), shards)
                            rewritten = sum(bool(e.get('ablation_rewritten')) for e in record['events'])
                            assert rewritten == (shards if mode == 'script' else 0), rewritten
                            if mode == 'hybrid':
                                assert record['script'] and record['native'], record['classification']
                            elif mode == 'script':
                                assert record['script'] and not record['native'], record['classification']
                            else:
                                assert not record['script'] and not record['native'] and record['source_hits'] == documents
                            record.update(execution_mode=mode, shards=shards, documents=documents,
                                          repetition=repetition, warmup=repetition == 0, order=order,
                                          candidate_summary=candidate, plan=plans[mode])
                            stream.write(json.dumps(record, ensure_ascii=False) + '\n')
                            stream.flush()
                            records.append(record)
                            if (not args.smoke and documents == 100000 and shards in (1, 8)
                                    and repetition == repeats - 1 and mode != 'local'):
                                body = {'size': 0, 'track_total_hits': True, 'profile': True,
                                        'query': searches[0]['request']['query']}
                                pending_profiles.append({'feature': item['feature'], 'mode': mode, 'shards': shards,
                                                         'documents': documents, 'request': body})
                    assert candidates['hybrid'] == candidates['script'], candidates
                    # Profiles run after ALL timed modes/repetitions for this case.
                    for profile in pending_profiles:
                        result = C.http(ES + '/' + name + '/_search?request_cache=false', profile['request'], 'POST')
                        assert not result.get('error'), result
                        profile['response'] = result
                        profiles.append(profile)
                    print('CORRECT', shards, documents, item['feature'], item['id'],
                          'candidates', candidates['hybrid']['rows'], flush=True)
                C.http(CONTROL, {}, 'POST')
                P.delete_index('es8', name)
                active = []
    finally:
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        try:
            for name in active:
                P.delete_index('es8', name)
        finally:
            proxy_path.write_bytes(original)
            subprocess.run(['docker', 'restart', 'trino-expr-proxy'], check=True, stdout=subprocess.DEVNULL)
            ready()
            assert proxy_path.read_bytes() == original
            (output / 'restored.json').write_text(json.dumps({'proxy_restored': True, 'time': datetime.now(timezone.utc).isoformat()}))
    assert not any(x['index'].startswith(P.BENCHMARK_PREFIX) for x in C.http(ES + '/_cat/indices?format=json&h=index'))
    fixture_after = P.templates('es8')
    assert fixture_after == fixture_before, 'Original boundary fixtures changed during benchmark'
    after = P.snapshot(('es8',))
    for old in before['containers']:
        current = next(x for x in after['containers'] if x['name'] == old['name'])
        assert old['image'] == current['image'] and old['cpus'] == current['cpus'] and old['memory'] == current['memory']
        if old['name'] != '/trino-expr-proxy':
            assert old['state']['Pid'] == current['state']['Pid']
    (output / 'profiles.json').write_text(json.dumps(profiles))
    (output / 'complete.json').write_text(json.dumps({'finished': datetime.now(timezone.utc).isoformat(),
        'SQL_executions': len(records), 'all_correct': True, 'candidate_sets_equal': True, 'backend': 'es8',
        'profiles': len(profiles), 'original_fixtures_unchanged': True, 'proxy_restored': True, 'temporary_indices_removed': True, 'after': after}, indent=2))
    print('COMPLETE', len(records), 'SQL', len(profiles), 'profiles; proxy restored; ES8 only', flush=True)


if __name__ == '__main__':
    main()
