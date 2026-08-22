# Elasticsearch Connector Pushdown Roadmap

This roadmap tracks the staged implementation of the Elasticsearch connector pushdown architecture. Work must progress in order. A stage is not considered complete until its implementation-specific tests and connector-level checks pass.

## Delivery rules

1. Work on a feature branch; never implement directly on `master`.
2. Keep each stage reviewable and independently testable.
3. Do not start the next stage until the current stage is green.
4. Every behavior change must include regression tests covering exact SQL semantics and generated Elasticsearch behavior.
5. Prefer exact pushdown. Candidate-only or approximate pushdown must retain the Trino residual predicate.
6. Avoid Elasticsearch scripts for generic predicate pushdown unless there is no index-native equivalent and the performance/correctness trade-off is demonstrated.
7. Keep upstream contribution in mind: isolate performance changes from architectural refactors where possible.

## Test gate used for every implementation stage

Run the narrowest unit tests first, then the connector module checks before marking a stage complete:

```bash
./mvnw -pl :trino-elasticsearch -Dtest=<affected-test-class> test
./mvnw -pl :trino-elasticsearch airstyle:check
./mvnw -pl :trino-elasticsearch test
```

For a PR intended for upstream, GitHub CI must also be green before the stage is marked complete.

---

## P0.1 — Remote Predicate IR

**Status:** IN PROGRESS

### Objective

Replace the current collection of loosely related remote-predicate state (`TupleDomain`, regex maps, prefix maps, match-phrase-prefix maps and synthetic domains) with a composable remote predicate representation that can express multiple predicates on the same Elasticsearch field without losing semantics.

### Target model

```text
RemotePredicate
├── And
├── Or
├── Not
├── Term
├── Terms
├── Range
├── Prefix
├── Regexp
├── MatchPhrase
├── MatchPhrasePrefix
└── Exists
```

Every translation must also retain enforcement information:

```text
EXACT        remote predicate is authoritative
PREFILTER    remote predicate only reduces candidates; Trino residual is required
APPROXIMATE  remote predicate intentionally uses approximate full-text semantics
```

### Implementation sequence

- [ ] P0.1a Add immutable/sealed Remote Predicate IR model.
- [ ] P0.1b Add Elasticsearch DSL renderer for the IR without changing current query behavior.
- [ ] P0.1c Add unit tests for every primitive node and boolean composition.
- [ ] P0.1d Add an optional remote predicate field to `ElasticsearchTableHandle`.
- [ ] P0.1e Teach `ElasticsearchQueryBuilder` to compose legacy constraints and the new IR.
- [ ] P0.1f Add round-trip/table-handle tests and regression tests.
- [ ] P0.1g Run full connector test gate.

### Acceptance criteria

- Multiple remote predicates can target the same field.
- `AND`, `OR`, and `NOT` are representable without map-key collisions.
- Existing connector behavior remains unchanged until individual translators migrate.
- No synthetic `TupleDomain` workaround is required for new predicates.

---

## P0.2 — Native Elasticsearch `terms`

**Status:** NOT STARTED

### Objective

Translate discrete multi-value predicates to native Elasticsearch `terms` queries instead of generating a large `bool.should` list of `term` queries.

### Scope

- [ ] Single discrete value -> `Term`.
- [ ] Multiple discrete values -> `Terms`.
- [ ] Numeric values.
- [ ] Boolean values.
- [ ] Timestamp/date values supported by the connector.
- [ ] `keyword` and safe `.keyword` paths for VARCHAR.
- [ ] Preserve residuals when exact semantics are not guaranteed.

### Required tests

- [ ] `IN` with 1, 10, 1,000 and more than 1,024 values.
- [ ] Generated DSL contains one `terms` query instead of >1,024 bool clauses.
- [ ] Results equal non-pushdown Trino execution.
- [ ] Case-preserving remote field names remain correct.

### Acceptance criteria

Large `IN (...)` predicates no longer depend on Elasticsearch's bool-clause limit for the common discrete-domain case.

---

## P0.3 — Primitive Array Exact Pushdown

**Status:** NOT STARTED

### Objective

Push only primitive-array predicates whose Trino semantics map cleanly to Elasticsearch multi-valued fields.

### Supported first

#### `contains(array, constant)`

```sql
contains(tags, 'telegram')
```

-> `Term(tags.keyword, 'telegram')` when an exact keyword representation exists.

#### `arrays_overlap(array, constant_array)`

```sql
arrays_overlap(tags, ARRAY['telegram', 'facebook'])
```

-> `Terms(tags.keyword, ['telegram', 'facebook'])`.

### Primitive element types

- [ ] TINYINT / SMALLINT / INTEGER / BIGINT
- [ ] REAL / DOUBLE
- [ ] BOOLEAN
- [ ] supported temporal types
- [ ] IP where supported by the connector
- [ ] VARCHAR backed by exact `keyword`
- [ ] VARCHAR backed by safe `text.keyword`

### Explicitly not exact-pushed

- [ ] `array_col = ARRAY[...]`
- [ ] `array_col[index] = value`
- [ ] `element_at(array_col, n)`
- [ ] `array_position(...)`
- [ ] `contains_sequence(...)`
- [ ] `cardinality(array_col)`
- [ ] analyzed-text-only array membership
- [ ] whole-array `IS NULL` / `IS NOT NULL` unless semantics are proven equivalent for empty arrays

### Required tests

- [ ] ES7 and ES8 connector tests.
- [ ] Empty array vs NULL behavior.
- [ ] Arrays containing NULL elements.
- [ ] Duplicate values.
- [ ] Numeric arrays.
- [ ] Exact keyword arrays.
- [ ] analyzed text fallback/residual.
- [ ] existing array subscript and whole-array equality tests remain non-pushdown.

---

## P0.4 — Dynamic Filter Planner

**Status:** NOT STARTED

### Objective

Use the new `Term`/`Terms` IR to make dynamic filtering scale beyond the current fixed domain-compaction behavior.

### Planner

```text
DynamicFilter
  ├── single value -> Term
  ├── small set -> Terms
  ├── medium set -> batched Terms
  ├── range -> Range
  └── excessive/unsafe -> bounded fallback
```

### Configuration candidates

```properties
elasticsearch.dynamic-filtering.max-values
elasticsearch.dynamic-filtering.terms-batch-size
elasticsearch.dynamic-filtering.max-query-bytes
```

### Required tests

- [ ] Small dynamic-filter value set.
- [ ] >1,000 values without losing selectivity merely because of the old hard-coded compaction threshold.
- [ ] Batched query generation.
- [ ] Request-byte budget.
- [ ] Correct fallback when budget is exceeded.
- [ ] Join integration tests where dynamic filtering reduces Elasticsearch input.

---

## P0.5 — Complete Rule-based Predicate Migration

**Status:** NOT STARTED

### Objective

Move legacy predicate-specific logic out of `ElasticsearchMetadata.applyFilter()` and into composable expression/domain translation rules targeting the Remote Predicate IR.

### Rules

- [ ] Exact discrete domain
- [ ] Range domain
- [ ] Exact LIKE/prefix
- [ ] analyzed-text LIKE
- [ ] `starts_with`
- [ ] `substr` / `substring` prefix recognition
- [ ] `regexp_like`
- [ ] array `contains`
- [ ] `arrays_overlap`

### Cleanup after migration

- [ ] Remove synthetic full-text domains.
- [ ] Remove or deprecate legacy regex/prefix/match-phrase-prefix maps when no longer referenced.
- [ ] Preserve full-text modes DISABLED / SAFE / UNSAFE.

---

## P1.1 — `any_match` Primitive Array Pushdown

**Status:** NOT STARTED

Implement only lambda forms that have provable existential semantics on an Elasticsearch multi-valued field:

- [ ] `any_match(a, x -> x = constant)` -> `Term`
- [ ] `any_match(a, x -> x IN (...))` -> `Terms`
- [ ] `any_match(a, x -> x >/< />=/<= constant)` -> `Range`
- [ ] combinations whose boolean semantics remain exact

Do not initially support arbitrary lambdas, `all_match`, `none_match`, or script-backed execution.

---

## P1.2 — Multi-predicate Boolean Composition

**Status:** NOT STARTED

### Objective

Fully exploit the IR to allow multiple independent predicates on the same field.

Examples:

```sql
name LIKE '%ngô%' AND name LIKE '%văn%'
```

-> two full-text predicates under remote `AND` when allowed by the configured full-text mode.

```sql
contains(tags, 'a') AND contains(tags, 'b')
```

-> two `Term` predicates under `AND`.

Implement in order:

- [ ] AND
- [ ] OR
- [ ] NOT only after SQL/Elasticsearch NULL semantics are proven safe

---

## P1.3 — Pushdown Observability

**Status:** NOT STARTED

Expose enough information to debug production pushdown decisions:

- [ ] SQL/connector expression -> Remote Predicate IR diagnostic output.
- [ ] Remote Predicate IR -> generated Elasticsearch DSL diagnostic output.
- [ ] EXACT / PREFILTER / APPROXIMATE / residual counts.
- [ ] `terms` query/value counts.
- [ ] array-membership pushdown counts.
- [ ] dynamic-filter values received/pushed/compacted/batched.
- [ ] remote requests, rows, bytes, pages and retries where available.

---

## P1.4 — Scan Execution v2

**Status:** NOT STARTED

### Objective

Reduce heap usage and improve large-scan throughput after predicate planning is stabilized.

- [ ] Benchmark current Scroll execution.
- [ ] Benchmark PIT + `search_after` where supported.
- [ ] Evaluate Jackson streaming parsing instead of String -> JsonNode -> Map materialization.
- [ ] Improve cancellation/clear-scroll resource handling.
- [ ] Add completed-byte and page/request accounting.
- [ ] Keep ES version compatibility explicit.

Do not replace Scroll without benchmark evidence.

---

## P2 — TopN, LIMIT, Aggregation and Statistics Hardening

**Status:** NOT STARTED

- [ ] Review shard-aware LIMIT/TopN over-fetch strategy.
- [ ] Improve early cancellation where safe.
- [ ] Make composite aggregation page size configurable if benchmarks justify it.
- [ ] Add aggregation resource/byte metrics.
- [ ] Add statistics caching/selectivity improvements only after measuring planning overhead.

---

## P3 — Low-priority SPI Extensions

**Status:** NOT STARTED

### `applySample`

Research only after P0/P1. Elasticsearch sampling semantics must be compared carefully with Trino TABLESAMPLE semantics.

### `applyJoin`

Do not prioritize generic join pushdown. Preferred architecture is:

```text
Trino join
  -> build-side dynamic filter
  -> DynamicFilterPlanner
  -> Term/Terms/Range
  -> Elasticsearch
```

---

## Current execution order

```text
P0.1 Remote Predicate IR
  -> P0.2 Native terms
  -> P0.3 Primitive array exact pushdown
  -> P0.4 Dynamic Filter Planner
  -> P0.5 Rule migration
  -> P1.1 any_match
  -> P1.2 boolean composition
  -> P1.3 observability
  -> P1.4 scan execution v2
  -> P2 hardening
  -> P3 optional SPI work
```

Update this file after every completed stage, including the tests/CI used to validate completion.
