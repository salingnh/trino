# Plan: mở rộng pushdown cho mọi field text theo chế độ UNSAFE

Ngày lập: 16/11/2026. Trạng thái: **đã triển khai code; đang kiểm thử và cập nhật kết quả VM**.

## 1. Mục tiêu và phạm vi

Mở một pipeline chung cho các biểu thức trên field Elasticsearch `text`, áp dụng cho cả cột scalar và `any_match(array(text), ...)`. LOWER là một trường hợp trong pipeline; không thêm rule đặc biệt chỉ cho một field, một câu SQL hay một hàm.

“Mọi trường hợp text” ở đây bao gồm mọi field text được connector nhận diện và các nhóm biểu thức đã hỗ trợ ở compiler/translator, kể cả tổ hợp hàm. Mỗi biểu thức đi qua lựa chọn native, script hoặc fallback theo khả năng thực tế. Điều này không có nghĩa Elasticsearch tự thực thi được mọi hàm SQL chưa có bản dịch.

Mục tiêu offload: ưu tiên truy vấn dùng index; dùng script đọc nguyên chuỗi khi native không biểu diễn được; giữ local cho phần còn lại nếu chưa có bản dịch hợp lệ. Ghi nhận riêng APPROXIMATE, EXACT, PREFILTER và LOCAL để không gọi kết quả tìm kiếm tương đối là SQL exact.

## 2. Option hiện có và cách bật

Dùng lại `full_text_pushdown_mode`, không tạo option chỉ cho LOWER:

```sql
SET SESSION osint_profiling_3517.full_text_pushdown_mode = 'UNSAFE';

-- Cho phép các đường fallback cần Painless:
SET SESSION osint_profiling_3517.scalar_script_pushdown_enabled = true;
SET SESSION osint_profiling_3517.array_script_pushdown_enabled = true;
```

Thay `osint_profiling_3517` bằng tên catalog thực tế. Cấu hình tương ứng ở catalog:

```properties
elasticsearch.full-text-pushdown=UNSAFE
elasticsearch.scalar-script-pushdown.enabled=true
elasticsearch.array-script-pushdown.enabled=true
```

| Chế độ | Hành vi dự kiến |
| --- | --- |
| DISABLED | Giữ các đường exact hiện có; không bật các phép xấp xỉ text mới. |
| SAFE | Giữ hợp đồng hiện có. Chỉ dùng candidate khi đã chứng minh cần thiết; không đổi sang full-text rồi giả định residual có thể khôi phục document bị loại. |
| UNSAFE | Cho phép các bản dịch text APPROXIMATE đã được định nghĩa. Elasticsearch là nơi quyết định kết quả của phần đã chấp nhận xấp xỉ; không chạy lại nguyên predicate SQL đó ở Trino. |
| UNSAFE, script flags tắt | Dùng các bản dịch native text; phần cần script có thể còn local. |
| UNSAFE, script flags bật | Xét thêm doc values đủ điều kiện hoặc runtime script đọc `_source` cho biểu thức phức tạp. |

Các flag script độc lập với full-text mode. Không đổi mặc định DISABLED của full-text hoặc mặc định false của hai flag script.

## 3. Hiện trạng đã xác minh

| Điểm hiện tại | Hệ quả | Vị trí |
| --- | --- | --- |
| LIKE scalar yêu cầu đối số đầu là Variable | `LOWER(text) LIKE ...`, cũng như các cây biến đổi chuỗi khác, chưa vào được đường full-text LIKE này. | [ElasticsearchFullTextPredicateTranslator.java:70](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchFullTextPredicateTranslator.java#L70) |
| UNSAFE đã dịch LIKE literal và contains-literal nhiều token thành match_phrase | Có thể dùng lại rule `%sách rất%`; phần còn thiếu là xử lý cây biểu thức chứa field text. | [RewriteAnalyzedTextLike.java:127](src/main/java/io/trino/plugin/elasticsearch/expression/RewriteAnalyzedTextLike.java#L127) |
| Compiler script chỉ chấp nhận chuỗi có mapping keyword đủ điều kiện và doc values tương đương nguồn | Đổi flag không tự giúp text đi vào compiler này. Cần tách cách đọc giá trị khỏi phần biên dịch biểu thức. | [ElasticsearchExpressionScriptTranslator.java:106](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchExpressionScriptTranslator.java#L106) |
| Scalar được xét trước khi planner phân rã AND/OR và gọi full-text translator | Cần phối hợp thứ tự thử; kết quả unsupported của một frontend không được chặn frontend text khác. | [ElasticsearchPredicatePushdownPlanner.java:198](src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicatePushdownPlanner.java#L198) |
| Search/Scroll/PIT chưa gắn runtime_mappings ở searchBody | Runtime predicate cần đi cùng định nghĩa field trong request, không chỉ một Query DSL node. | [ElasticsearchClient.java:784](src/main/java/io/trino/plugin/elasticsearch/client/ElasticsearchClient.java#L784) |
| COUNT tạo search body riêng với size=0 và track_total_hits=true | Cần truyền runtime definitions tới COUNT, không chỉ đường scan. | [ElasticsearchClient.java:952](src/main/java/io/trino/plugin/elasticsearch/client/ElasticsearchClient.java#L952) |

## 4. Các nhóm biểu thức phải được xét

| Nhóm | Ví dụ | Đường dự kiến |
| --- | --- | --- |
| Predicate trực tiếp trên text | equality, IN, LIKE, starts_with, regexp_like | Native hiện có và mở rộng có quy tắc; không đổi tùy ý range chuỗi thành tìm kiếm từng từ. |
| LIKE nguyên literal hoặc contains literal | `text LIKE '%sách rất%'` | match_phrase theo analyzer. |
| LIKE prefix / prefix function | `text LIKE 'sách rất%'`, `starts_with(text,'sách rất')` | match_phrase_prefix theo hợp đồng UNSAFE đã có. |
| Chuyển hoa/thường | `LOWER(text) LIKE ...`, `UPPER(text) LIKE ...` | Native khi có quy tắc xấp xỉ được công bố; nếu không phù hợp thì script trên nguyên chuỗi. |
| Cắt và sửa chuỗi | trim/ltrim/rtrim, substr/substring, replace, concat | Tái sử dụng compiler với nguồn đọc mới; chỉ dùng native khi thực sự có rule tương ứng. |
| Hàm trả số/boolean | length, strpos, ends_with, so sánh kết quả tính toán | Script hoặc native chuyên biệt nếu đã chứng minh/định nghĩa được bản dịch. |
| Biểu thức nullable | COALESCE, NULLIF, CASE/IF được planner hỗ trợ, IS NULL/IS NOT NULL | Script phải đọc được NULL/missing gốc và giữ quy tắc đánh giá nhánh. |
| Logic và phủ định | AND, OR, NOT, IN/NOT IN, BETWEEN trên kết quả biểu thức | Composer chung + null semantics; không bỏ nhánh OR chưa dịch được. |
| Tổ hợp hàm | `length(trim(text))>5`, `LOWER(replace(text,'_',' ')) LIKE ...` | Biên dịch cả cây; không chỉ bóc một lớp LOWER. |
| Text array | `any_match(comments,x -> length(trim(x))>5)` | Dùng cùng bộ biểu thức; script xét toàn bộ lambda trên từng phần tử nguồn. |
| Nhiều predicate/cột | `n>10 OR LOWER(text) LIKE ...` | Ghép các predicate có bản dịch; cây tính toán nhiều cột chỉ offload khi binder/compiler thực sự hỗ trợ. |

Field text có subfield keyword cũng được xét trong pipeline. Chọn đường dựa vào khả năng và ngữ nghĩa, không loại toàn bộ vì có/không có keyword. Khi chọn analyzed text, query dùng remote name của field text; khi chọn exact keyword, áp dụng các guard tương đương nguồn hiện có.

## 5. Thiết kế xử lý

### A. Bộ phân tích và lựa chọn đường cho text

- Nhận diện field text trong cả predicate trực tiếp và cây hàm; không hardcode tên field hoặc mẫu `%sách rất%`.
- Mang theo biểu thức gốc, field path, mapping, kiểu scalar/array, mode và script flags.
- Ưu tiên đường exact đủ điều kiện đã có. Trong UNSAFE, thử native full-text có quy tắc; sau đó script nếu được bật; cuối cùng candidate có ích hoặc local.
- Kiểm tra mapping trên backing indices và giữ đúng remote field name, gồm đường dẫn và chữ hoa/thường.
- Một frontend trả unsupported không được coi là quyết định local cuối cùng nếu frontend text khác vẫn có thể dịch.

### B. Native full-text dùng chung

- Tái sử dụng parser pattern: literal và `%literal%` -> match_phrase; prefix -> match_phrase_prefix; regex/pattern khác theo tập hỗ trợ thực tế.
- Xử lý ESCAPE bằng parser, không xóa ký tự `%`/`_` tùy ý. Pattern chỉ có wildcard, literal rỗng và pattern sai phải có rule riêng hoặc fallback.
- LOWER/UPPER chỉ được loại bỏ trong rule UNSAFE tường minh: kết quả theo analyzer của Elasticsearch, không tuyên bố đã chạy LOWER/UPPER của Trino. Kiểm thử analyzer chuẩn và analyzer tùy chỉnh.
- Không bỏ trim/replace/substr hoặc các hàm khác chỉ để có một query match_phrase; các phép biến đổi đó cần rule riêng hoặc script thực sự đánh giá biểu thức.
- Với câu SQL mục tiêu, query dự kiến là:

```json
{
  "query": {
    "match_phrase": {
      "raw_review_comment": "sách rất"
    }
  }
}
```

- Ghi enforcement APPROXIMATE và bỏ residual của phần đã dịch xấp xỉ; phần query khác chưa dịch vẫn cần Trino xử lý.
- Dùng lại composition policy và budget. Không tạo phương thức UNBOUNDED chỉ vì UNSAFE.

### C. Script chung trên giá trị nguyên gốc

- Tách expression compiler khỏi binder đọc giá trị: binder doc values hiện có và binder `_source` mới cùng sử dụng bộ phép tính/hàm.
- Ưu tiên doc values của keyword khi đủ bằng chứng và phù hợp đường đang chọn. Khi chỉ có text hoặc subfield keyword không đáng tin cậy, runtime script đọc `_source` nếu khả dụng.
- Dùng runtime_mappings trong search request; script runtime context có quyền đọc `params._source`. Không giả định mọi script-query context đều truy cập `_source` được.
- Đọc field path qua cấu trúc JSON; phân biệt tên key có dấu chấm và đường dẫn object theo metadata connector. Đọc đúng case, NULL, missing, chuỗi rỗng và array.
- Với array, xét nguyên predicate trên từng phần tử; không làm hai phần tử khác nhau thỏa riêng hai nhánh AND. Binder nguồn giữ được phần tử NULL nên có thể mở rộng các ca vốn bị chặn ở binder doc values sau khi kiểm chứng.
- Định nghĩa riêng ngữ nghĩa string ở UNSAFE. Với khác biệt Unicode/normalization so với Trino, dùng chính sách Elasticsearch đã công bố và gắn APPROXIMATE; không giữ cơ chế “nhận mọi Unicode” rồi bỏ residual, vì như vậy sẽ cho qua mọi chuỗi chưa được kiểm tra.
- Biểu thức được chứng minh exact vẫn có thể EXACT; biểu thức fallible cần guard/residual đúng hợp đồng. UNSAFE không có nghĩa nuốt mọi lỗi hoặc biến biểu thức chưa hiểu thành TRUE.
- Chỉ tiếp tục với overload và kiểu đã biên dịch được. Không dùng eval SQL tùy ý hoặc nối giá trị người dùng vào source code script.

### D. IR, request và tích hợp planner

- Bổ sung mô tả runtime predicate/field dependencies trong IR; đi cùng query qua serialize/deserialize, canonicalization, field tracking và request budget.
- Tạo tên runtime field ổn định, tránh trùng với field người dùng và các runtime expressions khác. Hằng số đi qua params.
- Kết hợp runtime definitions của các nhánh AND/OR mà không ghi đè nhau; kiểm tra số lượng, kích thước script, tham số và request tổng.
- Gắn definitions vào Scroll, từng request PIT/search_after, COUNT và đường aggregation khi sử dụng predicate này. Không bật aggregation/sort mới chỉ vì xuất hiện runtime field; kiểm tra khả năng hiện có và fallback đúng chỗ.
- Kiểm tra domain/range do optimizer sinh để không còn một điều kiện so sánh chuỗi gốc vô tình lọc tiếp kết quả đã chấp nhận analyzer semantics.
- NOT cần giữ TRUE/FALSE/UNKNOWN và NULL/missing; không đơn giản bọc must_not rồi đưa cả document NULL vào kết quả. Dùng guard hoặc script khi có bản dịch hợp lệ, còn lại giữ local.
- LIMIT, TopN, COUNT và projection sử dụng đúng enforcement: residual còn thì không giới hạn sai số ứng viên; predicate approximate đã nhận thì không bị đánh giá lại nguyên SQL ở bước khác.

## 6. Thứ tự thực hiện

| Đợt | Công việc | Kết quả review được |
| --- | --- | --- |
| 1 | Pipeline chung, policy mode, native rules cho predicate text và case conversion đủ điều kiện | Câu LOWER mục tiêu offload qua match_phrase; các nhóm native text đi cùng một cơ chế. Không đợi runtime fallback mới có kết quả này. |
| 2 | Binder `_source`, tái sử dụng typed compiler, runtime IR và request plumbing | Các nhóm biến đổi chuỗi, nullable, tổ hợp hàm và text array có đường script thực tế; không chỉ xử lý LOWER. |
| 3 | Composer/NULL/NOT, domains, Scroll/PIT/COUNT/aggregation, diagnostics và budget | Các đường xử lý query dùng predicate mới nhất quán, không tái áp SQL exact lên kết quả approximate. |
| 4 | Unit + integration ES7/ES8, ma trận so sánh, đo document/byte và cập nhật tài liệu | Có SQL, mode/flag, DSL, enforcement, kết quả chuẩn tương ứng và lượng dữ liệu truyền về để review. |

Hoàn thành toàn bộ plan bao gồm đợt 2–4; không dừng ở việc thêm hỗ trợ LOWER của đợt 1.

## 7. Kiểm thử bắt buộc

| Nhóm | Ca cần kiểm chứng |
| --- | --- |
| Mode và flags | DISABLED/SAFE/UNSAFE; script scalar và array bật/tắt độc lập; native hoạt động khi script tắt; đường exact cũ không bị biến thành approximate. |
| Native text | LIKE/equality/IN/prefix/regex hỗ trợ; LOWER/UPPER; chuỗi một từ và nhiều từ; literal chứa `%`, `_`, escape; wildcard rỗng và pattern sai. |
| Analyzer | standard, case-preserving, lowercase, asciifolding, stopwords; dữ liệu tiếng Việt có dấu, dấu phẩy và dấu cách. |
| Compiler text | Từng hàm chuỗi trong allowlist và cây tổ hợp; numeric/boolean result, NULLIF/COALESCE/CASE; kiểu hoặc overload chưa hỗ trợ. |
| Dữ liệu nguồn | NULL, missing, rỗng, Unicode/emoji, review dài vượt ignore_above, field scalar sai shape, array rỗng/NULL/nhiều phần tử, field path/case và nhiều backing indices. |
| Logic | AND/OR với các nhánh native/script/local; NOT với NULL/missing; array `["sách", "rất"]` không bị coi là một chuỗi nguồn `"sách rất"` trong đường script exact. Ghi rõ khác biệt của full-text array nếu chấp nhận approximate. |
| Runtime requests | Scalar/array, nhiều runtime fields trong một query, COUNT, query nhiều trang Scroll/PIT, LIMIT và các đường aggregation hiện có. |
| Hiệu quả | Fixture tối thiểu 10.000 document; đo source documents, response bytes, query/ES time riêng. Không suy ra dùng runtime là nhanh như native index. |
| Regression | Bộ test array/scalar cũ, signed zero, regex newline, overflow và temporal NULL; kiểm tra budget và fallback. |

Hai cách đối chiếu kết quả:

1. EXACT/PREFILTER bảo toàn SQL: đối chiếu với Trino local.
2. UNSAFE APPROXIMATE: đối chiếu với Query DSL Elasticsearch hoặc runtime policy đã định nghĩa. Ghi riêng sai biệt so với SQL local; không đánh fail chỉ vì khác biệt mà người dùng đã chấp nhận, cũng không coi APPROXIMATE là EXACT.

Các ca native fully pushed phải chứng minh không có Filter/residual của predicate đã nhận và không có script/runtime_mappings. Các ca runtime phải chứng minh có definitions đúng trong request, không chỉ assertion kết quả SQL.

## 8. File/module dự kiến thay đổi

| Thành phần | Trách nhiệm |
| --- | --- |
| ElasticsearchFullTextPredicateTranslator / RewriteAnalyzedTextLike và frontend text dùng chung | Nhận cây biểu thức text, native full-text, pattern parsing, mode. |
| ElasticsearchScalarPredicateTranslator / ElasticsearchArrayPredicateTranslator | Gọi frontend text đúng thứ tự cho scalar và lambda. |
| ElasticsearchExpressionScriptTranslator / ElasticsearchScriptFunctions | Binder nguồn, tái sử dụng các biểu thức và policy string UNSAFE. |
| expression/ElasticsearchRemotePredicate / query builder / normalizer / budget | Runtime dependencies, serialization và dựng query. |
| ElasticsearchPredicatePushdownPlanner / ElasticsearchPredicateComposer | Enforcement, residual, domains, AND/OR/NOT. |
| client/ElasticsearchClient và các page source liên quan | Truyền runtime_mappings vào mọi request sử dụng predicate. |
| Diagnostics, unit tests, BaseElasticsearchFullTextPushdownTest và tests array/scalar | Lý do chọn/fallback, request assertions và ma trận ES7/ES8. |
| docs connector, support table và lab comparison | Option, ngữ nghĩa tương đối, SQL thực tế và số liệu offload. |

## 9. Điều kiện hoàn thành

- Không còn giới hạn “field là text thì không xét compiler”; mọi nhóm biểu thức trong bảng được đi qua pipeline chung và có test kết quả/chọn đường.
- SQL mục tiêu dưới UNSAFE dùng match_phrase, không Painless, không residual của LOWER LIKE.
- Các phép biến đổi chuỗi đã hỗ trợ có fallback thực sự trên nguyên chuỗi của text khi bật script; không chỉ bỏ wrapper để tạo query native.
- Scalar và any_match(array(text)) được kiểm thử trên ES7.17.27 và ES8.11.3; cờ bật/tắt và các query COUNT/LIMIT/PIT/Scroll hoạt động đúng.
- Không còn nội dung tài liệu nhầm APPROXIMATE với prefilter bảo toàn SQL hoặc native exact.
- Đưa ra bảng SQL → DSL/script → enforcement → kết quả/semantic delta → số document/byte. Không tuyên bố mọi hàm SQL hoặc mọi analyzer có cùng kết quả.

## 10. Cơ sở Elasticsearch

- [match_phrase phân tích query theo analyzer và tìm theo vị trí token](https://www.elastic.co/guide/en/elasticsearch/reference/8.19/query-dsl-match-query-phrase.html).
- [Runtime field có thể đọc `_source`, gồm trường hợp text không có doc values](https://www.elastic.co/guide/en/elasticsearch/reference/7.17/runtime-mapping-fields.html).
- [runtime_mappings có thể khai báo ngay trong search request](https://www.elastic.co/guide/en/elasticsearch/reference/8.19/runtime-search-request.html).

Runtime fallback chuyển việc đọc/tính toán sang Elasticsearch và có thể giảm dữ liệu về Trino; nó vẫn cần xử lý document ứng viên, không có hiệu năng truy cập index như native cho cùng điều kiện.
