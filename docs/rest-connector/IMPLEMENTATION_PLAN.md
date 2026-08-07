# REST Connector — Detailed Implementation Plan

The implementation specification is split into focused work-item documents under [`implementation/`](implementation/00-README.md). Each file is intended to be implementable and reviewable independently by a coding agent.

## Start here

1. Read [`EPIC.md`](EPIC.md).
2. Read [`implementation/00-README.md`](implementation/00-README.md).
3. Implement one work item at a time in the priority and dependency order below.
4. Follow [`implementation/20-luna-execution-protocol.md`](implementation/20-luna-execution-protocol.md) for task decomposition, validation and reporting.

## Current implementation priority

**Start with Work Item 01 only.** Do not give Luna multiple work items in one implementation cycle.

Recommended execution priority:

| Priority | Work items | Objective |
|---|---|---|
| **P0 — Foundation and contract lock** | 01–03 | Establish a compilable plugin, compliant configuration model, and stable immutable compiled-contract format |
| **P1 — Coordinator model and metadata** | 04–08 | Load/verify contracts, compile types and metadata, create stable handles, and infer capabilities conservatively |
| **P2 — Optimizer and request planning** | 09–13 | Implement pushdown correctness, pure request planning, pagination, bounded split planning, and dynamic filtering |
| **P3 — Production-safe worker runtime** | 14–17 | Add HTTP/auth/security, resilience and budgets, streaming PageSource, errors and observability |
| **P4 — Release candidate and control-plane contract** | 18–19 | Complete QueryRunner/CI/security coverage and finalize Contract Registry/preview interoperability |

The dependency order inside each priority is mandatory. In particular:

```text
01 module foundation
  -> 02 configuration
  -> 03 contract schema
  -> 04 registry/cache
  -> 05 compiler/validation
  -> 06 type codecs
  -> 07 metadata/handles
  -> 08 capability inference
  -> 09 filter pushdown
  -> 10 limit/projection/Top-N
  -> 11 request planner
  -> 12 pagination
  -> 13 split planning/dynamic filtering
  -> 14 HTTP/auth/security
  -> 15 resilience/budgets/cancellation
  -> 16 streaming PageSource
  -> 17 errors/observability
  -> 18 QueryRunner/CI
  -> 19 registry/preview interoperability
```

Do not implement `RestPageSource` before the request planner, pagination state machines, HTTP security policy, and query budgets are defined. The page source must execute a normalized request plan rather than reimplement planning decisions in the worker data path.

## Priority checkpoints

### Checkpoint P0.1 — after Work Item 01

Before starting Work Item 02, prove all of the following:

- `plugin/trino-rest` exists and is registered in the repository build;
- `RestPlugin` is discoverable through the plugin service mechanism;
- `RestConnectorFactory` exposes connector name `rest`;
- connector bootstrap and lifecycle compile against the exact branch SPI;
- focused module/bootstrap tests pass;
- no placeholder implementation silently claims unsupported functionality.

### Checkpoint P0.2 — after Work Item 03

Before starting registry work, prove:

- compiled-contract JSON deserializes into immutable models;
- serialization is deterministic;
- fingerprint calculation is deterministic and covers semantic contract content;
- unsupported contract versions fail explicitly;
- no credential or secret field exists in the compiled-contract model;
- round-trip and fingerprint tests pass.

### Checkpoint P1 — after Work Item 08

Before optimizer implementation, prove:

- coordinator loads and verifies an immutable contract snapshot;
- normal metadata access does not call the registry repeatedly;
- schemas, tables, columns and hidden inputs are deterministic;
- table/column handles and splits are JSON-serializable and equality-stable;
- inference output includes evidence/reasons and conservative warnings;
- ambiguous semantics are not promoted to exact pushdown capability.

### Checkpoint P2 — after Work Item 13

Before worker HTTP execution, prove:

- exact filters become enforced constraints while unsupported/approximate predicates remain residual;
- repeated optimizer calls converge and return `Optional.empty()` when no semantic state changes;
- limit guarantee is correct for the final handle state;
- request planning is pure and shared by runtime and dry-run preview;
- page/offset/cursor/next-link state machines have loop and budget guards;
- split generation avoids default Cartesian expansion and remains bounded.

### Checkpoint P3 — after Work Item 17

Before release-candidate work, prove:

- every outgoing URI passes scheme/host/origin validation;
- credentials are not forwarded across unapproved origins;
- retry policy is method/error aware and bounded;
- request/page/row/byte/concurrency budgets fail rather than truncate silently;
- query cancellation closes active HTTP and parser resources;
- streaming PageSource can emit data without buffering the full response;
- connector errors and metrics contain no secrets or unbounded-cardinality labels.

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