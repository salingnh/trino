# REST Connector — Detailed Implementation Plan

## 1. Purpose

This document decomposes the REST Connector epic into implementable workstreams for the Trino repository. It is intentionally written as an engineering delivery plan rather than a user guide.

The initial target is a production-oriented, read-only connector that loads an immutable published API definition, infers safe Trino capabilities, and executes bounded REST reads through a streaming page source.

## 2. Repository and Trino development constraints

All implementation work must follow the repository guidance in `CLAUDE.md`, `.github/DEVELOPMENT.md`, and `.claude/rules/trino-config-properties.md`.

### Mandatory Java and repository conventions

- Read `.github/DEVELOPMENT.md` before writing Java code.
- Keep implementation consistent with surrounding Trino connector code.
- Add the Apache license header to every new source file.
- Use Airstyle formatting and run `./mvnw validate` before review.
- Do not use wildcard imports.
- Use braces for all `if`, `for`, and `while` bodies.
- Do not add `@author` tags.
- Avoid mocking libraries; use small hand-written test doubles or deterministic test servers.
- Prefer AssertJ for assertions.
- Prefer Guava immutable collections for deterministic iteration.
- Use `var` only when the type is obvious and readability improves.
- Avoid unexplained abbreviations in class, method, configuration, and error names.
- Avoid `default` in exhaustive enum switches.
- Use categorized connector error codes through `TrinoException`.
- Keep `pom.xml` clean and sorted; run `./mvnw sortpom:sort` when needed.
- Keep production and test code at the same quality level.

### Configuration and session-property rules

For every `*Config.java` and `*SessionProperties.java` change:

- configuration property names use dashes;
- session property names use snake case;
- every `@Config` setter has `@ConfigDescription`;
- secret-bearing properties have `@ConfigSecuritySensitive`;
- validation annotations are placed on setters, not fields;
- constructors copy resolved values instead of storing the config object;
- every config class has `ConfigAssertions` tests covering defaults and explicit mappings;
- renamed properties retain a deprecated `@LegacyConfig` alias;
- removed properties are listed in `@DefunctConfig`;
- session-property descriptions are always present.

### Build and validation gates

Minimum checks for documentation-only planning changes:

```bash
./mvnw validate
```

Minimum checks after Java implementation begins:

```bash
./mvnw -pl plugin/trino-rest -am test
./mvnw -pl plugin/trino-rest -am validate
./mvnw -pl plugin/trino-rest -am airstyle:check
```

Before a production-candidate pull request:

```bash
./mvnw -pl plugin/trino-rest -am clean verify
```

Use the branch-scoped Maven repository option when multiple branches or worktrees are built concurrently.

## 3. Delivery assumptions

- The connector module will be named `trino-rest` and use connector name `rest` unless a naming review decides otherwise.
- The connector is built against the exact Trino SPI version in this repository.
- One virtual table maps to one logical read operation.
- Only `GET` and explicitly read-only `POST` are supported initially.
- The registry/control-plane service is external to this repository.
- The connector loads a compiled immutable contract through an authenticated registry client.
- OpenAPI support is not part of the runtime data path. A later control-plane adapter may use OpenAPI to generate a draft API definition.
- Workers execute remote requests; only the coordinator loads and compiles registry metadata.
- Response caching is disabled in the initial release.
- Service-account authentication is the initial identity model.

## 4. Proposed module layout

```text
plugin/trino-rest/
├── pom.xml
├── src/main/java/io/trino/plugin/rest/
│   ├── RestPlugin.java
│   ├── RestConnectorFactory.java
│   ├── RestConnector.java
│   ├── RestModule.java
│   ├── RestTransactionHandle.java
│   ├── RestErrorCode.java
│   ├── config/
│   ├── contract/
│   ├── metadata/
│   ├── predicate/
│   ├── request/
│   ├── split/
│   ├── pagination/
│   ├── execution/
│   ├── decoder/
│   ├── authentication/
│   ├── security/
│   ├── resilience/
│   └── observability/
└── src/test/java/io/trino/plugin/rest/
    ├── contract/
    ├── predicate/
    ├── pagination/
    ├── execution/
    ├── security/
    └── TestRestConnectorTest.java
```

The final package structure should follow actual cohesion discovered during implementation. Avoid creating interfaces or packages before at least two implementations or a clear test seam requires them.

## 5. Workstream overview

| ID | Workstream | Primary output | Phase |
|---|---|---|---|
| WS-01 | Module foundation | loadable `rest` plugin | 1 |
| WS-02 | Configuration model | validated, redacted catalog properties | 1 |
| WS-03 | Contract registry client | immutable contract retrieval | 1 |
| WS-04 | Contract model and compiler | deterministic runtime model | 1 |
| WS-05 | Type system and metadata | schemas, tables, and columns | 1 |
| WS-06 | Table and column handles | stable coordinator/worker wire model | 1 |
| WS-07 | Capability inference | inferred capability report | 1–2 |
| WS-08 | Predicate translation | exact and residual filter handling | 2 |
| WS-09 | Projection pushdown | remote/local projection pruning | 2 |
| WS-10 | Limit pushdown | safe remote limits and guarantee reporting | 2 |
| WS-11 | Request planning | deterministic SQL-to-HTTP plan | 2 |
| WS-12 | Pagination engine | page, offset, cursor, and next-link state machines | 2–3 |
| WS-13 | Split planning | bounded request partitions | 2–4 |
| WS-14 | Streaming page source | incremental JSON-to-Page execution | 2 |
| WS-15 | Authentication | named service-account profiles | 3 |
| WS-16 | Network security | SSRF, redirect, header, and origin controls | 3 |
| WS-17 | Resilience and budgets | retries, limits, concurrency, cancellation | 3 |
| WS-18 | Errors and diagnostics | actionable connector error model | 3 |
| WS-19 | Observability | metrics and tracing hooks | 3 |
| WS-20 | Top-N and dynamic filtering | advanced read optimization | 4 |
| WS-21 | Statistics | bounded cost estimates | 4 |
| WS-22 | QueryRunner and system testing | end-to-end SPI verification | all |
| WS-23 | Documentation and deployment | operator and SQL user documentation | 4 |
| WS-24 | Control-plane API contract | registry interoperability specification | 1–5 |

## 6. Detailed workstreams

## WS-01 — Module foundation

### Goal

Create a minimal Trino plugin that loads successfully and exposes an empty read-only connector.

### Tasks

1. Add `plugin/trino-rest` to the root module list in sorted order.
2. Create a `trino-plugin` module `pom.xml` following an existing modern connector module.
3. Implement:
   - `RestPlugin`;
   - `RestConnectorFactory`;
   - `RestModule`;
   - `RestConnector`;
   - singleton `RestTransactionHandle`.
4. Register the connector name and strict SPI version matching.
5. Return read-only isolation and transaction behavior consistent with connector capabilities.
6. Add a minimal `RestQueryRunner` test utility.
7. Add plugin discovery tests.

### Acceptance criteria

- The plugin ZIP is generated.
- Trino loads `connector.name=rest` without classloader or service-provider errors.
- `SHOW SCHEMAS` returns a deterministic result from a test contract.
- Unsupported write SPI methods are not exposed.

### Tests

- connector factory creation;
- strict SPI match;
- plugin service discovery;
- transaction handle serialization if serialized by the SPI path.

### Dependencies

None.

---

## WS-02 — Configuration model

### Goal

Define a minimal, safe catalog configuration that references an immutable registry contract and enforces connector-wide hard limits.

### Proposed configuration groups

#### Registry identity

```properties
rest.registry-uri=https://integration.internal/api/rest-contracts
rest.contract-id=crm-api
rest.contract-version=12
rest.contract-fingerprint=sha256:...
```

#### Data-plane boundary

```properties
rest.base-uri=https://crm-api.internal
rest.allowed-hosts=crm-api.internal
rest.allow-insecure-http=false
rest.redirects-enabled=false
```

#### HTTP limits

```properties
rest.http.connect-timeout=5s
rest.http.request-timeout=30s
rest.http.max-response-size=32MB
rest.http.max-decompressed-response-size=128MB
rest.http.max-connections-per-server=20
```

#### Query hard limits

```properties
rest.query.max-requests=1000
rest.query.max-pages=10000
rest.query.max-rows=1000000
rest.query.max-bytes=10GB
rest.query.max-splits=1000
rest.query.max-concurrent-requests=8
```

#### Pushdown feature switches

```properties
rest.filter-pushdown.enabled=true
rest.projection-pushdown.enabled=true
rest.limit-pushdown.enabled=true
rest.topn-pushdown.enabled=false
rest.dynamic-filtering.enabled=false
```

### Tasks

1. Implement focused config classes instead of one large configuration object.
2. Add `@ConfigDescription` to every setter.
3. Mark secret references and credential-bearing values with `@ConfigSecuritySensitive`.
4. Put validation annotations on setters.
5. Copy configuration values into runtime services; do not retain config objects.
6. Define hard-limit precedence: connector hard limit, table limit, then session limit, always selecting the most restrictive value.
7. Add session properties only when per-query variation is safe. Session properties may disable or reduce behavior, not broaden the network trust boundary or raise hard limits.
8. Add complete `ConfigAssertions` tests.

### Acceptance criteria

- Missing required registry identity fails startup clearly.
- Invalid schemes, hosts, durations, sizes, and numeric bounds fail startup.
- Secret-bearing values are redacted from configuration diagnostics.
- Every config property follows dash naming and every session property follows snake-case naming.

### Tests

- defaults;
- explicit mappings;
- invalid values;
- secret redaction;
- precedence between catalog, table, and session limits;
- legacy and defunct behavior when properties are renamed later.

### Dependencies

WS-01.

---

## WS-03 — Contract Registry client

### Goal

Allow the coordinator to retrieve one immutable compiled contract by ID and version without exposing registry credentials to workers.

### Tasks

1. Define a narrow registry HTTP API model:
   - contract ID;
   - immutable version;
   - fingerprint;
   - content type and schema version;
   - compiled contract payload.
2. Implement coordinator-only registry loading.
3. Support ETag or content fingerprint verification.
4. Separate registry authentication from data-API authentication.
5. Cache the loaded immutable version for the connector lifetime.
6. Fail catalog initialization if the requested version is absent, malformed, unsupported, or has a fingerprint mismatch.
7. Do not implement mutable `latest` refresh in the initial release.
8. Expose safe registry-load diagnostics without response-body leakage.

### Acceptance criteria

- Workers never contact the registry.
- Registry credentials never enter table handles or splits.
- Contract fingerprint is verified before metadata publication.
- Registry unavailability after successful load does not affect active queries.

### Tests

- successful retrieval;
- authentication failure;
- missing version;
- fingerprint mismatch;
- malformed payload;
- oversized registry response;
- worker-side object graph contains no registry client or registry secret.

### Dependencies

WS-02, WS-16 foundations.

---

## WS-04 — Contract model and compiler

### Goal

Compile the registry payload into a deterministic, immutable runtime model independent of the transport JSON model.

### Core model

```text
RestCatalogDefinition
  └── RestSchemaDefinition
       └── RestTableDefinition
            ├── RestOperationDefinition
            ├── RestColumnDefinition[]
            ├── RestRequestFieldDefinition[]
            ├── RestBindingDefinition[]
            ├── InferredCapabilities
            ├── PaginationDefinition
            ├── AuthenticationReference
            └── RuntimeLimits
```

### Tasks

1. Define versioned registry DTOs separately from runtime definitions.
2. Normalize names and detect collisions deterministically.
3. Validate one logical read operation per table.
4. Validate path placeholders and request bindings.
5. Compile response pointers and request-body pointers.
6. Resolve remote field names and Trino column names.
7. Build immutable maps and lists with deterministic order.
8. Calculate or verify table and catalog fingerprints excluding secrets.
9. Reject unsupported contract schema versions.
10. Produce a structured validation report with errors and warnings.

### Acceptance criteria

- The same contract produces equal runtime models and fingerprints across runs.
- Invalid references fail before metadata is visible.
- Transport DTO classes are not serialized in Trino handles.
- No mutable collection is retained in the runtime model.

### Tests

- golden compiled-model tests;
- deterministic ordering;
- duplicate table/column detection;
- path-placeholder validation;
- unsupported version handling;
- fingerprint stability;
- secret exclusion from fingerprints and diagnostics.

### Dependencies

WS-03.

---

## WS-05 — Type system and metadata

### Goal

Expose stable Trino schemas, tables, columns, comments, hidden columns, and types.

### Initial type mapping

| API type | Trino type |
|---|---|
| boolean | `BOOLEAN` |
| int32 | `INTEGER` |
| int64 | `BIGINT` |
| float | `REAL` |
| double | `DOUBLE` |
| decimal with explicit precision/scale | `DECIMAL(p,s)` |
| string | `VARCHAR` |
| date | `DATE` |
| date-time with offset | `TIMESTAMP(p) WITH TIME ZONE` |
| binary/base64 | `VARBINARY` when explicitly enabled |
| stable array | `ARRAY(T)` |
| stable object | later `ROW(...)`; MVP may use `JSON` |
| homogeneous dictionary | later `MAP(VARCHAR,T)`; MVP may use `JSON` |
| unstable or polymorphic content | `JSON` |

### Tasks

1. Implement `RestMetadata` schema and table discovery.
2. Implement type parsing and validation through `TypeManager` where appropriate.
3. Distinguish output columns from hidden input-only columns.
4. Preserve remote request/response pointers in column handles, not in user-visible comments unless safe.
5. Define nullable behavior for missing and JSON `null`.
6. Add `DESCRIBE` and metadata golden tests.
7. Define table comments that identify the remote operation without exposing sensitive URI values.

### Acceptance criteria

- Metadata ordering is deterministic.
- Unsupported types fail contract compilation or fall back to configured `JSON` behavior.
- Hidden request columns are usable in predicates but not returned by `SELECT *`.
- Date-time decoding preserves offsets.

### Tests

- all primitive types;
- missing versus null;
- invalid decimal precision;
- timestamp offsets;
- hidden-column behavior;
- `SHOW SCHEMAS`, `SHOW TABLES`, `DESCRIBE`, and `SELECT *` metadata behavior.

### Dependencies

WS-04.

---

## WS-06 — Table and column handles

### Goal

Define small, immutable, JSON-serializable handles that preserve optimizer decisions across the coordinator/worker boundary.

### Proposed `RestTableHandle` content

- table identity;
- contract ID, version, and fingerprint;
- enforced constraint;
- prefilter bindings;
- projected columns;
- limit and guarantee state;
- Top-N state when implemented;
- request-plan identity;
- runtime hard-limit snapshot.

### Proposed `RestColumnHandle` content

- stable column ID;
- SQL name and type;
- response pointer or request binding identity;
- hidden/input/output role;
- remote field name;
- decoder descriptor.

### Tasks

1. Define Jackson-serializable records or immutable classes.
2. Ensure equality reflects semantic state exactly.
3. Keep runtime clients, credentials, parser objects, and mutable state out of handles.
4. Add round-trip serialization tests.
5. Add optimizer fixpoint tests proving unchanged pushdowns return `Optional.empty()`.

### Acceptance criteria

- Handles round-trip across JSON serialization.
- Reapplying the same filter, projection, or limit does not create a different handle.
- No secret or complete request body is serialized into a handle.

### Dependencies

WS-04, WS-05.

---

## WS-07 — Capability inference engine

### Goal

Infer connector capabilities from typed API definition and explicit bindings while producing reasons and warnings.

### Inference outputs

- filter operators by column;
- exact, approximate, or residual enforcement;
- required-predicate rules;
- remote limit capability and maximum;
- pagination family;
- remote projection capability;
- remote sort capability;
- Top-N guarantee status;
- batching capability and maximum values;
- warnings and required overrides.

### Tasks

1. Implement rule-based inference with explicit inputs and deterministic output.
2. Do not infer semantics from field names alone.
3. Require explicit operator bindings for filters.
4. Infer cursor pagination only from cursor role plus continuation metadata.
5. Infer page or offset pagination from explicit semantic roles.
6. Infer remote projection only from projection role plus remote field mapping.
7. Infer Top-N ordering but not guarantee without stable-order declarations.
8. Support explicit override only for recognized ambiguity categories.
9. Record a machine-readable reason for every supported, unsupported, and non-guaranteed capability.

### Acceptance criteria

- The same definition always produces the same capability report.
- Generic `q` or `search` fields never become exact equality automatically.
- Missing stability metadata prevents guaranteed Top-N.
- Required request fields sourced from constants or sessions do not become required SQL predicates.

### Tests

- equality, `IN`, and range inference;
- approximate search;
- all pagination families;
- projection inference;
- stable and unstable ordering;
- required predicate sources;
- invalid or conflicting overrides.

### Dependencies

WS-04.

---

## WS-08 — Predicate translation and `applyFilter`

### Goal

Translate only semantically equivalent constraints into request bindings and return all unhandled predicates to Trino.

### Tasks

1. Implement translation from `TupleDomain<ColumnHandle>` to typed request bindings.
2. Support initial exact operators:
   - equality;
   - `IN` for documented list encodings;
   - numeric and temporal bounds;
   - bounded ranges.
3. Preserve inclusive and exclusive boundary semantics.
4. Separate:
   - enforced predicates;
   - approximate remote prefilters;
   - remaining predicates.
5. Validate required predicates during planning.
6. Bound domain expansion before materialization.
7. Return `Optional.empty()` when no new constraint is applied.
8. Never discard an unsupported constraint summary.

### Acceptance criteria

- Exact predicates disappear from the Trino residual plan only when fully enforced.
- Approximate predicates are sent remotely and retained locally.
- Unsupported predicates remain in Trino.
- Required predicates fail with an actionable message and SQL example.
- Large `IN` domains are batched, retained as residual, or rejected according to a bounded policy; they never produce uncontrolled Cartesian splits.

### Tests

- exact equality;
- `IN` encodings;
- lower/upper ranges and inclusivity;
- mixed pushed and residual filters;
- approximate filtering;
- `TupleDomain.none()`;
- repeated optimizer application;
- required-filter failures;
- expansion-limit behavior.

### Dependencies

WS-06, WS-07.

---

## WS-09 — Projection pushdown

### Goal

Reduce local decoding and, when supported, remote response fields.

### Tasks

1. Implement `applyProjection` for direct columns.
2. Add nested dereference support after structured types are introduced.
3. Record projected columns in `RestTableHandle`.
4. Map projected columns to remote field names for projection-capable APIs.
5. Include mandatory technical fields required for pagination, tie-breaking, or decoding.
6. Ensure repeated projection application reaches optimizer fixpoint.

### Acceptance criteria

- Unselected fields are not decoded.
- Remote projection sends only approved remote field names when supported.
- Required pagination or tie-breaker fields remain included without becoming user-visible output.
- Projection pushdown never changes row count or semantics.

### Tests

- direct projection;
- no-op repeated projection;
- remote projection encoding;
- mandatory hidden fields;
- local decode pruning without remote projection.

### Dependencies

WS-05, WS-06, WS-11, WS-14.

---

## WS-10 — Limit pushdown

### Goal

Use SQL limits to reduce remote work without claiming guarantees that the connector cannot prove.

### Tasks

1. Implement `applyLimit`.
2. Clamp remote size to the API maximum and connector hard limit.
3. Store the smallest applied limit in the table handle.
4. Define guarantee evaluation based on:
   - no residual filter;
   - no local deduplication;
   - no row expansion;
   - remote endpoint returns at most the requested count;
   - ordering semantics when combined with Top-N.
5. Stop pagination and close the active response when enough rows have been emitted.
6. Return `Optional.empty()` when the existing limit is equal or smaller.

### Acceptance criteria

- `LIMIT 100` never requests more than the allowed remote page size unless controlled over-fetch is required and explicitly tested.
- A residual predicate prevents guaranteed-limit reporting.
- Pagination stops as soon as the connector can safely stop.
- No partial result is returned because a query budget was reached before the SQL limit.

### Tests

- lower and higher repeated limits;
- remote maximum clamping;
- residual-filter guarantee behavior;
- page-size interaction;
- early response close;
- zero-row and empty-table behavior.

### Dependencies

WS-06, WS-11, WS-12, WS-14.

---

## WS-11 — Request planning and serialization

### Goal

Create one deterministic typed request planner shared by runtime execution and SQL-to-request preview.

### Tasks

1. Define `RestRequestPlan` with:
   - method;
   - endpoint template;
   - path bindings;
   - query bindings;
   - allowlisted headers;
   - typed JSON-body bindings;
   - remote projection;
   - ordering;
   - page size and continuation state.
2. Implement path-template expansion with encoding.
3. Implement query serialization for scalar, repeated, CSV, and documented array forms.
4. Implement JSON-body writing without arbitrary string templates.
5. Define omission versus JSON-null behavior explicitly.
6. Produce a sanitized preview model with credential and sensitive-value redaction.
7. Enforce maximum URI and request-body sizes.

### Acceptance criteria

- Runtime request and preview request are generated from the same planner.
- Path, query, and body encoding are deterministic.
- Secrets and sensitive values are not present in previews or exceptions.
- Unsupported serialization styles fail contract compilation rather than at random query execution points.

### Tests

- path encoding;
- repeated and CSV query values;
- JSON nested body bindings;
- omission and null behavior;
- maximum URI/body limits;
- preview redaction;
- deterministic field ordering.

### Dependencies

WS-04, WS-07, WS-08, WS-09, WS-10.

---

## WS-12 — Pagination engine

### Goal

Implement explicit, bounded pagination state machines independent of split planning.

### Common interface

```text
initial request state
    -> execute page
    -> decode rows
    -> inspect continuation metadata
    -> complete or create next request state
```

### Strategies

1. no pagination;
2. page number;
3. offset/limit;
4. cursor/token;
5. next URL or `Link` relation.

### Tasks

1. Define a small strategy interface and serializable initial state.
2. Implement page-number conventions with first-page configuration.
3. Implement offset advancement with overflow checks.
4. Implement cursor extraction through compiled JSON Pointer.
5. Implement next-link parsing and origin validation.
6. Track seen cursors or next links with a bounded set.
7. Enforce maximum pages per split and query.
8. Handle empty pages according to explicit strategy rules.
9. Integrate query limit termination.
10. Surface best-effort consistency in metadata and diagnostics.

### Acceptance criteria

- Cursor and next-link chains execute sequentially within one split.
- Repeated continuation values fail deterministically.
- Page budgets fail the query without silent truncation.
- Next links cannot escape approved origins.
- Empty-page termination is strategy-specific, not a global assumption.

### Tests

- page base 0 and 1;
- short and empty last pages;
- offset overflow;
- cursor expiration and repetition;
- relative and absolute next links;
- malicious cross-origin next links;
- query-limit termination;
- maximum-page failures.

### Dependencies

WS-11, WS-16, WS-17.

---

## WS-13 — Split planning

### Goal

Produce bounded, independent work units without confusing pagination pages with parallel splits.

### Initial strategy

The MVP may use one initial split per table request or per bounded value batch. Cursor, next-link, and unknown-total pagination remain inside the split.

### Tasks

1. Implement `RestSplit` with table identity, partition bindings, and initial pagination state.
2. Implement `RestSplitManager`.
3. Prefer one request with array values over one split per value.
4. Batch discrete values according to contract and connector limits.
5. Estimate split count before materialization.
6. Enforce maximum split count.
7. Add dynamic-filter integration in WS-20, not the initial path.
8. Avoid page-level parallelism unless total pages and stable ordering are explicitly proven.

### Acceptance criteria

- Split planning never creates an unbounded Cartesian product.
- Pagination continuation remains private to the executing page source.
- Split handles are secret-free and serializable.
- Empty constraints produce no splits.

### Tests

- single split;
- bounded batches;
- split-count rejection;
- empty-domain behavior;
- split serialization;
- no cursor-token pre-generation.

### Dependencies

WS-06, WS-08, WS-12.

---

## WS-14 — Streaming `ConnectorPageSource`

### Goal

Read remote JSON incrementally and construct Trino pages directly.

### Tasks

1. Implement `RestPageSourceProvider`.
2. Implement `RestPageSource` as a clear state machine:
   - unopened;
   - reading current response;
   - awaiting continuation;
   - finished;
   - failed/closed.
3. Use Jackson streaming APIs.
4. Navigate to the compiled row pointer without creating a full `JsonNode` tree.
5. Decode one row at a time into `PageBuilder` and `BlockBuilder`.
6. Skip unprojected JSON subtrees.
7. Track completed bytes and read time.
8. Close streams promptly on cancellation, failure, or satisfied limit.
9. Bound JSON depth, string length, row size, response size, and decompressed size.
10. Keep decoder descriptors serializable and construct runtime decoders on workers.

### Acceptance criteria

- Time to first page does not depend on full-response materialization.
- Peak memory is bounded by page, parser, and configured response buffers rather than the complete result.
- `close()` is idempotent and releases the HTTP response.
- Only projected columns are materialized.
- Invalid values use the configured fail/null policy and are observable.

### Tests

- large streamed array;
- nested row pointer;
- early limit termination;
- cancellation;
- malformed JSON;
- oversized response and row;
- deep JSON;
- invalid type conversion;
- projected-column pruning;
- completed-byte and read-time accounting.

### Dependencies

WS-05, WS-11, WS-12, WS-15, WS-17, WS-18.

---

## WS-15 — Authentication profiles

### Goal

Support named service-account authentication without exposing credentials in SQL-visible artifacts.

### Initial profiles

- none;
- API key in approved header;
- bearer token reference;
- HTTP basic secret references;
- OAuth2 client credentials;
- mTLS when supported cleanly by the HTTP-client configuration.

### Tasks

1. Resolve table authentication reference to a catalog-approved profile.
2. Separate secret references from contract payload.
3. Implement OAuth token caching by issuer/client/scopes with expiry-aware refresh and single-flight behavior.
4. Refresh after one 401 only when the profile supports renewable credentials.
5. Prevent authentication headers from entering request previews.
6. Keep authentication objects out of handles and splits.
7. Scope clients appropriately for distinct mTLS identities.

### Acceptance criteria

- Missing profiles fail catalog initialization.
- OAuth uses token endpoint and expiry metadata correctly.
- Credentials are never logged or serialized.
- One failing profile does not poison unrelated profiles.

### Tests

- API key/header placement;
- bearer/basic redaction;
- OAuth token acquisition, reuse, expiry, refresh, and failure;
- single-flight token refresh;
- profile isolation;
- no secret in handle/split serialization.

### Dependencies

WS-02, WS-03, WS-16.

---

## WS-16 — Network and request security

### Goal

Prevent SSRF, credential forwarding, host escape, header injection, and unsafe redirects.

### Tasks

1. Canonicalize and validate base URI at startup.
2. Resolve endpoint paths only against approved origins.
3. Resolve DNS/IP targets defensively where practical and reject loopback, link-local, multicast, and metadata-service ranges.
4. Disable redirects by default.
5. If redirects are enabled, validate every target and strip credentials across origins.
6. Apply the same validation to OAuth token endpoints, registry endpoints, and next links.
7. Allow only explicitly modeled business headers.
8. Reject CR/LF and invalid header values.
9. Redact URLs and query values according to sensitivity policy.
10. Limit URI, header, request-body, and response sizes.

### Acceptance criteria

- A user-controlled SQL value cannot change scheme, host, port, or endpoint path structure.
- Cross-origin next links and redirects fail before credentials are sent.
- Sensitive headers and query parameters are redacted in all diagnostics.

### Tests

- loopback and link-local targets;
- DNS and numeric-address bypasses;
- relative-path escape attempts;
- malicious redirects and next links;
- header injection;
- credential stripping;
- URI and header size limits.

### Dependencies

WS-02.

---

## WS-17 — Resilience, budgets, rate limiting, and cancellation

### Goal

Bound remote impact and provide predictable failure behavior.

### Tasks

1. Implement per-query counters for requests, attempts, pages, rows, bytes, and splits.
2. Count retries toward request budgets.
3. Implement bounded retries for safe/idempotent operations.
4. Respect `Retry-After` within configured maximum delay.
5. Use exponential backoff with jitter.
6. Implement per-host/profile worker-local concurrency limits.
7. Implement a worker-local rate limiter as a safety valve and document that it is not cluster-global.
8. Add a scoped circuit breaker only after measurement proves it is needed; avoid prematurely adding a large dependency.
9. Propagate Trino cancellation into active HTTP requests and backoff waits.
10. Fail rather than truncate when a hard budget is exhausted.

### Acceptance criteria

- Every remote attempt is counted.
- Unsafe methods are never retried automatically.
- Cancellation stops active and pending work promptly.
- Budget exceptions include used and allowed counts without exposing request data.
- Per-worker limits are documented accurately; global quotas are delegated to an external gateway/quota service.

### Tests

- retryable and non-retryable statuses;
- connection failures;
- `Retry-After` bounds;
- cancellation during request and backoff;
- request/page/row/byte budget exhaustion;
- concurrency queue saturation;
- rate-limiter accounting.

### Dependencies

WS-02, WS-11, WS-14.

---

## WS-18 — Error codes and diagnostics

### Goal

Expose categorized, actionable, sanitized Trino errors.

### Proposed categories

- invalid contract;
- unsupported contract version;
- missing required predicate;
- invalid request mapping;
- authentication failure;
- permission denied;
- remote throttling;
- remote unavailable;
- remote timeout;
- malformed remote response;
- type conversion failure;
- pagination loop;
- query budget exceeded;
- network policy violation.

### Tasks

1. Add `RestErrorCode` with stable numeric ranges.
2. Map HTTP statuses and local failures to error categories.
3. Include safe context:
   - catalog;
   - schema/table;
   - operation identity;
   - sanitized endpoint template;
   - remote request ID;
   - attempt count;
   - page number where safe.
4. Never include credentials, complete response bodies, cursors, or sensitive predicates.
5. Treat 404 as empty only when the compiled operation explicitly declares point-lookup not-found semantics.
6. Cap remote error-message length and redact fields.

### Acceptance criteria

- Failures are categorized with connector error codes.
- Messages are actionable but safe.
- Partial results are not returned after malformed pages or budget failures.

### Tests

- all major HTTP status mappings;
- point-lookup 404 versus collection 404;
- malformed JSON;
- conversion errors;
- message truncation and redaction;
- remote request ID propagation.

### Dependencies

All execution workstreams.

---

## WS-19 — Observability

### Goal

Expose low-cardinality connector metrics and request traces suitable for operations.

### Metrics

- requests and attempts by operation/status class;
- request latency;
- response bytes;
- decoded rows;
- pagination pages;
- retries and throttle waits;
- active and queued requests;
- decode failures;
- budget failures;
- network-policy failures;
- token-refresh failures.

### Tasks

1. Add JMX-exportable metrics with bounded labels.
2. Do not use URI query, cursor, tenant, user values, or headers as labels.
3. Add tracing hooks around split execution, HTTP request, decoding, and backoff where the Trino tracing API permits.
4. Add request-plan and capability information to debug logging only in sanitized form.
5. Document dashboard and alert suggestions.

### Acceptance criteria

- Metrics identify catalog/table/operation without unbounded user-controlled cardinality.
- No secret or personal value appears in labels.
- Cancellation, retry, and budget failures are distinguishable.

### Tests

- metric increment behavior;
- low-cardinality label enforcement;
- redaction;
- success, retry, throttle, decode failure, and cancellation paths.

### Dependencies

WS-14 through WS-18.

---

## WS-20 — Top-N and dynamic filtering

### Goal

Add advanced optimizer integration after the core read path is stable.

### Top-N tasks

1. Implement `applyTopN` for explicitly supported column/direction combinations.
2. Validate stable ordering and tie-breaker requirements.
3. Handle null-ordering compatibility conservatively.
4. Combine Top-N with limit and pagination.
5. Report guarantee only when semantics are equivalent.

### Dynamic-filter tasks

1. Wait for dynamic filters only when the table can benefit and within a small configured timeout.
2. Convert discrete values into bounded batches.
3. Do not convert large value sets into uncontrolled ranges unless semantics are safe.
4. Avoid blocking split generation indefinitely.
5. Record dynamic-filter wait and pruning metrics.

### Acceptance criteria

- Unsupported sort combinations remain in Trino.
- Unstable ordering prevents guaranteed Top-N.
- Dynamic filters reduce requests for selective joins without causing request explosion.

### Tests

- ascending/descending ordering;
- tie-breaker behavior;
- null ordering;
- repeated `applyTopN` fixpoint;
- dynamic-filter timeout;
- small and oversized value sets;
- batched join lookups.

### Dependencies

Stable completion of WS-08 through WS-19.

---

## WS-21 — Table statistics

### Goal

Provide conservative row-count estimates without claiming database-quality statistics.

### Tasks

1. Read total-row metadata only when the contract identifies a trustworthy field or count endpoint.
2. Cache immutable metadata estimates for the contract lifetime or a bounded TTL.
3. Report unknown rather than fabricated statistics.
4. Avoid remote count calls during every planning operation.
5. Document uncertainty and freshness.

### Acceptance criteria

- Single-table queries do not incur unnecessary count requests.
- Join planning receives row-count estimates only when backed by declared metadata.
- Stale or absent statistics degrade to unknown safely.

### Tests

- total-row metadata;
- unknown statistics;
- count endpoint failure;
- caching behavior;
- no repeated count calls during optimizer fixpoint.

### Dependencies

WS-04, WS-12, WS-17.

---

## WS-22 — QueryRunner and testing strategy

### Goal

Provide high-value, deterministic test coverage aligned with Trino SPI expectations.

### Test layers

#### Unit tests

- contract normalization and compilation;
- type mapping;
- capability inference;
- predicate translation;
- request serialization;
- pagination state machines;
- retry decisions;
- security policy;
- redaction;
- handle serialization and equality.

#### Golden tests

Input registry contracts map to exact expected schemas, columns, capabilities, and fingerprints.

#### HTTP contract tests

Use a deterministic embedded HTTP server or existing approved Trino test utility. Do not use mocking libraries.

Cover:

- query/path/body bindings;
- pagination families;
- malformed responses;
- latency and timeout;
- retries and `Retry-After`;
- cancellation;
- redirects and malicious next links;
- oversized and compressed responses;
- authentication flows.

#### Connector SPI tests

Use QueryRunner to cover:

```sql
SHOW SCHEMAS;
SHOW TABLES;
DESCRIBE rest.default.users;
SELECT ...;
SELECT ... WHERE ...;
SELECT ... LIMIT ...;
SELECT ... ORDER BY ... LIMIT ...;
EXPLAIN ...;
JOIN ...;
```

#### Performance tests

Measure:

- time to first page;
- peak memory under large responses;
- rows per second;
- requests per output row;
- batching effectiveness;
- cancellation latency.

### Acceptance criteria

- Tests do not depend on public internet services.
- No mocking framework is introduced.
- Every pushdown has a plan-level or request-level correctness test.
- Security and budget limits have negative tests.
- Connector tests run against the exact repository Trino version.

### Dependencies

Continuous across all workstreams.

---

## WS-23 — Documentation and deployment

### Goal

Document configuration, API-definition requirements, SQL semantics, operational limitations, and deployment.

### Tasks

1. Add connector documentation under `docs/src/main/sphinx/connector/` when implementation is ready.
2. Add alphabetized references in documentation indexes.
3. Document:
   - catalog properties;
   - registry contract requirements;
   - supported request and response types;
   - supported pushdowns;
   - required predicates;
   - pagination semantics;
   - best-effort consistency;
   - retry and rate-limit behavior;
   - security boundaries;
   - metrics;
   - examples and troubleshooting.
4. Add release notes only when feature scope is stable.
5. Add development-server catalog example using a local deterministic test service.

### Acceptance criteria

- Every configuration property is documented with default and security implications.
- Limit and Top-N guarantee limitations are explicit.
- Users understand that per-node rate limits are not global quotas.
- No documentation recommends inline plaintext secrets.

### Dependencies

All implemented features.

---

## WS-24 — Registry interoperability specification

### Goal

Define the connector-facing API contract for the external Contract Registry without implementing the full control plane in this repository.

### Required endpoints or equivalent behavior

- fetch immutable contract by ID/version;
- fetch contract metadata and fingerprint;
- optional health and compatibility metadata;
- no draft mutation from the connector.

### Required published payload

- contract schema version;
- contract ID/version/fingerprint;
- catalog defaults;
- schemas/tables;
- request/response definitions;
- inferred capabilities and warnings;
- runtime limits;
- authentication profile references only;
- no secret values.

### Tasks

1. Define JSON schema or Java transport DTOs with clear versioning.
2. Define compatibility rules for additive and breaking changes.
3. Define maximum payload size.
4. Define registry authentication and TLS requirements.
5. Define immutable version and alias semantics.
6. Define how the control plane obtains capability-preview output from the same compiler rules.

### Acceptance criteria

- Connector and registry can evolve through explicit schema versions.
- Published versions are immutable.
- A contract does not contain credentials.
- The connector can reject unsupported versions before exposing partial metadata.

### Dependencies

WS-03, WS-04, WS-07.

## 7. Implementation sequence and quality gates

## Milestone M0 — Design freeze

### Deliverables

- approved epic;
- approved registry payload schema;
- approved module name and catalog property names;
- representative endpoint fixtures;
- explicit decisions for nested JSON, authentication, consistency, and scan safety.

### Exit gate

No Java implementation starts until ambiguous user-facing semantics have a conservative default or explicit override mechanism.

## Milestone M1 — Loadable connector and metadata

### Workstreams

WS-01 through WS-07.

### Demo

- create a catalog referencing an immutable test contract;
- run `SHOW SCHEMAS`, `SHOW TABLES`, and `DESCRIBE`;
- display an inferred capability report in a test utility.

### Exit gate

- module tests pass;
- config tests pass;
- contract and handle serialization is stable;
- `./mvnw -pl plugin/trino-rest -am validate` passes.

## Milestone M2 — Correct core query path

### Workstreams

WS-08 through WS-14 for no-pagination, page, offset, and cursor reads.

### Demo

- execute filtered `SELECT` with projection and limit;
- inspect remote requests;
- show residual predicates in `EXPLAIN`;
- stream a large response with bounded memory;
- cancel an active query.

### Exit gate

- exact versus residual behavior is covered by query and HTTP tests;
- limit guarantee behavior is covered;
- repeated-cursor and budget failures are covered;
- no full-response buffering remains in the execution path.

## Milestone M3 — Production safety

### Workstreams

WS-15 through WS-19.

### Demo

- authenticated query;
- retry after 429/503;
- blocked malicious redirect/next link;
- safe redacted error;
- metrics for request, retry, rows, bytes, and cancellation.

### Exit gate

- security test suite passes;
- secrets are absent from serialized artifacts and logs;
- request/row/page/byte limits are enforced;
- connector-specific error codes are stable.

## Milestone M4 — Advanced optimization

### Workstreams

WS-20 and WS-21 plus next-link pagination and richer structured types.

### Demo

- Top-N pushdown for a stable API sort;
- dynamic-filter-driven batched lookup;
- conservative statistics used by the optimizer.

### Exit gate

- no optimizer fixpoint loops;
- Top-N guarantee tests cover null order and tie-breakers;
- dynamic filtering cannot exceed configured batch and request limits.

## Milestone M5 — Release candidate

### Workstreams

WS-22 through WS-24 and final documentation.

### Exit gate

- `clean verify` passes for the connector and required dependencies;
- documentation is complete and indexed;
- compatibility is verified against the exact target Trino release;
- performance evidence demonstrates streaming memory behavior;
- operator runbook covers registry outage, upstream throttling, schema mismatch, and rollback.

## 8. Suggested backlog decomposition

The following issue-sized tasks are intended to be independently reviewable where possible.

### Foundation

- RC-001: add `trino-rest` Maven module and plugin skeleton;
- RC-002: add connector config classes and config assertions;
- RC-003: define REST connector error codes;
- RC-004: add test QueryRunner and deterministic HTTP test server.

### Contract and metadata

- RC-010: define registry transport schema;
- RC-011: implement coordinator registry client;
- RC-012: compile immutable catalog model;
- RC-013: add deterministic type mapping;
- RC-014: expose schemas/tables/columns;
- RC-015: add handle serialization and fixpoint tests;
- RC-016: implement capability inference report.

### Query planning

- RC-020: implement equality filter pushdown;
- RC-021: implement bounded `IN` serialization and batching;
- RC-022: implement numeric/temporal range pushdown;
- RC-023: implement approximate prefilter and residual handling;
- RC-024: implement required-predicate validation;
- RC-025: implement projection pushdown;
- RC-026: implement limit pushdown and guarantee calculation;
- RC-027: implement typed request planner and sanitized preview.

### Pagination and execution

- RC-030: implement no-pagination and page strategies;
- RC-031: implement offset strategy;
- RC-032: implement cursor strategy and loop detection;
- RC-033: implement next-link strategy and origin validation;
- RC-034: implement split manager and bounded batching;
- RC-035: implement streaming JSON row reader;
- RC-036: implement page/block decoders;
- RC-037: implement cancellation and early close;
- RC-038: enforce JSON/row/response size limits.

### Security and resilience

- RC-040: add API-key and bearer profiles;
- RC-041: add OAuth2 client-credentials profile;
- RC-042: add host, redirect, and SSRF policy;
- RC-043: add request/page/row/byte budgets;
- RC-044: add retry policy and `Retry-After` handling;
- RC-045: add concurrency and worker-local rate limiting;
- RC-046: add safe HTTP status mapping and diagnostics;
- RC-047: add JMX metrics and trace hooks.

### Advanced optimization

- RC-050: add Top-N pushdown;
- RC-051: add dynamic filtering and batched lookups;
- RC-052: add conservative table statistics;
- RC-053: add structured `ROW`, `ARRAY`, and `MAP` decoding;
- RC-054: add contract refresh design after MVP stability.

### Release

- RC-060: add connector documentation;
- RC-061: add development-server sample;
- RC-062: add performance and memory tests;
- RC-063: add security regression suite;
- RC-064: complete release notes and operator runbook.

## 9. Review strategy

Review each feature slice before starting the next dependent slice.

Recommended order:

1. module/config review;
2. contract/model review;
3. metadata/handle review;
4. filter review;
5. projection and limit review;
6. pagination review;
7. page-source review;
8. security/resilience review;
9. advanced optimizer review;
10. release and documentation review.

Every review should verify:

- Trino SPI correctness;
- optimizer fixpoint behavior;
- residual predicate preservation;
- handle serialization stability;
- memory and cancellation behavior;
- secret and user-data redaction;
- negative and boundary tests;
- config naming and descriptions;
- adherence to `.github/DEVELOPMENT.md`.

## 10. Risks and mitigations

| Risk | Mitigation |
|---|---|
| API semantics are declared incorrectly | conservative inference, warnings, exact/approximate distinction, preview and contract tests |
| request amplification | batching, split estimation, hard request/page/split budgets, required predicates |
| response memory pressure | streaming parser, direct page construction, page/row/response limits |
| schema drift | immutable published version and fingerprint, no mid-query refresh |
| unstable pagination | explicit strategy, stable-order warning, repeated-token detection, best-effort consistency |
| connector/Trino incompatibility | build and test against the exact repository SPI version |
| global upstream quota exceeded | external gateway or distributed quota service; per-worker limits only as local safety valves |
| leaked credentials | secret references, `@ConfigSecuritySensitive`, redaction tests, no secrets in handles/splits |
| optimizer loop | immutable equality-stable handles and repeated-application tests |
| incomplete results | fail on budget, decode, pagination, or remote partial errors; never truncate silently |

## 11. Definition of done for the MVP

The MVP is complete only when all of the following hold:

- a published immutable contract can create stable virtual-table metadata;
- exact equality, `IN`, and range filters are pushed correctly;
- unsupported and approximate predicates remain residual;
- projection and limit pushdown reach optimizer fixpoint;
- no-pagination, page, offset, and cursor APIs execute correctly;
- execution uses streaming `ConnectorPageSource` and direct page construction;
- cancellation closes active requests;
- request, page, row, byte, split, and concurrency budgets are enforced;
- authentication profiles and network allowlists are applied without secret leakage;
- errors are categorized and sanitized;
- metrics cover the critical data path;
- QueryRunner, contract, security, and configuration tests pass;
- Maven validation and Airstyle checks pass;
- connector documentation states supported semantics and limitations;
- the artifact is built for and tested against the exact target Trino version.
