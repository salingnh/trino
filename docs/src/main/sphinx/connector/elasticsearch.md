# Elasticsearch connector

```{raw} html
<img src="../_static/img/elasticsearch.png" class="connector-logo">
```

The Elasticsearch connector allows access to
[Elasticsearch](https://www.elastic.co/products/elasticsearch) data from Trino.
This document describes how to configure a catalog with the Elasticsearch
connector to run SQL queries against Elasticsearch.

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

:::{list-table} Elasticsearch configuration properties
:widths: 35, 55, 10
:header-rows: 1

* - Property name
  - Description
  - Default
* - `elasticsearch.host`
  - The comma-separated list of host names for the Elasticsearch node to connect
    to. This property is required.
  -
* - `elasticsearch.port`
  - Port to use to connect to Elasticsearch.
  - `9200`
* - `elasticsearch.default-schema-name`
  - The schema that contains all tables defined without a qualifying schema
    name.
  - `default`
* - `elasticsearch.scroll-size`
  - Sets the maximum number of hits that can be returned with each
    [Elasticsearch scroll
    request](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html#scroll-search-context).
  - `1000`
* - `elasticsearch.search-strategy`
  - Pagination strategy: `SCROLL` or opt-in `PIT`. PIT requires Elasticsearch
    7.17 or 8.x and permission to open and close point-in-time contexts.
  - `SCROLL`
* - `elasticsearch.response-decoder`
  - Search response decoder: `MATERIALIZED` or opt-in `STREAMING`. Streaming
    materializes one hit tree at a time, independently of pagination strategy.
  - `MATERIALIZED`
* - `elasticsearch.scroll-timeout`
  - [Duration](prop-type-duration) for Elasticsearch to keep the search context
    alive for scroll requests.
  - `1m`
* - `elasticsearch.request-timeout`
  - Timeout [duration](prop-type-duration) for all Elasticsearch requests.
  - `10s`
* - `elasticsearch.connect-timeout`
  - Timeout [duration](prop-type-duration) for all Elasticsearch connection
    attempts.
  - `1s`
* - `elasticsearch.backoff-init-delay`
  - The minimum [duration](prop-type-duration) between backpressure retry
    attempts for a single request to Elasticsearch. Setting it too low can
    overwhelm an already struggling cluster.
  - `500ms`
* - `elasticsearch.backoff-max-delay`
  - The maximum [duration](prop-type-duration) between backpressure retry
    attempts for a single request to Elasticsearch.
  - `20s`
* - `elasticsearch.max-retry-time`
  - The maximum [duration](prop-type-duration) across all retry attempts for a
    single request to Elasticsearch.
  - `30s`
* - `elasticsearch.node-refresh-interval`
  - [Duration](prop-type-duration) between requests to refresh the list of
    available Elasticsearch nodes.
  - `1m`
* - `elasticsearch.statistics.enabled`
  - Enable collecting table statistics by querying Elasticsearch aggregations
    (document count, distinct values, null fractions, and min/max ranges) so the
    cost-based optimizer can better plan joins and other operations.
  - `true`
* - `elasticsearch.statistics.max-index-documents`
  - Collect per-column statistics only for indices with at most this many
    documents. Larger indices report the row count only, avoiding a per-column
    aggregation over every document that can time out during planning.
  - `20000000`
* - `elasticsearch.statistics.max-statistics-columns`
  - Maximum number of columns to collect per-column statistics for. Statistics are
    collected only for columns the query references (filter, `GROUP BY`, and sort
    columns); this bounds that set.
  - `32`
* - `elasticsearch.statistics.request-timeout`
  - Socket timeout for a single per-column statistics request. A slow aggregation
    fails fast, with no retry, and falls back to the row count instead of blocking
    query planning.
  - `5s`
* - `elasticsearch.dynamic-filtering.wait-timeout`
  - Maximum [duration](prop-type-duration) to wait for the collection of dynamic
    filters, such as join keys, before generating splits. Dynamic filters are
    applied within the Elasticsearch query to reduce the number of scanned
    documents. Can be overridden per query with the
    `dynamic_filtering_wait_timeout` session property.
  - `5s`
* - `elasticsearch.keyword-subfield-pushdown-with-ignore-above`
  - Push predicates and aggregations for a `text` field down to its exact-match
    `keyword` sub-field even when that sub-field defines `ignore_above`. This is
    disabled by default because `ignore_above` omits longer values from the
    sub-field index, so pushed-down predicates could return incomplete results.
    Enable it only when all indexed string values are shorter than the
    `ignore_above` limit. Can be overridden per query with the
    `keyword_subfield_pushdown_with_ignore_above` session property.
  - `false`
* - `elasticsearch.aggregation-page-size`
  - Maximum composite aggregation buckets per response. Must be at least 1.
    Larger pages reduce requests but can increase memory per response.
  - `10000`
* - `elasticsearch.aggregation-pushdown.enabled`
  - Enable pushing down aggregations to Elasticsearch. Can be overridden per
    query with the `aggregation_pushdown_enabled` session property.
  - `true`
* - `elasticsearch.scalar-script-pushdown.enabled`
  - Enable Painless pushdown for supported scalar expressions on source-equivalent
    fields with doc values. Can be overridden with the
    `scalar_script_pushdown_enabled` session property.
  - `false`
* - `elasticsearch.full-text-pushdown`
  - Push predicates and dynamic filters on analyzed `text` fields to
    Elasticsearch as full-text queries. One of `DISABLED`, `SAFE`, or `UNSAFE`.
    A `text` field is tokenized, so this does not have exact SQL semantics. Can
    be overridden per query with the `full_text_pushdown_mode` session property.
  - `DISABLED`
* - `elasticsearch.ignore-publish-address`
  - Disable using the address published by the Elasticsearch API to connect for
    queries. Some deployments map Elasticsearch ports to a random public port
    and enabling this property can help in these cases.
  - `false`
:::

### Authentication

The connection to Elasticsearch can use AWS or password authentication.

To enable AWS authentication and authorization using IAM policies, the
`elasticsearch.security` option must be set to `AWS`. Additionally, the
following options must be configured:

:::{list-table}
:widths: 40, 60
:header-rows: 1

* - Property name
  - Description
* - `elasticsearch.aws.region`
  - AWS region of the Elasticsearch endpoint. This option is required.
* - `elasticsearch.aws.access-key`
  - AWS access key to use to connect to the Elasticsearch domain. If not set, the
    default AWS credentials provider chain is used.
* - `elasticsearch.aws.secret-key`
  - AWS secret key to use to connect to the Elasticsearch domain. If not set, the
    default AWS credentials provider chain is used.
* - `elasticsearch.aws.iam-role`
  - Optional ARN of an IAM role to assume to connect to Elasticsearch. Note that
    the configured IAM user must be able to assume this role.
* - `elasticsearch.aws.external-id`
  - Optional external ID to pass while assuming an AWS IAM role.
:::

To enable password authentication, the `elasticsearch.security` option must be set
to `PASSWORD`. Additionally the following options must be configured:

:::{list-table}
:widths: 45, 55
:header-rows: 1

* - Property name
  - Description
* - `elasticsearch.auth.user`
  - Username to use to connect to Elasticsearch.
* - `elasticsearch.auth.password`
  - Password to use to connect to Elasticsearch.
:::

### Connection security with TLS

The connector provides additional security options to connect to Elasticsearch
clusters with TLS enabled.

If your cluster has globally-trusted certificates, you should only need to
enable TLS. If you require custom configuration for certificates, the connector
supports key stores and trust stores in P12 (PKCS) or Java Key Store (JKS) format.

The available configuration values are listed in the following table:

:::{list-table} TLS Security Properties
:widths: 40, 60
:header-rows: 1

* - Property name
  - Description
* - `elasticsearch.tls.enabled`
  - Enables TLS security.
* - `elasticsearch.tls.keystore-path`
  - The path to the P12 (PKCS) or [JKS](/security/inspect-jks)
    key store.
* - `elasticsearch.tls.truststore-path`
  - The path to P12 (PKCS) or [JKS](/security/inspect-jks)
    trust store.
* - `elasticsearch.tls.keystore-password`
  - The key password for the key store specified by
    `elasticsearch.tls.keystore-path`.
* - `elasticsearch.tls.truststore-password`
  - The key password for the trust store specified by
    `elasticsearch.tls.truststore-path`.
* - `elasticsearch.tls.verify-hostnames`
  - Flag to determine if the hostnames in the certificates must be verified. Defaults
    to `true`.
:::

(elasticsearch-type-mapping)=
## Type mapping

Because Trino and Elasticsearch each support types that the other does not, this
connector {ref}`maps some types <type-mapping-overview>` when reading data.

### Elasticsearch type to Trino type mapping

The connector maps Elasticsearch types to the corresponding Trino types
according to the following table:

:::{list-table} Elasticsearch type to Trino type mapping
:widths: 30, 30, 50
:header-rows: 1

* - Elasticsearch type
  - Trino type
  - Notes
* - `BOOLEAN`
  - `BOOLEAN`
  -
* - `DOUBLE`
  - `DOUBLE`
  -
* - `FLOAT`
  - `REAL`
  -
* - `BYTE`
  - `TINYINT`
  -
* - `SHORT`
  - `SMALLINT`
  -
* - `INTEGER`
  - `INTEGER`
  -
* - `LONG`
  - `BIGINT`
  -
* - `KEYWORD`
  - `VARCHAR`
  -
* - `TEXT`
  - `VARCHAR`
  -
* - `DATE`
  - `TIMESTAMP`
  - For more information, see [](elasticsearch-date-types).
* - `IPADDRESS`
  - `IP`
  -
:::

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
    "array_string_field": ["trino","the","lean","machine-ohs"],
    "long_field": 314159265359,
    "id_field": "564e6982-88ee-4498-aa98-df9e3f6b6109",
    "timestamp_field": "2025-09-17T06:22:48.000Z",
    "object_field": {
        "array_int_field": [86,75,309],
        "int_field": 2
    }
}
```

The array fields of this structure can be defined by using the following command to add the field
property definition to the `_meta.trino` property of the target index mapping with Elasticsearch available at `search.example.com:9200`:

```shell
curl --request PUT \
    --url search.example.com:9200/doc/_mapping \
    --header 'content-type: application/json' \
    --data '
{
    "_meta": {
        "trino":{
            "array_string_field":{
                "isArray":true
            },
            "object_field":{
                "array_int_field":{
                    "isArray":true
                }
            },
        }
    }
}'
```

:::{note}
It is not allowed to use `asRawJson` and `isArray` flags simultaneously for the same column.
:::

(elasticsearch-date-types)=
### Date types

The Elasticsearch connector supports only the default `date` type. All other
[date] formats including [built-in date formats] and [custom date formats] are
not supported. Dates with the [format] property are ignored.

### Raw JSON transform

Documents in Elasticsearch can include more complex structures that are not
represented in the mapping. For example, a single `keyword` field can have
widely different content including a single `keyword` value, an array, or a
multidimensional `keyword` array with any level of nesting.

The following command configures `array_string_field` mapping with Elasticsearch
available at `search.example.com:9200`:

```shell
curl --request PUT \
    --url search.example.com:9200/doc/_mapping \
    --header 'content-type: application/json' \
    --data '
{
    "properties": {
        "array_string_field":{
            "type": "keyword"
        }
    }
}'
```

All the following documents are legal for Elasticsearch with
`array_string_field` mapping:

```json
[
    {
        "array_string_field": "trino"
    },
    {
        "array_string_field": ["trino","is","the","best"]
    },
    {
        "array_string_field": ["trino",["is","the","best"]]
    },
    {
        "array_string_field": ["trino",["is",["the","best"]]]
    }
]
```

See the [Elasticsearch array
documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/array.html)
for more details.

Further, Elasticsearch supports types, such as
[dense_vector](https://www.elastic.co/guide/en/elasticsearch/reference/current/dense-vector.html),
that are not supported in Trino. These and other types can cause parsing
exceptions for users that use of these types in Elasticsearch. To manage all of
these scenarios, you can transform fields to raw JSON by annotating it in a
Trino-specific structure in the
[\_meta](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-meta-field.html)
section of the index mapping. This indicates to Trino that the field, and all
nested fields beneath, need to be cast to a `VARCHAR` field that contains the
raw JSON content. These fields can be defined by using the following command to
add the field property definition to the `_meta.trino` property of the target
index mapping.

```shell
curl --request PUT \
    --url search.example.com:9200/doc/_mapping \
    --header 'content-type: application/json' \
    --data '
{
    "_meta": {
        "trino":{
            "array_string_field":{
                "asRawJson":true
            }
        }
    }
}'
```

This preceding configuration causes Trino to return the `array_string_field`
field as a `VARCHAR` containing raw JSON. You can parse these fields with the
[built-in JSON functions](/functions/json).

:::{note}
It is not allowed to use `asRawJson` and `isArray` flags simultaneously for the same column.
:::

## Special columns

The following hidden columns are available:

:::{list-table}
:widths: 25, 75
:header-rows: 1

* - Column
  - Description
* - `_id`
  - The Elasticsearch document ID.
* - `_score`
  - The document score returned by the Elasticsearch query.
* - `_source`
  - The source of the original document.
:::


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

:::{list-table}
:widths: 50, 50
:header-rows: 1

* - Elasticsearch
  - Trino
* - `boolean`
  - `BOOLEAN`
* - `double`
  - `DOUBLE`
* - `float`
  - `REAL`
* - `byte`
  - `TINYINT`
* - `short`
  - `SMALLINT`
* - `integer`
  - `INTEGER`
* - `long`
  - `BIGINT`
* - `keyword`
  - `VARCHAR`
* - `date`
  - `TIMESTAMP`
:::

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
lambdas. Enable `elasticsearch.scalar-script-pushdown.enabled=true` in the catalog,
or set the `scalar_script_pushdown_enabled` catalog session property to `true`.
It is disabled by default and independent of `array_script_pushdown_enabled`.
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
`full_text_pushdown_mode` session property, opts in to pushing these predicates
down as Elasticsearch full-text queries. Equality and `IN` predicates become a
`match_phrase` query, a `LIKE` prefix pattern (`abc%`) becomes a
`match_phrase_prefix` query, and other `LIKE` patterns and `regexp_like` become a
`regexp` query, which Elasticsearch evaluates per token using Lucene regular
expression syntax. Because tokenization, lowercasing, and stemming apply, results
can differ from exact SQL semantics. Three modes are available:

- `DISABLED`, the default: text predicates are evaluated by the engine only.
- `SAFE`: push a full-text pre-filter but re-apply the exact predicate in the
  engine, which removes false positives. This is *not* lossless: because the
  pre-filter is not a guaranteed superset, rows can still be dropped — for
  example a value that analyzes to nothing, such as a stop word, or a
  `regexp_like` pattern that matches across analyzer token boundaries, because
  Elasticsearch matches per token rather than across the whole string. A
  multi-token `LIKE` prefix is pushed as a `match_phrase_prefix` pre-filter while
  the engine keeps the exact prefix range, so its result stays correct; a
  multi-token contains pattern is left to the engine.
- `UNSAFE`: push the full-text query and trust the Elasticsearch result. Fully
  pushed down and fastest, but results follow Elasticsearch analysis semantics.

Both `SAFE` and `UNSAFE` are therefore approximate on analyzed `text`; only a
predicate on a field with an exact-match `keyword` sub-field is exact.

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
