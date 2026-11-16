# Báo cáo tính năng mới của Elasticsearch connector

Ngày lập báo cáo: **16/11/2026**. Số liệu dưới đây mô tả bản đã build và kiểm thử ngày **13/11/2026** tại commit `ffcf7b85a3b`. Kết quả sau đợt mở rộng text ngày 16/11 được cập nhật trong [ma trận SQL trên VM](../../testing/elasticsearch-query-shape-lab/EXPRESSION-COMPARISON.md).

**Bản cũ dùng làm mốc chính là `internal/production`.** Cột master được giữ để phân biệt những cải tiến đã có trong bản internal với phần vừa bổ sung. “Hỗ trợ mới” trong báo cáo nghĩa là connector có thêm khả năng prefilter/pushdown; các hàm SQL này vốn đã có ở Trino.

| Phiên bản | Commit được so sánh |
| --- | --- |
| master | `2162c006a7171d90305e21d1b2dd2fd47ef72b2a` |
| internal/production — bản cũ | `9abc69bc79ad0de1563031d7d5dd533b9212b3b6` |
| Bản mới — branch `Feat/elasticsearch-array-necessary-prefilter` | `ffcf7b85a3b187b869cac8d8f7a56ec531b7630a` |

Code mới gồm Plan 1 (`5faf1bc08f4`), Plan 2 số nguyên (`ba69398be06`), A native/hybrid (`ae4380b3687`), B typed compiler và scalar (`a2af4b7203e`), cùng sửa lỗi ngữ nghĩa (`ffcf7b85a3b`). Bộ SQL và kết quả so sánh đã lưu tại commit `d500020f290`.

## 1. Bảng tính năng mới và mở rộng

Bảng ghi hành vi **thực đo của SQL mẫu**, lấy từ ma trận Elasticsearch 8.11.3; cùng các ca cũng được chạy trên Elasticsearch 7.17.27. Nhãn của một ví dụ không áp dụng cho mọi overload hoặc mọi tổ hợp hàm trong nhóm.

- **Native exact**: dùng Query DSL như `term`, `terms`, `range`, `prefix`, `regexp`, `exists`, `match_none`, kết hợp bằng `bool.filter`/`bool.should`.
- **Script exact**: connector sinh Painless, Elasticsearch đánh giá điều kiện; không còn residual của điều kiện đó.
- **Hybrid exact**: kết hợp native và script, không còn residual của điều kiện đó.
- **Prefilter**: Elasticsearch giữ tập ứng viên; Trino tải và kiểm tra lại các document được giữ. Có thể là native, script hoặc kết hợp cả hai.
- **Local**: chưa có điều kiện remote hữu ích cho phép lọc tương ứng.

SQL mẫu chỉ trích phần `WHERE`. Các cột `numbers`, `words`, `dates` là array; `n`, `word` là scalar. ID đi kèm tra được toàn bộ SQL, plan và Query DSL trong ma trận chi tiết.

| Tính năng/thay đổi | SQL mẫu đã kiểm thử | master | internal/production | Bản mới |
| --- | --- | --- | --- | --- |
| 1. Lấy điều kiện cần từ AND, kể cả khi tắt script | <code>WHERE any_match(numbers, x -&gt; x &gt; 10 AND sin(x) &gt; 0)</code><br>ID: `A-P1` | Local | Local | Prefilter |
| 2. Lấy điều kiện cần bao phủ các nhánh OR | <code>WHERE any_match(numbers, x -&gt; (x &gt; 10 AND sin(x) &gt; 0) OR (x &lt; 0 AND cos(x) &gt; 0))</code><br>ID: `A-P3` | Local | Local | Prefilter |
| 3. Prefilter phần đã biết khi lambda còn tham chiếu cột khác | <code>WHERE any_match(numbers,x -&gt; x&gt;10 AND x&gt;n)</code><br>ID: `A-CAPTURE-PREFILTER` | Local | Local | Prefilter |
| 4. Native số nguyên: mở rộng phủ định `<>`, `NOT`, `NOT IN`, `NOT BETWEEN` | <code>WHERE any_match(numbers, x -&gt; x NOT IN (5, 12))</code><br>ID: `A-N11` | Local | Local | Native exact |
| 5. Native `IN` có hằng NULL, giữ logic ba giá trị | <code>WHERE any_match(numbers, x -&gt; x IN (5, 12, NULL))</code><br>ID: `A-N10` | Local | Local | Native exact |
| 6. Native: giao `IN` và range trên cùng một phần tử | <code>WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code><br>ID: `A-N15` | Local | Local | Native exact |
| 7. Native: bổ sung `IS NOT NULL`, `IS NOT DISTINCT FROM` và các hình dạng NULL được chứng minh | <code>WHERE any_match(numbers, x -&gt; x IS NOT DISTINCT FROM 12)</code><br>ID: `A-N18` | Local | Local | Native exact |
| 8. Native keyword: `starts_with` và tổ hợp prefix | <code>WHERE any_match(words, x -&gt; starts_with(x,'a') AND starts_with(x,'ab'))</code><br>ID: `A-TXT06` | Local | Local | Native exact |
| 9. Native keyword: `LIKE` với `%`, `_`, `ESCAPE` | <code>WHERE any_match(words, x -&gt; x LIKE '%a!_%' ESCAPE '!')</code><br>ID: `A-TXT05` | Local | Local | Native exact |
| 10. Native keyword: tập con `regexp_like` tương đương Lucene | <code>WHERE any_match(words, x -&gt; regexp_like(x,'abc'))</code><br>ID: `A-TXT25` | Local | Local | Native exact |
| 11. Số học số nguyên: `+`, `-`, `*`, `/`, unary `-` | <code>WHERE any_match(numbers, x -&gt; x + 1 &gt; 10)</code><br>ID: `A-I01` | Local | Local | Prefilter |
| 12. Modulo `%`, `mod`; mở rộng cho các độ rộng số nguyên được hỗ trợ | <code>WHERE any_match(tiny_numbers, x -&gt; mod(x, TINYINT '2') = TINYINT '0')</code><br>ID: `A-MOD-TINYINT` | Local | Local | Script exact |
| 13. Hybrid native + script cho OR | <code>WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code><br>ID: `A-I09` | Local | Local | Hybrid exact |
| 14. Hybrid native + script cho AND trên cùng phần tử | <code>WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code><br>ID: `A-I10` | Local | Local | Hybrid exact |
| 15. So sánh và logic `AND`, `OR`, `NOT` trên kết quả tính toán | <code>WHERE any_match(numbers, x -&gt; NOT (mod(x,2)=0))</code><br>ID: `A-I11` | Local | Local | Script exact |
| 16. Typed script cho `REAL`, `DOUBLE`, gồm phép tính sinh NaN/Infinity | <code>WHERE any_match(reals, x -&gt; x + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL))</code><br>ID: `A-REAL01` | Local | Local | Script exact |
| 17. Biểu thức DECIMAL và chuyển kiểu numeric được hỗ trợ | <code>WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00')</code><br>ID: `A-DEC01` | Local | Local | Prefilter |
| 18. `TRY_CAST` giữ đúng NULL khi chuyển kiểu thất bại | <code>WHERE TRY_CAST(n AS TINYINT) IS NULL</code><br>ID: `S-NULL5` | Local | Local | Script exact |
| 19. Hàm số: `abs`, `ceil`/`ceiling`, `floor`, `round`, `truncate`, `sign`, `sqrt`, `pow`/`power` | <code>WHERE any_match(numbers, x -&gt; abs(x)&gt;5)</code><br>ID: `A-MATH1` | Local | Local | Prefilter |
| 20. Biểu thức nullable: `COALESCE`, `NULLIF` | <code>WHERE any_match(numbers, x -&gt; coalesce(nullif(x,0),1)&gt;10)</code><br>ID: `A-NULL4` | Local | Local | Script exact |
| 21. Searched `CASE`, `IF` theo hình dạng planner dịch được | <code>WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN false ELSE x&gt;10 END)</code><br>ID: `A-NULL6` | Local | Local | Script exact |
| 22. Typed script cho BOOLEAN và logic TRUE/FALSE/NULL | <code>WHERE any_match(flags, x -&gt; x IS NOT DISTINCT FROM true)</code><br>ID: `A-BOOL4` | Local | Local | Script exact |
| 23. Hàm chuỗi: `length`, `ends_with`, `strpos`, so sánh theo code point | <code>WHERE any_match(words, x -&gt; strpos(x,'😀')&gt;0)</code><br>ID: `A-TXT10` | Local | Local | Script exact |
| 24. Hàm chuỗi: `substr`/`substring`, `trim`, `ltrim`, `rtrim` | <code>WHERE any_match(words, x -&gt; substring(x,2)='bc')</code><br>ID: `A-TXT16` | Local | Local | Script exact |
| 25. Biến đổi chuỗi: `replace`, `concat` | <code>WHERE any_match(words, x -&gt; concat(substr(x,1,1),'Z')='aZ')</code><br>ID: `A-TXT13` | Local | Local | Prefilter |
| 26. Chuyển hoa/thường `LOWER`, `UPPER` kết hợp điều kiện lọc | <code>WHERE any_match(words, x -&gt; lower(x) LIKE '%sá%')</code><br>ID: `A-TXT20` | Local | Local | Prefilter |
| 27. Trích xuất thời gian: month/day/hour/minute/second, thứ và ngày trong năm | <code>WHERE any_match(dates, x -&gt; month(x)=2)</code><br>ID: `A-DATE2` | Local | Local | Prefilter |
| 28. Phép tính ngày giờ: `date_add`; thêm compiler script cho tổ hợp `date_trunc` được hỗ trợ | <code>WHERE any_match(dates, x -&gt; date_add('month',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code><br>ID: `A-ADD-month` | Local | Local | Prefilter |
| 29. `date_diff` đơn vị cố định, kể cả đảo thứ tự hai timestamp | <code>WHERE any_match(dates, x -&gt; date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)&lt;=0)</code><br>ID: `A-DIFF-REVERSE` | Local | Local | Prefilter |
| 30. Đường scalar: áp dụng compiler cho giá trị đơn, không cần `any_match` | <code>WHERE n &gt; 10 OR mod(n,2)=0</code><br>ID: `S-I09` | Local | Local | Hybrid exact |
| 31. Scalar NULL/missing: đánh giá biểu thức với NULL thay vì bỏ document | <code>WHERE coalesce(n,100)&gt;10</code><br>ID: `S-NULL2` | Local | Local | Script exact |
| 32. Ghép điều kiện trên các cột scalar, gồm OR native + script prefilter | <code>WHERE n&gt;10 OR lower(word) LIKE '%sá%'</code><br>ID: `S-MIXED-OR` | Local | Local | Prefilter |

Các điểm cần hiểu đúng khi áp dụng bảng:

| Nhóm | Phạm vi và giới hạn của bản mới |
| --- | --- |
| Điều kiện cần | AND lấy được phần cần thiết đã dịch. OR phải có điều kiện cần bao phủ tất cả nhánh; `x > 10 OR sin(x) > 0` vẫn local khi chưa dịch được nhánh `sin`. Khi prefilter array, giữ toàn bộ lambda làm residual. |
| Native array | AND phải đúng trên cùng một phần tử. `x IN (5,12) AND x > 10` được giao thành tập giá trị phù hợp; không ghép tùy ý hai điều kiện document để hai phần tử khác nhau thỏa riêng. NOT trong lambda không ánh xạ tùy ý thành `must_not` trên cả document. |
| Hybrid AND | Script kiểm tra toàn bộ lambda để giữ tương quan giữa các điều kiện trên cùng phần tử; native làm guard khi đã chứng minh không che lỗi. |
| Kiểu dữ liệu | Integral: TINYINT/SMALLINT/INTEGER/BIGINT; REAL/DOUBLE; BOOLEAN; VARCHAR keyword; TIMESTAMP(3) không time zone. DECIMAL là biểu thức tính/cast từ nguồn hợp lệ, không thêm mapping DECIMAL riêng. |
| Số học, hàm số và cast | Có thể exact nếu cây an toàn; phép có thể overflow/chia 0/cast ngoài miền giữ residual. Không hỗ trợ mọi hướng CAST, mọi precision/scale hoặc mọi overload. `truncate(x)` đã hỗ trợ, `truncate(x,n)` chưa hỗ trợ. |
| NULL trong array | Nếu lambda có thể TRUE hoặc gây lỗi ở phần tử NULL không có trong doc values, script array có thể phải giữ local. Ví dụ `any_match(numbers,x -> coalesce(x,100)>10)` vẫn local; scalar `coalesce(n,100)>10` có thể exact. |
| Chuỗi | Tính theo code point để giữ đúng Unicode/emoji. Một số biến đổi có giới hạn kích thước hoặc khả năng lỗi nên giữ residual. LOWER/UPPER hiện lọc được chuỗi ASCII chắc chắn không khớp và nhận chuỗi ngoài ASCII cho Trino kiểm tra. |
| Ngày giờ | Compiler dùng TIMESTAMP(3), UTC, độ chính xác millisecond và giữ residual cho đường script thời gian. `date_add`/`date_trunc`: millisecond, second, minute, hour, day, week, month, quarter, year. `date_diff`: millisecond đến week; month/quarter/year chưa dịch. |
| Native có sẵn vẫn được ưu tiên | Một số hình dạng `year(...)` hoặc `date_trunc(...)` được planner biến thành range native từ trước. Compiler thời gian mới bổ sung đường xử lý các tổ hợp được hỗ trợ, không biến mọi query thời gian thành script. |
| Scalar trên nhiều cột | Có thể ghép các điều kiện riêng như `n > 10 OR lower(word) LIKE ...`. Cây tính toán dùng đồng thời hai cột như `n + small > 10` vẫn local. |

## 2. Sửa lỗi và bảo toàn ngữ nghĩa

| Thay đổi | Hành vi cũ / vấn đề | Hành vi bản mới | Bằng chứng |
| --- | --- | --- | --- |
| Signed zero của REAL/DOUBLE | Internal dùng native với zero và bỏ sót document `-0.0`, dù SQL coi bằng `+0.0`. | Không dùng native array không tương đương cho các hằng đặc biệt; có thể fallback typed script khi bật, hoặc local khi chưa chứng minh. | `A-REAL07/08`, `A-DOUBLE07/08`; 8 lượt sai của internal trên ES7/ES8 đã đúng ở bản mới. |
| Regex scalar có end anchor `$` | Internal SAFE prefilter bỏ sót chuỗi `abc` kèm newline cuối với regex `^abc$`. | Nới candidate Lucene thành `(abc).*`, giữ regex SQL làm residual. Không tuyên bố end anchor array đã exact. | `S-TXT27`; 2 lượt sai của internal đã đúng ở bản mới. |
| Hàm thời gian gặp NULL trong array | Trong bản phát triển B trước sửa, amount của `date_add` có thể lỗi khi phần tử là NULL, nhưng script bỏ phần tử NULL và che lỗi. Đây là regression của bản phát triển, không phải lỗi được ghi nhận ở baseline internal. | Null proof kiểm tra mọi đối số. Nếu không chứng minh an toàn thì giữ local, để Trino báo đúng lỗi. | `A-DATE-NULL-ERROR`; so với local giữ `NUMERIC_VALUE_OUT_OF_RANGE`. |
| `date_diff` đảo đối số | Bản phát triển trước sửa chỉ chứng minh NULL ở một phía nên bỏ lỡ khả năng prefilter. | Xét cả hai timestamp để hỗ trợ cả `date_diff(unit,x,c)` và `date_diff(unit,c,x)` khi đủ điều kiện. | `A-DIFF-REVERSE`, `S-DIFF-REVERSE`. |
| Lỗi số học và chuyển kiểu | Khi mở rộng script, lọc bỏ document lỗi có thể làm SQL trả kết quả thay vì báo lỗi như Trino. | Script nhận lại ứng viên có lỗi đã được guard, giữ residual; không dùng guard native che lỗi của biểu thức. | Nhóm `ERR-*`: overflow, division/modulo by zero, abs cực trị, narrowing cast, round. |
| LIMIT sau prefilter | Prefilter có thể giữ thừa document; giới hạn theo ứng viên remote có thể thiếu dòng SQL cuối cùng. | Giữ LIMIT đúng theo kết quả sau residual. Đây là bảo đảm tương thích của đường prefilter mới, không phải mở rộng mọi dạng LIMIT pushdown. | `LIMIT-2/3`, `A-LIMIT-2/3`. |
| Mapping và field name | Doc values có thể khác nguồn do ignore_above, normalizer, null_value/coercion hoặc thiếu khả năng đọc. Tên field Elasticsearch phân biệt hoa/thường. | Áp dụng guard trước khi dùng script; fallback khi chưa chứng minh. Compiler mới dùng đúng remote field name; không tùy ý lấy một phần tử của field scalar nhiều giá trị. | Nhóm `MAP-*`, `S-CASEPATH`; chi tiết guard trong tài liệu kỹ thuật. |

## 3. Các khả năng đã có ở internal, không tính là mới trong đợt này

| Khả năng | SQL đại diện | Internal cũ → bản mới |
| --- | --- | --- |
| Membership array | <code>WHERE contains(numbers, 12)</code><br>ID: `M1` | Native exact → Native exact |
| Overlap với danh sách hằng | <code>WHERE arrays_overlap(numbers, ARRAY[5,12])</code><br>ID: `M2` | Native exact → Native exact |
| BETWEEN số nguyên trong any_match | <code>WHERE any_match(numbers, x -&gt; x BETWEEN 5 AND 12)</code><br>ID: `A-N07` | Native exact → Native exact |
| OR gồm range đơn trong any_match | <code>WHERE any_match(numbers, x -&gt; x &lt; 0 OR x &gt; 10)</code><br>ID: `A-N16` | Native exact → Native exact |
| LIKE prefix trên scalar | <code>WHERE word LIKE 'ab%'</code><br>ID: `S-TXT02` | Native exact → Native exact |
| YEAR được rewrite thành range | <code>WHERE any_match(dates, x -&gt; year(x)=2024)</code><br>ID: `A-DATE1` | Native exact → Native exact |
| DATE_TRUNC được rewrite thành range | <code>WHERE any_match(dates, x -&gt; date_trunc('day',x)&gt;=TIMESTAMP '2024-01-01 00:00:00.000')</code><br>ID: `A-TRUNC-day` | Native exact → Native exact |

Đợt này cũng không thêm bộ aggregation/sort/projection tổng quát mới. Ca COUNT ở bảng đo bên dưới tận dụng điều kiện array đã trở thành exact để đường COUNT remote hiện có hoạt động.

## 4. Kết quả kiểm thử và mức offload

Chạy 393 SQL × 2 phiên bản Elasticsearch × 3 phiên bản Trino = **2.358 lượt**, trên dữ liệu fixture: 18 document biên và 10.000 document volume cho mỗi backend. Mỗi kết quả được đối chiếu với query local bằng `allow_pushdown_into_connectors=false`; lỗi SQL và LIMIT không ORDER BY có quy tắc đối chiếu riêng. Đây là số liệu của bộ test, không phải tỷ lệ offload chung cho mọi workload.

| Chỉ số — tổng ES7 và ES8 | master | internal/production | Bản mới |
| --- | ---: | ---: | ---: |
| Kết quả đúng | 786/786 | 776/786 | 786/786 |
| Native exact | 72 | 128 | 160 |
| Script exact | 0 | 0 | 248 |
| Hybrid exact | 0 | 0 | 16 |
| Tổng exact | 72 | 128 | 424 |
| Prefilter | 18 | 18 | 310 |
| Local | 692 | 636 | 48 |
| Rút gọn tại Trino, không scan | 4 | 4 | 4 |

Có thêm điều kiện remote không đồng nghĩa mọi query đều giảm số document: một prefilter có thể giữ nhiều ứng viên. Các ca volume sau đo trực tiếp document được chuyển về Trino trên ES8:

| SQL/nhóm trên 10.000 document | master | internal/production | Bản mới |
| --- | ---: | ---: | ---: |
| any_match với range native | 10000 | 2500 | 2500 |
| any_match với range AND mod — hybrid | 10000 | 10000 | 2500 |
| any_match chỉ dùng phép tính mod — script | 10000 | 10000 | 2500 |
| LOWER scalar LIKE tiếng Việt — prefilter | 10000 | 10000 | 5000 |
| COUNT với điều kiện array exact | 10000 | 10000 | 0 |

`0` document ở ca COUNT nghĩa là Elasticsearch trả kết quả đếm, không gửi `_source` về Trino; không có nghĩa Elasticsearch không đọc/xét dữ liệu nội bộ. Ở các ca hybrid/script volume, lượng document truyền về giảm 75% so với baseline local; LOWER giảm 50% trong fixture này. Không suy ra mức tăng tốc latency từ các tỷ lệ trên.

## 5. Điều kiện bật và các phần chưa hỗ trợ

| Nội dung | Trạng thái |
| --- | --- |
| Array script | Mặc định tắt. Bật bằng session `<catalog>.array_script_pushdown_enabled=true` hoặc catalog property `elasticsearch.array-script-pushdown.enabled=true`. |
| Scalar script | Mặc định tắt, độc lập với array. Bật bằng session `<catalog>.scalar_script_pushdown_enabled=true` hoặc `elasticsearch.scalar-script-pushdown.enabled=true`. |
| Khi tắt script | Native và native prefilter vẫn hoạt động; không có typed script/hybrid cần Painless. OR có nhánh chưa dịch được vẫn có thể phải local. |
| Mapping chuỗi | Cần keyword trực tiếp và bằng chứng doc values tương đương nguồn cho script. Không coi analyzed text là chuỗi gốc. LOWER trên field text bất kỳ chưa được bảo đảm offload. |
| Regex tổng quát | Inline flags, lookaround và những mẫu chưa chứng minh không tự được hỗ trợ qua Painless. End anchor array vẫn local trong ví dụ đã test. |
| Array mở rộng | Chưa bổ sung all_match/none_match, transform/filter/reduce, array lồng nhau, ROW/object hoặc so sánh hai array. Lambda capture toàn bộ vẫn có thể local; chỉ lấy được phần điều kiện cần đã chứng minh. |
| Hàm/toán tử khác | Chưa mở rộng tổng quát lượng giác, logarithm, bitwise, JSON/binary, parsing/formatting, TRY tổng quát, mọi hình dạng CASE hoặc mọi overload cast/trim/truncate. |
| Ngày giờ ngoài phạm vi | Chưa mở rộng script này cho timestamp có time zone, độ chính xác micro/nanosecond, hoặc date_diff theo month/quarter/year. |
| Ngân sách và fallback | Compiler dùng allowlist, giới hạn cây/độ sâu/tham số/kích thước script/request. Không dịch được an toàn hoặc vượt budget thì giữ xử lý local hoặc phần prefilter đã chứng minh. |

EXACT nghĩa là Trino không cần đánh giá lại điều kiện đã offload. Trino vẫn điều phối query, nhận kết quả và thực hiện các phép còn lại. PREFILTER vẫn cần residual nhưng chỉ trên tập document được Elasticsearch giữ.

## 6. Tài liệu và bằng chứng

- [Ma trận đầy đủ: SQL và ba phiên bản Trino](../../testing/elasticsearch-query-shape-lab/EXPRESSION-COMPARISON.md).
- [Raw result, full plan, Query DSL và artifact provenance](../../testing/elasticsearch-query-shape-lab/expression-comparison.json.gz).
- [Danh sách toán tử, hàm, kiểu và giới hạn chi tiết](ARRAY-PUSHDOWN-SUPPORT.md).
- [Hướng dẫn chạy lại bộ test và kết nối bản deploy](../../testing/elasticsearch-query-shape-lab/EXPRESSION-LAB.md).
