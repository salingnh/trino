# Work Item 18 — Testing, QueryRunner, Performance Gates, and CI

## Goal

Create a deterministic test pyramid proving SPI correctness, SQL semantics, streaming behavior, resilience, security, packaging and exact-version compatibility.

## Dependencies

All connector runtime work items.

## Files

```text
plugin/trino-rest/src/test/java/io/trino/plugin/rest/RestQueryRunner.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/BaseRestConnectorTest.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestConnectorTest.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/TestRestIntegrationSmokeTest.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/testing/TestingRestServer.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/testing/TestingContractRegistry.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/testing/TestingApiScenario.java
plugin/trino-rest/src/test/java/io/trino/plugin/rest/testing/RequestRecorder.java
plugin/trino-rest/src/test/resources/contracts/...
plugin/trino-rest/src/test/resources/scenarios/...
plugin/trino-rest/src/test/resources/catalog/rest.properties
```

Use existing Trino testing services and connector test base classes. Do not add mocking frameworks.

## Test layers

## 1. Pure unit tests

Cover deterministic logic with no network:

- contract parsing/validation/fingerprint;
- compiler diagnostics;
- capability inference;
- type codecs;
- predicate translation;
- request planning/serialization;
- pagination state machines;
- split batching/estimation;
- retry decisions;
- URI security validation;
- error classification/redaction.

Use table-driven tests and AssertJ.

## 2. HTTP contract tests

`TestingRestServer` must support scripted scenarios:

- request method/path/query/header/body matching;
- chunked/slow streaming response;
- TLS and optional mTLS;
- pagination variants;
- headers before/after body metadata patterns;
- 2xx/204/4xx/429/5xx;
- Retry-After;
- connection reset before/after headers;
- delayed response;
- malformed/truncated/oversized/deep JSON;
- redirects and next links;
- request recording with secret redaction.

Prefer repository-provided test HTTP server or MockWebServer/WireMock only if already permitted/dependency-aligned. A small hand-written Airlift test server is acceptable.

## 3. QueryRunner integration tests

`RestQueryRunner`:

1. starts distributed/local QueryRunner according to connector test pattern;
2. starts testing registry and API servers;
3. publishes/serves immutable test contract;
4. installs `RestPlugin`;
5. creates `rest` catalog;
6. exposes helper access to recorded requests;
7. closes all resources.

No tests depend on public internet or external services.

## SQL test matrix

### Metadata

```sql
SHOW SCHEMAS FROM rest;
SHOW TABLES FROM rest.crm;
DESCRIBE rest.crm.users;
SELECT * FROM rest.information_schema.columns ...;
```

Verify visible/hidden columns, types, comments, deterministic order, unknown relations.

### Basic reads

- root array;
- nested row path;
- point object;
- empty rows;
- nullable/missing fields;
- nested JSON/ARRAY/ROW/MAP where MVP supports them;
- projection and reordered columns.

### Filter pushdown

- equality;
- IN one request and bounded batches;
- ranges inclusive/exclusive;
- exact versus approximate;
- unsupported expression residual;
- null predicates;
- contradictory predicate -> zero HTTP requests;
- required predicate missing;
- no Cartesian request explosion.

Use both result assertions and recorded-request assertions.

### Plan assertions

Use `EXPLAIN`/plan testing utilities to assert:

- exact filter removed from engine plan;
- approximate/unsupported filter remains;
- guaranteed Limit removed;
- non-guaranteed Limit remains;
- remote projection/column pruning;
- guaranteed TopN removed only when exact;
- dynamic filter behavior where plan utilities permit.

Avoid brittle full-plan string snapshots. Assert specific nodes/properties using repository utilities.

### Pagination

- none;
- page 0/1 base;
- offset;
- cursor;
- next link body/header;
- total/empty/short termination;
- repeated cursor/link;
- maximum pages;
- query limit interactions;
- snapshot token propagation;
- mutable page warning/semantics where applicable.

### Join/dynamic filtering

Join REST probe table with small memory/TPC-H dimension:

- dynamic filter reduces request values/batches;
- timeout behavior;
- filter none -> zero requests;
- required static tenant predicate still enforced;
- no dynamic filter when disabled by session.

### Limits/projection/Top-N

- remote maximum clamping;
- residual causes non-guarantee;
- nested dereference;
- required pagination field included remotely;
- stable sort/tie-breaker/null ordering;
- multiple split behavior.

### Error semantics

Every connector error class through SQL:

- remote validation/auth/permission/not found/throttle/unavailable;
- invalid JSON/type;
- unsafe next link;
- response/row/depth size;
- all budgets;
- cancellation;
- stale fingerprint.

Assert error code and safe message, not only exception type.

## 4. Security tests

- base URI/redirect/next-link SSRF cases;
- loopback/link-local/metadata-service;
- approved on-prem private host;
- DNS rebinding where feasible;
- header injection;
- credential forwarding across origin;
- registry/data credential separation;
- secret sentinel absent from logs, plans, exceptions, handles, splits, metrics;
- TLS hostname/trust failure;
- OAuth token endpoint allowlist;
- oversized compressed/decompressed response.

## 5. Streaming and cancellation tests

Required proof:

- server sends first row/chunk, then blocks;
- connector returns first SourcePage before server completes full body;
- memory remains below a bounded threshold independent of full response size;
- selecting one field skips huge unselected field;
- cancellation closes socket/response and stops server-side streaming;
- no request issued after cancellation;
- no retry after rows emitted.

Avoid relying solely on elapsed-time flakiness; use latches/request recorder/state signals.

## 6. Concurrency/resilience tests

- many concurrent queries respect per-host active request cap;
- queue/rate waits cancel;
- retries bounded and counted;
- 429 Retry-After;
- OAuth single-flight refresh;
- no permit leaks on all exceptions;
- one query cannot exceed its budget while others continue;
- refresh failure leaves active contract usable.

## 7. Performance tests

Add JMH only when repository connector conventions support it and benchmarks are stable. At minimum create controlled tests/benchmarks for:

- rows/second scalar decode;
- time to first page;
- peak retained memory versus response size;
- projected versus full decode CPU;
- batching request count;
- page size tradeoff;
- parser depth/large string guard overhead.

Performance acceptance targets should be relative, not environment-specific absolute numbers:

- streaming peak memory grows with output page + row buffers, not full response;
- projected decode allocates materially less than decoding all fields;
- first page available before full response completion;
- one array batch uses fewer requests than scalar point requests.

## Golden fixtures

Version-control readable fixtures:

```text
contracts/minimal-get.json
contracts/post-body-cursor.json
contracts/page-pagination.json
contracts/offset-pagination.json
contracts/next-link.json
contracts/auth-profiles.json (references only, no secrets)
scenarios/users-page-*.json
scenarios/errors/*.json
```

Fixture changes require review because they define semantics.

## Connector base test support

Inspect `BaseConnectorTest` current requirements. Override unsupported capabilities explicitly:

- no create/drop/insert/update/delete;
- no views/materialized views;
- no table rename/comment mutation;
- no schema creation;
- no write retries.

Do not suppress tests broadly. Every skip/override includes reason tied to read-only REST semantics.

## Packaging test

Verify plugin ZIP/service provider and catalog startup in an isolated Trino server/container where repository patterns exist.

All Trino nodes in a distributed test use same plugin and immutable contract identity.

## CI commands

Focused during work item:

```bash
./mvnw -pl plugin/trino-rest -am test
./mvnw -pl plugin/trino-rest airstyle:check
```

Before review:

```bash
./mvnw -pl plugin/trino-rest -am verify
./mvnw validate
./mvnw sortpom:verify
```

Use branch-scoped local repository when parallel branch builds could conflict, following `.github/DEVELOPMENT.md`.

Do not recommend `-Dair.check.skip-all=true` as final validation.

## Test quality rules

- no mocking frameworks;
- no external network;
- no unbounded sleeps;
- production-quality test code;
- AssertJ assertions;
- deterministic clocks/randomness/schedulers;
- close resources with try-with-resources/cleanup;
- exact request assertions without printing secrets;
- focused test names describe behavior, not method implementation;
- avoid full plan/text snapshots when structural assertions exist.

## MVP release gate

All must pass:

- module unit tests;
- HTTP contract tests;
- BaseConnectorTest-supported matrix;
- QueryRunner pushdown/plan tests;
- streaming/cancellation tests;
- security/redaction tests;
- concurrency/budget tests;
- packaging/service-loader test;
- Airstyle/checkstyle/modernizer/dependency analysis;
- exact target-branch Java/Trino SPI build.

## Acceptance criteria

- Every acceptance scenario in EPIC has an executable test.
- Tests prove remote requests, plan semantics and SQL results together.
- Streaming and cancellation are demonstrated with synchronization, not assumptions.
- Security tests include sentinel-secret scanning.
- No flaky external dependency or mocking framework exists.
- CI commands and supported test exclusions are documented.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -am verify
./mvnw validate
```