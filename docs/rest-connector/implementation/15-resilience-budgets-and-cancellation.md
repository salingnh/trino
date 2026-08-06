# Work Item 15 — Resilience, Query Budgets, Rate Limits, and Cancellation

## Goal

Protect Trino and upstream APIs with bounded retries, worker-local rate/concurrency controls, exact query accounting, circuit breaking where justified, and prompt cancellation.

## Dependencies

Work Items 02, 11–14.

## Files

```text
io.trino.plugin.rest.resilience.RequestRetryPolicy
io.trino.plugin.rest.resilience.RetryDecision
io.trino.plugin.rest.resilience.RetryExecutor
io.trino.plugin.rest.resilience.RequestRateLimiter
io.trino.plugin.rest.resilience.HostConcurrencyLimiter
io.trino.plugin.rest.resilience.QueryBudget
io.trino.plugin.rest.resilience.QueryBudgetFactory
io.trino.plugin.rest.resilience.QueryProgress
io.trino.plugin.rest.resilience.QueryCancellation
io.trino.plugin.rest.resilience.RestCircuitBreaker
io.trino.plugin.rest.resilience.RetryAfterParser
io.trino.plugin.rest.resilience.ResilienceModule
src/test/java/io/trino/plugin/rest/resilience/TestRequestRetryPolicy.java
src/test/java/io/trino/plugin/rest/resilience/TestQueryBudget.java
src/test/java/io/trino/plugin/rest/resilience/TestRateAndConcurrencyLimits.java
src/test/java/io/trino/plugin/rest/resilience/TestCancellation.java
```

## Request accounting model

A query budget is created from effective limits:

```text
effective = minimum(catalog hard limit, contract limit, session reduction)
```

Track atomically per query/page-source/split as architecture requires:

```java
public interface QueryBudget
{
    RequestPermit beginRequest(RequestIdentity identity);

    void recordResponseHeaders(RequestPermit permit, long declaredBytes);

    void recordBytes(RequestPermit permit, long bytes);

    void recordRows(long rows);

    void recordPage();

    QueryProgress snapshot();
}
```

`RequestPermit` closes/completes exactly once and records success/failure/cancellation.

## Budget semantics

### Request budget

Count every outbound data API attempt, including retries, redirects, next links and optional count requests. OAuth token calls use a separate auth budget/limiter but still need bounds.

Fail before starting an attempt that would exceed the limit.

### Page budget

Count one successfully opened logical page response. Define behavior for retried attempts before headers; retries do not double-count logical pages but do count requests.

### Row budget

Count remote rows decoded before emission. Fail at the first row exceeding limit. Do not silently stop and return partial data.

### Byte budget

Count actual streamed response bytes after transport decompression boundary according to documented policy. Prefer counting both wire and decoded bytes as separate metrics; hard limit should cover decompressed data to prevent compression bombs.

### Split budget

Enforced before split materialization in Work Item 13.

### Per-response size

Enforce before allocation and while streaming. Content-Length above limit fails before body read; absent/incorrect length is still enforced incrementally.

## Error content

Budget exception includes:

- query ID;
- schema/table/operation;
- budget type;
- configured limit;
- used/attempted count;
- contract fingerprint prefix;
- no parameter values, URLs with query values, headers or cursor.

## Retry policy

Inputs:

```java
RetryDecision decide(
        RequestSafety safety,
        int attempt,
        Duration elapsed,
        Optional<Integer> statusCode,
        Optional<Throwable> failure,
        HttpHeaders responseHeaders);
```

### Retryable by default

- connection establishment/reset before response for `SAFE_GET`;
- same for explicitly idempotent read-only POST when body is replayable;
- 429 with bounded `Retry-After`;
- 502, 503, 504 for safe/idempotent reads;
- optional 408 under explicit policy.

### Not retryable by default

- 400/404/409/412/422;
- 401 except one token refresh path owned by auth layer;
- 403;
- malformed/semantically invalid successful response;
- type/decode failure;
- budget failure;
- cancellation/interruption;
- non-replayable body;
- request after partial response body consumption unless entire logical page can be safely restarted and no rows were emitted.

### Backoff

Use bounded exponential backoff with jitter:

```text
delay = min(maxDelay, initialDelay * 2^(attempt-1)) with full/equal jitter
```

Respect `Retry-After` seconds/date when valid, clamped to retry duration/deadline. Invalid or excessive values fall back/fail according to policy.

Total attempts and elapsed duration are both hard bounds.

## Retry and page emission

Once any row from a response is emitted to Trino, do not retry that response page because duplicates would be possible. A transport failure before any row emission may restart only if request is replayable and pagination state is unchanged.

Track response lifecycle:

```text
NOT_STARTED
HEADERS_RECEIVED
BODY_OPEN
ROWS_DECODED_NOT_EMITTED
ROWS_EMITTED
COMPLETE
```

Retry eligibility uses this state, not only HTTP method.

## Rate limiting

Worker-local token bucket per:

```text
(data origin, auth profile identity)
```

Rules:

- acquire before every attempt;
- waiting is cancellable and counts toward query/request deadline;
- expose wait time metric;
- do not hold concurrency permit while sleeping for rate token if this would block useful work; define acquisition order to avoid deadlock/starvation;
- fair enough to avoid one query monopolizing all permits.

Per-node rate is not a global cluster guarantee. Documentation/metrics must say so. Strict global quota requires gateway/distributed limiter outside connector.

## Concurrency bulkhead

Semaphore/async limiter per origin/profile:

- bounded active requests;
- bounded queue or immediate/backpressure behavior;
- queue wait cancellable;
- release exactly once on response close/failure;
- response stream holds permit until closed because connection/upstream work remains active;
- no permit leak on parser/decoder exception.

Recommended order:

```text
check query budget -> wait rate token -> acquire concurrency -> execute
```

Recheck cancellation/deadline between steps.

## Circuit breaker

MVP may omit circuit breaker initially if retry/rate/bulkhead provide sufficient safety. If implemented:

- scope by origin + operation group/auth boundary;
- failures limited to upstream availability failures, not user 4xx or decode/schema errors;
- states CLOSED/OPEN/HALF_OPEN;
- open duration bounded;
- small probe count;
- does not expose one tenant's failure to unrelated origins/profiles;
- metrics and tests required.

Do not add Resilience4j merely for fashion; prefer small deterministic implementation or existing Trino/Airlift primitives.

## Cancellation

Cancellation sources:

- Trino calls `ConnectorPageSource.close()`;
- query/session deadline;
- interruption;
- split source close;
- connector shutdown.

`QueryCancellation` owns a thread-safe signal and registered close actions:

```java
public interface QueryCancellation
{
    boolean isCancelled();
    void throwIfCancelled();
    Registration register(Runnable action);
    void cancel();
}
```

Cancellation must:

- cancel pending rate/concurrency waits;
- cancel HTTP future/request;
- close response stream;
- stop parser/page construction;
- prevent next pagination request;
- release permits/memory;
- avoid converting cancellation to retryable external failure.

Idempotent close/cancel is required.

## Deadlines

Calculate one request deadline from:

```text
min(query remaining time when available, session request timeout, catalog request timeout)
```

Retries share the same logical request/retry deadline. Do not reset full timeout for every attempt and exceed total policy.

## Tests

Use deterministic/fake clock/scheduler where repository patterns allow; avoid sleep-heavy flaky tests.

Cover:

- request/page/row/byte limits exactly at and over boundary;
- retries count as requests;
- 429 Retry-After seconds/date;
- bounded jitter/backoff using injected random/clock;
- safe GET/idempotent POST/non-idempotent POST;
- connection failure, 5xx, 4xx, decode failure;
- no retry after rows emitted;
- rate token cancellation;
- concurrency permit release on every path;
- queue fairness/basic isolation;
- response close releases permit;
- connector/page source cancellation during each lifecycle stage;
- deadline across retries;
- no silent truncation;
- sanitized budget/retry errors;
- per-node metric/documentation naming.

## Acceptance criteria

- Every outbound attempt is bounded and accounted.
- Hard budget excess fails the query, never returns partial success.
- Retry eligibility includes method safety and response-consumption state.
- Rate/concurrency waits are cancellable.
- Permits/resources are released exactly once.
- Total retry duration cannot exceed configured/query deadline.
- No claim of cluster-global rate enforcement is made.
- Tests are deterministic and do not use mocking frameworks.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRequestRetryPolicy,TestQueryBudget,TestRateAndConcurrencyLimits,TestCancellation' test
./mvnw -pl plugin/trino-rest airstyle:check
```