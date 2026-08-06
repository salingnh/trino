# Work Item 16 — Streaming Connector Page Source

## Goal

Implement the worker data path that asynchronously requests pages, streams JSON, decodes only projected columns, constructs Trino pages, observes memory/backpressure, and closes immediately on completion/cancellation.

## Dependencies

Work Items 06, 11–15.

## Files

```text
io.trino.plugin.rest.RestPageSourceProvider
io.trino.plugin.rest.RestPageSource
io.trino.plugin.rest.page.RestSourcePage
io.trino.plugin.rest.page.ResponseStream
io.trino.plugin.rest.page.RowStreamDecoder
io.trino.plugin.rest.page.ResponseEnvelopeParser
io.trino.plugin.rest.page.PageBuilderController
io.trino.plugin.rest.page.PageSourceState
io.trino.plugin.rest.page.PageSourceMetrics
src/test/java/io/trino/plugin/rest/TestRestPageSourceProvider.java
src/test/java/io/trino/plugin/rest/TestRestPageSource.java
src/test/java/io/trino/plugin/rest/page/TestRowStreamDecoder.java
```

## SPI signatures

Use exact target branch `ConnectorPageSourceProvider#createPageSource` signature, including `MemoryContext` callback. The current `ConnectorPageSource` interface produces `SourcePage`, supports `isBlocked()`, metrics, completed bytes/positions/read time, and requires `close()`.

## Page-source provider

Provider responsibilities:

1. Cast and validate `RestSplit`, `RestTableHandle`, and requested `RestColumnHandle` list.
2. Verify contract fingerprint/table/operation consistency.
3. Resolve immutable table model/codecs/pagination strategy.
4. Build effective limits/session switches.
5. Create query cancellation/accounting context.
6. Construct `RestPageSource` with no network call in constructor where possible.

Reject:

- mismatched handle/split fingerprint;
- column not part of table/derived projection;
- duplicate/invalid column channels;
- stale model unavailable;
- unsupported execution mode.

## State machine

```text
NEW
WAITING_FOR_REQUEST
READING_RESPONSE
BUILDING_PAGE
PAGE_READY
FINISHED
FAILED
CLOSED
```

State transitions are single-thread-safe according to Trino operator calling pattern. Async HTTP completion may occur on another thread; synchronize narrowly or use immutable future results.

Rules:

- constructor starts `NEW`;
- `isBlocked()` returns current request future when waiting;
- `getNextSourcePage()` never blocks on network;
- when response is available, decode a bounded amount of work and return a `SourcePage` or null;
- after `isFinished()` true, engine will not call next-page method;
- `close()` is idempotent from every state;
- failure is rethrown consistently and resources closed.

## Non-blocking request flow

Preferred flow:

1. `getNextSourcePage()` in NEW schedules first async request and returns null.
2. `isBlocked()` exposes request future.
3. Future completion stores response stream/status metadata.
4. Next call decodes rows until output page full, response page ends, query limit reached, or CPU/yield boundary.
5. On response end, parse continuation metadata and close response.
6. Pagination strategy decides finish/next.
7. Next request is scheduled asynchronously; page source becomes blocked.

Do not call `.get()`/`.join()` on network futures in operator thread unless already completed and repository pattern explicitly permits safe retrieval.

## `SourcePage`

Prefer returning a Trino `Page` wrapped/adapted by current helper or implementing `SourcePage` directly only when needed. Follow sibling connectors on current branch.

Page must contain channels in requested column order and exact types.

## Streaming response parser

`ResponseEnvelopeParser` handles envelope + row pointer + metadata pointers.

Requirements:

- Jackson `JsonParser`/streaming API;
- locate row pointer without full-tree materialization;
- require row container shape declared by contract (array, single object when explicitly allowed);
- compile pointer trie for projected columns and pagination/error metadata;
- skip unselected subtrees;
- continue parsing envelope after rows when continuation appears later;
- enforce max depth/string/row/response bytes;
- close parser and underlying response on all paths.

### Row pointer cases

Support MVP:

- root array `""` or `/` representation agreed by compiler;
- nested array `/data/items`;
- single object point lookup when table declares single-row response;
- empty/missing row pointer behavior explicit: empty result only when contract permits; otherwise schema mismatch.

Do not support arbitrary JSONPath expressions.

## Row decoding and PageBuilder

For every row:

1. reset bounded row state;
2. dispatch selected fields through compiled trie;
3. decode values through Work Item 06 codecs;
4. track seen fields;
5. append one position across all channel builders atomically;
6. append null/error for missing fields after object end;
7. increment remote row budget/count.

Avoid declaring a PageBuilder position before the row is known decodable if a failure could leave channel counts inconsistent. Use temporary field state or carefully coordinated block builder writes according to Trino APIs.

`PageBuilderController` determines page flush by:

- page builder full;
- target positions;
- target retained bytes;
- end of response page;
- query guaranteed limit remaining;
- yield/cpu budget if needed.

## Memory accounting

Use supplied `MemoryContext`.

Report retained bytes for:

- PageBuilder/block builders;
- parser input buffers if visible/owned;
- row scratch state;
- continuation history;
- pending request body/response wrapper;
- decoded metadata buffers.

Update on material changes and set zero/release on close. Do not rely only on deprecated `getMemoryUsage()`.

Avoid buffering full body. Per-page output memory should remain bounded by page builder target and max row size.

## Completed metrics

Implement:

- `getCompletedBytes()` actual response bytes read (define decompressed/wire metric separately);
- `getCompletedPositions()` remote rows decoded where exact;
- `getReadTimeNanos()` wall/read time spent on HTTP/body read according to Trino convention;
- `getMetrics()` immutable snapshot from Work Item 17;
- retries/throttle wait not falsely added to bytes/positions.

## Status and content type

Before row decode:

- classify status through error mapper;
- accept configured success codes;
- 204 -> empty only if allowed;
- 404 -> empty only for configured point lookup;
- validate JSON-compatible content type;
- enforce content length/response limits;
- capture safe request ID headers.

For non-success error bodies, read only bounded small content needed for configured error pointer. Never feed huge error body into full tree; use bounded parser/text extraction and redaction.

## Query limit

### Guaranteed

When handle limit is guaranteed:

- track positions emitted;
- stop decoding exactly at limit;
- close current stream immediately;
- do not schedule continuation;
- page may be truncated to remaining positions safely;
- metrics reflect bytes actually read.

### Non-guaranteed

Do not stop as if final SQL limit is satisfied because residual filter happens above connector. Use limit/page-size only as hint; engine retains Limit.

## Cancellation and close

`close()`:

- mark CLOSED/FINISHED idempotently;
- cancel pending request future;
- close response parser/stream;
- cancel limiter waits;
- release concurrency permit;
- clear PageBuilder/scratch references where useful;
- set memory context zero;
- prevent pagination/request scheduling;
- preserve original failure when close also fails (log/suppress according to repository convention).

## Failure atomicity

Once a page is returned, its rows are committed to engine. Do not retry any request/page that could duplicate returned rows.

If decode fails mid-output page before return, fail query and discard page builder. Close response. No partial page return.

## Tests

Use a controllable async HTTP test server/handler.

### State/blocking

- first call schedules request and returns null;
- `isBlocked` incomplete/complete behavior;
- multiple pages within one response;
- continuation schedules next request;
- no blocking `.join()` behavior;
- close in every state.

### Streaming

- first `SourcePage` returned before full response produced;
- very large trailing/unprojected field skipped;
- nested row path;
- pagination metadata after row array;
- single-object point response;
- malformed JSON early/late;
- content type/status behavior.

### Correctness

- projected channels/order/types;
- nullable/missing/non-null error;
- page boundaries do not lose/duplicate rows;
- guaranteed limit exact stop;
- non-guaranteed limit continues as required;
- cursor/page progression;
- retry before rows versus no retry after rows.

### Resource safety

- cancellation closes server connection/body;
- concurrency permit released;
- memory returns to zero;
- oversized response/row/string/depth fails;
- exception/redaction safe;
- no full response allocation measured via test instrumentation.

## Acceptance criteria

- Network wait is represented by `isBlocked()` rather than operator-thread blocking.
- Rows are streamed into bounded pages with no full response/tree materialization.
- Only selected columns are decoded.
- Pagination metadata and continuation are handled correctly.
- Guaranteed limits stop exactly; non-guaranteed limits do not under-read.
- Cancellation/close releases every resource and memory permit.
- Tests prove time-to-first-page before response completion.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestPageSourceProvider,TestRestPageSource,TestRowStreamDecoder' test
./mvnw -pl plugin/trino-rest airstyle:check
```