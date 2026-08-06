# REST Connector — Product Epic

## Status

- **Epic:** REST-backed virtual tables for Trino
- **Target module:** `plugin/trino-rest`
- **Initial delivery:** read-only
- **Primary users:** data engineers, backend API owners, data-platform administrators, and SQL users
- **Compatibility target:** the exact Trino version of this repository branch

## Product vision

Provide a native Trino connector that turns a documented REST endpoint into a stable virtual table without requiring the user to understand Trino SPI internals or manually declare every optimizer capability.

The user describes the API using API-oriented concepts:

- endpoint method and path;
- path, query, header, or JSON-body inputs;
- input types and semantic roles;
- response row location and response fields;
- pagination continuation metadata;
- authentication profile reference;
- only the semantic facts that cannot be inferred safely.

The platform compiles that API definition into an immutable connector model that:

- exposes schemas, tables, and columns to Trino;
- converts SQL predicates, projections, limits, and ordering into remote requests;
- converts streamed JSON responses into Trino pages;
- preserves residual predicates when remote semantics are not equivalent;
- protects the upstream API with bounded request, row, byte, page, split, and concurrency budgets;
- reports why a capability was or was not inferred.

## Problem statement

REST APIs are procedural, while Trino requires a relational and optimizer-aware contract. Existing integration approaches force one of two poor choices:

1. implement a connector for every API; or
2. expose a large, connector-specific configuration that asks users to understand `applyFilter`, `applyLimit`, `applyProjection`, `applyTopN`, split planning, and page-source execution.

A usable feature must avoid both extremes. It must let API owners describe what their endpoint accepts and returns, then infer only the capabilities that are semantically justified.

The connector must also prevent common failure modes:

- treating fuzzy search as SQL equality;
- claiming a limit is guaranteed while residual filtering remains;
- assuming a sort field implies stable SQL ordering;
- inferring pagination only from parameter names;
- generating a Cartesian explosion of requests from multi-value predicates;
- buffering large responses in memory;
- following untrusted next links or redirects;
- leaking credentials through catalog properties, handles, splits, logs, or metrics;
- continuing unbounded scans against APIs that require selective filters.

## Product principles

### API-first user model

Users define the API, not Trino SPI behavior. The connector owns the compilation from API definition to connector capabilities.

### Inference plus explicit override

The inference engine produces capabilities from typed input/output definitions and bindings. Overrides are reserved for facts that cannot be proven from structure alone, such as exact versus approximate search, stable ordering, cursor consistency, or collection semantics.

### Correctness before pushdown rate

A remote parameter is not considered an exact pushdown unless its semantics match Trino. Unsupported or approximate predicates remain residual.

### Immutable planning model

The coordinator uses a compiled, immutable catalog model. Query handles carry the contract identity and applied pushdowns. Workers do not load or reinterpret the source API definition.

### Streaming execution

The production data path uses `ConnectorPageSourceProvider` and direct page/block construction. Full-response materialization is not an accepted implementation strategy.

### Self-service with governance

Users can draft, validate, preview, and publish API definitions without filesystem access to the Trino cluster. Production definitions are versioned and immutable.

### Safe-by-default remote access

The connector operates within a fixed network and credential boundary. Arbitrary hosts, unsafe redirects, unrestricted headers, and inline secrets are rejected by default.

## User-facing model

The user-facing artifact is an **API Definition**, not the compiled connector contract.

An API Definition contains:

- data-source identity and base URI reference;
- one or more virtual-table definitions;
- endpoint method and relative path;
- typed request fields;
- semantic role for system fields such as limit, page, offset, cursor, projection, or sort;
- response row pointer and typed output fields;
- bindings from SQL column/operator to request field;
- continuation pointers for cursor or next-link pagination;
- optional semantic confirmations and overrides.

The system-generated artifact is a **Compiled REST Contract** containing:

- normalized schemas, tables, and columns;
- request encoders and response decoders;
- exact, approximate, and unsupported predicate capabilities;
- required-predicate rules;
- projection, limit, Top-N, and pagination capabilities;
- split-planning constraints;
- authentication references;
- runtime guardrails;
- contract version and fingerprint.

## Control-plane boundary

The production design assumes an external Contract Registry or integration control plane.

The registry is responsible for:

- draft storage;
- API-definition validation;
- response sampling and type suggestions;
- capability preview;
- SQL-to-request dry-run preview;
- immutable publishing;
- version, alias, fingerprint, approval, and audit management.

The Trino repository is responsible for:

- registry client and contract loading;
- compiled-model validation;
- metadata and optimizer integration;
- split planning;
- HTTP execution;
- JSON decoding;
- security, resilience, metrics, and Trino-facing errors.

The connector must not require users to place YAML files on coordinator or worker filesystems.

## Primary user journeys

### Create a REST data source

The platform administrator selects:

- base URI or approved service identity;
- authentication profile;
- allowed host policy;
- timeout and hard-limit policy.

### Create a virtual table

The API owner supplies:

- schema and table name;
- `GET` or read-only `POST` endpoint;
- request-field definitions;
- sample response or response-field definitions;
- response row pointer;
- filter bindings;
- pagination continuation metadata.

### Preview inferred capabilities

The platform displays:

- exposed columns and Trino types;
- supported filter operators by column;
- exact versus approximate enforcement;
- required predicates;
- remote and local projection behavior;
- remote limit and guarantee conditions;
- pagination family;
- Top-N support and guarantee conditions;
- warnings with reasons.

### Explain SQL to request

For a sample SQL statement, the platform displays a sanitized request plan:

- method and endpoint template;
- path/query/body bindings;
- pushed predicates;
- residual predicates;
- selected remote fields;
- remote limit;
- ordering;
- initial pagination state;
- estimated request or split expansion.

No remote call is made in dry-run mode.

### Publish and query

A published immutable contract version is referenced by the Trino catalog. The coordinator loads and validates it, then exposes the virtual tables to SQL users.

## Capability-inference rules

### Filter pushdown

The connector can infer:

- equality from an explicit equality binding to a scalar request field;
- `IN` from an explicit binding to an array or documented repeated-value field;
- ranges from lower- and upper-bound bindings with matching inclusivity;
- required equality predicates from required request inputs whose source is a SQL predicate.

The connector does not infer exact equality from generic search parameters.

### Limit pushdown

A request field with semantic role `limit` produces a remote-limit capability and remote maximum. The connector reports a guaranteed limit only when no residual filtering, local deduplication, row expansion, or other local operation can reduce or reorder the remote result.

### Projection

A request field with semantic role `projection` enables remote projection after remote-field mappings are validated. Without remote projection, the page source still performs local decode pruning.

### Pagination

Pagination is inferred from explicit semantic roles and response continuation metadata:

- `limit` plus `offset` implies offset pagination;
- `limit` plus `page` implies page-number pagination;
- `cursor` plus next-cursor pointer implies cursor pagination;
- next-link metadata implies next-link pagination.

Loop detection, page budgets, origin validation, and credential-forwarding policy remain connector guardrails rather than inferred API behavior.

### Top-N

Sort bindings permit remote ordering. Guaranteed Top-N additionally requires declared and validated ordering semantics, supported directions, null ordering or a conservative restriction, and stable ordering with a tie-breaker where necessary.

### Conservative defaults

When evidence is incomplete:

- filter enforcement is residual or approximate;
- limit remains non-guaranteed;
- ordering remains non-guaranteed;
- consistency is `best-effort`;
- runtime scan limits remain enforced.

## MVP scope

### Included

- new `plugin/trino-rest` module;
- one logical read operation per virtual table;
- `GET` and explicitly read-only `POST`;
- JSON requests and responses;
- path, query, allowlisted header, and JSON-body inputs;
- scalar response columns and `JSON` fallback for nested or unstable structures;
- hidden input-only columns;
- equality, `IN`, and numeric/temporal range pushdown;
- exact and approximate enforcement;
- required-predicate validation;
- projection pruning and optional remote projection;
- limit pushdown with correct guarantee reporting;
- no-pagination, page, offset, cursor, and next-link strategies;
- bounded batching for multi-value filters;
- streaming `ConnectorPageSource`;
- cancellation propagation;
- timeout, retry for safe operations, response-size limits, page/request/row budgets, and repeated-token detection;
- service-account authentication profiles;
- registry-based immutable contract loading;
- capability preview data model and SQL-to-request planning API reusable by the control plane;
- connector-specific error codes and safe diagnostics;
- JMX/OpenMetrics-compatible metrics;
- unit, contract, QueryRunner, security, and compatibility tests;
- user and administrator documentation.

### Excluded from MVP

- `INSERT`, `UPDATE`, `DELETE`, or other remote mutations;
- generic join pushdown;
- aggregation pushdown;
- arbitrary user-supplied URLs;
- automatic exposure of all OpenAPI operations;
- runtime mutable schemas during an active query;
- end-user delegated OAuth;
- XML, SOAP, GraphQL, WebSocket, SSE, and file transfer endpoints;
- general response caching;
- distributed global rate limiting implemented inside the connector;
- generic parallel time-window or key-range split planning beyond explicitly modeled partitions.

## Functional requirements

### FR-01: catalog initialization

The catalog loads one published contract identity and version from an approved registry endpoint and validates the fingerprint before exposing metadata.

### FR-02: metadata

The connector exposes stable schemas, tables, columns, comments, hidden-input columns, and column types from the compiled contract.

### FR-03: filter pushdown

`applyFilter` stores only semantically supported predicates in the table handle and returns all remaining predicates to Trino.

### FR-04: projection pushdown

`applyProjection` stores projected columns and nested dereferences without producing optimizer loops.

### FR-05: limit pushdown

`applyLimit` applies the best remote limit and reports whether it is guaranteed according to the final handle state.

### FR-06: pagination

The page source advances the configured pagination state machine and terminates on explicit completion, query limit, page budget, or detected loop.

### FR-07: request planning

The same request-planning component supports runtime requests and sanitized SQL-to-request previews.

### FR-08: streaming execution

Workers parse response streams incrementally and produce Trino pages without buffering the complete response or result set.

### FR-09: safe remote access

Every request is checked against approved scheme, host, redirect, next-link, header, and credential policies.

### FR-10: query guardrails

The connector enforces hard limits for requests, pages, rows, bytes, splits, queue depth, and concurrency. Exceeding a hard limit fails the query rather than truncating silently.

### FR-11: cancellation

Cancelling or completing a query closes active response streams, stops pagination, and releases HTTP resources promptly.

### FR-12: observability

The connector records low-cardinality metrics for requests, retries, latency, bytes, rows, pages, throttling, decoding failures, budget failures, and active requests.

## Non-functional requirements

### Correctness

- Pushdown claims must match Trino semantics.
- Handles are immutable and equality-stable across optimizer fixpoint calls.
- Existing queries retain their contract fingerprint for the full query lifetime.
- Unsupported predicates are never discarded.
- Query limits never cause silent partial results.

### Performance

- Time to first page must not require full-response materialization.
- Only requested columns are decoded.
- Connection pooling is reused per worker and trust boundary.
- Multi-value lookups are batched where supported.
- Cursor chains remain sequential unless an independent partition dimension exists.

### Security

- No plaintext secret is stored in a contract, table handle, split, plan, exception, log, or metric label.
- Authentication and registry credentials are separate.
- SSRF protections cover base URIs, redirects, next links, token endpoints, and registry locations.
- Credentials are not forwarded across origins.

### Availability

- Registry availability is required for catalog load or refresh, not for queries using an already loaded immutable contract.
- Registry failure does not mutate the currently active model.
- A failed refresh leaves the previous model active.

### Compatibility

The connector is built and tested against the exact Trino SPI version of the target branch. Cross-version binary compatibility is not assumed.

## Acceptance scenarios

### Equality and residual filtering

Given an exact `tenant_id = tenantId` binding and no binding for `lower(name)`, a query containing both predicates pushes `tenant_id` and retains `lower(name)` as a residual predicate.

### Multi-value filter

Given an `IN` binding to an array request field, `status IN ('active', 'pending')` produces one bounded array request when both values fit the configured batch size.

### Remote limit

Given a remote maximum of 500, `LIMIT 100` sends a remote size of 100. If a residual predicate remains, the connector does not report the limit as guaranteed.

### Cursor pagination

Given a next-cursor pointer, the next request uses the returned cursor. Repeated cursor values fail the query with a connector-specific pagination-loop error.

### Projection

Selecting two columns sends a remote field selection when supported, otherwise the page source decodes only those two columns.

### Required predicate

A table that requires `tenant_id = value` fails planning with an actionable error when the predicate is absent.

### Network safety

A next link targeting an unapproved host fails before credentials are sent.

### Query budget

A query exceeding its request budget fails with query ID, table, operation identity, used count, and configured limit, without returning truncated results.

## Delivery phases

### Phase 1: connector foundation

Establish module structure, immutable contract model, registry client, metadata, error codes, basic request planner, and deterministic metadata tests.

### Phase 2: core read path

Add filter, projection, limit, no-pagination/page/offset/cursor strategies, streaming page source, JSON decoding, cancellation, and QueryRunner coverage.

### Phase 3: production safety

Add auth profiles, SSRF controls, retry policy, request budgets, rate/concurrency controls, response limits, metrics, structured diagnostics, and security tests.

### Phase 4: advanced read optimization

Add next-link pagination, bounded batching, dynamic filtering, Top-N, richer nested types, statistics, and capability-preview integration.

### Phase 5: self-service control-plane integration

Complete registry publishing workflow, response sampling, type suggestions, capability review, SQL-to-request preview, approval, audit, and catalog provisioning outside this repository.

## Product success metrics

- median time from API definition to first successful SQL query;
- percentage of tables published without manual capability override;
- percentage of remote rows eliminated by pushed filters;
- percentage of queries using remote limit or projection;
- remote requests per output row;
- query success, timeout, throttle, and decode-failure rates;
- number of queries rejected before unsafe unbounded scans;
- number of pagination-loop and host-policy violations detected;
- peak worker memory and time to first page compared with a buffered reader.

## Key product decisions

1. The connector is read-only for the first release.
2. OpenAPI is an optional discovery aid, not the runtime relational contract.
3. Users describe API inputs and outputs; the connector infers Trino capabilities.
4. A published immutable contract is loaded from a registry, not from user-managed cluster files.
5. Exact semantics are required before a predicate, limit, or Top-N is declared fully enforced.
6. The worker data path is streaming and page-oriented.
7. Hard query guardrails are mandatory, not optional tuning.
8. Global upstream quotas should be enforced by an API gateway or external quota service when per-node limits are insufficient.
