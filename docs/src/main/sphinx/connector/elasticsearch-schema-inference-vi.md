# Connector Elasticsearch

```{raw} html
<img src="../_static/img/elasticsearch.png" class="connector-logo">
```

[English](elasticsearch.md)

Connector Elasticsearch cho phép Trino truy cập dữ liệu
[Elasticsearch](https://www.elastic.co/products/elasticsearch). Bạn có thể cấu
hình một catalog và dùng SQL để truy vấn dữ liệu Elasticsearch.

## Yêu cầu

- Elasticsearch 7.x hoặc 8.x.
- Coordinator và các worker của Trino cần kết nối được tới các node Elasticsearch.

## Cấu hình

Tạo tệp cấu hình catalog `etc/catalog/example.properties` theo mẫu dưới đây.
Thay các giá trị cho phù hợp với môi trường của bạn.

```text
connector.name=elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.default-schema-name=default
```

Bảng sau liệt kê các thuộc tính cấu hình chung.

| Thuộc tính | Mô tả | Mặc định |
| --- | --- | --- |
| `elasticsearch.host` | Danh sách tên máy chủ Elasticsearch, phân tách bằng dấu phẩy. Bắt buộc. |  |
| `elasticsearch.port` | Cổng kết nối tới Elasticsearch. | `9200` |
| `elasticsearch.default-schema-name` | Schema chứa các bảng khi tên bảng không chỉ rõ schema. | `default` |
| `elasticsearch.scroll-size` | Số kết quả tối đa mà mỗi [yêu cầu scroll của Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#scroll-search-context) có thể trả về. | `1000` |
| `elasticsearch.search-strategy` | Chiến lược phân trang. Chọn `SCROLL` hoặc bật `PIT`. PIT yêu cầu Elasticsearch 7.17 hoặc 8.x và quyền mở, đóng ngữ cảnh point in time. | `SCROLL` |
| `elasticsearch.response-decoder` | Bộ giải mã kết quả tìm kiếm. Chọn `MATERIALIZED` hoặc bật `STREAMING`. Streaming nạp từng kết quả một, độc lập với chiến lược phân trang. | `MATERIALIZED` |
| `elasticsearch.scroll-timeout` | [Thời gian](prop-type-duration) Elasticsearch giữ ngữ cảnh tìm kiếm cho các yêu cầu scroll. | `1m` |
| `elasticsearch.request-timeout` | [Thời gian chờ](prop-type-duration) cho các yêu cầu tới Elasticsearch. | `10s` |
| `elasticsearch.connect-timeout` | [Thời gian chờ](prop-type-duration) cho các lần kết nối tới Elasticsearch. | `1s` |
| `elasticsearch.backoff-init-delay` | [Khoảng chờ](prop-type-duration) tối thiểu giữa các lần thử lại một yêu cầu khi Elasticsearch quá tải. Giá trị quá thấp có thể làm cụm quá tải thêm. | `500ms` |
| `elasticsearch.backoff-max-delay` | [Khoảng chờ](prop-type-duration) tối đa giữa các lần thử lại một yêu cầu khi Elasticsearch quá tải. | `20s` |
| `elasticsearch.max-retry-time` | [Tổng thời gian](prop-type-duration) tối đa để thử lại một yêu cầu. | `30s` |
| `elasticsearch.node-refresh-interval` | [Khoảng thời gian](prop-type-duration) giữa các lần cập nhật danh sách node. | `1m` |
| `elasticsearch.schema-inference.enabled` | Bật [suy luận schema](#elasticsearch-schema-inference-vi) cho index thiếu metadata của Trino. | `true` |
| `elasticsearch.statistics.enabled` | Thu thập thống kê qua phép tổng hợp của Elasticsearch, gồm số document, số giá trị phân biệt, tỷ lệ NULL và khoảng giá trị nhỏ nhất đến lớn nhất. Bộ tối ưu theo chi phí dùng thống kê để lập kế hoạch cho phép nối và các phép toán khác. | `true` |
| `elasticsearch.statistics.max-index-documents` | Chỉ thu thập thống kê từng cột khi index có tối đa số document này. Với index lớn hơn, connector chỉ lấy số dòng để tránh phép tổng hợp trên từng document có thể hết thời gian chờ trong lúc lập kế hoạch. | `20000000` |
| `elasticsearch.statistics.max-statistics-columns` | Số cột tối đa được thu thập thống kê. Connector chỉ xét các cột được tham chiếu trong điều kiện lọc, `GROUP BY` hoặc sắp xếp. | `32` |
| `elasticsearch.statistics.request-timeout` | Thời gian chờ socket cho một yêu cầu thống kê cột. Khi phép tổng hợp quá chậm, connector dùng số dòng đã có để tiếp tục lập kế hoạch và bỏ qua lần thử lại. | `5s` |
| `elasticsearch.dynamic-filtering.wait-timeout` | [Thời gian chờ](prop-type-duration) tối đa để thu thập bộ lọc động, ví dụ từ khóa nối, trước khi tạo split. Bộ lọc được đưa vào truy vấn Elasticsearch để giảm số document cần quét. Có thể ghi đè theo truy vấn bằng thuộc tính phiên `dynamic_filtering_wait_timeout`. | `5s` |
| `elasticsearch.keyword-subfield-pushdown-with-ignore-above` | Cho phép đẩy điều kiện lọc và tổng hợp của trường `text` xuống trường con `keyword` dùng để khớp chính xác, kể cả khi trường con có `ignore_above`. Mặc định tắt vì các giá trị dài bị loại khỏi chỉ mục của trường con và điều kiện được đẩy xuống có thể trả thiếu kết quả. Chỉ bật khi mọi chuỗi được lập chỉ mục đều ngắn hơn giới hạn `ignore_above`. Có thể ghi đè theo truy vấn bằng thuộc tính phiên `keyword_subfield_pushdown_with_ignore_above`. | `false` |
| `elasticsearch.aggregation-page-size` | Số nhóm tối đa trong mỗi phản hồi của phép tổng hợp composite. Giá trị tối thiểu là 1. Trang lớn giảm số yêu cầu và có thể tốn thêm bộ nhớ cho mỗi phản hồi. | `10000` |
| `elasticsearch.aggregation-pushdown.enabled` | Bật pushdown phép tổng hợp. Có thể ghi đè theo truy vấn bằng thuộc tính phiên `aggregation_pushdown_enabled`. | `true` |
| `elasticsearch.scalar-script-pushdown.enabled` | Dùng Painless để đẩy xuống các biểu thức vô hướng được hỗ trợ trên trường có doc values và giữ đúng ngữ nghĩa của `_source`. Có thể ghi đè bằng thuộc tính phiên `scalar_script_pushdown_enabled`. | `false` |
| `elasticsearch.full-text-pushdown` | Đẩy điều kiện lọc và bộ lọc động trên trường `text` đã phân tích xuống Elasticsearch dưới dạng truy vấn toàn văn. Chọn `DISABLED`, `SAFE` hoặc `UNSAFE`. Elasticsearch tách `text` thành token nên kết quả có thể khác ngữ nghĩa SQL. Ghi đè theo truy vấn bằng `full_text_pushdown_mode`. | `DISABLED` |
| `elasticsearch.ignore-publish-address` | Bỏ qua địa chỉ do API Elasticsearch công bố khi kết nối để truy vấn. Tùy chọn này hữu ích khi cổng Elasticsearch được ánh xạ sang một cổng công khai được cấp ngẫu nhiên. | `false` |

### Xác thực

Connector hỗ trợ xác thực AWS và xác thực bằng mật khẩu.

Để xác thực qua AWS và phân quyền theo chính sách IAM, đặt
`elasticsearch.security` thành `AWS` rồi cấu hình các tùy chọn sau.

| Thuộc tính | Mô tả |
| --- | --- |
| `elasticsearch.aws.region` | Vùng AWS của endpoint Elasticsearch. Bắt buộc. |
| `elasticsearch.aws.access-key` | Access key AWS để kết nối tới domain Elasticsearch. Khi bỏ trống, connector tìm thông tin xác thực theo chuỗi nhà cung cấp mặc định của AWS. |
| `elasticsearch.aws.secret-key` | Secret key AWS để kết nối tới domain Elasticsearch. Khi bỏ trống, connector tìm thông tin xác thực theo chuỗi nhà cung cấp mặc định của AWS. |
| `elasticsearch.aws.iam-role` | ARN của vai trò IAM mà connector có thể đảm nhận để kết nối. Người dùng IAM đã cấu hình phải có quyền đảm nhận vai trò đó. |
| `elasticsearch.aws.external-id` | ID bên ngoài tùy chọn để gửi khi đảm nhận vai trò IAM. |

Để xác thực bằng mật khẩu, đặt `elasticsearch.security` thành `PASSWORD` rồi
cung cấp các thuộc tính sau.

| Thuộc tính | Mô tả |
| --- | --- |
| `elasticsearch.auth.user` | Tên người dùng để kết nối tới Elasticsearch. |
| `elasticsearch.auth.password` | Mật khẩu để kết nối tới Elasticsearch. |

### Bảo mật kết nối bằng TLS

Connector hỗ trợ các tùy chọn TLS khi kết nối tới cụm Elasticsearch.

Nếu chứng chỉ của cụm do tổ chức được tin cậy cấp, bạn chỉ cần bật TLS. Nếu
cần cấu hình chứng chỉ riêng, connector hỗ trợ kho khóa và kho chứng chỉ tin
cậy ở định dạng P12 (PKCS) hoặc Java Key Store (JKS).

| Thuộc tính | Mô tả |
| --- | --- |
| `elasticsearch.tls.enabled` | Bật TLS. |
| `elasticsearch.tls.keystore-path` | Đường dẫn đến kho khóa P12 (PKCS) hoặc [JKS](/security/inspect-jks). |
| `elasticsearch.tls.truststore-path` | Đường dẫn đến kho chứng chỉ tin cậy P12 (PKCS) hoặc [JKS](/security/inspect-jks). |
| `elasticsearch.tls.keystore-password` | Mật khẩu khóa trong kho được chỉ định bởi `elasticsearch.tls.keystore-path`. |
| `elasticsearch.tls.truststore-password` | Mật khẩu khóa trong kho được chỉ định bởi `elasticsearch.tls.truststore-path`. |
| `elasticsearch.tls.verify-hostnames` | Xác minh tên máy chủ trong chứng chỉ. Mặc định là `true`. |

(elasticsearch-type-mapping-vi)=
## Ánh xạ kiểu dữ liệu

Trino và Elasticsearch hỗ trợ các kiểu khác nhau. Khi đọc dữ liệu, connector
dùng {ref}`quy tắc ánh xạ kiểu <type-mapping-overview>`.

### Kiểu Elasticsearch tương ứng với kiểu Trino

| Kiểu Elasticsearch | Kiểu Trino | Ghi chú |
| --- | --- | --- |
| `BOOLEAN` | `BOOLEAN` |  |
| `DOUBLE` | `DOUBLE` |  |
| `FLOAT` | `REAL` |  |
| `BYTE` | `TINYINT` |  |
| `SHORT` | `SMALLINT` |  |
| `INTEGER` | `INTEGER` |  |
| `LONG` | `BIGINT` |  |
| `KEYWORD` | `VARCHAR` |  |
| `TEXT` | `VARCHAR` |  |
| `DATE` | `TIMESTAMP` | Xem [Kiểu ngày giờ](#elasticsearch-date-types-vi). |
| `IPADDRESS` | `IP` |  |

Bảng liệt kê toàn bộ các kiểu có ánh xạ trực tiếp trong mục này.

(elasticsearch-array-types-vi)=
### Kiểu mảng

Một trường Elasticsearch có thể chứa [không hoặc nhiều giá trị](https://www.elastic.co/guide/en/elasticsearch/reference/current/array.html).
Khai báo mảng cho Trino bằng cấu hình riêng trong phần
[`_meta`](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-meta-field.html)
của mapping.

Ví dụ dưới đây minh họa một document có mảng ở cấp ngoài và mảng trong object.

```json
{
    "array_string_field": ["trino", "the", "lean", "machine-ohs"],
    "long_field": 314159265359,
    "id_field": "564e6982-88ee-4498-aa98-df9e3f6b6109",
    "timestamp_field": "2025-09-17T06:22:48.000Z",
    "object_field": {
        "array_int_field": [86, 75, 309],
        "int_field": 2
    }
}
```

Để khai báo rõ hai trường mảng, thêm các thiết lập sau vào `_meta.trino` trong
mapping của index `doc` hiện có. Khi cập nhật `_meta`, hãy gửi kèm các mục
metadata cần giữ lại.

```shell
curl --request PUT \
    --url http://search.example.com:9200/doc/_mapping \
    --header 'Content-Type: application/json' \
    --data-binary @- <<'JSON'
{
    "_meta": {
        "trino": {
            "array_string_field": {
                "isArray": true
            },
            "object_field": {
                "array_int_field": {
                    "isArray": true
                }
            }
        }
    }
}
JSON
```

**Lưu ý**

Mỗi cột chỉ có thể bật một trong hai cờ `asRawJson` và `isArray`.

(elasticsearch-date-types-vi)=
### Kiểu ngày giờ

Connector hỗ trợ `date` với định dạng mặc định. Các trường [ngày][date] có
thuộc tính [`format`][format] bị bỏ qua, kể cả khi trường đó dùng một định dạng
[có sẵn][built-in date formats] hoặc [tùy chỉnh][custom date formats].

(elasticsearch-raw-json-transform-vi)=
### Đọc dưới dạng JSON thô

Document Elasticsearch có thể chứa cấu trúc phức tạp mà mapping không mô
tả hết. Ví dụ cùng một trường `keyword` có thể chứa một chuỗi, một mảng
hoặc mảng nhiều chiều.

Lệnh sau khai báo `array_string_field` trong mapping của index `doc`.
Thay `search.example.com:9200` bằng địa chỉ Elasticsearch của bạn.

```shell
curl --request PUT \
    --url http://search.example.com:9200/doc/_mapping \
    --header 'Content-Type: application/json' \
    --data-binary @- <<'JSON'
{
    "properties": {
        "array_string_field": {
            "type": "keyword"
        }
    }
}
JSON
```

Mapping trên chấp nhận tất cả các document dưới đây.

```json
[
    {
        "array_string_field": "trino"
    },
    {
        "array_string_field": ["trino", "is", "the", "best"]
    },
    {
        "array_string_field": ["trino", ["is", "the", "best"]]
    },
    {
        "array_string_field": ["trino", ["is", ["the", "best"]]]
    }
]
```

Xem thêm các dạng mảng mà Elasticsearch chấp nhận.
[Tài liệu mảng Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/array.html)

Đặt `asRawJson: true` trong `_meta.trino` để đọc cả trường và các trường con
dưới dạng `VARCHAR` chứa JSON thô. Cách này phù hợp với dữ liệu có nhiều
hình dạng và các kiểu Elasticsearch chưa có ánh xạ trực tiếp sang Trino,
chẳng hạn [`dense_vector`](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html).

Lệnh sau đặt `asRawJson` cho `array_string_field` trong index `doc` hiện có.
Khi cập nhật `_meta`, hãy gửi kèm các mục metadata cần giữ lại.

```shell
curl --request PUT \
    --url http://search.example.com:9200/doc/_mapping \
    --header 'Content-Type: application/json' \
    --data-binary @- <<'JSON'
{
    "_meta": {
        "trino": {
            "array_string_field": {
                "asRawJson": true
            }
        }
    }
}
JSON
```

Sau cập nhật, Trino trả `array_string_field` dưới dạng `VARCHAR` chứa JSON
thô. Bạn có thể đọc giá trị bằng [các hàm JSON có sẵn](/functions/json).

**Lưu ý**

Mỗi cột chỉ có thể bật một trong hai cờ `asRawJson` và `isArray`.

(elasticsearch-schema-inference-vi)=
### Suy luận schema

Suy luận schema bật mặc định với index thiếu cả `_meta.trino` lẫn
`_meta.presto` cũ. Khi có `_meta.trino`, connector bỏ qua bước lấy mẫu cho
index và dùng thiết lập đã khai báo cùng các giá trị mặc định. Thêm dòng sau
vào tệp cấu hình catalog để tắt suy luận.

```properties
elasticsearch.schema-inference.enabled=false
```

#### Cách lấy mẫu

1. Khi catalog khởi tạo, coordinator đọc mapping để xác định các trường
   của index rồi lấy tối đa ba document.
2. Với mỗi trường trong mapping vẫn thiếu giá trị mẫu và có thể tìm kiếm,
   coordinator dùng truy vấn `exists` để lấy thêm tối đa ba document. Mỗi
   document có thể cung cấp mẫu cho nhiều trường.
3. Connector chọn kiểu Trino từ các giá trị trong `_source` rồi lưu schema vào
   bộ nhớ đệm. Trino giữ cùng một schema trong suốt truy vấn.

Truy vấn `exists` kiểm tra giá trị đã được lập chỉ mục. Một trường có thể
xuất hiện trong `_source` mà vẫn thiếu giá trị trong chỉ mục, chẳng hạn khi
giá trị vượt ngưỡng `ignore_above`. [Tài liệu truy vấn `exists` của Elasticsearch](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-exists-query)

#### Kiểu Trino tương ứng với giá trị mẫu

Connector xét từng trường trong `_source`, kể cả trường con trong object.
Số chiều mảng và kiểu của giá trị bên trong được xét riêng. Các kiểu giá
trị được đếm gồm chuỗi, số nguyên, số dấu phẩy động, boolean và object.
Trường bị thiếu và giá trị `null` được bỏ qua khi đếm. Mảng rỗng hoặc
chỉ chứa `null` vẫn cho biết số lớp mảng quan sát được. Nếu chưa gặp phần
tử khác `null`, connector có thể dùng mapping được hỗ trợ để chọn kiểu
phần tử.

Trong các bảng dưới đây, **JSON thô** là kiểu `VARCHAR` chứa JSON của
toàn bộ trường bị xung đột. Ví dụ, chuỗi `"Hà Nội"` giữ dấu nháy của JSON,
còn mảng giữ dấu ngoặc vuông. Khi các kiểu được gộp thành `VARCHAR`
thông thường, connector trả giá trị dưới dạng chuỗi và không thêm dấu
nháy JSON.

##### Xung đột kiểu giá trị trong cùng một index

Với trường đơn cấp cao nhất có mapping số, boolean hoặc `date` được hỗ trợ
và có doc values, connector ưu tiên kiểu trong mapping. Ví dụ, Elasticsearch
có thể lập chỉ mục `"02"` thành số `2` cho trường `long`. Trino đọc giá trị
đã lập chỉ mục thành `BIGINT` và vẫn kiểm tra hình dạng trong `_source`.
Elasticsearch bỏ qua giá trị không hợp lệ khi bật `ignore_malformed`. Với
trường đã chọn chế độ này, Trino đọc giá trị bị bỏ qua thành SQL `NULL`.
Cột `keyword` chứa lẫn số hoặc boolean cũng lấy chuỗi từ doc values khi
mapping hỗ trợ.

Các quy tắc gộp sau áp dụng cho phần tử mảng và khi connector không dùng
kiểu từ mapping. Số chiều mảng phải nhất quán. Nếu gộp được
kiểu của phần tử, connector giữ nguyên số chiều. Ví dụ, `[1]` và `["02"]`
cho kiểu `ARRAY(VARCHAR)` với các giá trị `["1"]` và `["02"]`.

| Kiểu giá trị quan sát được | Ví dụ | Kiểu Trino suy luận |
| --- | --- | --- |
| Số nguyên và chuỗi | `1` và `"02"` | `VARCHAR`. Số nguyên thành chuỗi `"1"` và `"02"` giữ số 0 ở đầu. |
| Số dấu phẩy động và chuỗi | `1.5` và `"2.5"` | `VARCHAR`. Giá trị số được chuyển thành chuỗi. |
| Boolean và chuỗi | `true` và `"false"` | `VARCHAR`. Giá trị boolean thành chuỗi `"true"`. |
| Số nguyên và số dấu phẩy động | `1` và `2.5` | `DOUBLE` nếu biểu diễn chính xác được mọi số nguyên trong mẫu. Nếu mất độ chính xác, dùng JSON thô. |
| Số nguyên và boolean | `1` và `true` | JSON thô. |
| Số dấu phẩy động và boolean | `1.5` và `true` | JSON thô. |
| Object và một giá trị nguyên thủy | `{"name": "An"}` và `"An"` | JSON thô. |
| Từ ba kiểu giá trị trở lên | `1`, `2.5` và `"3"` | JSON thô, kể cả khi có thể gộp từng cặp riêng lẻ. |

Mapping `boolean` chấp nhận boolean JSON và các dạng chuỗi Elasticsearch
hỗ trợ, kể cả chuỗi rỗng biểu thị false. Khi có doc values, connector đọc
boolean đã lập chỉ mục. Với `date` có doc values, connector đọc
`TIMESTAMP(3)` từ epoch mili giây theo UTC, kể cả khi mapping đặt định dạng
tùy chỉnh như `yyyy/MM/dd HH:mm:ss||epoch_second`.
Các trường suy luận từ `float`, `half_float` và `scaled_float` dùng
`DOUBLE` theo độ chính xác của doc values. Ví dụ, `scaled_float` với hệ số
100 đọc thành 2,34 từ giá trị đầu vào 2,341.

Khi chỉ thấy một kiểu giá trị, connector giữ kiểu từ mapping nếu tương
thích và được hỗ trợ. Nếu mapping của trường nguyên thủy không tương
thích hoặc chưa được hỗ trợ, connector có thể suy luận `VARCHAR` cho
chuỗi, `BIGINT` cho số nguyên vừa phạm vi, `DOUBLE` cho số dấu phẩy động
hoặc `BOOLEAN` cho boolean. Các trường hợp còn lại dùng JSON thô.

##### Xung đột hình dạng dữ liệu

| Hình dạng quan sát được | Ví dụ | Kiểu Trino suy luận |
| --- | --- | --- |
| Giá trị nguyên thủy và mảng | `"Hà Nội"` và `["Hà Nội", "Huế"]` | JSON thô cho cả trường. |
| Object đơn và mảng object | `{"name": "An"}` và `[{"name": "Bình"}]` | JSON thô cho cả trường. |
| Số chiều khác nhau giữa các document hoặc trong cùng một mảng | `["29"]` và `[["30"]]`, hoặc `["29", ["30"]]` | JSON thô cho cả trường. |
| Mảng có số chiều nhất quán | `[["29", "30"]]` và `[["31"]]` | Giữ số chiều mảng. Ví dụ này cho `ARRAY(ARRAY(VARCHAR))`. |
| Các object ở cùng độ sâu có trường con trong mapping | `{"name": "An"}` và `{"age": 28}` với mapping object có cả hai trường con | Suy luận riêng từng trường con. Object thành `ROW` và được bọc `ARRAY` theo số chiều quan sát. Trường con bị thiếu trả SQL `NULL`. |
| Chưa xác định được hình dạng hoặc mapping object thiếu trường con | Chỉ gặp giá trị thiếu hoặc `null`, hoặc object rỗng có mapping thiếu trường con | JSON thô cho cả trường. |

JSON thô chỉ áp dụng tại trường bị xung đột. Nếu `users` là mảng object
và chỉ `users.city` có cả chuỗi lẫn mảng, `users` vẫn là
`ARRAY(ROW(...))`. Trường `city` bên trong `ROW` dùng JSON thô.

Các ví dụ mô tả giá trị connector có thể gặp trong `_source`. Mapping
Elasticsearch có thể từ chối một số tổ hợp, chẳng hạn object và giá
trị nguyên thủy trong cùng một trường đã được mapping.

##### Xung đột giữa các index

Khi truy vấn alias hoặc wildcard có ít nhất một index thành viên cần suy
luận, connector suy luận riêng các index đủ điều kiện và gộp kết quả với
schema khai báo của các index khác. Trường chỉ xuất hiện trong một
schema giữ nguyên kiểu. Trường xuất hiện trong nhiều schema được gộp
theo các quy tắc sau.

- Định nghĩa trường giống hệt nhau giữ nguyên kiểu. Nếu định nghĩa khác
  nhau, dùng JSON thô khi một định nghĩa đã dùng JSON thô, số chiều mảng
  khác nhau hoặc tổng số kiểu giá trị vượt quá hai.
- Các trường object ở cùng độ sâu được gộp đệ quy theo từng trường con.
  Với schema khai báo, kiểu dùng để gộp lấy từ mapping.
- Các trường có mapping nguyên thủy có thể gộp chuỗi với số nguyên, số
  dấu phẩy động hoặc boolean thành `VARCHAR`. Số nguyên và số dấu phẩy
  động có thể gộp thành `DOUBLE` khi phần suy luận đã xác nhận mọi số
  nguyên mẫu biểu diễn được chính xác ở từng index có trường đó.
- Với các trường hợp còn lại, giữ kiểu nếu kiểu từ mapping và phần ghi
  đè kiểu suy luận đều trùng nhau. Nếu còn xung đột thì dùng JSON thô.

Dùng lệnh sau để xem schema suy luận. Thay `elasticsearch` và `events` bằng
tên catalog và index của bạn.

```sql
DESCRIBE elasticsearch.default.events;
```

#### Ảnh hưởng đến pushdown

Các cột kiểu đơn giữ kiểu theo mapping có thể đẩy bộ lọc xuống ES theo
quy tắc thông thường của connector. Ví dụ, `age > 1` trên một trường
`long` dùng giá trị số đã lập chỉ mục. Mapping có `index: false`, kiểu
không nhất quán giữa các index hoặc trường đã chuyển sang JSON thô vẫn
được lọc ở Trino. Các giới hạn an toàn hiện có, như `ignore_above` và
`normalizer` trên trường chuỗi, vẫn áp dụng. Bộ lọc ngày dùng truy vấn
`range` với `format: epoch_millis`. Với `float`, `half_float` và
`scaled_float` được suy luận, connector điều chỉnh biên của truy vấn
`range` theo độ chính xác đã lập chỉ mục khi chuyển đổi được chính xác.
[Tài liệu ép kiểu của Elasticsearch](https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/coerce)

#### Xử lý lỗi lệch kiểu

Document ngoài mẫu có thể gây lỗi `TYPE_MISMATCH` khi Trino giải mã. Nếu
Elasticsearch thực thi toàn bộ truy vấn thì Trino bỏ qua bước giải mã đó.
Thông báo lỗi chứa tên
index, ID document và thông tin về trường bị lệch kiểu. Dùng `_meta.trino` để
khai báo cách Trino đọc trường này. Đặt `isArray` cho kiểu mảng được hỗ trợ
hoặc dùng `asRawJson` cho trường có nhiều hình dạng. Xem
[Kiểu mảng](#elasticsearch-array-types-vi) và [Đọc dưới dạng JSON thô](#elasticsearch-raw-json-transform-vi).

Ví dụ, gửi JSON sau vào `PUT /events/_mapping` để đọc `users` dưới dạng
mảng object và giữ hình dạng của `users.city` dưới dạng JSON thô. Giữ lại
các mục `_meta` hiện có mà index vẫn cần.

```json
{
  "_meta": {
    "trino": {
      "users": {
        "isArray": true,
        "city": {
          "asRawJson": true
        }
      }
    }
  }
}
```

Trong ví dụ này, `isArray` áp dụng cho `users` và `asRawJson` áp dụng cho
`users.city`.

## Cột đặc biệt

Connector cung cấp các cột ẩn sau.

| Cột | Mô tả |
| --- | --- |
| `_id` | ID document trong Elasticsearch. |
| `_score` | Điểm của document do truy vấn Elasticsearch trả về. |
| `_source` | Nội dung nguồn của document. |


(elasticsearch-full-text-queries-vi)=
## Truy vấn toàn văn

Để kết hợp SQL Trino với [truy vấn toàn văn][full text query] của Elasticsearch,
thêm biểu thức tìm kiếm vào sau dấu hai chấm trong tên bảng như dưới đây.

```sql
SELECT * FROM "tweets: +trino SQL^2"
```

(elasticsearch-sql-support-vi)=
## Hỗ trợ SQL

Connector cung cấp các câu lệnh [dùng chung](sql-globally-available) và
[câu lệnh đọc](sql-read-operations) để truy cập dữ liệu và metadata của catalog
Elasticsearch.

### Truy vấn nhiều bảng bằng ký tự đại diện

Dùng [mẫu tên bảng có ký tự đại diện](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multiple-indices.html)
để truy vấn nhiều bảng cùng lúc.

```sql
SELECT *
FROM example.web."page_views_*";
```

### Hàm bảng

Connector cung cấp {doc}`hàm bảng </functions/table>` để truy cập Elasticsearch.

(elasticsearch-raw-query-function-vi)=
#### `raw_query(varchar) -> table`

Hàm `raw_query` chạy trực tiếp truy vấn viết bằng
[Elastic Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html).
Elasticsearch xử lý toàn bộ truy vấn DSL. Cách này cho phép dùng các tính năng
riêng của Elasticsearch và có thể cải thiện hiệu năng khi Elasticsearch xử
lý truy vấn nhanh hơn.

**Cảnh báo**

Truy vấn gốc phải trả về một bảng kết quả. Nguồn dữ liệu tự kiểm tra
tính hợp lệ và quyền truy cập theo cấu hình của nó. Chỉ dùng truy vấn
gửi thẳng tới nguồn để đọc dữ liệu.

Hàm `raw_query` nhận ba tham số.

- `schema` là schema trong catalog dùng để thực thi truy vấn.
- `index` là index Elasticsearch cần tìm kiếm.
- `query` là truy vấn viết bằng [Elastic Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html).

Kết quả là một dòng chứa toàn bộ JSON phản hồi của Elasticsearch.

Ví dụ dùng catalog `example` để tìm document trong index `orders` có trường
`name` khớp với `ALGERIA`. Biểu thức tìm kiếm JSON được truyền vào tham số
`query` của `raw_query`.

```sql
SELECT
  *
FROM
  TABLE(
    example.system.raw_query(
      schema => 'sales',
      index => 'orders',
      query => '{
        "query": {
          "match": {
            "name": "ALGERIA"
          }
        }
      }'
    )
  );
```

**Lưu ý**

Thứ tự do hàm bảng trả về có thể thay đổi, kể cả khi truy vấn gửi xuống
có `ORDER BY`. Dùng `ORDER BY` trong truy vấn ngoài để sắp xếp kết quả.

## Hiệu năng

Connector hỗ trợ các cơ chế tối ưu dưới đây.

### Đọc dữ liệu song song

Connector gửi yêu cầu tới nhiều node của cụm Elasticsearch để đọc song song.

### Đẩy điều kiện lọc xuống Elasticsearch

Connector hỗ trợ [pushdown điều kiện lọc](predicate-pushdown) cho các cặp kiểu sau.

| Elasticsearch | Trino |
| --- | --- |
| `boolean` | `BOOLEAN` |
| `double` | `DOUBLE` |
| `float` | `REAL` |
| `byte` | `TINYINT` |
| `short` | `SMALLINT` |
| `integer` | `INTEGER` |
| `long` | `BIGINT` |
| `keyword` | `VARCHAR` |
| `date` | `TIMESTAMP` |

Bảng trên liệt kê toàn bộ các cặp kiểu được hỗ trợ cho pushdown điều kiện lọc.

### Lọc sơ bộ trên mảng

Với mảng kiểu nguyên thủy có mapping tương thích, khi toàn bộ lambda trong
`any_match` chưa thể đẩy xuống chính xác, connector có thể dùng một điều kiện
cần làm bộ lọc sơ bộ. Xét truy vấn sau.

```sql
SELECT id
FROM events
WHERE any_match(numbers, x -> x > 10 AND mod(x, 2) = 0);
```

Elasticsearch chỉ trả document có ít nhất một giá trị `numbers` lớn hơn 10.
Trino đánh giá lại toàn bộ lambda để bảo đảm cùng một phần tử vừa lớn hơn 10
vừa là số chẵn. Khi tắt pushdown bằng script, phép chia dư chạy ở Trino.

Với `AND`, connector có thể dùng điều kiện cần của một số nhánh. Với `OR`,
từng nhánh phải có điều kiện lọc sơ bộ an toàn. Vì vậy
`x > 10 OR mod(x, 2) = 0` chạy ở Trino khi tắt script. Bộ lọc sơ bộ luôn giữ
lambda gốc để Trino đánh giá lại. Connector bỏ qua bộ lọc sơ bộ khi mapping
không bảo toàn ngữ nghĩa hoặc truy vấn vượt giới hạn tài nguyên.
Chế độ pushdown toàn văn điều khiển riêng hành vi của mảng `text` đã phân tích.

### Pushdown biểu thức mảng bằng truy vấn gốc

Với mapping mảng số nguyên bảo toàn giá trị nguồn, connector có thể đẩy
`any_match` kết hợp phép so sánh, `IN`, `BETWEEN`, `AND`, `OR` và `NOT`
xuống Elasticsearch bằng truy vấn gốc. Connector xác định tập giá trị hợp lệ
cho từng phần tử rồi mới sinh truy vấn.

```sql
-- Dùng term cho 12 để cả hai điều kiện áp dụng lên cùng một phần tử
SELECT id FROM events
WHERE any_match(numbers, x -> x IN (5, 12) AND x > 10);

-- Dùng hai khoảng dưới và trên 12. Document chứa [12, 13] vẫn khớp
SELECT id FROM events
WHERE any_match(numbers, x -> x <> 12);
```

Phép giao, phép hợp, các giới hạn lặp và các điều kiện mâu thuẫn được biểu
diễn bằng `term`, `terms`, `range`, `bool.should` hoặc `match_none`.
`IS NOT NULL` có thể dùng `exists` nếu chỉ mục bảo toàn ngữ nghĩa của dữ
liệu nguồn. Connector theo dõi riêng các tập giá trị TRUE và FALSE. Giá trị
NULL trong danh sách `IN` vẫn là NULL khi phủ định. Điều kiện có thể trả TRUE
cho phần tử NULL chạy ở Trino vì Elasticsearch bỏ qua phần tử NULL khi lập
chỉ mục. Quy tắc này áp dụng cho `any_match` dùng trực tiếp làm bộ lọc.
Connector giữ phép phủ định toàn bộ `any_match` ở Trino.

Với mảng `keyword` được lập chỉ mục chính xác, connector còn hỗ trợ
`starts_with`, `LIKE` với ký tự thoát tùy chọn, dạng `substring` so khớp tiền
tố được hỗ trợ và một phần cú pháp `regexp_like`. Mẫu `LIKE` được chuyển
thành truy vấn tiền tố hoặc biểu thức chính quy. Cấu trúc regex chưa có
cách dịch tương đương, kể cả neo cuối chuỗi và cờ nội tuyến, chạy ở Trino.
Connector có thể rút gọn phép `AND` trên tiền tố về tiền tố chặt hơn hoặc
tập kết quả rỗng. Với những phép `AND` chưa chứng minh được là tương đương,
Trino đánh giá lại lambda gốc sau khi lọc sơ bộ. Trường bị chuẩn hóa, cắt
ngắn hoặc có mapping làm biến đổi dữ liệu nguồn sẽ bị loại khỏi pushdown
này. Trường thiếu cả chỉ mục lẫn doc values được lọc ở Trino. Để tương thích
với Elasticsearch 7, connector chỉ sinh truy vấn chuỗi gốc khi trường
`keyword` được lập chỉ mục. Trường chỉ có doc values vẫn có thể dùng script
nếu thỏa điều kiện ở mục sau.

Dịch sang truy vấn gốc hoạt động độc lập với
`array_script_pushdown_enabled`. Khi bật script, một phép `OR` có thể kết
hợp nhánh truy vấn gốc chính xác với nhánh script chạy được mà không phát
sinh lỗi. Một phép `AND` có thể kết hợp điều kiện cần ở dạng truy vấn gốc
với script cho toàn bộ lambda để giữ ngữ nghĩa cùng phần tử. Phép toán có
thể lỗi dùng script cho toàn bộ lambda để lọc sơ bộ và giữ điều kiện SQL
gốc cho Trino. Nhờ vậy điều kiện lọc gốc không che mất lỗi số học.
Khi truy vấn kết hợp vượt giới hạn tài nguyên, connector thử script toàn
bộ, bộ lọc sơ bộ dạng truy vấn gốc hoặc xử lý ở Trino. Connector giới hạn
độ sâu, kích thước biểu thức, số giá trị và kích thước truy vấn cuối.

### Script cho biểu thức trên mảng

Đặt `elasticsearch.array-script-pushdown.enabled=true` trong catalog hoặc
đặt thuộc tính phiên `array_script_pushdown_enabled` thành `true` để đẩy
các biểu thức `any_match` được hỗ trợ xuống dưới dạng truy vấn Painless.
Tùy chọn này mặc định tắt và độc lập với chế độ pushdown toàn văn. Connector
ưu tiên truy vấn gốc chính xác. Biểu thức hỗn hợp đủ điều kiện có thể dùng
kết hợp truy vấn gốc và script.

Các mapping mảng giữ tương đương với dữ liệu nguồn gồm các kiểu số nguyên,
`REAL` từ `float`, `DOUBLE` từ `double`, `BOOLEAN`, chuỗi `keyword` trực tiếp
và `TIMESTAMP(3)` từ trường ngày giờ đủ điều kiện. Mọi index thành viên phải
có doc values. Script chính xác loại các kiểu `half_float`, `scaled_float`,
chuỗi đã chuẩn hóa hoặc cắt ngắn và mapping có thể làm mất giá trị nguồn.

Bộ biên dịch theo kiểu hỗ trợ các biểu thức sau khi giữ được ngữ nghĩa về
NULL, kiểu và lỗi.

- Số học `+`, `-`, `*`, `/`, `%`, `mod` và dấu trừ một ngôi. Phép so sánh,
  `IS NOT DISTINCT FROM`, `IN`, `BETWEEN`, `AND`, `OR` và `NOT`.
- Ép kiểu số, `TRY_CAST`, `COALESCE`, `NULLIF` và `CASE` có điều kiện.
  Trino xử lý phép ép từ số dấu phẩy động sang số nguyên hoặc decimal.
  Phép toán decimal được hỗ trợ cho biểu thức decimal tính từ phần tử
  mảng đủ điều kiện. Mapping mảng decimal của Elasticsearch vẫn nằm
  ngoài phạm vi hỗ trợ.
- `abs`, `ceil`/`ceiling`, `floor`, `round`, `truncate`, `sign`, `sqrt` và
  `pow`/`power` với các chữ ký được hỗ trợ. Số chữ số làm tròn phải là hằng số.
- Các hàm chuỗi `length`, `starts_with`, `ends_with`, `strpos`, `replace`,
  `concat`, `substr`/`substring`, `trim`, `ltrim`, `rtrim`, `lower`, `upper`
  và mẫu SQL `LIKE` hằng với ký tự thoát tùy chọn. Vị trí ký tự tính theo
  điểm mã Unicode. Khi đổi hoa thường, script giữ các chuỗi ngoài ASCII
  để Trino kiểm tra.
- Các hàm timestamp UTC `year`, `month`, `day`/`day_of_month`, `hour`, `minute`,
  `second`, `day_of_week`/`dow`, `day_of_year`/`doy`, `date_trunc`,
  `date_add` và `date_diff` với đơn vị cố định. Trino tính chênh lệch theo
  tháng, quý hoặc năm lịch.

Ví dụ dưới đây có hai nhánh `OR` mà Elasticsearch có thể đánh giá.

```sql
SELECT id
FROM events
WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0);
```

Connector có thể đẩy xuống chính xác biểu thức được hỗ trợ và chạy được
mà không phát sinh lỗi. Với phép số học có thể lỗi, đổi hoa thường Unicode,
nối chuỗi có giới hạn, substring ba đối số và biểu thức thời gian, connector
giữ lambda SQL gốc để Trino đánh giá lại. Nếu thao tác trong script gặp lỗi
hoặc chạm giới hạn, script giữ document để Trino hoàn tất đánh giá và giữ
đúng lỗi SQL. Mọi biểu thức được đánh giá trên cùng một phần tử. Phép so
sánh số dấu phẩy động giữ ngữ nghĩa NaN của Trino, kể cả khác biệt giữa
phép bằng và `IS NOT DISTINCT FROM`. Connector bỏ qua truy vấn mảng gốc với
hằng số 0 dấu phẩy động hoặc giá trị không hữu hạn. Khi bật script có kiểu,
phép bằng giữ đúng ngữ nghĩa SQL của 0 dương và 0 âm.

Bộ biên dịch kiểm tra liệu phần tử NULL có thể làm lambda trả TRUE hoặc
phát sinh lỗi bị bỏ sót hay không. `coalesce(x, 0) > 10` đủ điều kiện.
`coalesce(x, 100) > 10` và `x IS NULL` chạy ở Trino. Với bộ lọc
`any_match` dùng trực tiếp và đủ điều kiện, mảng thiếu, mảng rỗng và mảng
chỉ chứa NULL đều được loại đúng. Khi lambda tham chiếu cột bên ngoài,
dùng chữ ký chưa được hỗ trợ hoặc không thỏa kiểm tra NULL, connector giữ
điều kiện ở Trino hoặc dùng bộ lọc sơ bộ dạng truy vấn gốc nếu có.

Connector sinh script với giá trị tham số hóa và giới hạn kích thước, độ sâu
biểu thức, số tham số và số byte của yêu cầu. Bộ lọc động dùng luồng xử lý
riêng. Cụm Elasticsearch phải cho phép truy vấn script, bao gồm thiết lập
`search.allow_expensive_queries`. Lỗi script bất ngờ sẽ làm truy vấn thất
bại. Script có thể tăng tải CPU Elasticsearch, nhất là với mảng lớn. Hãy
kiểm tra tải thực tế trước khi bật. Khi tắt, connector dùng truy vấn gốc và
lọc sơ bộ mảng như bình thường.

### Script cho biểu thức vô hướng

Biểu thức vô hướng trong `WHERE` dùng cùng bộ biên dịch theo kiểu với
lambda trên mảng. Đặt `elasticsearch.scalar-script-pushdown.enabled=true`
trong catalog hoặc đặt `scalar_script_pushdown_enabled` thành `true` trong
phiên catalog. Tùy chọn này mặc định tắt và độc lập với
`array_script_pushdown_enabled`. Connector ưu tiên truy vấn gốc, vốn hoạt
động độc lập với cả hai tùy chọn script.

```sql
SET SESSION elasticsearch.scalar_script_pushdown_enabled = true;

SELECT id
FROM events
WHERE lower(raw_review_comment) LIKE '%sá%'
LIMIT 20;

SELECT id
FROM events
WHERE number > 10 OR mod(number, 2) = 0;
```

Các phép số học, ép kiểu, biểu thức có thể nhận NULL, hàm toán học, hàm
chuỗi và timestamp UTC được hỗ trợ theo danh sách của biểu thức mảng.
Một biểu thức vô hướng được biên dịch có thể tham chiếu nhiều lần tới
cùng một cột. Có thể kết hợp các điều kiện riêng trên nhiều cột. Trino
xử lý biểu thức số học dùng hai cột khác nhau. Điều kiện regex vô hướng
dạng truy vấn gốc vẫn theo chế độ toàn văn và quy tắc đánh giá lại hiện có.

Connector đánh giá trực tiếp ngữ nghĩa NULL của biểu thức vô hướng, kể
cả khi document thiếu trường. Ví dụ `coalesce(number, 100) > 10` có thể
được đẩy xuống chính xác và giữ các dòng NULL. Lambda mảng tương ứng
chạy ở Trino vì phần tử NULL không có trong doc values. Miền giá trị
dấu phẩy động do Trino trích xuất cũng có thể dùng script để giữ ngữ
nghĩa của số 0 có dấu và phép kiểm tra NaN có thuộc tập giá trị hay không.

Script có cùng yêu cầu về doc values và mapping giữ tương đương với dữ
liệu nguồn như bộ biên dịch mảng. Điều này áp dụng cho trường `keyword`
trực tiếp khớp chính xác và các mapping số, boolean, ngày giờ đủ điều
kiện. Trường `text` chỉ có token đã phân tích, trường con `keyword` làm
mất giá trị, `ignore_above` và normalizer vẫn nằm ngoài phạm vi hỗ trợ
khi bật script. Script đọc đúng một giá trị vô hướng. Nếu gặp nhiều
doc values, truy vấn báo lỗi.

Biểu thức chạy được mà không phát sinh lỗi có thể được đẩy xuống chính
xác. Với phép toán có thể lỗi hoặc đổi hoa thường Unicode, script chỉ lọc
sơ bộ và Trino đánh giá lại điều kiện SQL gốc. Ví dụ
`lower(raw_review_comment) LIKE '%sá%'` có thể lọc document ở Elasticsearch
khi cột đủ điều kiện. Trino vẫn phải đổi hoa thường Unicode và kiểm tra
LIKE trên chuỗi ngoài ASCII. Trino áp dụng `LIMIT` sau bước kiểm tra để
đếm đúng số dòng kết quả SQL.

### Pushdown điều kiện tiền tố

Connector đẩy điều kiện khớp tiền tố xuống dưới dạng truy vấn `prefix`
trên trường `keyword` hoặc trường con `keyword` khớp chính xác của trường
`text`. Các dạng hỗ trợ gồm `col LIKE 'abc%'`, `starts_with(col, 'abc')`
và `substr(col, 1, n) = 'abc'` khi `n` bằng độ dài chuỗi tính theo ký tự.
Các mẫu `LIKE` khác được đẩy xuống dưới dạng truy vấn `regexp`.

### Pushdown tìm kiếm toàn văn

Mặc định, Trino xử lý điều kiện và bộ lọc động trên các trường `text` đã
phân tích mà thiếu trường con `keyword` khớp chính xác. Elasticsearch tách
`text` thành token, nên các token có thể khác giá trị chuỗi gốc.

Thuộc tính cấu hình `elasticsearch.full-text-pushdown` hoặc thuộc tính phiên
`full_text_pushdown_mode` chọn cách xử lý điều kiện trên trường đã phân tích.

- `DISABLED` (mặc định) giữ các biểu thức chưa xác nhận được tính tương đương
  ở Trino.
- `SAFE` chỉ đẩy xuống điều kiện được dịch chính xác hoặc bộ lọc sơ bộ đã
  xác nhận giữ đủ kết quả SQL. Các trường hợp còn lại chạy ở Trino.
- `UNSAFE` cho phép dùng kết quả gần đúng của Elasticsearch. Trino bỏ bước
  kiểm tra lại điều kiện SQL gốc đối với các điều kiện đã dịch gần đúng.
  Việc tách token, đổi hoa thường, rút gọn từ, xử lý từ dừng và dấu câu có
  thể thay đổi kết quả. Công cụ chẩn đoán đánh dấu các điều kiện này là
  `APPROXIMATE`.

Chế độ `UNSAFE` áp dụng cho cột văn bản vô hướng và `any_match` trên mảng
văn bản. Connector ưu tiên truy vấn toàn văn gốc nếu có cách dịch. Mẫu
`LIKE` là chuỗi cố định hoặc kiểm tra có chứa chuỗi cố định dùng
`match_phrase`, kể cả khi chuỗi gồm nhiều token. Mẫu khớp tiền tố dùng
`match_phrase_prefix`. Các mẫu khác đủ điều kiện dùng truy vấn Lucene
`regexp` trên token. `LOWER` và `UPPER` cũng có thể dùng cơ chế này.
Bộ phân tích của trường quyết định cách đổi hoa thường và chuẩn hóa, nên
kết quả có thể khác với hàm chuỗi tương ứng của Trino.

Trong ví dụ sau, thay `elasticsearch` bằng tên catalog thực tế.

```sql
SET SESSION elasticsearch.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM reviews
WHERE lower(raw_review_comment) LIKE '%sách rất%';
```

Ví dụ này dùng truy vấn gốc `match_phrase` trên trường `text` cha đã phân
tích, kể cả khi trường con `keyword` loại bỏ giá trị dài. Cách này chạy
trực tiếp trong Elasticsearch và không cần Painless hay bước kiểm tra lại
SQL. Tùy bộ phân tích, truy vấn có thể khớp cả `SÁCH rất tốt` lẫn
`SÁCH, rất tốt`.

Với biểu thức văn bản chưa có cách dịch sang truy vấn gốc, bật thuộc tính
phiên `scalar_script_pushdown_enabled` hoặc `array_script_pushdown_enabled`
tương ứng để cho phép dùng trường runtime. Hai cờ hoạt động độc lập và
mặc định tắt. Script đọc giá trị đầy đủ từ `_source`, nên hoạt động trên
chuỗi gốc. Định nghĩa trường runtime nằm trong yêu cầu tìm kiếm, nên có
thể dùng ngay với dữ liệu hiện tại. Cơ chế này cần truy cập `_source` và
yêu cầu cụm cho phép script runtime.

Cơ chế này dùng lại bộ biên dịch theo kiểu cho các hàm chuỗi như `length`,
`trim`/`ltrim`/`rtrim`, `substr`/`substring`, `replace`, `concat`, `strpos`,
`ends_with`, biểu thức có thể nhận NULL, `CASE` và các tổ hợp được hỗ trợ.
Giá trị số vô hướng trong nguồn được chuyển thành chuỗi giống bộ giải mã
VARCHAR. Với mảng trong nguồn, script giữ phần tử NULL và đánh giá toàn
bộ lambda trên từng phần tử. Các hàm, chữ ký chưa được hỗ trợ hoặc biểu
thức tham chiếu nhiều cột bên ngoài vẫn theo quy tắc xử lý thông thường.

Trong biểu thức trên dữ liệu nguồn không phát sinh lỗi, `LOWER` và
`UPPER` ở chế độ `UNSAFE` có thể dùng quy tắc Unicode của Elasticsearch
và được đánh dấu `APPROXIMATE`. Với biểu thức có thể lỗi, connector giữ
bộ lọc sơ bộ bảo toàn các kết quả, điều kiện SQL gốc và kiểm tra Unicode
để Trino vẫn báo đúng lỗi. Các biểu thức trên nguồn đã xác nhận tương
đương vẫn có thể được đẩy xuống chính xác. Khi bật `UNSAFE`, connector
vẫn đánh giá lại hoặc kiểm tra lỗi ở những trường hợp cần thiết.

Connector gửi định nghĩa trường runtime trong yêu cầu Scroll,
PIT/`search_after`, COUNT và các yêu cầu tổng hợp được hỗ trợ. Đọc
`_source` và chạy script tốn tài nguyên hơn điều kiện lọc gốc dùng
chỉ mục. Số document gửi về Trino có thể giảm ngay cả khi Elasticsearch
vẫn phải quét và phân tích các document ứng viên.

Đối với khóa nối kiểu `text`, connector có thể đẩy bộ lọc động xuống
dưới dạng lọc sơ bộ `match_phrase` khi chế độ khác `DISABLED`. Phép nối
kiểm tra lại khóa.

### Pushdown phép tổng hợp

Connector hỗ trợ [đẩy phép tổng hợp](aggregation-pushdown) cho truy vấn tổng
hợp toàn bộ kết quả và truy vấn có `GROUP BY`. Khi phép tổng hợp được đẩy
xuống, Elasticsearch tính kết quả bằng
[phép tổng hợp composite](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-composite-aggregation.html)
và trả kết quả tổng hợp trực tiếp cho Trino.

Các hàm tổng hợp hỗ trợ pushdown được liệt kê dưới đây.

- `count(*)` and `count(column)`
- `avg` trên `tinyint`, `smallint`, `integer`, `bigint`, `real` và `double`
- `sum` trên `real` và `double`
- `min` và `max` trên cột số, ngoại trừ `bigint`
- `approx_distinct`

`avg` trên số nguyên trả `double`. Trino và Elasticsearch đều cộng dồn
giá trị bằng số dấu phẩy động, nên thứ tự cộng và gộp kết quả theo shard
có thể dẫn đến chênh lệch nhỏ. Pushdown giúp giảm truyền và giải mã
document nguồn. Trino vẫn xử lý `sum` trên số nguyên và `min`/`max` trên
`bigint`.

Trino xử lý `count(DISTINCT ...)`, phép tổng hợp có điều kiện lọc và phép
tổng hợp có thứ tự. Khóa nhóm có thể là `boolean`, kiểu số nguyên,
`keyword` hoặc `timestamp(3)` từ mapping `date` mặc định. TopN và nhóm
theo giá trị dấu phẩy động chạy ở Trino vì Elasticsearch phân biệt
số 0 dương và số 0 âm.

Pushdown yêu cầu mọi index thành viên có mapping tương thích và bảo toàn
ngữ nghĩa của nguồn. Trino xử lý các trường chịu ảnh hưởng của bộ lọc
`_source`, mảng, định nghĩa runtime, script, bước chuẩn hóa, giá trị bị
bỏ qua hoặc trường cha có mapping không bảo toàn ngữ nghĩa. Connector
cũng bỏ qua trường con `keyword` của `text` vì document cũ có thể chưa
được lập chỉ mục cho trường con đó. Khi hết thời gian chờ, shard chỉ
trả một phần kết quả hoặc tổng số document khớp bị ước lượng, phép tổng
hợp báo lỗi để tránh trả kết quả thiếu.

Pushdown tổng hợp mặc định bật. Có thể tắt qua thuộc tính catalog
`elasticsearch.aggregation-pushdown.enabled` hoặc thuộc tính phiên
catalog `aggregation_pushdown_enabled`.

### Thống kê bảng

Khi `elasticsearch.statistics.enabled` là `true` (mặc định), connector
cung cấp [thống kê bảng](/optimizer/statistics) từ các phép tổng hợp của
Elasticsearch. Thống kê gồm số dòng, số giá trị phân biệt, tỷ lệ NULL
và khoảng giá trị của cột số hoặc ngày giờ. Bộ tối ưu theo chi phí dùng
thống kê để chọn kế hoạch, chẳng hạn thứ tự nối và cách phân phối phép nối.

Connector lấy số dòng bằng yêu cầu ít tốn tài nguyên. Thống kê từng cột
cần chạy phép tổng hợp trên mọi document khớp, nên connector giới hạn
công việc theo ba cách dưới đây. Bỏ qua thống kê chỉ ảnh hưởng chất lượng
kế hoạch. Kết quả truy vấn vẫn tuân theo ngữ nghĩa SQL.

- Connector chỉ thu thập thống kê cho các cột trong `WHERE`, `GROUP BY`
  hoặc `ORDER BY`. Bộ tối ưu dùng thống kê của những cột này. Các cột
  chỉ xuất hiện trong `SELECT` và khóa nối được bỏ qua vì connector
  không thực hiện phép nối. Thuộc tính
  `elasticsearch.statistics.max-statistics-columns` giới hạn số cột.
- Connector chỉ lấy thống kê cột nếu số document qua lọc tối đa bằng
  `elasticsearch.statistics.max-index-documents`. Với tập kết quả lớn
  hơn, connector chỉ lấy số dòng.
- Mỗi yêu cầu thống kê có thời gian chờ ngắn do
  `elasticsearch.statistics.request-timeout` quy định. Khi phép tổng hợp
  quá chậm, connector dùng số dòng đã có và tiếp tục lập kế hoạch sau
  khi hết thời gian chờ, không thử lại yêu cầu.

Connector tự thu thập thống kê với cấu hình mặc định. Với index rất lớn
hoặc chậm, giảm `elasticsearch.statistics.max-index-documents` hoặc
`elasticsearch.statistics.request-timeout` để bỏ qua phép tổng hợp tốn
tài nguyên sớm hơn. Tăng `elasticsearch.statistics.max-statistics-columns`
nếu truy vấn lọc hoặc nhóm theo nhiều cột. Đặt
`elasticsearch.statistics.enabled` thành `false` để tắt thu thập thống kê.

Yêu cầu thống kê bao gồm các điều kiện lọc đã đẩy xuống chính xác và
được lưu trong handle của bảng. Khi có bộ lọc sơ bộ, điều kiện gần đúng
hoặc handle tổng hợp, thống kê được coi là chưa xác định. `LIMIT` đã
đẩy xuống giới hạn ước lượng số dòng. Connector bỏ qua thống kê từng cột
cho tập đã giới hạn. Phản hồi hết thời gian chờ hoặc thiếu kết quả từ
một số shard được loại khỏi thống kê chính xác.

### Thực thi tìm kiếm

Scroll và PIT dùng chung cách ghi nhận quá trình quét, dừng ở giới hạn,
dọn dẹp khi lỗi và đóng an toàn nếu được gọi nhiều lần. PIT dùng ngữ cảnh
giới hạn theo shard, giá trị sắp xếp ổn định cho `search_after` và ID ngữ
cảnh từ phản hồi gần nhất. Khi ngữ cảnh hết hạn hoặc gặp lỗi, truy vấn
thất bại. Sau khi đã trả dòng, connector tránh mở lại ngữ cảnh hoặc đổi
chiến lược. Cả hai chiến lược đóng ngữ cảnh từ xa khi đọc xong, đạt tới
giới hạn đã đẩy xuống hoặc khi toán tử phía sau đóng nguồn đọc. Cả hai dùng
chung cấu hình kích thước và thời gian chờ của scroll.

Scroll là lựa chọn mặc định để bảo đảm tương thích. Hãy đo PIT và giải
mã streaming với tải đại diện trước khi bật. Mức cải thiện thông lượng
và bộ nhớ có thể thay đổi theo tải thực tế.

### Bộ lọc động

Connector hỗ trợ [lọc động](/admin/dynamic-filtering). Trino thu thập bộ
lọc như khóa nối từ nhánh build của phép nối rồi đưa vào truy vấn
Elasticsearch để giảm số document cần quét. Thuộc tính cấu hình
`elasticsearch.dynamic-filtering.wait-timeout` hoặc thuộc tính phiên
`dynamic_filtering_wait_timeout` điều khiển thời gian chờ bộ lọc
trước khi tạo split.

### Chẩn đoán pushdown

Connector công bố `ElasticsearchPushdownDiagnostics` qua JMX cho từng
catalog trên từng node Trino. Bộ đếm cộng dồn trong suốt vòng đời connector.
Số liệu lập kế hoạch trên coordinator và thực thi trên worker được ghi
riêng. Chỉ số phản ánh hoạt động của nhiều truy vấn và có thể thay đổi khi
chụp trong lúc truy vấn đang chạy.

- `TranslationReasonCounts` và `NormalizationCounts` ghi nhận quyết định của
  bộ lập kế hoạch, gồm dịch chính xác, lọc sơ bộ, dịch gần đúng, giữ điều kiện
  cho Trino, làm phẳng biểu thức và gộp điều kiện `terms`. Bộ đếm dịch bao gồm
  quyết định cho biểu thức con và các lần lập kế hoạch lặp lại.
- `DynamicFilterOutcomes` ghi các trường hợp bộ lọc không giới hạn, rỗng, được
  đẩy toàn phần, được đẩy một phần hoặc bị từ chối. Bộ đếm giá trị theo dõi
  các giá trị rời rạc. `DynamicFilterTermsBatches` đếm các nút `terms` gốc,
  bỏ qua `term` chỉ có một giá trị. Ước lượng byte chỉ tính các mệnh đề
  lọc được chấp nhận bên trong yêu cầu.
- `RenderedQueries` và `RenderedQueryBytes` đếm điều kiện lọc đã tạo thành công
  cho truy vấn quét, đếm và tổng hợp, cùng kích thước JSON UTF-8 của chúng.
  Bộ đếm nút mô tả biểu diễn trung gian của các điều kiện đó. Phạm vi đo
  là phần điều kiện lọc, và một truy vấn có thể được tính nhiều lần.
- `SearchRequests`, `NextPageRequests` và `CountRequests` đếm số lần scan hoặc
  count được gọi, gồm cả lần thất bại. `RetryAttempts` đếm số lần REST client
  thử lại do Elasticsearch quá tải. `RemotePagesReceived` đếm phản hồi quét
  thành công, kể cả trang cuối rỗng. `PagesReturned` đếm trang kết quả quét
  và đếm đưa về Trino. `RowsDecoded` và `SourceBytesDecoded` đếm document
  được giải mã và số byte của dữ liệu nguồn trong luồng quét.
- `PointInTimeOpens` và `PointInTimeCloses` đếm số lần thử mở và đóng PIT.
  `AggregationRequests` và `StatisticsRequests` đếm số lượt yêu cầu logic
  trong các luồng tương ứng. Phản hồi và trang kết quả tổng hợp cũng
  được cộng vào bộ đếm trang. `AggregationRows` và
  `AggregationOutputBytes` đếm số dòng tổng hợp và byte của block Trino
  đã giải mã. Lỗi thống kê vẫn góp vào `Failures`, kể cả khi bộ lập kế
  hoạch tiếp tục với thống kê chưa xác định.
- `Cancellations` đếm lần nguồn quét được đóng trước khi đọc xong hoặc trước
  khi chạm `LIMIT` đã đẩy xuống. Toán tử phía sau có thể đóng nguồn đọc
  ngay cả khi người dùng không hủy truy vấn. `Failures` đếm lỗi quét,
  lỗi đếm và lỗi dọn dẹp scroll. Một lần quét có thể gặp thêm lỗi dọn
  dẹp. `ClearScrollCalls` đếm các lần thử dọn dẹp.

Bật debug logging cho
`io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics` để xem
quyết định lập kế hoạch, chuẩn hóa, lọc động và dựng truy vấn. Các sự kiện
chẩn đoán bỏ qua giá trị của điều kiện lọc. Có thể bật log để quan sát
các quyết định pushdown và điều kiện xử lý lại. Đóng sớm phép tổng hợp
cũng tăng `Cancellations`. Lỗi dọn dẹp PIT tăng `Failures`.

[built-in date formats]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#built-in-date-formats
[custom date formats]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#custom-date-formats
[date]: https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html
[format]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#mapping-date-format
[full text query]: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax
