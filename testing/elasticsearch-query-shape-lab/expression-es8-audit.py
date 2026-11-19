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

"""Audit recorded ES8 requests and isolate slower, same-result comparisons."""
import argparse
from collections import Counter
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import re
from statistics import median


def load(path):
    with gzip.open(path, 'rt') as stream:
        return json.load(stream)


def save(path, value):
    with gzip.open(path, 'wt', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False)


def leaves(value):
    result = []
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ('term', 'terms', 'range', 'prefix', 'regexp', 'exists', 'match_phrase', 'match_phrase_prefix', 'script'):
                result.append((key, child))
                if key == 'script':
                    continue
            result.extend(leaves(child))
    elif isinstance(value, list):
        for child in value:
            result.extend(leaves(child))
    return result


def mechanism(body):
    nodes = leaves(body.get('query', {}))
    names = {name for name, _ in nodes}
    if body.get('runtime_mappings'):
        return 'runtime_source', 'Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn'
    if 'script' in names:
        native = names - {'script'}
        if native:
            return 'hybrid', 'Hybrid: native + script đọc doc values'
        return 'doc_values_script', 'Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính'
    if 'regexp' in names:
        return 'indexed_regexp', 'Regexp/automaton trên từ điển term của keyword index'
    if 'prefix' in names:
        return 'indexed_prefix', 'Prefix trên từ điển term của keyword index'
    if names & {'match_phrase', 'match_phrase_prefix'}:
        return 'indexed_phrase', 'Phrase dùng inverted index/positions sau analyzer'
    if names & {'term', 'terms'}:
        return 'indexed_numeric_term', 'Native term/terms trên field số; kiểm tra Lucene point/index qua profile'
    if 'range' in names:
        return 'indexed_numeric_range', 'Native range trên field số; không có Painless'
    return 'match_all', 'Không có prefilter hữu ích; scan/fetch để Trino xử lý'


def compact(body):
    if isinstance(body, dict):
        return {key: ('[Painless sha256=' + hashlib.sha256(value.encode()).hexdigest()[:16] + ']'
                      if key == 'source' and isinstance(value, str) else compact(value)) for key, value in body.items()}
    if isinstance(body, list):
        return [compact(value) for value in body]
    return body


def search_body(record):
    events = [event for event in record['events'] if '_search_shards' not in event['path']
              and '/scroll' not in event['path'] and event.get('request', {}).get('query')]
    return events[0]['request']


def node(snapshot):
    return next(iter(snapshot['elasticsearch']['es8']['nodes']['nodes'].values()))


def node_delta(before, after):
    a, b = node(before), node(after)
    assert a['name'] == b['name']
    result = {'elapsed_ms': b['timestamp'] - a['timestamp'],
              'process_cpu_ms': b['process']['cpu']['total_in_millis'] - a['process']['cpu']['total_in_millis']}
    for name in ('query_total', 'query_time_in_millis', 'fetch_total', 'fetch_time_in_millis'):
        result[name] = b['indices']['search'][name] - a['indices']['search'][name]
    for pool in ('search', 'search_coordination'):
        result[pool + '_rejected'] = b['thread_pool'].get(pool, {}).get('rejected', 0) - a['thread_pool'].get(pool, {}).get('rejected', 0)
        result[pool + '_queue_before'] = a['thread_pool'].get(pool, {}).get('queue')
        result[pool + '_queue_after'] = b['thread_pool'].get(pool, {}).get('queue')
    for collector in ('young', 'old'):
        ca = a['jvm']['gc']['collectors'].get(collector, {})
        cb = b['jvm']['gc']['collectors'].get(collector, {})
        result[collector + '_gc_ms'] = cb.get('collection_time_in_millis', 0) - ca.get('collection_time_in_millis', 0)
    return result


def prepare(source, output):
    data = load(source)
    summaries = [row for row in data['summaries'] if row['backend'] == 'es8']
    records = [row for row in data['results'] if row['backend'] == 'es8' and row['version'] == 'current'
               and row['shards'] == 1 and row['documents'] == 100000 and row['repetition'] == 1]
    selected = []
    for record in sorted(records, key=lambda row: row['feature']):
        body = search_body(record)
        kind, description = mechanism(body)
        selected.append({'feature': record['feature'], 'case_id': record['case_id'], 'sql': record['sql'],
                         'table': next(item['table'] for item in data['manifest']['features'] if item['feature'] == record['feature']),
                         'classification': record['classification'], 'kind': kind, 'mechanism': description,
                         'body': body, 'compact_query': compact(body['query']),
                         'expected_candidates': record['actual_summary']['first_rows'][0][0]
                         if record['feature'] == 38 else record['source_hits']})
    history = []
    for key, status in data['files'].items():
        if key.startswith('es8-') and key.endswith('-done.json'):
            history.append(dict(backend='es8', shards=status['shards'], documents=status['documents'],
                                **node_delta(status['before'], status['after'])))
    packet = {'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'features': data['manifest']['features'], 'summaries': summaries, 'requests': selected,
              'templates': data['files']['es8-templates.json'], 'historical_node_deltas': history,
              'initial_es8': data['manifest']['before']['elasticsearch']['es8'],
              'final_es8': data['files']['final-verification.json']['elasticsearch']['es8']}
    assert len(selected) == 40 and len(summaries) == 840
    save(output / 'expression-es8-audit-input.json.gz', packet)
    print(json.dumps({'ES8_requests': len(selected), 'ES8_summary_groups': len(summaries),
                      'mechanisms': dict(Counter(item['kind'] for item in selected))}, indent=2))


def md(value):
    return str(value).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('|', '&#124;').replace('\n', ' ')


def profile_nodes(value):
    if isinstance(value, dict):
        if 'type' in value and 'breakdown' in value:
            yield value
        for child in value.values():
            yield from profile_nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from profile_nodes(child)


def render_query_audit(packet, output):
    diagnostics = load(output / 'expression-es8-diagnostics.json.gz')
    profiles = {item['feature']: item for item in diagnostics['profiles'] if item['feature'] is not None}
    vocab = diagnostics['files']['keyword-vocabulary.json']['aggregations']['words']['buckets']
    lines = ['# ES8: dùng index, regexp, script và tác động tải', '',
             'Phạm vi: phân tích lại bằng chứng ES8 của benchmark ngày 18/11/2026 và chạy chẩn đoán mới **chỉ ES8**. '
             'Giữ nguyên ba Trino; không chạy lại ES7. [Bảng các ca chậm riêng](EXPRESSION-ES8-SLOW-CASES.md).', '',
             '## Kết luận đã kiểm chứng', '',
             '- Không chia đơn giản thành “term dùng index, regex scan document”. Phải xét field, mapping và cây Lucene thực tế.',
             '- Numeric `term`/`terms` đã ra `PointRangeQuery`/`PointInSetQuery`. Đây là truy vấn point index của số, khác keyword `TermQuery` dùng term/postings.',
             '- Các numeric range hiện ra `IndexOrDocValuesQuery`. Wrapper này có thể chọn index hoặc doc values theo kế hoạch Lucene; profile hiện tại không đủ khẳng định mọi range luôn đi BKD hoặc mọi doc đều bị scan.',
             '- Keyword `prefix` và `regexp` hiện ra `MultiTermQueryConstantScoreBlendedWrapper`, có các `TermQuery` con. Regexp duyệt/khớp từ điển term bằng automaton rồi lấy doc từ postings; không chạy Painless regex trên từng `_source`.',
             '- `term` trên `__trino_text_<hash>` có `runtime_mappings` ra **`BooleanScriptFieldTermQuery`**. Field đó không được index sẵn; vẫn tính script lúc query, không phải native term lookup rẻ.',
             '- `doc[... ]` đọc doc values nhanh hơn đọc source, nhưng không tạo index cho biểu thức như modulo/date_diff. Profile script thuần báo 100.000 lần `match` trên 100.000 document.', '',
             'Lucene mô tả regexp là automaton duyệt từ điển term và cảnh báo pattern bắt đầu `.*` có thể đắt. '
             '[Lucene 9.8 RegexpQuery](https://lucene.apache.org/core/9_8_0/core/org/apache/lucene/search/RegexpQuery.html). '
             'ES phân biệt script cần linear scan với regexp có chi phí upfront cao; '
             '[ES8.11 Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/query-dsl.html), '
             '[ES8.11 regexp](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/query-dsl-regexp-query.html).', '',
             '## Toàn bộ 40 SQL: DSL thật và lớp Lucene thật', '',
             'Profile chẩn đoán dùng 1 shard / 100.000 document, `size=0`, `track_total_hits=true`, cùng predicate/runtime definitions từ request đã ghi. '
             'Mục đích là nhận dạng đường thực thi và số lần `match`, không thay latency của SQL trả hết dữ liệu. '
             'Profile có overhead và không đo đầy đủ network/queue/coordinator; '
             '[Profile API 8.11](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/search-profile.html).', '',
             '| SQL ID / SQL | DSL rút gọn | Cơ chế | Lớp Lucene trong profile | Script match calls |',
             '| --- | --- | --- | --- | ---: |']
    csv_rows = []
    for recorded in packet['requests']:
        item = dict(recorded, mechanism=mechanism(recorded['body'])[1])
        nodes = list(profile_nodes(profiles[item['feature']]['response']['profile']))
        types = sorted({node['type'] for node in nodes})
        calls = sum(node['breakdown'].get('match_count', 0) for node in nodes if 'Script' in node['type'])
        query = json.dumps(item['compact_query'], ensure_ascii=False, separators=(',', ':'))
        query = re.sub(r'__trino_text_[0-9a-f]{64}', '__trino_text_HASH', query)
        if len(query) > 550:
            query = query[:550] + '… (full body trong raw evidence)'
        lines.append(f"| `{item['case_id']}`<br><code>{md(item['sql'])}</code> | <code>{md(query)}</code> | {md(item['mechanism'])} | {', '.join(types)} | {calls:,} |")
        csv_rows.append({'feature': item['feature'], 'case_id': item['case_id'], 'sql': item['sql'], 'mechanism': item['kind'],
                         'lucene_types': ';'.join(types), 'script_match_calls': calls,
                         'query': json.dumps(item['compact_query'], ensure_ascii=False)})
    with (output / 'expression-es8-query-paths.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, list(csv_rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(csv_rows)
    lines.extend(['', '### Regexp trong chính bộ test này', '',
                  f"`words` là keyword có index và chỉ có **{len(vocab)} term khác nhau**. Dữ liệu 100.000 document được lặp từ 18 document biên, nên tăng document không tăng độ đa dạng từ điển term. "
                  'Bộ đo này không mô phỏng hàng triệu giá trị keyword khác nhau.', '',
                  '- `A-TXT05`: SQL `LIKE ... ESCAPE` được dịch thành `regexp words = ".*a_.*"`.',
                  '- `A-TXT25`: SQL `regexp_like(x,\'abc\')` thành `regexp words = ".*(abc).*"`.',
                  '- Cả hai thiếu prefix cố định nên có nguy cơ phải duyệt nhiều term khi cardinality cao. Trên bộ từ điển 17 term hiện tại chúng rất nhỏ; không thể suy regexp tương tự luôn rẻ trong production.',
                  '- Không có regexp trong bảy ca chậm hơn internal tại 8 shard / 100.000 document. Các ca chậm ở cấu hình khác đã giữ đầy đủ trong bảng riêng, không loại các regression regexp nếu có.', '',
                  '### Native guard giảm công việc script thế nào?', '',
                  '| SQL ID | Predicate | Script match calls, 1 shard / 100k |',
                  '| --- | --- | ---: |',
                  '| `A-MOD-TINYINT` | Modulo thuần | 100.000 |',
                  '| `A-I09` | Native range OR modulo | 50.000 |',
                  '| `A-I10` | Native range AND modulo, script kiểm tra cả lambda | 50.000 |',
                  '| `S-I09` | Scalar range OR modulo | 72.222 |',
                  '| `S-TEXT02` | Runtime source `length(trim(review))>2` | 100.000 |', '',
                  'Đây là counter `match_count` của node script trong Profile API của request đếm. Không phải số source document gửi về, không phải số phần tử array và không khẳng định toàn bộ các trang Scroll chạy đúng số lần đó. '
                  'OR ở các ví dụ này đã tránh gọi script trên các doc có nhánh native đáp ứng; không suy mọi tổ hợp OR sẽ có cùng tối ưu.', '',
                  'Các runtime field không có index sẵn; chi phí phụ thuộc số document và phép tính. Script đọc `_source` còn phải đọc/giải mã nguồn thay vì chỉ lấy giá trị cột. '
                  '[Runtime fields 8.11](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/runtime.html), '
                  '[Doc values và source trong script](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/modules-scripting-fields.html).', '',
                  '## Các query có làm ES làm việc nhiều/chậm hơn không?', '',
                  '**Có những trường hợp phần việc ở ES tăng rõ dù Trino nhận ít document hơn. Nhưng điều đó không tự chứng minh toàn cluster bị quá tải hoặc query của người khác bị chậm.**', '',
                  'Ví dụ từ benchmark gốc, 8 shard / 100.000 document. `ES took` dưới đây là median tổng took các request, có overlap giữa shard; không phải CPU hay wall time của cả query:', '',
                  '| SQL | Doc internal → mới | Median tổng ES took internal → mới (ms) | Wall SQL internal → mới (ms) |',
                  '| --- | ---: | ---: | ---: |'])
    summary = {(row['shards'], row['documents'], row['feature'], row['version']): row for row in packet['summaries']}
    for feature in (1, 2, 4, 29, 32, 34, 38):
        old, new = (summary[8, 100000, feature, version] for version in ('internal', 'current'))
        lines.append(f"| `{old['case_id']}` | {old['source_hits_median']:,} → {new['source_hits_median']:,} | {old['es_took_sum_median_ms']:,} → {new['es_took_sum_median_ms']:,} | {old['median_ms']:.1f} → {new['median_ms']:.1f} |")
    lines.extend(['', '### Tải node trong benchmark gốc', '',
                  'Counter trước/sau mỗi cấu hình bao gồm ba Trino, warm-up, EXPLAIN và công việc nền trên node. Không quy riêng toàn bộ CPU đó cho bản mới. '
                  'Queue chỉ có snapshot ở đầu/cuối, không có chuỗi thời gian đỉnh tải.', '',
                  '| Shard / doc | Khoảng đo (s) | CPU node tăng (s) | Search rejected Δ | Old GC Δ ms | Young GC Δ ms |',
                  '| --- | ---: | ---: | ---: | ---: | ---: |'])
    for value in packet['historical_node_deltas']:
        lines.append(f"| {value['shards']} / {value['documents']:,} | {value['elapsed_ms']/1000:.1f} | {value['process_cpu_ms']/1000:.1f} | {value['search_rejected']} | {value['old_gc_ms']} | {value['young_gc_ms']} |")
    lines.extend(['', 'Không có search/search_coordination rejection mới và không có old GC trong các khoảng benchmark đã lưu; các queue snapshot đầu/cuối đều 0. '
                  'Đây là không ghi nhận các dấu hiệu đó trong mẫu, không phải bằng chứng “ES không chịu tải”. Benchmark gốc không có canary đồng thời nên không kết luận được độ trễ query khác lúc đó.', '',
                  '### Canary đồng thời trong lượt chẩn đoán ES8 mới', '',
                  'Một process Python độc lập gửi term query rất nhỏ vào index canary riêng mỗi 250 ms. Request cache tắt. '
                  'Process riêng tránh việc parse JSON SQL của harness giữ GIL làm giả độ trễ probe. '
                  'Chỉ tổng hợp các khoảng đo thật, loại warm-up/profile/seeding và sample qua ranh giới phase. '
                  'p95 là percentile mô tả của probe, không phải p95 SQL của ba repetition.', '',
                  '| Trạng thái | Số probe | Median ms | p95 ms | Max ms |',
                  '| --- | ---: | ---: | ---: | ---: |'])
    grouped = {}
    for value in diagnostics['canary']:
        if value['boundary'] or value.get('error'):
            continue
        parts = value['phase'].split(':')
        name = value['phase'] if value['phase'] in ('idle_before', 'idle_after') else parts[0] if len(parts) == 3 and parts[-1] == 'measure' else None
        if name:
            grouped.setdefault(name, []).append(value['elapsed_ms'])
    for name in ('idle_before', 'master', 'internal', 'current', 'idle_after'):
        values = sorted(grouped[name])
        lines.append(f"| {name} | {len(values)} | {median(values):.3f} | {values[math.ceil(.95*len(values))-1]:.3f} | {max(values):.3f} |")
    lines.extend(['', f"Tổng {len(diagnostics['canary'])} probe, {sum(bool(value.get('error')) for value in diagnostics['canary'])} lỗi. "
                  'Trong lượt chẩn đoán này không thấy canary bị tăng latency lớn, kéo dài riêng khi chạy bản mới; cả ba tải có mức p95 gần nhau. '
                  'Có spike riêng lẻ và không có cam kết về SLA/workload khác: canary rất nhẹ, ES8 không có quota CPU Docker, host có 48 logical CPU, phép đo không phải stress test nhiều query đồng thời.', '',
                  '### CPU container đo trực tiếp', '',
                  'Node stats `process.cpu` được cache theo chu kỳ; nhiều query dưới một giây có delta 0 dù đã chạy. Các delta đó không được coi là CPU bằng 0. '
                  'Pass bổ sung dùng cgroup v2 `cpu.stat/usage_usec` đọc trực tiếp quanh lần gửi SQL đến nhận hết kết quả, giữ một warm-up + ba repetition trên ba Trino. '
                  'Đây là CPU toàn container ES8 trong khoảng đó, cộng tất cả threads nên có thể lớn hơn wall time; có thể gồm GC/công việc nền, không phải profiler CPU tuyệt đối của riêng request. Không chạy canary trong pass CPU.', ''])
    if diagnostics.get('cpu_results'):
        lines.extend(['| SQL ID | CPU ES8 master (ms) | CPU ES8 internal (ms) | CPU ES8 mới (ms) |',
                      '| --- | ---: | ---: | ---: |'])
        for feature in sorted({row['feature'] for row in diagnostics['cpu_results']}):
            values = [median(row['actual']['es_container_cpu_ms'] for row in diagnostics['cpu_results']
                             if row['feature'] == feature and row['version'] == version and not row['warmup'])
                      for version in ('master', 'internal', 'current')]
            case_id = next(item['case_id'] for item in packet['requests'] if item['feature'] == feature)
            lines.append(f"| `{case_id}` | " + ' | '.join(f'{value:.1f}' for value in values) + ' |')
    lines.extend(['', '## Giới hạn và bằng chứng', '',
                  '- Profile diagnostic đã chạy 43 request trên ES8: 40 predicate gốc + 3 control (keyword term, regexp rộng, match_all). Profile không được trộn vào latency benchmark.',
                  '- Pass node/canary chạy 14 SQL tập trung × 3 Trino × 4 lượt = 168 SQL. Pass CPU cgroup chạy riêng cùng 168 SQL để tránh cache của bộ đếm OS. Tất cả đạt oracle; không có query ES7 trong các pass mới.',
                  '- Các index chẩn đoán/canary dùng namespace riêng và đã thu hồi. Không đổi binary, không đổi `search.allow_expensive_queries`, compilation rate hoặc cache cluster.',
                  '- [Raw profile, canary, node/cgroup counters, SQL và source harness](expression-es8-diagnostics.json.gz).',
                  '- [Input ES8 trích từ benchmark gốc, chứa full DSL/script](expression-es8-audit-input.json.gz).',
                  '- [CSV phân loại 40 SQL](expression-es8-query-paths.csv), [CSV các ca chậm](expression-es8-slow-cases.csv).',
                  '- [Harness phân tích](expression-es8-audit.py), [harness profile/canary](expression-es8-profile.py), [harness CPU](expression-es8-cpu.py).', ''])
    (output / 'EXPRESSION-ES8-QUERY-AUDIT.md').write_text('\n'.join(lines))


def render(packet, output):
    summary = {(row['shards'], row['documents'], row['feature'], row['version']): row for row in packet['summaries']}
    requests = {row['feature']: dict(row, mechanism=mechanism(row['body'])[1]) for row in packet['requests']}
    slow = []
    for key, new in summary.items():
        if new['version'] != 'current':
            continue
        master = summary[*key[:3], 'master']
        internal = summary[*key[:3], 'internal']
        if not all(row['correct'] for row in (master, internal, new)):
            continue
        if len({row['result_sha256'] for row in (master, internal, new)}) != 1:
            continue
        if new['median_ms'] <= internal['median_ms'] and new['median_ms'] <= master['median_ms']:
            continue
        slow.append({'feature': new['feature'], 'case_id': new['case_id'], 'shards': new['shards'],
                     'documents': new['documents'], 'sql': new['sql'], 'kind': requests[new['feature']]['kind'],
                     'mechanism': requests[new['feature']]['mechanism'],
                     'master_ms': master['median_ms'], 'internal_ms': internal['median_ms'], 'new_ms': new['median_ms'],
                     'internal_min_ms': internal['min_ms'], 'internal_max_ms': internal['max_ms'],
                     'new_min_ms': new['min_ms'], 'new_max_ms': new['max_ms'],
                     'delta_internal_pct': 100 * (new['median_ms'] / internal['median_ms'] - 1),
                     'delta_master_pct': 100 * (new['median_ms'] / master['median_ms'] - 1),
                     'separated_internal_ranges': new['min_ms'] > internal['max_ms'],
                     'internal_range_relation': ('all_new_samples_slower' if new['min_ms'] > internal['max_ms']
                                                 else 'all_new_samples_faster' if new['max_ms'] < internal['min_ms'] else 'overlap'),
                     'internal_docs': internal['source_hits_median'], 'new_docs': new['source_hits_median'],
                     'internal_es_took_sum_ms': internal['es_took_sum_median_ms'],
                     'new_es_took_sum_ms': new['es_took_sum_median_ms'],
                     'internal_trino_cpu_ms': internal['cpu_median_ms'], 'new_trino_cpu_ms': new['cpu_median_ms']})
    slow.sort(key=lambda row: row['delta_internal_pct'], reverse=True)
    with (output / 'expression-es8-slow-cases.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, list(slow[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(slow)
    lines = ['# Các trường hợp bản mới chậm hơn — chỉ Elasticsearch 8', '',
             'Phân tích lại bằng chứng ngày 18/11/2026; không chạy lại ES7. Ba Trino và SQL giữ nguyên như [benchmark gốc](EXPRESSION-PERFORMANCE.md).', '',
             'Chỉ so các lượt đúng và có cùng SHA-256 tập kết quả giữa cả ba bản. Ghi mọi cấu hình có median bản mới cao hơn **ít nhất một** baseline. '
             'Số dương ở cột Δ là chậm hơn; số âm là nhanh hơn baseline tương ứng. Không dùng ngưỡng tùy ý để loại ca nhỏ. '
             'Cột so khoảng đo có ba trạng thái: “Cả 3 lượt mới chậm hơn” khi min mới > max internal; '
             '“Khoảng đo giao nhau” khi hai khoảng min–max chạm hoặc giao nhau; '
             '“Cả 3 lượt mới nhanh hơn” khi max mới < min internal (có thể xuất hiện nếu chỉ chậm hơn master). '
             'Đây là so các mẫu đã đo, không phải kiểm định ý nghĩa thống kê hay bảo đảm cho mọi lần chạy sau.', '',
             f"Có **{len(slow)} ca**: {sum(row['delta_internal_pct'] > 0 for row in slow)} chậm hơn internal, "
             f"{sum(row['delta_master_pct'] > 0 for row in slow)} chậm hơn master. "
             'Mẫu gồm 40 SQL × 7 cấu hình = 280 điểm ES8; 21 điểm native approximate khác SQL bị loại khỏi tỷ lệ tốc độ.', '',
             '## Bảy ca chậm hơn internal tại 8 shard / 100.000 document', '',
             '| SQL ID / đường thực thi | master (ms) | internal (ms) | Bản mới (ms) | Δ internal | Doc internal → mới |',
             '| --- | ---: | ---: | ---: | ---: | ---: |']
    for row in sorted(slow, key=lambda item: item['feature']):
        if (row['shards'], row['documents']) == (8, 100000) and row['delta_internal_pct'] > 0:
            lines.append(f"| `{row['case_id']}` · {md(row['mechanism'])} | {row['master_ms']:.1f} | {row['internal_ms']:.1f} | {row['new_ms']:.1f} | +{row['delta_internal_pct']:.1f}% | {row['internal_docs']:,} → {row['new_docs']:,} |")
    lines.extend(['', '## Tất cả cấu hình chậm — có đầy đủ SQL', '',
                  '| Shard / doc; SQL | Đường thực thi | master ms | Internal ms [min–max] | Mới ms [min–max] | Δ internal / master | So khoảng 3 lượt với internal | Doc internal → mới |',
                  '| --- | --- | ---: | ---: | ---: | ---: | --- | ---: |'])
    for row in slow:
        relation = {'all_new_samples_slower': 'Cả 3 lượt mới chậm hơn', 'all_new_samples_faster': 'Cả 3 lượt mới nhanh hơn',
                    'overlap': 'Khoảng đo giao nhau'}[row['internal_range_relation']]
        lines.append(f"| {row['shards']} / {row['documents']:,}; `{row['case_id']}`<br><code>{md(row['sql'])}</code> | {md(row['mechanism'])} | "
                     f"{row['master_ms']:.1f} | {row['internal_ms']:.1f} [{row['internal_min_ms']:.1f}–{row['internal_max_ms']:.1f}] | {row['new_ms']:.1f} [{row['new_min_ms']:.1f}–{row['new_max_ms']:.1f}] | "
                     f"{row['delta_internal_pct']:+.1f}% / {row['delta_master_pct']:+.1f}% | {relation} | {row['internal_docs']:,} → {row['new_docs']:,} |")
    lines.extend(['', 'Không có regexp trong bảy ca chậm hơn internal ở mức lớn nhất. Các ca native chậm tại mức đó dùng range/OR-range; '
                  'các ca khác dùng script hoặc runtime `_source`. Không suy mọi native query là rẻ, cũng không suy mọi regexp là scan `_source`.', '',
                  '- [CSV chậm: kèm CPU Trino và tổng ES took](expression-es8-slow-cases.csv).',
                  '- [Phân loại index, term, regexp và tải ES](EXPRESSION-ES8-QUERY-AUDIT.md).', ''])
    (output / 'EXPRESSION-ES8-SLOW-CASES.md').write_text('\n'.join(lines))
    print(json.dumps({'ES8_slow_either': len(slow), 'ES8_slow_internal': sum(row['delta_internal_pct'] > 0 for row in slow),
                      'ES8_slow_master': sum(row['delta_master_pct'] > 0 for row in slow)}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--out', type=Path, default=Path(__file__).parent)
    args = parser.parse_args()
    if args.prepare:
        prepare(args.out / 'expression-performance.json.gz', args.out)
    else:
        packet = load(args.out / 'expression-es8-audit-input.json.gz')
        render(packet, args.out)
        if (args.out / 'expression-es8-diagnostics.json.gz').exists():
            render_query_audit(packet, args.out)


if __name__ == '__main__':
    main()
