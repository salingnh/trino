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

"""Render the ES8 same-binary hybrid/Painless/local benchmark evidence."""
import argparse
from collections import defaultdict
import csv
import gzip
import hashlib
from html import escape
import json
from pathlib import Path
from statistics import median

MODES = ('hybrid', 'script', 'local')


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--input', type=Path, required=True)
    ap.add_argument('--out', type=Path, default=Path(__file__).parent)
    args = ap.parse_args()
    with gzip.open(args.input, 'rt') as stream:
        packet = json.load(stream)
    records = packet['records']
    assert packet['complete']['all_correct'] and len(records) == 336
    assert all(r['correct'] and r['sql_equal'] and r['backend'] == 'es8' for r in records)
    groups = defaultdict(list)
    for r in records:
        if not r['warmup']:
            groups[r['feature'], r['shards'], r['documents'], r['execution_mode']].append(r)
    assert len(groups) == 84 and all(len(values) == 3 for values in groups.values())
    def metric(record, name):
        if name == 'wall': return record['actual']['elapsed_ms']
        if name == 'es_cpu': return record['actual']['es_container_cpu_ms']
        if name == 'trino_cpu': return record['actual']['statement_stats']['cpuTimeMillis']
        if name == 'search_requests': return sum(e.get('took_ms') is not None and '/_search_shards' not in e['path'] for e in record['events'])
        return record[name]
    def values(key, mode, name):
        return [metric(r, name) for r in groups[(*key, mode)]]
    def m(key, mode, name='wall'): return median(values(key, mode, name))
    def latency(key, mode):
        v = values(key, mode, 'wall')
        return f'{median(v):,.1f} [{min(v):,.1f}–{max(v):,.1f}]'
    def change(key, base): return (m(key, 'hybrid') / m(key, base) - 1) * 100
    def interval(key, base):
        a, b = values(key, 'hybrid', 'wall'), values(key, base, 'wall')
        if max(a) < min(b): return 'hybrid nhanh hơn cả 3 lượt'
        if min(a) > max(b): return 'hybrid chậm hơn cả 3 lượt'
        return 'khoảng đo chồng lấp'
    keys = sorted({key[:3] for key in groups})
    for key in keys:
        for mode in MODES:
            selected = groups[(*key, mode)]
            assert len({r['actual_summary']['sha256'] for r in selected}) == 1
        assert groups[(*key, 'hybrid')][0]['candidate_summary'] == groups[(*key, 'script')][0]['candidate_summary']
    titles = {f['feature']: f for f in packet['metadata']['features']}
    csv_rows = []
    for key in keys:
        f, shards, documents = key
        row = dict(feature=f, case_id=titles[f]['id'], shards=shards, documents=documents,
                   sql=groups[(*key, 'hybrid')][0]['sql'],
                   result_rows=groups[(*key, 'hybrid')][0]['actual_summary']['rows'],
                   result_sha256=groups[(*key, 'hybrid')][0]['actual_summary']['sha256'],
                   offload_candidate_sha256=groups[(*key, 'hybrid')][0]['candidate_summary']['sha256'])
        for mode in MODES:
            for name in ('wall', 'es_cpu', 'trino_cpu', 'source_hits', 'response_bytes', 'es_took_sum_ms', 'search_requests'):
                v = values(key, mode, name)
                row[f'{mode}_{name}_median'] = median(v)
                if name == 'wall': row.update({f'{mode}_wall_min': min(v), f'{mode}_wall_max': max(v)})
            row[f'{mode}_classification'] = groups[(*key, mode)][0]['classification']
            row[f'{mode}_residual'] = groups[(*key, mode)][0]['residual']
        row.update(hybrid_vs_script_percent=change(key, 'script'), hybrid_vs_local_percent=change(key, 'local'),
                   hybrid_vs_script_interval=interval(key, 'script'), hybrid_vs_local_interval=interval(key, 'local'))
        csv_rows.append(row)
    with (args.out / 'expression-es8-hybrid.csv').open('w') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(csv_rows[0]), lineterminator='\n')
        writer.writeheader(); writer.writerows(csv_rows)
    def tree_nodes(node):
        if isinstance(node, dict):
            if 'type' in node and 'breakdown' in node: yield node
            for value in node.values(): yield from tree_nodes(value)
        elif isinstance(node, list):
            for value in node: yield from tree_nodes(value)
    profile_rows = []
    for p in packet['profiles']:
        nodes = list(tree_nodes(p['response']['profile']))
        calls = sum(n['breakdown'].get('match_count', 0) for n in nodes if n['type'] == 'ScriptQuery')
        profile_rows.append({'feature': p['feature'], 'shards': p['shards'], 'mode': p['mode'],
                             'calls': calls, 'types': sorted({n['type'] for n in nodes})})
    wins_script = sum(change(key, 'script') < 0 for key in keys)
    wins_local = sum(change(key, 'local') < 0 for key in keys)
    clean_script = sum(interval(key, 'script') == 'hybrid nhanh hơn cả 3 lượt' for key in keys)
    slow_script = sum(interval(key, 'script') == 'hybrid chậm hơn cả 3 lượt' for key in keys)
    lines = ['# ES8: hybrid native + Painless so với Painless thuần và Trino local', '',
        'Đo trên VM `172.16.11.138`, ngày 18/11/2026. **Chỉ ES8.11.3**; cùng Trino `current-e10be840714` cho cả ba chế độ. Phạm vi là bốn mẫu hybrid trong bộ 40 SQL hiệu năng trước đó, không phải mọi tổ hợp hàm có thể dịch hybrid. Giữ cùng binary để tách hiệu quả của native guard khỏi khác biệt phiên bản connector.', '',
        f'Kết quả: median hybrid nhanh hơn Painless thuần ở **{wins_script}/28** điểm SQL/cấu hình; nhanh hơn local ở **{wins_local}/28** điểm. Với Painless thuần: **{clean_script}** điểm có cả ba lượt hybrid thấp hơn cả ba lượt script; **{slow_script}** điểm ngược lại; **{28-clean_script-slow_script}** điểm có khoảng min–max chồng lấp. Đây là quan sát ba lượt, không phải kiểm định thống kê hay p95/p99.', '',
        '## 1. Đọc ba cột so sánh', '',
        '| Chế độ | Xử lý ở ES8 | Xử lý ở Trino |', '| --- | --- | --- |',
        '| Hybrid | DSL thật do connector sinh: native range kết hợp Painless | Nhận kết quả; riêng `S-MIXED-OR` giữ residual |',
        '| Painless thuần | Toàn bộ điều kiện lọc ứng viên trong một script; không còn native range bên ngoài | Giữ đúng plan, projection, phân trang và residual như hybrid |',
        '| Trino local | `allow_pushdown_into_connectors=false`; lấy đủ document | Tính toàn bộ WHERE bằng Trino |', '',
        'Painless thuần là **thí nghiệm loại bỏ native tại proxy của lab**, chưa phải session option sản phẩm. Hook chỉ thay `query` của initial search trên tên index riêng `expr_es8_hybrid_20261118_*`, chỉ listener `current/es8`. Cả hybrid và script thuần dùng `bool.filter`; các phần khác của request giữ nguyên. Proxy ghi DSL thực sự đã gửi ES, gồm query gốc khi thay thế. Không sửa/rebuild/redeploy Java.', '',
        'Ba script số học được xuất trực tiếp từ compiler hiện có: array dùng compact integral compiler, scalar dùng typed compiler. Với OR phải dịch **cả OR** sang script, không được xóa nhánh native. Với AND array, script vẫn kiểm tra hai điều kiện trên **cùng phần tử**; native range là guard theo document. `S-MIXED-OR` dùng cùng script Unicode candidate của connector, thêm nhánh `n>10` bằng Painless; giữ residual ở Trino. Đây là prefilter tương đương trên tập fixture đã kiểm chứng, không tuyên bố một compiler multi-field mới.', '',
        '## 2. SQL và đường thực thi', '',
        '| SQL ID | SQL mẫu (`T` là index của từng cấu hình) | Native | Script trong hybrid | Mức thực thi |', '| --- | --- | --- | --- | --- |']
    for f in (13, 14, 30, 32):
        item = titles[f]
        sql = item['sql'].replace('expr_a2af4b7203e_cases', 'T')
        native = '`range numbers > 10`' if f in (13,14) else '`range n > 10`'
        script = {13:'Modulo; OR ghép bằng `bool.should`',14:'Cả lambda AND; native guard ghép bằng `bool.filter`',30:'Modulo; OR ghép bằng `bool.should`',32:'`lower(word) LIKE ...` candidate; OR bằng `bool.should`'}[f]
        lines.append(f'| `{item["id"]}` | <code>{escape(sql)}</code> | {native} | {script} | {"PREFILTER + residual" if f==32 else "EXACT; không tính lại WHERE tại Trino"} |')
    lines += ['', '## 3. Kết quả chính: 8 shard / 100.000 document', '',
        'Wall time là từ gửi SQL tới nhận hết kết quả; đơn vị **ms**, ô ghi `median [min–max]` của ba lượt sau warm-up. Δ = `(hybrid / baseline − 1) × 100%`: âm là hybrid nhanh hơn.', '',
        '| SQL ID | Hybrid | Painless thuần | Trino local | Δ so script | Δ so local |', '| --- | ---: | ---: | ---: | ---: | ---: |']
    for f in (13,14,30,32):
        key = f,8,100000
        lines.append(f'| `{titles[f]["id"]}` | {latency(key,"hybrid")} | {latency(key,"script")} | {latency(key,"local")} | {change(key,"script"):+.1f}% | {change(key,"local"):+.1f}% |')
    lines += ['', '### Công việc ở ES và dữ liệu về Trino tại cùng cấu hình', '',
        'CPU ES đọc trực tiếp `cpu.stat usage_usec` của cgroup container quanh SQL, cộng mọi thread và có thể gồm GC/nền; có thể lớn hơn wall time. CPU Trino lấy `statement_stats.cpuTimeMillis`. Không diễn giải `sum ES took` giữa các shard/request thành CPU hoặc wall toàn SQL.', '',
        '| SQL ID | CPU ES hybrid / script / local (ms) | CPU Trino hybrid / script / local (ms) | Source docs hybrid / script / local | Response hybrid / script / local (MiB) |', '| --- | ---: | ---: | ---: | ---: |']
    for f in (13,14,30,32):
        key=f,8,100000
        fmt=lambda name, divisor=1, digits=1: ' / '.join(f'{m(key,mode,name)/divisor:,.{digits}f}' for mode in MODES)
        lines.append(f'| `{titles[f]["id"]}` | {fmt("es_cpu")} | {fmt("trino_cpu")} | {fmt("source_hits",digits=0)} | {fmt("response_bytes",1024**2)} |')
    key = 32, 1, 100000
    lines += ['', '### Ca chậm nổi bật: OR trộn field, 1 shard / 100.000 document', '',
        f'Median hybrid **{m(key,"hybrid"):,.1f} ms**, script thuần **{m(key,"script"):,.1f} ms**, local **{m(key,"local"):,.1f} ms**. Hybrid chậm hơn script **{change(key,"script"):.1f}%** và local **{change(key,"local"):.1f}%**. Đây là điểm đáng chú ý khi chỉ tăng document, không tăng shard.', '',
        'Đối chiếu lượt đo thứ hai sau warm-up (`repetition=2` trong raw). Hai chế độ offload đều nhận cùng 61.111 candidate qua 63 request search/scroll. Chuỗi took là: trang Scroll đầu tiên sau initial search / trang giữa / trang gần cuối còn dữ liệu / trang rỗng kết thúc.', '',
        '| Chế độ | Query ID | Wall SQL (ms) | CPU ES (ms) | Tổng ES took (ms) | Requests | Took bốn trang (ms) |',
        '| --- | --- | ---: | ---: | ---: | ---: | --- |']
    for mode in MODES:
        r = next(r for r in records if r['feature']==32 and r['shards']==1 and r['documents']==100000
                 and r['execution_mode']==mode and r['repetition']==2)
        events = [e for e in r['events'] if '/_search_shards' not in e['path'] and e['took_ms'] is not None]
        pages = ' / '.join(str(events[i]['took_ms']) for i in (1, len(events)//2, -2, -1))
        lines.append(f'| {mode} | `{r["actual"]["query_id"]}` | {r["actual"]["elapsed_ms"]:,.1f} | {r["actual"]["es_container_cpu_ms"]:,.1f} | {r["es_took_sum_ms"]:,} | {len(events)} | {pages} |')
    lines += ['', 'Chi phí tăng nằm ở đường ES search/Scroll của hybrid: lượng dữ liệu và số trang giống script thuần, nhưng took trang hybrid tăng về cuối và CPU ES cao hơn rõ. Không thể giải thích chênh lệch này bằng gửi nhiều document hơn script thuần hoặc chỉ bằng residual Trino. Chưa profile nội bộ từng trang để kết luận chính xác iterator/collector Lucene nào gây tăng chi phí.', '']
    lines += ['', 'Hybrid và script thuần nhận **cùng tập candidate**, nên lượng source về Trino gần như nhau. Lợi ích riêng của native được đánh giá bằng số lần gọi script, CPU ES và latency; giảm document so với local là lợi ích chung của cả hai cách offload.', '',
        '## 4. Profile: native thực sự giảm bao nhiêu lần gọi script?', '',
        'Profile `size=0, track_total_hits=true` chạy **sau** các lượt tính thời gian của mỗi SQL/cấu hình. Đây là một request đếm trên toàn index, không phải tổng số lần script của tất cả trang Scroll. `ScriptQuery.breakdown.match_count` là số lần kiểm tra document, không phải số phần tử array.', '',
        '| SQL ID | Shard | Script calls hybrid | Script calls thuần | Giảm | Lớp native trong hybrid |', '| --- | ---: | ---: | ---: | ---: | --- |']
    for f in (13,14,30,32):
        for shards in (1,8):
            pair={p['mode']:p for p in profile_rows if p['feature']==f and p['shards']==shards}
            h,s=pair['hybrid']['calls'],pair['script']['calls']
            types=', '.join(t for t in pair['hybrid']['types'] if t not in ('ScriptQuery','ConstantScoreQuery','BooleanQuery'))
            lines.append(f'| `{titles[f]["id"]}` | {shards} | {h:,} | {s:,} | {(1-h/s)*100:.1f}% | {types} |')
    lines += ['', 'Numeric range hiện ra `IndexOrDocValuesQuery`: có đường native, nhưng wrapper này không chứng minh mọi thao tác range luôn dùng point index. Counter ở trên chứng minh lượng script giảm trong request đã profile; không suy mọi OR/hybrid đều giảm cùng tỷ lệ. Script OR thuần cũng có thể short-circuit bên trong script; ít `match_count` hơn không đồng nghĩa giảm tương ứng mọi phép tính hay wall time. Hybrid còn có chi phí dựng/ghép iterator native + script; khi source fetch/Scroll hoặc residual chiếm phần lớn, phần tiết kiệm script có thể nhỏ.', '',
        '## 5. Toàn bộ ma trận shard/document', '',
        'Bảy cấu hình duy nhất: tăng shard `(1,10k)→(4,10k)→(8,10k)`; tăng document `(1,10k)→(1,50k)→(1,100k)`; tăng cả hai `(1,10k)→(4,50k)→(8,100k)`. Các hàng dùng SQL đầy đủ và median [min–max] ms.', '',
        '| SQL thực thi | Shard / docs | Hybrid | Painless thuần | Trino local | Δ script | Độ tách khoảng so script |', '| --- | ---: | ---: | ---: | ---: | ---: | --- |']
    for row in csv_rows:
        key=row['feature'],row['shards'],row['documents']
        lines.append(f'| <code>{escape(row["sql"])}</code> | {key[1]} / {key[2]:,} | {latency(key,"hybrid")} | {latency(key,"script")} | {latency(key,"local")} | {change(key,"script"):+.1f}% | {interval(key,"script")} |')
    lines += ['', '## 6. Điều kiện đo, bằng chứng và giới hạn', '',
        '- 4 mẫu hybrid × 7 cấu hình × 3 chế độ × (1 warm-up + 3 đo) = **336 SQL**, gồm **252 lượt đo + 84 warm-up**. Có thêm 56 EXPLAIN, 4 SQL local reference và 16 profile; không đưa chúng vào latency benchmark. Smoke 12 SQL và các lượt kiểm tra harness chưa hoàn tất giữ riêng, không trộn vào 336 SQL này.',
        '- Mỗi SQL chạy tuần tự; xoay thứ tự ba chế độ để mỗi chế độ đứng đầu/giữa/cuối đúng một lần trong ba lượt đo. Không phát tải đồng thời nhiều SQL.',
        '- Tất cả 336 SQL đúng theo reference local trên fixture gốc; kiểm tra cả tập ID và fingerprint, không chỉ số dòng. Cả 28 cặp hybrid/script có cùng tập ID candidate ở mọi lần chạy, kể cả `S-MIXED-OR` có residual.',
        '- Dùng cùng ES8 một node, replica 0, request cache tắt trên index benchmark. Trino current 2 CPU/7 GiB, heap 4 GiB; ES8 container 3 GiB, không đặt quota CPU Docker. Cache/JIT/script đã warm; không phải phép đo cold start. Latency gồm recorder proxy và HTTP/Scroll của lab, không phải kết nối trực tiếp production.',
        '- Nạp dữ liệu lặp từ 18 document biên, tăng tới 100.000; độ chọn lọc và cardinality gần như cố định. Chưa đại diện dữ liệu production, array dài, skew shard hay nhiều truy vấn đồng thời. Tăng shard vẫn trên cùng một node.',
        '- Không có assertion hybrid phải nhanh hơn: các ca chậm và khoảng chồng lấp vẫn giữ trong bảng/CSV. Ba lượt không đủ kết luận chênh nhỏ là ổn định.',
        '- Kết thúc: proxy khôi phục đúng bytes/hash gốc; xóa toàn bộ index tạm của lượt đo; fixture gốc (mapping/analysis/giá trị) không đổi; image, PID và tài nguyên ES8/Trino không đổi. Trong lượt benchmark, không chạy ES7 hay thay đổi mã Java.', '',
        'Trong CSV, `search_requests` chỉ đếm search/scroll có `took`, không tính clear-scroll; raw events vẫn giữ đầy đủ request cleanup.\n\nBằng chứng: [CSV đầy đủ CPU/latency/source/bytes/SQL](expression-es8-hybrid.csv), [raw kết quả + DSL + plan + profile + source harness](expression-es8-hybrid.json.gz). Harness: [benchmark](expression-hybrid-benchmark.py), [proxy lab](expression-hybrid-proxy.py), [script thuần](expression-hybrid-scripts.json), [exporter compiler](HybridScriptExporter.java.txt), [bộ sinh báo cáo](expression-hybrid-report.py).', '',
        f'SHA-256 raw archive: `{hashlib.sha256(args.input.read_bytes()).hexdigest()}`.', '',
        'Tái lập: đặt các file benchmark/proxy/scripts/exporter cùng thư mục lab và dùng đúng `expression-compare.py` và `expression-performance.py` trong `raw.sources`. `raw.benchmark_input` chứa features/templates để tạo input gzip riêng khi tái lập; input gốc đầy đủ vẫn là [expression-es8-audit-input.json.gz](expression-es8-audit-input.json.gz). Các bản source đã chạy trong raw được giữ nguyên; bản repo có bổ sung license header. Helper performance trong raw khác bản workspace hiện mặc định ES8; harness hybrid luôn truyền ES8 rõ ràng. Chạy `python3 expression-hybrid-benchmark.py` trên VM. Harness từ chối ghi đè thư mục bằng chứng đã tồn tại; dùng một lab output mới được đổi tên rõ ràng khi tái lập. Chỉ namespace index riêng được tạo/xóa. Khi dừng bằng SIGTERM/SIGINT, `finally` dọn index và khôi phục proxy; nếu tiến trình bị SIGKILL hoặc VM mất điện, khôi phục bytes `proxy-original.py` bằng ghi in-place vào `expression-proxy.py`, rồi restart container proxy của lab.']
    (args.out/'EXPRESSION-ES8-HYBRID.md').write_text('\n'.join(lines)+'\n')
    print(json.dumps({'rows':len(csv_rows),'hybrid_faster_script':wins_script,'hybrid_faster_local':wins_local,'profiles':profile_rows},ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
