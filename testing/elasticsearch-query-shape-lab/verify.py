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

"""Execute real SQL against the isolated source-built Trino lab. Standard library only."""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re
import time
import traceback
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / 'results'
RESULTS.mkdir(exist_ok=True)
TRINO = 'http://127.0.0.1:18081'
PROXY = 'http://127.0.0.1:19202'
report = {'revision': (ROOT / 'SOURCE_REVISION').read_text().strip(), 'tests': [], 'measurements': []}


def http(url, body=None, method=None, headers=None):
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
    req = Request(url, data=body, method=method, headers=headers or {'Content-Type': 'application/json'})
    with urlopen(req, timeout=120) as response:
        data = response.read()
        return json.loads(data) if data else {}


def query(sql, catalog='es8_scroll', disabled=False):
    headers = {'X-Trino-User': 'query-shape-lab', 'X-Trino-Catalog': catalog, 'X-Trino-Schema': 'default'}
    if disabled:
        headers['X-Trino-Session'] = f'allow_pushdown_into_connectors=false,{catalog}.aggregation_pushdown_enabled=false'
    result = http(TRINO + '/v1/statement', sql.encode(), 'POST', headers)
    rows = []
    columns = []
    deadline = time.monotonic() + 240
    while True:
        if 'error' in result:
            raise RuntimeError(result['error']['message'])
        columns = result.get('columns', columns)
        rows.extend(result.get('data', []))
        if 'nextUri' not in result:
            return {'id': result['id'], 'rows': rows, 'columns': columns, 'stats': result.get('stats', {})}
        if time.monotonic() > deadline:
            http(result['nextUri'], method='DELETE', headers=headers)
            raise TimeoutError(sql)
        result = http(result['nextUri'], headers=headers)


def canonical(value):
    if isinstance(value, list):
        return tuple(canonical(item) for item in value)
    if isinstance(value, float):
        if math.isnan(value):
            return 'NaN'
        return float(format(value, '.6g')) if value else 0.0
    return value


def same(actual, expected):
    assert Counter(map(canonical, actual)) == Counter(map(canonical, expected)), f'{actual[:8]} != {expected[:8]}'


def save():
    (RESULTS / 'verification.json').write_text(json.dumps(report, indent=2, ensure_ascii=False, default=str))


def test(name, action):
    start = time.monotonic()
    try:
        detail = action()
        entry = {'name': name, 'status': 'PASS', 'seconds': round(time.monotonic() - start, 3), 'detail': detail}
    except Exception as error:
        entry = {'name': name, 'status': 'FAIL', 'error': str(error), 'trace': traceback.format_exc()}
    report['tests'].append(entry)
    print(entry['status'], name, entry.get('error', ''), flush=True)
    save()


def compare(catalog, sql, aggregate=None, sort=None):
    pushed = query(sql, catalog)
    baseline = query(sql, catalog, disabled=True)
    same(pushed['rows'], baseline['rows'])
    plan = query('EXPLAIN (TYPE DISTRIBUTED) ' + sql, catalog)['rows'][0][0]
    if aggregate is not None:
        local_aggregate = bool(re.search(r'\bAggregate\[', plan))
        assert local_aggregate != aggregate, f'Unexpected aggregation plan: {plan}'
    if sort is not None:
        assert ('sort=[' in plan) == sort, f'Unexpected sort pushdown: {plan}'
    return {'sql': sql, 'query_id': pushed['id'], 'rows': len(pushed['rows']), 'plan': plan}


def seed_index(base, name, properties, docs, settings=None, meta=None):
    assert base in ('http://127.0.0.1:19200', 'http://127.0.0.1:19201') and name.startswith('lab_qs_')
    mapping = {'properties': properties}
    if meta:
        mapping['_meta'] = meta
    try:
        http(base + '/' + name, method='DELETE')
    except HTTPError as error:
        if error.code != 404:
            raise
    http(base + '/' + name, {'settings': settings or {'number_of_shards': 3, 'number_of_replicas': 0}, 'mappings': mapping}, 'PUT')
    for offset in range(0, len(docs), 1000):
        lines = []
        for number, document in enumerate(docs[offset:offset + 1000], offset):
            action = {'_id': str(number)}
            if name.startswith('lab_qs_alias_'):
                action['routing'] = 'tenant'
            lines += [json.dumps({'index': action}), json.dumps(document)]
        result = http(base + '/' + name + '/_bulk?refresh=true', ('\n'.join(lines) + '\n').encode(), 'POST')
        assert not result.get('errors'), str(result)[:1000]


def seed():
    fields = {name: {'type': kind} for name, kind in {
        'id': 'long', 'agg_0': 'long', 'g': 'keyword', 'b': 'byte', 's': 'short', 'i': 'integer',
        'l': 'long', 'amount': 'double', 'f': 'float', 'dt': 'date', 'flag': 'boolean', 'tags': 'keyword'}.items()}
    fields['obj'] = {'properties': {'value': {'type': 'long'}}}
    docs = [
        {'id': 1, 'agg_0': 1, 'g': 'a', 'b': -128, 's': -32768, 'i': -2147483648, 'l': -9, 'amount': 0.0, 'f': 0.0, 'dt': '2020-01-01T00:00:00.001Z', 'flag': True, 'obj': {'value': 1}, 'tags': ['a','b']},
        {'id': 2, 'agg_0': 1, 'g': 'a', 'b': 127, 's': 32767, 'i': 2147483647, 'l': 9, 'amount': -0.0, 'f': -0.0, 'dt': '2020-01-01T00:00:00.001Z', 'flag': True, 'obj': {'value': 2}},
        {'id': 3, 'agg_0': 9007199254740993, 'g': 'b', 'b': -5, 's': -17, 'i': -17, 'l': -17, 'amount': -2.5, 'f': -2.5, 'dt': '1969-12-31T23:59:59.999Z', 'flag': False},
        {'id': 4, 'agg_0': 9007199254740993, 'g': 'b', 'b': 5, 's': 17, 'i': 17, 'l': 17, 'amount': 2.5, 'f': 2.5, 'flag': False},
        {'id': 5, 'agg_0': 2},
    ]
    boundaries = [{'g': group, 'l': value} for group, values in {
        'near53': [2**53-1, 2**53, 2**53+1], 'max': [2**63-1, 2**63-2], 'min': [-2**63, -2**63+1]}.items() for value in values]
    for port in (19200, 19201):
        base = f'http://127.0.0.1:{port}'
        seed_index(base, 'lab_qs_shapes', fields, docs, meta={'trino': {'tags': {'isArray': True}}})
        seed_index(base, 'lab_qs_boundaries', {'g': {'type': 'keyword'}, 'l': {'type': 'long'}}, boundaries)
        seed_index(base, 'lab_qs_volume', {'id': {'type': 'long'}, 'value': {'type': 'long'}}, [{'id': n, 'value': n-25000} for n in range(50000)])
        seed_index(base, 'lab_qs_normalized', {'city': {'type': 'keyword', 'normalizer': 'lower'}}, [{'city':'HANOI'}, {'city':'HaNoi'}],
                   {'number_of_shards':3, 'number_of_replicas':0, 'analysis': {'normalizer': {'lower': {'type':'custom','filter':['lowercase']}}}})
        seed_index(base, 'lab_qs_ignored', {'city': {'type': 'keyword', 'ignore_above': 3}}, [{'city':'a'}, {'city':'abcdef'}])
        seed_index(base, 'lab_qs_nodoc', {'n': {'type': 'long', 'doc_values': False}}, [{'n':1}, {'n':3}])
        seed_index(base, 'lab_qs_nullvalue', {'n': {'type':'integer','null_value':42}}, [{'n':None}, {'n':1}])
        seed_index(base, 'lab_qs_alias_a', {'id': {'type':'long'}, 'active': {'type':'boolean'}}, [{'id':1,'active':True},{'id':999,'active':False}])
        seed_index(base, 'lab_qs_alias_b', {'id': {'type':'long'}, 'active': {'type':'boolean'}}, [{'id':10,'active':True},{'id':9999,'active':False}])
        for index in ('lab_qs_alias_a','lab_qs_alias_b'):
            http(base + '/' + index + '/_alias/lab_qs_active', {'filter': {'term': {'active':True}}, 'routing':'tenant'}, 'PUT')
        print('Seeded', base, flush=True)


def metrics():
    names = query("SHOW TABLES FROM jmx.current LIKE '%elasticsearchpushdowndiagnostics%'", 'jmx')['rows']
    total = {}
    for row in names:
        table = row[0].replace('"', '""')
        result = query(f'SELECT * FROM jmx.current."{table}"', 'jmx')
        for values in result['rows']:
            for column, value in zip(result['columns'], values):
                key = column['name'].lower()
                if isinstance(value, (int,float)) and key in ('aggregationrequests','aggregationrows','aggregationoutputbytes','rowsdecoded','sourcebytesdecoded','searchrequests','nextpagerequests'):
                    total[key] = total.get(key, 0) + value
    if not total:
        raise AssertionError('Elasticsearch diagnostics MBeans not found')
    return total


def allocation():
    result = query('SELECT totalthreadallocatedbytes FROM jmx.current."java.lang:type=threading"', 'jmx')
    return sum(row[0] for row in result['rows'] if row[0] is not None)


def measure_average(disabled=False):
    http(PROXY + '/__lab/control?mode=normal&reset=1')
    before = metrics()
    actual = query('SELECT avg(value) FROM lab_qs_volume', 'es8_proxy', disabled)
    after = metrics()
    same(actual['rows'], [[-0.5]])
    delta = {key: after[key] - before[key] for key in before}
    proxy = http(PROXY + '/__lab/stats')['searches']
    detail = {'disabled':disabled, 'diagnostics':delta, 'requests':proxy, 'query_id':actual['id'], 'stats':actual['stats']}
    report['measurements'].append(detail)
    if not disabled:
        assert delta['aggregationrequests'] == 1 and delta['aggregationrows'] == 1, delta
        assert delta['rowsdecoded'] == 0 and delta['sourcebytesdecoded'] == 0, delta
        assert len(proxy) == 1 and proxy[0]['size'] == 0 and proxy[0]['source_hits'] == 0, proxy
    else:
        assert delta['rowsdecoded'] == 50000 and delta['sourcebytesdecoded'] > 0, delta
    return detail


def measure_pages(size):
    catalog = f'es8_page{size}'
    sql = 'SELECT id, count(*) FROM lab_qs_volume GROUP BY id'
    query(sql, catalog)  # warm up before measurement
    http(PROXY + '/__lab/control?mode=normal&reset=1')
    before = metrics()
    allocated = allocation()
    started = time.monotonic()
    actual = query(sql, catalog)
    elapsed = time.monotonic() - started
    allocated = allocation() - allocated
    after = metrics()
    requests = http(PROXY + '/__lab/stats')['searches']
    assert len(actual['rows']) == 50000 and all(row[1] == 1 for row in actual['rows'])
    assert len({row[0] for row in actual['rows']}) == 50000
    assert all(request['size'] == 0 and request['composite_size'] == size for request in requests)
    detail = {'page_size':size, 'rows':len(actual['rows']), 'requests':len(requests), 'response_bytes':sum(r['response_bytes'] for r in requests),
              'largest_response_bytes':max(r['response_bytes'] for r in requests), 'allocated_bytes_process_wide':allocated,
              'elapsed_seconds':elapsed, 'diagnostics':{k:after[k]-before[k] for k in before}}
    report['measurements'].append(detail)
    return detail


def fault(mode):
    http(PROXY + '/__lab/control?reset=1&mode=' + mode)
    try:
        query('SELECT avg(value) FROM lab_qs_volume', 'es8_proxy')
    except RuntimeError as error:
        requests = http(PROXY + '/__lab/stats')['searches']
        assert len(requests) == 1, requests
        return {'expected_error':str(error), 'requests':requests}
    finally:
        http(PROXY + '/__lab/control?mode=normal')
    raise AssertionError('Invalid aggregation response was accepted')


def verify():
    for catalog in ('es7_scroll','es7_pit','es8_scroll','es8_pit'):
        for column in ('b','s','i','l','amount','f'):
            for grouped in (False,True):
                sql = f'SELECT {"g, " if grouped else ""}avg({column}) FROM lab_qs_shapes' + (' GROUP BY g' if grouped else '')
                test(f'{catalog} avg {column} grouped={grouped}', lambda c=catalog,s=sql:compare(c,s,aggregate=True))
        for group in ('near53','max','min'):
            sql = f"SELECT avg(l) FROM lab_qs_boundaries WHERE g='{group}'"
            test(f'{catalog} bigint boundary {group}', lambda c=catalog,s=sql:compare(c,s,aggregate=True))
        for label, sql, pushed in [
            ('empty avg', 'SELECT avg(l) FROM lab_qs_shapes WHERE id < 0', True),
            ('collision', 'SELECT agg_0,count(*) FROM lab_qs_shapes GROUP BY agg_0', True),
            ('timestamp boolean bigint', 'SELECT dt,flag,agg_0,avg(i) FROM lab_qs_shapes GROUP BY dt,flag,agg_0', True),
            ('sum all null', 'SELECT agg_0,sum(amount) FROM lab_qs_shapes GROUP BY agg_0', True),
            ('integral sum fallback','SELECT sum(l) FROM lab_qs_shapes',False),
            ('bigint minmax fallback','SELECT min(l),max(l) FROM lab_qs_shapes',False),
            ('distinct fallback','SELECT avg(DISTINCT i) FROM lab_qs_shapes',False),
            ('normalized fallback','SELECT city,count(*) FROM lab_qs_normalized GROUP BY city',False),
            ('ignored fallback','SELECT city,count(*) FROM lab_qs_ignored GROUP BY city',False),
            ('doc values fallback','SELECT avg(n) FROM lab_qs_nodoc',False),
            ('null value fallback','SELECT avg(n) FROM lab_qs_nullvalue',False),
            ('approx distinct','SELECT approx_distinct(g) FROM lab_qs_shapes',True),
            ('projection','SELECT obj.value,count(*) FROM lab_qs_shapes GROUP BY obj.value',True),
        ]:
            test(f'{catalog} {label}', lambda c=catalog,s=sql,p=pushed:compare(c,s,aggregate=p))
        for column in ('amount','f'):
            test(f'{catalog} signed zero grouping {column}', lambda c=catalog,col=column:compare(c,f'SELECT {col},count(*) FROM lab_qs_shapes GROUP BY {col}',aggregate=False))
            for order in ('ASC NULLS FIRST','ASC NULLS LAST','DESC NULLS FIRST','DESC NULLS LAST'):
                test(f'{catalog} floating TopN {column} {order}', lambda c=catalog,col=column,o=order:compare(c,f'SELECT id FROM lab_qs_shapes ORDER BY {col} {o},id LIMIT 2',sort=False))
        for order in ('ASC NULLS FIRST','ASC NULLS LAST','DESC NULLS FIRST','DESC NULLS LAST'):
            test(f'{catalog} integer TopN {order}',lambda c=catalog,o=order:compare(c,f'SELECT id FROM lab_qs_shapes ORDER BY agg_0 {o},id DESC LIMIT 3',sort=True))
        for sql in ('SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 1','SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 10',
                    'SELECT id FROM lab_qs_volume ORDER BY id DESC LIMIT 2501',
                    'SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) ORDER BY id DESC LIMIT 2',
                    'SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) WHERE id > 2498'):
            test(f'{catalog} boundary/alias {sql}',lambda c=catalog,s=sql:compare(c,s))
    for size in (1,2,1000,10000):
        test(f'page size {size} typed keys',lambda n=size:compare(f'es8_page{n}','SELECT dt,flag,agg_0,count(*) FROM lab_qs_shapes GROUP BY dt,flag,agg_0',aggregate=True))
    test('AVG 50000 documents pushed counters',lambda:measure_average(False))
    test('AVG 50000 documents baseline counters',lambda:measure_average(True))
    for size in (1000,10000):
        test(f'composite allocation/page measurement {size}',lambda n=size:measure_pages(n))
    for mode in ('missing_completion','timeout','failed_shard','invalid_failed_type','inexact_count','negative_count'):
        test(f'strict response {mode}',lambda m=mode:fault(m))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--seed', action='store_true')
    parser.add_argument('--seed-only', action='store_true')
    args = parser.parse_args()
    if args.seed or args.seed_only:
        seed()
    if not args.seed_only:
        verify()
        passed = sum(t['status']=='PASS' for t in report['tests'])
        print(f'RESULT: {passed}/{len(report["tests"])} passed', flush=True)
        save()
        raise SystemExit(0 if passed == len(report['tests']) else 1)
