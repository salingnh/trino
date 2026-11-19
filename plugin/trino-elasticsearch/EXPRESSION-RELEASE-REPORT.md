# Báo cáo tính năng mới của Elasticsearch connector

Ngày rà soát tài liệu: **19/11/2026**. Số đo gần nhất: **18/11/2026**. Các bảng tính năng và kiểm thử functional dùng bản deploy **`e10be840714`** và lần chạy **495 SQL × ES7/ES8 × 3 Trino = 2.970 lượt** ngày 16/11/2026, với bằng chứng tại commit `c60177ed6ff`. Benchmark hiệu năng ngày 18/11/2026 trên cùng ba runtime được bổ sung riêng tại mục 7 bên dưới.

**Bản cũ dùng làm mốc chính là `internal/production`.** Cột master được giữ để phân biệt những cải tiến đã có trong bản internal với phần vừa bổ sung. “Hỗ trợ mới” trong báo cáo nghĩa là connector có thêm khả năng prefilter/pushdown; các hàm SQL này vốn đã có ở Trino.

| Phiên bản | Commit được so sánh |
| --- | --- |
| master | `2162c006a7171d90305e21d1b2dd2fd47ef72b2a` |
| internal/production — bản cũ | `9abc69bc79ad0de1563031d7d5dd533b9212b3b6` |
| Bản mới — branch `Feat/elasticsearch-array-necessary-prefilter` | `e10be8407141f8e758ca343726e74fe60ce955b7` |

Code mới gồm Plan 1 (`5faf1bc08f4`), Plan 2 số nguyên (`ba69398be06`), A native/hybrid (`ae4380b3687`), B typed compiler và scalar (`a2af4b7203e`), sửa lỗi ngữ nghĩa (`ffcf7b85a3b`), và mở rộng native/runtime cho text (`e10be840714`). Bộ SQL và kết quả so sánh functional đã lưu tại commit `c60177ed6ff`. Các SHA nêu trong báo cáo là SHA gốc tại thời điểm build/deploy/đo; trước khi mở PR, một số tiêu đề commit chưa công bố được rút gọn theo giới hạn CI, làm thay đổi SHA hậu duệ nhưng không đổi cây nội dung của các commit đó.

## 1. Bảng tính năng mới và mở rộng

Bảng ghi hành vi **thực đo của SQL mẫu**, lấy từ ma trận Elasticsearch 8.11.3; cùng các ca cũng được chạy trên Elasticsearch 7.17.27. Nhãn của một ví dụ không áp dụng cho mọi overload hoặc mọi tổ hợp hàm trong nhóm.

- **Native exact**: dùng Query DSL như `term`, `terms`, `range`, `prefix`, `regexp`, `exists`, `match_none`, kết hợp bằng `bool.filter`/`bool.should`.
- **Script exact**: connector sinh Painless, Elasticsearch đánh giá điều kiện; không còn residual của điều kiện đó.
- **Hybrid exact**: kết hợp native và script, không còn residual của điều kiện đó.
- **Native/Script approximate**: chấp nhận ngữ nghĩa Elasticsearch khi bật UNSAFE; không giữ residual SQL của phần đã nhận xấp xỉ, có thể thêm hoặc mất dòng so với SQL exact.
- **Prefilter**: Elasticsearch giữ tập ứng viên; Trino tải và kiểm tra lại các document được giữ. Có thể là native, script hoặc kết hợp cả hai.
- **Local**: chưa có điều kiện remote hữu ích cho phép lọc tương ứng.

SQL mẫu chỉ trích phần `WHERE`. Các cột `numbers`, `words`, `dates` là array; `n`, `word` là scalar. Các cột `raw_review_comment`, `review`, `reviews` là text, trong đó `reviews` là array. ID đi kèm tra được toàn bộ SQL, plan và Query DSL trong ma trận chi tiết. Các hàng text mới bên dưới dùng UNSAFE; các hàng keyword/numeric giữ chế độ đã ghi trong ma trận.

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
| 33. Text native: predicate và LOWER/UPPER trên text dùng analyzer; không cần script cho mẫu thích hợp | <code>WHERE LOWER(raw_review_comment) LIKE '%sách rất%'</code><br>ID: `TEXT-USER-SQL` | Local | Local | Native approximate |
| 34. Compiler trên text: length/trim/substring/replace/concat/strpos/ends_with và tổ hợp qua runtime `_source` | <code>WHERE length(trim(review))&gt;2</code><br>ID: `S-TEXT02` | Local | Local | Script exact |
| 35. Text array: đánh giá cả phần tử NULL, COALESCE/NULLIF/CASE và logic được hỗ trợ | <code>WHERE any_match(reviews, x -&gt; x IS NULL)</code><br>ID: `A-TEXT18` | Local | Local | Script exact |
| 36. Unicode case trong runtime source, đánh dấu approximate khi dùng chính sách Elasticsearch | <code>WHERE ends_with(lower(trim(review)),'tốt')</code><br>ID: `S-TEXT06` | Local | Local | Script approximate |
| 37. Text có keyword phụ bỏ qua review dài: native dùng parent text | <code>WHERE lower(with_keyword) LIKE '%sách rất%'</code><br>ID: `TEXT-NATIVE-with_keyword-lower-match_phrase` | Local | Local | Native approximate |
| 38. Runtime definitions đi cùng COUNT và các đường aggregation/Scroll/PIT đang hỗ trợ | <code>SELECT count(*) FROM expr_a2af4b7203e_text WHERE length(trim(review))&gt;2</code><br>ID: `TEXT-COUNT` | Local | Local | Script exact |
| 39. Đọc text ở field path lồng nhau và giữ đúng case của remote field | <code>WHERE length(profile.reviewcomment)&gt;2</code><br>ID: `TEXT-NESTED` | Local | Local | Script exact |
| 40. Analyzer tùy chỉnh và stopwords: ghi nhận sai biệt tìm kiếm tương đối thay vì gọi là SQL exact | <code>WHERE lower(foldedreview) LIKE '%the%'</code><br>ID: `TEXT-ZERO-TOKENS` | Local | Local | Native approximate |

Các điểm cần hiểu đúng khi áp dụng bảng:

| Nhóm | Phạm vi và giới hạn của bản mới |
| --- | --- |
| Điều kiện cần | AND lấy được phần cần thiết đã dịch. OR phải có điều kiện cần bao phủ tất cả nhánh; `x > 10 OR sin(x) > 0` vẫn local khi chưa dịch được nhánh `sin`. Khi prefilter array, giữ toàn bộ lambda làm residual. |
| Native array | AND phải đúng trên cùng một phần tử. `x IN (5,12) AND x > 10` được giao thành tập giá trị phù hợp; không ghép tùy ý hai điều kiện document để hai phần tử khác nhau thỏa riêng. NOT trong lambda không ánh xạ tùy ý thành `must_not` trên cả document. |
| Hybrid AND | Script kiểm tra toàn bộ lambda để giữ tương quan giữa các điều kiện trên cùng phần tử; native làm guard khi đã chứng minh không che lỗi. |
| Kiểu dữ liệu | Integral: TINYINT/SMALLINT/INTEGER/BIGINT; REAL/DOUBLE; BOOLEAN; VARCHAR keyword; TIMESTAMP(3) không time zone. DECIMAL là biểu thức tính/cast từ nguồn hợp lệ, không thêm mapping DECIMAL riêng. |
| Số học, hàm số và cast | Có thể exact nếu cây an toàn; phép có thể overflow/chia 0/cast ngoài miền giữ residual. Không hỗ trợ mọi hướng CAST, mọi precision/scale hoặc mọi overload. `truncate(x)` đã hỗ trợ, `truncate(x,n)` chưa hỗ trợ. |
| NULL trong array | Ở đường doc values, nếu lambda có thể TRUE hoặc gây lỗi ở phần tử NULL không có trong doc values, script array có thể phải giữ local. Ví dụ `any_match(numbers,x -> coalesce(x,100)>10)` vẫn local; scalar `coalesce(n,100)>10` có thể exact. Với text array dùng runtime `_source`, phần tử NULL được đánh giá trực tiếp nên các ca như `x IS NULL` đã có thể exact. |
| Chuỗi | Doc values trên keyword và runtime source cho text dùng compiler chung. Tính theo code point để giữ đúng Unicode/emoji. Một số biến đổi có giới hạn kích thước hoặc khả năng lỗi nên giữ residual. Ở đường bảo toàn SQL, LOWER/UPPER giữ guard Unicode và residual. Với text UNSAFE, native dùng analyzer; runtime case không fallible có thể dùng Unicode của Elasticsearch và gắn APPROXIMATE. Cây fallible vẫn giữ PREFILTER. |
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
| Mapping và field name | Doc values có thể khác nguồn do ignore_above, normalizer, null_value/coercion hoặc thiếu khả năng đọc. Tên field Elasticsearch phân biệt hoa/thường. | Đường doc values áp dụng guard tương đương nguồn; text UNSAFE có thêm native parent hoặc runtime đọc nguyên `_source`, không lấy token làm chuỗi gốc. Compiler dùng đúng remote field name; không tùy ý lấy một phần tử của field scalar nhiều giá trị. | Nhóm `MAP-*`, `S-CASEPATH`; chi tiết guard trong tài liệu kỹ thuật. |

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

Chạy **495 SQL × 2 phiên bản Elasticsearch × 3 phiên bản Trino = 2.970 lượt**. Fixture cho mỗi backend gồm 18 document biên keyword/numeric, 20 document biên text và hai index volume 10.000 document.

Các ca EXACT/PREFILTER được đối chiếu với Trino local. Native APPROXIMATE dùng oracle Query DSL Elasticsearch riêng; runtime case approximate được kiểm tra theo policy và fixture đã công bố. Vì vậy “đạt” không có nghĩa mọi kết quả UNSAFE giống SQL exact. Bảng ghi rõ các sai biệt có chủ đích.

| Chỉ số — tổng ES7 và ES8 | master | internal/production | Bản mới |
| --- | ---: | ---: | ---: |
| Ca đạt theo oracle tương ứng | 990/990 | 980/990 | 990/990 |
| Native exact | 72 | 128 | 160 |
| Script exact | 0 | 0 | 348 |
| Hybrid exact | 0 | 0 | 16 |
| Tổng exact | 72 | 128 | 524 |
| Native approximate | 0 | 10 | 54 |
| Script approximate | 0 | 0 | 14 |
| Tổng approximate | 0 | 10 | 68 |
| Prefilter | 20 | 20 | 326 |
| Local | 894 | 828 | 68 |
| Rút gọn tại Trino, không scan | 4 | 4 | 4 |

**Phần text mới:** 102 SQL × ES7/ES8 = **204/204 ca đạt** trên bản mới. Có 20 lượt local theo cấu hình SAFE/DISABLED hoặc tắt script. Internal vẫn có 10 lỗi signed zero/regex được giữ nguyên để so sánh.

| SQL/nhóm trên 10.000 document | master: doc chuyển về | internal: doc chuyển về | Bản mới: doc chuyển về |
| --- | ---: | ---: | ---: |
| any_match với range native | 10000 | 2500 | 2500 |
| range AND mod — hybrid | 10000 | 10000 | 2500 |
| mod — script | 10000 | 10000 | 2500 |
| LOWER keyword — prefilter | 10000 | 10000 | 5000 |
| COUNT với array exact | 10000 | 10000 | 0 |
| LOWER text — native approximate | 10000 | 10000 | 5000 |
| Chuỗi text phức tạp — runtime approximate | 10000 | 10000 | 5000 |
| COUNT với runtime source exact | 10000 | 10000 | 0 |

`0` document ở COUNT nghĩa là Elasticsearch trả số đếm, không gửi `_source`; không có nghĩa Elasticsearch không xét dữ liệu nội bộ. Ca text native approximate trả 5.000 kết quả trong khi SQL exact trả 2.500, nên giảm truyền dữ liệu và khác tập kết quả phải được đọc cùng nhau. Không suy ra tỷ lệ tăng tốc latency từ số document.

**Câu SQL người dùng:** `LOWER(raw_review_comment) LIKE '%sách rất%'` trong fixture 20 document trả 4 dòng ở SQL exact và 5 dòng ở native analyzer; bản mới tải 5 document thay vì tải 20 để lọc local. Dòng tăng thêm chứa `SÁCH, rất tốt`, được chấp nhận khi UNSAFE.

Kiểm tra local: **226 unit test trong CI/Error Prone**, thêm **34 unit về page-source/PIT**, và các ca integration ES7/ES8 Scroll/PIT nhiều trang đã qua. Một lần ES8 PIT song song bị đóng kết nối khi cleanup; lần chạy riêng đã qua, log ban đầu được giữ để đối chiếu.

## 5. Điều kiện bật và các phần chưa hỗ trợ

| Nội dung | Trạng thái |
| --- | --- |
| Array script | Mặc định tắt. Bật bằng session `<catalog>.array_script_pushdown_enabled=true` hoặc catalog property `elasticsearch.array-script-pushdown.enabled=true`. |
| Scalar script | Mặc định tắt, độc lập với array. Bật bằng session `<catalog>.scalar_script_pushdown_enabled=true` hoặc `elasticsearch.scalar-script-pushdown.enabled=true`. |
| Khi tắt script | Native và native prefilter vẫn hoạt động. Trong UNSAFE, câu LOWER(text) LIKE literal đã hỗ trợ native nên vẫn offload; biểu thức cần runtime source có thể local. |
| Mapping chuỗi | Keyword/doc values giữ các guard cũ. Text UNSAFE dùng native analyzer hoặc runtime `_source` khi bật script tương ứng. Không đọc token như chuỗi gốc; runtime cần `_source` khả dụng. Biểu thức không có bản dịch hoặc vượt budget giữ fallback. |
| Regex tổng quát | Chỉ tập mẫu có bản dịch hợp lệ được offload. Inline flags/lookaround/regex engine tổng quát chưa được tự động thêm vào Painless. Không suy ra mọi end anchor hoặc regex array đều exact từ chế độ UNSAFE. |
| Array mở rộng | Chưa bổ sung all_match/none_match, transform/filter/reduce, array lồng nhau, ROW/object hoặc so sánh hai array. Lambda capture toàn bộ vẫn có thể local; chỉ lấy được phần điều kiện cần đã chứng minh. |
| Hàm/toán tử khác | Chưa mở rộng tổng quát lượng giác, logarithm, bitwise, JSON/binary, parsing/formatting, TRY tổng quát, mọi hình dạng CASE hoặc mọi overload cast/trim/truncate. |
| Ngày giờ ngoài phạm vi | Chưa mở rộng script này cho timestamp có time zone, độ chính xác micro/nanosecond, hoặc date_diff theo month/quarter/year. |
| Ngân sách và fallback | Compiler dùng allowlist, giới hạn cây/độ sâu/tham số/kích thước script/request. Không dịch được an toàn hoặc vượt budget thì giữ xử lý local hoặc phần prefilter đã chứng minh. |

Ví dụ bật tìm kiếm tương đối trong catalog test:

```sql
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
```

Catalog trên VM đã bật hai flag script; native của câu LOWER text không cần Painless. Để cấu hình catalog khác, bật flag scalar/array tương ứng khi dùng runtime fallback. UNSAFE tuân theo analyzer: có thể bỏ qua dấu câu, khác chữ hoa/thường, mất toàn bộ token do stopwords hoặc khớp các token ở các phần tử khác nhau của text array.

EXACT nghĩa là Trino không cần đánh giá lại điều kiện đã offload. Trino vẫn điều phối query, nhận kết quả và thực hiện các phép còn lại. PREFILTER vẫn cần residual nhưng chỉ trên tập document được Elasticsearch giữ.

## 6. Tài liệu và bằng chứng

- [Ma trận đầy đủ: SQL và ba phiên bản Trino](../../testing/elasticsearch-query-shape-lab/EXPRESSION-COMPARISON.md).
- [Raw result, full plan, Query DSL và artifact provenance](../../testing/elasticsearch-query-shape-lab/expression-comparison.json.gz).
- [Danh sách toán tử, hàm, kiểu và giới hạn chi tiết](ARRAY-PUSHDOWN-SUPPORT.md).
- [Plan và kết quả triển khai text](PLAN-UNSAFE-TEXT-PUSHDOWN.md).
- [Hướng dẫn chạy lại bộ test và kết nối bản deploy](../../testing/elasticsearch-query-shape-lab/EXPRESSION-LAB.md).
- [Benchmark tăng shard/document: SQL và ba phiên bản Trino](../../testing/elasticsearch-query-shape-lab/EXPRESSION-PERFORMANCE.md).

## 7. Benchmark hiệu năng tăng shard và document — 18/11/2026

Đã chạy **40 SQL đại diện cho 40 hàng tính năng ở mục 1**, trên ES8.11.3 và ES7.17.27, so sánh master / internal / bản mới. Đây là đo hiệu năng các SQL mẫu, không phải benchmark mọi hàm hoặc overload được liệt kê chung trong từng hàng. Các ca signed zero/regex lỗi ở baseline vẫn thuộc ma trận functional riêng; việc baseline đạt benchmark này không có nghĩa các lỗi đó đã được sửa.

| Hướng tăng tải | Ba điểm đo `(shard; document trên mỗi index)` |
| --- | --- |
| Tăng shard | `(1; 10.000)` → `(4; 10.000)` → `(8; 10.000)` |
| Tăng document | `(1; 10.000)` → `(1; 50.000)` → `(1; 100.000)` |
| Tăng đồng thời | `(1; 10.000)` → `(4; 50.000)` → `(8; 100.000)` |

Có **7 cấu hình duy nhất** do dùng chung điểm gốc. Mỗi SQL có 1 warm-up và 3 lượt đo, các Trino chạy tuần tự và xoay vị trí giữa các lượt: **6.720 lượt SQL**, gồm **5.040 lượt đo + 1.680 warm-up**. Bổ sung **144 lượt control cuối** ở cấu hình gốc để kiểm tra độ trôi. Tất cả đạt theo oracle tương ứng; native approximate dùng oracle analyzer riêng và vẫn ghi chênh lệch với SQL exact.

[Bảng hiệu năng đầy đủ](../../testing/elasticsearch-query-shape-lab/EXPRESSION-PERFORMANCE.md) có **560 dòng SQL/cấu hình/backend**, mỗi dòng gồm lệnh SQL và ba cột Trino, median `[min–max]` thời gian, CPU Trino, document và byte chuyển về, số dòng/giá trị COUNT, cùng loại offload. [CSV chi tiết](../../testing/elasticsearch-query-shape-lab/expression-performance.csv) có thêm planning/execution time và metric request; [raw evidence](../../testing/elasticsearch-query-shape-lab/expression-performance.json.gz) lưu từng lượt, plan, DSL/runtime mapping, fingerprint kết quả, oracle, hash source và snapshot môi trường.

Ở mức **8 shard / 100.000 document**, có 37/40 ví dụ trả cùng fingerprint kết quả giữa internal và bản mới. Ba ví dụ native text có khác biệt UNSAFE được tách khỏi so sánh tỷ lệ tăng tốc:

| Backend | Bản mới có median thấp hơn internal | Bản mới có median cao hơn internal |
| --- | ---: | ---: |
| ES8 | 30/37 | 7/37 |
| ES7 | 14/37 | 23/37 |

Đây là so sánh median của ba mẫu tại một điểm đo, không phải kiểm định ý nghĩa thống kê hoặc kết luận cho mọi tải production. Các ví dụ cụ thể dưới đây giữ cùng tập kết quả SQL:

| SQL đại diện ở 8 shard / 100.000 document | ES8: internal → mới (ms) | ES7: internal → mới (ms) | Document chuyển về: internal → mới |
| --- | ---: | ---: | ---: |
| `any_match(numbers,x -> x>10 AND sin(x)>0)` — native prefilter, script OFF | 1.198,3 → 956,4 | 1.596,1 → 1.999,5 | 100.000 → 50.000 |
| `any_match(numbers,x -> x>10 AND mod(x,2)=0)` — hybrid exact | 1.175,6 → 701,0 | 1.588,6 → 1.384,7 | 100.000 → 33.334 |
| `any_match(words,x -> strpos(x,'😀')>0)` — script exact | 1.252,0 → 479,5 | 1.587,8 → 566,9 | 100.000 → 11.111 |
| `length(trim(review))>2` — runtime source exact | 1.254,3 → 1.624,6 | 1.226,0 → 2.497,9 | 100.000 → 85.000 |
| `COUNT(*) WHERE length(trim(review))>2` — runtime source exact | 1.249,2 → 496,4 | 1.166,7 → 504,3 | 100.000 → 0 |

**Offload nhiều hơn không đồng nghĩa query luôn nhanh hơn.** Ví dụ runtime `length(trim(review))>2` giảm số document/CPU Trino nhưng tăng latency trên cả hai backend; ngay native prefilter đầu bảng cũng chậm hơn ở cấu hình ES7 này. Số đo chỉ ra cần xét độ chọn lọc, chi phí Elasticsearch và đường thực thi, thay vì suy tốc độ từ phần trăm document được lọc. COUNT remote vẫn có lợi rõ trong các ví dụ trên; `0` source document không có nghĩa ES không xử lý dữ liệu.

Giới hạn môi trường: đo warm cache qua recorder proxy của lab, mỗi ES chỉ **một node**, replica = 0; dữ liệu lặp xác định từ fixture biên 18/20 document, không phải trace production. Ba Trino đều 2 CPU / 7 GiB; ES7 có quota 2 CPU / 3 GiB, ES8 3 GiB và không đặt quota CPU Docker. Vì vậy chỉ so các Trino trong cùng backend; không quy chênh lệch ES7/ES8 cho riêng phiên bản Elasticsearch. Ba lượt đo không đủ kết luận p95/p99.

Sau đo đã xác minh: fixture gốc giữ nguyên số lượng, source/mapping của fixture biên không đổi, không còn index benchmark, compilation rate đã khôi phục, runtime/image Trino không đổi và các container lab vẫn khỏe. [Hướng dẫn tái lập và SQL replay](../../testing/elasticsearch-query-shape-lab/EXPRESSION-PERFORMANCE-LAB.md) mô tả đầy đủ cách nạp lại dữ liệu và đọc số đo.

## 8. Tách ca chậm và kiểm tra cơ chế thực thi — chỉ ES8

Các kiểm tra bổ sung chỉ chạy **ES8**; kết quả ES7 ở mục 7 là lịch sử, không chạy lại. Harness hiện mặc định `--backends es8`.

- [Bảng riêng các ca ES8 chậm](../../testing/elasticsearch-query-shape-lab/EXPRESSION-ES8-SLOW-CASES.md): 76 điểm SQL/cấu hình có median bản mới cao hơn ít nhất một baseline, gồm 70 chậm hơn internal và 71 chậm hơn master. Chỉ so các ca đúng và cùng fingerprint kết quả; bảng ghi cả mức tăng nhỏ và khoảng min–max chồng lấp.
- [Phân tích đầy đủ DSL/index/profile/tải ES8](../../testing/elasticsearch-query-shape-lab/EXPRESSION-ES8-QUERY-AUDIT.md): 40 SQL, lớp Lucene thực tế và số lần gọi script. Thực hiện 43 profile và hai pass tập trung, mỗi pass 168 SQL; không trộn số đo profile vào benchmark gốc.

**Regexp không đồng nghĩa scan `_source`.** Hai regexp của bộ test dùng automaton/multi-term trên keyword index; field `words` chỉ có 17 term khác nhau dù lặp tới 100.000 document. Pattern `.*...` có thể tốn khi từ điển term lớn; benchmark hiện tại chưa mô phỏng cardinality lớn đó. Không có regexp trong bảy ca chậm hơn internal ở mức 8 shard / 100.000 document.

**Cú pháp `term` cũng chưa đủ để gọi là indexed lookup.** Numeric term trong các ví dụ ra `PointRangeQuery`/`PointInSetQuery`; term trên `__trino_text_<hash>` lại ra `BooleanScriptFieldTermQuery` vì đó là runtime field chưa được index sẵn. Script thuần có 100.000 lần `match` trong profile đếm; native range guard giảm xuống 50.000 ở ví dụ hybrid AND/OR.

Pass CPU bổ sung đọc trực tiếp cgroup v2 của container ES8 quanh SQL, tránh CPU node-stats bị cache. Ví dụ median CPU ES8 internal → mới: native `NOT IN` **2.485 → 3.899 ms**, runtime `length(trim(review))>2` **1.459 → 4.340 ms**, regexp chọn lọc `.*a_.*` **2.368 → 657 ms**. Đây là CPU cộng các threads trong khoảng đo, có thể gồm GC/công việc nền; không phải wall time hay CPU tuyệt đối của riêng request.

Vì vậy có các query làm ES làm nhiều việc hơn dù giảm dữ liệu về Trino. Tuy nhiên, trong mẫu chẩn đoán không ghi nhận search rejection hoặc lỗi trong 1.235 probe canary; p95 canary khi chạy bản mới khoảng **3,38 ms**, so với idle trước khoảng **3,32 ms**. Canary nhẹ và node ES8 không giới hạn quota CPU Docker: chưa thấy quá tải trong mẫu này không có nghĩa mọi workload khác không bị ảnh hưởng. Raw profile, node/cgroup counters, SQL và canary đã lưu trong [bằng chứng ES8](../../testing/elasticsearch-query-shape-lab/expression-es8-diagnostics.json.gz).

## 9. So sánh hybrid / Painless thuần / Trino local — chỉ ES8

[Báo cáo so sánh ba chế độ trên cùng bản Trino](../../testing/elasticsearch-query-shape-lab/EXPRESSION-ES8-HYBRID.md)
tách riêng hiệu quả của native guard khỏi khác biệt master/internal/bản mới.
Phạm vi gồm bốn mẫu hybrid của các hàng 13, 14, 30 và 32, trên bảy cấu hình
shard/document; bảng có SQL, latency, CPU ES/Trino, document/bytes và profile
số lần gọi script. `S-MIXED-OR` giữ residual trong cả hai chế độ offload.
Dữ liệu chi tiết nằm trong [CSV](../../testing/elasticsearch-query-shape-lab/expression-es8-hybrid.csv)
và [raw evidence](../../testing/elasticsearch-query-shape-lab/expression-es8-hybrid.json.gz).

Đã hoàn tất 336 SQL và 16 profile, tất cả đúng kết quả; hai chế độ offload nhận
cùng tập candidate ở mọi cấu hình. Hybrid giảm số lần gọi script 50% cho hai mẫu
array và 27,8% cho hai mẫu scalar trong profile đếm, nhưng median chỉ nhanh hơn
script thuần ở 7/28 điểm và cả bảy đều có khoảng đo chồng lấp. Ở 8 shard/100.000
document, hybrid AND array gần ngang script thuần (723,6 / 721,7 ms), nhanh hơn
local (1.199,3 ms); OR trộn field lại chậm hơn cả script và local
(1.699,1 / 1.340,4 / 1.194,0 ms). Báo cáo cũng nêu rõ regression Scroll lớn ở
1 shard/100.000 document; giảm số lần gọi script không bảo đảm giảm latency/CPU ES.
