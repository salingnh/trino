# ES8: dùng index, regexp, script và tác động tải

Phạm vi: phân tích lại bằng chứng ES8 của benchmark ngày 18/11/2026 và chạy chẩn đoán mới **chỉ ES8**. Giữ nguyên ba Trino; không chạy lại ES7. [Bảng các ca chậm riêng](EXPRESSION-ES8-SLOW-CASES.md).

## Kết luận đã kiểm chứng

- Không chia đơn giản thành “term dùng index, regex scan document”. Phải xét field, mapping và cây Lucene thực tế.
- Numeric `term`/`terms` đã ra `PointRangeQuery`/`PointInSetQuery`. Đây là truy vấn point index của số, khác keyword `TermQuery` dùng term/postings.
- Các numeric range hiện ra `IndexOrDocValuesQuery`. Wrapper này có thể chọn index hoặc doc values theo kế hoạch Lucene; profile hiện tại không đủ khẳng định mọi range luôn đi BKD hoặc mọi doc đều bị scan.
- Keyword `prefix` và `regexp` hiện ra `MultiTermQueryConstantScoreBlendedWrapper`, có các `TermQuery` con. Regexp duyệt/khớp từ điển term bằng automaton rồi lấy doc từ postings; không chạy Painless regex trên từng `_source`.
- `term` trên `__trino_text_<hash>` có `runtime_mappings` ra **`BooleanScriptFieldTermQuery`**. Field đó không được index sẵn; vẫn tính script lúc query, không phải native term lookup rẻ.
- `doc[... ]` đọc doc values nhanh hơn đọc source, nhưng không tạo index cho biểu thức như modulo/date_diff. Profile script thuần báo 100.000 lần `match` trên 100.000 document.

Lucene mô tả regexp là automaton duyệt từ điển term và cảnh báo pattern bắt đầu `.*` có thể đắt. [Lucene 9.8 RegexpQuery](https://lucene.apache.org/core/9_8_0/core/org/apache/lucene/search/RegexpQuery.html). ES phân biệt script cần linear scan với regexp có chi phí upfront cao; [ES8.11 Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/query-dsl.html), [ES8.11 regexp](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/query-dsl-regexp-query.html).

## Toàn bộ 40 SQL: DSL thật và lớp Lucene thật

Profile chẩn đoán dùng 1 shard / 100.000 document, `size=0`, `track_total_hits=true`, cùng predicate/runtime definitions từ request đã ghi. Mục đích là nhận dạng đường thực thi và số lần `match`, không thay latency của SQL trả hết dữ liệu. Profile có overhead và không đo đầy đủ network/queue/coordinator; [Profile API 8.11](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/search-profile.html).

| SQL ID / SQL | DSL rút gọn | Cơ chế | Lớp Lucene trong profile | Script match calls |
| --- | --- | --- | --- | ---: |
| `A-P1`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND sin(x) &gt; 0)</code> | <code>{"bool":{"filter":[{"range":{"numbers":{"gt":10}}}]}}</code> | Native range trên field số; không có Painless | ConstantScoreQuery, IndexOrDocValuesQuery | 0 |
| `A-P3`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; (x &gt; 10 AND sin(x) &gt; 0) OR (x &lt; 0 AND cos(x) &gt; 0))</code> | <code>{"bool":{"filter":[{"bool":{"should":[{"range":{"numbers":{"gt":10}}},{"range":{"numbers":{"lt":0}}}],"minimum_should_match":1}}]}}</code> | Native range trên field số; không có Painless | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery | 0 |
| `A-CAPTURE-PREFILTER`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers,x -&gt; x&gt;10 AND x&gt;n)</code> | <code>{"bool":{"filter":[{"range":{"numbers":{"gt":10}}}]}}</code> | Native range trên field số; không có Painless | ConstantScoreQuery, IndexOrDocValuesQuery | 0 |
| `A-N11`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x NOT IN (5, 12))</code> | <code>{"bool":{"filter":[{"bool":{"should":[{"range":{"numbers":{"lt":5}}},{"range":{"numbers":{"gt":5,"lt":12}}},{"range":{"numbers":{"gt":12}}}],"minimum_should_match":1}}]}}</code> | Native range trên field số; không có Painless | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery | 0 |
| `A-N10`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x IN (5, 12, NULL))</code> | <code>{"bool":{"filter":[{"terms":{"numbers":[5,12]}}]}}</code> | Native term/terms trên field số; kiểm tra Lucene point/index qua profile | ConstantScoreQuery, PointInSetQuery | 0 |
| `A-N15`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x IN (5,12) AND x &gt; 10)</code> | <code>{"bool":{"filter":[{"term":{"numbers":12}}]}}</code> | Native term/terms trên field số; kiểm tra Lucene point/index qua profile | ConstantScoreQuery, PointRangeQuery | 0 |
| `A-N18`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x IS NOT DISTINCT FROM 12)</code> | <code>{"bool":{"filter":[{"term":{"numbers":12}}]}}</code> | Native term/terms trên field số; kiểm tra Lucene point/index qua profile | ConstantScoreQuery, PointRangeQuery | 0 |
| `A-TXT06`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; starts_with(x,'a') AND starts_with(x,'ab'))</code> | <code>{"bool":{"filter":[{"prefix":{"words":"ab"}}]}}</code> | Prefix trên từ điển term của keyword index | BooleanQuery, ConstantScoreQuery, MultiTermQueryConstantScoreBlendedWrapper, TermQuery | 0 |
| `A-TXT05`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; x LIKE '%a!_%' ESCAPE '!')</code> | <code>{"bool":{"filter":[{"regexp":{"words":".*a_.*"}}]}}</code> | Regexp/automaton trên từ điển term của keyword index | ConstantScoreQuery, MultiTermQueryConstantScoreBlendedWrapper, TermQuery | 0 |
| `A-TXT25`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; regexp_like(x,'abc'))</code> | <code>{"bool":{"filter":[{"regexp":{"words":".*(abc).*"}}]}}</code> | Regexp/automaton trên từ điển term của keyword index | BooleanQuery, ConstantScoreQuery, MultiTermQueryConstantScoreBlendedWrapper, TermQuery | 0 |
| `A-I01`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x + 1 &gt; 10)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=d522de6effc9fc68]","params":{"p0":10,"p1":1,"field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-MOD-TINYINT`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(tiny_numbers, x -&gt; mod(x, TINYINT '2') = TINYINT '0')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=a415dd09a2a9739b]","params":{"p0":2,"p1":0,"field":"tiny_numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-I09`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | <code>{"bool":{"filter":[{"bool":{"should":[{"range":{"numbers":{"gt":10}}},{"script":{"script":{"lang":"painless","source":"[Painless sha256=a415dd09a2a9739b]","params":{"p0":2,"p1":0,"field":"numbers"}}}}],"minimum_should_match":1}}]}}</code> | Hybrid: native + script đọc doc values | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery, ScriptQuery | 50,000 |
| `A-I10`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | <code>{"bool":{"filter":[{"bool":{"filter":[{"range":{"numbers":{"gt":10}}},{"script":{"script":{"lang":"painless","source":"[Painless sha256=b015322c63ae1a77]","params":{"p0":10,"p1":2,"p2":0,"field":"numbers"}}}}]}}]}}</code> | Hybrid: native + script đọc doc values | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery, ScriptQuery | 50,000 |
| `A-I11`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; NOT (mod(x,2)=0))</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=0bffe55309d672ac]","params":{"p0":2,"p1":0,"field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-REAL01`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(reals, x -&gt; x + CAST(1.5 AS REAL) &gt; CAST(2 AS REAL))</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=0f01a87c8b70f3fd]","params":{"p0":1073741824,"p1":1069547520,"field":"reals"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-DEC01`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' &gt; DECIMAL '10.00')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=490e697e0d05a2b7]","params":{"p0":"10.00","p1":"1.25","field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `S-NULL5`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=a86a1ead3bbb28cd]","params":{"field":"n"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-MATH1`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; abs(x)&gt;5)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=e1b91bff23011af8]","params":{"p0":5,"p1":0,"field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-NULL4`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; coalesce(nullif(x,0),1)&gt;10)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=e2ded736c9af5e8f]","params":{"p0":10,"p1":0,"p2":1,"field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-NULL6`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; CASE WHEN x IS NULL THEN false ELSE x&gt;10 END)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=fc35e0fb5e618f6c]","params":{"p0":false,"p1":10,"field":"numbers"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-BOOL4`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(flags, x -&gt; x IS NOT DISTINCT FROM true)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=a45e94a07b1d1b73]","params":{"p0":true,"field":"flags"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-TXT10`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; strpos(x,'😀')&gt;0)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=0ee25655c0ff1c67]","params":{"p0":0,"p1":"😀","p2":"","field":"words"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-TXT16`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; substring(x,2)='bc')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=37a14abeb1fea0ed]","params":{"p0":2,"p1":9223372036854775807,"p2":"bc","field":"words"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-TXT13`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; concat(substr(x,1,1),'Z')='aZ')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=5cedef084aa1568e]","params":{"p0":1,"p1":1,"p2":"Z","p3":"aZ","field":"words"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-TXT20`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -&gt; lower(x) LIKE '%sá%')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=d1e1ecd29ca80181]","params":{"p0":"","field":"words"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-DATE2`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -&gt; month(x)=2)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=5486711555a55dc5]","params":{"p0":0,"p1":2,"field":"dates"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-ADD-month`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -&gt; date_add('month',1,x)&gt;TIMESTAMP '2024-03-01 00:00:00.000')</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=bac9b12b11a8e1c1]","params":{"p0":1709251200000,"p1":"month","p2":1,"p3":0,"field":"dates"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `A-DIFF-REVERSE`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -&gt; date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)&lt;=0)</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=0108d74b02ae296f]","params":{"p0":"day","p1":1735689600000,"p2":0,"p3":0,"field":"dates"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `S-I09`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | <code>{"bool":{"filter":[{"bool":{"should":[{"range":{"n":{"gt":10}}},{"script":{"script":{"lang":"painless","source":"[Painless sha256=b10298cacee97acc]","params":{"p0":2,"p1":0,"field":"n"}}}}],"minimum_should_match":1}}]}}</code> | Hybrid: native + script đọc doc values | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery, ScriptQuery | 72,222 |
| `S-NULL2`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE coalesce(n,100)&gt;10</code> | <code>{"bool":{"filter":[{"script":{"script":{"lang":"painless","source":"[Painless sha256=b7a56668f6b29426]","params":{"p0":10,"p1":100,"field":"n"}}}}]}}</code> | Script: đánh giá doc values từng ứng viên; không có index cho biểu thức tính | ConstantScoreQuery, ScriptQuery | 100,000 |
| `S-MIXED-OR`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n&gt;10 OR lower(word) LIKE '%sá%'</code> | <code>{"bool":{"filter":[{"bool":{"should":[{"range":{"n":{"gt":10}}},{"script":{"script":{"lang":"painless","source":"[Painless sha256=718fb1f404805b1b]","params":{"p0":"","field":"word"}}}}],"minimum_should_match":1}}]}}</code> | Hybrid: native + script đọc doc values | BooleanQuery, ConstantScoreQuery, IndexOrDocValuesQuery, ScriptQuery | 72,222 |
| `TEXT-USER-SQL`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%'</code> | <code>{"bool":{"filter":[{"match_phrase":{"raw_review_comment":"sách rất"}}]}}</code> | Phrase dùng inverted index/positions sau analyzer | ConstantScoreQuery, PhraseQuery | 0 |
| `S-TEXT02`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))&gt;2</code> | <code>{"bool":{"filter":[{"term":{"__trino_text_HASH":true}}]}}</code> | Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn | BooleanScriptFieldTermQuery, ConstantScoreQuery | 100,000 |
| `A-TEXT18`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE any_match(reviews, x -&gt; x IS NULL)</code> | <code>{"bool":{"filter":[{"term":{"__trino_text_HASH":true}}]}}</code> | Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn | BooleanScriptFieldTermQuery, ConstantScoreQuery | 100,000 |
| `S-TEXT06`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE ends_with(lower(trim(review)),'tốt')</code> | <code>{"bool":{"filter":[{"term":{"__trino_text_HASH":true}}]}}</code> | Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn | BooleanScriptFieldTermQuery, ConstantScoreQuery | 100,000 |
| `TEXT-NATIVE-with_keyword-lower-match_phrase`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(with_keyword) LIKE '%sách rất%'</code> | <code>{"bool":{"filter":[{"match_phrase":{"with_keyword":"sách rất"}}]}}</code> | Phrase dùng inverted index/positions sau analyzer | ConstantScoreQuery, PhraseQuery | 0 |
| `TEXT-COUNT`<br><code>SELECT count(*) FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))&gt;2</code> | <code>{"bool":{"filter":[{"term":{"__trino_text_HASH":true}}]}}</code> | Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn | BooleanScriptFieldTermQuery, ConstantScoreQuery | 100,000 |
| `TEXT-NESTED`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(profile.reviewcomment)&gt;2</code> | <code>{"bool":{"filter":[{"term":{"__trino_text_HASH":true}}]}}</code> | Runtime `_source`: term trên field tính lúc query; không có predicate index sẵn | BooleanScriptFieldTermQuery, ConstantScoreQuery | 100,000 |
| `TEXT-ZERO-TOKENS`<br><code>SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(foldedreview) LIKE '%the%'</code> | <code>{"bool":{"filter":[{"match_phrase":{"foldedreview":"the"}}]}}</code> | Phrase dùng inverted index/positions sau analyzer | MatchNoDocsQuery | 0 |

### Regexp trong chính bộ test này

`words` là keyword có index và chỉ có **17 term khác nhau**. Dữ liệu 100.000 document được lặp từ 18 document biên, nên tăng document không tăng độ đa dạng từ điển term. Bộ đo này không mô phỏng hàng triệu giá trị keyword khác nhau.

- `A-TXT05`: SQL `LIKE ... ESCAPE` được dịch thành `regexp words = ".*a_.*"`.
- `A-TXT25`: SQL `regexp_like(x,'abc')` thành `regexp words = ".*(abc).*"`.
- Cả hai thiếu prefix cố định nên có nguy cơ phải duyệt nhiều term khi cardinality cao. Trên bộ từ điển 17 term hiện tại chúng rất nhỏ; không thể suy regexp tương tự luôn rẻ trong production.
- Không có regexp trong bảy ca chậm hơn internal tại 8 shard / 100.000 document. Các ca chậm ở cấu hình khác đã giữ đầy đủ trong bảng riêng, không loại các regression regexp nếu có.

### Native guard giảm công việc script thế nào?

| SQL ID | Predicate | Script match calls, 1 shard / 100k |
| --- | --- | ---: |
| `A-MOD-TINYINT` | Modulo thuần | 100.000 |
| `A-I09` | Native range OR modulo | 50.000 |
| `A-I10` | Native range AND modulo, script kiểm tra cả lambda | 50.000 |
| `S-I09` | Scalar range OR modulo | 72.222 |
| `S-TEXT02` | Runtime source `length(trim(review))>2` | 100.000 |

Đây là counter `match_count` của node script trong Profile API của request đếm. Không phải số source document gửi về, không phải số phần tử array và không khẳng định toàn bộ các trang Scroll chạy đúng số lần đó. OR ở các ví dụ này đã tránh gọi script trên các doc có nhánh native đáp ứng; không suy mọi tổ hợp OR sẽ có cùng tối ưu.

Các runtime field không có index sẵn; chi phí phụ thuộc số document và phép tính. Script đọc `_source` còn phải đọc/giải mã nguồn thay vì chỉ lấy giá trị cột. [Runtime fields 8.11](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/runtime.html), [Doc values và source trong script](https://www.elastic.co/guide/en/elasticsearch/reference/8.11/modules-scripting-fields.html).

## Các query có làm ES làm việc nhiều/chậm hơn không?

**Có những trường hợp phần việc ở ES tăng rõ dù Trino nhận ít document hơn. Nhưng điều đó không tự chứng minh toàn cluster bị quá tải hoặc query của người khác bị chậm.**

Ví dụ từ benchmark gốc, 8 shard / 100.000 document. `ES took` dưới đây là median tổng took các request, có overlap giữa shard; không phải CPU hay wall time của cả query:

| SQL | Doc internal → mới | Median tổng ES took internal → mới (ms) | Wall SQL internal → mới (ms) |
| --- | ---: | ---: | ---: |
| `A-P1` | 100,000 → 50,000 | 1,850 → 2,156 | 1198.3 → 956.4 |
| `A-P3` | 100,000 → 66,667 | 1,938 → 2,957 | 1204.9 → 1274.4 |
| `A-N11` | 100,000 → 72,222 | 1,898 → 3,050 | 1205.0 → 1275.0 |
| `A-DIFF-REVERSE` | 100,000 → 83,334 | 1,920 → 3,898 | 1262.2 → 1651.6 |
| `S-MIXED-OR` | 100,000 → 61,111 | 1,961 → 4,662 | 1191.9 → 1691.4 |
| `S-TEXT02` | 100,000 → 85,000 | 1,014 → 3,608 | 1254.3 → 1624.6 |
| `TEXT-COUNT` | 100,000 → 0 | 1,153 → 163 | 1249.2 → 496.4 |

### Tải node trong benchmark gốc

Counter trước/sau mỗi cấu hình bao gồm ba Trino, warm-up, EXPLAIN và công việc nền trên node. Không quy riêng toàn bộ CPU đó cho bản mới. Queue chỉ có snapshot ở đầu/cuối, không có chuỗi thời gian đỉnh tải.

| Shard / doc | Khoảng đo (s) | CPU node tăng (s) | Search rejected Δ | Old GC Δ ms | Young GC Δ ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 / 10,000 | 253.9 | 176.7 | 0 | 0 | 665 |
| 4 / 10,000 | 150.9 | 155.1 | 0 | 0 | 652 |
| 8 / 10,000 | 192.8 | 146.7 | 0 | 0 | 691 |
| 1 / 50,000 | 759.6 | 524.3 | 0 | 0 | 1966 |
| 1 / 100,000 | 1424.9 | 1005.5 | 0 | 0 | 3579 |
| 4 / 50,000 | 330.8 | 559.6 | 0 | 0 | 1988 |
| 8 / 100,000 | 592.1 | 1071.2 | 0 | 0 | 3636 |

Không có search/search_coordination rejection mới và không có old GC trong các khoảng benchmark đã lưu; các queue snapshot đầu/cuối đều 0. Đây là không ghi nhận các dấu hiệu đó trong mẫu, không phải bằng chứng “ES không chịu tải”. Benchmark gốc không có canary đồng thời nên không kết luận được độ trễ query khác lúc đó.

### Canary đồng thời trong lượt chẩn đoán ES8 mới

Một process Python độc lập gửi term query rất nhỏ vào index canary riêng mỗi 250 ms. Request cache tắt. Process riêng tránh việc parse JSON SQL của harness giữ GIL làm giả độ trễ probe. Chỉ tổng hợp các khoảng đo thật, loại warm-up/profile/seeding và sample qua ranh giới phase. p95 là percentile mô tả của probe, không phải p95 SQL của ba repetition.

| Trạng thái | Số probe | Median ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| idle_before | 40 | 2.378 | 3.317 | 3.573 |
| master | 238 | 2.520 | 3.812 | 6.628 |
| internal | 237 | 2.431 | 3.665 | 30.916 |
| current | 232 | 2.416 | 3.379 | 6.758 |
| idle_after | 40 | 2.746 | 3.089 | 3.292 |

Tổng 1235 probe, 0 lỗi. Trong lượt chẩn đoán này không thấy canary bị tăng latency lớn, kéo dài riêng khi chạy bản mới; cả ba tải có mức p95 gần nhau. Có spike riêng lẻ và không có cam kết về SLA/workload khác: canary rất nhẹ, ES8 không có quota CPU Docker, host có 48 logical CPU, phép đo không phải stress test nhiều query đồng thời.

### CPU container đo trực tiếp

Node stats `process.cpu` được cache theo chu kỳ; nhiều query dưới một giây có delta 0 dù đã chạy. Các delta đó không được coi là CPU bằng 0. Pass bổ sung dùng cgroup v2 `cpu.stat/usage_usec` đọc trực tiếp quanh lần gửi SQL đến nhận hết kết quả, giữ một warm-up + ba repetition trên ba Trino. Đây là CPU toàn container ES8 trong khoảng đó, cộng tất cả threads nên có thể lớn hơn wall time; có thể gồm GC/công việc nền, không phải profiler CPU tuyệt đối của riêng request. Không chạy canary trong pass CPU.

| SQL ID | CPU ES8 master (ms) | CPU ES8 internal (ms) | CPU ES8 mới (ms) |
| --- | ---: | ---: | ---: |
| `A-P1` | 2764.5 | 2417.6 | 2815.6 |
| `A-P3` | 2757.2 | 3219.0 | 3755.2 |
| `A-N11` | 2416.0 | 2485.3 | 3898.6 |
| `A-N15` | 2272.6 | 2306.6 | 835.5 |
| `A-TXT05` | 2361.0 | 2368.0 | 656.5 |
| `A-TXT25` | 2344.4 | 2383.8 | 1732.7 |
| `A-I09` | 2360.8 | 2389.9 | 3255.6 |
| `A-I10` | 2488.8 | 2368.9 | 1717.7 |
| `A-DIFF-REVERSE` | 2301.2 | 2368.2 | 4239.5 |
| `S-MIXED-OR` | 2389.0 | 2334.1 | 5183.4 |
| `S-TEXT02` | 1480.9 | 1459.4 | 4339.9 |
| `A-TEXT18` | 1378.0 | 1365.6 | 4042.3 |
| `TEXT-COUNT` | 1423.4 | 1387.7 | 819.6 |
| `TEXT-NESTED` | 1537.9 | 1451.9 | 4082.1 |

## Giới hạn và bằng chứng

- Profile diagnostic đã chạy 43 request trên ES8: 40 predicate gốc + 3 control (keyword term, regexp rộng, match_all). Profile không được trộn vào latency benchmark.
- Pass node/canary chạy 14 SQL tập trung × 3 Trino × 4 lượt = 168 SQL. Pass CPU cgroup chạy riêng cùng 168 SQL để tránh cache của bộ đếm OS. Tất cả đạt oracle; không có query ES7 trong các pass mới.
- Các index chẩn đoán/canary dùng namespace riêng và đã thu hồi. Không đổi binary, không đổi `search.allow_expensive_queries`, compilation rate hoặc cache cluster.
- [Raw profile, canary, node/cgroup counters, SQL và source harness](expression-es8-diagnostics.json.gz).
- [Input ES8 trích từ benchmark gốc, chứa full DSL/script](expression-es8-audit-input.json.gz).
- [CSV phân loại 40 SQL](expression-es8-query-paths.csv), [CSV các ca chậm](expression-es8-slow-cases.csv).
- [Harness phân tích](expression-es8-audit.py), [harness profile/canary](expression-es8-profile.py), [harness CPU](expression-es8-cpu.py).
