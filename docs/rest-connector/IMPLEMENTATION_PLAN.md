# REST Connector — Detailed Implementation Plan

The implementation specification is split into focused work-item documents under [`implementation/`](implementation/00-README.md). Each file is intended to be implementable and reviewable independently by a coding agent.

## Start here

1. Read [`EPIC.md`](EPIC.md).
2. Read [`implementation/00-README.md`](implementation/00-README.md).
3. Implement one work item at a time in the order below.
4. Follow [`implementation/20-luna-execution-protocol.md`](implementation/20-luna-execution-protocol.md) for task decomposition, validation and reporting.

## Work items

| Order | Design file | Scope |
|---:|---|---|
| 01 | [`01-module-foundation.md`](implementation/01-module-foundation.md) | Maven module, plugin/factory, connector lifecycle and packaging |
| 02 | [`02-configuration-and-session-properties.md`](implementation/02-configuration-and-session-properties.md) | Catalog configuration, session reductions and Trino config rules |
| 03 | [`03-contract-schema.md`](implementation/03-contract-schema.md) | Versioned compiled contract, immutable records and fingerprint |
| 04 | [`04-registry-client-and-model-cache.md`](implementation/04-registry-client-and-model-cache.md) | Coordinator registry loading, verification, snapshots and cache |
| 05 | [`05-contract-compiler-and-validation.md`](implementation/05-contract-compiler-and-validation.md) | API definition compiler, deterministic diagnostics and validation |
| 06 | [`06-type-system-and-value-codecs.md`](implementation/06-type-system-and-value-codecs.md) | Type descriptors, request encoders and streaming JSON decoders |
| 07 | [`07-metadata-and-handles.md`](implementation/07-metadata-and-handles.md) | Metadata, catalog model, table/column handles and splits |
| 08 | [`08-capability-inference.md`](implementation/08-capability-inference.md) | Conservative inference, evidence, warnings and overrides |
| 09 | [`09-filter-pushdown.md`](implementation/09-filter-pushdown.md) | Exact/approximate filters, residuals, IN batching and required scans |
| 10 | [`10-limit-projection-and-topn.md`](implementation/10-limit-projection-and-topn.md) | Limit, projection/dereference and Top-N optimizer hooks |
| 11 | [`11-request-planner-and-serialization.md`](implementation/11-request-planner-and-serialization.md) | Typed SQL-to-HTTP planning and sanitized preview |
| 12 | [`12-pagination-state-machines.md`](implementation/12-pagination-state-machines.md) | none/page/offset/cursor/next-link state machines |
| 13 | [`13-split-planning-and-dynamic-filtering.md`](implementation/13-split-planning-and-dynamic-filtering.md) | Independent partitions, dynamic filtering and bounded batching |
| 14 | [`14-http-auth-and-network-security.md`](implementation/14-http-auth-and-network-security.md) | HTTP transport, auth profiles, TLS, SSRF and redirects |
| 15 | [`15-resilience-budgets-and-cancellation.md`](implementation/15-resilience-budgets-and-cancellation.md) | Retry, rate/concurrency limits, query budgets and cancellation |
| 16 | [`16-streaming-page-source.md`](implementation/16-streaming-page-source.md) | asynchronous streaming PageSource, memory and resource lifecycle |
| 17 | [`17-errors-and-observability.md`](implementation/17-errors-and-observability.md) | Connector errors, safe diagnostics, metrics, JMX and tracing |
| 18 | [`18-testing-queryrunner-and-ci.md`](implementation/18-testing-queryrunner-and-ci.md) | Test pyramid, QueryRunner, security/performance and CI gates |
| 19 | [`19-contract-registry-and-preview-api.md`](implementation/19-contract-registry-and-preview-api.md) | External control-plane API and compiled-contract interoperability |
| 20 | [`20-luna-execution-protocol.md`](implementation/20-luna-execution-protocol.md) | Strict execution protocol and task templates for GPT-5.6 Luna |

## Milestones

### M0 — Compilable connector skeleton

Work items 01–03.

Exit evidence:

- plugin discovered by ServiceLoader;
- catalog configuration validated;
- compiled contract round trips and fingerprints deterministically.

### M1 — Stable metadata and planning model

Work items 04–08.

Exit evidence:

- immutable contract loaded and verified;
- schemas/tables/columns exposed without remote calls per metadata access;
- handles/splits serialize and remain equality-stable;
- capabilities include evidence and conservative warnings.

### M2 — Correct optimizer and request planning

Work items 09–13.

Exit evidence:

- exact filters are enforced and approximate filters remain residual;
- limit/projection/Top-N hooks converge;
- pure request planner supports runtime and dry-run;
- all pagination families are bounded;
- no default Cartesian split expansion.

### M3 — Production-safe worker execution

Work items 14–17.

Exit evidence:

- authentication and network policies enforced;
- request/page/row/byte/concurrency budgets applied;
- async PageSource streams first page before full response;
- cancellation closes every resource;
- errors and metrics are safe and actionable.

### M4 — Release candidate

Work items 18–19.

Exit evidence:

- QueryRunner and base connector tests pass;
- plan assertions prove residual/guarantee behavior;
- security, retry, cancellation and streaming tests pass;
- registry/preview compatibility contract is versioned;
- module `verify` and repository `validate` pass.

## Global Definition of Done

A work item is not complete until:

- production code and focused tests are in the same change;
- Airstyle and focused Maven tests pass;
- every acceptance criterion has test or inspection evidence;
- relevant `.claude/rules` and `.github/DEVELOPMENT.md` requirements are satisfied;
- the diff contains no unrelated changes;
- an independent review checks correctness, security and resource lifecycle;
- temporary skeletons fail explicitly and identify the later work item that replaces them.

The connector remains read-only throughout this plan. Writes, aggregation pushdown, generic join pushdown, delegated per-user OAuth, mutable runtime schemas and automatic OpenAPI exposure are outside the MVP.