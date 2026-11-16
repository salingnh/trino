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

"""Live, reproducible SQL/correctness/actual-DSL comparison of three isolated revisions."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

PREFIX = 'expr_a2af4b7203e'
ROOT = Path('/opt/trino-expression-comparison-a2af4b7203e')
PORTS = {'master': 18091, 'internal': 18092, 'current': 18093}
ES_PORTS = {'es8': 19211, 'es7': 19215}
LABELS = {'master': 'Trino master', 'internal': 'Trino internal/production', 'current': 'Trino branch mới'}
CASES = []


def add(identifier, group, predicate=None, *, array=None, sql=None, table='cases', off=False, allow_local=False, reference=None, subset=False, mode='SAFE', approximate_query=None):
    if sql is None:
        condition = f'any_match({array}, x -> {predicate})' if array else predicate
        sql = f'SELECT id FROM {PREFIX}_{table} WHERE {condition}'
    CASES.append({'id': identifier, 'group': group, 'sql': sql, 'table': table,
                  'off': off, 'allow_local': allow_local, 'reference': reference, 'subset': subset, 'mode': mode, 'approximate_query': approximate_query})


def pair(identifier, group, body, field='numbers', scalar='n', **kwargs):
    add('A-' + identifier, group, body, array=field, **kwargs)
    add('S-' + identifier, group, re.sub(r'\bx\b', scalar, body), **kwargs)


for index, predicate in enumerate([
        'x = 12', 'x <> 12', 'x < 10', 'x <= 10', 'x > 10', 'x >= 10',
        'x BETWEEN 5 AND 12', 'x NOT BETWEEN 5 AND 12', 'x IN (5, 12)', 'x IN (5, 12, NULL)',
        'x NOT IN (5, 12)', 'x NOT IN (5, NULL)', 'NOT (x = 12)', 'x IS NOT NULL',
        'x IN (5,12) AND x > 10', 'x < 0 OR x > 10', 'x > 10 AND x < 5',
        'x IS NOT DISTINCT FROM 12', 'x IS DISTINCT FROM NULL'], 1):
    pair(f'N{index:02}', 'Native integral / ba giá trị', predicate)
add('A-NNULL', 'Giữ semantics NULL', 'x IS NULL', array='numbers', allow_local=True)
add('S-NNULL', 'Giữ semantics NULL', 'n IS NULL')
add('A-NDISTINCT', 'Giữ semantics NULL', 'x IS DISTINCT FROM 12', array='numbers', allow_local=True)
add('S-NDISTINCT', 'Giữ semantics NULL', 'n IS DISTINCT FROM 12')
add('A-NTRUE', 'Giữ semantics NULL', 'TRUE OR x > 10', array='numbers', allow_local=True)
for identifier, predicate in [
        ('M1', 'contains(numbers, 12)'), ('M2', 'arrays_overlap(numbers, ARRAY[5,12])'),
        ('M3', "contains(words, 'abc')"), ('M4', 'contains(flags, true)'),
        ('M5', 'arrays_overlap(numbers, ARRAY[12,CAST(NULL AS INTEGER)])')]:
    add(identifier, 'Membership hiện có', predicate, allow_local=identifier == 'M5')
for identifier, predicate in [
        ('P1', 'x > 10 AND sin(x) > 0'),
        ('P2', 'x > 10 OR sin(x) > 0'),
        ('P3', '(x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0)'),
        ('P4', 'x IN (5,12) AND x > 10')]:
    pair(identifier, 'Điều kiện cần; scripts OFF', predicate, off=True, allow_local=identifier == 'P2')
for index, predicate in enumerate([
        'x + 1 > 10', 'x - 1 > 10', 'x * 2 > 20', 'x / 2 > 5', 'x % 2 = 0', 'mod(x,2)=0',
        'mod(x,-1)=0', '-x > 5', 'x > 10 OR mod(x,2)=0', 'x > 10 AND mod(x,2)=0',
        'NOT (mod(x,2)=0)', 'abs(x) > 10 OR x = 2'], 1):
    pair(f'I{index:02}', 'Số học integral / hybrid', predicate)
for sql_type, field, scalar in [('TINYINT', 'tiny_numbers', 'tiny'), ('SMALLINT', 'small_numbers', 'small'), ('BIGINT', 'big_numbers', 'big')]:
    pair('WIDTH-' + sql_type, 'Độ rộng kiểu số', f'x + {sql_type} \'1\' > {sql_type} \'10\'', field=field, scalar=scalar)
    pair('MOD-' + sql_type, 'Độ rộng kiểu số', f'mod(x, {sql_type} \'2\') = {sql_type} \'0\'', field=field, scalar=scalar)
for index, body in enumerate([
        'abs(x)>5', 'ceil(x)>=1', 'floor(x)<1', 'round(x, -1)=10', 'truncate(x)>5', 'sign(x)=-1',
        'sqrt(abs(x))>DOUBLE \'2\'', 'pow(x,DOUBLE \'2\')>DOUBLE \'100\''], 1):
    pair(f'MATH{index}', 'Hàm số', body)
for sql_type, field, scalar in [('REAL', 'reals', 'f'), ('DOUBLE', 'doubles', 'd')]:
    expressions = [
        f'x + CAST(1.5 AS {sql_type}) > CAST(2 AS {sql_type})',
        f'x - CAST(1.5 AS {sql_type}) < CAST(0 AS {sql_type})',
        f'x * CAST(2 AS {sql_type}) > CAST(1 AS {sql_type})',
        f'x / CAST(0 AS {sql_type}) > CAST(0 AS {sql_type})',
        f'mod(x, CAST(2 AS {sql_type})) = CAST(0 AS {sql_type})',
        f'-x > CAST(0 AS {sql_type})', f'x = CAST(0 AS {sql_type})', f'x >= CAST(0 AS {sql_type})',
        f'x / x = CAST(nan() AS {sql_type})', f'x / x <> CAST(nan() AS {sql_type})',
        f'x / x < CAST(nan() AS {sql_type})', f'x / x IS NOT DISTINCT FROM CAST(nan() AS {sql_type})',
        f'x / x IN (CAST(nan() AS {sql_type}), CAST(2 AS {sql_type}))',
        f'round(x,1)>=CAST(1 AS {sql_type})', 'ceil(x)=floor(x)',
        f'truncate(x)>CAST(0 AS {sql_type})', f'sign(x)=CAST(-1 AS {sql_type})',
    ]
    for index, body in enumerate(expressions, 1):
        pair(f'{sql_type}{index:02}', 'REAL/DOUBLE; signed zero; NaN', body, field=field, scalar=scalar)
for index, body in enumerate([
        "CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00'",
        "CAST(x AS DECIMAL(12,2)) - DECIMAL '1.25' > DECIMAL '10.00'",
        "CAST(x AS DECIMAL(12,2)) * DECIMAL '1.25' > DECIMAL '10.00'",
        "CAST(x AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00'",
        "mod(CAST(x AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00'",
        "round(CAST(x AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30'",
        "CAST(x AS DECIMAL(30,10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890'",
        "abs(CAST(x AS DECIMAL(12,2)))>DECIMAL '10.00'",
        "sign(CAST(x AS DECIMAL(12,2)))=DECIMAL '-1'",
        "floor(CAST(x AS DECIMAL(12,2)))=ceil(CAST(x AS DECIMAL(12,2)))",
        "CAST(CAST(x AS DECIMAL(12,2)) AS INTEGER)>10",
        "CAST(CAST(x AS DECIMAL(12,2)) AS DOUBLE)>DOUBLE '10'",
        "CAST(x AS SMALLINT)>10", 'TRY_CAST(x AS TINYINT)>10',
    ], 1):
    pair(f'DEC{index:02}', 'DECIMAL và CAST', body)
nullable = [
    ('coalesce(x,0)>10', False), ('coalesce(x,100)>10', True), ('nullif(x,0)>1', False),
    ('coalesce(nullif(x,0),1)>10', False), ('TRY_CAST(x AS TINYINT) IS NULL', True),
    ('CASE WHEN x IS NULL THEN false ELSE x>10 END', False),
    ('CASE WHEN x IS NULL THEN true ELSE x>10 END', True), ('IF(x>10,true,false)', False),
]
for index, (body, array_local) in enumerate(nullable, 1):
    add(f'A-NULL{index}', 'COALESCE / NULLIF / TRY_CAST / CASE', body, array='numbers', allow_local=array_local)
    add(f'S-NULL{index}', 'COALESCE / NULLIF / TRY_CAST / CASE', re.sub(r'\bx\b', 'n', body))
for index, body in enumerate(['x OR NOT x', 'coalesce(x,false)', 'coalesce(x,true)', 'x IS NOT DISTINCT FROM true'], 1):
    add(f'A-BOOL{index}', 'BOOLEAN', body, array='flags', allow_local=index == 3)
    add(f'S-BOOL{index}', 'BOOLEAN', re.sub(r'\bx\b', 'flag', body))
strings = [
    "starts_with(x,'abc')", "x LIKE 'ab%'", "x LIKE '%b%'", "x LIKE 'a_b'", "x LIKE '%a!_%' ESCAPE '!'",
    "starts_with(x,'a') AND starts_with(x,'ab')", "starts_with(x,'ab') OR x='nice'",
    "length(x)>2", "ends_with(x,'b')", "strpos(x,'😀')>0", "replace(x,'_','😀') LIKE '%😀%'",
    "replace(x,'','!') LIKE '%!s!á!%'", "concat(substr(x,1,1),'Z')='aZ'", "(x || 'Z')='abcZ'",
    "substr(x,-2,1)='b'", "substring(x,2)='bc'", "trim(x)='abc'", "ltrim(x)='abc'", "rtrim(x)='abc'",
    "lower(x) LIKE '%sá%'", "upper(x) LIKE '%SÁ%'", "x > '\ue000'",
    "length(x)>2 AND starts_with(x,'a')", "starts_with(x,'abc') AND lower(x) LIKE '%b%'",
    "regexp_like(x,'abc')", "regexp_like(x,'(?i)^abc')", "regexp_like(x,'^abc$')",
]
for index, body in enumerate(strings, 1):
    pair(f'TXT{index:02}', 'Keyword / Unicode / chuỗi', body, field='words', scalar='word', allow_local=index in (26, 27))
for field, scalar, prefix in [('ignored_words','ignored_word','IGNORE'), ('normalized_words','normalized_word','NORMAL'),
                              ('nodv_words','nodv_word','NODV'), ('doconly_words','doconly_word','DOCONLY')]:
    pair('MAP-' + prefix, 'Mapping guard / doc values', "lower(x) LIKE '%sá%'", field=field, scalar=scalar, allow_local=prefix != 'DOCONLY')
add('S-CASEPATH', 'Tên field phân biệt hoa thường', 'length(reviewcomment)>2')
extracts = ['year(x)=2024', 'month(x)=2', 'day(x)=29', 'hour(x)=12', 'minute(x)=59', 'second(x)=56',
            'day_of_week(x) BETWEEN 1 AND 5', 'day_of_year(x)=60']
for index, body in enumerate(extracts, 1):
    pair(f'DATE{index}', 'TIMESTAMP(3) UTC / extract', body, field='dates', scalar='dt')
for unit in ['millisecond', 'second', 'minute', 'hour', 'day', 'week', 'month', 'quarter', 'year']:
    pair('TRUNC-' + unit, 'date_trunc UTC', f"date_trunc('{unit}',x)>=TIMESTAMP '2024-01-01 00:00:00.000'", field='dates', scalar='dt')
    pair('ADD-' + unit, 'date_add UTC', f"date_add('{unit}',1,x)>TIMESTAMP '2024-03-01 00:00:00.000'", field='dates', scalar='dt')
for unit in ['millisecond', 'second', 'minute', 'hour', 'day', 'week']:
    pair('DIFF-' + unit, 'date_diff đơn vị cố định', f"date_diff('{unit}',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0", field='dates', scalar='dt')
pair('DIFF-REVERSE', 'date_diff đảo đối số; NULL', "date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0", field='dates', scalar='dt')
pair('DATE-NULL-ERROR', 'Lỗi trên phần tử NULL', "date_add('day',coalesce(year(x),BIGINT '9223372036854775807')+BIGINT '1',x)<TIMESTAMP '0001-01-01 00:00:00.000'", field='dates', scalar='dt', allow_local=True)
pair('DIFF-MONTH', 'Fallback được chủ định', "date_diff('month',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0", field='dates', scalar='dt', allow_local=True)
add('A-CAPTURE', 'Fallback được chủ định', 'any_match(numbers,x -> x>n)', allow_local=True)
add('A-CAPTURE-PREFILTER', 'Điều kiện cần với biến capture', 'any_match(numbers,x -> x>10 AND x>n)')
add('S-MULTICOL', 'Fallback được chủ định', 'n+small>10', allow_local=True)
add('S-MIXED-AND', 'Ghép các cột scalar', "n>10 AND lower(word) LIKE '%sá%'")
add('S-MIXED-OR', 'Ghép các cột scalar', "n>10 OR lower(word) LIKE '%sá%'")
pair('BUDGET', 'Resource budget fallback', 'mod(x,2) IN (' + ','.join(str(i) for i in range(200)) + ')', allow_local=True)
for identifier, body, field, scalar in [
        ('OVERFLOW', 'x + 1 > 0', 'error_ints', 'error_int'),
        ('DIV0', 'x / 0 > 0', 'numbers', 'n'),
        ('MOD0', 'mod(x,0)=0', 'numbers', 'n'),
        ('LONGDIV', "x / BIGINT '-1' > 0", 'error_longs', 'error_long'),
        ('ABS', 'abs(x)>0', 'error_longs', 'error_long'),
        ('CAST', 'CAST(x AS TINYINT)>0', 'error_ints', 'error_int'),
        ('ROUND', 'round(x,-19)>0', 'numbers', 'n'),
        ('TINY', "x + TINYINT '1' > TINYINT '0'", 'error_tinys', 'error_tiny'),
]:
    pair('ERR-' + identifier, 'Bảo toàn lỗi SQL', body, field=field, scalar=scalar)
for limit in (2, 3):
    reference = f"SELECT id FROM {PREFIX}_cases WHERE lower(word) LIKE '%sá%'"
    add('LIMIT-' + str(limit), 'LIMIT sau residual', sql=reference + f' LIMIT {limit}', reference=reference, subset=True)
    reference = f"SELECT id FROM {PREFIX}_cases WHERE any_match(words,x -> lower(x) LIKE '%sá%')"
    add('A-LIMIT-' + str(limit), 'LIMIT sau residual', sql=reference + f' LIMIT {limit}', reference=reference, subset=True)
add('VOL-NATIVE', '10.000 document', "any_match(numbers,x -> x>1500)", table='volume')
add('VOL-HYBRID', '10.000 document', "any_match(numbers,x -> x>1500 AND mod(x,2)=0)", table='volume')
add('VOL-SCRIPT', '10.000 document', "any_match(numbers,x -> mod(x,17)=0 AND mod(x,19)=0)", table='volume')
add('VOL-LOWER', '10.000 document', "lower(word) LIKE '%sá%'", table='volume')
add('VOL-COUNT', '10.000 document + COUNT', table='volume', sql=f'SELECT count(*) FROM {PREFIX}_volume WHERE any_match(numbers,x -> x>1500 AND mod(x,2)=0)')


# Text uses a dedicated fixture, preserving the 393 existing regression SQL unchanged.
add('TEXT-USER-SQL', 'Text / user query', "LOWER(raw_review_comment) LIKE '%sách rất%'", table='text', mode='UNSAFE', off=True, approximate_query={'match_phrase':{'raw_review_comment':'sách rất'}})
for field in ('review', 'with_keyword', 'foldedreview', 'casereview'):
    for operation, pattern, query_type in [('lower', '%sách rất%', 'match_phrase'), ('upper', '%SÁCH RẤT%', 'match_phrase'), ('lower', 'sách rất%', 'match_phrase_prefix')]:
        literal = pattern.strip('%')
        add(f'TEXT-NATIVE-{field}-{operation}-{query_type}', 'Text / native approximate',
            f"{operation}({field}) LIKE '{pattern}'", table='text', mode='UNSAFE',
            approximate_query={query_type:{field:literal}})
add('TEXT-ZERO-TOKENS', 'Text / analyzer stopwords', "lower(foldedreview) LIKE '%the%'", table='text', mode='UNSAFE', approximate_query={'match_phrase':{'foldedreview':'the'}})
add('TEXT-ARRAY-AND', 'Text / approximate array correlation', "lower(x) LIKE '%sách%' AND lower(x) LIKE '%rất%'", array='reviews', table='text', mode='UNSAFE', approximate_query={'bool':{'filter':[{'match_phrase':{'reviews':'sách'}},{'match_phrase':{'reviews':'rất'}}]}})
for mode in ('DISABLED', 'SAFE', 'UNSAFE'):
    for array in (False, True):
        add(f'TEXT-MODE-{mode}-' + ('A' if array else 'S'), 'Text / mode and script flags',
            "lower(x) LIKE '%sách rất%'" if array else "lower(review) LIKE '%sách rất%'",
            array='reviews' if array else None, table='text', mode=mode, off=True,
            approximate_query={'match_phrase':{'reviews' if array else 'review':'sách rất'}})
        add(f'TEXT-FLAG-{mode}-' + ('A' if array else 'S'), 'Text / mode and script flags',
            'length(trim(x))>2' if array else 'length(trim(review))>2',
            array='reviews' if array else None, table='text', mode=mode, off=True)
text_expressions = [
    'length(x)>2', 'length(trim(x))>2', "ends_with(x,'tốt')", "strpos(x,'😀')>0",
    "lower(trim(x)) LIKE '%sách rất%'", "ends_with(lower(trim(x)),'tốt')", "length(upper(trim(x)))>3",
    "trim(x)='ABC'", "ltrim(x)='ABC  '", "rtrim(x)='  ABC'", "substring(x,2)='😀Y'", "substr(x,-2,1)='b'",
    "strpos(replace(x,' ','_'),'rất_tốt')>0", "replace(x,'','!') LIKE '%!s!á!%'", "concat(trim(x),'Z')='ABCZ'",
    "coalesce(x,'fallback')='fallback'", "nullif(x,'') IS NULL", 'x IS NULL', 'x IS NOT NULL',
    "CASE WHEN x IS NULL THEN true ELSE length(x)>2 END", "IF(x IS NULL,true,length(x)>2)",
    "NOT starts_with(trim(x),'abc')", "length(trim(x))>2 AND ends_with(x,'tốt')",
    "length(trim(x))>2 OR ends_with(x,'tốt')", "length(x) BETWEEN 2 AND 10", "length(x) IN (2,3,4)",
    "x > 'abc'", "x NOT IN ('abc','')", "x BETWEEN 'a' AND 'z'", "x IS NOT DISTINCT FROM 'abc'",
    "regexp_like(lower(x),'sách')", "x LIKE '%rất%'", "starts_with(x,'sách')",
]
for index, body in enumerate(text_expressions,1):
    pair(f'TEXT{index:02}', 'Text / shared source compiler', body, field='reviews', scalar='review', table='text', mode='UNSAFE')
# Native token queries are intentionally approximate. Provide independent Elasticsearch oracles for those shapes.
for case in CASES:
    if case['id'] in ('A-TEXT32','S-TEXT32'):
        case['approximate_query']={'match_phrase':{'reviews' if case['id'].startswith('A-') else 'review':'rất'}}
    if case['id'] in ('A-TEXT30','S-TEXT30'):
        case['approximate_query']={'match_phrase':{'reviews' if case['id'].startswith('A-') else 'review':'abc'}}
    if case['id'] in ('A-TEXT33','S-TEXT33'):
        case['approximate_query']={'match_phrase_prefix':{'reviews' if case['id'].startswith('A-') else 'review':'sách'}}
add('TEXT-NESTED', 'Text / source field paths', 'length(profile.reviewcomment)>2', table='text', mode='UNSAFE')
add('TEXT-MIXED-AND', 'Text / compose columns', "n>10 AND lower(review) LIKE '%sách rất%'", table='text', mode='UNSAFE',
    approximate_query={'bool':{'filter':[{'range':{'n':{'gt':10}}},{'match_phrase':{'review':'sách rất'}}]}})
add('TEXT-MIXED-OR', 'Text / compose columns', "n>10 OR lower(review) LIKE '%sách rất%'", table='text', mode='UNSAFE',
    approximate_query={'bool':{'should':[{'range':{'n':{'gt':10}}},{'match_phrase':{'review':'sách rất'}}], 'minimum_should_match':1}})
add('TEXT-COUNT', 'Text / COUNT and GROUP BY', table='text', mode='UNSAFE', sql=f'SELECT count(*) FROM {PREFIX}_text WHERE length(trim(review))>2')
add('TEXT-GROUP', 'Text / COUNT and GROUP BY', table='text', mode='UNSAFE', sql=f'SELECT n,count(*) FROM {PREFIX}_text WHERE length(trim(review))>2 GROUP BY n')
add('TEXT-LIMIT', 'Text / LIMIT and ordering', table='text', mode='UNSAFE', sql=f'SELECT id FROM {PREFIX}_text WHERE length(trim(review))>2 ORDER BY id LIMIT 3')
add('VOL-TEXT-NATIVE', '10.000 text documents', "lower(review) LIKE '%sách rất%'", table='text_volume', mode='UNSAFE',
    approximate_query={'match_phrase':{'review':'sách rất'}})
add('VOL-TEXT-RUNTIME', '10.000 text documents', "ends_with(lower(trim(review)),'rất tốt')", table='text_volume', mode='UNSAFE')
add('VOL-TEXT-COUNT', '10.000 text documents', table='text_volume', mode='UNSAFE', sql=f'SELECT count(*) FROM {PREFIX}_text_volume WHERE length(trim(review))>8')

def http(url, payload=None, method=None, headers=None, timeout=90):
    if isinstance(payload, dict):
        payload = json.dumps(payload, ensure_ascii=False).encode()
    request = Request(url, data=payload, method=method, headers=headers or {'Content-Type': 'application/json'})
    with urlopen(request, timeout=timeout) as response:
        raw = response.read()
        return json.loads(raw) if raw else {}


def query(version, sql, backend, session=None):
    catalog = backend + '_compare'
    headers = {'X-Trino-User': 'expression-comparison', 'X-Trino-Catalog': catalog, 'X-Trino-Schema': 'default', 'X-Trino-Time-Zone': 'UTC', 'X-Trino-Source': 'expression-comparison', 'Content-Type': 'text/plain; charset=utf-8'}
    if session:
        headers['X-Trino-Session'] = ','.join(key + '=' + str(value).lower() for key, value in session.items())
    started = time.monotonic()
    rows = []
    result = http(f'http://127.0.0.1:{PORTS[version]}/v1/statement', sql.encode(), 'POST', headers)
    while True:
        rows.extend(result.get('data', []))
        if 'error' in result:
            return {'query_id': result['id'], 'error': result['error']['errorName'], 'message': result['error']['message'], 'elapsed_ms': round((time.monotonic() - started) * 1000, 3)}
        if 'nextUri' not in result:
            return {'query_id': result['id'], 'rows': rows, 'elapsed_ms': round((time.monotonic() - started) * 1000, 3), 'statement_stats': result.get('stats', {})}
        if time.monotonic() - started > 180:
            try:
                http(result['nextUri'], method='DELETE', headers=headers)
            finally:
                raise TimeoutError(sql)
        result = http(result['nextUri'], headers=headers)


def float32(value):
    if value is None:
        return None
    return struct.unpack('!f', struct.pack('!f', max(-3.4028234663852886e38, min(3.4028234663852886e38, value))))[0]


def seed(backend, volume_count):
    es = f'http://127.0.0.1:{ES_PORTS[backend]}'
    properties = {'id': {'type': 'integer'}}
    array_types = {'numbers':'integer', 'tiny_numbers':'byte', 'small_numbers':'short', 'big_numbers':'long',
                   'words':'keyword', 'flags':'boolean', 'reals':'float', 'doubles':'double', 'dates':'date',
                   'ignored_words':'keyword', 'normalized_words':'keyword', 'nodv_words':'keyword', 'doconly_words':'keyword',
                   'error_ints':'integer', 'error_longs':'long', 'error_tinys':'byte'}
    scalar_types = {'n':'integer', 'tiny':'byte', 'small':'short', 'big':'long', 'word':'keyword', 'flag':'boolean',
                    'f':'float', 'd':'double', 'dt':'date', 'ignored_word':'keyword', 'normalized_word':'keyword',
                    'nodv_word':'keyword', 'doconly_word':'keyword', 'ReviewComment':'keyword', 'scaled':'scaled_float',
                    'error_int':'integer', 'error_long':'long', 'error_tiny':'byte'}
    for name, kind in {**array_types, **scalar_types}.items():
        properties[name] = {'type': kind}
        if name.startswith('ignored_'):
            properties[name]['ignore_above'] = 3
        if name.startswith('normalized_'):
            properties[name]['normalizer'] = 'lowered'
        if name.startswith('nodv_'):
            properties[name]['doc_values'] = False
        if name.startswith('doconly_'):
            properties[name]['index'] = False
        if kind == 'scaled_float':
            properties[name]['scaling_factor'] = 100
    settings = {'number_of_shards': 1, 'number_of_replicas': 0, 'analysis': {'normalizer': {'lowered': {'type': 'custom', 'filter': ['lowercase']}}}}
    meta = {'trino': {name: {'isArray': True} for name in array_types}}
    n = [-12,-1,0,1,2,5,12,128,11,None,None,None,7,10,20,-5,3,13]
    numbers = [[-12,-1],[-1],[0],[1],[2,11],[5,12],[12,13],[128],[None,12],[None],[],None,[7,17],[10,20],[20],[-5,5],[3],[13,15]]
    words = ['nice','SÁCH đẹp','sá','đóng gói','abc','ab','a_b','a%b','a😀b','ABC','',None,'😀','\ue000','abc\n','  abc\t','\u2009abc\u2009','SÁCH khác']
    word_arrays = [['nice'],['SÁCH đẹp'],['sá'],['đóng gói'],['abc','xyz'],['ab','abc'],['a_b'],['a%b','abc'],[None,'a😀b'],[None],[],None,['😀','\ue000'],['\ue000'],['abc\n','zz'],['  abc\t'],['\u2009abc\u2009'],['SÁCH khác','SÁCH khác']]
    double_values = [-2.5,-0.0,0.0,1.25,1.7976931348623157e308,2.2250738585072014e-308,5e-324,2.5,10.0,None,None,None,7.5,10.5,20.0,-5.0,3.25,13.0]
    dates = ['2024-02-29T12:34:56.123Z','1969-12-31T23:59:59.999Z','2025-01-01T00:00:00Z','1582-10-10T00:00:00Z',
             '2024-01-31T00:00:00Z','2023-12-31T00:00:00Z','2000-02-29T00:00:00Z','2024-03-01T00:00:00Z',
             '2024-02-29T00:00:00Z',None,None,None,'2022-01-01T00:00:00Z','2024-01-01T00:00:00Z','2024-12-31T23:59:59Z','2020-02-29T01:02:03Z','1970-01-01T00:00:00Z','2024-04-30T00:00:00Z']
    docs = []
    for index in range(len(n)):
        value = n[index]
        numeric = numbers[index]
        word = words[index]
        strings = word_arrays[index]
        doc = {'id': index + 1, 'n': value, 'small': value, 'big': value, 'tiny': None if value is None else min(value,120),
               'numbers': numeric, 'small_numbers': numeric, 'big_numbers': numeric,
               'tiny_numbers': None if numeric is None else [None if x is None else min(x,120) for x in numeric],
               'word': word, 'words': strings, 'ReviewComment': word,
               'flag': None if value is None else index % 2 == 0, 'flags': None if numeric is None else [None if x is None else x % 2 == 0 for x in numeric],
               'd': double_values[index], 'f': float32(double_values[index]),
               'doubles': None if numeric is None else ([None] if value is None else [None, double_values[index]]),
               'reals': None if numeric is None else ([None] if value is None else [None,float32(double_values[index])]),
               'dt': dates[index], 'dates': None if dates[index] is None else [None,dates[index]], 'scaled': (index+1)*1.23456,
               'error_int':2147483647, 'error_ints':[2147483647], 'error_long':-9223372036854775808,
               'error_longs':[-9223372036854775808], 'error_tiny':127, 'error_tinys':[127]}
        for stem in ('ignored','normalized','nodv','doconly'):
            doc[stem+'_word'] = word
            doc[stem+'_words'] = strings
        if index == 11:
            doc = {'id': index + 1}
        docs.append(doc)
    def create(name, mapping, values, index_settings=None):
        if not name.startswith(PREFIX + '_'):
            raise ValueError('Refusing to mutate a non-owned index')
        try:
            http(es + '/' + name, method='DELETE')
        except HTTPError as error:
            if error.code != 404:
                raise
        http(es + '/' + name, {'settings': index_settings or {'number_of_shards':1,'number_of_replicas':0}, 'mappings': mapping}, 'PUT')
        lines = []
        for position, doc in enumerate(values):
            lines.extend([json.dumps({'index':{'_index':name,'_id':str(position)}}),json.dumps(doc,ensure_ascii=False,allow_nan=False)])
            if len(lines) >= 1000:
                answer = http(es+'/_bulk', ('\n'.join(lines)+'\n').encode(), 'POST', {'Content-Type':'application/x-ndjson'})
                assert not answer.get('errors'), str(answer)[:500]
                lines = []
        if lines:
            answer = http(es+'/_bulk', ('\n'.join(lines)+'\n').encode(), 'POST', {'Content-Type':'application/x-ndjson'})
            assert not answer.get('errors'), str(answer)[:500]
        http(es+'/'+name+'/_refresh', method='POST')
        return http(es+'/'+name+'/_count')['count']
    main_count = create(PREFIX+'_cases', {'_meta':meta,'properties':properties}, docs, settings)
    volume = [{'id':i,'numbers':[(i%20)*100+j for j in range(64)],'word':['abc plain','đóng gói','nice plain','SÁCH đẹp'][i%4]} for i in range(volume_count)]
    volume_total = create(PREFIX+'_volume', {'_meta':{'trino':{'numbers':{'isArray':True}}},'properties':{'id':{'type':'integer'},'numbers':{'type':'integer'},'word':{'type':'keyword'}}}, volume)
    text_values = ['SÁCH rất tốt','SÁCH, rất tốt','Cuốn sách rất tốt','  ABC  ','abc','a_b','a%b','',None,None,
                   'a '*160+'SÁCH rất tốt','đóng gói','SÁCH đẹp','sách rất','rất sách','X😀Y','abc\n',123,'\u2009abc\u2009','THE']
    text_docs=[]
    for index,value in enumerate(text_values):
        document={'id':index+1,'n':index,'review':value,'with_keyword':value,'foldedreview':value,
                  'reviews':[None,value], 'casereview':value, 'raw_review_comment':value, 'profile':{'ReviewComment':value}}
        if index==5:
            document['reviews']=['sách','rất tốt']
        if index==7:
            document['reviews']=[]
        if index==9:
            document={'id':index+1,'n':index}
        text_docs.append(document)
    text_mapping={'_meta':{'trino':{'reviews':{'isArray':True}}},'properties':{
        'id':{'type':'integer'},'n':{'type':'integer'},'review':{'type':'text'},'reviews':{'type':'text'},'raw_review_comment':{'type':'text'},
        'with_keyword':{'type':'text','fields':{'keyword':{'type':'keyword','ignore_above':256}}},
        'foldedreview':{'type':'text','analyzer':'folded_review'},'casereview':{'type':'text','analyzer':'case_review'},
        'profile':{'properties':{'ReviewComment':{'type':'text'}}}}}
    text_settings={'number_of_shards':1,'number_of_replicas':0,'analysis':{'analyzer':{'folded_review':{
        'type':'custom','tokenizer':'standard','filter':['lowercase','asciifolding','stop']},'case_review':{'type':'custom','tokenizer':'standard','filter':[]}}}}
    text_count=create(PREFIX+'_text',text_mapping,text_docs,text_settings)
    text_volume=[{'id':index,'review':['abc plain','SÁCH rất tốt','đóng gói','SÁCH, rất tốt'][index%4]} for index in range(volume_count)]
    text_volume_count=create(PREFIX+'_text_volume',{'properties':{'id':{'type':'integer'},'review':{'type':'text'}}},text_volume)
    return {'cases':main_count,'volume':volume_total,'schema':properties,'text':text_count,'text_volume':text_volume_count,'text_schema':text_mapping}



def key_rows(rows):
    return Counter(json.dumps(row, sort_keys=True, ensure_ascii=False) for row in rows)


def useful(value):
    if isinstance(value, list):
        return any(useful(item) for item in value)
    if not isinstance(value, dict):
        return False
    for name, item in value.items():
        if name in ('script','range','prefix','regexp','terms','term','exists','match_none','match_phrase','match_phrase_prefix'):
            if isinstance(item, dict) and item and set(item).issubset({'_index'}):
                continue
            return True
        if useful(item):
            return True
    return False


def has_script(value):
    if isinstance(value, dict):
        return 'script' in value or any(has_script(item) for item in value.values())
    return isinstance(value, list) and any(has_script(item) for item in value)


def has_native(value):
    if isinstance(value, list):
        return any(has_native(item) for item in value)
    if not isinstance(value, dict):
        return False
    for name, item in value.items():
        if name == 'script':
            continue
        if name in ('range','prefix','regexp','terms','term','exists','match_none','match_phrase','match_phrase_prefix'):
            if isinstance(item, dict) and item and (set(item).issubset({'_index'}) or all(key.startswith('__trino_text_') for key in item)):
                continue
            return True
        if has_native(item):
            return True
    return False


def classify(plan, actual, events):
    search_events = [event for event in events if '/_search_shards' not in event.get('path', '')]
    queries = [event.get('request',{}).get('query',{}) for event in search_events]
    pushed = any(useful(value) for value in queries)
    scripts = any(has_script(value) for value in queries) or any(event.get('request',{}).get('runtime_mappings') for event in search_events)
    native = any(has_native(value) for value in queries)
    residual = bool(re.search(r'filterPredicate\s*=|\bFilter\[|\bScanFilter\[',plan))
    if not events and ('Values[' in plan or not plan):
        classification = 'FOLD' if 'Values[' in plan else 'NO_SCAN'
    elif residual:
        classification = 'PREFILTER' if pushed else 'LOCAL'
    elif pushed:
        suffix = ' APPROXIMATE' if 'APPROXIMATE' in plan else ' EXACT'
        classification = ('HYBRID' if scripts and native else 'SCRIPT' if scripts else 'NATIVE') + suffix
    else:
        classification = 'LOCAL' if 'error' in actual else 'NO_FILTER'
    return classification, scripts, native, residual, search_events


def run_case(version, backend, item, golden, session, approximate_golden=None):
    catalog = backend+'_compare'
    settings = dict(session)
    if catalog+'.full_text_pushdown_mode' in settings:
        settings[catalog+'.full_text_pushdown_mode']=item['mode']
    if item['off']:
        for name in ('array_script_pushdown_enabled','scalar_script_pushdown_enabled'):
            if catalog+'.'+name in settings:
                settings[catalog+'.'+name] = 'false'
    plan_result = query(version, 'EXPLAIN (TYPE DISTRIBUTED) '+item['sql'], backend, settings)
    plan = '\n'.join(row[0] for row in plan_result.get('rows', []))
    control = f'http://127.0.0.1:19214/__compare/control?variant={version}&backend={backend}'
    http(control+'&reset=1')
    actual = query(version,item['sql'],backend,settings)
    events = http(control)['events']
    info = {}
    try:
        info = http(f'http://127.0.0.1:{PORTS[version]}/v1/query/{actual["query_id"]}', headers={'X-Trino-User':'expression-comparison'})
    except (HTTPError,URLError):
        pass
    classification, scripts, native, residual, search_events = classify(plan, actual, events)
    sql_golden = golden
    oracle_kind = 'SQL'
    if classification.endswith('APPROXIMATE') and approximate_golden is not None:
        golden = approximate_golden
        oracle_kind = 'Elasticsearch analyzer'
    if 'error' in golden:
        correct = actual.get('error') == golden['error']
    elif 'error' in actual:
        correct = False
    elif item['subset']:
        target = int(re.search(r'LIMIT (\d+)\s*$',item['sql']).group(1))
        actual_keys = key_rows(actual['rows'])
        eligible = key_rows(golden['rows'])
        correct = len(actual['rows']) == min(target,len(golden['rows'])) and all(count <= eligible[row] for row,count in actual_keys.items())
    else:
        correct = key_rows(actual['rows']) == key_rows(golden['rows'])
    classification, scripts, native, residual, search_events = classify(plan, actual, events)
    stats = info.get('queryStats', {})
    result = {
        'version':version,'backend':backend,'case_id':item['id'],'group':item['group'],'sql':item['sql'],'mode':item['mode'],'oracle_kind':oracle_kind,
        'sql_equal':actual.get('error')==sql_golden.get('error') if 'error' in actual or 'error' in sql_golden else key_rows(actual.get('rows',[]))==key_rows(sql_golden.get('rows',[])),
        'sql_extra':list((key_rows(actual.get('rows',[]))-key_rows(sql_golden.get('rows',[]))).elements())[:10],
        'sql_missing':list((key_rows(sql_golden.get('rows',[]))-key_rows(actual.get('rows',[]))).elements())[:10], 'transport':'N+S' if scripts and native else 'S' if scripts else 'N' if native else '-',
        'correct':correct,'classification':classification,'residual':residual,'source_hits':sum(event.get('source_hits',0) for event in events),
        'response_bytes':sum(event.get('response_bytes',0) for event in search_events), 'requests':len(search_events), 'events':events,
        'physical_input_positions':stats.get('physicalInputPositions'),'physical_input_bytes':stats.get('physicalInputDataSize'),
        'processed_input_positions':stats.get('processedInputPositions'), 'query_stats':{name:stats.get(name) for name in ('elapsedTime','executionTime','cpuTime','peakUserMemoryReservation')},
        'plan':plan,'plan_error':plan_result.get('error'),'session':settings,'actual':actual,
        'golden_error':golden.get('error'),'golden_rows':len(golden.get('rows',[])),
    }
    if not correct and 'rows' in actual and 'rows' in golden:
        result['extra'] = list((key_rows(actual['rows'])-key_rows(golden['rows'])).elements())[:10]
        result['missing'] = list((key_rows(golden['rows'])-key_rows(actual['rows'])).elements())[:10]
    return result


def wait_and_sessions(versions, backends):
    sessions = {}
    info = {}
    for version in versions:
        deadline = time.monotonic()+240
        while True:
            try:
                ready = http(f'http://127.0.0.1:{PORTS[version]}/v1/info', timeout=10)
                catalogs = query(version,'SHOW CATALOGS','es8')
                if not ready['starting'] and not catalogs.get('error') and all([backend+'_compare'] in catalogs['rows'] for backend in backends):
                    info[version] = ready
                    break
            except (OSError,HTTPError):
                pass
            if time.monotonic()>deadline:
                raise RuntimeError('Server not ready: '+version)
            time.sleep(2)
        for backend in backends:
            values = query(version,'SHOW SESSION',backend)
            assert 'error' not in values, values
            supported = {row[0] for row in values['rows']}
            settings = {}
            for name, value in [('array_script_pushdown_enabled','true'),('scalar_script_pushdown_enabled','true'),('full_text_pushdown_mode','SAFE')]:
                key=backend+'_compare.'+name
                if key in supported:
                    settings[key]=value
            sessions[version+':'+backend]=settings
    return info,sessions


def execute():
    parser=argparse.ArgumentParser()
    parser.add_argument('--seed',action='store_true')
    parser.add_argument('--backends',default='es8,es7')
    parser.add_argument('--versions',default='master,internal,current')
    parser.add_argument('--volume',type=int,default=10000)
    parser.add_argument('--only',default='')
    parser.add_argument('--out',default=str(ROOT/'results'))
    args=parser.parse_args()
    output=Path(args.out)
    output.mkdir(parents=True,exist_ok=True)
    backends=args.backends.split(',')
    versions=args.versions.split(',')
    cases=[case for case in CASES if not args.only or re.search(args.only,case['id'])]
    if args.seed:
        fixtures={backend:seed(backend,args.volume) for backend in backends}
        (output/'fixtures.json').write_text(json.dumps(fixtures,ensure_ascii=False,indent=2))
    else:
        fixtures=json.loads((output/'fixtures.json').read_text())
    info,sessions=wait_and_sessions(versions,backends)
    (output/'server-info.json').write_text(json.dumps({'info':info,'sessions':sessions},indent=2))
    golden={}
    approximate_golden={}
    for backend in backends:
        for number,item in enumerate(cases):
            sql=item['reference'] or item['sql']
            if (backend,sql) not in golden:
                golden[backend,sql]=query('current',sql,backend,{'allow_pushdown_into_connectors':'false'})
            if item.get('approximate_query'):
                response=http(f'http://127.0.0.1:{ES_PORTS[backend]}/{PREFIX}_{item["table"]}/_search',
                              {'query':item['approximate_query'],'size':10000,'track_total_hits':True,'_source':['id']},'POST')
                rows=[[hit['_source']['id']] for hit in response.get('hits',{}).get('hits',[])]
                if re.match(r'SELECT count\(\*\)',item['sql'],re.IGNORECASE):
                    rows=[[response['hits']['total']['value']]]
                approximate_golden[backend,item['id']]={'rows':rows}
            if number%50==0:
                print('ORACLE',backend,number,'/',len(cases),flush=True)
    (output/'oracle.json').write_text(json.dumps([{'backend':backend,'sql':sql,**result} for (backend,sql),result in golden.items()],ensure_ascii=False))
    def run_version(version):
        records=[]
        path=output/(version+'.jsonl')
        with path.open('w') as stream:
            for backend in backends:
                for number,item in enumerate(cases):
                    try:
                        record=run_case(version,backend,item,golden[backend,item['reference'] or item['sql']],sessions[version+':'+backend],approximate_golden.get((backend,item['id'])))
                    except Exception as error:
                        record={'version':version,'backend':backend,'case_id':item['id'],'sql':item['sql'],'group':item['group'],'correct':False,'harness_error':repr(error)}
                    records.append(record)
                    stream.write(json.dumps(record,ensure_ascii=False)+'\n')
                    stream.flush()
                    if not record['correct'] or number%25==0:
                        print(version,backend,item['id'],record['correct'],record.get('classification'),record.get('actual',{}).get('error',record.get('harness_error','')),flush=True)
        return records
    records=[]
    with ThreadPoolExecutor(max_workers=len(versions)) as executor:
        jobs=[executor.submit(run_version,version) for version in versions]
        for job in as_completed(jobs):
            records.extend(job.result())
    document={'generated_at':datetime.now(timezone.utc).isoformat(),'deployment':json.loads((ROOT/'deployment.json').read_text()),
              'fixtures':fixtures,'cases':cases,'results':records,'server_info':info,'sessions':sessions,
              'approximate_oracles':[{'backend':b,'case_id':c,**value} for (b,c),value in approximate_golden.items()]}
    with gzip.open(output/'comparison.json.gz','wt',encoding='utf-8') as stream:
        json.dump(document,stream,ensure_ascii=False)
    summary={version:{'total':sum(r['version']==version for r in records),'correct':sum(r['version']==version and r['correct'] for r in records),
                     'modes':dict(Counter(r.get('classification','HARNESS_ERROR') for r in records if r['version']==version))} for version in versions}
    (output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    print(json.dumps(summary,ensure_ascii=False,indent=2),flush=True)
    candidate_failures=[record for record in records if record['version']=='current' and not record['correct']]
    if candidate_failures:
        raise SystemExit(1)


def reclassify(directory):
    """Recompute derived labels only; preserve SQL, rows, errors, plans and captured requests."""
    path = Path(directory)
    with gzip.open(path / 'comparison.json.gz', 'rt', encoding='utf-8') as stream:
        document = json.load(stream)
    records = document['results']
    for record in records:
        if 'harness_error' in record:
            continue
        mode, scripts, native, residual, events = classify(record['plan'], record['actual'], record['events'])
        record.update(classification=mode, transport='N+S' if scripts and native else 'S' if scripts else 'N' if native else '-',
                      residual=residual, requests=len(events), response_bytes=sum(event.get('response_bytes', 0) for event in events))
    document['classification_note'] = 'Derived from captured plans/DSL; an empty match_none body is a real native predicate. Metadata search_shards calls excluded.'
    with gzip.open(path / 'comparison.json.gz', 'wt', encoding='utf-8') as stream:
        json.dump(document, stream, ensure_ascii=False)
    versions = sorted({record['version'] for record in records})
    for version in versions:
        with (path / (version + '.jsonl')).open('w') as stream:
            for record in records:
                if record['version'] == version:
                    stream.write(json.dumps(record, ensure_ascii=False) + '\n')
    summary = {version: {'total': sum(r['version'] == version for r in records),
                         'correct': sum(r['version'] == version and r['correct'] for r in records),
                         'modes': dict(Counter(r.get('classification', 'HARNESS_ERROR') for r in records if r['version'] == version))}
               for version in versions}
    (path / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def main():
    if len(sys.argv) == 3 and sys.argv[1] == '--reclassify':
        reclassify(sys.argv[2])
        return
    options = argparse.ArgumentParser(add_help=False)
    options.add_argument('--backends', default='es8,es7')
    options.add_argument('--out', default=str(ROOT/'results'))
    settings, _ = options.parse_known_args()
    saved = {}
    output = Path(settings.out)
    output.mkdir(parents=True, exist_ok=True)
    try:
        for backend in settings.backends.split(','):
            endpoint = f'http://127.0.0.1:{ES_PORTS[backend]}'
            original = http(endpoint+'/_cluster/settings?flat_settings=true')
            saved[backend] = original.get('transient', {}).get('script.max_compilations_rate')
            http(endpoint+'/_cluster/settings', {'transient': {'script.max_compilations_rate':'10000/5m'}}, 'PUT')
        (output/'compilation-settings-before.json').write_text(json.dumps(saved, indent=2))
        execute()
    finally:
        for backend, value in saved.items():
            http(f'http://127.0.0.1:{ES_PORTS[backend]}/_cluster/settings', {'transient': {'script.max_compilations_rate':value}}, 'PUT')
        (output/'compilation-settings-restored.json').write_text(json.dumps(saved, indent=2))


if __name__=='__main__':
    main()
