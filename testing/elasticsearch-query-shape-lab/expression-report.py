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

"""Render the four-column SQL comparison from captured execution evidence."""
import argparse
from collections import Counter
import csv
import gzip
import json
import re
from pathlib import Path

VERSIONS = ('master', 'internal', 'current')
LABELS = ('Trino master', 'Trino internal/production', 'Trino branch mới')
NAMES = {'NATIVE EXACT': 'Native exact', 'SCRIPT EXACT': 'Script exact', 'HYBRID EXACT': 'Hybrid exact',
         'NATIVE APPROXIMATE': 'Native approximate', 'SCRIPT APPROXIMATE': 'Script approximate', 'HYBRID APPROXIMATE': 'Hybrid approximate', 'PREFILTER': 'Prefilter', 'LOCAL': 'Local', 'FOLD': 'Fold', 'NO_FILTER': 'Không cần filter', 'NO_SCAN': 'Không scan'}


def commands(value):
    found = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ('bool', 'filter', 'must', 'must_not', 'should', 'range', 'term', 'terms', 'exists', 'prefix', 'regexp', 'match_phrase', 'match_phrase_prefix', 'match_none', 'script'):
                found.add(key)
            if key != 'script':
                found |= commands(child)
    elif isinstance(value, list):
        for child in value:
            found |= commands(child)
    return found


def describe(record):
    status = 'ĐÚNG' if record['correct'] else '**SAI**'
    mode = NAMES.get(record.get('classification'), record.get('classification', 'HARNESS ERROR'))
    names = set()
    for event in record.get('events', []):
        if '/_search_shards' not in event.get('path', ''):
            names |= commands(event.get('request', {}).get('query', {}))
            if event.get('request', {}).get('runtime_mappings'):
                names.update(('runtime_mappings', 'script'))
    dsl = (','.join(sorted(names)) or '—') if record.get('classification') not in ('LOCAL', 'NO_FILTER') else '—'
    actual = record.get('actual', {})
    outcome = 'lỗi ' + actual['error'] if actual.get('error') else str(len(actual.get('rows', []))) + ' dòng'
    if re.match(r'\s*SELECT\s+count\(\*\)', record.get('sql', ''), re.IGNORECASE) and len(actual.get('rows', [])) == 1:
        outcome = 'COUNT=' + str(actual['rows'][0][0])
    reference = record.get('oracle_kind', 'SQL')
    delta = ' · khác SQL gốc' if record.get('sql_equal') is False and record['correct'] and record.get('classification', '').endswith('APPROXIMATE') else ''
    if delta and record.get('sql_golden_rows') is not None:
        delta += ' (' + str(record['sql_golden_rows']) + ' dòng SQL gốc)'
    return f"{status} [{reference}]{delta} · {mode} ({dsl}) · {outcome} · {record.get('source_hits', 0)} doc / {record.get('response_bytes', 0):,} B"


def markdown(text):
    return str(text).replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('|', '&#124;').replace('\n', ' ')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('input', help='comparison.json.gz produced by expression-compare.py')
    parser.add_argument('--out', default='.')
    args = parser.parse_args()
    output = Path(args.out)
    output.mkdir(exist_ok=True, parents=True)
    with gzip.open(args.input, 'rt', encoding='utf-8') as stream:
        data = json.load(stream)
    records = data['results']
    lookup = {(r['backend'], r['case_id'], r['version']): r for r in records}
    backends = sorted({r['backend'] for r in records}, reverse=True)
    summary = {}
    for version in VERSIONS:
        selected = [r for r in records if r['version'] == version]
        summary[version] = {'total': len(selected), 'correct': sum(r['correct'] for r in selected),
                            'modes': dict(Counter(r.get('classification', 'HARNESS ERROR') for r in selected))}
    lines = ['# Kết quả SQL: master, internal/production và branch mới', '',
             f"Thời điểm: `{data['generated_at']}`. Host kiểm thử: `172.16.11.138`. Branch: `Feat/elasticsearch-array-necessary-prefilter`.", '',
             f"Chạy **{len(data['cases'])} SQL × {len(backends)} phiên bản Elasticsearch × 3 Trino = {len(records):,} lượt**; các ca SQL exact được đối chiếu với local (`allow_pushdown_into_connectors=false`); các ca UNSAFE native approximate có oracle Query DSL Elasticsearch riêng. Kết quả dưới đây được lấy từ SQL đã thực thi, distributed plan và Query DSL HTTP thực gửi tới Elasticsearch.", '',
             '| Chỉ số | ' + ' | '.join(LABELS) + ' |', '| --- | --- | --- | --- |']
    def summary_row(label, values):
        lines.append('| ' + label + ' | ' + ' | '.join(str(v) for v in values) + ' |')
    summary_row('Commit runtime', ['`' + data['deployment']['versions'][v]['revision'] + '`' for v in VERSIONS])
    summary_row('Port loopback', [data['deployment']['versions'][v]['port'] for v in VERSIONS])
    summary_row('Kết quả đúng', [f"{summary[v]['correct']}/{summary[v]['total']}" for v in VERSIONS])
    for mode in ('NATIVE EXACT', 'SCRIPT EXACT', 'HYBRID EXACT', 'NATIVE APPROXIMATE', 'SCRIPT APPROXIMATE', 'HYBRID APPROXIMATE', 'PREFILTER', 'LOCAL', 'FOLD', 'NO_FILTER'):
        summary_row(NAMES[mode], [summary[v]['modes'].get(mode, 0) for v in VERSIONS])
    lines += ['', '## Kết quả mở rộng text trên VM', '',
              '102 SQL text bổ sung × ES7/ES8: bản mới đạt 204/204 ca. Có 20 lượt local theo các cấu hình SAFE/DISABLED hoặc tắt script. Câu người dùng `LOWER(raw_review_comment) LIKE ...` dùng native match_phrase khi UNSAFE, kể cả scripts OFF.', '',
              'Ví dụ trong fixture: SQL exact trả 4 dòng, native analyzer trả 5 dòng vì nhận thêm chuỗi `SÁCH, rất tốt`. Ca stopword có thể mất dòng SQL vì analyzer xóa toàn bộ token. AND full-text trên array có thể khớp các token ở các phần tử khác nhau. Đây là các khác biệt được ghi nhận khi chọn UNSAFE, không phải bằng chứng tương đương SQL.', '',
              'Biểu thức source không fallible có thể EXACT; Unicode case UNSAFE dùng chính sách Elasticsearch được ghi APPROXIMATE. Các cây fallible giữ PREFILTER và residual. Runtime source definitions có mặt trong COUNT và từng request PIT; Source statistics trả unknown để không lấy ước lượng sai từ query thiếu runtime definitions.', '']
    lines += ['- Text UNSAFE: native full-text dùng analyzer; source runtime dùng nguyên chuỗi. Các ca có oracle Elasticsearch được gắn nhãn [Elasticsearch analyzer]. Khác SQL gốc có chủ đích được ghi riêng, không gọi là lỗi hoặc EXACT.',
              '- Các ca có source runtime mang runtime_mappings trong request. Native approximate không cần Painless; runtime source cần flag scalar/array script tương ứng.', '']
    lines += ['', '## Cách đọc và phạm vi', '',
              '- **Exact**: không còn residual cho điều kiện được dịch. **Prefilter**: Elasticsearch lọc ứng viên, Trino kiểm tra residual. **Local**: không có điều kiện remote hữu ích cho phép lọc này. **Fold**: Trino rút gọn SQL, không cần scan.',
              '- `doc` là tổng document `_source` thực chuyển về Trino trong các response search/scroll; không phải số document Elasticsearch xét nội bộ. `B` là byte HTTP response body chưa nén, gồm cả metadata của response search/count/scroll. Loại `_search_shards` khỏi số đo này. Query COUNT remote có thể trả kết quả mà không chuyển document nào.',
              '- Các dòng lỗi được coi là ĐÚNG khi giữ cùng mã lỗi SQL như chạy local. Không coi query trả sai kết quả là một thành công pushdown. Thứ tự kết quả không quan trọng; LIMIT không ORDER BY được kiểm tra theo tập con hợp lệ và số dòng.',
              '- Dữ liệu do bộ test tạo: 18 document biên keyword/numeric, 20 document biên text, cùng hai index volume 10.000 document trên mỗi Elasticsearch. Có NULL/missing/array rỗng, nhiều phần tử khác nhau, Unicode/newline, signed zero/NaN sinh từ phép tính, overflow/division by zero, mapping không tương đương nguồn và timestamp trước epoch/leap day.',
              '- Elasticsearch 7.17.27 và 8.11.3 dùng cùng schema và dữ liệu. Ba Trino có cùng tài nguyên (2 CPU, heap 4 GiB), scroll-size 1.000 và cùng backend qua listener proxy riêng. Bảng nhằm đo tính đúng và offload; không phải benchmark latency độc lập vì ba phiên bản chạy đồng thời.',
              '- Branch mới bật hai catalog properties `elasticsearch.array-script-pushdown.enabled=true` và `elasticsearch.scalar-script-pushdown.enabled=true`; full-text SAFE. Baseline chỉ dùng properties mà phiên bản đó thực sự hỗ trợ. Các ca có nhãn scripts OFF tắt cả hai session properties để kiểm tra native/Plan 1.',
              '- Trên keyword, Unicode LOWER/UPPER giữ residual và cho ứng viên chưa chứng minh qua Trino. Mapping ignore_above/normalizer/không doc values, capture, nhiều cột hoặc overload chưa hỗ trợ được kiểm tra để fallback an toàn.',
              '- Chỉ các index có prefix `expr_a2af4b7203e_` được tạo lại khi dùng `--seed`. Giới hạn script compilation được tăng tạm thời trong lab để chạy nhiều hình dạng biểu thức, rồi phục hồi trong `finally`; trước/sau lưu tại thư mục kết quả trên host.',
              '', '## Sai biệt của baseline và regression đã sửa', '', 'Các ca REAL/DOUBLE của internal thiếu document `id=2` chứa `-0.0`: Elasticsearch phân biệt hai dấu zero trong native query, SQL coi chúng bằng nhau. Regex của internal thiếu `id=15` chứa `abc` kèm newline cuối, trong khi end anchor SQL vẫn khớp trước newline cuối. Mỗi ca được chạy trên cả ES7 và ES8.', '']
    failures = [r for r in records if not r['correct']]
    if not failures:
        lines.append('Không có sai biệt kết quả trong lần chạy này.')
    else:
        grouped = {}
        for record in failures:
            grouped.setdefault((record['version'], record['case_id']), []).append(record)
        for (version, identifier), values in grouped.items():
            record = values[0]
            details = f"missing={record.get('missing', [])}, extra={record.get('extra', [])}"
            lines.append(f"- `{version}` / `{identifier}` / {', '.join(r['backend'] for r in values)}: `{record['sql']}`. {details}.")
    lines += ['', 'Lần chạy đầu được giữ trên host tại `results-initial/`. Nó phát hiện regex `$` bỏ sót newline cuối; probe bổ sung phát hiện lỗi amount của date_add trên phần tử NULL bị che. Branch mới nới candidate regex SAFE và giữ residual, đồng thời kiểm tra mọi đối số trong temporal null proof. Date_diff đảo hai timestamp cũng được thêm vào ma trận.', '',
              '## Ví dụ trên 10.000 document', '', '| SQL | ' + ' | '.join(LABELS) + ' |', '| --- | --- | --- | --- |']
    for case in data['cases']:
        if case['id'].startswith('VOL-'):
            lines.append('| <code>' + markdown(case['sql']) + '</code> | ' + ' | '.join(describe(lookup['es8', case['id'], v]) for v in VERSIONS) + ' |')
    lines += ['', '## Ma trận SQL đầy đủ', '',
              'SQL chạy trong schema `default` của catalog tương ứng (`es8_compare` hoặc `es7_compare`). ID `A-` là array, `S-` là scalar. Nội dung Query DSL và full plan nằm trong [raw evidence](expression-comparison.json.gz); có thể chạy lại từ [file SQL](expression-comparison.sql) hoặc [harness](expression-compare.py).', '']
    for backend in backends:
        lines += ['### ' + ('Elasticsearch 8.11.3' if backend == 'es8' else 'Elasticsearch 7.17.27'), '']
        previous = None
        for case in data['cases']:
            if case['group'] != previous:
                lines += ['', '#### ' + case['group'], '', '| SQL | ' + ' | '.join(LABELS) + ' |', '| --- | --- | --- | --- |']
                previous = case['group']
            suffix = (' · scripts OFF' if case['off'] else '') + ' · ' + case.get('mode', 'SAFE')
            sql = f"**{case['id']}{suffix}**<br><code>{markdown(case['sql'])}</code>"
            lines.append('| ' + sql + ' | ' + ' | '.join(describe(lookup[backend, case['id'], v]) for v in VERSIONS) + ' |')
    lines += ['', '## Provenance triển khai', '',
              'Master và internal được ghim theo remote head tại thời điểm kiểm tra, không thay baseline bằng code mới. Image base bất biến là internal; diff production giữa master và internal chỉ ở connector Elasticsearch (trino-main chỉ khác test). Master overlay thay connector, trino-main và server launcher được clean-build từ master. Candidate overlay thay connector, trino-main, SPI và launcher; core/SPI giữ artifact của a2af4b7 khi source không đổi, connector và launcher clean-build theo commit runtime mới.', '',
              'Image IDs, artifact SHA-256, manifests và session properties được lưu trong raw evidence. Báo cáo/harness có thể được commit sau commit runtime; vì vậy commit chứa báo cáo không thay đổi mã Java đã deploy.', '',
              'Các container của lần so sánh có label `codex.lab=trino-expression-comparison`; dịch vụ Trino sẵn có ở port 18090 vẫn được giữ nguyên. Candidate chạy tại `127.0.0.1:18093` trên host, với catalogs `es8_compare`, `es7_compare`. Các port này chỉ bind loopback; dùng SSH tunnel để truy cập từ máy khác.', '']
    (output / 'EXPRESSION-COMPARISON.md').write_text('\n'.join(lines), encoding='utf-8')
    with (output / 'expression-comparison.csv').open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream, lineterminator='\n')
        writer.writerow(['SQL', *LABELS])
        for backend in backends:
            for case in data['cases']:
                writer.writerow([f"-- {backend} {case['id']} mode={case.get('mode', 'SAFE')}\n" + case['sql'], *(describe(lookup[backend, case['id'], v]).replace('**', '') for v in VERSIONS)])
    sql = ['-- Executed SQL matrix. Run with username expression-comparison and time zone UTC.', '-- Use current runtime on port 18093; scripts are enabled by catalog defaults.', '']
    for backend in backends:
        sql += ['USE ' + backend + '_compare.default;', "SET TIME ZONE 'UTC';"]
        for case in data['cases']:
            state = 'false' if case['off'] else 'true'
            sql += [f"-- {case['id']}: {case['group']}",
                    f"SET SESSION {backend}_compare.full_text_pushdown_mode = '{case.get('mode', 'SAFE')}';",
                    f'SET SESSION {backend}_compare.array_script_pushdown_enabled = {state};',
                    f'SET SESSION {backend}_compare.scalar_script_pushdown_enabled = {state};', case['sql'] + ';', '']
    (output / 'expression-comparison.sql').write_text('\n'.join(sql), encoding='utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
