# Kết quả SQL: master, internal/production và branch mới

Thời điểm: `2026-11-13T13:08:55.197719+00:00`. Host kiểm thử: `172.16.11.138`. Branch: `Feat/elasticsearch-array-necessary-prefilter`.

Chạy **393 SQL × 2 phiên bản Elasticsearch × 3 Trino = 2,358 lượt**; mỗi SQL được đối chiếu với cùng query chạy local (`allow_pushdown_into_connectors=false`). Kết quả dưới đây được lấy từ SQL đã thực thi, distributed plan và Query DSL HTTP thực gửi tới Elasticsearch.

| Chỉ số | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| Commit runtime | `2162c006a7171d90305e21d1b2dd2fd47ef72b2a` | `9abc69bc79ad0de1563031d7d5dd533b9212b3b6` | `ffcf7b85a3b187b869cac8d8f7a56ec531b7630a` |
| Port loopback | 18091 | 18092 | 18093 |
| Kết quả đúng | 786/786 | 776/786 | 786/786 |
| Native exact | 72 | 128 | 160 |
| Script exact | 0 | 0 | 248 |
| Hybrid exact | 0 | 0 | 16 |
| Prefilter | 18 | 18 | 310 |
| Local | 692 | 636 | 48 |
| Fold | 4 | 4 | 4 |
| Không cần filter | 0 | 0 | 0 |

## Cách đọc và phạm vi

- **Exact**: không còn residual cho điều kiện được dịch. **Prefilter**: Elasticsearch lọc ứng viên, Trino kiểm tra residual. **Local**: không có điều kiện remote hữu ích cho phép lọc này. **Fold**: Trino rút gọn SQL, không cần scan.
- `doc` là tổng document `_source` thực chuyển về Trino trong các response search/scroll; không phải số document Elasticsearch xét nội bộ. `B` là byte HTTP response body chưa nén, gồm cả metadata của response search/count/scroll. Loại `_search_shards` khỏi số đo này. Query COUNT remote có thể trả kết quả mà không chuyển document nào.
- Các dòng lỗi được coi là ĐÚNG khi giữ cùng mã lỗi SQL như chạy local. Không coi query trả sai kết quả là một thành công pushdown. Thứ tự kết quả không quan trọng; LIMIT không ORDER BY được kiểm tra theo tập con hợp lệ và số dòng.
- Dữ liệu do bộ test tạo: 18 document biên và 10.000 document volume trên mỗi Elasticsearch. Có NULL/missing/array rỗng, nhiều phần tử khác nhau, Unicode/newline, signed zero/NaN sinh từ phép tính, overflow/division by zero, mapping không tương đương nguồn và timestamp trước epoch/leap day.
- Elasticsearch 7.17.27 và 8.11.3 dùng cùng schema và dữ liệu. Ba Trino có cùng tài nguyên (2 CPU, heap 4 GiB), scroll-size 1.000 và cùng backend qua listener proxy riêng. Bảng nhằm đo tính đúng và offload; không phải benchmark latency độc lập vì ba phiên bản chạy đồng thời.
- Branch mới bật hai catalog properties `elasticsearch.array-script-pushdown.enabled=true` và `elasticsearch.scalar-script-pushdown.enabled=true`; full-text SAFE. Baseline chỉ dùng properties mà phiên bản đó thực sự hỗ trợ. Các ca có nhãn scripts OFF tắt cả hai session properties để kiểm tra native/Plan 1.
- Unicode LOWER/UPPER giữ residual và cho ứng viên chưa chứng minh qua Trino. Mapping ignore_above/normalizer/không doc values, capture, nhiều cột hoặc overload chưa hỗ trợ được kiểm tra để fallback an toàn.
- Chỉ các index có prefix `expr_a2af4b7203e_` được tạo lại khi dùng `--seed`. Giới hạn script compilation được tăng tạm thời trong lab để chạy nhiều hình dạng biểu thức, rồi phục hồi trong `finally`; trước/sau lưu tại thư mục kết quả trên host.

## Sai biệt của baseline và regression đã sửa

Các ca REAL/DOUBLE của internal thiếu document `id=2` chứa `-0.0`: Elasticsearch phân biệt hai dấu zero trong native query, SQL coi chúng bằng nhau. Regex của internal thiếu `id=15` chứa `abc` kèm newline cuối, trong khi end anchor SQL vẫn khớp trước newline cuối. Mỗi ca được chạy trên cả ES7 và ES8.

- `internal` / `A-REAL07` / es8, es7: `SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x = CAST(0 AS REAL))`. missing=['[2]'], extra=[].
- `internal` / `A-REAL08` / es8, es7: `SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x >= CAST(0 AS REAL))`. missing=['[2]'], extra=[].
- `internal` / `A-DOUBLE07` / es8, es7: `SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x = CAST(0 AS DOUBLE))`. missing=['[2]'], extra=[].
- `internal` / `A-DOUBLE08` / es8, es7: `SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x >= CAST(0 AS DOUBLE))`. missing=['[2]'], extra=[].
- `internal` / `S-TXT27` / es8, es7: `SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'^abc$')`. missing=['[15]'], extra=[].

Lần chạy đầu được giữ trên host tại `results-initial/`. Nó phát hiện regex `$` bỏ sót newline cuối; probe bổ sung phát hiện lỗi amount của date_add trên phần tử NULL bị che. Branch mới nới candidate regex SAFE và giữ residual, đồng thời kiểm tra mọi đối số trong temporal null proof. Date_diff đảo hai timestamp cũng được thêm vào ma trận.

## Ví dụ trên 10.000 document

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| <code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 250,403 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 250,403 B |
| <code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 2500 dòng · 2500 doc / 250,403 B |
| <code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; mod(x,17)=0 AND mod(x,19)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Script exact (bool,filter,script) · 2500 dòng · 2500 doc / 250,397 B |
| <code>SELECT id FROM expr_a2af4b7203e_volume WHERE lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,205,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,205,002 B | ĐÚNG · Prefilter (bool,filter,script) · 2500 dòng · 5000 doc / 607,662 B |
| <code>SELECT count(*) FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 3,864,112 B | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 3,864,112 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 1 dòng · 0 doc / 163 B |

## Ma trận SQL đầy đủ

SQL chạy trong schema `default` của catalog tương ứng (`es8_compare` hoặc `es7_compare`). ID `A-` là array, `S-` là scalar. Nội dung Query DSL và full plan nằm trong [raw evidence](expression-comparison.json.gz); có thể chạy lại từ [file SQL](expression-comparison.sql) hoặc [harness](expression-compare.py).

### Elasticsearch 8.11.3


#### Native integral / ba giá trị

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-N01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x = 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B |
| **S-N01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n = 12</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B |
| **A-N02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt;&gt; 12)</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,211 B |
| **S-N02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt;&gt; 12</code> | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,255 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,255 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,255 B |
| **A-N03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,662 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,662 B |
| **S-N03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt; 10</code> | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,662 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,662 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,662 B |
| **A-N04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt;= 10)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,757 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,757 B |
| **S-N04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt;= 10</code> | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,757 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,757 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,757 B |
| **A-N05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B |
| **S-N05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B |
| **A-N06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt;= 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B |
| **S-N06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt;= 10</code> | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,304 B | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,304 B | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,304 B |
| **A-N07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x BETWEEN 5 AND 12)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,350 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,350 B |
| **S-N07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n BETWEEN 5 AND 12</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,123 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,123 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,123 B |
| **A-N08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT BETWEEN 5 AND 12)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,121 B |
| **S-N08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT BETWEEN 5 AND 12</code> | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 1,845 B | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 1,845 B | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 1,845 B |
| **A-N09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5, 12))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,074 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,074 B |
| **S-N09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12)</code> | ĐÚNG · Native exact (bool,filter,should,term) · 2 dòng · 2 doc / 803 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 803 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 803 B |
| **A-N10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5, 12, NULL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,074 B |
| **S-N10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12, NULL)</code> | ĐÚNG · Native exact (bool,filter,should,term) · 2 dòng · 2 doc / 803 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 803 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 803 B |
| **A-N11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT IN (5, 12))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,121 B |
| **S-N11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, 12)</code> | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,165 B | ĐÚNG · Native exact (bool,exists,filter,must_not,terms) · 13 dòng · 13 doc / 2,165 B | ĐÚNG · Native exact (bool,exists,filter,must_not,terms) · 13 dòng · 13 doc / 2,165 B |
| **A-N12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT IN (5, NULL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,match_none) · 0 dòng · 0 doc / 328 B |
| **S-N12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, NULL)</code> | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B |
| **A-N13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; NOT (x = 12))</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,211 B |
| **S-N13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (n = 12)</code> | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,255 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,255 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,255 B |
| **A-N14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NOT NULL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B |
| **S-N14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT NULL</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B |
| **A-N15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B |
| **S-N15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B |
| **A-N16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt; 0 OR x &gt; 10)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range,should) · 12 dòng · 12 doc / 1,984 B | ĐÚNG · Native exact (bool,filter,range,should) · 12 dòng · 12 doc / 1,984 B |
| **S-N16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt; 0 OR n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,616 B | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,616 B | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,616 B |
| **A-N17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND x &lt; 5)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 0 dòng · 0 doc / 328 B | ĐÚNG · Native exact (bool,filter,match_none) · 0 dòng · 0 doc / 328 B |
| **S-N17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND n &lt; 5</code> | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B |
| **A-N18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NOT DISTINCT FROM 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B |
| **S-N18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT DISTINCT FROM 12</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B |
| **A-N19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS DISTINCT FROM NULL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B |
| **S-N19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM NULL</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B |

#### Giữ semantics NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-NNULL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NULL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,901 B |
| **S-NNULL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NULL</code> | ĐÚNG · Native exact (bool,exists,must_not) · 3 dòng · 3 doc / 900 B | ĐÚNG · Native exact (bool,exists,filter,must_not) · 3 dòng · 3 doc / 900 B | ĐÚNG · Native exact (bool,exists,filter,must_not) · 3 dòng · 3 doc / 900 B |
| **A-NDISTINCT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS DISTINCT FROM 12)</code> | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B |
| **S-NDISTINCT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM 12</code> | ĐÚNG · Native exact (bool,exists,filter,must_not,range,should) · 17 dòng · 17 doc / 2,532 B | ĐÚNG · Native exact (bool,filter,must_not,term) · 17 dòng · 17 doc / 2,532 B | ĐÚNG · Native exact (bool,filter,must_not,term) · 17 dòng · 17 doc / 2,532 B |
| **A-NTRUE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRUE OR x &gt; 10)</code> | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 2,901 B |

#### Membership hiện có

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **M1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(numbers, 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B |
| **M2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[5,12])</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,074 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,074 B |
| **M3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(words, 'abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 893 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 893 B |
| **M4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(flags, true)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,931 B | ĐÚNG · Native exact (bool,filter,term) · 9 dòng · 9 doc / 1,571 B | ĐÚNG · Native exact (bool,filter,term) · 9 dòng · 9 doc / 1,571 B |
| **M5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[12,CAST(NULL AS INTEGER)])</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B |

#### Điều kiện cần; scripts OFF

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-P1 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND sin(x) &gt; 0)</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,range) · 5 dòng · 9 doc / 1,733 B |
| **S-P1 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND sin(n) &gt; 0</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,247 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,247 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,247 B |
| **A-P2 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR sin(x) &gt; 0)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,901 B |
| **S-P2 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 OR sin(n) &gt; 0</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,741 B |
| **A-P3 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; (x &gt; 10 AND sin(x) &gt; 0) OR (x &lt; 0 AND cos(x) &gt; 0))</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,range,should) · 8 dòng · 12 doc / 2,191 B |
| **S-P3 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE (n &gt; 10 AND sin(n) &gt; 0) OR (n &lt; 0 AND cos(n) &gt; 0)</code> | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,674 B | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,674 B | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,674 B |
| **A-P4 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 937 B |
| **S-P4 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B |

#### Số học integral / hybrid

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-I01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x + 1 &gt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-I01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n + 1 &gt; 10</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,348 B |
| **A-I02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x - 1 &gt; 10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,626 B |
| **S-I02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n - 1 &gt; 10</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,107 B |
| **A-I03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x * 2 &gt; 20)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,733 B |
| **S-I03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n * 2 &gt; 20</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,248 B |
| **A-I04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x / 2 &gt; 5)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,487 B |
| **S-I04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n / 2 &gt; 5</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,078 B |
| **A-I05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x % 2 = 0)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,571 B |
| **S-I05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n % 2 = 0</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,348 B |
| **A-I06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,2)=0)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,571 B |
| **S-I06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2)=0</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,347 B |
| **A-I07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,-1)=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,345 B |
| **S-I07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,-1)=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,345 B |
| **A-I08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; -x &gt; 5)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 777 B |
| **S-I08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -n &gt; 5</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 766 B |
| **A-I09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Hybrid exact (bool,filter,range,script,should) · 11 dòng · 11 doc / 1,803 B |
| **S-I09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,741 B | ĐÚNG · Hybrid exact (bool,filter,range,script,should) · 9 dòng · 9 doc / 1,618 B |
| **A-I10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,901 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 6 dòng · 6 doc / 1,257 B |
| **S-I10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND mod(n,2)=0</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,247 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,247 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 3 dòng · 3 doc / 940 B |
| **A-I11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; NOT (mod(x,2)=0))</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,801 B |
| **S-I11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (mod(n,2)=0)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,620 B |
| **A-I12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(x) &gt; 10 OR x = 2)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 1,889 B |
| **S-I12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n) &gt; 10 OR n = 2</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,486 B |

#### Độ rộng kiểu số

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-WIDTH-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -&gt; x + TINYINT '1' &gt; TINYINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,986 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,986 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,778 B |
| **S-WIDTH-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE tiny + TINYINT '1' &gt; TINYINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,792 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,792 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,366 B |
| **A-MOD-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -&gt; mod(x, TINYINT '2') = TINYINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,986 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,986 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,571 B |
| **S-MOD-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(tiny, TINYINT '2') = TINYINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,792 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,792 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,348 B |
| **A-WIDTH-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -&gt; x + SMALLINT '1' &gt; SMALLINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,003 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,003 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,787 B |
| **S-WIDTH-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE small + SMALLINT '1' &gt; SMALLINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,809 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,809 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,372 B |
| **A-MOD-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -&gt; mod(x, SMALLINT '2') = SMALLINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,003 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,003 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,571 B |
| **S-MOD-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(small, SMALLINT '2') = SMALLINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,809 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,809 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,348 B |
| **A-WIDTH-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -&gt; x + BIGINT '1' &gt; BIGINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,969 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,969 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,769 B |
| **S-WIDTH-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE big + BIGINT '1' &gt; BIGINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,775 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,775 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,360 B |
| **A-MOD-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -&gt; mod(x, BIGINT '2') = BIGINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,969 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,969 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,571 B |
| **S-MOD-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(big, BIGINT '2') = BIGINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,775 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,775 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,348 B |

#### Hàm số

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-MATH1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(x)&gt;5)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 1,889 B |
| **S-MATH1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n)&gt;5</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,589 B |
| **A-MATH2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; ceil(x)&gt;=1)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 12 dòng · 12 doc / 2,189 B |
| **S-MATH2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(n)&gt;=1</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 1,924 B |
| **A-MATH3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; floor(x)&lt;1)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,184 B |
| **S-MATH3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE floor(n)&lt;1</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,147 B |
| **A-MATH4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(x, -1)=10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,630 B |
| **S-MATH4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(n, -1)=10</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,301 B |
| **A-MATH5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; truncate(x)&gt;5)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-MATH5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(n)&gt;5</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,447 B |
| **A-MATH6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sign(x)=-1)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,080 B |
| **S-MATH6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(n)=-1</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,051 B |
| **A-MATH7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sqrt(abs(x))&gt;DOUBLE '2')</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,043 B |
| **S-MATH7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sqrt(abs(n))&gt;DOUBLE '2'</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 1,831 B |
| **A-MATH8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; pow(x,DOUBLE '2')&gt;DOUBLE '100')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,714 B |
| **S-MATH8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE pow(n,DOUBLE '2')&gt;DOUBLE '100'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 6 dòng · 6 doc / 1,346 B |

#### REAL/DOUBLE; signed zero; NaN

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-REAL01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-REAL01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-REAL02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x - CAST(1.5 AS REAL) &lt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,433 B |
| **S-REAL02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f - CAST(1.5 AS REAL) &lt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,433 B |
| **A-REAL03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x * CAST(2 AS REAL) &gt; CAST(1 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-REAL03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f * CAST(2 AS REAL) &gt; CAST(1 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-REAL04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / CAST(0 AS REAL) &gt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-REAL04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / CAST(0 AS REAL) &gt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-REAL05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; mod(x, CAST(2 AS REAL)) = CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,389 B |
| **S-REAL05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(f, CAST(2 AS REAL)) = CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,389 B |
| **A-REAL06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; -x &gt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **S-REAL06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -f &gt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **A-REAL07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x = CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,964 B | **SAI** · Native exact (bool,filter,term) · 3 dòng · 3 doc / 893 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **S-REAL07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f = CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **A-REAL08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x &gt;= CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,964 B | **SAI** · Native exact (bool,filter,range) · 12 dòng · 12 doc / 1,940 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,075 B |
| **S-REAL08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f &gt;= CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,075 B |
| **A-REAL09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x = CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f = CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x &lt;&gt; CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,346 B |
| **S-REAL10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f &lt;&gt; CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,346 B |
| **A-REAL11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x &lt; CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f &lt; CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x IS NOT DISTINCT FROM CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **S-REAL12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IS NOT DISTINCT FROM CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **A-REAL13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x IN (CAST(nan() AS REAL), CAST(2 AS REAL)))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IN (CAST(nan() AS REAL), CAST(2 AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; round(x,1)&gt;=CAST(1 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-REAL14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(f,1)&gt;=CAST(1 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-REAL15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; ceil(x)=floor(x))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,663 B |
| **S-REAL15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(f)=floor(f)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,663 B |
| **A-REAL16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; truncate(x)&gt;CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-REAL16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(f)&gt;CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-REAL17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; sign(x)=CAST(-1 AS REAL))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,964 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,964 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **S-REAL17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(f)=CAST(-1 AS REAL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,787 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,787 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **A-DOUBLE01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x + CAST(1.5 AS DOUBLE) &gt; CAST(2 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-DOUBLE01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d + CAST(1.5 AS DOUBLE) &gt; CAST(2 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-DOUBLE02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x - CAST(1.5 AS DOUBLE) &lt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,433 B |
| **S-DOUBLE02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d - CAST(1.5 AS DOUBLE) &lt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,433 B |
| **A-DOUBLE03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x * CAST(2 AS DOUBLE) &gt; CAST(1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-DOUBLE03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d * CAST(2 AS DOUBLE) &gt; CAST(1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-DOUBLE04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / CAST(0 AS DOUBLE) &gt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 1,851 B |
| **S-DOUBLE04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / CAST(0 AS DOUBLE) &gt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 1,851 B |
| **A-DOUBLE05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; mod(x, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,209 B |
| **S-DOUBLE05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(d, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,209 B |
| **A-DOUBLE06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; -x &gt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **S-DOUBLE06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -d &gt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **A-DOUBLE07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x = CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | **SAI** · Native exact (bool,filter,term) · 1 dòng · 1 doc / 713 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 848 B |
| **S-DOUBLE07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d = CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 848 B |
| **A-DOUBLE08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x &gt;= CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,024 B | **SAI** · Native exact (bool,filter,range) · 12 dòng · 12 doc / 1,940 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,075 B |
| **S-DOUBLE08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d &gt;= CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,075 B |
| **A-DOUBLE09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x = CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d = CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x &lt;&gt; CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,346 B |
| **S-DOUBLE10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d &lt;&gt; CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,346 B |
| **A-DOUBLE11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x &lt; CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d &lt; CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x IS NOT DISTINCT FROM CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 848 B |
| **S-DOUBLE12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IS NOT DISTINCT FROM CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 848 B |
| **A-DOUBLE13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE)))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; round(x,1)&gt;=CAST(1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-DOUBLE14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(d,1)&gt;=CAST(1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-DOUBLE15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; ceil(x)=floor(x))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,483 B |
| **S-DOUBLE15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(d)=floor(d)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,483 B |
| **A-DOUBLE16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; truncate(x)&gt;CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **S-DOUBLE16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(d)&gt;CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,669 B |
| **A-DOUBLE17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; sign(x)=CAST(-1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,024 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |
| **S-DOUBLE17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(d)=CAST(-1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,813 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 895 B |

#### DECIMAL và CAST

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DEC01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-DEC01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,348 B |
| **A-DEC02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) - DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,627 B |
| **S-DEC02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) - DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,107 B |
| **A-DEC03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) * DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-DEC03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) * DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,348 B |
| **A-DEC04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) / DECIMAL '3.00' &gt; DECIMAL '1.00')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 1,890 B |
| **S-DEC04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) / DECIMAL '3.00' &gt; DECIMAL '1.00'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,543 B |
| **A-DEC05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(CAST(x AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00')</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,556 B |
| **S-DEC05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(CAST(n AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,102 B |
| **A-DEC06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(CAST(x AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 731 B |
| **S-DEC06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(CAST(n AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 720 B |
| **A-DEC07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(30,10)) + DECIMAL '0.1234567890' &gt; DECIMAL '10.1234567890')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-DEC07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(30,10)) + DECIMAL '0.1234567890' &gt; DECIMAL '10.1234567890'</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,248 B |
| **A-DEC08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(CAST(x AS DECIMAL(12,2)))&gt;DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 1,889 B |
| **S-DEC08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(CAST(n AS DECIMAL(12,2)))&gt;DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,390 B |
| **A-DEC09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sign(CAST(x AS DECIMAL(12,2)))=DECIMAL '-1')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,080 B |
| **S-DEC09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(CAST(n AS DECIMAL(12,2)))=DECIMAL '-1'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,051 B |
| **A-DEC10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; floor(CAST(x AS DECIMAL(12,2)))=ceil(CAST(x AS DECIMAL(12,2))))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,595 B |
| **S-DEC10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE floor(CAST(n AS DECIMAL(12,2)))=ceil(CAST(n AS DECIMAL(12,2)))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,447 B |
| **A-DEC11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(CAST(x AS DECIMAL(12,2)) AS INTEGER)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-DEC11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS INTEGER)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,248 B |
| **A-DEC12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(CAST(x AS DECIMAL(12,2)) AS DOUBLE)&gt;DOUBLE '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,577 B |
| **S-DEC12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS DOUBLE)&gt;DOUBLE '10'</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B |
| **A-DEC13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS SMALLINT)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,734 B |
| **S-DEC13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS SMALLINT)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,248 B |
| **A-DEC14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRY_CAST(x AS TINYINT)&gt;10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,488 B |
| **S-DEC14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT)&gt;10</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,122 B |

#### COALESCE / NULLIF / TRY_CAST / CASE

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-NULL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(x,0)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,578 B |
| **S-NULL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,0)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,212 B |
| **A-NULL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(x,100)&gt;10)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B |
| **S-NULL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,100)&gt;10</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,488 B |
| **A-NULL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; nullif(x,0)&gt;1)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 1,854 B |
| **S-NULL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE nullif(n,0)&gt;1</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,717 B |
| **A-NULL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(nullif(x,0),1)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,578 B |
| **S-NULL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(nullif(n,0),1)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,212 B |
| **A-NULL5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRY_CAST(x AS TINYINT) IS NULL)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,901 B |
| **S-NULL5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT) IS NULL</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 991 B |
| **A-NULL6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN false ELSE x&gt;10 END)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,578 B |
| **S-NULL6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN false ELSE n&gt;10 END</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,212 B |
| **A-NULL7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN true ELSE x&gt;10 END)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,901 B |
| **S-NULL7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN true ELSE n&gt;10 END</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,488 B |
| **A-NULL8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; IF(x&gt;10,true,false))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,578 B |
| **S-NULL8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE IF(n&gt;10,true,false)</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,211 B |

#### BOOLEAN

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-BOOL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; x OR NOT x)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,931 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 2,931 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,346 B |
| **S-BOOL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE flag OR NOT flag</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,345 B |
| **A-BOOL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; coalesce(x,false))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,931 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,931 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,572 B |
| **S-BOOL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,false)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,833 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,833 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,529 B |
| **A-BOOL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; coalesce(x,true))</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,931 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,931 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 2,931 B |
| **S-BOOL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,true)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,833 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 2,833 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 1,807 B |
| **A-BOOL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; x IS NOT DISTINCT FROM true)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,931 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 2,931 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,572 B |
| **S-BOOL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE flag IS NOT DISTINCT FROM true</code> | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,528 B |

#### Keyword / Unicode / chuỗi

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TXT01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'abc'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,030 B |
| **S-TXT01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc')</code> | ĐÚNG · Prefilter (bool,filter,range) · 2 dòng · 2 doc / 878 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 2 dòng · 2 doc / 850 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 2 dòng · 2 doc / 850 B |
| **A-TXT02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE 'ab%')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,030 B |
| **S-TXT02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'ab%'</code> | ĐÚNG · Native exact (bool,filter,must,range,regexp) · 3 dòng · 3 doc / 940 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 940 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 940 B |
| **A-TXT03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE '%b%')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,528 B |
| **S-TXT03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%b%'</code> | ĐÚNG · Native exact (bool,filter,must,regexp) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,528 B |
| **A-TXT04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE 'a_b')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,regexp) · 3 dòng · 3 doc / 937 B |
| **S-TXT04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'a_b'</code> | ĐÚNG · Native exact (bool,filter,must,range,regexp) · 3 dòng · 3 doc / 937 B | ĐÚNG · Native exact (bool,filter,range,regexp) · 3 dòng · 3 doc / 937 B | ĐÚNG · Native exact (bool,filter,range,regexp) · 3 dòng · 3 doc / 937 B |
| **A-TXT05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE '%a!_%' ESCAPE '!')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 713 B |
| **S-TXT05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%a!_%' ESCAPE '!'</code> | ĐÚNG · Native exact (bool,filter,must,regexp) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 713 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 713 B |
| **A-TXT06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'a') AND starts_with(x,'ab'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,030 B |
| **S-TXT06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'a') AND starts_with(word,'ab')</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 3 doc / 980 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 940 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 940 B |
| **A-TXT07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'ab') OR x='nice')</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,prefix,should,term) · 5 dòng · 5 doc / 1,164 B |
| **S-TXT07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'ab') OR word='nice'</code> | ĐÚNG · Prefilter (bool,filter,range,should,term) · 4 dòng · 4 doc / 1,128 B | ĐÚNG · Prefilter (bool,filter,range,should,term) · 4 dòng · 4 doc / 1,128 B | ĐÚNG · Native exact (bool,filter,prefix,range,should,term) · 4 dòng · 4 doc / 1,074 B |
| **A-TXT08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; length(x)&gt;2)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,070 B |
| **S-TXT08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)&gt;2</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,071 B |
| **A-TXT09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; ends_with(x,'b'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **S-TXT09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ends_with(word,'b')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,028 B |
| **A-TXT10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; strpos(x,'😀')&gt;0)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 851 B |
| **S-TXT10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE strpos(word,'😀')&gt;0</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 851 B |
| **A-TXT11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; replace(x,'_','😀') LIKE '%😀%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,020 B |
| **S-TXT11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'_','😀') LIKE '%😀%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,000 B |
| **A-TXT12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; replace(x,'','!') LIKE '%!s!á!%')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 730 B |
| **S-TXT12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'','!') LIKE '%!s!á!%'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 727 B |
| **A-TXT13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; concat(substr(x,1,1),'Z')='aZ')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,391 B |
| **S-TXT13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE concat(substr(word,1,1),'Z')='aZ'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,345 B |
| **A-TXT14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; (x &#124;&#124; 'Z')='abcZ')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 959 B |
| **S-TXT14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE (word &#124;&#124; 'Z')='abcZ'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 727 B |
| **A-TXT15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; substr(x,-2,1)='b')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 959 B |
| **S-TXT15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE substr(word,-2,1)='b'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 727 B |
| **A-TXT16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; substring(x,2)='bc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 3 dòng · 3 doc / 894 B |
| **S-TXT16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE substring(word,2)='bc'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 714 B |
| **A-TXT17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; trim(x)='abc')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 6 dòng · 6 doc / 1,305 B |
| **S-TXT17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE trim(word)='abc'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,125 B |
| **A-TXT18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; ltrim(x)='abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 3 dòng · 3 doc / 894 B |
| **S-TXT18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ltrim(word)='abc'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 714 B |
| **A-TXT19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; rtrim(x)='abc')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,031 B |
| **S-TXT19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE rtrim(word)='abc'</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 851 B |
| **A-TXT20**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,780 B |
| **S-TXT20**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,731 B |
| **A-TXT21**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; upper(x) LIKE '%SÁ%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,780 B |
| **S-TXT21**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE upper(word) LIKE '%SÁ%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,731 B |
| **A-TXT22**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x &gt; '')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 2,993 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 717 B |
| **S-TXT22**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word &gt; ''</code> | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 716 B | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 716 B | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 716 B |
| **A-TXT23**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; length(x)&gt;2 AND starts_with(x,'a'))</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Hybrid exact (bool,filter,prefix,script) · 6 dòng · 6 doc / 1,255 B |
| **S-TXT23**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)&gt;2 AND starts_with(word,'a')</code> | ĐÚNG · Prefilter (bool,filter,range) · 5 dòng · 6 doc / 1,344 B | ĐÚNG · Prefilter (bool,filter,prefix,range) · 5 dòng · 6 doc / 1,344 B | ĐÚNG · Hybrid exact (bool,filter,prefix,range,script) · 5 dòng · 5 doc / 1,165 B |
| **A-TXT24**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'abc') AND lower(x) LIKE '%b%')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 12 doc / 2,277 B |
| **S-TXT24**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc') AND lower(word) LIKE '%b%'</code> | ĐÚNG · Prefilter (bool,filter,range) · 2 dòng · 2 doc / 878 B | ĐÚNG · Prefilter (bool,filter,prefix,range) · 2 dòng · 2 doc / 878 B | ĐÚNG · Prefilter (bool,filter,range,script) · 2 dòng · 2 doc / 879 B |
| **A-TXT25**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'abc'))</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 2,993 B | ĐÚNG · Native exact (bool,filter,regexp) · 6 dòng · 6 doc / 1,304 B |
| **S-TXT25**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'abc')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,regexp) · 4 dòng · 4 doc / 1,188 B | ĐÚNG · Prefilter (bool,filter,regexp) · 4 dòng · 4 doc / 1,188 B |
| **A-TXT26**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'(?i)^abc'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B |
| **S-TXT26**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'(?i)^abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B |
| **A-TXT27**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'^abc$'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 2,993 B |
| **S-TXT27**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'^abc$')</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | **SAI** · Prefilter (bool,filter,regexp) · 1 dòng · 1 doc / 726 B | ĐÚNG · Prefilter (bool,filter,regexp) · 2 dòng · 2 doc / 878 B |

#### Mapping guard / doc values

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-MAP-IGNORE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(ignored_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,129 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,129 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,129 B |
| **S-MAP-IGNORE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(ignored_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,033 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,033 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,033 B |
| **A-MAP-NORMAL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(normalized_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,180 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,180 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,180 B |
| **S-MAP-NORMAL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(normalized_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,084 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,084 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,084 B |
| **A-MAP-NODV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(nodv_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,078 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,078 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,078 B |
| **S-MAP-NODV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(nodv_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,982 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,982 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,982 B |
| **A-MAP-DOCONLY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doconly_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,129 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,129 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,843 B |
| **S-MAP-DOCONLY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(doconly_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,033 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,033 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,794 B |

#### Tên field phân biệt hoa thường

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-CASEPATH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(reviewcomment)&gt;2</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,050 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,050 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,070 B |

#### TIMESTAMP(3) UTC / extract

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DATE1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; year(x)=2024)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,438 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,438 B |
| **S-DATE1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE year(dt)=2024</code> | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,438 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,438 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,438 B |
| **A-DATE2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; month(x)=2)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,275 B |
| **S-DATE2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE month(dt)=2</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,415 B |
| **A-DATE3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day(x)=29)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,275 B |
| **S-DATE3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day(dt)=29</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,415 B |
| **A-DATE4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; hour(x)=12)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 800 B |
| **S-DATE4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE hour(dt)=12</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 835 B |
| **A-DATE5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; minute(x)=59)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 2 doc / 975 B |
| **S-DATE5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE minute(dt)=59</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 2 doc / 1,045 B |
| **A-DATE6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; second(x)=56)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 800 B |
| **S-DATE6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE second(dt)=56</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 835 B |
| **A-DATE7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day_of_week(x) BETWEEN 1 AND 5)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,318 B |
| **S-DATE7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_week(dt) BETWEEN 1 AND 5</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,703 B |
| **A-DATE8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day_of_year(x)=60)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,275 B |
| **S-DATE8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_year(dt)=60</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,415 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('millisecond',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,837 B |
| **S-TRUNC-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('millisecond',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,117 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('millisecond',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,230 B |
| **S-ADD-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('millisecond',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,370 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('second',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,837 B |
| **S-TRUNC-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('second',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,117 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('second',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,230 B |
| **S-ADD-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('second',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,370 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('minute',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,837 B |
| **S-TRUNC-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('minute',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,117 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('minute',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,230 B |
| **S-ADD-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('minute',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,370 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('hour',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('hour',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('hour',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,230 B |
| **S-ADD-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('hour',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,370 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('day',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('day',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('day',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,406 B |
| **S-ADD-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,581 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('week',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('week',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('week',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,578 B |
| **S-ADD-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('week',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,788 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('month',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('month',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('month',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,578 B |
| **S-ADD-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('month',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,788 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('quarter',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('quarter',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('quarter',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,965 B |
| **S-ADD-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('quarter',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,280 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('year',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |
| **S-TRUNC-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('year',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,528 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('year',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,965 B |
| **S-ADD-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('year',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,280 B |

#### date_diff đơn vị cố định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('millisecond',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('millisecond',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |
| **A-DIFF-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('second',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('second',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |
| **A-DIFF-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('minute',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('minute',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |
| **A-DIFF-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('hour',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('hour',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |
| **A-DIFF-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('day',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |
| **A-DIFF-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('week',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('week',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |

#### date_diff đảo đối số; NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-REVERSE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)&lt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,924 B |
| **S-DIFF-REVERSE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',dt)&lt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,449 B |

#### Lỗi trên phần tử NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DATE-NULL-ERROR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('day',coalesce(year(x),BIGINT '9223372036854775807')+BIGINT '1',x)&lt;TIMESTAMP '0001-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,226 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,226 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,226 B |
| **S-DATE-NULL-ERROR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',coalesce(year(dt),BIGINT '9223372036854775807')+BIGINT '1',dt)&lt;TIMESTAMP '0001-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,745 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,745 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 3 doc / 921 B |

#### Fallback được chủ định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-MONTH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('month',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B |
| **S-DIFF-MONTH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('month',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,745 B |
| **A-CAPTURE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -&gt; x&gt;n)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,020 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,020 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,020 B |

#### Điều kiện cần với biến capture

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-CAPTURE-PREFILTER**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -&gt; x&gt;10 AND x&gt;n)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,020 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,020 B | ĐÚNG · Prefilter (bool,filter,range) · 7 dòng · 9 doc / 1,794 B |

#### Fallback được chủ định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-MULTICOL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n+small&gt;10</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,928 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,928 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 2,928 B |

#### Ghép các cột scalar

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-MIXED-AND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n&gt;10 AND lower(word) LIKE '%sá%'</code> | ĐÚNG · Prefilter (bool,filter,range) · 1 dòng · 5 doc / 1,297 B | ĐÚNG · Prefilter (bool,filter,range) · 1 dòng · 5 doc / 1,297 B | ĐÚNG · Prefilter (bool,filter,range,script) · 1 dòng · 2 doc / 939 B |
| **S-MIXED-OR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n&gt;10 OR lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,016 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,016 B | ĐÚNG · Prefilter (bool,filter,range,script,should) · 7 dòng · 11 doc / 2,164 B |

#### Resource budget fallback

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-BUDGET**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199))</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 2,901 B | ĐÚNG · Script exact (bool,filter,script) · 14 dòng · 14 doc / 2,212 B |
| **S-BUDGET**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,741 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 2,741 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,075 B |

#### Bảo toàn lỗi SQL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ERR-OVERFLOW**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -&gt; x + 1 &gt; 0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,064 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,064 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,971 B |
| **S-ERR-OVERFLOW**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_int + 1 &gt; 0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,013 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,013 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,920 B |
| **A-ERR-DIV0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x / 0 &gt; 0)</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,901 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,595 B |
| **S-ERR-DIV0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n / 0 &gt; 0</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,741 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,446 B |
| **A-ERR-MOD0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,0)=0)</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,869 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,595 B |
| **S-ERR-MOD0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,0)=0</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,741 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 2,741 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,446 B |
| **A-ERR-LONGDIV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -&gt; x / BIGINT '-1' &gt; 0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,251 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,251 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,158 B |
| **S-ERR-LONGDIV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_long / BIGINT '-1' &gt; 0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,200 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,200 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,108 B |
| **A-ERR-ABS**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -&gt; abs(x)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,251 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,251 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,159 B |
| **S-ERR-ABS**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(error_long)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,200 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,200 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,108 B |
| **A-ERR-CAST**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -&gt; CAST(x AS TINYINT)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,064 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,064 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,972 B |
| **S-ERR-CAST**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(error_int AS TINYINT)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,013 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,013 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,921 B |
| **A-ERR-ROUND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(x,-19)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,901 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,901 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 15 doc / 2,595 B |
| **S-ERR-ROUND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(n,-19)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,741 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,742 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 15 doc / 2,447 B |
| **A-ERR-TINY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_tinys, x -&gt; x + TINYINT '1' &gt; TINYINT '0')</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,962 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,962 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,869 B |
| **S-ERR-TINY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_tiny + TINYINT '1' &gt; TINYINT '0'</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,911 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 2,911 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 2,818 B |

#### LIMIT sau residual

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **LIMIT-2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 2</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 8 doc / 1,730 B |
| **A-LIMIT-2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -&gt; lower(x) LIKE '%sá%') LIMIT 2</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 8 doc / 1,779 B |
| **LIMIT-3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 3</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,897 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,730 B |
| **A-LIMIT-3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -&gt; lower(x) LIKE '%sá%') LIMIT 3</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 2,993 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,779 B |

#### 10.000 document

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **VOL-NATIVE**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 250,403 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 250,403 B |
| **VOL-HYBRID**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 2500 dòng · 2500 doc / 250,403 B |
| **VOL-SCRIPT**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; mod(x,17)=0 AND mod(x,19)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 3,963,002 B | ĐÚNG · Script exact (bool,filter,script) · 2500 dòng · 2500 doc / 250,397 B |
| **VOL-LOWER**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,205,002 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,205,002 B | ĐÚNG · Prefilter (bool,filter,script) · 2500 dòng · 5000 doc / 607,662 B |

#### 10.000 document + COUNT

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **VOL-COUNT**<br><code>SELECT count(*) FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 3,864,112 B | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 3,864,112 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 1 dòng · 0 doc / 163 B |
### Elasticsearch 7.17.27


#### Native integral / ba giá trị

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-N01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x = 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B |
| **S-N01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n = 12</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B |
| **A-N02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt;&gt; 12)</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,446 B |
| **S-N02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt;&gt; 12</code> | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,490 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,490 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,490 B |
| **A-N03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,822 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,822 B |
| **S-N03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt; 10</code> | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,822 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,822 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,822 B |
| **A-N04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt;= 10)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,932 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,932 B |
| **S-N04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt;= 10</code> | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,932 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,932 B | ĐÚNG · Native exact (bool,filter,range) · 10 dòng · 10 doc / 1,932 B |
| **A-N05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B |
| **S-N05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B |
| **A-N06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt;= 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B |
| **S-N06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt;= 10</code> | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,419 B | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,419 B | ĐÚNG · Native exact (bool,filter,range) · 6 dòng · 6 doc / 1,419 B |
| **A-N07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x BETWEEN 5 AND 12)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,480 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,480 B |
| **S-N07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n BETWEEN 5 AND 12</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,223 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,223 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,223 B |
| **A-N08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT BETWEEN 5 AND 12)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,341 B |
| **S-N08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT BETWEEN 5 AND 12</code> | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 2,020 B | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 2,020 B | ĐÚNG · Native exact (bool,filter,range,should) · 10 dòng · 10 doc / 2,020 B |
| **A-N09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5, 12))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,159 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,159 B |
| **S-N09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12)</code> | ĐÚNG · Native exact (bool,filter,should,term) · 2 dòng · 2 doc / 858 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 858 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 858 B |
| **A-N10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5, 12, NULL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,159 B |
| **S-N10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12, NULL)</code> | ĐÚNG · Native exact (bool,filter,should,term) · 2 dòng · 2 doc / 858 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 858 B | ĐÚNG · Native exact (bool,filter,terms) · 2 dòng · 2 doc / 858 B |
| **A-N11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT IN (5, 12))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,341 B |
| **S-N11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, 12)</code> | ĐÚNG · Native exact (bool,filter,range,should) · 13 dòng · 13 doc / 2,385 B | ĐÚNG · Native exact (bool,exists,filter,must_not,terms) · 13 dòng · 13 doc / 2,385 B | ĐÚNG · Native exact (bool,exists,filter,must_not,terms) · 13 dòng · 13 doc / 2,385 B |
| **A-N12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x NOT IN (5, NULL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,match_none) · 0 dòng · 0 doc / 328 B |
| **S-N12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, NULL)</code> | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B |
| **A-N13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; NOT (x = 12))</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,446 B |
| **S-N13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (n = 12)</code> | ĐÚNG · Native exact (bool,filter,range,should) · 14 dòng · 14 doc / 2,490 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,490 B | ĐÚNG · Native exact (bool,exists,filter,must_not,term) · 14 dòng · 14 doc / 2,490 B |
| **A-N14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NOT NULL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B |
| **S-N14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT NULL</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B |
| **A-N15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B |
| **S-N15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B |
| **A-N16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &lt; 0 OR x &gt; 10)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range,should) · 12 dòng · 12 doc / 2,189 B | ĐÚNG · Native exact (bool,filter,range,should) · 12 dòng · 12 doc / 2,189 B |
| **S-N16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &lt; 0 OR n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,761 B | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,761 B | ĐÚNG · Native exact (bool,filter,range,should) · 8 dòng · 8 doc / 1,761 B |
| **A-N17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND x &lt; 5)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 0 dòng · 0 doc / 328 B | ĐÚNG · Native exact (bool,filter,match_none) · 0 dòng · 0 doc / 328 B |
| **S-N17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND n &lt; 5</code> | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B | ĐÚNG · Fold (—) · 0 dòng · 0 doc / 0 B |
| **A-N18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NOT DISTINCT FROM 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B |
| **S-N18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT DISTINCT FROM 12</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B |
| **A-N19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS DISTINCT FROM NULL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B |
| **S-N19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM NULL</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B |

#### Giữ semantics NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-NNULL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS NULL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,196 B |
| **S-NNULL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NULL</code> | ĐÚNG · Native exact (bool,exists,must_not) · 3 dòng · 3 doc / 970 B | ĐÚNG · Native exact (bool,exists,filter,must_not) · 3 dòng · 3 doc / 970 B | ĐÚNG · Native exact (bool,exists,filter,must_not) · 3 dòng · 3 doc / 970 B |
| **A-NDISTINCT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IS DISTINCT FROM 12)</code> | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B |
| **S-NDISTINCT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM 12</code> | ĐÚNG · Native exact (bool,exists,filter,must_not,range,should) · 17 dòng · 17 doc / 2,812 B | ĐÚNG · Native exact (bool,filter,must_not,term) · 17 dòng · 17 doc / 2,812 B | ĐÚNG · Native exact (bool,filter,must_not,term) · 17 dòng · 17 doc / 2,812 B |
| **A-NTRUE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRUE OR x &gt; 10)</code> | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 16 dòng · 18 doc / 3,196 B |

#### Membership hiện có

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **M1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(numbers, 12)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B |
| **M2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[5,12])</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,159 B | ĐÚNG · Native exact (bool,filter,terms) · 4 dòng · 4 doc / 1,159 B |
| **M3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(words, 'abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 963 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 963 B |
| **M4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE contains(flags, true)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Native exact (bool,filter,term) · 9 dòng · 9 doc / 1,731 B | ĐÚNG · Native exact (bool,filter,term) · 9 dòng · 9 doc / 1,731 B |
| **M5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[12,CAST(NULL AS INTEGER)])</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B |

#### Điều kiện cần; scripts OFF

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-P1 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND sin(x) &gt; 0)</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,range) · 5 dòng · 9 doc / 1,893 B |
| **S-P1 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND sin(n) &gt; 0</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,347 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,347 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,347 B |
| **A-P2 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR sin(x) &gt; 0)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,196 B |
| **S-P2 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 OR sin(n) &gt; 0</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,036 B |
| **A-P3 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; (x &gt; 10 AND sin(x) &gt; 0) OR (x &lt; 0 AND cos(x) &gt; 0))</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,range,should) · 8 dòng · 12 doc / 2,396 B |
| **S-P3 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE (n &gt; 10 AND sin(n) &gt; 0) OR (n &lt; 0 AND cos(n) &gt; 0)</code> | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,819 B | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,819 B | ĐÚNG · Prefilter (bool,filter,range,should) · 6 dòng · 8 doc / 1,819 B |
| **A-P4 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,term) · 3 dòng · 3 doc / 1,007 B |
| **S-P4 · scripts OFF**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n &gt; 10</code> | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B |

#### Số học integral / hybrid

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-I01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x + 1 &gt; 10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,893 B |
| **S-I01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n + 1 &gt; 10</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,463 B |
| **A-I02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x - 1 &gt; 10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,771 B |
| **S-I02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n - 1 &gt; 10</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,192 B |
| **A-I03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x * 2 &gt; 20)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,893 B |
| **S-I03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n * 2 &gt; 20</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,348 B |
| **A-I04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x / 2 &gt; 5)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,632 B |
| **S-I04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n / 2 &gt; 5</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,163 B |
| **A-I05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x % 2 = 0)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,731 B |
| **S-I05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n % 2 = 0</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,478 B |
| **A-I06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,2)=0)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,731 B |
| **S-I06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2)=0</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,477 B |
| **A-I07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,-1)=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,595 B |
| **S-I07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,-1)=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,595 B |
| **A-I08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; -x &gt; 5)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 816 B |
| **S-I08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -n &gt; 5</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 806 B |
| **A-I09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Hybrid exact (bool,filter,range,script,should) · 11 dòng · 11 doc / 1,993 B |
| **S-I09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,036 B | ĐÚNG · Hybrid exact (bool,filter,range,script,should) · 9 dòng · 9 doc / 1,778 B |
| **A-I10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,196 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 6 dòng · 6 doc / 1,372 B |
| **S-I10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n &gt; 10 AND mod(n,2)=0</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,347 B | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 5 doc / 1,347 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 3 dòng · 3 doc / 1,010 B |
| **A-I11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; NOT (mod(x,2)=0))</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,976 B |
| **S-I11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (mod(n,2)=0)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,765 B |
| **A-I12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(x) &gt; 10 OR x = 2)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 2,064 B |
| **S-I12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n) &gt; 10 OR n = 2</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,616 B |

#### Độ rộng kiểu số

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-WIDTH-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -&gt; x + TINYINT '1' &gt; TINYINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,281 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,281 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,938 B |
| **S-WIDTH-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE tiny + TINYINT '1' &gt; TINYINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,087 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,087 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,481 B |
| **A-MOD-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -&gt; mod(x, TINYINT '2') = TINYINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,281 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,281 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,731 B |
| **S-MOD-TINYINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(tiny, TINYINT '2') = TINYINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,087 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,087 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,478 B |
| **A-WIDTH-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -&gt; x + SMALLINT '1' &gt; SMALLINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,298 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,298 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,947 B |
| **S-WIDTH-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE small + SMALLINT '1' &gt; SMALLINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,104 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,104 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,487 B |
| **A-MOD-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -&gt; mod(x, SMALLINT '2') = SMALLINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,298 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,298 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,731 B |
| **S-MOD-SMALLINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(small, SMALLINT '2') = SMALLINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,104 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,104 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,478 B |
| **A-WIDTH-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -&gt; x + BIGINT '1' &gt; BIGINT '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,264 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,264 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,929 B |
| **S-WIDTH-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE big + BIGINT '1' &gt; BIGINT '10'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,070 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,070 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,475 B |
| **A-MOD-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -&gt; mod(x, BIGINT '2') = BIGINT '0')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,264 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,264 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,731 B |
| **S-MOD-BIGINT**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(big, BIGINT '2') = BIGINT '0'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,070 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,070 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,478 B |

#### Hàm số

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-MATH1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(x)&gt;5)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 2,064 B |
| **S-MATH1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n)&gt;5</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,734 B |
| **A-MATH2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; ceil(x)&gt;=1)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 12 dòng · 12 doc / 2,394 B |
| **S-MATH2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(n)&gt;=1</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,114 B |
| **A-MATH3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; floor(x)&lt;1)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,269 B |
| **S-MATH3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE floor(n)&lt;1</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,232 B |
| **A-MATH4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(x, -1)=10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,775 B |
| **S-MATH4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(n, -1)=10</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,416 B |
| **A-MATH5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; truncate(x)&gt;5)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-MATH5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(n)&gt;5</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,577 B |
| **A-MATH6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sign(x)=-1)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,150 B |
| **S-MATH6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(n)=-1</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,121 B |
| **A-MATH7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sqrt(abs(x))&gt;DOUBLE '2')</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,233 B |
| **S-MATH7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sqrt(abs(n))&gt;DOUBLE '2'</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 2,006 B |
| **A-MATH8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; pow(x,DOUBLE '2')&gt;DOUBLE '100')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,889 B |
| **S-MATH8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE pow(n,DOUBLE '2')&gt;DOUBLE '100'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 6 dòng · 6 doc / 1,461 B |

#### REAL/DOUBLE; signed zero; NaN

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-REAL01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-REAL01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-REAL02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x - CAST(1.5 AS REAL) &lt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,563 B |
| **S-REAL02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f - CAST(1.5 AS REAL) &lt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,563 B |
| **A-REAL03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x * CAST(2 AS REAL) &gt; CAST(1 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-REAL03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f * CAST(2 AS REAL) &gt; CAST(1 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-REAL04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / CAST(0 AS REAL) &gt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-REAL04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / CAST(0 AS REAL) &gt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-REAL05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; mod(x, CAST(2 AS REAL)) = CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,519 B |
| **S-REAL05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(f, CAST(2 AS REAL)) = CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,519 B |
| **A-REAL06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; -x &gt; CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **S-REAL06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -f &gt; CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **A-REAL07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x = CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,259 B | **SAI** · Native exact (bool,filter,term) · 3 dòng · 3 doc / 963 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **S-REAL07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f = CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **A-REAL08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x &gt;= CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,259 B | **SAI** · Native exact (bool,filter,range) · 12 dòng · 12 doc / 2,145 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,295 B |
| **S-REAL08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f &gt;= CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,295 B |
| **A-REAL09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x = CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f = CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x &lt;&gt; CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,596 B |
| **S-REAL10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f &lt;&gt; CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,596 B |
| **A-REAL11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x &lt; CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f &lt; CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x IS NOT DISTINCT FROM CAST(nan() AS REAL))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **S-REAL12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IS NOT DISTINCT FROM CAST(nan() AS REAL)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **A-REAL13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; x / x IN (CAST(nan() AS REAL), CAST(2 AS REAL)))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-REAL13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IN (CAST(nan() AS REAL), CAST(2 AS REAL))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-REAL14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; round(x,1)&gt;=CAST(1 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-REAL14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(f,1)&gt;=CAST(1 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-REAL15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; ceil(x)=floor(x))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,823 B |
| **S-REAL15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(f)=floor(f)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,823 B |
| **A-REAL16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; truncate(x)&gt;CAST(0 AS REAL))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-REAL16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(f)&gt;CAST(0 AS REAL)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-REAL17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -&gt; sign(x)=CAST(-1 AS REAL))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,259 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,259 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **S-REAL17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(f)=CAST(-1 AS REAL)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,082 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,082 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **A-DOUBLE01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x + CAST(1.5 AS DOUBLE) &gt; CAST(2 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-DOUBLE01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d + CAST(1.5 AS DOUBLE) &gt; CAST(2 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-DOUBLE02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x - CAST(1.5 AS DOUBLE) &lt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,563 B |
| **S-DOUBLE02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d - CAST(1.5 AS DOUBLE) &lt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,563 B |
| **A-DOUBLE03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x * CAST(2 AS DOUBLE) &gt; CAST(1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-DOUBLE03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d * CAST(2 AS DOUBLE) &gt; CAST(1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-DOUBLE04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / CAST(0 AS DOUBLE) &gt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 2,041 B |
| **S-DOUBLE04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / CAST(0 AS DOUBLE) &gt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 2,041 B |
| **A-DOUBLE05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; mod(x, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,309 B |
| **S-DOUBLE05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(d, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,309 B |
| **A-DOUBLE06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; -x &gt; CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **S-DOUBLE06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE -d &gt; CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **A-DOUBLE07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x = CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | **SAI** · Native exact (bool,filter,term) · 1 dòng · 1 doc / 753 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 903 B |
| **S-DOUBLE07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d = CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 903 B |
| **A-DOUBLE08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x &gt;= CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,319 B | **SAI** · Native exact (bool,filter,range) · 12 dòng · 12 doc / 2,145 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,295 B |
| **S-DOUBLE08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d &gt;= CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,295 B |
| **A-DOUBLE09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x = CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d = CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x &lt;&gt; CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,596 B |
| **S-DOUBLE10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d &lt;&gt; CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,596 B |
| **A-DOUBLE11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x &lt; CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d &lt; CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x IS NOT DISTINCT FROM CAST(nan() AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 903 B |
| **S-DOUBLE12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IS NOT DISTINCT FROM CAST(nan() AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 903 B |
| **A-DOUBLE13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; x / x IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE)))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **S-DOUBLE13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE))</code> | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 0 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 0 dòng · 0 doc / 329 B |
| **A-DOUBLE14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; round(x,1)&gt;=CAST(1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-DOUBLE14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(d,1)&gt;=CAST(1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-DOUBLE15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; ceil(x)=floor(x))</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,613 B |
| **S-DOUBLE15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(d)=floor(d)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 7 dòng · 7 doc / 1,613 B |
| **A-DOUBLE16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; truncate(x)&gt;CAST(0 AS DOUBLE))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **S-DOUBLE16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(d)&gt;CAST(0 AS DOUBLE)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,829 B |
| **A-DOUBLE17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -&gt; sign(x)=CAST(-1 AS DOUBLE))</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,319 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |
| **S-DOUBLE17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(d)=CAST(-1 AS DOUBLE)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,108 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 950 B |

#### DECIMAL và CAST

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DEC01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-DEC01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,463 B |
| **A-DEC02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) - DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,772 B |
| **S-DEC02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) - DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,192 B |
| **A-DEC03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) * DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-DEC03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) * DECIMAL '1.25' &gt; DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,463 B |
| **A-DEC04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) / DECIMAL '3.00' &gt; DECIMAL '1.00')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 2,065 B |
| **S-DEC04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) / DECIMAL '3.00' &gt; DECIMAL '1.00'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,688 B |
| **A-DEC05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(CAST(x AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00')</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 7 dòng · 7 doc / 1,686 B |
| **S-DEC05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(CAST(n AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,187 B |
| **A-DEC06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(CAST(x AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 771 B |
| **S-DEC06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(CAST(n AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 760 B |
| **A-DEC07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(30,10)) + DECIMAL '0.1234567890' &gt; DECIMAL '10.1234567890')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-DEC07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(30,10)) + DECIMAL '0.1234567890' &gt; DECIMAL '10.1234567890'</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,348 B |
| **A-DEC08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; abs(CAST(x AS DECIMAL(12,2)))&gt;DECIMAL '10.00')</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 10 dòng · 10 doc / 2,064 B |
| **S-DEC08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(CAST(n AS DECIMAL(12,2)))&gt;DECIMAL '10.00'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,505 B |
| **A-DEC09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; sign(CAST(x AS DECIMAL(12,2)))=DECIMAL '-1')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,150 B |
| **S-DEC09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE sign(CAST(n AS DECIMAL(12,2)))=DECIMAL '-1'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,121 B |
| **A-DEC10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; floor(CAST(x AS DECIMAL(12,2)))=ceil(CAST(x AS DECIMAL(12,2))))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,845 B |
| **S-DEC10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE floor(CAST(n AS DECIMAL(12,2)))=ceil(CAST(n AS DECIMAL(12,2)))</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 2,697 B |
| **A-DEC11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(CAST(x AS DECIMAL(12,2)) AS INTEGER)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-DEC11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS INTEGER)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,348 B |
| **A-DEC12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(CAST(x AS DECIMAL(12,2)) AS DOUBLE)&gt;DOUBLE '10')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B | ĐÚNG · Native exact (bool,filter,range) · 9 dòng · 9 doc / 1,737 B |
| **S-DEC12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS DOUBLE)&gt;DOUBLE '10'</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B |
| **A-DEC13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CAST(x AS SMALLINT)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 1,894 B |
| **S-DEC13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS SMALLINT)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,348 B |
| **A-DEC14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRY_CAST(x AS TINYINT)&gt;10)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,633 B |
| **S-DEC14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT)&gt;10</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,207 B |

#### COALESCE / NULLIF / TRY_CAST / CASE

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-NULL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(x,0)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,738 B |
| **S-NULL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,0)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,313 B |
| **A-NULL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(x,100)&gt;10)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B |
| **S-NULL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,100)&gt;10</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,633 B |
| **A-NULL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; nullif(x,0)&gt;1)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 2,044 B |
| **S-NULL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE nullif(n,0)&gt;1</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 10 dòng · 10 doc / 1,892 B |
| **A-NULL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; coalesce(nullif(x,0),1)&gt;10)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,738 B |
| **S-NULL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(nullif(n,0),1)&gt;10</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,312 B |
| **A-NULL5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; TRY_CAST(x AS TINYINT) IS NULL)</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,196 B |
| **S-NULL5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT) IS NULL</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,076 B |
| **A-NULL6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN false ELSE x&gt;10 END)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,738 B |
| **S-NULL6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN false ELSE n&gt;10 END</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 5 dòng · 5 doc / 1,312 B |
| **A-NULL7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN true ELSE x&gt;10 END)</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,196 B |
| **S-NULL7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN true ELSE n&gt;10 END</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,633 B |
| **A-NULL8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; IF(x&gt;10,true,false))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,738 B |
| **S-NULL8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE IF(n&gt;10,true,false)</code> | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B | ĐÚNG · Native exact (bool,filter,range) · 5 dòng · 5 doc / 1,311 B |

#### BOOLEAN

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-BOOL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; x OR NOT x)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,226 B | ĐÚNG · Script exact (bool,filter,script) · 15 dòng · 15 doc / 2,596 B |
| **S-BOOL1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE flag OR NOT flag</code> | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B | ĐÚNG · Native exact (bool,exists,filter) · 15 dòng · 15 doc / 2,595 B |
| **A-BOOL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; coalesce(x,false))</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,732 B |
| **S-BOOL2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,false)</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,128 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,128 B | ĐÚNG · Script exact (bool,filter,script) · 8 dòng · 8 doc / 1,674 B |
| **A-BOOL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; coalesce(x,true))</code> | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 10 dòng · 18 doc / 3,226 B |
| **S-BOOL3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,true)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,128 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,128 B | ĐÚNG · Script exact (bool,filter,script) · 11 dòng · 11 doc / 1,997 B |
| **A-BOOL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -&gt; x IS NOT DISTINCT FROM true)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,226 B | ĐÚNG · Script exact (bool,filter,script) · 9 dòng · 9 doc / 1,732 B |
| **S-BOOL4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE flag IS NOT DISTINCT FROM true</code> | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,term) · 8 dòng · 8 doc / 1,673 B |

#### Keyword / Unicode / chuỗi

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TXT01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'abc'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,115 B |
| **S-TXT01**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc')</code> | ĐÚNG · Prefilter (bool,filter,range) · 2 dòng · 2 doc / 933 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 2 dòng · 2 doc / 905 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 2 dòng · 2 doc / 905 B |
| **A-TXT02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE 'ab%')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,115 B |
| **S-TXT02**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'ab%'</code> | ĐÚNG · Native exact (bool,filter,must,range,regexp) · 3 dòng · 3 doc / 1,010 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 1,010 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 1,010 B |
| **A-TXT03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE '%b%')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,673 B |
| **S-TXT03**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%b%'</code> | ĐÚNG · Native exact (bool,filter,must,regexp) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,regexp) · 8 dòng · 8 doc / 1,673 B |
| **A-TXT04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE 'a_b')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,regexp) · 3 dòng · 3 doc / 1,007 B |
| **S-TXT04**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'a_b'</code> | ĐÚNG · Native exact (bool,filter,must,range,regexp) · 3 dòng · 3 doc / 1,007 B | ĐÚNG · Native exact (bool,filter,range,regexp) · 3 dòng · 3 doc / 1,007 B | ĐÚNG · Native exact (bool,filter,range,regexp) · 3 dòng · 3 doc / 1,007 B |
| **A-TXT05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x LIKE '%a!_%' ESCAPE '!')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 753 B |
| **S-TXT05**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%a!_%' ESCAPE '!'</code> | ĐÚNG · Native exact (bool,filter,must,regexp) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 753 B | ĐÚNG · Native exact (bool,filter,regexp) · 1 dòng · 1 doc / 753 B |
| **A-TXT06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'a') AND starts_with(x,'ab'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,prefix) · 4 dòng · 4 doc / 1,115 B |
| **S-TXT06**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'a') AND starts_with(word,'ab')</code> | ĐÚNG · Prefilter (bool,filter,range) · 3 dòng · 3 doc / 1,050 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 1,010 B | ĐÚNG · Native exact (bool,filter,prefix,range) · 3 dòng · 3 doc / 1,010 B |
| **A-TXT07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'ab') OR x='nice')</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,prefix,should,term) · 5 dòng · 5 doc / 1,264 B |
| **S-TXT07**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'ab') OR word='nice'</code> | ĐÚNG · Prefilter (bool,filter,range,should,term) · 4 dòng · 4 doc / 1,213 B | ĐÚNG · Prefilter (bool,filter,range,should,term) · 4 dòng · 4 doc / 1,213 B | ĐÚNG · Native exact (bool,filter,prefix,range,should,term) · 4 dòng · 4 doc / 1,159 B |
| **A-TXT08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; length(x)&gt;2)</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,275 B |
| **S-TXT08**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)&gt;2</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,276 B |
| **A-TXT09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; ends_with(x,'b'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **S-TXT09**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ends_with(word,'b')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,113 B |
| **A-TXT10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; strpos(x,'😀')&gt;0)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 906 B |
| **S-TXT10**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE strpos(word,'😀')&gt;0</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 906 B |
| **A-TXT11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; replace(x,'_','😀') LIKE '%😀%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,090 B |
| **S-TXT11**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'_','😀') LIKE '%😀%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,070 B |
| **A-TXT12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; replace(x,'','!') LIKE '%!s!á!%')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 770 B |
| **S-TXT12**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'','!') LIKE '%!s!á!%'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 767 B |
| **A-TXT13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; concat(substr(x,1,1),'Z')='aZ')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,506 B |
| **S-TXT13**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE concat(substr(word,1,1),'Z')='aZ'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,460 B |
| **A-TXT14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; (x &#124;&#124; 'Z')='abcZ')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,029 B |
| **S-TXT14**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE (word &#124;&#124; 'Z')='abcZ'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 767 B |
| **A-TXT15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; substr(x,-2,1)='b')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 3 doc / 1,029 B |
| **S-TXT15**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE substr(word,-2,1)='b'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 767 B |
| **A-TXT16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; substring(x,2)='bc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 3 dòng · 3 doc / 964 B |
| **S-TXT16**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE substring(word,2)='bc'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 754 B |
| **A-TXT17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; trim(x)='abc')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 6 dòng · 6 doc / 1,420 B |
| **S-TXT17**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE trim(word)='abc'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,210 B |
| **A-TXT18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; ltrim(x)='abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 3 dòng · 3 doc / 964 B |
| **S-TXT18**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE ltrim(word)='abc'</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 754 B |
| **A-TXT19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; rtrim(x)='abc')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 4 dòng · 4 doc / 1,116 B |
| **S-TXT19**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE rtrim(word)='abc'</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Script exact (bool,filter,script) · 2 dòng · 2 doc / 906 B |
| **A-TXT20**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,925 B |
| **S-TXT20**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,876 B |
| **A-TXT21**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; upper(x) LIKE '%SÁ%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,925 B |
| **S-TXT21**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE upper(word) LIKE '%SÁ%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,876 B |
| **A-TXT22**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; x &gt; '')</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,288 B | ĐÚNG · Script exact (bool,filter,script) · 1 dòng · 1 doc / 757 B |
| **S-TXT22**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE word &gt; ''</code> | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 756 B | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 756 B | ĐÚNG · Native exact (bool,filter,range) · 1 dòng · 1 doc / 756 B |
| **A-TXT23**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; length(x)&gt;2 AND starts_with(x,'a'))</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Hybrid exact (bool,filter,prefix,script) · 6 dòng · 6 doc / 1,370 B |
| **S-TXT23**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)&gt;2 AND starts_with(word,'a')</code> | ĐÚNG · Prefilter (bool,filter,range) · 5 dòng · 6 doc / 1,459 B | ĐÚNG · Prefilter (bool,filter,prefix,range) · 5 dòng · 6 doc / 1,459 B | ĐÚNG · Hybrid exact (bool,filter,prefix,range,script) · 5 dòng · 5 doc / 1,265 B |
| **A-TXT24**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; starts_with(x,'abc') AND lower(x) LIKE '%b%')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 12 doc / 2,482 B |
| **S-TXT24**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc') AND lower(word) LIKE '%b%'</code> | ĐÚNG · Prefilter (bool,filter,range) · 2 dòng · 2 doc / 933 B | ĐÚNG · Prefilter (bool,filter,prefix,range) · 2 dòng · 2 doc / 933 B | ĐÚNG · Prefilter (bool,filter,range,script) · 2 dòng · 2 doc / 934 B |
| **A-TXT25**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'abc'))</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,288 B | ĐÚNG · Native exact (bool,filter,regexp) · 6 dòng · 6 doc / 1,419 B |
| **S-TXT25**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'abc')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,regexp) · 4 dòng · 4 doc / 1,273 B | ĐÚNG · Prefilter (bool,filter,regexp) · 4 dòng · 4 doc / 1,273 B |
| **A-TXT26**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'(?i)^abc'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,289 B |
| **S-TXT26**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'(?i)^abc')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B |
| **A-TXT27**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -&gt; regexp_like(x,'^abc$'))</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,288 B |
| **S-TXT27**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'^abc$')</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | **SAI** · Prefilter (bool,filter,regexp) · 1 dòng · 1 doc / 766 B | ĐÚNG · Prefilter (bool,filter,regexp) · 2 dòng · 2 doc / 933 B |

#### Mapping guard / doc values

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-MAP-IGNORE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(ignored_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,424 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,424 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,424 B |
| **S-MAP-IGNORE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(ignored_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,328 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,328 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,328 B |
| **A-MAP-NORMAL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(normalized_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,475 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,475 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,475 B |
| **S-MAP-NORMAL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(normalized_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,379 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,379 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,379 B |
| **A-MAP-NODV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(nodv_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,373 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,373 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,373 B |
| **S-MAP-NODV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(nodv_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,277 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,277 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,277 B |
| **A-MAP-DOCONLY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doconly_words, x -&gt; lower(x) LIKE '%sá%')</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,424 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,424 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,988 B |
| **S-MAP-DOCONLY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(doconly_word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,328 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,328 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,939 B |

#### Tên field phân biệt hoa thường

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-CASEPATH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE length(reviewcomment)&gt;2</code> | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,345 B | ĐÚNG · Local (—) · 12 dòng · 18 doc / 3,345 B | ĐÚNG · Script exact (bool,filter,script) · 12 dòng · 12 doc / 2,275 B |

#### TIMESTAMP(3) UTC / extract

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DATE1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; year(x)=2024)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,568 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,568 B |
| **S-DATE1**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE year(dt)=2024</code> | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,568 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,568 B | ĐÚNG · Native exact (bool,filter,range) · 7 dòng · 7 doc / 1,568 B |
| **A-DATE2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; month(x)=2)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,360 B |
| **S-DATE2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE month(dt)=2</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,500 B |
| **A-DATE3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day(x)=29)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,360 B |
| **S-DATE3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day(dt)=29</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,500 B |
| **A-DATE4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; hour(x)=12)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 840 B |
| **S-DATE4**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE hour(dt)=12</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 875 B |
| **A-DATE5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; minute(x)=59)</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 2 doc / 1,030 B |
| **S-DATE5**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE minute(dt)=59</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 2 doc / 1,100 B |
| **A-DATE6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; second(x)=56)</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 840 B |
| **S-DATE6**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE second(dt)=56</code> | ĐÚNG · Local (—) · 1 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 1 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 1 dòng · 1 doc / 875 B |
| **A-DATE7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day_of_week(x) BETWEEN 1 AND 5)</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,508 B |
| **S-DATE7**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_week(dt) BETWEEN 1 AND 5</code> | ĐÚNG · Local (—) · 11 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 11 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 11 dòng · 11 doc / 2,893 B |
| **A-DATE8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; day_of_year(x)=60)</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,360 B |
| **S-DATE8**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_year(dt)=60</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,500 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('millisecond',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,982 B |
| **S-TRUNC-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('millisecond',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,262 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('millisecond',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,315 B |
| **S-ADD-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('millisecond',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,455 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('second',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,982 B |
| **S-TRUNC-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('second',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,262 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('second',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,315 B |
| **S-ADD-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('second',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,455 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('minute',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 1,982 B |
| **S-TRUNC-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('minute',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 8 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 8 dòng · 8 doc / 2,262 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('minute',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,315 B |
| **S-ADD-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('minute',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,455 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('hour',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('hour',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('hour',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,315 B |
| **S-ADD-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('hour',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 4 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 4 dòng · 4 doc / 1,455 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('day',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('day',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('day',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,506 B |
| **S-ADD-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 5 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 5 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 5 dòng · 5 doc / 1,681 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('week',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('week',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('week',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,693 B |
| **S-ADD-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('week',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,903 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('month',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('month',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('month',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,693 B |
| **S-ADD-month**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('month',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 6 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 6 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 6 dòng · 6 doc / 1,903 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('quarter',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('quarter',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('quarter',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,125 B |
| **S-ADD-quarter**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('quarter',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,440 B |

#### date_trunc UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-TRUNC-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_trunc('year',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 8 dòng · 18 doc / 3,521 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |
| **S-TRUNC-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('year',dt)&gt;=TIMESTAMP '2024-01-01 00:00:00.000'</code> | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B | ĐÚNG · Native exact (bool,filter,range) · 8 dòng · 8 doc / 1,673 B |

#### date_add UTC

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ADD-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('year',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,125 B |
| **S-ADD-year**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('year',1,dt)&gt;TIMESTAMP '2024-03-01 00:00:00.000'</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 9 dòng · 9 doc / 2,440 B |

#### date_diff đơn vị cố định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('millisecond',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-millisecond**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('millisecond',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |
| **A-DIFF-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('second',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-second**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('second',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |
| **A-DIFF-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('minute',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-minute**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('minute',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |
| **A-DIFF-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('hour',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-hour**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('hour',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |
| **A-DIFF-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('day',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-day**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |
| **A-DIFF-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('week',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-week**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('week',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |

#### date_diff đảo đối số; NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-REVERSE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)&lt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,174 B |
| **S-DIFF-REVERSE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',dt)&lt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · 15 dòng · 15 doc / 3,699 B |

#### Lỗi trên phần tử NULL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DATE-NULL-ERROR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_add('day',coalesce(year(x),BIGINT '9223372036854775807')+BIGINT '1',x)&lt;TIMESTAMP '0001-01-01 00:00:00.000')</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,521 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,521 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,521 B |
| **S-DATE-NULL-ERROR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',coalesce(year(dt),BIGINT '9223372036854775807')+BIGINT '1',dt)&lt;TIMESTAMP '0001-01-01 00:00:00.000'</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 4,040 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 4,040 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 3 doc / 991 B |

#### Fallback được chủ định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-DIFF-MONTH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -&gt; date_diff('month',x,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0)</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 3,521 B |
| **S-DIFF-MONTH**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('month',dt,TIMESTAMP '2025-01-01 00:00:00.000')&gt;=0</code> | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B | ĐÚNG · Local (—) · 15 dòng · 18 doc / 4,040 B |
| **A-CAPTURE**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -&gt; x&gt;n)</code> | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,315 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,315 B | ĐÚNG · Local (—) · 9 dòng · 18 doc / 3,315 B |

#### Điều kiện cần với biến capture

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-CAPTURE-PREFILTER**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -&gt; x&gt;10 AND x&gt;n)</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,315 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,315 B | ĐÚNG · Prefilter (bool,filter,range) · 7 dòng · 9 doc / 1,954 B |

#### Fallback được chủ định

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-MULTICOL**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n+small&gt;10</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,223 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,223 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,223 B |

#### Ghép các cột scalar

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **S-MIXED-AND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n&gt;10 AND lower(word) LIKE '%sá%'</code> | ĐÚNG · Prefilter (bool,filter,range) · 1 dòng · 5 doc / 1,397 B | ĐÚNG · Prefilter (bool,filter,range) · 1 dòng · 5 doc / 1,397 B | ĐÚNG · Prefilter (bool,filter,range,script) · 1 dòng · 2 doc / 994 B |
| **S-MIXED-OR**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n&gt;10 OR lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,311 B | ĐÚNG · Local (—) · 7 dòng · 18 doc / 3,311 B | ĐÚNG · Prefilter (bool,filter,range,script,should) · 7 dòng · 11 doc / 2,354 B |

#### Resource budget fallback

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-BUDGET**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199))</code> | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Local (—) · 14 dòng · 18 doc / 3,196 B | ĐÚNG · Script exact (bool,filter,script) · 14 dòng · 14 doc / 2,447 B |
| **S-BUDGET**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199)</code> | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,036 B | ĐÚNG · Local (—) · 13 dòng · 18 doc / 3,036 B | ĐÚNG · Script exact (bool,filter,script) · 13 dòng · 13 doc / 2,295 B |

#### Bảo toàn lỗi SQL

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **A-ERR-OVERFLOW**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -&gt; x + 1 &gt; 0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,359 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,359 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,251 B |
| **S-ERR-OVERFLOW**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_int + 1 &gt; 0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,308 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,308 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,200 B |
| **A-ERR-DIV0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; x / 0 &gt; 0)</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,196 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,845 B |
| **S-ERR-DIV0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE n / 0 &gt; 0</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,036 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,696 B |
| **A-ERR-MOD0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; mod(x,0)=0)</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,196 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,845 B |
| **S-ERR-MOD0**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,0)=0</code> | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,036 B | ĐÚNG · Local (—) · lỗi DIVISION_BY_ZERO · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi DIVISION_BY_ZERO · 15 doc / 2,696 B |
| **A-ERR-LONGDIV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -&gt; x / BIGINT '-1' &gt; 0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,546 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,546 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,438 B |
| **S-ERR-LONGDIV**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_long / BIGINT '-1' &gt; 0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,495 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,495 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,388 B |
| **A-ERR-ABS**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -&gt; abs(x)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,546 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,546 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,439 B |
| **S-ERR-ABS**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE abs(error_long)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,495 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,495 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,388 B |
| **A-ERR-CAST**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -&gt; CAST(x AS TINYINT)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,359 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,359 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,252 B |
| **S-ERR-CAST**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(error_int AS TINYINT)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,308 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,308 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,201 B |
| **A-ERR-ROUND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -&gt; round(x,-19)&gt;0)</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,196 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,196 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 15 doc / 2,845 B |
| **S-ERR-ROUND**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE round(n,-19)&gt;0</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,036 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,036 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 15 doc / 2,697 B |
| **A-ERR-TINY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_tinys, x -&gt; x + TINYINT '1' &gt; TINYINT '0')</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,257 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,257 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,149 B |
| **S-ERR-TINY**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE error_tiny + TINYINT '1' &gt; TINYINT '0'</code> | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,206 B | ĐÚNG · Local (—) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 18 doc / 3,206 B | ĐÚNG · Prefilter (bool,filter,script) · lỗi NUMERIC_VALUE_OUT_OF_RANGE · 17 doc / 3,098 B |

#### LIMIT sau residual

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **LIMIT-2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 2</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 8 doc / 1,875 B |
| **A-LIMIT-2**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -&gt; lower(x) LIKE '%sá%') LIMIT 2</code> | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 2 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 2 dòng · 8 doc / 1,924 B |
| **LIMIT-3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 3</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,192 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,875 B |
| **A-LIMIT-3**<br><code>SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -&gt; lower(x) LIKE '%sá%') LIMIT 3</code> | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Local (—) · 3 dòng · 18 doc / 3,288 B | ĐÚNG · Prefilter (bool,filter,script) · 3 dòng · 8 doc / 1,924 B |

#### 10.000 document

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **VOL-NATIVE**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 4,113,244 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 287,977 B | ĐÚNG · Native exact (bool,filter,range) · 2500 dòng · 2500 doc / 287,977 B |
| **VOL-HYBRID**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 4,113,244 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 4,113,244 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 2500 dòng · 2500 doc / 287,977 B |
| **VOL-SCRIPT**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; mod(x,17)=0 AND mod(x,19)=0)</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 4,113,244 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 4,113,244 B | ĐÚNG · Script exact (bool,filter,script) · 2500 dòng · 2500 doc / 287,969 B |
| **VOL-LOWER**<br><code>SELECT id FROM expr_a2af4b7203e_volume WHERE lower(word) LIKE '%sá%'</code> | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,355,244 B | ĐÚNG · Local (—) · 2500 dòng · 10000 doc / 1,355,244 B | ĐÚNG · Prefilter (bool,filter,script) · 2500 dòng · 5000 doc / 682,784 B |

#### 10.000 document + COUNT

| SQL | Trino master | Trino internal/production | Trino branch mới |
| --- | --- | --- | --- |
| **VOL-COUNT**<br><code>SELECT count(*) FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -&gt; x&gt;1500 AND mod(x,2)=0)</code> | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 4,014,354 B | ĐÚNG · Local (—) · 1 dòng · 10000 doc / 4,014,354 B | ĐÚNG · Hybrid exact (bool,filter,range,script) · 1 dòng · 0 doc / 164 B |

## Provenance triển khai

Master và internal được ghim theo remote head tại thời điểm kiểm tra, không thay baseline bằng code mới. Image base bất biến là internal; diff production giữa master và internal chỉ ở connector Elasticsearch (trino-main chỉ khác test). Master overlay thay connector, trino-main và server launcher được clean-build từ master. Candidate overlay thay connector, trino-main, SPI và launcher; core/SPI giữ artifact của a2af4b7 khi source không đổi, connector và launcher clean-build theo commit runtime mới.

Image IDs, artifact SHA-256, manifests và session properties được lưu trong raw evidence. Báo cáo/harness có thể được commit sau commit runtime; vì vậy commit chứa báo cáo không thay đổi mã Java đã deploy.

Các container của lần so sánh có label `codex.lab=trino-expression-comparison`; dịch vụ Trino sẵn có ở port 18090 vẫn được giữ nguyên. Candidate chạy tại `127.0.0.1:18093` trên host, với catalogs `es8_compare`, `es7_compare`. Các port này chỉ bind loopback; dùng SSH tunnel để truy cập từ máy khác.
