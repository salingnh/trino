# Elasticsearch connector

```{raw} html
<img src="../_static/img/elasticsearch.png" class="connector-logo">
```

```{toctree}
:hidden:

elasticsearch-schema-inference-vi
```

The Elasticsearch connector allows access to
[Elasticsearch](https://www.elastic.co/products/elasticsearch) data from Trino.
This document describes how to configure a catalog with the Elasticsearch
connector to run SQL queries against Elasticsearch.

[Tiếng Việt](elasticsearch-schema-inference-vi.md)

## Requirements

- Elasticsearch 7.x or 8.x
- Network access from the Trino coordinator and workers to the Elasticsearch nodes.

## Configuration

To configure the Elasticsearch connector, create a catalog properties file
`etc/catalog/example.properties` with the following contents, replacing the
properties as appropriate for your setup:

```text
connector.name=elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.default-schema-name=default
```

The following table details all general configuration properties:

| Property name | Description | Default |
| --- | --- | --- |
| `elasticsearch.host` | The comma-separated list of host names for the Elasticsearch node to connect to. This property is required. |  |
| `elasticsearch.port` | Port to use to connect to Elasticsearch. | `9200` |
| `elasticsearch.default-schema-name` | The schema that contains all tables defined without a qualifying schema name. | `default` |
| `elasticsearch.scroll-size` | Sets the maximum number of hits that can be returned with each [Elasticsearch scroll request](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#scroll-search-context). | `1000` |
| `elasticsearch.search-strategy` | Pagination strategy: `SCROLL` or opt-in `PIT`. PIT requires Elasticsearch 7.17 or 8.x and permission to open and close point-in-time contexts. | `SCROLL` |
| `elasticsearch.response-decoder` | Search response decoder: `MATERIALIZED` or opt-in `STREAMING`. Streaming materializes one hit tree at a time, independently of pagination strategy. | `MATERIALIZED` |
| `elasticsearch.scroll-timeout` | [Duration](prop-type-duration) for Elasticsearch to keep the search context alive for scroll requests. | `1m` |
| `elasticsearch.request-timeout` | Timeout [duration](prop-type-duration) for all Elasticsearch requests. | `10s` |
| `elasticsearch.connect-timeout` | Timeout [duration](prop-type-duration) for all Elasticsearch connection attempts. | `1s` |
| `elasticsearch.backoff-init-delay` | The minimum [duration](prop-type-duration) between backpressure retry attempts for a single request to Elasticsearch. Setting it too low can overwhelm an already struggling cluster. | `500ms` |
| `elasticsearch.backoff-max-delay` | The maximum [duration](prop-type-duration) between backpressure retry attempts for a single request to Elasticsearch. | `20s` |
| `elasticsearch.max-retry-time` | The maximum [duration](prop-type-duration) across all retry attempts for a single request to Elasticsearch. | `30s` |
| `elasticsearch.node-refresh-interval` | [Duration](prop-type-duration) between requests to refresh the list of available Elasticsearch nodes. | `1m` |
| `elasticsearch.schema-inference.enabled` | Enable [schema inference](#elasticsearch-schema-inference) for indices without Trino metadata. | `true` |
| `elasticsearch.statistics.enabled` | Enable collecting table statistics by querying Elasticsearch aggregations (document count, distinct values, null fractions, and min/max ranges) so the cost-based optimizer can better plan joins and other operations. | `true` |
| `elasticsearch.statistics.max-index-documents` | Collect per-column statistics only for indices with at most this many documents. Larger indices report the row count only, avoiding a per-column aggregation over every document that can time out during planning. | `20000000` |
| `elasticsearch.statistics.max-statistics-columns` | Maximum number of columns to collect per-column statistics for. Statistics are collected only for columns the query references (filter, `GROUP BY`, and sort columns); this bounds that set. | `32` |
| `elasticsearch.statistics.request-timeout` | Socket timeout for a single per-column statistics request. A slow aggregation fails fast, with no retry, and falls back to the row count instead of blocking query planning. | `5s` |
| `elasticsearch.dynamic-filtering.wait-timeout` | Maximum [duration](prop-type-duration) to wait for the collection of dynamic filters, such as join keys, before generating splits. Dynamic filters are applied within the Elasticsearch query to reduce the number of scanned documents. Can be overridden per query with the `dynamic_filtering_wait_timeout` session property. | `5s` |
| `elasticsearch.keyword-subfield-pushdown-with-ignore-above` | Push predicates and aggregations for a `text` field down to its exact-match `keyword` sub-field even when that sub-field defines `ignore_above`. This is disabled by default because `ignore_above` omits longer values from the sub-field index, so pushed-down predicates could return incomplete results. Enable it only when all indexed string values are shorter than the `ignore_above` limit. Can be overridden per query with the `keyword_subfield_pushdown_with_ignore_above` session property. | `false` |
| `elasticsearch.aggregation-page-size` | Maximum composite aggregation buckets per response. Must be at least 1. Larger pages reduce requests but can increase memory per response. | `10000` |
| `elasticsearch.aggregation-pushdown.enabled` | Enable pushing down aggregations to Elasticsearch. Can be overridden per query with the `aggregation_pushdown_enabled` session property. | `true` |
| `elasticsearch.scalar-script-pushdown.enabled` | Enable Painless pushdown for supported scalar expressions on source-equivalent fields with doc values. Can be overridden with the `scalar_script_pushdown_enabled` session property. | `true` |
| `elasticsearch.full-text-pushdown` | Push predicates and dynamic filters on analyzed `text` fields to Elasticsearch as full-text queries. One of `DISABLED`, `SAFE`, or `UNSAFE`. A `text` field is tokenized, so this does not have exact SQL semantics. Can be overridden per query with the `full_text_pushdown_mode` session property. | `DISABLED` |
| `elasticsearch.ignore-publish-address` | Disable using the address published by the Elasticsearch API to connect for queries. Some deployments map Elasticsearch ports to a random public port and enabling this property can help in these cases. | `false` |

### Authentication

The connection to Elasticsearch can use AWS or password authentication.

To enable AWS authentication and authorization using IAM policies, the
`elasticsearch.security` option must be set to `AWS`. Additionally, the
following options must be configured:

| Property name | Description |
| --- | --- |
| `elasticsearch.aws.region` | AWS region of the Elasticsearch endpoint. This option is required. |
| `elasticsearch.aws.access-key` | AWS access key to use to connect to the Elasticsearch domain. If not set, the default AWS credentials provider chain is used. |
| `elasticsearch.aws.secret-key` | AWS secret key to use to connect to the Elasticsearch domain. If not set, the default AWS credentials provider chain is used. |
| `elasticsearch.aws.iam-role` | Optional ARN of an IAM role to assume to connect to Elasticsearch. Note that the configured IAM user must be able to assume this role. |
| `elasticsearch.aws.external-id` | Optional external ID to pass while assuming an AWS IAM role. |

To enable password authentication, the `elasticsearch.security` option must be set
to `PASSWORD`. Additionally the following options must be configured:

| Property name | Description |
| --- | --- |
| `elasticsearch.auth.user` | Username to use to connect to Elasticsearch. |
| `elasticsearch.auth.password` | Password to use to connect to Elasticsearch. |

### Connection security with TLS

The connector provides additional security options to connect to Elasticsearch
clusters with TLS enabled.

If your cluster has globally-trusted certificates, you should only need to
enable TLS. If you require custom configuration for certificates, the connector
supports key stores and trust stores in P12 (PKCS) or Java Key Store (JKS) format.

The available configuration values are listed in the following table:

| Property name | Description |
| --- | --- |
| `elasticsearch.tls.enabled` | Enables TLS security. |
| `elasticsearch.tls.keystore-path` | The path to the P12 (PKCS) or [JKS](/security/inspect-jks) key store. |
| `elasticsearch.tls.truststore-path` | The path to P12 (PKCS) or [JKS](/security/inspect-jks) trust store. |
| `elasticsearch.tls.keystore-password` | The key password for the key store specified by `elasticsearch.tls.keystore-path`. |
| `elasticsearch.tls.truststore-password` | The key password for the trust store specified by `elasticsearch.tls.truststore-path`. |
| `elasticsearch.tls.verify-hostnames` | Flag to determine if the hostnames in the certificates must be verified. Defaults to `true`. |

(elasticsearch-type-mapping)=
## Type mapping

Because Trino and Elasticsearch each support types that the other does not, this
connector {ref}`maps some types <type-mapping-overview>` when reading data.

### Elasticsearch type to Trino type mapping

The connector maps Elasticsearch types to the corresponding Trino types
according to the following table:

| Elasticsearch type | Trino type | Notes |
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
| `DATE` | `TIMESTAMP` | For more information, see [Date types](#elasticsearch-date-types). |
| `IPADDRESS` | `IP` |  |

No other types are supported.

(elasticsearch-array-types)=
### Array types

Fields in Elasticsearch can contain [zero or more
values](https://www.elastic.co/guide/en/elasticsearch/reference/current/array.html),
but there is no dedicated array type. To indicate a field contains an array, it
can be annotated in a Trino-specific structure in the
[\_meta](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-meta-field.html)
section of the index mapping.

For example, you can have an Elasticsearch index that contains documents with the following structure:

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

To declare the array fields explicitly, add them to `_meta.trino` in the mapping
for the existing `doc` index. Include any metadata you want to retain when
updating `_meta`.

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

:::{note}
It is not allowed to use `asRawJson` and `isArray` flags simultaneously for the same column.
:::

(elasticsearch-date-types)=
### Date types

The connector supports the default Elasticsearch [date] format. With
[schema inference](elasticsearch-schema-inference), fields using a [built-in date format][built-in date formats]
or [custom date format][custom date formats] can also map to `TIMESTAMP(3)` when doc values are
enabled. For explicitly declared schemas, the connector skips fields with
a [format] property.

(elasticsearch-raw-json-transform)=
### Raw JSON transform

Documents in Elasticsearch can include more complex structures that are not
represented in the mapping. For example, a single `keyword` field can have
widely different content including a single `keyword` value, an array, or a
multidimensional `keyword` array with any level of nesting.

The following command configures `array_string_field` mapping with Elasticsearch
available at `search.example.com:9200`:

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

All the following documents are legal for Elasticsearch with
`array_string_field` mapping:

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

See the [Elasticsearch array
documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/array.html)
for more details.

Set `asRawJson: true` in `_meta.trino` to read a field and its children as
`VARCHAR` containing raw JSON. This is useful for mixed shapes and for
Elasticsearch types such as
[`dense_vector`](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html)
that have no direct Trino type mapping.

The following command sets `asRawJson` for `array_string_field` in the existing
`doc` index. Include any metadata you want to retain when updating `_meta`.

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

This preceding configuration causes Trino to return the `array_string_field`
field as a `VARCHAR` containing raw JSON. You can parse these fields with the
[built-in JSON functions](/functions/json).

:::{note}
It is not allowed to use `asRawJson` and `isArray` flags simultaneously for the same column.
:::

(elasticsearch-schema-inference)=
### Schema inference

Schema inference is enabled by default. The connector infers types for an index
when its mapping contains neither `_meta.trino` nor legacy `_meta.presto`
metadata. If `_meta.trino` exists, the connector skips sampling for the index and
uses the declared settings and defaults. To disable inference for the catalog,
add the following property to its properties file.

```properties
elasticsearch.schema-inference.enabled=false
```

#### How sampling works

1. On the first metadata lookup for an index, the coordinator reads its mapping
   and requests up to three documents.
2. For each searchable field that still lacks a sample, the coordinator requests
   up to three additional documents with an `exists` query. It groups these
   queries into batches of up to 16.
3. The coordinator uses `_source` to choose Trino types and caches up to 512
   schema entries for five minutes. The cache distinguishes sessions by their
   bounded keyword subfield setting. A mapping change triggers a fresh sample.
   Trino keeps a consistent schema throughout each query.

For an empty index, the connector uses the mapping to expose column types and
retries sampling at the next metadata lookup.

Elasticsearch `exists` checks for indexed values. A field can appear in
`_source` and still have no indexed value, for example when its value exceeds
`ignore_above`. [Elasticsearch `exists` query](https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-exists-query)

#### How values map to Trino types

Inference examines each field in `_source`, including fields inside objects.
It checks array depth and the types of values inside the arrays separately.
The observed value types are string, integer, floating-point number, boolean,
and object. Missing fields and `null` do not count as observed types.
Empty arrays and arrays containing only `null` provide evidence about array
depth. The connector can use a supported mapping to determine the element
type when no non-null elements are found.

In the tables below, **raw JSON** means the connector exposes the entire
affected field as `VARCHAR` containing JSON. For example, a string becomes
the JSON text `"Hà Nội"` with quotes, and an array becomes JSON text with
brackets. A field promoted to plain `VARCHAR` uses the text value without
adding JSON quotes.

##### Value type conflicts within one index

For top-level scalar fields with supported numeric, boolean, or `date`
mappings and doc values, the connector prefers the mapped type. For example,
Elasticsearch can index `"02"` as the integer `2` in a `long` field. Trino
reads the indexed value as `BIGINT` and validates the shape of `_source`.
When `ignore_malformed` is enabled on a field using this mode, Trino
reads an ignored value as SQL `NULL`. A `keyword` field with mixed number
or boolean values can also use its indexed strings when the mapping supports
this.

The following promotion rules apply to array elements and fields that do
not use the mapped type. The field must have a consistent array depth. A supported
promotion at the element level keeps that depth. For example, arrays
`[1]` and `["02"]` infer to `ARRAY(VARCHAR)` with values `["1"]` and
`["02"]`.

| Observed value types | Example | Inferred Trino type |
| --- | --- | --- |
| Integer and string | `1` and `"02"` | `VARCHAR`. The integer becomes `"1"` and `"02"` keeps its leading zero. |
| Floating-point number and string | `1.5` and `"2.5"` | `VARCHAR`. The number becomes text. |
| Boolean and string | `true` and `"false"` | `VARCHAR`. The boolean becomes `"true"`. |
| Integer and floating-point number | `1` and `2.5` | `DOUBLE` when every sampled integer can be represented exactly. Otherwise, raw JSON. |
| Integer and boolean | `1` and `true` | Raw JSON. |
| Floating-point number and boolean | `1.5` and `true` | Raw JSON. |
| Object and any primitive value | `{"name": "An"}` and `"An"` | Raw JSON. |
| Three or more value types | `1`, `2.5`, and `"3"` | Raw JSON, even if each pair could be promoted on its own. |

A mapped `boolean` field accepts JSON booleans and the string forms accepted
by Elasticsearch, including the empty string for false. With doc values, the
connector reads the indexed boolean. A `date` field with doc values becomes
`TIMESTAMP(3)` using UTC epoch milliseconds, including when its mapping
defines custom formats such as `yyyy/MM/dd HH:mm:ss||epoch_second`.
A mapped `float` uses `REAL`. Mapped `half_float` and `scaled_float` use
`DOUBLE`. These fields read the stored precision from doc values. For example,
a `scaled_float` field with a scaling factor of 100 exposes 2.34 for an input
of 2.341.

For one observed value type, the connector keeps a compatible, supported
mapping. If a primitive mapping is incompatible or unsupported, the connector
can infer `VARCHAR` for a string, `BIGINT` for an integer that fits, `DOUBLE`
for a floating-point number, or `BOOLEAN` for a boolean. It uses raw JSON
when no such rule applies.

##### Conflicting shapes

| Observed shapes | Example | Inferred Trino type |
| --- | --- | --- |
| A primitive value and an array | `"Hà Nội"` and `["Hà Nội", "Huế"]` | Raw JSON for the field. |
| An object and an array of objects | `{"name": "An"}` and `[{"name": "Bình"}]` | Raw JSON for the field. |
| Different array depths across documents or within one array | `["29"]` and `[["30"]]`, or `["29", ["30"]]` | Raw JSON for the field. |
| Consistent array depth | `[["29", "30"]]` and `[["31"]]` | Keep the array depth. In this example the type is `ARRAY(ARRAY(VARCHAR))`. |
| Objects with mapped child fields at the same depth | `{"name": "An"}` and `{"age": 28}` under an object mapping that defines both children | Infer each child separately. The object becomes `ROW`, wrapped in `ARRAY` at each observed array level. Missing children read as SQL `NULL`. |
| Shape still unknown after sampling, or an object with no mapped child fields | Only missing or `null` values, or an empty object whose mapping has no children | Raw JSON for the field. |

Fallback applies at the conflicting field. If `users` is an array of
objects and only `users.city` mixes strings and arrays, `users` stays an
`ARRAY(ROW(...))` and `city` becomes raw JSON inside the row.

These examples describe values the connector can encounter in `_source`.
An Elasticsearch mapping can reject some combinations, such as an object
and a primitive value in the same mapped field.

##### Conflicts across index mappings

For an alias or wildcard, the connector merges the schemas of all backing
indices. Each index contributes its declared or inferred schema. A field present in just one
schema keeps its type. For fields present in more than one schema, the connector
applies the following rules.

- Identical field definitions keep their type. If the definitions differ, a
  raw JSON field, different array depths, or more than two combined value types
  cause raw JSON fallback.
- Object fields at the same depth merge their children recursively. In a
  declared schema, the mapping supplies the value type for the merge rather
  than a document sample.
- String plus integer, floating-point number, or boolean can merge to
  `VARCHAR` for primitive mappings. Integer plus floating-point number
  can merge to `DOUBLE` when inference established that the sampled integers
  can be represented exactly for every contributing field.
- Otherwise, the connector keeps the type if the underlying type and inferred
  type override agree. Other conflicts use raw JSON.

Use the following statement to check the inferred schema. Replace
`elasticsearch` and `events` with the catalog and index names.

```sql
DESCRIBE elasticsearch.default.events;
```

#### Pushdown

Mapped scalar types can use predicate pushdown under the connector's normal
rules. For example, `age > 1` on a `long` field uses the indexed numeric
value. Trino evaluates filters on fields with `index: false`, incompatible
mappings across indices, or raw JSON fallback. Existing guards such as
`ignore_above` and `normalizer` for string fields also apply. Date predicates
use native ranges with `format: epoch_millis`. Predicates on inferred
`float`, `half_float`, and `scaled_float` use native range bounds adjusted
for the indexed precision where an exact translation is available.
[Elasticsearch type coercion](https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/coerce)

#### Troubleshoot type mismatches

A document outside the sample can still trigger `TYPE_MISMATCH` when Trino
decodes it. Queries evaluated entirely by Elasticsearch skip that decoding
step. The error
includes the index, document ID, and field information. Provide `_meta.trino`
for the index to control how Trino reads the affected field. Use `isArray` for
a supported array type or `asRawJson` to read mixed shapes as raw JSON. See
[](elasticsearch-array-types) and [](elasticsearch-raw-json-transform).

Read this section in {ref}`Vietnamese <elasticsearch-schema-inference-vi>`.

## Special columns

The following hidden columns are available:

| Column | Description |
| --- | --- |
| `_id` | The Elasticsearch document ID. |
| `_score` | The document score returned by the Elasticsearch query. |
| `_source` | The source of the original document. |


(elasticsearch-full-text-queries)=
## Full text queries

Trino SQL queries can be combined with Elasticsearch queries by providing the [full text query]
as part of the table name, separated by a colon. For example:

```sql
SELECT * FROM "tweets: +trino SQL^2"
```

(elasticsearch-sql-support)=
## SQL support

The connector provides [globally available](sql-globally-available) and [read
operation](sql-read-operations) statements to access data and metadata in the
Elasticsearch catalog.

### Wildcard table

The connector provides support to query multiple tables using a concise
[wildcard table](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-multiple-indices.html)
notation.

```sql
SELECT *
FROM example.web."page_views_*";
```

### Table functions

The connector provides specific {doc}`table functions </functions/table>` to
access Elasticsearch.

(elasticsearch-raw-query-function)=
#### `raw_query(varchar) -> table`

The `raw_query` function allows you to query the underlying database directly.
This function requires [Elastic Query
DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
syntax. The full DSL query is pushed down and processed in Elasticsearch. This
can be useful for accessing native features which are not available in Trino or
for improving query performance in situations where running a query natively may
be faster.

```{include} query-passthrough-warning.fragment
```

The `raw_query` function requires three parameters:

- `schema`: The schema in the catalog that the query is to be executed on.
- `index`: The index in Elasticsearch to be searched.
- `query`: The query to execute, written in [Elastic Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html).

Once executed, the query returns a single row containing the resulting JSON
payload returned by Elasticsearch.

For example, query the `example` catalog and use the `raw_query` table function
to search for documents in the `orders` index where the country name is
`ALGERIA` as defined as a JSON-formatted query matcher and passed to the
`raw_query` table function in the `query` parameter:

```
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

```{include} query-table-function-ordering.fragment
```

## Performance

The connector includes a number of performance improvements, detailed in the
following sections.

### Parallel data access

The connector requests data from multiple nodes of the Elasticsearch cluster for
query processing in parallel.

### Predicate push down

The connector supports [predicate push down](predicate-pushdown) for the
following data types:

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

No other data types are supported for predicate push down.

### Array predicate prefiltering

For primitive arrays with compatible exact mappings, the connector can use a
necessary condition from an `any_match` lambda as an Elasticsearch prefilter when
the whole lambda cannot be pushed down exactly. For example:

```sql
SELECT id
FROM events
WHERE any_match(numbers, x -> x > 10 AND mod(x, 2) = 0);
```

Elasticsearch filters documents to those with a `numbers` value greater than 10.
Trino then evaluates the complete original lambda, so the same element must be
both greater than 10 and even. With script pushdown disabled, the modulo
expression itself is not pushed down.

An `AND` can use necessary conditions from a subset of its branches. An `OR`
requires a safe candidate for every branch; `x > 10 OR mod(x, 2) = 0` remains
local with script pushdown disabled. Prefilters retain the original lambda for
evaluation in Trino, and are omitted when mapping safety or query resource limits
cannot be met.
Analyzed-text array behavior is controlled separately by the full-text mode.

### Native array expression logic

For source-equivalent integral array mappings, `any_match` can combine
comparisons, `IN`, `BETWEEN`, `AND`, `OR`, and `NOT` without Painless. The
connector computes the allowed values of one element before generating queries.
For example:

```sql
-- term query for 12, rather than independent document conditions
SELECT id FROM events
WHERE any_match(numbers, x -> x IN (5, 12) AND x > 10);

-- ranges below and above 12; a document containing [12, 13] still matches
SELECT id FROM events
WHERE any_match(numbers, x -> x <> 12);
```

Intersections, unions, repeated bounds, and contradictions become `term`,
`terms`, `range`, `bool.should`, or `match_none` queries. `IS NOT NULL` may use
`exists` when indexed values preserve source semantics. TRUE and FALSE value
sets are tracked separately: NULL in an `IN` list cannot become TRUE through
negation. Predicates that can be TRUE for a NULL element remain local because
NULL array elements are not indexed. These rules apply to a positive `any_match`
filter, not to negating the enclosing `any_match`.

Exact indexed keyword arrays additionally support `starts_with`, SQL `LIKE` with its
optional escape, the supported substring-prefix form, and a conservative subset
of `regexp_like`. LIKE patterns use native prefix or regexp queries. Regex
constructs without an exact shared translation, including end anchors and inline
flags, remain local. Prefix conjunctions can be reduced to the stronger prefix
or an empty match; other unproven conjunctions retain the original lambda after
prefiltering. Normalized, truncated, and otherwise source-incompatible mappings
are not eligible. Fields with neither an index nor doc values remain local.
These native string queries are not generated for doc-values-only keyword
fields, for compatibility with Elasticsearch 7. Script support described below
can still apply to eligible keyword doc values.

Native translation is independent of `array_script_pushdown_enabled`. When
scripts are enabled, an OR can combine exact native branches with non-throwing
script branches. An AND can combine a native necessary condition with a script
for the complete lambda, preserving same-element evaluation. Fallible arithmetic
keeps the whole-script prefilter strategy and SQL residual, so a native guard
cannot hide an arithmetic error. If a combined query exceeds resource limits,
the connector tries the whole script or the existing native prefilter/local
fallback. Expression depth, size, value counts, and final query size are bounded.

### Array expression scripts

Enable `elasticsearch.array-script-pushdown.enabled=true` in the catalog, or set
its `array_script_pushdown_enabled` session property to `true`, to push supported
`any_match` expressions as Painless script queries. This option is disabled by
default and is independent of full-text pushdown mode. Native exact queries are
preferred; eligible mixed expressions can use native queries and scripts together.

Supported source-equivalent array mappings include integral types, `REAL` on
`float`, `DOUBLE` on `double`, `BOOLEAN`, direct `keyword` strings, and
`TIMESTAMP(3)` on eligible date fields. Doc values are required in every backing
index. `half_float`, `scaled_float`, normalized or truncated strings, and mappings
that can lose source values are not eligible for exact script evaluation.

The typed compiler supports the following expressions when it can preserve their
NULL, type, and error semantics:

- Arithmetic `+`, `-`, `*`, `/`, `%`, `mod`, and unary minus; comparisons,
  `IS NOT DISTINCT FROM`, `IN`, `BETWEEN`, `AND`, `OR`, and `NOT`.
- Numeric casts and `TRY_CAST`, `COALESCE`, `NULLIF`, and searched `CASE`.
  Floating-point to integral or decimal casts remain local. Decimal arithmetic
  is supported on decimal expressions derived from eligible array elements;
  this does not add a decimal Elasticsearch array mapping.
- `abs`, `ceil`/`ceiling`, `floor`, `round`, `truncate`, `sign`, `sqrt`, and
  `pow`/`power`, for supported signatures. Decimal rounding places must be constant.
- String `length`, `starts_with`, `ends_with`, `strpos`, `replace`, `concat`,
  `substr`/`substring`, `trim`, `ltrim`, `rtrim`, `lower`, `upper`, and constant
  SQL `LIKE` patterns with optional escape. Character positions use Unicode code
  points. Case conversion admits non-ASCII strings for checking by Trino.
- UTC timestamp `year`, `month`, `day`/`day_of_month`, `hour`, `minute`, `second`,
  `day_of_week`/`dow`, `day_of_year`/`doy`, `date_trunc`, `date_add`, and fixed-unit
  `date_diff`. Calendar month, quarter, and year differences remain local.

For example, both branches of this `OR` can be evaluated by Elasticsearch:

```sql
SELECT id
FROM events
WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0);
```

A supported non-throwing expression can be pushed exactly. Fallible arithmetic,
Unicode case conversion, guarded string expansion, three-argument substring,
and temporal expressions retain the original SQL lambda as a residual. A script
admits the document when a supported operation errors or reaches its guard, so
Trino can preserve SQL errors and finish evaluation. Every expression is evaluated
on the same array element. Floating comparisons preserve Trino's NaN semantics,
including the difference between equality and `IS NOT DISTINCT FROM`. Native
array predicates with floating zero or non-finite constants are declined; typed
scripts preserve SQL equality of positive and negative zero when enabled.

The compiler proves that a NULL element cannot make the lambda true or introduce
an error that would be lost by skipping it. Expressions such as
`coalesce(x, 0) > 10` are eligible; `coalesce(x, 100) > 10` and `x IS NULL` remain
local. Missing, empty, and only-NULL arrays are excluded correctly for eligible
positive `any_match` filters. Captured columns, unsupported signatures, and failed
NULL proofs remain local or use an available necessary native prefilter.

Scripts are generated by the connector with parameterized values. Expression
size and depth, parameter counts, and rendered request bytes are bounded.
Dynamic filters do not use this script compiler. Script queries require the
Elasticsearch cluster to allow them, including `search.allow_expensive_queries`.
Unexpected runtime script failures fail the query. Scripts can increase
Elasticsearch CPU usage, especially for large arrays; test the workload before
enabling the option. Disabling it restores native queries and array prefiltering
without scripts.

### Scalar expression scripts

Scalar `WHERE` expressions use the same typed expression compiler as array
lambdas. Scalar script pushdown is enabled by default and independent of
`array_script_pushdown_enabled`. To disable it, set
`elasticsearch.scalar-script-pushdown.enabled=false` in the catalog, or set the
`scalar_script_pushdown_enabled` catalog session property to `false`.
Native predicates are preferred and do not require either script option.

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

The supported arithmetic, casts, nullable expressions, math functions, strings,
and UTC timestamps follow the array expression allowlist above. Each compiled
scalar expression refers to one column, with any number of references to that
column. Separate predicates on different columns can be composed; an arithmetic
expression combining two different columns remains local. Native scalar regexp
predicates keep their existing full-text mode and residual policy.

Scalar NULL semantics are evaluated directly, including a missing field. For
example, `coalesce(number, 100) > 10` can be exact and include NULL rows; the
corresponding array lambda is ineligible because NULL array elements are absent
from doc values. Floating-point domains extracted by Trino can also use scripts
to preserve signed-zero and explicit NaN membership semantics.

Scripts require the same source-equivalent mapping proof and doc values as the
array compiler: direct exact `keyword` strings and eligible numeric, boolean,
and date mappings. An analyzed text field alone, a lossy keyword sub-field,
`ignore_above`, or a normalizer does not become eligible by enabling scripts.
The field is read as one scalar value; unexpected multiple doc values fail the
query rather than selecting an arbitrary element.

Non-throwing expressions can be exact. Fallible operations and Unicode case
conversion remain prefilters with the original SQL predicate retained. Thus
`lower(raw_review_comment) LIKE '%sá%'` can filter documents in Elasticsearch
when the column is eligible, but non-ASCII strings still require Trino's Unicode
case conversion and LIKE check. LIMIT remains above this residual filter; a
remote candidate count must not be mistaken for the requested SQL result count.

With `full_text_pushdown_mode=UNSAFE`, scalar predicates of the form
`lower(keyword_column) LIKE constant_pattern` or
`upper(keyword_column) LIKE constant_pattern` instead use a native Elasticsearch
`regexp` query with `case_insensitive=true`. Constant `ESCAPE` clauses are
supported. This path requires an indexed, source-equivalent direct `keyword`
mapping and is preferred even when scalar scripts are disabled. An analyzed
text subfield does not change the target: the query reads the keyword parent.
It requires Elasticsearch 7.10 or later.

These predicates are marked `APPROXIMATE` and do not retain the SQL residual.
Elasticsearch case matching can differ from Trino's `lower` or `upper`, including
for Unicode and uppercase letters in the pattern. `SAFE` and `DISABLED` retain
the existing scalar script or local evaluation behavior. Plain `column LIKE
pattern` predicates retain their existing case-sensitive native translation.

### Prefix predicate push down

Predicates that match a string prefix are pushed down as an Elasticsearch
`prefix` query on `keyword` fields, or on a `text` field's exact-match `keyword`
sub-field. This applies to `col LIKE 'abc%'`, `starts_with(col, 'abc')`, and
`substr(col, 1, n) = 'abc'` when `n` equals the character length of the literal.
Other `LIKE` patterns are pushed down as a `regexp` query.

### Full-text pushdown

By default, predicates and dynamic filters on analyzed `text` fields — fields
without an exact-match `keyword` sub-field — are evaluated by the engine,
because a `text` field is tokenized and does not preserve exact string values.

The `elasticsearch.full-text-pushdown` configuration property, or the
`full_text_pushdown_mode` session property, selects the treatment of analyzed
text predicates:

- `DISABLED` (default) keeps unproven analyzed-text expressions in Trino.
- `SAFE` uses only exact translations or candidates proven not to lose SQL
  matches. Analyzed-text candidates without that proof remain local.
- `UNSAFE` accepts explicitly approximate Elasticsearch semantics. Translated
  approximate predicates do not retain their original exact SQL residual.
  Tokenization, case conversion, stemming, stop words and punctuation can change
  results. Diagnostics report these predicates as `APPROXIMATE`, not `EXACT`.

UNSAFE text routing applies to scalar text columns and `any_match` over text
arrays. Native full-text queries are preferred when a translation exists.
Literal and contains-literal LIKE patterns, including multi-token patterns,
use `match_phrase`; prefixes use `match_phrase_prefix`. Supported other patterns
use Lucene `regexp` queries over tokens. LOWER/UPPER around a field can use this
native path: the field's analyzer defines the case/normalization behavior, which
is intentionally not a promise to execute Trino's string function exactly.

For example, replace `elasticsearch` with the actual catalog name:

```sql
SET SESSION elasticsearch.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM reviews
WHERE lower(raw_review_comment) LIKE '%sách rất%';
```

This shape uses a native `match_phrase` query on the analyzed parent text field,
including when an optional keyword subfield drops long values. It does not need
Painless or an exact SQL residual. It can match both `SÁCH rất tốt` and
`SÁCH, rất tốt`, according to the configured analyzer.

For text expressions without a native translation, enable the corresponding
`scalar_script_pushdown_enabled` or `array_script_pushdown_enabled` session
property to allow a runtime-field fallback. These flags remain independent:
scalar scripts default to true, and array scripts default to false. The fallback
reads the complete value from `_source`; it does not treat analyzed tokens as the
original string. Runtime definitions exist only
in the search request, so no reindexing or persistent mapping change is required.
This requires `_source` to be available and the cluster to permit runtime scripts.

The fallback reuses the typed expression compiler for string functions such as
length, trim/ltrim/rtrim, substr/substring, replace, concat, strpos, ends_with,
nullable/CASE expressions, and their supported compositions. Scalar numeric
source values are converted to strings like the VARCHAR decoder. Source arrays
preserve NULL elements and evaluate the complete lambda on each element.
Unsupported functions/overloads and expressions over multiple captured columns
still follow the normal fallback rules.

In non-fallible source expressions, UNSAFE LOWER/UPPER can use Elasticsearch's
Unicode case behavior and are marked APPROXIMATE. Fallible expressions keep a
lossless candidate and the original residual, including the existing conservative
Unicode case guard, rather than silently suppressing SQL errors. Other proven
source expressions may be exact. Turning on UNSAFE does not disable all residuals
or error checks in the query.

Runtime definitions are carried through Scroll, PIT/search_after, COUNT and
supported aggregation requests. Reading `_source` and evaluating scripts costs
more than an indexed native predicate: fewer documents transferred to Trino does
not imply that Elasticsearch avoided scanning or parsing candidate documents.

Dynamic filters on `text` join keys are always safe to push, because the join
re-checks the key. They are pushed as `match_phrase` pre-filters whenever the
mode is not `DISABLED`.

### Aggregation pushdown

The connector supports [aggregation pushdown](aggregation-pushdown) for global
and `GROUP BY` queries. A pushed-down aggregation is computed by Elasticsearch
with a [composite aggregation](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-composite-aggregation.html),
so Trino does not read the underlying documents.

The following aggregate functions are pushed down:

- `count(*)` and `count(column)`
- `avg` over `tinyint`, `smallint`, `integer`, `bigint`, `real`, and `double`
- `sum` over `real` and `double`
- `min` and `max` over numeric columns except `bigint`
- `approx_distinct`

Integer `avg` returns `double`. Both Trino and Elasticsearch accumulate integer
averages using floating-point arithmetic; shard reduction and summation order can
produce small differences. Pushdown avoids transferring and decoding source
documents for the aggregate. Integral `sum` and `bigint` `min`/`max` remain in Trino.

`count(DISTINCT ...)`, filtered aggregates, and ordered aggregates are not pushed
down. Grouping keys can be `boolean`, integral, `keyword`, or `timestamp(3)` from
the default Elasticsearch `date` mapping. Floating-point TopN and grouping stay
in Trino because Elasticsearch distinguishes positive and negative zero.

Pushdown requires compatible, source-equivalent mappings in every backing index.
Fields affected by source filters, arrays, runtime definitions, scripts,
normalization, ignored values, or unsafe ancestors remain in Trino. Text keyword
multi-fields are conservatively excluded because existing documents might not
have been backfilled. Timeouts, partial shard results, and inexact global hit
counts fail the aggregation rather than returning incomplete results.

Aggregation pushdown is enabled by default. Disable it with the
`elasticsearch.aggregation-pushdown.enabled` catalog configuration property or
the `aggregation_pushdown_enabled` catalog session property.

### Table statistics

When `elasticsearch.statistics.enabled` is `true`, the default, the connector
exposes [table statistics](/optimizer/statistics) computed from Elasticsearch
aggregations: row count, number of distinct values, null fraction, and value
ranges for numeric and date columns. The cost-based optimizer uses these
statistics to choose more efficient query plans, such as the join distribution
type and join order.

The row count is always collected with a cheap request. The per-column statistics
(distinct values, null fractions, and ranges) require an aggregation that scans
every document, so the connector limits that work in three ways. Any statistics it
skips only affect plan quality, never correctness:

- It collects per-column statistics only for the columns a query references in a
  `WHERE`, `GROUP BY`, or `ORDER BY` clause — the columns the optimizer actually
  consults. Columns that appear only in the `SELECT` list, and join keys (joins are
  not pushed to the connector), are not aggregated.
  `elasticsearch.statistics.max-statistics-columns` caps how many referenced columns
  are collected.
- It collects per-column statistics only when the filtered document count is at
  most `elasticsearch.statistics.max-index-documents`. Larger result sets get the
  row count only.
- Each statistics request uses a short timeout,
  `elasticsearch.statistics.request-timeout`, with no retry, so a slow aggregation
  falls back to the row count in seconds instead of blocking query planning.

Statistics collection is therefore automatic — you generally do not need to change
your queries. For very large or slow indices, lower
`elasticsearch.statistics.max-index-documents` or
`elasticsearch.statistics.request-timeout` to skip expensive aggregations sooner,
raise `elasticsearch.statistics.max-statistics-columns` if queries filter or group by
many columns, or set `elasticsearch.statistics.enabled` to `false` to disable
statistics collection entirely.

Statistics requests include the table handle's exact remote predicates. Candidate
and approximate predicates return unknown statistics, as do aggregated handles.
Pushed limits bound the row estimate; per-column statistics are not inferred for
the limited subset. Timed-out and partial-shard responses are not used as exact
statistics.

### Search execution

Scroll and PIT share scan accounting, limit termination, failure cleanup and
idempotent close. PIT uses a shard-restricted context, stable `search_after` sort
tokens and the latest returned context ID. An expired or failed context fails the
query; the connector does not reopen it or switch strategies after returning rows.
Both strategies close remote contexts at observed exhaustion, a pushed limit, or
downstream close. The existing scroll size and timeout configure both strategies.

Scroll remains the default compatibility path. Evaluate PIT and streaming decoding
on representative workloads before enabling them. Small local measurements do not
establish a universal throughput or memory improvement.

### Dynamic filtering

The connector supports [dynamic filtering](/admin/dynamic-filtering). Dynamic
filters generated on the build side of a join, such as join keys, are folded
into the Elasticsearch query to reduce the number of scanned documents. The
`elasticsearch.dynamic-filtering.wait-timeout` configuration property, or the
`dynamic_filtering_wait_timeout` session property, controls how long split
generation waits for dynamic filters to be collected.

### Pushdown diagnostics

The connector exports `ElasticsearchPushdownDiagnostics` through JMX for each
catalog on each Trino node. Counters are cumulative for the lifetime of the
connector. Coordinator planning and worker execution are separate observations;
they are not per-query totals. Snapshots taken while queries run can include
concurrent updates.

- `TranslationReasonCounts` and `NormalizationCounts` report structured planner
  decisions, including exact, prefilter, approximate, residual, flattening, and
  terms compaction decisions. Translation counts include child decisions and
  repeated planner invocations; they do not count distinct SQL predicates.
- `DynamicFilterOutcomes` distinguishes unrestricted, empty, pushed, partially
  pushed, and rejected filters. Value counters count discrete values, not the
  cardinality of ranges. `DynamicFilterTermsBatches` counts native `terms` nodes;
  a single-value `term` is not a terms batch. Request-byte estimates cover accepted
  filter clauses and exclude the enclosing request.
- `RenderedQueries` and `RenderedQueryBytes` count successfully constructed scan,
  count, and aggregation filters and their UTF-8 JSON bytes. Remote predicate node
  counters describe the IR rendered in those filters. These are not HTTP request
  bytes or unique-query counts.
- `SearchRequests`, `NextPageRequests`, and `CountRequests` count logical scan/count
  attempts, including failed attempts. `RetryAttempts` counts backpressure retries
  across the connector's REST client. `RemotePagesReceived` counts successful scan
  responses, including empty terminal responses; `PagesReturned` counts Trino
  scan/count output pages. `RowsDecoded` and `SourceBytesDecoded` cover decoded
  scan hits, not count results or network traffic.
- `PointInTimeOpens` and `PointInTimeCloses` count PIT context attempts;
  `AggregationRequests` and `StatisticsRequests` count logical attempts in those
  paths. Aggregation responses and output pages also contribute to page counters.
  `AggregationRows` and `AggregationOutputBytes` count produced rows and their
  decoded Trino block bytes, not network bytes or source documents.
  Statistics failures contribute to `Failures` even when planning safely falls
  back to unknown statistics.
- `Cancellations` counts downstream scan close before observed exhaustion or a
  pushed limit. It does not establish that a user cancelled a query. `Failures`
  counts scan/count failures and failed scroll cleanup; one failed scan can also
  have a separate cleanup failure. `ClearScrollCalls` counts cleanup attempts.

Enable debug logging for
`io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics` to see planner,
normalization, dynamic-filter, and rendering observations from the same model.
These diagnostic events omit predicate values. Enabling logging does not change
pushdown or residual decisions. Aggregation early close also contributes to
`Cancellations`; failed PIT cleanup contributes to `Failures`.

[built-in date formats]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#built-in-date-formats
[custom date formats]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#custom-date-formats
[date]: https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html
[format]: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-date-format.html#mapping-date-format
[full text query]: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#query-string-syntax
