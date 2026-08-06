# Work Item 11 — Typed Request Planner and HTTP Serialization

## Goal

Convert a final `RestTableHandle` plus one `RestSplit` into a deterministic, sanitized, executable HTTP request plan. The same planner must support runtime execution and SQL-to-request preview.

## Dependencies

Work Items 06–10.

## Files

```text
io.trino.plugin.rest.request.RestRequestPlanner
io.trino.plugin.rest.request.RestRequestPlan
io.trino.plugin.rest.request.RestRequestTemplate
io.trino.plugin.rest.request.RestRequestContext
io.trino.plugin.rest.request.PathTemplateExpander
io.trino.plugin.rest.request.QueryParameterSerializer
io.trino.plugin.rest.request.HeaderParameterSerializer
io.trino.plugin.rest.request.JsonBodyWriter
io.trino.plugin.rest.request.ParameterEncoding
io.trino.plugin.rest.request.SanitizedRequestPlan
io.trino.plugin.rest.request.RequestPlanningException
src/test/java/io/trino/plugin/rest/request/TestRestRequestPlanner.java
src/test/java/io/trino/plugin/rest/request/TestParameterSerialization.java
```

## Request plan model

```java
public record RestRequestPlan(
        HttpMethod method,
        URI uri,
        ImmutableListMultimap<String, String> businessHeaders,
        Optional<byte[]> body,
        Optional<String> contentType,
        String operationId,
        RequestSafety safety,
        RestRequestAccountingEstimate accounting) {}
```

Do not store auth headers in the plan returned by the pure planner. Authentication is applied by Work Item 14 immediately before transport. For large JSON bodies, prefer a streaming body generator/immutable typed body tree over copying byte arrays repeatedly; exact HTTP client integration decides final representation.

## Inputs

```java
public RestRequestPlan plan(
        RestTableModel table,
        RestTableHandle handle,
        RestSplit split,
        PaginationState paginationState,
        RestRequestContext context);
```

Context includes:

- configured base URI;
- safe catalog/session settings already bounded;
- query ID for diagnostics/accounting;
- preview/runtime mode;
- current time only when explicitly needed for request deadline, not for semantic values;
- no secret.

## Determinism

For equal semantic inputs, planner output before auth must be equal:

- path placeholder order fixed by template;
- query parameters sorted by contract/request-field ordinal, not hash-map order;
- repeated values canonicalized where order is semantically irrelevant;
- JSON object field order deterministic;
- projection/sort lists retain SQL/contract-defined order;
- locale-independent number/time formatting.

Determinism enables handle equality tests, previews, request de-duplication and reproducible diagnostics.

## Request construction phases

```text
1. Resolve effective exact/approximate/system/split bindings
2. Validate required request fields
3. Encode typed values
4. Expand relative path template
5. Serialize query parameters
6. Serialize allowlisted business headers
7. Build JSON body for POST/body fields
8. Apply projection/sort/limit/pagination system values
9. Resolve URI against fixed base URI
10. Run network-policy validation on final URI
11. Calculate accounting estimate
12. Produce executable and sanitized plans
```

Authentication occurs after phase 12 in transport pipeline.

## Binding precedence

Define one precedence and reject conflicts:

1. contract constants;
2. approved session-sourced values;
3. exact/approximate SQL predicate bindings;
4. split partition bindings;
5. system bindings (limit/projection/sort/pagination).

This is not arbitrary overwrite order. Each request field has one source role or a documented merge policy. If two components attempt incompatible values for one scalar field, fail planning.

For range lower/upper fields, separate request fields avoid conflict. For list fields, merge only when semantics explicitly allow intersection/union and define which.

## Path expansion

`PathTemplateExpander` rules:

- every `{placeholder}` has exactly one scalar value;
- reject missing/multiple/null values;
- percent-encode as one path segment using UTF-8;
- encoded slash remains `%2F`; never permit path traversal;
- reject `.`/`..` traversal after decoding/normalization;
- result remains a relative path under configured base path;
- placeholder names and bindings were prevalidated by compiler.

Do not use naive `String.replace`.

## Query serialization

Support explicit encodings:

```text
SCALAR
REPEATED         ?id=1&id=2
CSV              ?id=1,2
SSV/PIPES        only when explicit
JSON_STRING      only when explicit
```

Rules:

- parameter names come from contract, not SQL;
- values percent-encoded independently;
- `allowReserved` equivalent is disabled unless contract/security review enables narrowly;
- omit field only under explicit null/optional policy;
- URL length is estimated/limited; large batches switch to body only if the table operation supports it, otherwise batch smaller/fail;
- query parameters marked sensitive are not supported in MVP for auth and are redacted in preview/logs if business-sensitive.

## Headers

Only compiled allowlisted business headers can be produced. Reject:

```text
Authorization
Proxy-Authorization
Cookie
Set-Cookie
Host
Connection
Transfer-Encoding
Content-Length
Forwarded
X-Forwarded-*
```

Header names/values:

- validate token syntax;
- reject CR/LF/NUL;
- enforce maximum length/count;
- do not allow SQL user to overwrite trace/auth/content framing headers.

## JSON body writer

For read-only POST:

- content type must be JSON-compatible and explicit;
- build nested object structure from JSON Pointers;
- reject pointer collisions (`/a` scalar and `/a/b` field);
- write typed values through codecs;
- omit optional absent fields;
- preserve explicit JSON null;
- arrays honor value order only when semantics require it, otherwise canonical order can be used for deterministic previews;
- enforce request body size before sending;
- no arbitrary user JSON fragment interpolation.

A compiled pointer trie should write body fields without repeated parsing of pointer strings.

## System bindings

### Limit/page size

Use effective handle limit and pagination maximum. Per-request size:

```text
min(remaining query limit when useful, remote max page size, contract max, catalog/session cap)
```

When limit is not guaranteed, do not stop request planning in a way that can underproduce final SQL rows after residual filtering.

### Projection

Serialize remote field names and automatically include mandatory fields. Enforce maximum fields and request length/body size.

### Sort

Serialize sort items in order using contract pattern, allowlist and direction mapping. Never accept arbitrary SQL column text as remote parameter.

### Pagination

Pagination state supplies page/offset/cursor/next URI values. Next-link strategy may provide a validated continuation URI that bypasses normal path/query assembly only under Work Items 12 and 14 policy.

## Request safety classification

```java
public enum RequestSafety
{
    SAFE_GET,
    IDEMPOTENT_READ_POST,
    NON_RETRYABLE_READ_POST
}
```

POST is retryable only when compiled operation explicitly declares read-only/idempotent semantics. Planner records safety; retry layer enforces it.

## Preview

```java
public SanitizedRequestPlan sanitize(RestRequestPlan plan)
```

Preview contains:

- method;
- path template/final safe URI with sensitive values redacted;
- business headers with redaction;
- JSON body with sensitive paths redacted;
- pushed exact/approximate/residual summaries;
- projection/sort/limit/pagination state;
- estimated requests/splits;
- no auth material;
- no remote call.

Redaction is schema-driven, not regex-only. Values bound from sensitive columns/session properties are replaced with typed markers.

## Tests

- GET path/query request;
- read-only POST nested body;
- path encoding and traversal rejection;
- all query array encodings;
- header injection rejection;
- pointer collision;
- typed numbers/booleans/timestamps/nulls;
- projection/sort/limit/system values;
- split + filter binding merge;
- conflicting scalar bindings;
- deterministic ordering under shuffled maps;
- request body/URL/header limits;
- preview redaction;
- no auth header in pure plan;
- next-link integration boundary;
- plan equality and JSON/body bytes stable.

## Acceptance criteria

- Runtime and preview use the same pure planner.
- No string concatenation is used for path/query/body semantics.
- Final URI cannot escape configured origin/base path through ordinary bindings.
- Headers/body are injection-safe and bounded.
- Auth remains outside request plan.
- Equal inputs create deterministic plans.
- Preview performs no network access and exposes no sensitive values.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestRequestPlanner,TestParameterSerialization' test
./mvnw -pl plugin/trino-rest airstyle:check
```