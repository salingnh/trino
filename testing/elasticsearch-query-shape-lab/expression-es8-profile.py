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

"""ES8-only diagnosis: Lucene profiles, isolated SQL/node counters and a canary.

Profiles change execution cost and are never mixed into the latency benchmark.
The independent canary process avoids sharing Python's GIL with SQL JSON decoding.
"""
from copy import deepcopy
from datetime import datetime, timezone
import gzip
import importlib.util
import json
import multiprocessing
from pathlib import Path
import queue
import time

ROOT = Path('/opt/trino-expression-comparison-a2af4b7203e')
SPEC = importlib.util.spec_from_file_location('performance', ROOT / 'expression-performance.py')
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)
C = P.C
ES = 'http://127.0.0.1:19211'
PREFIX = 'expr_es8_diag_20261118_'
P.BENCHMARK_PREFIX = PREFIX
CANARY = PREFIX + 'canary'
OUTPUT = ROOT / 'es8-diagnostics-20261118'
FOCUS = (1, 2, 4, 6, 9, 10, 13, 14, 29, 32, 34, 35, 38, 39)


def save(name, value):
    (OUTPUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2))


def stats():
    response = C.http(ES + '/_nodes/stats')
    value = next(iter(response['nodes'].values()))
    return {'timestamp': value['timestamp'], 'node': value['name'],
            'process': value['process'], 'jvm': value['jvm'],
            'search': value['indices']['search'],
            'thread_pool': {key: value['thread_pool'][key] for key in ('search', 'search_coordination')},
            'script': value.get('script', {}), 'query_cache': value['indices']['query_cache']}


def canary(stop, phases):
    phase = 'setup'
    with (OUTPUT / 'canary.jsonl').open('w') as stream:
        while not stop.is_set():
            try:
                while True:
                    phase = phases.get_nowait()
            except queue.Empty:
                pass
            start_phase = phase
            started = time.monotonic()
            record = {'phase': start_phase, 'at': datetime.now(timezone.utc).isoformat()}
            try:
                value = C.http(ES + '/' + CANARY + '/_search?request_cache=false',
                               {'size': 0, 'track_total_hits': True, 'query': {'term': {'id': 1}}}, 'POST')
                record.update(elapsed_ms=(time.monotonic() - started) * 1000, took_ms=value['took'],
                              hits=value['hits']['total']['value'])
            except Exception as error:
                record.update(error=repr(error), elapsed_ms=(time.monotonic() - started) * 1000)
            try:
                while True:
                    phase = phases.get_nowait()
            except queue.Empty:
                pass
            record['boundary'] = phase != start_phase
            stream.write(json.dumps(record) + '\n')
            stream.flush()
            stop.wait(max(0, 0.25 - (time.monotonic() - started)))


def body_for(item, profile=False):
    value = {'size': 0, 'track_total_hits': True, 'query': item['body']['query']}
    if item['body'].get('runtime_mappings'):
        value['runtime_mappings'] = item['body']['runtime_mappings']
    if profile:
        value['profile'] = True
    return value


def main():
    OUTPUT.mkdir(exist_ok=True)
    if (OUTPUT / 'complete.json').exists():
        raise RuntimeError('Diagnostics already completed; preserve the original evidence')
    with gzip.open(ROOT / 'expression-es8-audit-input.json.gz', 'rt') as stream:
        packet = json.load(stream)
    assert C.http(ES)['version']['number'] == '8.11.3'
    # Read only this backend's inventory; never delete or request ES7 resources.
    existing = C.http(ES + '/_cat/indices?format=json&h=index')
    assert not any(value['index'].startswith(PREFIX) for value in existing)
    info, sessions = C.wait_and_sessions(('master', 'internal', 'current'), ('es8',))
    template = packet['templates']
    source = {value['feature']: value for value in packet['requests']}
    active = []
    context = multiprocessing.get_context('fork')
    stop = context.Event()
    phases = context.Queue()
    probe = None
    profiles = []
    results = []
    before = stats()
    save('before.json', before)
    try:
        C.http(ES + '/' + CANARY, {'settings': {'number_of_shards': 1, 'number_of_replicas': 0,
                                               'requests.cache.enable': False},
                                  'mappings': {'properties': {'id': {'type': 'integer'}}}}, 'PUT')
        C.http(ES + '/' + CANARY + '/_doc/1?refresh=true', {'id': 1}, 'PUT')
        probe = context.Process(target=canary, args=(stop, phases))
        probe.start()
        phases.put('idle_before')
        time.sleep(10)
        phases.put('profile_not_benchmark')
        active = [P.index_name(1, 100000, table) for table in template]
        fixtures = P.seed('es8', 1, 100000, template)
        save('s1-fixtures.json', fixtures)
        for feature in range(1, 41):
            item = source[feature]
            value = body_for(item, profile=True)
            answer = C.http(ES + '/' + P.index_name(1, 100000, item['table']) + '/_search?request_cache=false', value, 'POST')
            assert not answer.get('timed_out') and answer['_shards']['failed'] == 0
            assert answer['hits']['total']['value'] == item['expected_candidates'], (feature, answer['hits']['total'])
            profiles.append({'feature': feature, 'case_id': item['case_id'], 'table': item['table'],
                             'kind': item['kind'], 'request': value, 'response': answer})
            print('PROFILE', feature, item['case_id'], 'candidates', answer['hits']['total']['value'], flush=True)
        controls = [('keyword_term', {'term': {'words': 'abc'}}),
                    ('keyword_all_regex', {'regexp': {'words': '.*'}}),
                    ('match_all', {'match_all': {}})]
        for name, query in controls:
            request = {'profile': True, 'size': 0, 'track_total_hits': True, 'query': query}
            answer = C.http(ES + '/' + P.index_name(1, 100000, 'cases') + '/_search?request_cache=false', request, 'POST')
            profiles.append({'feature': None, 'case_id': name, 'kind': name, 'request': request, 'response': answer})
        vocabulary = C.http(ES + '/' + P.index_name(1, 100000, 'cases') + '/_search',
                            {'size': 0, 'aggs': {'words': {'terms': {'field': 'words', 'size': 1000}}}}, 'POST')
        save('keyword-vocabulary.json', vocabulary)
        for name in active:
            P.delete_index('es8', name)
        active = [P.index_name(8, 100000, table) for table in template]
        phases.put('seeding')
        fixtures = P.seed('es8', 8, 100000, template)
        save('s8-fixtures.json', fixtures)
        golden = P.oracle('es8', [item for item in packet['features'] if item['feature'] in FOCUS])
        with (OUTPUT / 'sql-node-results.jsonl').open('w') as stream:
            for feature in FOCUS:
                item = next(item for item in packet['features'] if item['feature'] == feature)
                sql = item['sql'].replace(C.PREFIX + '_' + item['table'], P.index_name(8, 100000, item['table']))
                settings = {version: P.settings_for(version, 'es8', item, sessions) for version in ('master', 'internal', 'current')}
                plans = {}
                phases.put('explain')
                for version in settings:
                    plan = C.query(version, 'EXPLAIN (TYPE DISTRIBUTED) ' + sql, 'es8', settings[version])
                    assert not plan.get('error'), plan
                    plans[version] = '\n'.join(row[0] for row in plan['rows'])
                for repetition in range(4):
                    versions = ('master', 'internal', 'current')
                    shift = (feature + repetition) % 3
                    order = versions[shift:] + versions[:shift]
                    for version in order:
                        phases.put(f'{version}:{feature}:' + ('warmup' if repetition == 0 else 'measure'))
                        start_stats = stats()
                        record = P.measure(version, 'es8', item, sql, settings[version], plans[version], golden[item['id']],
                                           len(template[item['table']]['values']), 100000)
                        end_stats = stats()
                        assert record['correct'], (feature, version, record['actual'])
                        record.update(before=start_stats, after=end_stats, repetition=repetition, warmup=repetition == 0,
                                      shards=8, documents=100000, order=order)
                        stream.write(json.dumps(record, ensure_ascii=False) + '\n')
                        stream.flush()
                        results.append(record)
                print('SQL', feature, item['id'], 'correct', flush=True)
        phases.put('cleanup')
        for name in active:
            P.delete_index('es8', name)
        active = []
        phases.put('idle_after')
        time.sleep(10)
    finally:
        stop.set()
        if probe:
            probe.join(timeout=5)
            if probe.is_alive():
                probe.terminate()
        for name in active:
            P.delete_index('es8', name)
        # Canary name is a separate exact owned name, never a wildcard deletion.
        try:
            C.http(ES + '/' + CANARY, method='DELETE')
        except Exception as error:
            print('CANARY CLEANUP ERROR', repr(error), flush=True)
        with gzip.open(OUTPUT / 'profiles.json.gz', 'wt') as stream:
            json.dump(profiles, stream, ensure_ascii=False)
        save('after.json', stats())
        remaining = [value['index'] for value in C.http(ES + '/_cat/indices?format=json&h=index')
                     if value['index'].startswith(PREFIX)]
        save('cleanup.json', {'remaining_owned_indices': remaining, 'cluster_settings': C.http(ES + '/_cluster/settings')})
        assert not remaining
    save('complete.json', {'finished': datetime.now(timezone.utc).isoformat(), 'backend': 'es8',
                           'profiles': len(profiles), 'SQL_executions': len(results), 'all_correct': True,
                           'server_info': info, 'no_ES7_requests': True})
    print('COMPLETE', len(profiles), 'profiles;', len(results), 'SQL executions; ES8 only', flush=True)


if __name__ == '__main__':
    main()
