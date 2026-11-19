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

"""Sequential, correctness-checked scaling measurements of the release's 40 examples.

Run on the existing comparison VM. Only newly named benchmark indices are mutated.
The original functional comparison and its fixtures/results are never overwritten.
"""
import argparse
from collections import Counter
from copy import deepcopy
from datetime import datetime, timezone
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
from urllib.error import HTTPError

SPEC = importlib.util.spec_from_file_location('comparison', Path(__file__).with_name('expression-compare.py'))
C = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(C)
VERSIONS = ('master', 'internal', 'current')
BENCHMARK_PREFIX = 'expr_perf_20261118_'
EXPECTED_REVISIONS = {'master': '2162c00', 'internal': '9abc69b', 'current': 'e10be84'}
CONFIGURATIONS = [(1, 10000), (4, 10000), (8, 10000), (1, 50000), (1, 100000), (4, 50000), (8, 100000)]


def save(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    temporary.replace(path)


def features(report):
    cases = {case['id']: case for case in C.CASES}
    result = []
    for line in report.read_text().splitlines():
        match = re.match(r'\| (\d+)\. (.*?) \|.*?ID: `([^`]+)`', line)
        if match:
            number, title, identifier = match.groups()
            result.append(dict(deepcopy(cases[identifier]), feature=int(number), title=title))
    if [item['feature'] for item in result] != list(range(1, 41)):
        raise ValueError('The release report must contain exactly 40 numbered feature examples')
    return result


def es_url(backend):
    return f'http://127.0.0.1:{C.ES_PORTS[backend]}'


def index_name(shards, documents, table):
    return f'{BENCHMARK_PREFIX}s{shards}_n{documents}_{table}'


def assert_owned(name):
    if not re.fullmatch(re.escape(BENCHMARK_PREFIX) + r's\d+_n\d+_(cases|text)', name):
        raise ValueError('Not an owned benchmark index: ' + name)


def delete_index(backend, name):
    assert_owned(name)
    try:
        C.http(es_url(backend) + '/' + name, method='DELETE')
    except HTTPError as error:
        if error.code != 404:
            raise


def templates(backend):
    result = {}
    for table in ('cases', 'text'):
        name = C.PREFIX + '_' + table
        mapping = C.http(es_url(backend) + '/' + name + '/_mapping')[name]['mappings']
        settings = C.http(es_url(backend) + '/' + name + '/_settings')[name]['settings']['index']
        answer = C.http(es_url(backend) + '/' + name + '/_search', {'size': 100, 'sort': [{'id': 'asc'}]}, 'POST')
        values = [hit['_source'] for hit in answer['hits']['hits']]
        assert len(values) == (18 if table == 'cases' else 20)
        assert [value['id'] for value in values] == list(range(1, len(values) + 1))
        result[table] = {'mapping': mapping, 'analysis': settings.get('analysis', {}), 'values': values}
    return result


def seed(backend, shards, documents, template):
    result = {}
    for table, original in template.items():
        if shutil.disk_usage('/opt').free < 2 * 1024**3:
            raise RuntimeError('Less than 2 GiB free before seeding; preserving existing data')
        name = index_name(shards, documents, table)
        delete_index(backend, name)
        settings = {'number_of_shards': shards, 'number_of_replicas': 0, 'refresh_interval': '-1',
                    'requests.cache.enable': False, 'analysis': original['analysis']}
        C.http(es_url(backend) + '/' + name, {'settings': settings, 'mappings': original['mapping']}, 'PUT')
        for start in range(0, documents, 1000):
            lines = []
            for position in range(start, min(start + 1000, documents)):
                document = dict(original['values'][position % len(original['values'])], id=position + 1)
                lines.extend((json.dumps({'index': {'_index': name, '_id': str(position + 1)}}),
                              json.dumps(document, ensure_ascii=False, allow_nan=False)))
            answer = C.http(es_url(backend) + '/_bulk', ('\n'.join(lines) + '\n').encode(),
                            'POST', {'Content-Type': 'application/x-ndjson'})
            if answer.get('errors'):
                raise RuntimeError(str(answer)[:1000])
        C.http(es_url(backend) + '/' + name + '/_settings', {'index': {'refresh_interval': '1s'}}, 'PUT')
        C.http(es_url(backend) + '/' + name + '/_refresh', method='POST')
        C.http(es_url(backend) + '/' + name + '/_flush', method='POST')
        health = C.http(es_url(backend) + '/_cluster/health/' + name + '?wait_for_status=green&timeout=60s')
        if health.get('timed_out') or health['active_primary_shards'] != shards:
            raise RuntimeError('Shards are not ready: ' + str(health))
        count = C.http(es_url(backend) + '/' + name + '/_count')['count']
        assert count == documents, (name, count, documents)
        deadline = time.monotonic() + 60
        while True:
            stats = C.http(es_url(backend) + '/' + name + '/_stats/docs,store,merge')
            if stats['_all']['total']['merges']['current'] == 0:
                break
            if time.monotonic() > deadline:
                raise RuntimeError('Index still merging before measurement: ' + name)
            time.sleep(1)
        result[table] = {'name': name, 'documents': count, 'health': health, 'stats': stats,
                         'settings': C.http(es_url(backend) + '/' + name + '/_settings')}
    return result


def oracle(backend, items):
    result = {}
    for item in items:
        # Every release example is SELECT id or SELECT count(*). Evaluate the
        # predicate with pushdown disabled on the original small boundary fixture.
        sql = re.sub(r'^SELECT count\(\*\)', 'SELECT id', item['sql'], flags=re.I)
        answer = C.query('current', sql, backend, {'allow_pushdown_into_connectors': 'false'})
        if answer.get('error'):
            raise RuntimeError('Local reference failed: ' + str(answer))
        expected = {'sql_ids': sorted(row[0] for row in answer['rows']), 'local_reference': answer}
        if item.get('approximate_query'):
            native = C.http(es_url(backend) + '/' + C.PREFIX + '_' + item['table'] + '/_search',
                            {'query': item['approximate_query'], 'size': 100, '_source': ['id']}, 'POST')
            expected.update(analyzer_ids=sorted(hit['_source']['id'] for hit in native['hits']['hits']),
                            analyzer_query=item['approximate_query'], analyzer_response=native)
        result[item['id']] = expected
    return result


def expected_rows(eligible, template_size, documents, count):
    selected = set(eligible)
    rows = [[position + 1] for position in range(documents) if position % template_size + 1 in selected]
    return [[len(rows)]] if count else rows


def row_summary(rows):
    ordered = sorted(rows)
    return {'rows': len(rows), 'first_rows': ordered[:10],
            'sha256': hashlib.sha256(json.dumps(ordered, separators=(',', ':')).encode()).hexdigest()}


def settings_for(version, backend, item, sessions):
    settings = dict(sessions[version + ':' + backend])
    catalog = backend + '_compare.'
    if catalog + 'full_text_pushdown_mode' in settings:
        settings[catalog + 'full_text_pushdown_mode'] = item['mode']
    if item['off']:
        for name in ('array_script_pushdown_enabled', 'scalar_script_pushdown_enabled'):
            if catalog + name in settings:
                settings[catalog + name] = 'false'
    return settings


def measure(version, backend, item, sql, settings, plan, reference, template_size, documents):
    control = f'http://127.0.0.1:19214/__compare/control?variant={version}&backend={backend}'
    C.http(control + '&reset=1')
    actual = C.query(version, sql, backend, settings)
    events = C.http(control)['events']
    info = C.http(f'http://127.0.0.1:{C.PORTS[version]}/v1/query/{actual["query_id"]}',
                  headers={'X-Trino-User': 'expression-comparison'})
    mode, scripts, native, residual, searches = C.classify(plan, actual, events)
    approximate = mode.endswith('APPROXIMATE') and 'analyzer_ids' in reference
    eligible = reference['analyzer_ids'] if approximate else reference['sql_ids']
    count = bool(re.match(r'^SELECT count\(\*\)', sql, re.I))
    expected = row_summary(expected_rows(eligible, template_size, documents, count))
    sql_expected = row_summary(expected_rows(reference['sql_ids'], template_size, documents, count))
    rows = actual.pop('rows', [])
    summary = row_summary(rows)
    correct = not actual.get('error') and summary == expected
    stats = info.get('queryStats', {})
    metrics = {key: stats.get(key) for key in (
        'elapsedTime', 'executionTime', 'planningTime', 'queuedTime', 'cpuTime', 'scheduledTime',
        'physicalInputPositions', 'physicalInputDataSize', 'processedInputPositions', 'processedInputDataSize',
        'peakUserMemoryReservation', 'peakTotalMemoryReservation', 'outputPositions', 'outputDataSize',
        'fullyBlocked', 'blockedReasons')}
    return {'version': version, 'backend': backend, 'feature': item['feature'], 'case_id': item['id'],
            'sql': sql, 'mode': item['mode'], 'session': settings, 'correct': correct,
            'sql_equal': summary == sql_expected, 'oracle_kind': 'Elasticsearch analyzer' if approximate else 'SQL',
            'expected': expected, 'sql_expected': sql_expected, 'actual_summary': summary, 'actual': actual,
            'classification': mode, 'script': scripts, 'native': native, 'residual': residual,
            'source_hits': sum(event.get('source_hits', 0) for event in searches),
            'response_bytes': sum(event.get('response_bytes', 0) for event in searches),
            'request_bytes': sum(event.get('request_bytes', 0) for event in searches),
            'search_requests': len(searches), 'es_took_sum_ms': sum(event.get('took_ms') or 0 for event in searches),
            'query_stats': metrics, 'events': events}


def snapshot(backends):
    names = ['trino-expr-master', 'trino-expr-internal', 'trino-expr-current', 'trino-expr-proxy']
    backend_containers = {'es8': 'trino-harbor-verification-es8-1', 'es7': 'trino-expr-es7'}
    names.extend(backend_containers[backend] for backend in backends)
    containers = json.loads(subprocess.check_output(['docker', 'inspect', *names], text=True))
    return {'time': datetime.now(timezone.utc).isoformat(), 'load': Path('/proc/loadavg').read_text(),
            'memory': Path('/proc/meminfo').read_text(), 'disk': list(shutil.disk_usage('/opt')),
            'containers': [{'name': value['Name'], 'image': value['Image'], 'state': value['State'],
                            'cpus': value['HostConfig']['NanoCpus'], 'memory': value['HostConfig']['Memory']}
                           for value in containers],
            'elasticsearch': {backend: {'health': C.http(es_url(backend) + '/_cluster/health'),
                                        'nodes': C.http(es_url(backend) + '/_nodes/stats/jvm,process,fs,indices,thread_pool')}
                              for backend in backends}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--backends', default='es8')
    parser.add_argument('--only', default='')
    parser.add_argument('--config', default='', help='Optional single SHARDS:DOCUMENTS smoke configuration')
    parser.add_argument('--repeats', type=int, default=3)
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error('--repeats must be positive')
    output = args.out
    output.mkdir(parents=True, exist_ok=True)
    items = [item for item in features(args.report) if not args.only or re.search(args.only, item['id'])]
    backends = args.backends.split(',')
    configurations = [tuple(map(int, args.config.split(':')))] if args.config else CONFIGURATIONS
    info, sessions = C.wait_and_sessions(VERSIONS, backends)
    for version, expected in EXPECTED_REVISIONS.items():
        if info[version]['nodeVersion']['version'] != expected:
            raise RuntimeError('Unexpected deployed revision: ' + str(info))
    manifest_path = output / 'manifest.json'
    manifest = {'started': datetime.now(timezone.utc).isoformat(), 'server_info': info, 'sessions': sessions,
                'features': items, 'configurations': configurations, 'backends': backends, 'repeats': args.repeats,
                'warmups': 1, 'before': snapshot(backends), 'deployment': json.loads((C.ROOT / 'deployment.json').read_text()),
                'harness_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'comparison_harness_sha256': hashlib.sha256(Path(C.__file__).read_bytes()).hexdigest(),
                'report_sha256': hashlib.sha256(args.report.read_bytes()).hexdigest(),
                'method': 'Sequential variants, rotating order per feature/round; warm cache; request cache disabled; no writes during measurement'}
    if manifest_path.exists():
        if not args.resume:
            raise RuntimeError('Output already exists; use --resume or another --out')
        original = json.loads(manifest_path.read_text())
        for key in ('server_info', 'features', 'configurations', 'backends', 'repeats', 'harness_sha256'):
            left = original[key]
            right = manifest[key]
            if key == 'server_info':
                left = {v: x['nodeVersion'] for v, x in left.items()}
                right = {v: x['nodeVersion'] for v, x in right.items()}
            if json.dumps(left, sort_keys=True) != json.dumps(right, sort_keys=True):
                raise RuntimeError('Cannot resume a different benchmark: ' + key)
        manifest = original
    else:
        save(manifest_path, manifest)
    saved_compilation = {}
    active = []
    completed = []
    failures = []
    try:
        for backend in backends:
            original = C.http(es_url(backend) + '/_cluster/settings?flat_settings=true')
            saved_compilation[backend] = original.get('transient', {}).get('script.max_compilations_rate')
            C.http(es_url(backend) + '/_cluster/settings', {'transient': {'script.max_compilations_rate': '10000/5m'}}, 'PUT')
        save(output / 'compilation-settings-before.json', saved_compilation)
        for backend in backends:
            template = templates(backend)
            references = oracle(backend, items)
            save(output / f'{backend}-templates.json', template)
            save(output / f'{backend}-oracles.json', references)
            for shards, documents in configurations:
                stem = f'{backend}-s{shards}-n{documents}'
                result_path = output / (stem + '.jsonl.gz')
                done_path = output / (stem + '-done.json')
                if done_path.exists():
                    status = json.loads(done_path.read_text())
                    completed.append(status)
                    failures.extend(status['failures'])
                    print('SKIP completed', stem, flush=True)
                    continue
                print('SEED', stem, flush=True)
                active = [(backend, index_name(shards, documents, table)) for table in template]
                fixtures = seed(backend, shards, documents, template)
                save(output / (stem + '-fixtures.json'), fixtures)
                before = snapshot([backend])
                count = 0
                config_failures = []
                with gzip.open(result_path, 'wt', encoding='utf-8') as stream:
                    for item in items:
                        sql = item['sql'].replace(C.PREFIX + '_' + item['table'], index_name(shards, documents, item['table']))
                        plans = {}
                        settings = {}
                        for version in VERSIONS:
                            settings[version] = settings_for(version, backend, item, sessions)
                            result = C.query(version, 'EXPLAIN (TYPE DISTRIBUTED) ' + sql, backend, settings[version])
                            if result.get('error'):
                                raise RuntimeError('EXPLAIN failed: ' + str(result))
                            plans[version] = '\n'.join(row[0] for row in result['rows'])
                        for round_number in range(args.repeats + 1):
                            shift = (item['feature'] + round_number) % len(VERSIONS)
                            order = VERSIONS[shift:] + VERSIONS[:shift]
                            for version in order:
                                record = measure(version, backend, item, sql, settings[version], plans[version],
                                                 references[item['id']], len(template[item['table']]['values']), documents)
                                record.update(shards=shards, documents=documents, warmup=round_number == 0,
                                              repetition=round_number, order=list(order),
                                              plan=plans[version] if round_number == 0 else None,
                                              captured_at=datetime.now(timezone.utc).isoformat())
                                stream.write(json.dumps(record, ensure_ascii=False) + '\n')
                                stream.flush()
                                count += 1
                                if not record['correct']:
                                    failure = {key: record[key] for key in ('version', 'feature', 'case_id', 'shards', 'documents', 'repetition', 'classification', 'actual')}
                                    config_failures.append(failure)
                                    print('MISMATCH', backend, failure, flush=True)
                        print('DONE', stem, item['feature'], item['id'], count,
                              'last_ms', record['actual']['elapsed_ms'], flush=True)
                status = {'backend': backend, 'shards': shards, 'documents': documents, 'executions': count,
                          'failures': config_failures, 'before': before, 'after': snapshot([backend]),
                          'completed': datetime.now(timezone.utc).isoformat()}
                save(done_path, status)
                completed.append(status)
                failures.extend(config_failures)
                for selected_backend, name in active:
                    delete_index(selected_backend, name)
                active = []
                save(output / 'progress.json', {'completed': completed, 'failures': failures})
    finally:
        cleanup_errors = []
        for backend, name in active:
            try:
                delete_index(backend, name)
            except Exception as error:
                cleanup_errors.append(repr(error))
        for backend, original in saved_compilation.items():
            try:
                C.http(es_url(backend) + '/_cluster/settings', {'transient': {'script.max_compilations_rate': original}}, 'PUT')
            except Exception as error:
                cleanup_errors.append(repr(error))
        save(output / 'cleanup.json', {'original_compilation_settings': saved_compilation,
                                      'errors': cleanup_errors, 'after': snapshot(backends)})
    manifest.update(finished=datetime.now(timezone.utc).isoformat(), completed=len(completed),
                    executions=sum(value['executions'] for value in completed), failures=len(failures))
    save(manifest_path, manifest)
    print('COMPLETE', manifest['executions'], 'executions;', len(failures), 'mismatches', flush=True)
    if any(failure['version'] == 'current' for failure in failures):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
