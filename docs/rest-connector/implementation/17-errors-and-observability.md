# Work Item 17 — Connector Error Codes, Diagnostics, Metrics, and Tracing

## Goal

Give SQL users and operators actionable, sanitized failures and low-cardinality observability across planning, registry loading, HTTP execution, pagination, decoding, and budgets.

## Dependencies

All previous runtime work items.

## Files

```text
io.trino.plugin.rest.RestErrorCode
io.trino.plugin.rest.RestExceptionFactory
io.trino.plugin.rest.diagnostic.RestDiagnosticContext
io.trino.plugin.rest.diagnostic.RemoteErrorExtractor
io.trino.plugin.rest.diagnostic.SanitizedRemoteFailure
io.trino.plugin.rest.metrics.RestConnectorMetrics
io.trino.plugin.rest.metrics.RestMetricNames
io.trino.plugin.rest.metrics.PageSourceMetricSnapshot
io.trino.plugin.rest.tracing.RestTracing
io.trino.plugin.rest.tracing.RestSpanAttributes
src/test/java/io/trino/plugin/rest/TestRestErrorCode.java
src/test/java/io/trino/plugin/rest/diagnostic/TestRestDiagnostics.java
src/test/java/io/trino/plugin/rest/metrics/TestRestConnectorMetrics.java
```

## Error code namespace

Define connector-specific `ErrorCodeSupplier` enum with stable names and correct type (`USER_ERROR`, `EXTERNAL`, `INTERNAL_ERROR`, `INSUFFICIENT_RESOURCES` as appropriate under current Trino conventions).

Suggested codes:

```text
REST_INVALID_CONTRACT
REST_CONTRACT_NOT_FOUND
REST_CONTRACT_FINGERPRINT_MISMATCH
REST_STALE_CONTRACT
REST_INVALID_CONFIGURATION
REST_MISSING_REQUIRED_PREDICATE
REST_UNSUPPORTED_PREDICATE
REST_REQUEST_PLANNING_ERROR
REST_REMOTE_BAD_REQUEST
REST_REMOTE_AUTHENTICATION_FAILED
REST_REMOTE_PERMISSION_DENIED
REST_REMOTE_NOT_FOUND
REST_REMOTE_THROTTLED
REST_REMOTE_UNAVAILABLE
REST_REMOTE_TIMEOUT
REST_TLS_ERROR
REST_UNSAFE_REMOTE_URI
REST_INVALID_REMOTE_RESPONSE
REST_RESPONSE_TOO_LARGE
REST_ROW_TOO_LARGE
REST_JSON_DEPTH_EXCEEDED
REST_VALUE_CONVERSION_ERROR
REST_PAGINATION_ERROR
REST_PAGINATION_LOOP
REST_QUERY_REQUEST_LIMIT_EXCEEDED
REST_QUERY_PAGE_LIMIT_EXCEEDED
REST_QUERY_ROW_LIMIT_EXCEEDED
REST_QUERY_BYTE_LIMIT_EXCEEDED
REST_SPLIT_LIMIT_EXCEEDED
REST_CANCELLED
```

Allocate numeric code range according to repository convention for plugin error codes. Do not reuse generic internal error for expected remote/user failures.

## Error classification

| Condition | Error category |
|---|---|
| invalid published contract | configuration/user or external registry contract error |
| missing required SQL predicate | user error |
| unsafe URI/host | user/config security error |
| remote 400/422 | user request/mapping error |
| remote 401 | external authentication failure |
| remote 403 | external permission failure |
| configured point 404 | empty result, no exception |
| unexpected 404 | remote not found/contract drift |
| 429 after retries | external throttled |
| timeout/5xx/connect | external unavailable |
| malformed JSON/schema mismatch | invalid remote response |
| type conversion mismatch | invalid remote data |
| query hard budget | user/resource limit error, never partial success |
| connector invariant violation | internal error |
| cancellation | preserve cancellation semantics rather than wrapping as unavailable |

## Diagnostic context

```java
public record RestDiagnosticContext(
        Optional<QueryId> queryId,
        String catalogName,
        Optional<SchemaTableName> table,
        Optional<String> operationId,
        Optional<ContractFingerprint> fingerprint,
        Optional<Integer> httpStatus,
        Optional<String> remoteRequestId,
        OptionalLong pageSequence,
        OptionalLong rowSequence) {}
```

Context must not contain:

- full URI with values;
- headers/body;
- predicate literals;
- cursor/next link;
- auth profile secret details.

Factory methods produce consistent messages.

## Message format

Prefer concise structure:

```text
REST request failed for crm.users (operation listUsers, status 503, request id abc): remote service unavailable after 4 attempts
```

For required predicate:

```text
Query on crm.users requires an exact predicate: tenant_id = <value>
```

For decode:

```text
Cannot decode crm.users.created_at at /createdAt as timestamp(3) with time zone; received JSON string with invalid format on page 2 row 41
```

No full sample by default. Optional sample must be truncated, structured-field-redacted and disabled for sensitive columns.

## Remote error extraction

If contract has safe `errorPointer`:

- read a bounded error body (small independent limit);
- validate content type;
- stream/extract only configured scalar pointer;
- enforce maximum message length;
- strip control characters;
- pass through secret redactor;
- fall back to status/reason/request ID.

Never return arbitrary HTML/full JSON error body to user.

## Retryability metadata

Central error mapper should expose retry decision input rather than infer retry from exception message.

```java
public record SanitizedRemoteFailure(
        RestErrorCode code,
        boolean retryable,
        Optional<Duration> retryAfter,
        String safeMessage,
        RestDiagnosticContext context) {}
```

Retry layer uses structured status/failure before final `TrinoException` creation.

## Metrics

Use low-cardinality labels only. Never label with query ID, URI, tenant, cursor, status text, exception message, column name where unbounded, or user input.

Required logical metrics:

```text
requests_total{catalog,operation,status_class}
request_duration_nanos{catalog,operation}
request_bytes_total{catalog,operation}
response_bytes_total{catalog,operation}
rows_decoded_total{catalog,table}
pages_total{catalog,table,pagination_type}
retries_total{catalog,operation,reason}
throttle_wait_nanos{catalog,origin_profile}
active_requests{catalog,origin_profile}
request_queue_size{catalog,origin_profile}
budget_failures_total{catalog,table,budget_type}
decode_failures_total{catalog,table,error_class}
pagination_loops_total{catalog,table}
registry_loads_total{catalog,result}
registry_load_duration_nanos{catalog}
dynamic_filter_wait_nanos{catalog,table}
dynamic_filter_reduction_total{catalog,table}
```

If Trino `Metrics` does not support labels directly, create stable metric names/objects following current connector patterns and expose aggregate dimensions through bounded metric IDs/JMX beans.

## Page source metrics

`ConnectorPageSource#getMetrics()` returns immutable snapshot. Include:

- requests;
- retries;
- remote pages;
- decoded rows;
- response bytes;
- throttle/concurrency wait;
- decode time;
- HTTP read time;
- continuation count;
- approximate-filter flag or count without values.

Same metric IDs must merge correctly across tasks.

## JMX

Expose connector-level beans only for bounded operational state:

- active/queued requests;
- rate limiter tokens/wait counters;
- OAuth token refresh success/failure counts;
- registry cache load/hit/failure;
- circuit breaker states if implemented.

Do not expose secret/profile contents or per-query unbounded beans.

## Tracing

Create spans/events only where current Trino/OpenTelemetry integration allows:

```text
rest.registry.load
rest.split.plan
rest.request
rest.response.decode
rest.pagination.advance
rest.retry.wait
```

Attributes:

- catalog;
- schema/table;
- operation ID;
- method;
- status class/code;
- attempt;
- page sequence;
- request/response byte counts;
- result/retry reason;
- fingerprint prefix.

Forbidden attributes:

- full URI/query;
- headers/body;
- SQL values;
- cursor/link;
- tenant/user identifiers unless approved and bounded;
- secret profile values.

Trace propagation to third-party APIs is configurable and default policy should consider topology leakage. Use standard W3C headers only when enabled and never allow user override.

## Logs

Logging levels:

- INFO: connector startup, contract ID/version/fingerprint prefix, model counts, shutdown;
- WARN: failed refresh while old snapshot retained, final external failures, circuit state changes, sanitized unusual conditions;
- DEBUG: sanitized request plan shape, attempts/status/timing, capability decisions;
- TRACE: avoid body/value logging; generally not needed.

Use structured parameterized logging, not eager concatenation of sensitive objects. Ensure `toString()` methods for handles/splits/request state are redacted.

## Tests

- error code uniqueness/type;
- HTTP status -> error/retry classification;
- required predicate message;
- invalid response/decode messages;
- bounded error pointer extraction;
- HTML/oversized/control-character error body;
- Authorization/API key/cookie/query/body/cursor redaction;
- no secret in exception stack/message/log capture;
- metric counts for success/retry/throttle/failure/cancel/budget;
- immutable metric snapshot;
- low-cardinality IDs under many query values;
- tracing attributes exclude forbidden values;
- registry refresh failure metrics/log;
- handle/split/request `toString()` redaction.

## Acceptance criteria

- Expected user/external failures use connector-specific categorized codes.
- No generic internal error is used for normal remote status/budget/decode failures.
- Error messages are actionable and value-redacted.
- Metrics cover requests/pages/rows/bytes/retries/throttling/decoding/budgets.
- Labels/metric IDs are bounded.
- Traces/logs contain no secret, cursor or query literal.
- Tests explicitly search captured outputs for sentinel secret values.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestErrorCode,TestRestDiagnostics,TestRestConnectorMetrics' test
./mvnw -pl plugin/trino-rest airstyle:check
```