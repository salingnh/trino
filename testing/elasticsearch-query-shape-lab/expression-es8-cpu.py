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

"""ES8-only CPU follow-up using uncached cgroup v2 usage_usec, not node OS cache."""
from datetime import datetime, timezone
import gzip
import importlib.util
import json
from pathlib import Path
import subprocess

ROOT = Path('/opt/trino-expression-comparison-a2af4b7203e')
SPEC = importlib.util.spec_from_file_location('diagnostic', ROOT / 'expression-es8-profile.py')
D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D)
P, C = D.P, D.C
P.BENCHMARK_PREFIX = 'expr_es8_cpu_20261118_'
OUTPUT = ROOT / 'es8-diagnostics-20261118'


def main():
    if (OUTPUT / 'cpu-complete.json').exists():
        raise RuntimeError('CPU pass already completed; preserve evidence')
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'trino-harbor-verification-es8-1'], text=True))[0]
    pid = container['State']['Pid']
    entry = next(line.split('::', 1)[1] for line in Path(f'/proc/{pid}/cgroup').read_text().splitlines() if line.startswith('0::'))
    counter_file = Path('/sys/fs/cgroup') / entry.lstrip('/') / 'cpu.stat'

    def cpu():
        return {key: int(value) for key, value in (line.split() for line in counter_file.read_text().splitlines())}

    original_query = C.query

    def counted_query(*args, **kwargs):
        start = cpu()
        value = original_query(*args, **kwargs)
        end = cpu()
        value.update(es_container_cpu_ms=(end['usage_usec'] - start['usage_usec']) / 1000,
                     cgroup_before=start, cgroup_after=end)
        return value

    C.query = counted_query
    with gzip.open(ROOT / 'expression-es8-audit-input.json.gz', 'rt') as stream:
        packet = json.load(stream)
    versions = ('master', 'internal', 'current')
    info, sessions = C.wait_and_sessions(versions, ('es8',))
    active = [P.index_name(8, 100000, table) for table in packet['templates']]
    existing = C.http(D.ES + '/_cat/indices?format=json&h=index')
    assert not any(value['index'].startswith(P.BENCHMARK_PREFIX) for value in existing)
    records = []
    try:
        P.seed('es8', 8, 100000, packet['templates'])
        items = [item for item in packet['features'] if item['feature'] in D.FOCUS]
        references = P.oracle('es8', items)
        with (OUTPUT / 'cpu-sql-results.jsonl').open('w') as stream:
            for item in items:
                sql = item['sql'].replace(C.PREFIX + '_' + item['table'], P.index_name(8, 100000, item['table']))
                settings = {version: P.settings_for(version, 'es8', item, sessions) for version in versions}
                plans = {}
                for version in versions:
                    result = C.query(version, 'EXPLAIN (TYPE DISTRIBUTED) ' + sql, 'es8', settings[version])
                    assert not result.get('error'), result
                    plans[version] = '\n'.join(row[0] for row in result['rows'])
                for repetition in range(4):
                    shift = (item['feature'] + repetition) % 3
                    order = versions[shift:] + versions[:shift]
                    for version in order:
                        record = P.measure(version, 'es8', item, sql, settings[version], plans[version], references[item['id']],
                                           len(packet['templates'][item['table']]['values']), 100000)
                        assert record['correct'], (item['id'], version, record['actual'])
                        record.update(repetition=repetition, warmup=repetition == 0, order=order)
                        stream.write(json.dumps(record, ensure_ascii=False) + '\n')
                        stream.flush()
                        records.append(record)
                print('CPU', item['feature'], item['id'], 'correct', flush=True)
    finally:
        for name in active:
            P.delete_index('es8', name)
    current = json.loads(subprocess.check_output(['docker', 'inspect', 'trino-harbor-verification-es8-1'], text=True))[0]
    assert current['State']['Pid'] == pid and current['Image'] == container['Image']
    assert not any(value['index'].startswith(P.BENCHMARK_PREFIX)
                   for value in C.http(D.ES + '/_cat/indices?format=json&h=index'))
    (OUTPUT / 'cpu-complete.json').write_text(json.dumps({'finished': datetime.now(timezone.utc).isoformat(),
        'SQL_executions': len(records), 'all_correct': True, 'backend': 'es8', 'counter_file': str(counter_file),
        'method': 'ES8 container cgroup usage_usec snapshots directly around C.query; includes all ES8 container work in interval',
        'container_pid': pid, 'image': container['Image'], 'server_info': info}, indent=2))
    print('CPU COMPLETE', len(records), 'SQL; ES8 only', flush=True)


if __name__ == '__main__':
    main()
