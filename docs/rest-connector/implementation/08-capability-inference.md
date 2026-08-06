# Work Item 08 — Capability Inference Engine

## Goal

Infer connector capabilities from explicit API structure and semantic bindings, while producing an evidence trail and never strengthening semantics through naming heuristics.

## Dependencies

Work Items 03, 05, 06.

## Files

```text
io.trino.plugin.rest.capability.CapabilityInferenceEngine
io.trino.plugin.rest.capability.InferredCapabilities
io.trino.plugin.rest.capability.FilterCapability
io.trino.plugin.rest.capability.LimitCapability
io.trino.plugin.rest.capability.ProjectionCapability
io.trino.plugin.rest.capability.TopNCapability
io.trino.plugin.rest.capability.PaginationCapability
io.trino.plugin.rest.capability.SplitCapability
io.trino.plugin.rest.capability.CapabilityEvidence
io.trino.plugin.rest.capability.CapabilityWarning
io.trino.plugin.rest.capability.CapabilityOverrideApplier
src/test/java/io/trino/plugin/rest/capability/TestCapabilityInferenceEngine.java
```

## Core rule

The engine infers capabilities from explicit roles and bindings, not from remote field names.

Forbidden heuristics:

```text
field named limit -> limit pushdown
field named page -> page pagination
field named q -> contains/full-text
field named sort -> Top-N
path ending /{id} -> primary key
array field -> IN with OR semantics
```

Names may be used by a UI to suggest a draft, but suggestions cannot become compiled runtime semantics without user confirmation.

## Output model

```java
public record InferredCapabilities(
        ImmutableMap<Integer, ImmutableMap<FilterOperator, FilterCapability>> filters,
        Optional<LimitCapability> limit,
        ProjectionCapability projection,
        Optional<TopNCapability> topN,
        PaginationCapability pagination,
        SplitCapability split,
        ImmutableList<CapabilityWarning> warnings) {}
```

Every positive capability contains one or more `CapabilityEvidence` items:

```java
public record CapabilityEvidence(
        String code,
        String definitionPointer,
        String statement) {}
```

Example evidence:

```text
CAP-EQ-001: column tenant_id has explicit EQ binding to scalar request field tenantId
CAP-IN-001: column status has explicit IN binding to array request field status with OR semantics
CAP-CURSOR-001: request role CURSOR and response nextCursor pointer are both present
```

## Filter inference

### Equality

Infer exact equality only when:

- explicit `EQ` binding exists;
- request field accepts one scalar value;
- conversion is lossless for the SQL type/domain;
- remote semantics are declared exact;
- null/missing behavior does not alter equality semantics.

Infer approximate equality/prefilter only when explicit enforcement is `APPROXIMATE`. It must never be placed in the enforced tuple domain.

### `IN`

Infer when:

- explicit `IN` binding exists;
- request representation supports collection/repeated values;
- collection semantics are OR/membership equivalent;
- maximum values or batching policy is known;
- element encoding is lossless.

Do not infer for array fields whose semantics are AND, ordered sequence, range, or undocumented.

### Range

Infer each bound independently from explicit binding:

```text
GT
GTE
LT
LTE
```

Bound capability records inclusivity. Combining lower/upper bounds is allowed only when the remote request can carry both simultaneously.

### Null predicates

Infer `IS_NULL`/`IS_NOT_NULL` only with explicit remote null-filter semantics. Parameter omission is not null filtering.

### Prefix/contains/regex/full-text

MVP may model these as approximate prefilters only unless exact equivalence is explicitly proven. Preserve residual predicate by default.

### Boolean combinations

`TupleDomain` conjunctions can be pushed per-column. General OR expressions outside a single discrete domain remain residual unless the API contract explicitly supports a boolean expression language and an expression translator is implemented outside basic MVP.

## Required predicate inference

Infer a required SQL predicate when:

- request field is required;
- source is `SQL_PREDICATE`;
- at least one exact binding can populate it;
- binding shape satisfies field cardinality (for path fields usually one exact value).

Store requirement as an expression over operators, for example:

```text
tenant_id EQ exactly one value
account_id IN between 1 and 100 values
created_at must have both GTE and LT
```

Do not turn required constant/session/auth fields into required SQL predicates.

## Limit inference

Infer remote limit when a system binding role `LIMIT` or `PAGE_SIZE` exists and request field is a positive integral type.

Capability fields:

```java
public record LimitCapability(
        long minimum,
        long maximum,
        boolean remoteBoundIsStrict,
        GuaranteePolicy guaranteePolicy,
        ImmutableList<CapabilityEvidence> evidence) {}
```

`remoteBoundIsStrict` means API promises no more than requested. It does not alone make Trino `applyLimit` guaranteed; final guarantee also depends on residual filtering, row expansion, deduplication, and ordering state in the table handle.

## Projection inference

Always provide:

```text
localDecodePruning = true
```

when the streaming decoder supports selected columns.

Remote projection requires:

- explicit projection system binding;
- every projected output column has a remote field name/encoding;
- API declares omitted fields do not alter row selection/cardinality;
- required response fields needed for pagination/error handling remain requested automatically.

Capability must include a deterministic function from selected columns to remote field list and maximum field-count/URL/body constraints.

## Pagination inference

Exactly one strategy:

- none: no continuation metadata;
- page: PAGE plus optional PAGE_SIZE/LIMIT, termination metadata;
- offset: OFFSET plus LIMIT/PAGE_SIZE;
- cursor: CURSOR plus next cursor pointer;
- next-link: next-link source.

Ambiguous combinations are compiler errors, not warnings.

Capability includes:

- sequential versus partition-parallel potential;
- stable ordering/consistency declaration;
- termination evidence;
- maximum page size;
- repeated continuation detection requirement.

## Top-N inference

Remote ordering capability requires:

- explicit SORT system binding;
- map from SQL columns to remote sort fields;
- allowed directions;
- syntax/serialization;
- API ordering semantic declaration.

Guaranteed Top-N additionally requires:

- stable ordering;
- exact comparator/collation for involved types;
- compatible null ordering or restrictions that avoid nulls;
- unique tie-breaker when pagination/order can otherwise be unstable;
- strict remote limit;
- no residual filter that can invalidate final top N.

If only remote order is supported, mark as preordering/non-guaranteed and leave TopN in Trino.

## Split capability inference

Infer only independent partition dimensions explicitly modeled:

- key batch from bounded `IN` values;
- time windows with independent half-open range semantics;
- fixed tenant/region partitions;
- offset/page ranges only with known total and stable ordering.

Cursor chain is not a split dimension.

Capability records:

- partition type;
- maximum partitions;
- target batch/window size;
- whether partitions overlap;
- deduplication requirement;
- remote quota impact.

## Overrides

Overrides are applied after inference.

Allowed safe reductions:

- disable capability/operator;
- change exact to approximate;
- reduce maximum values/page size;
- mark guarantee false;
- require stricter predicate;
- force sequential execution.

Strengthening requires evidence and revalidation:

- approximate to exact;
- non-guaranteed to guaranteed;
- best-effort to stable consistency;
- unstable sort to stable;
- larger batch/page size.

An override cannot bypass type, auth, host, path, or guardrail validation.

## Warnings

Stable warning codes:

```text
CAP-W001 approximate-filter-remains-residual
CAP-W002 remote-limit-not-guaranteed
CAP-W003 cursor-consistency-unspecified
CAP-W004 ordering-not-stable
CAP-W005 offset-pagination-may-duplicate
CAP-W006 no-remote-projection
CAP-W007 unbounded-scan-requires-filter
CAP-W008 split-parallelism-disabled
```

Warnings are safe for UI and `getInfo`; they do not include values or credentials.

## Tests

Use table-driven tests for every inference rule.

Required cases:

- exact and approximate equality;
- IN with OR/AND/unknown collection semantics;
- independent range bounds and bounded range;
- null behavior;
- required SQL/path predicate versus constant/session field;
- limit maximum/strictness;
- local versus remote projection;
- all pagination strategies and ambiguous combinations;
- stable/non-stable Top-N;
- split dimensions and cursor exclusion;
- safe reductions and rejected strengthening overrides;
- deterministic evidence/warning ordering;
- no inference based solely on conventional names.

## Acceptance criteria

- Every capability has explicit evidence.
- Generic naming does not affect runtime inference.
- Approximate filters remain residual.
- Limit and Top-N guarantees are conditional and conservative.
- Pagination strategy is unambiguous.
- Cursor continuation never becomes automatic page splits.
- Override behavior cannot weaken correctness/security.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest=TestCapabilityInferenceEngine test
./mvnw -pl plugin/trino-rest airstyle:check
```