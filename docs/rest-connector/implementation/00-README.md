# REST Connector — Implementation Guide

This directory is the executable engineering specification for `plugin/trino-rest`.
It decomposes the epic into bounded implementation units that can be completed and reviewed independently by a coding agent.

## Source of truth

Read these files before changing Java code:

1. repository `CLAUDE.md`;
2. `.github/DEVELOPMENT.md` in full;
3. `.claude/rules/trino-config-properties.md` whenever editing `*Config.java` or `*SessionProperties.java`;
4. `docs/rest-connector/EPIC.md`;
5. the relevant work-item document in this directory.

Do not infer requirements from filenames or parameter names. The connector is mapping-driven. OpenAPI support is optional assisted discovery and is not the runtime execution model.

## Fixed architectural decisions

- Module: `plugin/trino-rest`.
- Initial capability: read-only `GET` and explicitly read-only `POST`.
- Contract source: immutable compiled contracts loaded by the coordinator from a registry.
- Worker behavior: workers never contact the registry and never parse user-facing API definitions.
- Planning state: immutable, JSON-serializable handles and splits carrying a contract fingerprint.
- Data path: `ConnectorPageSourceProvider` and streaming JSON parsing into Trino `SourcePage`/blocks.
- Correctness: unsupported or approximate predicates remain residual in Trino.
- Pagination and split dimensions are separate. Cursor continuation remains inside a split.
- Secrets never enter contracts, handles, splits, plans, logs, metrics, or exceptions.
- Every remote scan is bounded by request, page, row, byte, split, timeout, and concurrency budgets.
- Exact Trino SPI version compatibility is required; cross-release binary compatibility is not assumed.

## Work-item files and order

| Order | File | Deliverable |
|---:|---|---|
| 1 | `01-module-foundation.md` | Maven module, plugin bootstrap, connector lifecycle |
| 2 | `02-configuration-and-session-properties.md` | Catalog/session configuration with Trino conventions |
| 3 | `03-contract-schema.md` | Stable compiled-contract JSON schema and immutable model |
| 4 | `04-registry-client-and-model-cache.md` | Coordinator-only registry client, verification, cache |
| 5 | `05-contract-compiler-and-validation.md` | API definition to compiled contract, inference validation |
| 6 | `06-type-system-and-value-codecs.md` | Trino types, JSON encoders/decoders, null semantics |
| 7 | `07-metadata-and-handles.md` | Metadata, table/column handles, serialization invariants |
| 8 | `08-capability-inference.md` | Conservative capability derivation and explanations |
| 9 | `09-filter-pushdown.md` | `applyFilter`, residual predicates, required filters |
| 10 | `10-limit-projection-and-topn.md` | `applyLimit`, `applyProjection`, `applyTopN` |
| 11 | `11-request-planner-and-serialization.md` | Typed SQL-to-HTTP request planning |
| 12 | `12-pagination-state-machines.md` | none/page/offset/cursor/next-link strategies |
| 13 | `13-split-planning-and-dynamic-filtering.md` | split dimensions, batching, dynamic filtering |
| 14 | `14-http-auth-and-network-security.md` | HTTP client, auth profiles, SSRF protection |
| 15 | `15-resilience-budgets-and-cancellation.md` | retry, throttling, budgets, cancellation |
| 16 | `16-streaming-page-source.md` | asynchronous HTTP and streaming page production |
| 17 | `17-errors-and-observability.md` | error codes, diagnostics, metrics and tracing |
| 18 | `18-testing-queryrunner-and-ci.md` | complete test pyramid and required commands |
| 19 | `19-contract-registry-and-preview-api.md` | external control-plane interoperability contract |
| 20 | `20-luna-execution-protocol.md` | strict task protocol for implementation agents |

## Required implementation sequence

The implementation agent must not skip dependency gates:

```text
foundation
  -> configuration
  -> contract model
  -> registry/cache
  -> compiler/validation
  -> type codecs
  -> metadata/handles
  -> capability inference
  -> filter/limit/projection
  -> request planner
  -> pagination
  -> splits/dynamic filtering
  -> HTTP/auth/security
  -> resilience/budgets
  -> streaming page source
  -> errors/metrics
  -> QueryRunner/CI
```

## Per-work-item completion protocol

For every file:

1. Inspect the referenced existing Trino classes and at least one comparable connector.
2. List intended files before editing.
3. Implement only the stated scope.
4. Add or update tests in the same change.
5. Run focused tests.
6. Run Airstyle formatting/check for the module.
7. Inspect the diff for unrelated changes.
8. Record evidence against every acceptance criterion.
9. Stop after the work item; do not begin the next item until review passes.

## Universal coding constraints

- Add Apache license headers to every source file.
- Keep names explicit; avoid abbreviations.
- Prefer Guava immutable collections for deterministic iteration.
- Do not use mocking frameworks. Use concrete fakes, test HTTP servers, and small hand-written test doubles.
- Use AssertJ for assertions.
- Categorize failures with connector-specific `TrinoException` error codes.
- Use `Optional.empty()` from optimizer hooks when the new invocation causes no semantic handle change.
- Make records/classes immutable and equality-stable.
- Never store a mutable configuration object in runtime components; copy validated values in constructors.
- Keep JSON/Jackson tree materialization out of the row data path.
- Never silently truncate results when a hard budget is exceeded.

## Definition of MVP done

The MVP is complete only when all of the following are demonstrated through `QueryRunner` and deterministic HTTP tests:

- schema/table/column discovery from a published compiled contract;
- exact equality, `IN`, and range pushdown with correct residual predicates;
- required-predicate rejection through `validateScan`;
- limit application with correct guaranteed flag;
- local decode projection and optional remote projection;
- none, page, offset, cursor, and next-link pagination;
- repeated-token/link detection;
- bounded batching without Cartesian request explosion;
- streaming first page without buffering the complete response;
- query cancellation closes active responses;
- API key, bearer, basic, OAuth2 client-credentials, and mTLS profiles where configured;
- redirect/next-link host restrictions and credential stripping;
- request/page/row/byte/split/concurrency budgets;
- actionable sanitized connector errors;
- metrics for requests, pages, rows, bytes, retries, throttling, decoding, and budget failures;
- module validation and focused test suite passing against the repository's exact Trino SPI.