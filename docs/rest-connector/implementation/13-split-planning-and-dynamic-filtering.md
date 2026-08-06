# Work Item 13 — Split Planning, Batching, and Dynamic Filtering

## Goal

Translate a final table handle and optional dynamic filter into a bounded set of independent remote partitions without request explosion.

## Dependencies

Work Items 07–12.

## Files

```text
io.trino.plugin.rest.RestSplitManager
io.trino.plugin.rest.split.RestSplitPlanner
io.trino.plugin.rest.split.SplitPlanningContext
io.trino.plugin.rest.split.SplitPlan
io.trino.plugin.rest.split.RestPartition
io.trino.plugin.rest.split.SinglePartition
io.trino.plugin.rest.split.KeyBatchPartition
io.trino.plugin.rest.split.TimeWindowPartition
io.trino.plugin.rest.split.OffsetRangePartition
io.trino.plugin.rest.split.PageRangePartition
io.trino.plugin.rest.split.DomainBatcher
io.trino.plugin.rest.split.DynamicFilterCollector
io.trino.plugin.rest.split.SplitPlanningException
src/test/java/io/trino/plugin/rest/split/TestRestSplitPlanner.java
src/test/java/io/trino/plugin/rest/TestRestSplitManager.java
```

## SPI integration

Follow the exact target branch `ConnectorSplitManager#getSplits` signature, including transaction/session/table handle/dynamic filter/constraint parameters where present.

Return a `ConnectorSplitSource` implementation appropriate for fixed bounded splits. Use `FixedSplitSource` unless rate-aware lazy split production is demonstrably necessary. HTTP request rate is controlled at execution, not by split creation alone.

## Core principle

A split represents an **independent partition dimension**. Pagination is usually sequential state inside the split.

Valid examples:

- one tenant;
- one bounded key batch;
- one half-open time window;
- one stable page/offset range with known total;
- one explicit API partition/region.

Invalid default examples:

- one cursor page per split;
- one split per output row;
- Cartesian product of every value in every constrained column;
- unbounded split generation from a mutable next link.

## Planner input

```java
public SplitPlan plan(
        RestTableModel table,
        RestTableHandle handle,
        TupleDomain<RestColumnHandle> dynamicFilter,
        EffectiveQueryLimits limits,
        SplitPlanningContext context);
```

Context includes node/host scheduling metadata and dynamic-filter timeout settings, not credentials.

## Planning sequence

1. Verify contract fingerprint and table identity.
2. If handle constraint is none, return zero splits.
3. Await eligible dynamic filter up to configured timeout.
4. Intersect dynamic domain with existing exact constraints.
5. Re-run binding/batching logic for affected columns.
6. Validate required predicates after eligible dynamic filtering.
7. Select one primary partition strategy from compiled split capability.
8. Estimate split/request amplification before materializing splits.
9. Enforce maximum splits/query and values/batch.
10. Create deterministic ordered `RestSplit` instances.

## Dynamic filtering

Use `DynamicFilter` according to current SPI. Requirements:

- wait only when the table declares eligible columns and waiting can avoid an unsafe/broad scan;
- timeout is bounded by catalog/session setting;
- preserve interruption/cancellation;
- if dynamic filter becomes none, return no splits;
- if filter is not complete at timeout, use current predicate only when scan remains safe;
- do not wait for dynamic filtering on point queries already bounded;
- record wait time and whether filter reduced splits/requests.

Dynamic-filter domains are translated using the same exact/approximate semantics as static filters. Do not treat engine-produced domain as automatically supported remotely.

## Required predicate interaction

Classify requirements:

```text
STATIC_REQUIRED
DYNAMIC_ELIGIBLE
STATIC_OR_DYNAMIC
```

Security/tenant path predicates should default to `STATIC_REQUIRED`; do not allow a join to supply a tenant credential boundary accidentally.

After dynamic filter wait, fail safely if a required bounded key predicate remains unsatisfied.

## Batching algorithm

For a discrete exact domain:

```java
ImmutableList<ImmutableList<NullableValue>> batches(
        SortedSet<NullableValue> values,
        int maximumBatchSize,
        int maximumBatches);
```

Rules:

- deterministic value ordering using type-aware comparator or canonical serialized bytes;
- no null unless exact null collection semantics exist;
- each batch size <= effective maximum;
- number of batches checked before allocating complete list;
- preserve value type and exactness;
- do not expose values in split `toString()`; record count/hash only.

## Avoid Cartesian explosion

For predicates:

```sql
account_id IN (...A...)
AND status IN (...B...)
AND region IN (...C...)
```

Planner evaluates strategies:

1. encode all supported lists in each request without splitting when API accepts them;
2. batch one primary dimension and include other list bindings in every batch when their full list fits;
3. choose one partition dimension and keep unsupported dimensions residual;
4. reject if required dimensions cannot be represented safely;
5. only form Cartesian products when the compiled contract explicitly declares independent scalar request routing, estimated product is below a hard cap, and duplicate semantics are understood.

MVP default forbids Cartesian products.

## Strategy selection

### Single partition

Use when pagination is sequential and no independent dimension exists. One split carries handle/split bindings.

### Key batch

Use when exact discrete values can be sent as list/repeated values or independent point calls. Split count = ceil(values / batch size).

### Time window

Only with explicit capability:

- lower/upper request fields;
- half-open `[start,end)` semantics;
- timezone/precision compatible;
- window boundaries independent;
- maximum/minimum window size;
- late-arrival/snapshot behavior documented.

Do not auto-window arbitrary timestamp filters.

### Offset/page range

Only when:

- total count/pages known before planning or through an approved cheap count request;
- ordering stable and exact;
- ranges independent;
- query snapshot semantics acceptable;
- request budget accounts for count request.

Avoid a planning-time remote count request in MVP unless explicitly designed; it delays planning and introduces failure/rate behavior.

### Explicit partitions

Contract may provide fixed safe partitions (region/shard). Values must be non-secret and immutable. Planner creates one split per partition within cap.

## Split sizing

Target enough splits for parallelism without overwhelming API.

Effective split count considers:

- API per-host concurrency;
- expected requests per split from pagination;
- Trino worker count only as a hint;
- total request/query budget;
- remote quota;
- estimated rows/bytes;
- maximum split cap.

Do not create 10,000 splits merely because cap allows it. Use conservative default target concurrency.

## Scheduling

REST endpoints are network services, not data-local storage. `getAddresses()` normally returns empty unless an explicit proxy/locality model exists. `isRemotelyAccessible()` follows current `ConnectorSplit` contract.

Do not pin splits to coordinator.

## Split source cancellation

If lazy split source is used:

- close stops generation;
- dynamic-filter wait is cancellable;
- no background task survives query completion;
- exceptions propagate once;
- memory use is bounded.

Prefer fixed list after bounded planning for MVP.

## Tests

- none constraint -> zero splits;
- single split;
- key batching exact sizes/order;
- batch size/request/split caps;
- dynamic filter reduces batches;
- dynamic filter none;
- timeout behavior safe/unsafe;
- static required predicate not satisfied by dynamic filter;
- dynamic-eligible predicate satisfied;
- two/three IN columns do not form Cartesian product;
- explicit rejection when required scalar combinations exceed cap;
- time windows half-open and no gaps/overlap;
- offset/page ranges only under stable conditions;
- deterministic split ordering/serialization;
- split `toString()` redacts values;
- cancellation during dynamic wait;
- estimates/accounting recorded.

## Acceptance criteria

- Split count is known/bounded before execution.
- No default Cartesian expansion exists.
- Cursor pagination remains in one split chain.
- Dynamic filtering uses the same correctness rules as static filters.
- Required security predicates cannot be deferred incorrectly.
- Split planning is deterministic, cancellable and value-redacted.
- HTTP request rate is not incorrectly modeled as split rate.

## Validation commands

```bash
./mvnw -pl plugin/trino-rest -Dtest='TestRestSplitPlanner,TestRestSplitManager' test
./mvnw -pl plugin/trino-rest airstyle:check
```