# Work Item 10 — Limit, Projection, and Top-N Pushdown

## Goal

Implement optimizer hooks that reduce remote work without overstating guarantees or causing optimizer fixpoint loops.

## Dependencies

Work Items 07–09.

## Files

```text
io.trino.plugin.rest.pushdown.RestLimitPushdown
io.trino.plugin.rest.pushdown.RestProjectionPushdown
io.trino.plugin.rest.pushdown.RestTopNPushdown
io.trino.plugin.rest.pushdown.RemoteProjection
io.trino.plugin.rest.pushdown.RestSortItem
io.trino.plugin.rest.pushdown.DereferenceExtractor
src/test/java/io/trino/plugin/rest/pushdown/TestRestLimitPushdown.java
src/test/java/io/trino/plugin/rest/pushdown/TestRestProjectionPushdown.java
src/test/java/io/trino/plugin/rest/pushdown/TestRestTopNPushdown.java
```

## `applyLimit`

Use the exact target-branch signature:

```java
@Override
public Optional<LimitApplicationResult<ConnectorTableHandle>> applyLimit(
        ConnectorSession session,
        ConnectorTableHandle handle,
        long limit)
```

### Algorithm

1. Reject negative/invalid input only if SPI does not already guarantee validity.
2. Resolve effective remote maximum from capability, contract, catalog and session.
3. Calculate requested remote row/page size.
4. Compare with current handle limit.
5. If existing limit is less than or equal to new limit and guarantee state does not change, return `Optional.empty()`.
6. Store the lower effective limit.
7. Calculate guarantee from current handle state.
8. Return `LimitApplicationResult(newHandle, guaranteed, false-or-current-flag)` according to target constructor.

### Guarantee rules

`limitGuaranteed=true` only when all are true:

- remote API strictly returns no more than requested limit across the full operation;
- pagination stops when emitted remote rows reach limit;
- no residual predicate remains;
- no approximate filter remains requiring local recheck;
- no local row expansion, unnesting, deduplication or transformation changes cardinality;
- no local Top-N/sort semantics require additional rows;
- request planner can encode the limit for every split/partition without aggregate overrun;
- parallel splits coordinate a global limit or connector reports non-guaranteed.

For multiple independent splits, default to non-guaranteed unless split manager/page source implements a correct global cap. A per-split limit is not a global SQL limit.

When limit is a useful hint but not guaranteed, store it and return guaranteed false.

### Over-fetch

MVP should not implement arbitrary over-fetch guesses. If residual filtering remains, remote limit may be omitted or set to a bounded page size hint while Trino enforces final limit. Document exact behavior and test it.

## `applyProjection`

Use exact target signature:

```java
@Override
public Optional<ProjectionApplicationResult<ConnectorTableHandle>> applyProjection(
        ConnectorSession session,
        ConnectorTableHandle handle,
        List<ConnectorExpression> projections,
        Map<String, ColumnHandle> assignments)
```

### MVP scope

Support:

- identity column projections;
- dereferences of stable `ROW` columns when representable by response pointer/type descriptor;
- local decode pruning for all selected columns;
- remote field list when projection capability exists;
- required extra remote fields for pagination, error extraction, tie-breakers or decoder structure.

Do not attempt arbitrary computed expression pushdown.

### Projection algorithm

1. Translate each projection to an existing or synthetic `RestColumnHandle`.
2. For unsupported expressions, keep original expression and assignments as required by SPI result semantics.
3. Canonicalize derived columns by base column + dereference path.
4. Build replacement projection expressions and assignment list.
5. Store ordered unique physical columns in handle.
6. Build remote projection from all required remote fields.
7. If handle/projection/assignments are semantically unchanged, return empty.

Synthetic dereference handle contains:

- base column ordinal;
- nested field path;
- nested type descriptor;
- full row-relative response pointer;
- stable synthetic name not exposed as user metadata.

### Remote projection safety

Remote projection may be sent only when:

- API declares field-selection semantics;
- selecting fewer fields does not alter row membership/cardinality;
- required row identity/pagination fields are automatically included;
- field names are mapped, not derived from SQL names blindly;
- serialization length/field count stays bounded.

If remote projection cannot be sent, local decode pruning still applies.

## `applyTopN`

Use exact target signature:

```java
@Override
public Optional<TopNApplicationResult<ConnectorTableHandle>> applyTopN(
        ConnectorSession session,
        ConnectorTableHandle handle,
        long topNCount,
        List<SortItem> sortItems,
        Map<String, ColumnHandle> assignments)
```

### Translation checks

For every sort item:

- variable resolves to `RestColumnHandle`;
- remote sort field exists;
- direction supported;
- null ordering supported exactly or query/contract guarantees no nulls;
- comparator/collation semantics match;
- all sort fields can be sent in requested order;
- maximum sort field count respected.

If any item cannot be translated, return empty for Top-N pushdown. Do not partially claim Top-N.

### Guarantee rules

`topNGuaranteed=true` only when:

- remote ordering is exact and stable;
- tie-breaker is unique/stable when required;
- remote strict limit is applied;
- no residual predicate/approximate filter remains;
- pagination preserves ordering across pages/splits;
- multiple splits do not independently return top N without a global merge;
- null ordering and collation match Trino.

Otherwise remote ordering may be useful as preordering, but SPI Top-N pushdown should remain non-guaranteed only if target API/result supports that meaning safely. If uncertain, return empty and leave full TopN in Trino.

### Repeated calls

- lower count replaces higher count;
- identical count/items returns empty;
- different ordering is not merged; evaluate as a new handle only if legal;
- applyLimit interaction uses the smaller row bound but does not automatically make either guarantee true.

## Cross-hook consistency

Handle-derived guarantees can change when later hooks add/remove state. Because optimizer call order may vary:

- derive guarantee from complete handle state each time;
- do not store a stale true flag that survives addition of residual/parallel state;
- canonical handle factory recalculates `limitGuaranteed` and `topNGuaranteed`;
- tests invoke hooks in multiple orders and assert equivalent final handles.

Example orders:

```text
filter -> projection -> limit
limit -> filter -> projection
projection -> topN -> filter
filter -> topN -> limit
```

## Tests

### Limit

- strict single split/no residual -> guaranteed;
- residual -> false;
- approximate prefilter -> false;
- remote max clamps request;
- repeated lower/higher/same calls;
- multiple splits -> false unless global mechanism;
- pagination stops at limit;
- limit zero behavior according to SPI/query planner.

### Projection

- identity selection;
- reorder selection;
- duplicate expressions;
- nested dereference;
- unsupported computed expression retained;
- remote field mapping;
- required pagination field auto-included;
- no remote projection but local pruning;
- repeated call converges;
- handle JSON round trip.

### Top-N

- exact stable ordering;
- unsupported direction;
- null ordering mismatch;
- collation mismatch;
- missing tie-breaker;
- residual predicate;
- multiple splits;
- repeated calls;
- interaction with limit/projection/dereference.

### Plan tests

Later QueryRunner tests must assert:

- Limit node removed only for guaranteed limit;
- Filter node remains for approximate/residual predicates;
- Project/dereference behavior matches returned assignments;
- TopN node removed only for guaranteed Top-N.

## Acceptance criteria

- Every optimizer hook returns empty on no semantic change.
- Limit/Top-N guarantees are conservative and recomputed from full handle state.
- Projection result obeys replacement semantics of SPI.
- Remote projection never omits fields required for pagination/identity/error handling.
- Unsupported expressions remain in Trino.
- Different hook orders converge to equivalent handles.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestLimitPushdown,TestRestProjectionPushdown,TestRestTopNPushdown' test
./mvnw -pl plugin/trino-rest airstyle:check
```