# Work Item 12 — Pagination State Machines

## Goal

Implement explicit, bounded pagination strategies for no pagination, page number, offset, cursor, and next link. Pagination state lives inside one page source/split unless an independent partition dimension exists.

## Dependencies

Work Items 03, 08, 11.

## Files

```text
io.trino.plugin.rest.pagination.PaginationStrategy
io.trino.plugin.rest.pagination.PaginationState
io.trino.plugin.rest.pagination.PaginationDecision
io.trino.plugin.rest.pagination.PageResultMetadata
io.trino.plugin.rest.pagination.NoPaginationStrategy
io.trino.plugin.rest.pagination.PageNumberPaginationStrategy
io.trino.plugin.rest.pagination.OffsetPaginationStrategy
io.trino.plugin.rest.pagination.CursorPaginationStrategy
io.trino.plugin.rest.pagination.NextLinkPaginationStrategy
io.trino.plugin.rest.pagination.PaginationStrategyFactory
io.trino.plugin.rest.pagination.ContinuationHistory
io.trino.plugin.rest.pagination.PaginationException
src/test/java/io/trino/plugin/rest/pagination/Test*PaginationStrategy.java
```

## Interface

```java
public interface PaginationStrategy
{
    PaginationState initial(
            RestTableHandle table,
            RestSplit split,
            EffectiveQueryLimits limits);

    PaginationDecision afterPage(
            PaginationState current,
            PageResultMetadata result,
            QueryProgress progress,
            EffectiveQueryLimits limits);
}
```

`PaginationDecision`:

```java
public record PaginationDecision(
        boolean finished,
        Optional<PaginationState> next,
        PaginationStopReason reason) {}
```

Stop reasons include:

```text
NO_PAGINATION
EMPTY_PAGE
SHORT_PAGE
TOTAL_REACHED
NO_CONTINUATION
QUERY_LIMIT_REACHED
PAGE_BUDGET_REACHED
ROW_BUDGET_REACHED
BYTE_BUDGET_REACHED
REPEATED_CONTINUATION
REMOTE_COMPLETION
```

Budget-exceeded hard limits must throw, not return a normal finished decision, unless the SQL query limit itself was satisfied exactly.

## Common invariants

- state is immutable;
- page sequence number increases monotonically;
- request/page counters are updated exactly once per successful/attempted request according to Work Item 15 accounting policy;
- continuation values are opaque and never logged;
- repeated continuation detection is bounded in memory;
- query cancellation stops before scheduling another request;
- query limit check occurs before and after page decode;
- no strategy silently truncates due to connector hard budget;
- strategy cannot change base origin/security policy.

## `PageResultMetadata`

Contains only information extracted safely from the current response:

```java
public record PageResultMetadata(
        long rowsInPage,
        long responseBytes,
        OptionalLong totalRows,
        OptionalLong totalPages,
        Optional<String> nextCursor,
        Optional<URI> nextLink,
        Optional<String> snapshotToken,
        boolean remoteCompleted) {}
```

Cursor/link values are sensitive opaque values. Their `toString()` must redact or record only presence/hash prefix where safe.

## No pagination

Initial state identifies one request.

After first successful response:

- finish always;
- if response exceeds configured row/byte limit, fail;
- if SQL limit is smaller, page source stops decoding/stream closes once safe;
- contract compiler should have rejected obviously unbounded no-pagination endpoints without guardrails.

Test that a second call to `afterPage` is rejected as invalid state.

## Page-number strategy

Definition fields:

- first page (0 or 1 or explicit positive value);
- page request field;
- optional page-size field;
- maximum page size;
- optional total pages/rows pointer;
- stop-on-empty;
- optional stop-on-short-page;
- stable ordering/consistency metadata.

State:

```java
public record PageNumberState(
        long pageNumber,
        long pageSequence,
        long requestedPageSize) implements PaginationState {}
```

Algorithm:

1. Send first page with calculated page size.
2. After page:
   - stop on query limit;
   - stop when total rows/pages reached;
   - stop on empty page when enabled;
   - stop on short page only when API contract guarantees short final page;
   - otherwise increment page using exact overflow checks.
3. Detect APIs that clamp out-of-range page and repeat data through optional page fingerprint/continuation guard when configured.
4. Enforce maximum pages.

Do not parallelize page numbers here; page-range splitting belongs to Work Item 13 and requires known total + stable ordering.

## Offset strategy

Definition:

- initial offset;
- offset field;
- limit/page-size field;
- maximum page size;
- optional total rows;
- stable ordering declaration.

State:

```java
public record OffsetState(
        long offset,
        long pageSequence,
        long requestedSize) implements PaginationState {}
```

Next offset is `offset + rowsConsumedForRemotePage` or requested page size according to explicit API semantics. Do not assume one. Most APIs advance by requested size; contract must state behavior.

Use exact arithmetic and fail on overflow.

Warnings/consistency:

- mutable datasets can duplicate/omit rows;
- connector reports best-effort unless API supports snapshot/as-of token;
- snapshot token, when present, is applied to all requests through system binding.

## Cursor strategy

Definition:

- cursor request field;
- next-cursor response pointer;
- optional initial cursor;
- optional page-size field;
- empty/null/blank cursor termination policy;
- consistency metadata.

State:

```java
public record CursorState(
        Optional<String> cursor,
        long pageSequence,
        long requestedSize,
        ContinuationHistory history) implements PaginationState {}
```

Rules:

- cursor is opaque;
- no parsing, ordering or arithmetic;
- next cursor absent/null/blank terminates according to contract;
- repeated cursor fails with `REST_PAGINATION_LOOP`;
- history stores cryptographic hashes, not raw cursor values, when equality detection can be performed securely;
- cap history by maximum pages;
- cursor chain remains sequential inside one split;
- no retries after a response has been partially consumed unless transport/request execution guarantees safe replay and parser state restarts from response boundary.

## Next-link strategy

Sources:

- JSON pointer in body metadata;
- HTTP `Link` header relation `next`;
- approved custom response header only if explicitly configured.

State stores validated URI or relative reference plus page sequence/history.

Before next request:

1. Parse URI strictly.
2. Resolve relative reference against previous request URI.
3. Normalize scheme/host/port/path.
4. Validate through Work Item 14 network policy.
5. Reject user info, fragments, unsupported schemes and disallowed origin.
6. Decide credential forwarding: same approved auth boundary only; default deny cross-origin.
7. Detect repeated normalized URI using hash/history.
8. Do not merge arbitrary next-link query parameters with SQL bindings; the link is server-generated continuation. Preserve only required safe headers/auth after policy validation.

Redirect and next link are separate concepts and independently controlled.

## Query limit behavior

Track remote rows decoded and rows emitted. With no residual filter, emitted equals decoded for ordinary rows. With residual filters, connector cannot know final Trino output rows, so it must not stop early based solely on remote decoded rows as if SQL LIMIT were satisfied.

Define `QueryProgress` fields:

```text
requestsStarted
requestsCompleted
pagesCompleted
remoteRowsDecoded
remoteBytesRead
remoteRowsEmittedToTrino
remoteLimitHint
limitGuaranteed
```

When limit is guaranteed, stop as soon as page source has emitted the required number of rows. Close response without parsing continuation metadata if safe and no further request is needed.

When non-guaranteed, SQL engine retains Limit and connector uses only page-size/request hints; hard budgets still apply.

## Continuation loop detection

`ContinuationHistory`:

- uses SHA-256 or stable hash of cursor/normalized link plus operation identity;
- no raw values in diagnostics;
- exact set bounded by max pages;
- repeated state fails immediately;
- optional page fingerprint for page APIs may hash stable row identifiers/response metadata only when configured and bounded.

## Metadata extraction

Pagination metadata should be extracted while streaming without materializing entire body.

Challenge: next cursor may appear after row array. Implement one of:

- parser continues through remaining envelope after rows before closing response;
- response parser captures selected metadata paths through compiled pointer trie;
- cursor in headers is available before body.

Page source must not issue the next request until required continuation metadata is available.

## Tests

For each strategy:

- first state;
- normal multi-page completion;
- empty/short/total completion;
- SQL limit guaranteed/non-guaranteed;
- page/request/row/byte budgets;
- overflow;
- repeated cursor/link;
- missing/invalid continuation;
- cancellation before next request;
- snapshot token propagation;
- immutable/equality state;
- redacted `toString()`.

Additional next-link security tests:

- same-origin relative link;
- absolute approved link;
- unapproved host;
- HTTP downgrade;
- user info;
- loopback/link-local/metadata-service address after DNS resolution policy;
- link loop with query parameter ordering normalized.

## Acceptance criteria

- All five strategies have explicit immutable state machines.
- Cursor/next-link chains remain sequential.
- Continuation loops fail with safe diagnostics.
- Hard budget exhaustion never returns silent partial data.
- Query-limit stopping respects guaranteed versus non-guaranteed semantics.
- Next links cannot bypass host/auth policy.
- Streaming metadata extraction is proven by tests.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='Test*PaginationStrategy' test
./mvnw -pl plugin/trino-rest airstyle:check
```