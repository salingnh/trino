# ES8: hybrid native + Painless so với Painless thuần và Trino local

Đo trên VM `172.16.11.138`, ngày 18/11/2026. **Chỉ ES8.11.3**; cùng Trino `current-e10be840714` cho cả ba chế độ. Phạm vi là bốn mẫu hybrid trong bộ 40 SQL hiệu năng trước đó, không phải mọi tổ hợp hàm có thể dịch hybrid. Giữ cùng binary để tách hiệu quả của native guard khỏi khác biệt phiên bản connector.

Kết quả: median hybrid nhanh hơn Painless thuần ở **7/28** điểm SQL/cấu hình; nhanh hơn local ở **15/28** điểm. Với Painless thuần: **0** điểm có cả ba lượt hybrid thấp hơn cả ba lượt script; **14** điểm ngược lại; **14** điểm có khoảng min–max chồng lấp. Đây là quan sát ba lượt, không phải kiểm định thống kê hay p95/p99.

## 1. Đọc ba cột so sánh

| Chế độ | Xử lý ở ES8 | Xử lý ở Trino |
| --- | --- | --- |
| Hybrid | DSL thật do connector sinh: native range kết hợp Painless | Nhận kết quả; riêng `S-MIXED-OR` giữ residual |
| Painless thuần | Toàn bộ điều kiện lọc ứng viên trong một script; không còn native range bên ngoài | Giữ đúng plan, projection, phân trang và residual như hybrid |
| Trino local | `allow_pushdown_into_connectors=false`; lấy đủ document | Tính toàn bộ WHERE bằng Trino |

Painless thuần là **thí nghiệm loại bỏ native tại proxy của lab**, chưa phải session option sản phẩm. Hook chỉ thay `query` của initial search trên tên index riêng `expr_es8_hybrid_20261118_*`, chỉ listener `current/es8`. Cả hybrid và script thuần dùng `bool.filter`; các phần khác của request giữ nguyên. Proxy ghi DSL thực sự đã gửi ES, gồm query gốc khi thay thế. Không sửa/rebuild/redeploy Java.

Ba script số học được xuất trực tiếp từ compiler hiện có: array dùng compact integral compiler, scalar dùng typed compiler. Với OR phải dịch **cả OR** sang script, không được xóa nhánh native. Với AND array, script vẫn kiểm tra hai điều kiện trên **cùng phần tử**; native range là guard theo document. `S-MIXED-OR` dùng cùng script Unicode candidate của connector, thêm nhánh `n>10` bằng Painless; giữ residual ở Trino. Đây là prefilter tương đương trên tập fixture đã kiểm chứng, không tuyên bố một compiler multi-field mới.

## 2. SQL và đường thực thi

| SQL ID | SQL mẫu (`T` là index của từng cấu hình) | Native | Script trong hybrid | Mức thực thi |
| --- | --- | --- | --- | --- |
| `A-I09` | <code>SELECT id FROM T WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | `range numbers > 10` | Modulo; OR ghép bằng `bool.should` | EXACT; không tính lại WHERE tại Trino |
| `A-I10` | <code>SELECT id FROM T WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | `range numbers > 10` | Cả lambda AND; native guard ghép bằng `bool.filter` | EXACT; không tính lại WHERE tại Trino |
| `S-I09` | <code>SELECT id FROM T WHERE n &gt; 10 OR mod(n,2)=0</code> | `range n > 10` | Modulo; OR ghép bằng `bool.should` | EXACT; không tính lại WHERE tại Trino |
| `S-MIXED-OR` | <code>SELECT id FROM T WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | `range n > 10` | `lower(word) LIKE ...` candidate; OR bằng `bool.should` | PREFILTER + residual |

## 3. Kết quả chính: 8 shard / 100.000 document

Wall time là từ gửi SQL tới nhận hết kết quả; đơn vị **ms**, ô ghi `median [min–max]` của ba lượt sau warm-up. Δ = `(hybrid / baseline − 1) × 100%`: âm là hybrid nhanh hơn.

| SQL ID | Hybrid | Painless thuần | Trino local | Δ so script | Δ so local |
| --- | ---: | ---: | ---: | ---: | ---: |
| `A-I09` | 1,169.2 [1,159.8–1,195.3] | 1,156.1 [1,126.8–1,191.5] | 1,212.7 [1,177.2–1,233.5] | +1.1% | -3.6% |
| `A-I10` | 723.6 [717.9–751.1] | 721.7 [711.3–749.4] | 1,199.3 [1,198.0–1,207.5] | +0.3% | -39.7% |
| `S-I09` | 1,091.0 [1,008.6–1,152.8] | 988.2 [978.8–1,005.3] | 1,153.0 [1,117.7–1,200.7] | +10.4% | -5.4% |
| `S-MIXED-OR` | 1,699.1 [1,652.1–1,728.7] | 1,340.4 [1,308.6–1,372.0] | 1,194.0 [1,174.8–1,272.9] | +26.8% | +42.3% |

### Công việc ở ES và dữ liệu về Trino tại cùng cấu hình

CPU ES đọc trực tiếp `cpu.stat usage_usec` của cgroup container quanh SQL, cộng mọi thread và có thể gồm GC/nền; có thể lớn hơn wall time. CPU Trino lấy `statement_stats.cpuTimeMillis`. Không diễn giải `sum ES took` giữa các shard/request thành CPU hoặc wall toàn SQL.

| SQL ID | CPU ES hybrid / script / local (ms) | CPU Trino hybrid / script / local (ms) | Source docs hybrid / script / local | Response hybrid / script / local (MiB) |
| --- | ---: | ---: | ---: | ---: |
| `A-I09` | 3,477.0 / 3,473.7 / 2,855.7 | 167.0 / 170.0 / 422.0 | 61,112 / 61,112 / 100,000 | 7.9 / 7.9 / 14.8 |
| `A-I10` | 2,105.7 / 1,767.5 / 2,925.8 | 94.0 / 93.0 / 361.0 | 33,334 / 33,334 / 100,000 | 4.3 / 4.3 / 14.8 |
| `S-I09` | 3,594.6 / 2,626.3 / 2,875.2 | 143.0 / 151.0 / 319.0 | 50,001 / 50,001 / 100,000 | 6.7 / 6.7 / 14.0 |
| `S-MIXED-OR` | 5,055.3 / 3,690.6 / 2,330.1 | 241.0 / 251.0 / 380.0 | 61,111 / 61,111 / 100,000 | 9.8 / 9.8 / 15.4 |

### Ca chậm nổi bật: OR trộn field, 1 shard / 100.000 document

Median hybrid **13,458.7 ms**, script thuần **4,124.2 ms**, local **2,957.9 ms**. Hybrid chậm hơn script **226.3%** và local **355.0%**. Đây là điểm đáng chú ý khi chỉ tăng document, không tăng shard.

Đối chiếu lượt đo thứ hai sau warm-up (`repetition=2` trong raw). Hai chế độ offload đều nhận cùng 61.111 candidate qua 63 request search/scroll. Chuỗi took là: trang Scroll đầu tiên sau initial search / trang giữa / trang gần cuối còn dữ liệu / trang rỗng kết thúc.

| Chế độ | Query ID | Wall SQL (ms) | CPU ES (ms) | Tổng ES took (ms) | Requests | Took bốn trang (ms) |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| hybrid | `20261118_085001_03613_ifvjz` | 13,458.7 | 12,946.3 | 12,506 | 63 | 54 / 195 / 318 / 300 |
| script | `20261118_084953_03611_ifvjz` | 4,087.2 | 3,488.9 | 3,193 | 63 | 50 / 49 / 9 / 4 |
| local | `20261118_084958_03612_ifvjz` | 2,983.0 | 2,116.4 | 1,708 | 101 | 17 / 18 / 15 / 1 |

Chi phí tăng nằm ở đường ES search/Scroll của hybrid: lượng dữ liệu và số trang giống script thuần, nhưng took trang hybrid tăng về cuối và CPU ES cao hơn rõ. Không thể giải thích chênh lệch này bằng gửi nhiều document hơn script thuần hoặc chỉ bằng residual Trino. Chưa profile nội bộ từng trang để kết luận chính xác iterator/collector Lucene nào gây tăng chi phí.


Hybrid và script thuần nhận **cùng tập candidate**, nên lượng source về Trino gần như nhau. Lợi ích riêng của native được đánh giá bằng số lần gọi script, CPU ES và latency; giảm document so với local là lợi ích chung của cả hai cách offload.

## 4. Profile: native thực sự giảm bao nhiêu lần gọi script?

Profile `size=0, track_total_hits=true` chạy **sau** các lượt tính thời gian của mỗi SQL/cấu hình. Đây là một request đếm trên toàn index, không phải tổng số lần script của tất cả trang Scroll. `ScriptQuery.breakdown.match_count` là số lần kiểm tra document, không phải số phần tử array.

| SQL ID | Shard | Script calls hybrid | Script calls thuần | Giảm | Lớp native trong hybrid |
| --- | ---: | ---: | ---: | ---: | --- |
| `A-I09` | 1 | 50,000 | 100,000 | 50.0% | IndexOrDocValuesQuery |
| `A-I09` | 8 | 50,000 | 100,000 | 50.0% | IndexOrDocValuesQuery |
| `A-I10` | 1 | 50,000 | 100,000 | 50.0% | IndexOrDocValuesQuery |
| `A-I10` | 8 | 50,000 | 100,000 | 50.0% | IndexOrDocValuesQuery |
| `S-I09` | 1 | 72,222 | 100,000 | 27.8% | IndexOrDocValuesQuery |
| `S-I09` | 8 | 72,222 | 100,000 | 27.8% | IndexOrDocValuesQuery |
| `S-MIXED-OR` | 1 | 72,222 | 100,000 | 27.8% | IndexOrDocValuesQuery |
| `S-MIXED-OR` | 8 | 72,222 | 100,000 | 27.8% | IndexOrDocValuesQuery |

Numeric range hiện ra `IndexOrDocValuesQuery`: có đường native, nhưng wrapper này không chứng minh mọi thao tác range luôn dùng point index. Counter ở trên chứng minh lượng script giảm trong request đã profile; không suy mọi OR/hybrid đều giảm cùng tỷ lệ. Script OR thuần cũng có thể short-circuit bên trong script; ít `match_count` hơn không đồng nghĩa giảm tương ứng mọi phép tính hay wall time. Hybrid còn có chi phí dựng/ghép iterator native + script; khi source fetch/Scroll hoặc residual chiếm phần lớn, phần tiết kiệm script có thể nhỏ.

## 5. Toàn bộ ma trận shard/document

Bảy cấu hình duy nhất: tăng shard `(1,10k)→(4,10k)→(8,10k)`; tăng document `(1,10k)→(1,50k)→(1,100k)`; tăng cả hai `(1,10k)→(4,50k)→(8,100k)`. Các hàng dùng SQL đầy đủ và median [min–max] ms.

| SQL thực thi | Shard / docs | Hybrid | Painless thuần | Trino local | Δ script | Độ tách khoảng so script |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 1 / 10,000 | 478.0 [475.6–520.5] | 467.0 [466.7–472.9] | 436.6 [428.1–444.6] | +2.4% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n50000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 1 / 50,000 | 1,961.3 [1,920.3–1,975.3] | 1,748.2 [1,714.2–1,811.0] | 1,588.2 [1,575.9–1,604.9] | +12.2% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 1 / 100,000 | 4,085.0 [4,042.5–4,144.0] | 3,296.2 [3,287.3–3,413.7] | 2,942.5 [2,915.2–2,956.7] | +23.9% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 4 / 10,000 | 215.8 [212.1–262.6] | 209.8 [205.8–212.0] | 222.9 [218.7–229.3] | +2.9% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n50000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 4 / 50,000 | 593.9 [591.3–596.6] | 582.2 [574.8–593.5] | 599.0 [588.6–628.8] | +2.0% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 8 / 10,000 | 293.1 [292.6–325.5] | 303.8 [290.9–304.4] | 321.0 [309.6–322.5] | -3.5% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 OR mod(x,2)=0)</code> | 8 / 100,000 | 1,169.2 [1,159.8–1,195.3] | 1,156.1 [1,126.8–1,191.5] | 1,212.7 [1,177.2–1,233.5] | +1.1% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 1 / 10,000 | 333.1 [321.0–339.4] | 337.7 [333.6–337.8] | 454.8 [437.1–489.0] | -1.4% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n50000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 1 / 50,000 | 1,026.2 [1,013.9–1,077.0] | 1,053.0 [1,050.2–1,058.6] | 1,566.6 [1,563.1–1,595.3] | -2.5% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 1 / 100,000 | 1,881.4 [1,854.8–1,903.8] | 1,889.5 [1,858.9–1,899.9] | 2,887.8 [2,879.1–2,954.1] | -0.4% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 4 / 10,000 | 167.3 [165.8–169.7] | 167.1 [167.0–172.3] | 227.8 [217.0–233.5] | +0.1% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n50000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 4 / 50,000 | 377.0 [373.8–388.5] | 373.3 [371.1–382.5] | 625.0 [577.3–632.3] | +1.0% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n10000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 8 / 10,000 | 262.6 [261.2–302.2] | 267.0 [260.5–271.7] | 314.2 [312.3–315.3] | -1.7% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n100000_cases WHERE any_match(numbers, x -&gt; x &gt; 10 AND mod(x,2)=0)</code> | 8 / 100,000 | 723.6 [717.9–751.1] | 721.7 [711.3–749.4] | 1,199.3 [1,198.0–1,207.5] | +0.3% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n10000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 1 / 10,000 | 499.8 [486.9–524.4] | 472.8 [469.5–482.7] | 424.4 [422.5–498.5] | +5.7% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n50000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 1 / 50,000 | 1,916.9 [1,815.2–1,921.9] | 1,532.3 [1,502.1–1,540.7] | 1,524.0 [1,475.7–1,622.4] | +25.1% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n100000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 1 / 100,000 | 4,339.4 [4,298.0–4,464.8] | 2,931.7 [2,914.5–2,951.5] | 2,798.2 [2,791.4–2,843.8] | +48.0% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n10000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 4 / 10,000 | 210.8 [207.6–213.0] | 211.9 [207.1–212.6] | 225.7 [211.0–225.7] | -0.5% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n50000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 4 / 50,000 | 551.9 [544.9–560.4] | 518.2 [507.1–520.1] | 587.1 [561.9–594.5] | +6.5% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n10000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 8 / 10,000 | 287.9 [281.9–325.1] | 289.2 [289.0–293.6] | 299.0 [298.7–302.7] | -0.4% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n100000_cases WHERE n &gt; 10 OR mod(n,2)=0</code> | 8 / 100,000 | 1,091.0 [1,008.6–1,152.8] | 988.2 [978.8–1,005.3] | 1,153.0 [1,117.7–1,200.7] | +10.4% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n10000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 1 / 10,000 | 719.6 [701.1–736.0] | 573.6 [567.5–613.4] | 444.1 [442.4–445.8] | +25.5% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n50000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 1 / 50,000 | 4,516.5 [4,489.5–4,655.1] | 2,178.5 [2,153.0–2,247.2] | 1,603.5 [1,594.5–1,720.3] | +107.3% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s1_n100000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 1 / 100,000 | 13,458.7 [13,341.4–13,550.1] | 4,124.2 [4,087.2–4,188.4] | 2,957.9 [2,954.5–2,983.0] | +226.3% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n10000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 4 / 10,000 | 262.4 [256.3–294.9] | 246.8 [244.8–275.7] | 219.4 [219.0–221.1] | +6.3% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s4_n50000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 4 / 50,000 | 881.8 [875.9–924.3] | 733.3 [726.5–751.8] | 632.8 [625.4–677.9] | +20.3% | hybrid chậm hơn cả 3 lượt |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n10000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 8 / 10,000 | 339.9 [333.5–342.3] | 337.9 [330.2–340.4] | 320.1 [304.5–350.6] | +0.6% | khoảng đo chồng lấp |
| <code>SELECT id FROM expr_es8_hybrid_20261118_s8_n100000_cases WHERE n&gt;10 OR lower(word) LIKE &#x27;%sá%&#x27;</code> | 8 / 100,000 | 1,699.1 [1,652.1–1,728.7] | 1,340.4 [1,308.6–1,372.0] | 1,194.0 [1,174.8–1,272.9] | +26.8% | hybrid chậm hơn cả 3 lượt |

## 6. Điều kiện đo, bằng chứng và giới hạn

- 4 mẫu hybrid × 7 cấu hình × 3 chế độ × (1 warm-up + 3 đo) = **336 SQL**, gồm **252 lượt đo + 84 warm-up**. Có thêm 56 EXPLAIN, 4 SQL local reference và 16 profile; không đưa chúng vào latency benchmark. Smoke 12 SQL và các lượt kiểm tra harness chưa hoàn tất giữ riêng, không trộn vào 336 SQL này.
- Mỗi SQL chạy tuần tự; xoay thứ tự ba chế độ để mỗi chế độ đứng đầu/giữa/cuối đúng một lần trong ba lượt đo. Không phát tải đồng thời nhiều SQL.
- Tất cả 336 SQL đúng theo reference local trên fixture gốc; kiểm tra cả tập ID và fingerprint, không chỉ số dòng. Cả 28 cặp hybrid/script có cùng tập ID candidate ở mọi lần chạy, kể cả `S-MIXED-OR` có residual.
- Dùng cùng ES8 một node, replica 0, request cache tắt trên index benchmark. Trino current 2 CPU/7 GiB, heap 4 GiB; ES8 container 3 GiB, không đặt quota CPU Docker. Cache/JIT/script đã warm; không phải phép đo cold start. Latency gồm recorder proxy và HTTP/Scroll của lab, không phải kết nối trực tiếp production.
- Nạp dữ liệu lặp từ 18 document biên, tăng tới 100.000; độ chọn lọc và cardinality gần như cố định. Chưa đại diện dữ liệu production, array dài, skew shard hay nhiều truy vấn đồng thời. Tăng shard vẫn trên cùng một node.
- Không có assertion hybrid phải nhanh hơn: các ca chậm và khoảng chồng lấp vẫn giữ trong bảng/CSV. Ba lượt không đủ kết luận chênh nhỏ là ổn định.
- Kết thúc: proxy khôi phục đúng bytes/hash gốc; xóa toàn bộ index tạm của lượt đo; fixture gốc (mapping/analysis/giá trị) không đổi; image, PID và tài nguyên ES8/Trino không đổi. Trong lượt benchmark, không chạy ES7 hay thay đổi mã Java.

Trong CSV, `search_requests` chỉ đếm search/scroll có `took`, không tính clear-scroll; raw events vẫn giữ đầy đủ request cleanup.

Bằng chứng: [CSV đầy đủ CPU/latency/source/bytes/SQL](expression-es8-hybrid.csv), [raw kết quả + DSL + plan + profile + source harness](expression-es8-hybrid.json.gz). Harness: [benchmark](expression-hybrid-benchmark.py), [proxy lab](expression-hybrid-proxy.py), [script thuần](expression-hybrid-scripts.json), [exporter compiler](HybridScriptExporter.java.txt), [bộ sinh báo cáo](expression-hybrid-report.py).

SHA-256 raw archive: `0df1c1e9078f049187968c9dd233c05ba3ac7835b545f1d852b9153b5dc62ca9`.

Tái lập: đặt các file benchmark/proxy/scripts/exporter cùng thư mục lab và dùng đúng `expression-compare.py` và `expression-performance.py` trong `raw.sources`. `raw.benchmark_input` chứa features/templates để tạo input gzip riêng khi tái lập; input gốc đầy đủ vẫn là [expression-es8-audit-input.json.gz](expression-es8-audit-input.json.gz). Các bản source đã chạy trong raw được giữ nguyên; bản repo có bổ sung license header. Helper performance trong raw khác bản workspace hiện mặc định ES8; harness hybrid luôn truyền ES8 rõ ràng. Chạy `python3 expression-hybrid-benchmark.py` trên VM. Harness từ chối ghi đè thư mục bằng chứng đã tồn tại; dùng một lab output mới được đổi tên rõ ràng khi tái lập. Chỉ namespace index riêng được tạo/xóa. Khi dừng bằng SIGTERM/SIGINT, `finally` dọn index và khôi phục proxy; nếu tiến trình bị SIGKILL hoặc VM mất điện, khôi phục bytes `proxy-original.py` bằng ghi in-place vào `expression-proxy.py`, rồi restart container proxy của lab.
