# Elasticsearch Primitive Array UNSAFE Full-Text Pushdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend explicit `full_text_pushdown_mode=UNSAFE` to positive existential primitive analyzed-text arrays while preserving the permanent Remote Predicate IR, enforcement contract, same-element boundary, exact array behavior, and operational bounds.

**Architecture:** `ElasticsearchArrayPredicateTranslator` will return the existing `ElasticsearchPredicateTranslation<ConnectorExpression>` result directly. Array syntax recognition will remain array-owned, while analyzed literal/LIKE/prefix/regexp conversion will be shared with the scalar planner. `ElasticsearchPredicateComposer` will continue to own document-scope boolean composition; lambda-local AND will only use the existing same-element-safe fused range path.

**Tech Stack:** Java 25, Trino connector SPI, Elasticsearch Remote Predicate IR, Maven, JUnit 5, Elasticsearch 7/8 Testcontainers, Docker Compose.

**Spec:** `plugin/trino-elasticsearch/UNSAFE-ARRAY-PUSHDOWN-PLAN.md` and `plugin/trino-elasticsearch/ROADMAP-PUSHDOWN.md`

## Global Constraints

- New remote predicates use `ElasticsearchRemotePredicate` only.
- Array translation returns `ElasticsearchPredicateTranslation`; no temporary adapter or parallel predicate map is allowed.
- `EXACT`, `PREFILTER`, and `APPROXIMATE` remain explicit and cannot be strengthened implicitly.
- Analyzed array translations are authoritative only in `UNSAFE` and are marked `APPROXIMATE` with no residual when valid.
- Generic analyzed lambda AND remains residual unless one IR predicate proves same-element semantics.
- OR pushdown requires every branch to translate; partial OR is planner-owned residual.
- SAFE, DISABLED, exact keyword/numeric/timestamp/IP behavior, and dynamic filtering remain unchanged.
- Tests and runtime commands run inside the repository Docker Compose environment.
- Resource bounds apply to analyzed phrase disjunctions, request bytes, regexp validation, and boolean clauses.

---

### Task 1: A0 baseline freeze

**Files:**
- Read: `plugin/trino-elasticsearch/UNSAFE-ARRAY-PUSHDOWN-PLAN.md`
- Read: `plugin/trino-elasticsearch/ROADMAP-PUSHDOWN.md`
- Evidence: `plugin/trino-elasticsearch/UNSAFE-ARRAY-PUSHDOWN-PLAN.md`

- [ ] Record `origin/master` SHA, implementation branch/worktree, and clean status before production edits.
- [ ] Verify `origin/master` has not advanced beyond the plan’s recorded `44d719ac2977c98532234f3002376551997f00ac`; if it has, re-audit affected files and record the successor SHA.
- [ ] Start or reuse Compose with `docker compose up -d --build`; inspect `docker compose ps` and `docker compose logs --tail=200 maven`.
- [ ] Run the existing baseline selectors inside `maven`:

```bash
docker compose exec -T maven ./mvnw -pl :trino-elasticsearch -Dtest=BaseElasticsearchFullTextPushdownTest test
docker compose exec -T maven ./mvnw -pl :trino-elasticsearch -Dtest=BaseElasticsearchAnyMatchPushdownTest test
docker compose exec -T maven ./mvnw -pl :trino-elasticsearch -Dtest=BaseElasticsearchP0PredicatePushdownTest test
```

- [ ] Capture that `contains(text_tags, 'telegram')` and `any_match(text_tags, x -> x = 'telegram')` remain residual, while scalar UNSAFE equality/LIKE/regexp and exact arrays retain their current behavior.
- [ ] Record exact command results, test counts, Docker limitation evidence, and current HEAD SHA in the source plan.

### Task 2: A1 enforcement-aware array contract

**Files:**
- Modify: `plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java`
- Modify: `plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicatePushdownPlanner.java`
- Test: `plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java`
- Test: `plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchPredicatePushdownPlanner.java`
- Test: `plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchPredicateTranslation.java`

**Interfaces:**
- Consumes: existing `ElasticsearchPredicateTranslation`, `ElasticsearchRemotePredicate`, array translator, and planner-owned residual model.
- Produces: `ElasticsearchArrayPredicateTranslator.translate(ConnectorSession, ConnectorExpression, Map<String, ColumnHandle>, FullTextPushdownMode)` returning `ElasticsearchPredicateTranslation<ConnectorExpression>`.

- [ ] Write unit tests showing existing exact array predicates return `EXACT`, analyzed arrays remain unsupported/residual before feature expansion, and an approximate translation cannot be constructed as `EXACT`.
- [ ] Run the focused unit tests and verify the new tests fail for the missing contract rather than for setup errors.
- [ ] Change the array translator to return the permanent translation result, preserving exact paths and unsupported ownership.
- [ ] Update planner expression translation to consume the array result directly; remove the unconditional `exact(...)` wrapper.
- [ ] Run focused tests and verify exact array IR/enforcement and residual ownership.
- [ ] Run `docker compose exec -T maven ./mvnw -pl :trino-elasticsearch airstyle:check`.
- [ ] Update source-plan A1 evidence with files, commands, results, semantic findings, remaining blockers, and HEAD SHA.

### Task 3: A2 exact-array regression gate

**Files:** existing exact-array and planner/composition test classes.

- [ ] Run exact `contains`, `arrays_overlap`, `any_match` equality/IN/range/OR, fused range AND, keyword/text.keyword, numeric, timestamp, boolean, IP, NULL, empty, missing, duplicate, and whole-array negative tests.
- [ ] Add only missing enforcement assertions; do not alter exact expected results.
- [ ] Run the affected classes inside Compose and record `EXACT` results.
- [ ] Update source-plan A2 evidence before enabling analyzed UNSAFE behavior.

### Task 4: A3 analyzed equality and membership

**Files:** array translator, shared scalar-value helper, composition policy if needed, array/planner unit tests, shared ES7/ES8 acceptance test hierarchy.

- [ ] Write failing tests for analyzed `contains`, `arrays_overlap`, `any_match` equality, and `any_match IN`, including multi-token and custom-analyzer values.
- [ ] Reuse a bounded common discrete analyzed-value policy; fail closed to residual/unsupported above existing terms/boolean/request limits.
- [ ] Translate one analyzed value to `MatchPhrase` and multiple values to bounded `Or(MatchPhrase...)`, marked `APPROXIMATE` only in UNSAFE.
- [ ] Preserve exact keyword/text.keyword and SAFE/DISABLED behavior.
- [ ] Run focused unit/planner and ES7/ES8 acceptance tests; update A3 evidence.

### Task 5: A4/A5 shared LIKE, prefix, and regexp parity

**Files:**
- Modify: scalar expression/planner translation path
- Modify: `ElasticsearchArrayPredicateTranslator.java`
- Add or modify: permanent shared analyzed-value translation helper
- Test: scalar and array translator/planner/acceptance suites

- [ ] Write failing tests for literal LIKE, `%literal%`, prefix LIKE, `starts_with`, valid regexp, unsupported regexp, escapes, Unicode, and analyzer folding.
- [ ] Move or generalize existing scalar LIKE and regexp conversion so scalar and array paths call the same helper and validation.
- [ ] Emit `MatchPhrase`, `MatchPhrasePrefix`, or validated `Regexp` with `APPROXIMATE` under UNSAFE; leave unsupported shapes residual.
- [ ] Verify scalar behavior is unchanged and array behavior never accepts more syntax than scalar behavior.
- [ ] Run focused and ES7/ES8 acceptance tests; update A4/A5 evidence.

### Task 6: A6 OR and A7 same-element safety

**Files:** array translator, composer/planner tests, acceptance tests.

- [ ] Write failing tests for fully translatable analyzed OR, mixed exact/approximate OR, partial OR rejection, nested OR normalization, and lambda-local analyzed AND.
- [ ] Route supported OR through the existing composer; reject partial OR without dropping a branch.
- [ ] Keep numeric/timestamp fused range AND as one same-element `Range`.
- [ ] Add the `Nguyen Anh` / `Le Van` cross-element trap and NULL/empty/missing/duplicate/custom analyzer regressions.
- [ ] Run focused and ES7/ES8 tests; update A6/A7 evidence.

### Task 7: A8 diagnostics/statistics/resource integration

**Files:** diagnostics/statistics tests and only the permanent model consumers required by evidence.

- [ ] Assert array/any_match category, `APPROXIMATE`, remote IR nodes, accepted residual absence, and rejected residual decisions through existing structured diagnostics.
- [ ] Verify approximate remote predicates produce conservative filtered statistics.
- [ ] Verify table-handle serialization and legal rewrites preserve `remotePredicate`.
- [ ] Verify dynamic-filter planner remains exact-only.
- [ ] Run focused diagnostics/statistics/resource tests and update A8 evidence.

### Task 8: A9 final validation and documentation

**Files:** source plan and roadmap only after implementation is stable.

- [ ] Run the required focused commands, AirStyle, full module tests, clean Error Prone verify, and normal ES7/ES8 suites inside Compose.
- [ ] Inspect Compose logs and record test counts, failures, errors, skips, packaging, and exact SHA.
- [ ] Run `git diff --check` and an independent diff review against both source-of-truth documents.
- [ ] Update roadmap/plan evidence accurately, including CI status; do not claim COMPLETE without green CI on the exact final SHA.
