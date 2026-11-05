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


"""Read real distributed plans on three isolated Trino revisions; never seed data."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from pathlib import Path
import re
import time
from urllib.request import Request, urlopen

VERSIONS = {
    'original': {'port': 18083, 'revision': '5a5b158ce93ff35c5a01da76df2e7a0e9db35122'},
    'old': {'port': 18082, 'revision': 'cb0fc90dc9014ed2e1a84a6b28b94ec5b8e46069'},
    'new': {'port': 18081, 'revision': '58b28a227c7edac4cbbba1368620aa099d36f9b9'},
}
CASES = []


def case(identifier, row, sql, kind='aggregate', expected=True, **extra):
    CASES.append(dict(id=identifier, row=row, sql=sql, kind=kind, expected_new=expected, **extra))


case('M01', '1.1', 'SELECT obj.value, count(*) FROM lab_qs_shapes GROUP BY obj.value')
case('M02', '1.1', 'SELECT city, count(*) FROM lab_qs_ignored GROUP BY city', expected=False, execute=True)
case('M03', '1.1', 'SELECT city, count(*) FROM lab_qs_normalized GROUP BY city', expected=False, execute=True)
case('Z01', '1.2', 'SELECT id FROM lab_qs_shapes ORDER BY amount, id LIMIT 2', 'topn', False)
case('Z02', '1.2', 'SELECT amount, count(*) FROM lab_qs_shapes GROUP BY amount', expected=False, execute=True)
case('C01', '1.3', 'SELECT agg_0, count(*) FROM lab_qs_shapes GROUP BY agg_0', execute=True)
case('R01', '1.4', 'SELECT avg(amount) FROM lab_qs_shapes')
case('T01', '1.5', 'SELECT dt, count(*) FROM lab_qs_shapes GROUP BY dt', execute=True)
case('P01', '1.6', 'SELECT id, count(*) FROM lab_qs_volume GROUP BY id')
for column in ['b', 's', 'i', 'l']:
    case('A' + column, '1.7', f'SELECT avg({column}) FROM lab_qs_shapes')
case('L01', '2.1', 'SELECT id FROM (SELECT id FROM lab_qs_shapes LIMIT 5) LIMIT 2', 'limit')
case('L02', '2.2', 'SELECT id FROM (SELECT id FROM lab_qs_shapes ORDER BY id LIMIT 5) ORDER BY id LIMIT 2', 'topn')
case('L03', '2.3', 'SELECT id FROM lab_qs_volume ORDER BY id DESC LIMIT 2501', 'topn')
for number, order in enumerate(['ASC NULLS FIRST', 'ASC NULLS LAST', 'DESC NULLS FIRST', 'DESC NULLS LAST'], 1):
    case(f'N0{number}', '2.4', f'SELECT id FROM lab_qs_shapes ORDER BY agg_0 {order}, id DESC LIMIT 3', 'topn')
case('B01', '2.5', 'SELECT count(*) FROM (SELECT id FROM lab_qs_shapes LIMIT 2)', expected=False)
case('B02', '2.5', 'SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) ORDER BY id DESC LIMIT 2', 'outer_topn', False)
case('G01', '2.6', 'SELECT count(*), sum(amount), avg(amount), min(amount), max(amount) FROM lab_qs_shapes')
case('G02', '2.7', 'SELECT g, flag, agg_0, count(*) FROM lab_qs_shapes GROUP BY g, flag, agg_0', execute=True)
case('S01', '2.8', 'SELECT sum(amount) FROM lab_qs_shapes WHERE id = 5', execute=True)
case('F01', '2.9', 'SELECT avg(DISTINCT i) FROM lab_qs_shapes', expected=False)
case('F02', '2.9', 'SELECT sum(amount) FILTER (WHERE id > 1) FROM lab_qs_shapes', expected=False)
case('F03', '2.9', 'SELECT array_agg(i ORDER BY id) FROM lab_qs_shapes', expected=False)
case('F04', '2.9', 'SELECT g, flag, count(*) FROM lab_qs_shapes GROUP BY GROUPING SETS ((g), (flag))', expected=False)
case('I01', '2.10', 'SELECT sum(i) FROM lab_qs_shapes', expected=False)
case('I02', '2.10', 'SELECT min(l), max(l) FROM lab_qs_boundaries', expected=False)
case('D01', '2.11', 'SELECT approx_distinct(g) FROM lab_qs_shapes')
case('IR01', '2.12', 'SELECT id FROM "lab_qs_shapes:g:a" ORDER BY id LIMIT 2', 'topn', execute=True)
case('IR02', '2.12', "SELECT id FROM lab_qs_shapes WHERE any_match(tags, t -> t = 'a') ORDER BY id LIMIT 2", 'topn', execute=True)
case('E01', '2.13', 'SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 1', 'topn', execute=True)
case('E02', '2.13', 'SELECT id FROM lab_qs_shapes ORDER BY id LIMIT 2', 'topn', catalog='es8_pit', versions=['old', 'new'])
case('Q01', '2.14', 'SELECT g, count(*) FROM lab_qs_shapes GROUP BY g')
case('W01', '2.15', 'SELECT avg(amount) FROM lab_qs_shapes', session='aggregation_pushdown_enabled=true', versions=['old', 'new'])
case('W02', '2.15', 'SELECT avg(amount) FROM lab_qs_shapes', expected=False, session='aggregation_pushdown_enabled=false', versions=['old', 'new'])


def query(version, sql, catalog, session=None):
    headers = {'X-Trino-User': 'matrix-review', 'X-Trino-Catalog': catalog, 'X-Trino-Schema': 'default'}
    if session:
        headers['X-Trino-Session'] = catalog + '.' + session
    request = Request(f'http://127.0.0.1:{VERSIONS[version]["port"]}/v1/statement', data=sql.encode(), headers=headers)
    rows = []
    deadline = time.monotonic() + 180
    while True:
        with urlopen(request, timeout=60) as response:
            result = json.load(response)
        if 'error' in result:
            raise RuntimeError(result['error']['message'])
        rows.extend(result.get('data', []))
        if 'nextUri' not in result:
            return {'query_id': result['id'], 'rows': rows}
        if time.monotonic() > deadline:
            raise TimeoutError(sql)
        request = Request(result['nextUri'], headers=headers)


def inspect_plan(plan, kind):
    scan = '\n'.join(line.strip() for line in plan.splitlines() if 'TableScan[' in line or 'ScanFilterProject[' in line)
    if kind == 'aggregate':
        return not bool(re.search(r'\bAggregate\[', plan)) and bool(scan), scan
    if kind == 'limit':
        return bool(re.search(r'(?:limit=|count=)\s*\d+', scan)), scan
    if kind == 'outer_topn':
        descending = 'ascending=false' in scan or 'order=DESC_' in scan
        count_two = bool(re.search(r'(?:limit=|count=)2(?:\D|$)', scan))
        return descending and count_two, scan
    return bool('sort=[' in scan or re.search(r'TopNSortItem\[field=(?!_doc\b)', scan)), scan


def run_case(version, item, catalog):
    result = {'version': version, 'catalog': catalog, **item}
    if version not in item.get('versions', VERSIONS):
        return {**result, 'status': 'NOT_APPLICABLE'}
    try:
        answer = query(version, 'EXPLAIN (TYPE DISTRIBUTED) ' + item['sql'], catalog, item.get('session'))
        plan = answer['rows'][0][0]
        pushed, scan = inspect_plan(plan, item['kind'])
        result.update(status='OK', pushed=pushed, scan=scan, plan=plan, query_id=answer['query_id'])
        if item.get('execute'):
            result['actual'] = query(version, item['sql'], catalog)['rows']
    except Exception as error:
        result.update(status='ERROR', error=str(error))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--versions', default='original,old,new')
    parser.add_argument('--output', default='results/pushdown-matrix.json')
    args = parser.parse_args()
    results = []
    tasks = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        for version in args.versions.split(','):
            for item in CASES:
                catalogs = [item['catalog']] if 'catalog' in item else ['es7_scroll', 'es8_scroll']
                for catalog in catalogs:
                    tasks.append(pool.submit(run_case, version, item, catalog))
        for task in as_completed(tasks):
            result = task.result()
            results.append(result)
            print(result['version'], result['catalog'], result['id'], result['status'], result.get('pushed', result.get('error', '')), flush=True)
    document = {'versions': VERSIONS, 'cases': CASES, 'results': sorted(results, key=lambda r: (r['version'], r['id'], r['catalog']))}
    Path(args.output).write_text(json.dumps(document, indent=2, ensure_ascii=False))
    print('TOTAL', len(results), 'ERRORS', sum(r['status'] == 'ERROR' for r in results))
