# P1.2 Full Test Architecture Audit

This audit is required because P1.2 changes the Elasticsearch predicate translation and boolean-composition architecture. The goal is not merely to keep tests green. Each existing test source is checked against the production architecture so obsolete implementation tests do not masquerade as regression coverage.

## Acceptance inheritance

The Elasticsearch 7 and 8 suites use the same cumulative acceptance hierarchy:

```text
TestElasticsearch7ConnectorTest / TestElasticsearch8ConnectorTest
        ↓
BaseElasticsearchPredicateCompositionTest     (P1.2)
        ↓
BaseElasticsearchAnyMatchPushdownTest         (P1.1)
        ↓
BaseElasticsearchP0PredicatePushdownTest      (P0)
        ↓
BaseElasticsearchFullTextPushdownTest
        ↓
BaseElasticsearchConnectorTest
        ↓
Trino BaseConnectorTest
```

Therefore adding P1.2 does not replace P0/P1.1 acceptance coverage. Both Elasticsearch generations rerun the complete inherited behavior contract.

## Classification vocabulary

- **CURRENT-SEMANTIC** — validates SQL/Elasticsearch behavior independent of an implementation detail.
- **CURRENT-ARCH** — validates a permanent production abstraction introduced or retained by the current architecture.
- **COMPATIBILITY** — validates a compatibility boundary that is still reachable in production and therefore must remain tested.
- **SUPERSEDED** — test definition remains in an ancestor but current suites override it with the newer contract; it is not counted as current coverage.
- **INFRASTRUCTURE** — test fixture/support code, compiled and exercised by integration tests but not itself a behavior contract.
- **INDEPENDENT** — unrelated to predicate-composition architecture; unchanged but mandatory in the full module regression run.
- **REWRITTEN** — old implementation-based assertion was migrated to the new permanent architecture.
- **REMOVED-OBSOLETE** — old implementation path no longer executes in production; test was removed rather than changing expected output.
- **ADDED-GAP** — architecture audit found a permanent contract without direct test coverage, so a new test was added.

## Complete inventory

| # | Test source | Classification | P1.2 audit decision |
|---:|---|---|---|
| 1 | `BaseElasticsearchAnyMatchPushdownTest.java` | CURRENT-SEMANTIC / CURRENT-ARCH | Keep. P1.1 same-element semantics are mandatory regression coverage under P1.2. |
| 2 | `BaseElasticsearchConnectorTest.java` | CURRENT-SEMANTIC + SUPERSEDED definitions | Keep as inherited baseline. Some historical predicate methods are overridden by FullText/P0; those ancestor definitions are not counted as current predicate coverage. |
| 3 | `BaseElasticsearchFullTextPushdownTest.java` | CURRENT-SEMANTIC | Keep. DISABLED/SAFE/UNSAFE behavior and residual semantics remain production contracts. |
| 4 | `BaseElasticsearchP0PredicatePushdownTest.java` | CURRENT-SEMANTIC / CURRENT-ARCH | Keep. Native Terms, dynamic filtering, array membership, NULL edges and same-field regexp behavior must survive P1.2. |
| 5 | `BaseElasticsearchPredicateCompositionTest.java` | CURRENT-SEMANTIC / CURRENT-ARCH | Keep. P1.2 ES7/ES8 acceptance contract. |
| 6 | `ElasticsearchLoader.java` | INFRASTRUCTURE | No architecture assertion to rewrite. Still compiled/used by connector tests. |
| 7 | `ElasticsearchQueryRunner.java` | INFRASTRUCTURE | No architecture assertion to rewrite. Required by integration suite. |
| 8 | `ElasticsearchServer.java` | INFRASTRUCTURE | No architecture assertion to rewrite. Required by ES7/ES8 integration suite. |
| 9 | `TestAggregationQueryPageSource.java` | INDEPENDENT | Aggregation page decoding is not changed by P1.2; still mandatory in full module run. |
| 10 | `TestAwsSecurityConfig.java` | INDEPENDENT | Security configuration unaffected; keep and rerun. |
| 11 | `TestBuildSort.java` | INDEPENDENT | Sort construction unaffected; keep and rerun. |
| 12 | `TestElasticsearch7ConnectorTest.java` | CURRENT-ARCH | Keep. Entry point for cumulative P1.2→P1.1→P0→FullText→Base acceptance on ES7. |
| 13 | `TestElasticsearch8ConnectorTest.java` | CURRENT-ARCH | Keep. Entry point for cumulative P1.2→P1.1→P0→FullText→Base acceptance on ES8. |
| 14 | `TestElasticsearchArrayPredicateTranslator.java` | CURRENT-ARCH | Keep. Owns same-element proof boundary for `any_match`; document-level composer must not reinterpret these tests. |
| 15 | `TestElasticsearchComplexTypePredicatePushDown.java` | CURRENT-SEMANTIC | Keep. Nested primitive/ROW/ARRAY predicate and no-data-read behavior remains a broad regression contract. |
| 16 | `TestElasticsearchConfig.java` | INDEPENDENT / CONFIG | Keep. Existing dynamic-filter/resource configuration remains mandatory even though P1.2 composition policy currently has its own permanent policy object. |
| 17 | `TestElasticsearchDynamicFilterPlanner.java` | CURRENT-ARCH | Keep. Exact-only dynamic filters, Terms batching and query-byte fallback must not be altered by P1.2 normalization. |
| 18 | `TestElasticsearchMetadata.java` | CURRENT-SEMANTIC | Keep. `LIKE`→regexp helper semantics remain used by current translation. |
| 19 | `TestElasticsearchPredicateComposer.java` | CURRENT-ARCH / REWRITTEN | Updated: partial OR and unproven NOT are planner-owned residuals, not compatibility `remaining`. |
| 20 | `TestElasticsearchPredicateCompositionPlanner.java` | CURRENT-ARCH / REWRITTEN | Updated to assert planner-owned residuals for partial OR/NOT and current document-scope composition behavior. |
| 21 | `TestElasticsearchPredicateCompositionPolicy.java` | CURRENT-ARCH | Keep. Resource-shape limits are part of the permanent composer contract. |
| 22 | `TestElasticsearchPredicateCompositionRequestBudget.java` | CURRENT-ARCH | Keep. Oversized composed predicates must fall back safely rather than create an unsafe/oversized remote request. |
| 23 | `TestElasticsearchPredicatePushdownPlanner.java` | CURRENT-ARCH | Keep. Direct planner→IR behavior is the current predicate planning contract. Synthetic LIKE range handling remains relevant because Trino DomainTranslator can still contribute that domain. |
| 24 | `TestElasticsearchPredicateTranslation.java` | CURRENT-ARCH / ADDED-GAP | Added by the architecture audit. Directly validates EXACT/PREFILTER ownership, compatibility `remaining` versus planner-owned `residual`, and result invariant enforcement. |
| 25 | `TestElasticsearchProjectionPushdownPlans.java` | CURRENT-ARCH | Keep. Proves remote predicate state survives projection/dereference and join planning in the table handle. |
| 26 | `TestElasticsearchQueryBuilder.java` | COMPATIBILITY + REMOVED-OBSOLETE | Rewritten. Generic TupleDomain compatibility rendering remains; old analyzed-text synthetic-domain MatchPhrase/legacy MatchPhrasePrefix tests were removed. Direct full-text IR rendering is covered by `TestElasticsearchRemotePredicateQueryBuilder`. |
| 27 | `TestElasticsearchRemoteColumnCase.java` | CURRENT-ARCH / REWRITTEN | Migrated analyzed-text casing test from legacy TupleDomain query building to planner→Remote Predicate IR. |
| 28 | `TestElasticsearchRemotePredicateQueryBuilder.java` | CURRENT-ARCH | Keep. Canonical DSL renderer tests for Term/Terms/Range/Prefix/Regexp/MatchPhrase/MatchPhrasePrefix/Exists/And/Or/Not/Enforced. |
| 29 | `TestElasticsearchRemotePredicateTranslator.java` | CURRENT-ARCH + COMPATIBILITY | Keep. Normal translation/composition tests are current; `canonicalize` legacy-state test is legitimate because runtime compatibility fallback still canonicalizes legacy state into IR. |
| 30 | `TestElasticsearchTableHandle.java` | CURRENT-ARCH + COMPATIBILITY | Keep. IR serialization, connector-handle round trip and copy preservation are permanent; legacy constructor behavior remains a compatibility construction contract. |
| 31 | `TestLikePrefix.java` | CURRENT-SEMANTIC | Keep. Pure LIKE-prefix recognition helper used by the current planner. |
| 32 | `TestPasswordConfig.java` | INDEPENDENT | Authentication configuration unaffected; keep and rerun. |
| 33 | `TestRegexpPushdownTranslator.java` | CURRENT-SEMANTIC | Keep. Current planner still consumes `translateRegexpLike`; exact/approximate/unsupported classification remains production behavior. |
| 34 | `TestRuleBasedElasticsearchMetadata.java` | CURRENT-ARCH / REMOVED-OBSOLETE | Rewritten to facade fixed-point and residual orchestration only. Tests for retired synthetic-domain lowering helper were removed. |
| 35 | `client/TestExtractAddress.java` | INDEPENDENT | Client address parsing unaffected; keep and rerun. |
| 36 | `client/TestKeywordSubfield.java` | CURRENT-SEMANTIC | Keep. Exact-predicate safety of keyword sub-fields directly affects planner field selection. |

## Obsolete tests removed or migrated in P1.2

### Synthetic full-text `TupleDomain` lowering

Old tests in `TestRuleBasedElasticsearchMetadata` directly exercised `rewriteUnsafeFullTextConstraint()` and asserted temporary synthetic Domain transport. Runtime planning now lowers to `ElasticsearchRemotePredicate` directly, so those tests were removed instead of updating expected values.

Replacement coverage exists at the owning abstractions:

- `TestElasticsearchPredicateTranslation`
- `TestElasticsearchPredicatePushdownPlanner`
- `TestElasticsearchPredicateComposer`
- `TestElasticsearchPredicateCompositionPlanner`
- `TestElasticsearchRemotePredicateQueryBuilder`
- ES7/ES8 cumulative acceptance suites

### Legacy QueryBuilder full-text transport

Old `TestElasticsearchQueryBuilder` cases manufactured analyzed-text `TupleDomain`/legacy prefix-map state solely to render `match_phrase` or `match_phrase_prefix`. These are no longer the primary production transport for new predicate functionality and were removed.

The canonical equivalents are tested directly as Remote Predicate IR in `TestElasticsearchRemotePredicateQueryBuilder` and end-to-end in `BaseElasticsearchFullTextPushdownTest`.

### Remote field casing

The analyzed-text case-preservation test formerly built a legacy analyzed-text Domain directly. It was migrated to:

```text
Constraint
  -> ElasticsearchPredicatePushdownPlanner
  -> Enforced(MatchPhrase("Ho_ten", ...), APPROXIMATE)
```

This protects the same user-visible casing behavior on the actual current architecture.

### Partial OR and NOT ownership

Old P1.2 expectations treated rejected partial OR and unproven NOT as compatibility `remaining`. That was architecturally unsafe because legacy fallback could retry a subtree the composer deliberately rejected.

Tests now require:

```text
partial OR / unproven NOT
  -> no remote predicate
  -> no compatibility remaining expression
  -> planner-owned Trino residual
```

### Missing direct translation-result coverage

The new `ElasticsearchPredicateTranslation` abstraction initially had no dedicated unit test. The audit added `TestElasticsearchPredicateTranslation` rather than assuming composer tests indirectly covered the result contract.

The new test distinguishes:

```text
unsupported
  -> remaining
  -> compatibility may inspect it

planner-owned residual
  -> residual
  -> compatibility must not retry it
```

and verifies EXACT/PREFILTER constructor invariants.

## Compatibility paths intentionally retained

P1.2 does not delete all legacy state atomically. The following compatibility tests remain intentional:

- legacy predicate state canonicalized into `ElasticsearchRemotePredicate` at the `RuleBasedElasticsearchMetadata` boundary;
- generic `TupleDomain` state rendered together with an already planned remote predicate;
- legacy table-handle constructor/serialization compatibility where still supported.

No new P1.2 feature may be implemented on top of these compatibility paths.

## Architecture-sensitive test groups to pass before completion

The following groups must pass on the final squashed P1.2 head:

1. Translation and IR
   - `TestElasticsearchPredicateTranslation`
   - `TestElasticsearchPredicatePushdownPlanner`
   - `TestElasticsearchArrayPredicateTranslator`
   - `TestElasticsearchRemotePredicateTranslator`
   - `TestElasticsearchRemotePredicateQueryBuilder`
   - `TestElasticsearchTableHandle`

2. Composition
   - `TestElasticsearchPredicateComposer`
   - `TestElasticsearchPredicateCompositionPlanner`
   - `TestElasticsearchPredicateCompositionPolicy`
   - `TestElasticsearchPredicateCompositionRequestBudget`

3. Metadata/planner boundaries
   - `TestRuleBasedElasticsearchMetadata`
   - `TestElasticsearchProjectionPushdownPlans`
   - `TestElasticsearchRemoteColumnCase`

4. Dynamic filtering/resource safety
   - `TestElasticsearchDynamicFilterPlanner`

5. Full ES acceptance
   - `TestElasticsearch7ConnectorTest`
   - `TestElasticsearch8ConnectorTest`
   - inherited FullText/P0/P1.1/P1.2 suites

6. Entire module
   - every architecture-independent test listed in the inventory above

## Final completion gate

P1.2 must not be marked complete until all of these are true:

- the complete test inventory above has been reviewed against the final production architecture;
- obsolete implementation tests have been removed or migrated;
- no current behavior is covered only by a superseded ancestor test;
- focused architecture-sensitive tests pass;
- AirStyle passes;
- the complete `:trino-elasticsearch` module test suite passes;
- Elasticsearch 7 cumulative acceptance passes;
- Elasticsearch 8 cumulative acceptance passes;
- Error Prone/compile checks pass;
- final CI is run on the cleaned/squashed branch history;
- P1.3 can consume `ElasticsearchPredicateTranslation`, composer, IR and reason metadata without replacing them.
