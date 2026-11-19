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

"""Render sequential scaling evidence; never infer latency from source-hit reduction."""
import argparse
from collections import Counter, defaultdict
import csv
import gzip
import hashlib
import json
from pathlib import Path
import re
from statistics import median

VERSIONS = ('master', 'internal', 'current')
LABELS = {'master': 'Trino master', 'internal': 'Trino internal/production', 'current': 'Trino branch mới'}
AXES = [('Tăng shard, giữ 10.000 document', [(1, 10000), (4, 10000), (8, 10000)]),
        ('Tăng document, giữ 1 shard', [(1, 10000), (1, 50000), (1, 100000)]),
        ('Tăng cả shard và document', [(1, 10000), (4, 50000), (8, 100000)])]


def duration_ms(value):
    if value is None:
        return None
    match = re.fullmatch(r'([\d.]+)(ns|us|µs|ms|s|m|h|d)', value)
    if not match:
        raise ValueError('Unknown Trino duration: ' + value)
    scale = {'ns': 1e-6, 'us': 1e-3, 'µs': 1e-3, 'ms': 1, 's': 1000,
             'm': 60000, 'h': 3600000, 'd': 86400000}
    return float(match[1]) * scale[match[2]]


def markdown(value):
    return str(value).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('|', '&#124;').replace('\n', ' ')


def summarize(records):
    measured = [record for record in records if not record['warmup']]
    elapsed = [record['actual']['elapsed_ms'] for record in measured]
    # Recent QueryInfo exposes totalCpuTime rather than cpuTime. The statement
    # protocol independently includes cpuTimeMillis in every completed result;
    # use that recorded execution CPU when the older harness field is absent.
    cpu = [duration_ms(record['query_stats']['cpuTime']) if record['query_stats'].get('cpuTime') is not None
           else record['actual'].get('statement_stats', {}).get('cpuTimeMillis') for record in measured]
    execution = [duration_ms(record['query_stats']['executionTime']) for record in measured]
    planning = [duration_ms(record['query_stats']['planningTime']) for record in measured]
    result = {key: measured[0][key] for key in ('backend', 'feature', 'case_id', 'shards', 'documents', 'version', 'sql', 'mode', 'session')}
    result.update(samples=len(measured), total_samples=len(records), correct=all(record['correct'] for record in records),
                  sql_equal=all(record['sql_equal'] for record in records),
                  classifications=dict(Counter(record['classification'] for record in records)),
                  median_ms=median(elapsed), min_ms=min(elapsed), max_ms=max(elapsed),
                  cpu_median_ms=median(cpu) if all(value is not None for value in cpu) else None,
                  execution_median_ms=median(execution) if all(value is not None for value in execution) else None,
                  planning_median_ms=median(planning) if all(value is not None for value in planning) else None,
                  source_hits_median=median(record['source_hits'] for record in measured),
                  response_bytes_median=median(record['response_bytes'] for record in measured),
                  request_bytes_median=median(record['request_bytes'] for record in measured),
                  es_took_sum_median_ms=median(record['es_took_sum_ms'] for record in measured),
                  search_requests_median=median(record['search_requests'] for record in measured),
                  output_rows=measured[0]['actual_summary']['rows'],
                  result_sha256=measured[0]['actual_summary']['sha256'],
                  output_example=measured[0]['actual_summary']['first_rows'],
                  sql_output_rows=measured[0]['sql_expected']['rows'])
    result['count_value'] = (result['output_example'][0][0]
                             if re.match(r'^SELECT count\(\*\)', result['sql'], re.I) and result['output_example'] else None)
    return result


def comparison_cell(result):
    status = 'ĐÚNG' if result['correct'] else '**SAI**'
    mode = ' / '.join(result['classifications'])
    cpu = '—' if result['cpu_median_ms'] is None else f"{result['cpu_median_ms']:.1f}"
    delta = ' · **khác SQL exact**' if not result['sql_equal'] else ''
    output = str(result['output_rows']) + ' dòng'
    if re.match(r'^SELECT count\(\*\)', result['sql'], re.I) and result['output_example']:
        output = 'COUNT=' + str(result['output_example'][0][0])
    return (f"{status} · {mode}{delta}<br>**{result['median_ms']:.1f} ms** "
            f"[{result['min_ms']:.1f}–{result['max_ms']:.1f}] · CPU {cpu} ms<br>"
            f"{result['source_hits_median']:,.0f} doc · {result['response_bytes_median']:,.0f} B · {output}")


def ratio_cell(old, current):
    if not old['correct'] or not current['correct']:
        return 'Không so tốc độ: có kết quả sai'
    if old['sql_equal'] != current['sql_equal'] or old['result_sha256'] != current['result_sha256']:
        return 'Khác tập kết quả (UNSAFE)'
    ratio = old['median_ms'] / current['median_ms']
    doc_reduction = 100 * (1 - current['source_hits_median'] / old['source_hits_median']) if old['source_hits_median'] else 0
    return f"{ratio:.2f}× · giảm {doc_reduction:.1f}% doc"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path, help='Completed performance-results directory on VM')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--drift', type=Path, help='Optional end-of-run 1-shard/10k control measurements')
    args = parser.parse_args()
    root = args.input
    manifest = json.loads((root / 'manifest.json').read_text())
    if not manifest.get('finished'):
        raise RuntimeError('Benchmark has not completed')
    records = []
    files = {}
    for name, hash_key in [('expression-performance.py', 'harness_sha256'),
                           ('expression-compare.py', 'comparison_harness_sha256'),
                           ('performance-release-report.md', 'report_sha256')]:
        source = (root.parent / name).read_bytes()
        if hashlib.sha256(source).hexdigest() != manifest[hash_key]:
            raise RuntimeError('Executed source snapshot no longer matches: ' + name)
        files[name] = source.decode()
    hardware = root.parent / 'performance-host-20261118.json'
    if hardware.exists():
        files[hardware.name] = json.loads(hardware.read_text())
    files['expression-performance-report.py'] = Path(__file__).read_text()
    for name in ('final-verification.json', 'script-stability.json'):
        verification = root / name
        if verification.exists():
            files[verification.name] = json.loads(verification.read_text())
    for backend in manifest['backends']:
        for shards, documents in manifest['configurations']:
            stem = f'{backend}-s{shards}-n{documents}'
            for suffix in ('-done.json', '-fixtures.json'):
                files[stem + suffix] = json.loads((root / (stem + suffix)).read_text())
            with gzip.open(root / (stem + '.jsonl.gz'), 'rt') as stream:
                records.extend(json.loads(line) for line in stream)
        for suffix in ('-templates.json', '-oracles.json'):
            files[backend + suffix] = json.loads((root / (backend + suffix)).read_text())
    cleanup = json.loads((root / 'cleanup.json').read_text())
    if cleanup['errors']:
        raise RuntimeError('Benchmark cleanup failed: ' + str(cleanup['errors']))
    groups = defaultdict(list)
    for record in records:
        groups[record['backend'], record['shards'], record['documents'], record['feature'], record['version']].append(record)
    expected_groups = len(manifest['backends']) * len(manifest['configurations']) * len(manifest['features']) * 3
    assert len(groups) == expected_groups, (len(groups), expected_groups)
    for values in groups.values():
        assert len(values) == manifest['repeats'] + 1
        assert sorted(record['repetition'] for record in values) == list(range(manifest['repeats'] + 1))
        assert sum(record['warmup'] for record in values) == 1
    summaries = {key: summarize(values) for key, values in groups.items()}
    drift_summaries = {}
    if args.drift:
        drift_manifest = json.loads((args.drift / 'manifest.json').read_text())
        assert drift_manifest.get('finished') and drift_manifest['configurations'] == [[1, 10000]]
        assert drift_manifest['harness_sha256'] == manifest['harness_sha256']
        drift_records = []
        for backend in drift_manifest['backends']:
            with gzip.open(args.drift / f'{backend}-s1-n10000.jsonl.gz', 'rt') as stream:
                drift_records.extend(json.loads(line) for line in stream)
        drift_groups = defaultdict(list)
        for record in drift_records:
            drift_groups[record['backend'], 1, 10000, record['feature'], record['version']].append(record)
        assert len(drift_groups) == len(drift_manifest['backends']) * len(drift_manifest['features']) * 3
        for key, values in drift_groups.items():
            assert len(values) == drift_manifest['repeats'] + 1
            drift_summaries[key] = summarize(values)
        files['drift_control'] = {'manifest': drift_manifest, 'results': drift_records,
                                  'cleanup': json.loads((args.drift / 'cleanup.json').read_text())}
        assert not files['drift_control']['cleanup']['errors']
    output = args.out
    output.mkdir(parents=True, exist_ok=True)
    with gzip.open(output / 'expression-performance.json.gz', 'wt', encoding='utf-8') as stream:
        json.dump({'manifest': manifest, 'cleanup': cleanup, 'files': files, 'results': records,
                   'summaries': list(summaries.values())}, stream, ensure_ascii=False)
    detailed_fields = ['backend', 'feature', 'case_id', 'shards', 'documents', 'version', 'sql', 'mode', 'samples',
                       'correct', 'sql_equal', 'classifications', 'median_ms', 'min_ms', 'max_ms', 'cpu_median_ms',
                       'execution_median_ms', 'planning_median_ms', 'source_hits_median', 'response_bytes_median', 'request_bytes_median',
                       'search_requests_median', 'es_took_sum_median_ms', 'output_rows', 'sql_output_rows', 'count_value', 'result_sha256']
    with (output / 'expression-performance.csv').open('w', newline='') as stream:
        writer = csv.DictWriter(stream, detailed_fields, extrasaction='ignore', lineterminator='\n')
        writer.writeheader()
        for key in sorted(summaries):
            writer.writerow(summaries[key])
    correct = Counter(record['version'] for record in records if record['correct'])
    counts = Counter(record['version'] for record in records)
    lines = ['# Hiệu năng Elasticsearch expression pushdown theo shard và document', '',
             f"Chạy trên VM `172.16.11.138`, từ `{manifest['started']}` đến `{manifest['finished']}` (UTC).", '',
             '| Phiên bản | Runtime revision | Lượt đúng / tổng (gồm warm-up) |',
             '| --- | --- | ---: |']
    for version in VERSIONS:
        revision = manifest['server_info'][version]['nodeVersion']['version']
        lines.append(f"| {LABELS[version]} | `{revision}` | {correct[version]:,}/{counts[version]:,} |")
    hardware_info = files.get('performance-host-20261118.json')
    if hardware_info:
        lines.extend(['', f"Host báo {hardware_info['cpu_count']} logical CPU; model: "
                      f"`{', '.join(hardware_info['cpu_models'])}`. Quota container được ghi riêng bên dưới và trong raw evidence."])
    lines.extend(['', '## Phạm vi và cách đo', '',
                  '- Ánh xạ **40 SQL đại diện cho 40 hàng tính năng mới/mở rộng** trong [báo cáo phát hành](../../plugin/trino-elasticsearch/EXPRESSION-RELEASE-REPORT.md). Không tuyên bố đã benchmark mọi overload/hàm trong từng nhóm; các ca lỗi và giới hạn vẫn có trong ma trận functional 495 SQL. Các lỗi signed zero/regex đã ghi nhận ở internal không thuộc 40 SQL đại diện này; benchmark đạt không có nghĩa baseline đã được sửa các lỗi đó.',
                  '- Backend của lần chạy: ' + ', '.join(manifest['backends']) + '. Mỗi backend chỉ có **một node**, replica = 0. Tăng shard đo chia shard/parallelism trên node hiện có, không phải tăng số node.',
                  '- Ba hướng: tăng shard 1 → 4 → 8 ở 10.000 document; tăng document 10.000 → 50.000 → 100.000 ở 1 shard; tăng cả hai theo (1;10.000) → (4;50.000) → (8;100.000). Có **7 cấu hình duy nhất**, điểm gốc được dùng chung.',
                  f"- **{len(records):,} lượt SQL** = {len(manifest['features'])} × {len(manifest['configurations'])} × {len(manifest['backends'])} backend × 3 Trino × (1 warm-up + {manifest['repeats']} lượt đo). EXPLAIN và truy vấn oracle không tính vào số lượt đo. Warm-up được lưu nhưng loại khỏi thống kê thời gian.",
                  '- Các query được chạy **tuần tự**, mỗi lúc chỉ một SQL benchmark trên một Trino. Thứ tự master/internal/current xoay vòng theo feature và repetition; mỗi bản xuất hiện ở cả ba vị trí trong ba lượt đo. Không chạy ba bản đồng thời như ma trận functional cũ.',
                  '- Chỉ số chính: **wall time phía client trên VM** (gửi SQL đến nhận hết kết quả), median [min–max] của 3 lượt. Đây là phép đo warm cache gồm planning, điều phối và truyền kết quả. Cả ba bản đi qua recorder proxy sẵn có của lab; latency bao gồm chi phí proxy, không phải phép đo Trino kết nối ES trực tiếp hoặc thời gian riêng của Elasticsearch. Ba mẫu chỉ đủ mô tả khoảng đo, không dùng để khẳng định p95/p99 hoặc ý nghĩa thống kê.',
                  '- CPU là tổng execution CPU Trino do statement protocol `cpuTimeMillis` báo khi trường QueryInfo `cpuTime` không có; không phải CPU Elasticsearch và không bao gồm planning CPU. Raw evidence giữ nguyên cả statement stats lẫn QueryInfo metrics đã thu, execution/planning time, input/memory, số request, tổng ES `took`, DSL/runtime mapping và query ID. Tổng ES `took` có thể cộng các shard/request xử lý song song; không thay cho wall time.',
                  '- `doc` là số source hits chuyển về Trino qua proxy; `B` là byte HTTP response từ các request search/count (loại `_search_shards`). Đây không phải số document ES xét nội bộ, byte lưu trên đĩa hoặc toàn bộ traffic TCP. COUNT remote có thể trả 0 source document mà vẫn phải xét dữ liệu.',
                  '- Dữ liệu được lặp xác định từ fixture biên gốc 18 document keyword/numeric và 20 document text, đổi ID để không trùng. Mapping/analyzer/NULL/array được giữ nguyên; selectivity gần cố định theo quy mô, kể cả phần lẻ của chu kỳ. Đây là synthetic boundary workload, không đại diện mọi phân bố/array length/review size trong production.',
                  '- Kết quả SQL exact/prefilter đối chiếu oracle local không pushdown; native approximate đối chiếu DSL analyzer độc lập và ghi rõ khác SQL exact. Không so tỷ lệ tốc độ hai bản khi kết quả khác nhau. Runtime case của ví dụ đã chọn có cùng tập kết quả SQL trên fixture này; nhãn vẫn là APPROXIMATE theo contract.',
                  '- Index request cache tắt ở tất cả index benchmark; query cache/JIT/OS cache được làm nóng, không xóa cache toàn cluster. Nạp dữ liệu, refresh, flush và chờ shard green/merge hiện tại kết thúc trước khi đo. Index benchmark được xóa sau mỗi cấu hình, không thay fixture functional hay container/binary đang deploy.',
                  '- Ba Trino đều bị giới hạn 2 CPU / 7 GiB RAM. ES7 bị giới hạn 2 CPU / 3 GiB; ES8 3 GiB và không có quota CPU Docker. Chỉ so ba Trino **trong cùng backend**; không quy chênh lệch ES7/ES8 cho riêng phiên bản ES. VM dùng chung với dịch vụ khác; các snapshot load/JVM/GC/disk/container được lưu trong raw evidence.',
                  '- Tạm tăng compilation rate cho cả hai ES trong benchmark để tránh giới hạn cold compile của nhiều hình dạng script; khôi phục giá trị ban đầu trong `finally`. Không đưa chi phí compile đầu tiên thành lợi thế hay bất lợi của warm measurement.',
                  '', '## Đọc kết quả', '',
                  '`Tỷ lệ = median internal / median mới`: > 1 là bản mới nhanh hơn; < 1 là chậm hơn trong lần đo này. Giảm document không tự động có nghĩa giảm latency. Nhãn APPROXIMATE không bảo đảm cùng tập kết quả với SQL.', '',
                  '## Kết quả ở mức lớn nhất: 8 shard / 100.000 document', ''])
    for backend in manifest['backends']:
        comparable = [(summaries[backend, 8, 100000, item['feature'], 'internal'],
                       summaries[backend, 8, 100000, item['feature'], 'current'])
                      for item in manifest['features']]
        comparable = [(old, new) for old, new in comparable if old['correct'] and new['correct']
                      and old['result_sha256'] == new['result_sha256']]
        faster = sum(new['median_ms'] < old['median_ms'] for old, new in comparable)
        slower = sum(new['median_ms'] > old['median_ms'] for old, new in comparable)
        lines.extend([f'### {backend.upper()}', '',
                      f"Trong {len(comparable)} ví dụ có cùng fingerprint kết quả ở mức này, bản mới có median thấp hơn internal ở {faster} ví dụ và cao hơn ở {slower} ví dụ. "
                      'Đây là mô tả ba mẫu tại một điểm đo, không phải kết luận ý nghĩa thống kê. Các ca khác tập kết quả UNSAFE không nằm trong thống kê này.', '',
                      '| Tính năng / SQL ID | master (ms) | internal (ms) | Bản mới (ms) | internal / mới; giảm doc |',
                      '| --- | ---: | ---: | ---: | --- |'])
        for item in manifest['features']:
            results = [summaries[backend, 8, 100000, item['feature'], version] for version in VERSIONS]
            timing = ' | '.join(f"{result['median_ms']:.1f}" for result in results)
            lines.append(f"| {item['feature']}. {markdown(item['title'])}<br>`{item['id']}` | {timing} | {ratio_cell(results[1], results[2])} |")
        lines.append('')
    lines.extend(['**Offload nhiều hơn không bảo đảm giảm latency.** Những hàng có median bản mới cao hơn internal là chậm hơn trong lần đo này, '
                  'kể cả khi số document/byte hoặc CPU Trino giảm. Painless/runtime `_source` chuyển công việc sang Elasticsearch; '
                  'CPU Trino giảm không chứng minh tổng CPU của cả hệ thống giảm.', ''])
    if drift_summaries:
        lines.extend(['## Kiểm tra độ trôi ở cuối lần chạy', '',
                      'Sau ma trận chính, chạy lại sáu SQL đại diện ở 1 shard / 10.000 document, cùng policy warm-up và ba lượt đo. '
                      'Các lượt này là control bổ sung, không trộn vào median hoặc số lượt của ma trận chính. '
                      'Đây là kiểm tra mô tả ảnh hưởng warm-up/JIT/cache/tải VM; không tự tách được nguyên nhân.', '',
                      '| Backend / SQL ID | Trino | Ban đầu: median [min–max] ms | Cuối: median [min–max] ms | Cuối / đầu |',
                      '| --- | --- | ---: | ---: | ---: |'])
        for key in sorted(drift_summaries):
            end = drift_summaries[key]
            start = summaries[key]
            lines.append(f"| {end['backend']} / `{end['case_id']}` | {LABELS[end['version']]} | "
                         f"{start['median_ms']:.1f} [{start['min_ms']:.1f}–{start['max_ms']:.1f}] | "
                         f"{end['median_ms']:.1f} [{end['min_ms']:.1f}–{end['max_ms']:.1f}] | "
                         f"{end['median_ms'] / start['median_ms']:.2f}× |")
        drift_records = files['drift_control']['results']
        lines.extend(['', f"Control bổ sung: {len(drift_records)} lượt; "
                      f"{sum(record['correct'] for record in drift_records)}/{len(drift_records)} đúng theo oracle. "
                      'Cấu hình chính được chạy theo thứ tự cố định và chỉ có ba mẫu; đọc xu hướng shard/document cùng mức trôi này, '
                      'không quy toàn bộ chênh lệch latency cho riêng số shard/document.', ''])
    lines.extend(['## Xu hướng bản mới theo ba hướng tăng tải', '',
                  'Mỗi ô là median wall time (ms) của ba điểm tăng tải theo đúng thứ tự đã nêu. Đây là số đo trực tiếp; không fit đường tăng trưởng hoặc ngoại suy.', ''])
    for backend in manifest['backends']:
        lines.extend([f'### {backend.upper()}', '',
                      '| Tính năng / SQL ID | Tăng shard: 1 → 4 → 8 | Tăng doc: 10k → 50k → 100k | Tăng cả hai |',
                      '| --- | --- | --- | --- |'])
        for item in manifest['features']:
            cells = []
            for _, configurations in AXES:
                values = [summaries[backend, shards, documents, item['feature'], 'current']['median_ms']
                          for shards, documents in configurations]
                cells.append(' → '.join(f'{value:.1f}' for value in values))
            lines.append(f"| {item['feature']}. `{item['id']}` | " + ' | '.join(cells) + ' |')
        lines.append('')
    lines.extend(['## Ma trận SQL đầy đủ: cùng SQL trên ba Trino', '',
                  'Mỗi ô: đúng/sai và loại offload; **median ms** [min–max]; median CPU Trino; median document/byte chuyển về; số dòng trả về hoặc giá trị COUNT. Session chỉ đặt các property mà từng bản hỗ trợ. Các SQL feature 1/2 và 33 tắt script theo ví dụ gốc; các feature text dùng UNSAFE, các feature khác giữ SAFE.', ''])
    comparison_rows = []
    for backend in manifest['backends']:
        for shards, documents in manifest['configurations']:
            lines.extend([f'### {backend.upper()} — {shards} shard / {documents:,} document', '',
                          '| Tính năng, setting và lệnh SQL | Trino master | Trino internal/production | Trino branch mới |',
                          '| --- | --- | --- | --- |'])
            for item in manifest['features']:
                results = [summaries[backend, shards, documents, item['feature'], version] for version in VERSIONS]
                title = (f"{item['feature']}. {markdown(item['title'])} · `{item['id']}`<br>"
                         f"{item['mode']}; scripts {'OFF' if item['off'] else 'ON nếu được hỗ trợ'}<br>"
                         f"<code>{markdown(results[0]['sql'])}</code>")
                cells = [comparison_cell(result) for result in results]
                lines.append('| ' + ' | '.join([title, *cells]) + ' |')
                comparison_rows.append([f"{backend}; shards={shards}; documents={documents}; feature={item['feature']}; "
                                        f"{item['id']}; mode={item['mode']}; scripts_off={item['off']}; {results[0]['sql']}", *cells])
            lines.append('')
    if 'final-verification.json' in files:
        lines.extend(['## Kiểm tra môi trường sau đo', '',
                      'Không còn index benchmark. Số document của bốn fixture gốc trên mỗi backend vẫn là 18 / 20 / 10.000 / 10.000; '
                      'nguồn và mapping của hai fixture biên được đối chiếu SHA-256 và giữ nguyên. '
                      'Compilation rate đã khôi phục; runtime revision, node ID và image của ba Trino không đổi; '
                      'các container lab vẫn chạy và qua healthcheck đã cấu hình. Dịch vụ Trino cũ ở port 18090 vẫn hoạt động. '
                      'Chi tiết nằm trong `files.final-verification.json` của raw evidence.', ''])
    lines.extend(['## Bằng chứng và chạy lại', '',
                  '- [CSV số đo chi tiết](expression-performance.csv): mỗi dòng là một feature/config/backend/version; đầy đủ median/min/max, CPU, execution time, request/byte/doc và kiểm tra kết quả.',
                  '- [CSV bốn cột SQL và ba Trino](expression-performance-comparison.csv).',
                  '- [Raw evidence](expression-performance.json.gz): mọi warm-up/lượt đo, fingerprint kết quả, oracle, SQL/session, query ID, plan, DSL/runtime definitions, metric và snapshot môi trường. Không chứa mật khẩu.',
                  '- [SQL replay](expression-performance.sql) chạy tuần tự từng feature/config trên candidate; cần nạp lại index từ harness vì index benchmark đã được thu hồi.',
                  '- [Hướng dẫn](EXPRESSION-PERFORMANCE-LAB.md), [harness](expression-performance.py), [renderer](expression-performance-report.py).', ''])
    (output / 'EXPRESSION-PERFORMANCE.md').write_text('\n'.join(lines))
    with (output / 'expression-performance-comparison.csv').open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['SQL / cấu hình', *[LABELS[version] for version in VERSIONS]])
        writer.writerows(comparison_rows)
    sql_lines = ['-- Candidate replay. Benchmark indices must first be recreated with expression-performance.py.',
                 '-- Full session settings for each revision are preserved in raw evidence.']
    for backend in manifest['backends']:
        sql_lines.extend([f'USE {backend}_compare.default;'])
        for shards, documents in manifest['configurations']:
            for item in manifest['features']:
                result = summaries[backend, shards, documents, item['feature'], 'current']
                sql_lines.append(f"-- feature {item['feature']} {item['id']}; {shards} shards; {documents} documents")
                for key, value in result['session'].items():
                    literal = value if value in ('true', 'false') else "'" + value + "'"
                    sql_lines.append(f'SET SESSION {key} = {literal};')
                sql_lines.append(result['sql'] + ';')
        sql_lines.append('-- Restore catalog defaults after replaying this backend.')
        for key in manifest['sessions']['current:' + backend]:
            sql_lines.append(f'RESET SESSION {key};')
    (output / 'expression-performance.sql').write_text('\n'.join(sql_lines) + '\n')
    (output / 'performance-summary.json').write_text(json.dumps({'manifest': manifest,
        'counts': dict(counts), 'correct': dict(correct), 'groups': len(summaries),
        'summaries': list(summaries.values())}, ensure_ascii=False, indent=2))
    print(json.dumps({'executions': len(records), 'groups': len(summaries),
                      'correct': dict(correct), 'comparison_rows': len(comparison_rows)}, indent=2))


if __name__ == '__main__':
    main()
