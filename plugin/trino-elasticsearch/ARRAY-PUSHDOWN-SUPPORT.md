# Hỗ trợ pushdown biểu thức array và scalar sang Elasticsearch

Tài liệu mô tả code trên branch `Feat/elasticsearch-array-necessary-prefilter`, gồm Plan 1, Plan 2, A và B. A đã commit tại `ae4380b3687`; B và đường scalar đã commit tại `a2af4b7203e`.

Phạm vi gồm điều kiện `WHERE any_match(array_column, x -> biểu_thức)` và cây biểu thức scalar trên một cột. Một hàm nằm trong bảng không có nghĩa mọi kiểu dữ liệu, overload hoặc cách kết hợp của hàm đều được pushdown. Connector phải dịch được cây biểu thức, kiểm tra mapping, NULL, lỗi và giới hạn tài nguyên trước khi chọn plan.

## Mở rộng text với UNSAFE

Bản mở rộng sau `ffcf7b85a3b` bổ sung pipeline text chung cho scalar và `any_match(array(text))`, dùng `full_text_pushdown_mode=UNSAFE`. Native full-text được xét trước; `LOWER(text) LIKE '%sách rất%'` có thể dùng `match_phrase` và bỏ residual SQL của phần đã nhận xấp xỉ. Các biến đổi khác dùng compiler chung qua runtime field đọc `_source` khi bật flag script scalar/array tương ứng. Đây là phần bổ sung cho điều kiện keyword/doc values của đường script cũ, không phải nới mọi mapping thành source-equivalent.

Runtime source giữ NULL/missing và phần tử NULL trong array. Hàm chuỗi, nullable/CASE, so sánh, logic và tổ hợp được xét theo allowlist hiện có. Source expressions không gây lỗi có thể EXACT; case conversion UNSAFE dùng Unicode của Elasticsearch được đánh dấu APPROXIMATE. Biểu thức fallible giữ PREFILTER và residual bảo toàn SQL. Native và runtime definitions được kiểm tra budget; definitions đi cùng Scan/Scroll/PIT, COUNT và aggregation đang hỗ trợ.

Xem [plan và phạm vi](PLAN-UNSAFE-TEXT-PUSHDOWN.md) và [bảng SQL được chạy trên VM](../../testing/elasticsearch-query-shape-lab/EXPRESSION-COMPARISON.md). Mô tả keyword bên dưới vẫn áp dụng cho đường doc values và các chế độ không mở rộng text UNSAFE.

## Phần mở rộng scalar

Các nhóm biểu thức ở bảng số học, so sánh, nullable/CASE, hàm số, chuỗi và timestamp bên dưới hiện dùng **cùng compiler** cho cả phần tử array và giá trị scalar của một cột. Phần mở rộng này đã được commit cùng B tại `a2af4b7203e`. Native vẫn được ưu tiên; không cần bọc cột scalar thành array trong SQL.

```sql
SET SESSION elasticsearch.scalar_script_pushdown_enabled = true;

SELECT id FROM events
WHERE LOWER(raw_review_comment) LIKE '%sá%'
LIMIT 20;
```

Trong `SET SESSION`, thay tiền tố `elasticsearch` bằng tên catalog thực tế, ví dụ `osint_profiling_3517.scalar_script_pushdown_enabled`. Catalog có thể bật mặc định bằng `elasticsearch.scalar-script-pushdown.enabled=true`. Mặc định false; độc lập với cờ array. Cột chuỗi cần mapping keyword trực tiếp, doc values và bằng chứng tương đương nguồn giống compiler array. Text đã tokenize, keyword bị ignore_above/normalizer hoặc mapping không đủ bằng chứng vẫn local.

| Trường hợp | Scalar | Array `any_match` |
| --- | --- | --- |
| `LOWER(value) LIKE '%sá%'`, `UPPER(value)` | Script PREFILTER; nhận chuỗi ngoài ASCII để Trino kiểm tra | Cùng quy tắc PREFILTER |
| `length(value) > 2`, so sánh/logic không gây lỗi | Có thể EXACT | Có thể EXACT |
| `value > 10 OR mod(value,2)=0` | Có thể hybrid native + script EXACT | Có thể hybrid native + script EXACT |
| `abs(value) > 10`, phép tính có thể lỗi | Script toàn bộ + residual | Script toàn bộ + residual |
| `coalesce(value,100) > 10` | Có thể EXACT, gồm cột NULL hoặc thiếu | Local nếu NULL làm lambda TRUE |
| `TRY_CAST(value AS TINYINT) IS NULL` | Có thể EXACT | Local nếu NULL làm lambda TRUE |
| `value` thiếu/NULL | Gán NULL rồi đánh giá biểu thức | Bỏ qua phần tử NULL; cần chứng minh không mất TRUE/lỗi |
| `a + b > 10` với hai cột khác nhau | Chưa dịch cây tính toán nhiều cột; các điều kiện riêng vẫn có thể ghép | Lambda bắt cột khác vẫn local |

Compiler scalar đọc một giá trị, không dùng vòng lặp existential. Nếu field scalar có nhiều doc values, query báo lỗi thay vì tùy ý lấy phần tử đầu tiên. Native scalar regexp vẫn dùng hợp đồng SAFE/UNSAFE và residual hiện có. Trino đôi khi chuyển điều kiện thành TupleDomain; đường scalar mới có fallback cho domain số thực, giữ đúng signed zero và NaN membership.

Với ví dụ tiếng Việt, offload hiện là **prefilter**, vẫn cần kiểm tra local: script loại được các chuỗi ASCII chắc chắn không khớp và giữ các chuỗi Unicode cho Trino xử lý. LIMIT chỉ tính số dòng sau khi residual lọc xong.

Code mới: [ElasticsearchScalarPredicateTranslator.java:54](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchScalarPredicateTranslator.java#L54), compiler chung [ElasticsearchExpressionScriptTranslator.java:80](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchExpressionScriptTranslator.java#L80), [kiểm thử scalar](src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchScalarPredicateTranslator.java). IR có `scalarScript` riêng, dùng chung render/budget/field tracking với `arrayScript`, cùng metric `scalarScriptPredicates` riêng.

## 1. Cách đọc kết quả

| Ký hiệu | Ý nghĩa | Trino còn kiểm tra điều kiện này không? |
| --- | --- | --- |
| Native / EXACT | Query DSL có sẵn của Elasticsearch biểu diễn chính xác tập document đạt điều kiện | Không còn residual của điều kiện đã offload |
| Hybrid / EXACT | Kết hợp query native và Painless trong một query DSL | Không còn residual nếu toàn bộ biểu thức đã được chứng minh chính xác |
| Script / EXACT | Connector sinh Painless, chạy vòng lặp theo từng phần tử của doc values | Không còn residual của điều kiện đã offload |
| PREFILTER | Điều kiện cần: có thể giữ thừa document, không được loại document cần giữ hoặc che lỗi SQL | Có; chỉ tải và kiểm tra những document Elasticsearch giữ lại |
| Local | Không tạo được điều kiện remote an toàn cho biểu thức này | Có; vẫn có thể hưởng điều kiện remote khác của query |
| APPROXIMATE | Đường full-text UNSAFE đã có trước, chấp nhận ngữ nghĩa analyzer khác SQL | Có thể bỏ residual theo chế độ UNSAFE; không được hiểu là EXACT hoặc prefilter bảo toàn SQL |

EXACT chỉ nói về điều kiện lọc tương ứng. Trino vẫn lập kế hoạch, nhận kết quả, đọc các cột cần thiết và thực hiện các phần còn lại của SQL. PREFILTER không có nghĩa tải toàn bộ index: Elasticsearch lọc trước, Trino nhận các document còn lại.

Thứ tự lựa chọn: **native chính xác → hybrid → script toàn bộ → native prefilter của Plan 1 → local**. Script chỉ được xét khi bật tính năng:

```sql
SET SESSION elasticsearch.array_script_pushdown_enabled = true;
```

Thiết lập áp dụng cho session hiện tại. Có thể bật mặc định cho catalog bằng `elasticsearch.array-script-pushdown.enabled=true`. Mặc định là `false`; tắt script vẫn giữ native và native prefilter. Cluster Elasticsearch cũng phải cho phép script query/expensive query.

## 2. Native Query DSL và logic

| SQL / biểu thức | Elasticsearch thực sự dùng | Phạm vi và điều kiện |
| --- | --- | --- |
| `contains(a, constant)` | `term` | Giá trị khác NULL; mapping và kiểu phần tử được translator membership chấp nhận |
| `arrays_overlap(a, ARRAY[constants...])` | `terms` hoặc `term` | Danh sách hằng được hỗ trợ; không phải phép so sánh hai cột array |
| `x = c` | `term` | Integral, REAL/DOUBLE, BOOLEAN, VARCHAR exact, IPADDRESS và TIMESTAMP(3), theo mapping thích hợp |
| `x IN (c1, c2, ...)` | `terms`, `term` hoặc `match_none` | Native integral theo dõi cả TRUE/FALSE/UNKNOWN khi danh sách có NULL |
| `x > c`, `>=`, `<`, `<=` | `range` với `gt`, `gte`, `lt`, `lte` | Số nguyên hỗ trợ tổ hợp đầy đủ; range đơn còn hỗ trợ REAL, DOUBLE và TIMESTAMP(3), theo mapping nguồn tương đương |
| `x BETWEEN lo AND hi` | Một `range` sau khi giao hai cận | Native đầy đủ cho số nguyên; không tạo hai điều kiện độc lập trên document |
| `x <> c` / `x != c` | Hợp các khoảng ở hai phía `c`, thường là `bool.should` chứa `range` | Native số nguyên; **không** dịch thành `must_not term(c)` trên cả document |
| `NOT p(x)` | Đổi tập TRUE và FALSE của `p` rồi dựng `term`/`terms`/`range`/`exists`/`match_none` | Native số nguyên; không biến UNKNOWN thành TRUE. Một số hình dạng keyword được chứng minh riêng |
| `p(x) AND q(x)` | Giao tập giá trị của **một phần tử**, sau đó dựng query | Số nguyên: chuẩn hóa cận, IN, phủ định, mâu thuẫn. Prefix keyword: rút về prefix mạnh hơn hoặc `match_none`; AND keyword tổng quát có thể chỉ PREFILTER |
| `p(x) OR q(x)` | Hợp tập giá trị; `terms`, `range` hoặc `bool.should` với `minimum_should_match: 1` | Mọi nhánh phải có bản dịch cần thiết an toàn; nhánh exact cho phép exact khi hợp đúng ngữ nghĩa |
| `x IS NOT NULL` | `exists` | Khi mapping bảo đảm giá trị indexed tương đương nguồn và có thể truy vấn |
| `x IS NULL` | Thường local | NULL trong array không có doc value. Có thể được xử lý bên trong một biểu thức lớn nếu toàn bộ biểu thức không TRUE khi `x=NULL` |
| `x IS NOT DISTINCT FROM c` | `term(c)` cho số nguyên, `c` khác NULL | B bổ sung nhận diện native `$identical`; với `c=NULL`, nhánh đơn này local |
| `x IS DISTINCT FROM NULL` | `exists` trong trường hợp số nguyên đủ điều kiện | Là phủ định của `$identical(x,NULL)`; không TRUE với phần tử NULL |
| Điều kiện không thể đúng | `match_none` | Ví dụ `x > 10 AND x < 5`, hoặc hai prefix mâu thuẫn |
| `starts_with(x, 'abc')` | `prefix` | Keyword exact được index; không áp dụng nếu normalizer/ignore_above/mapping làm mất ngữ nghĩa nguồn |
| `x LIKE 'abc%'` | `prefix` | Pattern phù hợp dạng prefix |
| `x LIKE pattern [ESCAPE e]` | `prefix`, `regexp` hoặc query exact tương đương do translator chọn | Pattern hằng được hỗ trợ; có kiểm tra escape và ngữ nghĩa ký tự |
| Hình dạng `substr`/`substring` kiểm tra prefix được nhận diện | `prefix` | Chỉ hình dạng được native translator nhận diện, không phải mọi substring |
| `regexp_like(x, constant_pattern)` | `regexp` | Chỉ tập regex có bản dịch chính xác dùng chung với Lucene; không coi toàn bộ regex SQL là Lucene regex |

Ở đường scalar full-text SAFE, end anchor `$` của regex cần giữ cả chuỗi có newline cuối. Candidate Lucene được nới suffix và giữ residual SQL, ví dụ `regexp_like(word, '^abc$')` dùng `(abc).*` để prefilter. Đường array vẫn giữ local cho end anchor chưa được chứng minh.

Giá trị hằng REAL/DOUBLE là `0`, NaN hoặc Infinity không dùng native term/terms/range ở đường array này. Đặc biệt Elasticsearch phân biệt `-0.0` và `+0.0`, còn SQL coi chúng bằng nhau. `any_match` có thể chuyển sang typed script khi bật tính năng; membership không có script fallback sẽ giữ local.

Elasticsearch có `bool.filter`, `bool.must`, `bool.should`, `bool.must_not`; không có câu lệnh SQL `AND`/`OR`. Trong đường lọc này, điều kiện AND của document được dựng bằng `bool.filter`, OR bằng `bool.should`. `must` thường mang scoring và không cần thiết cho bộ lọc này. Connector **không** ánh xạ mọi `NOT` trong lambda sang `must_not` của document.

Ví dụ array `[5, 12]`:

- `any_match(a, x -> x > 10 AND x < 8)` phải FALSE. Hai `range` đặt độc lập vào `bool.filter` sẽ sai vì hai phần tử khác nhau có thể thỏa hai cận; native integral tính giao trước và tạo `match_none`.
- `any_match(a, x -> x <> 5)` phải TRUE vì có phần tử 12. `must_not term(a,5)` sẽ sai vì document vẫn chứa 5; connector dùng khoảng giá trị khác 5.
- `NOT any_match(...)` không tự động được hưởng các quy tắc dành cho positive `any_match` trong bảng.

Đường full-text hiện có được điều khiển riêng bởi `full_text_pushdown_mode`. Với analyzed text không có keyword exact, chế độ UNSAFE có thể dịch membership/equality/IN thành `match_phrase`, và một số LIKE/starts_with/regexp thành query full-text tương ứng. Đây là APPROXIMATE theo lựa chọn của chế độ UNSAFE, không thuộc typed script B và không có bảo đảm tương đương SQL của các dòng EXACT. Xem [tài liệu full-text](../../docs/src/main/sphinx/connector/elasticsearch.md#full-text-pushdown).

## 3. Hybrid và lấy điều kiện cần

| Hình dạng | Query remote | Residual |
| --- | --- | --- |
| `x > 10 AND unsupported(x)` khi script không dịch được | Native điều kiện cần `range > 10` | Giữ **toàn bộ** `any_match`, không chỉ nhánh unsupported |
| `x > 10 OR unsupported(x)` | Không dùng riêng `range > 10` để lọc | Local cho biểu thức này nếu không có điều kiện cần chung an toàn |
| `x > 10 OR mod(x,2)=0` khi bật script, đủ điều kiện exact | `bool.should` gồm native `range` và script cho nhánh modulo | Không còn residual của `any_match` |
| `x > 10 AND mod(x,2)=0` khi bật script, không thể lỗi | `bool.filter` gồm native điều kiện cần và script cho **toàn bộ lambda** | Không còn residual của `any_match` |
| OR chỉ gồm các nhánh cần script | Một script cho toàn bộ lambda | EXACT hoặc PREFILTER theo ngữ nghĩa của toàn bộ biểu thức |
| Biểu thức có thể overflow/chia 0 | Script toàn bộ lambda, gặp lỗi thì nhận document | Giữ toàn bộ lambda; không để native guard che lỗi |

AND cho phép lấy nhánh cần thiết đã biết; OR cần bao phủ tất cả nhánh. Ví dụ `x>10 OR unsupported(x)` có thể TRUE với `x=2` do nhánh sau, nên dùng riêng `range >10` sẽ làm mất dữ liệu.

## 4. Kiểu dữ liệu trong script B

| Kiểu SQL của phần tử array | Mapping Elasticsearch | Trạng thái |
| --- | --- | --- |
| `TINYINT`, `SMALLINT`, `INTEGER`, `BIGINT` | `byte`, `short`, `integer`, `long` tương ứng | Hỗ trợ compact integral compiler và typed fallback |
| `REAL` | `float` | Hỗ trợ, tính theo float 32 bit |
| `DOUBLE` | `double` | Hỗ trợ, tính theo double 64 bit |
| `BOOLEAN` | `boolean` | Hỗ trợ logic ba giá trị và biểu thức nullable |
| `VARCHAR` | `keyword` trực tiếp | Hỗ trợ khi doc values tương đương nguồn; không lấy text token làm chuỗi gốc |
| `TIMESTAMP(3)` không time zone | Date mapping được connector chấp nhận | Hỗ trợ UTC, độ chính xác millisecond; giữ residual |
| `DECIMAL(p,s)` | Không thêm mapping array DECIMAL mới | Hỗ trợ **biểu thức DECIMAL** sinh ra bằng cast/tính toán từ phần tử hợp lệ, precision tối đa 38 |
| `half_float`, `scaled_float`, array lồng nhau/ROW/object | Không thuộc tập typed script này | Local hoặc đường translator khác, không tuyên bố hỗ trợ ở B |

Script cần doc values và bằng chứng `sourceValueSemanticsExact` trên mọi backing index. `null_value`, `ignore_above`, normalizer, coercion làm thay đổi nguồn và các trường hợp mất giá trị có thể chặn pushdown. Field không có doc values không chạy compiler script này. Native keyword mới cần `index:true` để tương thích ES 7; ES 8 có thể làm được nhiều hơn nhưng connector dùng tập chung an toàn.

## 5. Số học, so sánh và logic trong Painless

Các tên `sqlArithmetic`, `sqlCompare`, ... dưới đây là **helper do connector sinh**, chạy trên Painless của Elasticsearch. Chúng không phải tên hàm SQL hay lệnh Elasticsearch có sẵn. Hằng số được đưa vào `params`, không ghép giá trị người dùng thành mã thực thi.

| SQL | Script sinh ra / cơ chế | Loại và giới hạn |
| --- | --- | --- |
| `+`, `-`, `*` | Compact: helper kiểm tra tràn số; fallback: `sqlArithmetic(..., operation)` | Integral/REAL/DOUBLE/DECIMAL. Integral có thể lỗi → PREFILTER; REAL/DOUBLE thường EXACT; DECIMAL PREFILTER |
| `/` | Chia nguyên có kiểm tra MIN/-1, BigInteger, float/double hoặc BigDecimal theo kiểu | Chia nguyên/DECIMAL có thể lỗi → PREFILTER; float/double giữ Infinity/NaN như Trino |
| `%`, `mod(a,b)` | `%` hoặc `sqlArithmetic(..., modulo)` | Integral với mẫu số hằng khác 0 có thể EXACT; trường hợp có thể lỗi giữ residual; REAL/DOUBLE hỗ trợ |
| Unary `-` | Compact checked negate hoặc `sqlMath(..., negate)` | Không âm thầm wrap số nguyên cực tiểu |
| `=`, `<>`, `<`, `<=`, `>`, `>=` | `sqlCompare`, hoặc so sánh compact | Nullable. Float/double dùng quy tắc IEEE của Trino: `NaN = NaN` FALSE, so sánh thứ tự với NaN FALSE; `<>` TRUE |
| `IS NOT DISTINCT FROM` | `sqlCompare(..., identical)` | Hai NULL là giống nhau; hai NaN là giống nhau. Lambda TRUE tại NULL không đủ điều kiện exact script |
| `IS DISTINCT FROM` | NOT của identical | Có thể local nếu TRUE tại NULL; ví dụ `x IS DISTINCT FROM 12` |
| `AND`, `OR`, `NOT` | `sqlBoolean`: kết hợp TRUE/FALSE/NULL | Không dùng trực tiếp toán tử boolean Java cho nullable. Từng phép tính trong lambda vẫn dùng cùng `x` |
| `IN`, `NOT IN` | `sqlIn` và `sqlBoolean NOT` | Danh sách hằng; giữ UNKNOWN khi có NULL và không có phần tử bằng |
| `BETWEEN`, `NOT BETWEEN` | Hai `sqlCompare` kết hợp bằng `sqlBoolean`, rồi NOT nếu cần | Không tách thành các phần tử khác nhau |
| `IS NULL`, `IS NOT NULL` | Kiểm tra nullable trong script | Dùng trong cây biểu thức; phải qua chứng minh toàn bộ lambda tại NULL |
| `COALESCE` | `sqlCoalesce` | `coalesce(x,0)>10` hợp lệ; `coalesce(x,100)>10` local vì NULL có thể TRUE. Nhánh fallible vẫn khiến toàn bộ script PREFILTER |
| `NULLIF` | `sqlNullIf` | Dùng equality SQL, giữ nullable và kiểu |
| Searched `CASE WHEN ... THEN ... ELSE ... END`, `IF` khi được planner hạ về cùng IR | `$case` từ planner; Painless ternary chọn nhánh khi condition TRUE | Bổ sung round-trip SPI/planner. Chỉ nhánh được chọn được đánh giá; điều kiện NULL đi tiếp. Hình dạng có Let/single-evaluation chưa dịch được vẫn local |
| `CAST` integral → integral | Widening hoặc `sqlCast` có kiểm tra giới hạn | Widening thường EXACT; narrowing có thể lỗi → PREFILTER |
| `TRY_CAST` integral → integral | `sqlCast(..., tryCast=true)`, lỗi chuyển đổi trả NULL | Có thể EXACT nếu toàn bộ lambda an toàn tại NULL |
| `CAST` numeric → REAL/DOUBLE | Float/double conversion trong `sqlCast` | Hỗ trợ cả integral/DECIMAL; giữ kiểu tính toán của SQL |
| `CAST` integral ↔ DECIMAL, DECIMAL → DECIMAL | `sqlCast`, `sqlDecimalCheck`, `HALF_UP` | Scale/precision được kiểm tra; có thể lỗi → PREFILTER. Widening được chứng minh an toàn có thể EXACT |
| `TRY_CAST` DECIMAL → integral/DECIMAL, numeric → REAL/DOUBLE | Cùng `sqlCast`, lỗi chuyển đổi trả NULL | Có thể EXACT nếu cây con và chứng minh NULL đủ điều kiện; không bắt lỗi của biểu thức con thay cho `TRY` tổng quát |
| REAL/DOUBLE → integral/DECIMAL, VARCHAR parsing/formatting | Chưa dịch | Local; không sử dụng cast Java thay thế ngữ nghĩa Trino |

DECIMAL dùng BigDecimal và hằng số thập phân dạng chuỗi; không đổi sang double để so sánh. Cộng/trừ/mod/multiply bị từ chối nếu kiểu kết quả làm giảm scale ngoài phạm vi được chứng minh. Phép chia làm tròn HALF_UP theo scale của kiểu kết quả. Hằng REAL/DOUBLE được truyền bằng raw bits để NaN/Infinity không phụ thuộc biểu diễn JSON số.

## 6. Hàm số

| Hàm SQL | Painless/helper | Trạng thái |
| --- | --- | --- |
| `abs` | `sqlMath`, `BigInteger.abs`, `BigDecimal.abs`, `Math.abs` | Integral/DECIMAL giữ residual; REAL/DOUBLE có thể EXACT |
| `ceil`, `ceiling`, `floor` | `sqlMath`, `setScale(0, CEILING/FLOOR)` hoặc `Math.ceil/floor` | Theo overload numeric được hỗ trợ |
| `round(x)`, `round(x,n)` | `sqlMath`, HALF_UP hoặc helper mô phỏng Trino round | `n` integral; DECIMAL yêu cầu `n` hằng trong [-38,38]; giữ lỗi round integral |
| `truncate(x)` | `sqlMath`, BigDecimal DOWN hoặc sign × floor(abs) | Chỉ overload một đối số; `truncate(x,n)` chưa hỗ trợ |
| `sign` | BigInteger/BigDecimal signum hoặc `Math.signum` | Giữ kiểu kết quả SQL; integral/DECIMAL hiện bảo thủ PREFILTER |
| `sqrt` | `Math.sqrt` | Overload DOUBLE, có thể có coercion từ REAL/integral |
| `pow`, `power` | `Math.pow` | Overload DOUBLE; không thêm phép lũy thừa integer riêng |

## 7. Hàm chuỗi

| Hàm/biểu thức SQL | Native trước, script sau | Trạng thái script B |
| --- | --- | --- |
| `length(x)` | `sqlString` → `codePointCount` | EXACT nếu cây con exact |
| `starts_with`, `ends_with` | Prefix native cho starts_with đủ điều kiện; script `startsWith`/`endsWith` | EXACT nếu cây con exact |
| `strpos(x,s)` | `indexOf` rồi chuyển vị trí sang code point + 1 | EXACT; không thấy trả 0 |
| `replace(x,s[,r])` | `sqlString`, thay literal; mẫu rỗng chèn tại ranh giới code point | PREFILTER; nhận document để Trino xử lý nếu kết quả mở rộng quá giới hạn bảo vệ |
| `concat(a,b,...)`, chuỗi `\|\|` được planner hạ về concat | `sqlConcat` | PREFILTER; bảo toàn lỗi giới hạn 1 MiB UTF-8 của Trino |
| `substr(x,start)`, `substring(x,start)` | `sqlSubstring`, vị trí code point, hỗ trợ vị trí âm | EXACT nếu cây con exact |
| `substr(x,start,len)`, `substring(x,start,len)` | `sqlSubstring` | PREFILTER; độ dài >1.000.000 được nhận lại để Trino xử lý các trường hợp biên |
| `trim`, `ltrim`, `rtrim` | Cắt tập whitespace của Trino, không gọi Java `String.trim()` | Overload một đối số; EXACT nếu cây con exact |
| `lower`, `upper` | `sqlString`, chuyển ASCII; gặp non-ASCII thì nhận document | PREFILTER; Trino xử lý Unicode để tránh sai khác phiên bản Unicode giữa JVM |
| `LIKE` constant `[ESCAPE e]` | Native prefix/regexp nếu exact; fallback `sqlLike` với token `%`, `_`, escape | Script theo code point; không cần bật Painless regex. Pattern tối đa 1024 code point |
| So sánh chuỗi | `sqlStringCompare`, so thứ tự code point | Không dùng UTF-16 `String.compareTo` làm sai thứ tự emoji; chỉ hỗ trợ khi cây nullable đủ điều kiện |
| `regexp_like` không dịch được native | Chưa thêm regex engine trong Painless | Local hoặc prefilter khác; inline flags/end anchor không tự động được hỗ trợ bởi B |

## 8. Ngày giờ

Tất cả biểu thức ngày giờ dưới đây dùng phần tử `TIMESTAMP(3)` không time zone, epoch millisecond, UTC; đều giữ residual. Không mở rộng sang timestamp có time zone hay độ chính xác micro/nanosecond.

| Hàm SQL | Painless/helper | Đơn vị/phạm vi |
| --- | --- | --- |
| `year`, `month`, `day`, `day_of_month` | `sqlTemporal` → UTC `ZonedDateTime` getters | Trích xuất thành phần lịch |
| `hour`, `minute`, `second` | `sqlTemporal` → UTC getters | Trích xuất thời gian |
| `day_of_week`, `dow` | `getDayOfWeek().getValue()` | ISO thứ Hai=1, Chủ Nhật=7 |
| `day_of_year`, `doy` | `getDayOfYear()` | Có năm nhuận |
| `date_trunc(unit,x)` | `sqlTemporal`, đặt lại thành phần UTC | unit hằng: millisecond, second, minute, hour, day, week, month, quarter, year; tuần bắt đầu thứ Hai |
| `date_add(unit,n,x)` | `sqlTemporal`, phép cộng thời gian/lịch | Cùng tập unit; kiểm tra giới hạn `n`, quarter và timestamp; lỗi nhận document về Trino |
| `date_diff(unit,a,b)` | `sqlDateDiff`, chênh lệch epoch / hệ số đơn vị, chia về 0 | unit hằng: millisecond, second, minute, hour, day, week |
| `date_diff('month'/'quarter'/'year',...)` | Chưa dịch | Local vì chưa chứng minh tương đương cách tính lịch của Trino |

Null proof của các hàm thời gian kiểm tra mọi đối số, kể cả lượng cộng của `date_add` và cả hai timestamp của `date_diff`. Nếu một đối số có thể gây lỗi khi phần tử array là NULL, connector giữ local để không che lỗi SQL trên phần tử không có trong doc values.

## 9. Ví dụ SQL và hình dạng query

```sql
-- Native; tính giao tập giá trị thành term 12
SELECT id FROM events
WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);

-- Hybrid exact khi numbers là integral đủ điều kiện và script được bật
SELECT id FROM events
WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);

-- Script exact cho phép tính DOUBLE, nếu mapping/source/null đủ điều kiện
SELECT id FROM events
WHERE any_match(doubles, x -> x * DOUBLE '1.5' > DOUBLE '10');

-- Script prefilter; giữ lambda để Trino xử lý lỗi và kiểm tra kết quả
SELECT id FROM events
WHERE any_match(numbers, x -> abs(x) > 10);

-- Typed nullable expression: NULL không làm lambda TRUE
SELECT id FROM events
WHERE any_match(numbers, x -> coalesce(x,0) > 10);

-- Script chuỗi, vị trí tính theo code point
SELECT id FROM events
WHERE any_match(words, x -> length(x) > 2 AND ends_with(x,'😀'));

-- Script temporal prefilter
SELECT id FROM events
WHERE any_match(timestamps, x -> year(x)=2024);
```

Hình dạng DSL rút gọn cho OR native + script; `source` thực tế chứa helper và vòng lặp do connector sinh:

```json
{
  "bool": {
    "should": [
      {"range": {"numbers": {"gt": 10}}},
      {"script": {"script": {
        "lang": "painless",
        "source": "<generated loop: any indexed element has mod(x,2)=0>",
        "params": {"field": "numbers", "p0": 2, "p1": 0}
      }}}
    ],
    "minimum_should_match": 1
  }
}
```

Tên/thứ tự params có thể khác sau tối ưu. Đây là minh họa hình dạng, không phải payload có thể copy chạy nguyên văn.

## 10. Những phần chưa được tuyên bố hỗ trợ

- Toàn bộ SQL functions/toán tử còn lại, ví dụ lượng giác, logarithm, bitwise, JSON, binary, regex script tổng quát, string parsing/formatting, overload hai đối số của trim/truncate.
- `all_match`, `none_match`, array transformations/reductions, so sánh hai cột array và lambda bắt cột khác.
- Offload tổng quát cho `CASE` cần Let mà planner chưa xuất được; `TRY` tổng quát; predicate TRUE khi phần tử NULL.
- Elasticsearch scoring queries (`match`/`multi_match`/boosting...), geo, nested/object, joins, aggregation, sort/limit/projection: không nằm trong phần mở rộng array A/B này. Các tính năng có đường xử lý khác không được suy ra từ bảng này.
- Dùng script không làm một biểu thức bất kỳ tự được hỗ trợ: compiler vẫn là allowlist, không gửi SQL thô cho Elasticsearch tự hiểu.

Giới hạn script: tối đa 128 node (đồng thời chịu policy clause/depth), tham số theo policy, pattern LIKE tối đa 1024 code point, script tối đa 60.000 byte và request phải nằm trong budget của connector. Vượt giới hạn sẽ fallback; lỗi runtime ngoài các guard đã biết làm query thất bại, không tự bỏ điều kiện lọc.

## 11. Vị trí code và kiểm thử

| Thành phần | File |
| --- | --- |
| Chọn native/hybrid/script và lấy điều kiện cần | [ElasticsearchArrayPredicateTranslator.java:191](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java#L191) |
| Native tập giá trị, TRUE/FALSE/UNKNOWN, keyword | [ElasticsearchArrayNativePredicateTranslator.java:70](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayNativePredicateTranslator.java#L70) |
| Compact integral compiler + gọi typed fallback | [ElasticsearchArrayScriptTranslator.java:133](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayScriptTranslator.java#L133) |
| Typed compiler, NULL proof, allowlist và kiểm tra kiểu | [ElasticsearchExpressionScriptTranslator.java:80](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchExpressionScriptTranslator.java#L80) |
| Mã helper Painless connector sinh | [ElasticsearchScriptFunctions.java:20](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchScriptFunctions.java#L20) |
| CASE SPI và chuyển đổi hai chiều planner | [StandardFunctions.java](../../core/trino-spi/src/main/java/io/trino/spi/expression/StandardFunctions.java), [ConnectorExpressionTranslator.java](../../core/trino-main/src/main/java/io/trino/sql/planner/ConnectorExpressionTranslator.java) |
| Unit typed compiler | [TestElasticsearchExpressionScriptTranslator.java:48](src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchExpressionScriptTranslator.java#L48) |
| SQL thực tế ES 7/8, kết quả so với local, plan/residual và số document đọc | [BaseElasticsearchAnyMatchPushdownTest.java:599](src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchAnyMatchPushdownTest.java#L599) |

Các kiểm thử so sánh cùng SQL giữa session bật script và session `allow_pushdown_into_connectors=false`, kiểm tra fully-pushed-down cho trường hợp EXACT, kiểm tra lượng document đọc cho PREFILTER và kiểm tra Trino vẫn báo lỗi overflow/chia 0/cast ngoài giới hạn.

Kết quả kiểm tra B:

- 27 kiểm thử `TestConnectorExpressionTranslator`: qua.
- 153 kiểm thử connector: qua, gồm 139 kiểm thử unit và 14 ca tích hợp (7 trên Elasticsearch 7.17.27, 7 trên Elasticsearch 8.11.3).
- Biên dịch sạch Error Prone cho SPI, core và code production connector; test-compile connector bằng cùng profile: qua.
- CI verify và Airstyle cho các module thay đổi: qua; kiểm tra hãng JDK dùng ngoại lệ `requireJavaVendor` vì môi trường là Ubuntu JDK 25.
- Các ca số thực bao gồm signed zero, NaN equality/identical/IN, chia 0, số rất lớn/rất nhỏ; có kiểm tra cả script bật và tắt.

Kiểm tra phần mở rộng scalar:

- 225 kiểm thử hồi quy array/scalar: qua, gồm 18 ca tích hợp trên ES 7.17.27 và ES 8.11.3.
- Sau khi bổ sung guard không vượt qua mapping unsupported, 212 kiểm thử cuối qua: 208 unit và 4 ca tích hợp scalar ES 7/8. Có kiểm tra native priority, hybrid OR, NULL/missing, signed zero/NaN, overflow và lỗi cast/chia 0.
- `LOWER(word) LIKE '%sá%'` trên fixture keyword đọc 4/9 document sau remote prefilter, giữ residual và trả đúng 3 dòng. Có kiểm tra LIMIT trên kết quả SQL sau residual.
- Compiler production và test được biên dịch bằng Error Prone; cấu hình default/explicit, IR round-trip, field tracking, request budget và metrics scalar/array được kiểm tra.

- Ca bổ sung `LIMIT 3`: qua trên ES 7, trả đủ 3 dòng SQL từ 4 ứng viên remote có một document bị residual loại.

- Airstyle và CI verify module Elasticsearch: qua. CI dùng `-Denforcer.skipRules=requireJavaVendor` vì môi trường là Ubuntu JDK 25.
