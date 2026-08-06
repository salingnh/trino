# Work Item 09 — Filter Pushdown and Required Predicates

## Goal

Implement `ConnectorMetadata.applyFilter` and `validateScan` with exact remote enforcement, approximate prefiltering, residual predicate preservation, bounded multi-value behavior, and required-filter validation.

## Dependencies

Work Items 07–08.

## Files

```text
io.trino.plugin.rest.pushdown.RestPredicateTranslator
io.trino.plugin.rest.pushdown.PredicateTranslationResult
io.trino.plugin.rest.pushdown.DomainBindingTranslator
io.trino.plugin.rest.pushdown.BoundValueSet
io.trino.plugin.rest.pushdown.RequiredPredicateValidator
io.trino.plugin.rest.pushdown.FilterPushdownPlanner
src/test/java/io/trino/plugin/rest/pushdown/TestRestPredicateTranslator.java
src/test/java/io/trino/plugin/rest/pushdown/TestRestFilterPushdown.java
src/test/java/io/trino/plugin/rest/pushdown/TestRequiredPredicateValidator.java
```

## SPI method

Follow the exact target-branch signature:

```java
@Override
public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(
        ConnectorSession session,
        ConnectorTableHandle handle,
        Constraint constraint)
```

The method may be invoked repeatedly. Return `Optional.empty()` when the invocation creates no semantic change.

## Input model

Use both:

- `constraint.getSummary()` for `TupleDomain` pushdown;
- `constraint.getExpression()` only for supported connector expressions when explicitly implemented.

MVP primarily handles `TupleDomain`. Do not discard an expression that cannot be translated.

The SPI guarantees summary is not NONE; still preserve current repository precondition patterns.

## Translation result

```java
public record PredicateTranslationResult(
        TupleDomain<RestColumnHandle> enforcedConstraint,
        TupleDomain<ColumnHandle> remainingConstraint,
        ImmutableMap<RequestBindingKey, BoundValueSet> exactBindings,
        ImmutableMap<RequestBindingKey, BoundValueSet> approximateBindings,
        ConnectorExpression remainingExpression,
        ImmutableList<PushdownDiagnostic> diagnostics) {}
```

Rules:

- enforced constraint includes only exact predicates fully represented remotely;
- approximate bindings never remove the predicate from remaining constraint/expression;
- unsupported domains remain entirely residual;
- exact bindings are canonicalized for stable equality;
- contradictions result in an empty scan handle or SPI-supported no-row behavior, not a remote request.

## Domain translation algorithm

For each constrained column in deterministic ordinal order:

1. Resolve table capability by column/operator.
2. Inspect null allowance and ranges/discrete values.
3. Select a representation the API can express exactly.
4. Build exact or approximate request binding.
5. Remove only the exactly enforced portion from remaining domain.
6. Keep unsupported parts residual.
7. Merge with bindings already present in the handle by intersection.
8. Detect no-op equality.

Do not convert a complex multi-range domain into a broad min/max range unless the broad range is approximate and the original predicate remains residual.

## Equality

Exact equality requires a single-value domain and exact EQ capability.

Path parameter rules:

- usually exactly one non-null value;
- encode during request planning;
- multiple values may use multiple splits only when an explicit point-lookup split strategy exists and split cap is respected;
- no value means required predicate is unsatisfied.

## `IN`

Discrete-set translation choices in priority order:

1. one array/repeated request if API supports list membership and values fit maximum;
2. bounded batches when API supports list membership with maximum batch size;
3. independent scalar splits only when explicit and estimated split count is within cap;
4. do not push; leave residual for a bounded collection scan;
5. reject scan when required predicate cannot be satisfied safely.

Never create a Cartesian product across multiple multi-value columns by default.

If one column can be batched and another cannot, prefer pushing the most selective/safe dimension and leave the other residual rather than multiplying combinations.

## Ranges

Translate independently:

- low inclusive -> GTE;
- low exclusive -> GT;
- high inclusive -> LTE;
- high exclusive -> LT.

If API has only inclusive bounds and SQL has exclusive bound, do not alter value by successor/predecessor unless the type exposes exact discrete stepping and the semantics are proven. Safer default: keep unsupported bound residual.

Multiple disjoint ranges are not collapsed into one exact remote range. Options are bounded splits if explicitly supported or residual filtering.

## Nulls

- `IS NULL` only when explicit exact null-filter capability exists;
- `IS NOT NULL` only when explicit exact capability exists;
- a domain allowing null plus values may require an OR the API cannot express; push only safe approximate prefilter and keep full residual, or do not push;
- omitting a request parameter is never treated as `IS NULL`.

## Strings

Exact pushdown must account for:

- case sensitivity;
- collation/comparison semantics;
- Unicode normalization;
- trimming;
- wildcard/full-text behavior.

Generic search, contains, prefix, regexp, fuzzy and full-text filters are approximate unless the contract explicitly states exact equivalence. Approximate remote filter reduces data but leaves SQL predicate residual.

## Timestamp/timezone

Range/equality encoding must preserve instant/local semantics. Reject pushdown if remote timezone assumption differs and cannot be represented exactly.

## Existing handle merge

Given existing enforced domain and new summary:

```text
newEnforced = existingEnforced intersect newlyExact
newRequestBindings = canonical merge/intersection
```

If new summary is weaker than existing state, keep existing stronger state and return no change.

If intersection is empty, create a handle representing empty scan. Split manager returns no splits.

## `ConstraintApplicationResult`

Return result with:

- derived handle;
- remaining tuple domain;
- remaining connector expression according to current constructor signature;
- precalculate-statistics flag only when justified; default false.

Verify the exact constructor available in the target branch before coding.

## Required predicates and `validateScan`

Implement:

```java
@Override
public void validateScan(
        ConnectorSession session,
        ConnectorTableHandle handle)
```

Validation occurs on final planned handle and checks compiled requirements:

- exact single value required;
- non-empty bounded set required;
- both lower and upper range required;
- at least one of a set of predicates required;
- maximum values/batch count;
- bounded-scan policy.

Failure uses a connector-specific user error and includes:

- catalog/schema/table;
- operation ID;
- missing requirement;
- example safe SQL predicate using column name;
- no values from the user's query when sensitive.

Do not perform this rejection during initial `getTableHandle`; optimizer needs a chance to apply filters/dynamic filters.

## Dynamic filtering interaction

Static `applyFilter` stores pushable domains. Split manager later intersects dynamic filter with handle state. Required predicate may be satisfiable by dynamic filtering only when contract and query shape allow waiting. Final validation timing must not reject before dynamic filter is considered. Define one of:

- `validateScan` accepts a requirement flagged `DYNAMIC_FILTER_ELIGIBLE`, while split manager enforces it after wait; or
- static required predicates cannot be satisfied only by dynamic filter in MVP.

Choose explicitly and test. Conservative MVP default: required security/tenant path predicate must be static, while optional batch key may come from dynamic filter.

## Diagnostics/getInfo

Record safe pushdown summary in table handle/info:

- exact pushed columns/operators;
- approximate prefilter columns/operators;
- residual-present boolean;
- estimated batch/split count;
- no literal values.

## Tests

Required matrix:

- exact equality;
- unsupported equality;
- approximate search leaves residual;
- single/multiple discrete values;
- array batch and batching limits;
- no Cartesian product across two IN columns;
- inclusive/exclusive ranges;
- disjoint ranges;
- null-only, not-null, values-plus-null;
- timestamp with timezone;
- empty intersection returns no splits;
- repeated applyFilter returns `Optional.empty()`;
- existing stronger handle not weakened;
- expression residual preserved;
- required path equality missing/present/multiple;
- bounded range requirement;
- safe error text;
- handle JSON round trip after pushdown.

Use plan assertions in QueryRunner tests later to prove filters disappear only when exact.

## Acceptance criteria

- Unsupported predicates are never discarded.
- Approximate predicates remain residual.
- Repeated optimizer calls converge.
- Multi-value filters cannot cause uncontrolled Cartesian expansion.
- Final unsafe scans are rejected with actionable user errors.
- Contradictory predicates cause zero remote requests.
- Pushdown state is immutable, serializable and value-redacted.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestPredicateTranslator,TestRestFilterPushdown,TestRequiredPredicateValidator' test
./mvnw -pl plugin/trino-rest airstyle:check
```