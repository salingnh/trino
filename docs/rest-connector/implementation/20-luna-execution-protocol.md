# Work Item 20 — GPT-5.6 Luna Execution Protocol

## Purpose

This file is the operating protocol for a lower-cost implementation agent such as GPT-5.6 Luna. It converts the design documents into small, verifiable tasks and prevents broad speculative implementation.

## Mandatory operating rules

1. Read repository `CLAUDE.md` and `.github/DEVELOPMENT.md` in full before Java edits.
2. Read `.claude/rules/trino-config-properties.md` before editing any `*Config.java` or `*SessionProperties.java`.
3. Read `docs/rest-connector/EPIC.md`, `implementation/00-README.md`, and exactly one current work-item file.
4. Do not implement future work items unless required to compile the current item; use a minimal explicit interface/skeleton instead.
5. Inspect comparable code in at least two current Trino connectors before selecting an SPI/bootstrap/testing pattern.
6. Use exact current-branch SPI signatures. Never rely on remembered Trino APIs.
7. Do not copy `trino-openapi` wholesale. It is reference material only.
8. Never introduce writes, raw OpenAPI runtime execution, arbitrary URLs, full-response buffering, mocking frameworks, or silent result truncation.
9. Every code change includes tests in the same task.
10. Stop after review evidence is produced.

## One work item per implementation cycle

Each cycle follows:

```text
ANALYZE
  -> PLAN
  -> IMPLEMENT
  -> FORMAT
  -> TEST
  -> REVIEW
  -> REPORT
  -> STOP
```

Do not combine two work-item documents into one coding run unless the user explicitly asks.

## Analyze phase output

Before editing, produce a short internal/task note containing:

```text
Work item:
Target package/module:
Existing Trino reference classes inspected:
Files to create:
Files to modify:
SPI signatures verified:
Dependencies already available:
Open decisions:
Risks:
Focused test commands:
```

Resolve decisions from repository/source code. Do not ask the user questions for details already specified in these documents.

## Plan granularity

Break current work item into tasks that each change a coherent set of files and can be reviewed independently.

Good task:

```text
Task 09.2 — Translate exact scalar equality domains
Files:
- RestPredicateTranslator.java
- PredicateTranslationResult.java
- TestRestPredicateTranslator.java
Acceptance:
- exact VARCHAR/BIGINT equality becomes enforced binding
- unsupported type remains residual
- repeated translation returns no handle change
```

Bad task:

```text
Implement filter pushdown
```

Each task must state:

- exact files/classes;
- method signatures;
- input/output model;
- invariants;
- error behavior;
- tests;
- completion command.

## Implementation constraints

### Java and style

- add license header;
- no wildcard imports;
- braces for control flow;
- no `@author`;
- use `var` only when inferred type is obvious/readable;
- avoid ternary except trivial;
- avoid abbreviations;
- prefer specific method names over generic `get` where repository style allows;
- prefer Guava immutable collections;
- no mutable public exposure;
- no default branch in exhaustive enum/sealed switch;
- use connector-specific categorized `TrinoException` errors;
- use AssertJ;
- no mocking libraries.

### Models/handles

- constructors validate and defensively copy;
- equality/hash represent all semantic planning state;
- deterministic collection order;
- no secret/client/session/mutable runtime state;
- `toString()` redacts values/tokens/links;
- JSON round-trip test for every handle/split variant.

### Optimizer hooks

For `applyFilter`, `applyLimit`, `applyProjection`, `applyTopN`:

- inspect exact current SPI documentation/source;
- return `Optional.empty()` when invocation has no effect;
- never remove residual predicate unless exact remote semantics;
- never mark guarantee true without all conditions;
- test repeated calls and different hook orders;
- test plan node removal/retention in QueryRunner stage.

### HTTP/data path

- async network wait through `isBlocked()`;
- no `join()`/blocking network on operator thread;
- no `readTree`, full body string or `InMemoryRecordSet` in row path;
- direct streaming parse and selected field decode;
- bounded page/row/response memory;
- close all streams/futures/permits on success/failure/cancel;
- retry only before rows emitted and only when request safety permits.

### Security

- fixed/allowlisted origins;
- validate redirect/next-link/token URI;
- no arbitrary headers/auth from SQL;
- auth applied after pure request planning;
- no credentials in model/handle/split/log/metric/error;
- TLS hostname verification enabled;
- no trust-all mode.

## Temporary skeleton policy

A work item may need interfaces/classes implemented later. Allowed skeleton:

- narrow interface with documented method contract;
- constructor-injected dependency;
- temporary implementation throws a clear internal unsupported exception;
- one test proving skeleton cannot accidentally execute.

Forbidden skeleton:

- return empty data and pretend success;
- catch/ignore exceptions;
- `TODO` with ambiguous behavior;
- mutable global placeholder;
- permissive security default;
- unbounded request path.

Every skeleton must name the work item that replaces it.

## Test protocol

### During implementation

Run smallest affected test class after each meaningful feature.

### Before task completion

```bash
./mvnw -pl plugin/trino-rest -Dtest='<focused tests>' test
./mvnw -pl plugin/trino-rest airstyle:check
```

### Before work-item completion

```bash
./mvnw -pl plugin/trino-rest -am test
./mvnw -pl plugin/trino-rest validate
```

### Before final MVP review

```bash
./mvnw -pl plugin/trino-rest -am verify
./mvnw validate
./mvnw sortpom:verify
```

When a command fails:

1. report exact failing test/check;
2. determine whether caused by current changes;
3. fix current-scope cause;
4. rerun focused command;
5. do not suppress check or skip tests to claim success.

## Review phase

Review the diff independently from implementation intent.

Checklist:

- only planned files changed;
- no generated/build artifacts;
- no broad dependency addition;
- no code copied with incompatible license/style;
- no full-response buffering;
- no dropped residual predicates;
- no false limit/Top-N guarantee;
- no Cartesian request expansion;
- no worker registry call/mutable alias resolution;
- no secret in serialization/logging;
- no permit/stream/future leak;
- cancellation path covered;
- failure uses correct error category;
- tests assert behavior and remote request shape.

For complex work, use a separate reviewer agent/session after focused tests. Address findings before beginning next work item.

## Completion report format

```text
Work item completed: <number/title>

Implemented:
- <behavior>

Files changed:
- <path>

Validation:
- <command>: PASS/FAIL

Acceptance evidence:
- AC1: <test/method/result>
- AC2: <test/method/result>

Known limitations:
- <only explicit current-scope limitation>

Next eligible work item:
- <number/title>
```

Do not report `done` when:

- focused tests were not run;
- formatter/check failed;
- acceptance criterion lacks evidence;
- a temporary skeleton silently returns data;
- relevant diff was not reviewed.

## Task decomposition template

For every work-item file, create an implementation plan using:

```markdown
### Task <work-item>.<sequence> — <name>

**Goal**

**Depends on**

**Files**

**Existing references to inspect**

**Implementation steps**
1.
2.
3.

**Invariants**

**Failure behavior**

**Tests**

**Commands**

**Definition of done**
```

Keep each task small enough for one focused diff, normally 2–6 production files plus related tests.

## Recommended initial task sequence

### 01 — Foundation

```text
01.1 root/module POM
01.2 plugin/factory/service loader
01.3 connector/module/lifecycle
01.4 packaging and factory tests
```

### 02 — Configuration

```text
02.1 catalog config model/defaults
02.2 conditional/cross-field validation
02.3 session properties and hard-limit reductions
02.4 configuration tests
```

### 03 — Contract model

```text
03.1 identity/schema/table/column records
03.2 request/filter/system bindings
03.3 pagination polymorphism
03.4 JSON codec and validation
03.5 canonical fingerprint/golden tests
```

### 04–08 — Control/planning model

```text
04.1 registry transport
04.2 verification/cache/single-flight
05.1 API definition model
05.2 compiler phases/diagnostics
06.1 scalar codecs
06.2 structural streaming codecs
07.1 catalog model/metadata
07.2 handles/splits serialization
08.1 filter inference
08.2 limit/projection/pagination/TopN inference
```

### 09–13 — Optimizer/request/splits

```text
09.1 equality/range
09.2 IN batching/residual/null
09.3 required predicate validation
10.1 limit
10.2 projection/dereference
10.3 TopN
11.1 path/query/header serializers
11.2 JSON body/system bindings/preview
12.1 none/page/offset
12.2 cursor/next-link/loop detection
13.1 dynamic filter wait/intersection
13.2 split strategies/caps
```

### 14–17 — Runtime safety/data path

```text
14.1 network policy
14.2 auth profiles/OAuth/mTLS
14.3 streaming HTTP client
15.1 budgets/accounting
15.2 retry/rate/concurrency/cancel
16.1 parser/decoder/page builder
16.2 async page-source state machine
16.3 limit/pagination/close/resource safety
17.1 error codes/remote mapping
17.2 metrics/tracing/redaction
```

### 18 — Integration

```text
18.1 testing registry/API server
18.2 QueryRunner/metadata/basic reads
18.3 pushdown/plan/pagination tests
18.4 security/resilience/streaming tests
18.5 module validation/packaging gate
```

## Stop conditions

Stop and report blocker rather than guessing when:

- target SPI signature/pattern is ambiguous after source inspection;
- required dependency is not allowed by repository management;
- exact semantics cannot be proven from compiled contract;
- a work item conflicts with a current Trino interface invariant;
- security implementation cannot enforce claimed URI/DNS behavior;
- test harness cannot prove streaming/cancellation behavior.

A partial, tested implementation with explicit unsupported behavior is preferable to a permissive or incorrect implementation.