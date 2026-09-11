# Elasticsearch Primitive Array UNSAFE Pushdown Plan

## Status

**IMPLEMENTATION IN PROGRESS — A0 PASS; A1 PASS; A2 PASS; A3 PASS; A4 PASS; A5 PASS; A6 PASS; A7 PASS; A8 PASS; A9 BLOCKED BY EXTERNAL VALIDATION**

Planning branch: `docs/elasticsearch-unsafe-array-pushdown-plan`

Baseline audited from `master` at commit:

```text
44d719ac2977c98532234f3002376551997f00ac
```

## Implementation evidence

### A0 — Baseline freeze

**Status:** PASS

The production baseline is `44d719ac2977c98532234f3002376551997f00ac`. `origin/master`
was verified at the same SHA before implementation, so the plan's audited baseline had not
advanced. Implementation is isolated in worktree
`/home/admin/github/trino/.claude/worktrees/feature-elasticsearch-unsafe-array-full-text`
on branch `feature/elasticsearch-unsafe-array-full-text`. The worktree was clean before
production changes; the current HEAD at the time of this evidence update is recorded by the
gate commit below.

The initial `docker compose up -d --build` attempt stalled while downloading the external base
image. The already available `trino-maven:latest` image subsequently started successfully with
`docker compose up -d`; this is an infrastructure observation, not a code blocker. The linked
worktree mount also required the documented Maven option
`-Dmaven.gitcommitid.skip=true` because its `.git` file points outside the mounted container
workspace.

Reactor bootstrap:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -am -DskipTests install
RESULT: BUILD SUCCESS (11:17)
```

The requested abstract base-class selectors were confirmed not to be runnable selectors in this
repository (`Tests run: 0`, `No tests were executed`). Their concrete ES7 inherited methods were
used for the focused baseline instead:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testPrimitiveArrayExactMembershipPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testAnyMatchPrimitiveArrayExactPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeLikePushdownUsesTextAnalyzer test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testRegexpLikeIsNotPushedDown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicatePushdownPlanner test
RESULT: BUILD SUCCESS; Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
```

Current semantic observations are unchanged from the audited baseline: `contains(text_tags,
'telegram')` and `any_match(text_tags, x -> x = 'telegram')` remain local with a Trino
`FilterNode`; exact keyword/numeric/timestamp array paths remain fully pushed. Scalar `UNSAFE`
LIKE and valid regexp translations are authoritative, while unsupported regexp syntax remains
local. No production source was changed during A0.

Gate files changed: this evidence section and the committed implementation plan at
`docs/superpowers/plans/2026-09-11-elasticsearch-unsafe-array-full-text.md`.

The A0 evidence update was committed at `06e4b74a651`.

### A1 — Enforcement-aware ARRAY translation

**Status:** PASS

`ElasticsearchArrayPredicateTranslator` now returns the permanent
`Optional<ElasticsearchPredicateTranslation<ConnectorExpression>>` contract and accepts the
connector session and full-text mode at the translation boundary. Recognized array predicates
that cannot be translated are returned as `unsupported(..., remaining)` rather than being
silently treated as exact or falling through a temporary representation. The planner consumes
the translation directly; it no longer wraps every returned ARRAY remote predicate as `EXACT`.
Existing exact paths still produce `EXACT_ARRAY` or `EXACT_ANY_MATCH`, and the contract's
invariant rejects any attempt to strengthen an `APPROXIMATE` IR node to `EXACT`.

Files changed in A1:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicatePushdownPlanner.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchPredicateTranslation.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchSourceValueSemantics.java
```

Test-first RED check:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateTranslation test
RESULT: expected compilation failure; tests required the new four-argument permanent API while production still exposed the old two-argument API.
```

Focused A1 verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateTranslation,TestElasticsearchPredicatePushdownPlanner,TestElasticsearchSourceValueSemantics test
RESULT: BUILD SUCCESS; Tests run: 42, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testPrimitiveArrayExactMembershipPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testAnyMatchPrimitiveArrayExactPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

The tests prove exact ARRAY translation remains `EXACT`, unsupported analyzed-text ARRAY
translation remains local, approximate IR is representable through the same contract, and
approximate IR cannot be strengthened to exact. A1 intentionally adds no analyzed-text ARRAY
capability yet; `contains(text_tags, ...)` and analyzed `any_match` remain local until A3.
There is no architectural contradiction with `ROADMAP-PUSHDOWN.md`, and no remaining A1 blocker.

Current gate HEAD: `7215c16e705`.

### A2 — Preserve exact ARRAY behavior

**Status:** PASS

A2 introduced no capability changes. Existing exact primitive-array translation and composition
remain on the same IR paths, including keyword/text.keyword, numeric, timestamp, boolean, IP,
membership, exact OR, and same-element-safe fused range AND behavior. The permanent translation
contract migration did not change exact results or dynamic-filter ownership.

Files changed in A2: none beyond the A1 implementation and tests already listed above.

Exact regression verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateComposer,TestElasticsearchPredicateCompositionPlanner,TestElasticsearchPredicateCompositionPolicy,TestElasticsearchPredicateCompositionRequestBudget,TestElasticsearchPredicatePushdownPlanner,TestElasticsearchPushdownDiagnostics,TestElasticsearchSourceValueSemantics test
RESULT: BUILD SUCCESS; Tests run: 64, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testPrimitiveArrayExactMembershipPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testAnyMatchPrimitiveArrayExactPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Semantic finding: exact array behavior is unchanged and no A2 blocker remains. Analyzed-text
ARRAY equality/membership is still intentionally local; A3 is the first capability gate for
UNSAFE approximate element predicates.

Current gate HEAD: `5880dcea1fd`.

### A3 — UNSAFE analyzed equality and membership

**Status:** PASS

The permanent ARRAY translation contract now has one shared element-membership path for
`contains`, `arrays_overlap`, `any_match` equality, and `any_match` IN. Exact mappings still
use `Term`/`Terms` and report `EXACT_ARRAY` or `EXACT_ANY_MATCH`. Analyzed `text` arrays with
no keyword subfield are accepted only in `UNSAFE`; each value lowers to `MatchPhrase` and is
explicitly marked `APPROXIMATE_ARRAY` or `APPROXIMATE_ANY_MATCH`. Multi-value membership is
composed through `ElasticsearchPredicateComposer`, so the same permanent boolean, residual,
diagnostics, and resource-policy contracts apply. SAFE and DISABLED remain local for analyzed
ARRAY membership.

The shared request guard now covers both query bytes and recursive Elasticsearch boolean-clause
counts. Scalar analyzed discrete-domain disjunctions and analyzed ARRAY membership therefore
fall back to local evaluation together when the common policy is exceeded; no array-only bound
was introduced. Dynamic filters continue to use exact term/domain planning only.

Files changed in A3:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicateComposer.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicateTranslation.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPushdownDiagnostics.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchRemotePredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchParallelConnectorTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchUnsafeArrayPushdownTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchRemotePredicateTranslator.java
```

Test-first RED check:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchArrayPredicateTranslator test
RESULT: expected test-compilation failure before production changes; the new approximate-array reason categories and
analyzed ARRAY expectations were not yet represented by the production contract.
```

Focused unit and resource-safety verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchRemotePredicateTranslator,TestElasticsearchPredicateCompositionPolicy,TestElasticsearchPushdownDiagnostics test
RESULT: BUILD SUCCESS; Tests run: 43, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateComposer,TestElasticsearchPredicateCompositionPlanner,TestElasticsearchPredicateCompositionPolicy,TestElasticsearchPredicateCompositionRequestBudget,TestElasticsearchPredicatePushdownPlanner,TestElasticsearchPushdownDiagnostics,TestElasticsearchSourceValueSemantics,TestElasticsearchRemotePredicateTranslator test
RESULT: BUILD SUCCESS; Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
```

Connector acceptance and exact regression verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArrayEqualityAndMembershipPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeAnalyzedArrayEqualityAndMembershipPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testPrimitiveArrayExactMembershipPushdown,TestElasticsearch7ConnectorTest#testAnyMatchPrimitiveArrayExactPushdown test
RESULT: BUILD SUCCESS; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testPrimitiveArrayExactMembershipPushdown,TestElasticsearch8ConnectorTest#testAnyMatchPrimitiveArrayExactPushdown test
RESULT: BUILD SUCCESS; Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

Semantic findings: analyzed ARRAY translations are authoritative only under explicit UNSAFE
semantics and carry effective `APPROXIMATE` enforcement; no SQL residual remains for accepted
single-value or bounded multi-value membership. Invalid or NULL-containing constants remain
local. Exact keyword/text.keyword arrays and exact numeric/timestamp paths remain unchanged.
The shared non-`Terms` disjunction guard prevents oversized analyzed requests. No same-element
boundary was widened: analyzed lambda bodies other than direct equality/IN remain local until
their own semantic gates. There is no architectural contradiction with `ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `1748b84758b`.
Current gate HEAD before this evidence commit: `1748b84758b`.

### A4 — UNSAFE LIKE and prefix parity inside `any_match`

**Status:** PASS

Scalar and ARRAY-element full-text translation now share
`ElasticsearchFullTextPredicateTranslator`. The shared helper reuses the existing analyzed-LIKE
rewrite for literal and contains-literal patterns, the existing metadata conversion for prefix
and escaped patterns, and the existing exact keyword/prefix strategies. Array `any_match` uses
the enclosing lambda call as the translation source, so an unsupported element shape remains a
local ARRAY predicate rather than becoming an unrelated remote candidate. Analyzed ARRAY LIKE
and `starts_with` translations in `UNSAFE` are explicit `APPROXIMATE`; SAFE and DISABLED remain
residual. Token-spanning internal wildcard shapes remain unsupported, matching scalar behavior.

Files changed in A4:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchFullTextPredicateTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchPredicatePushdownPlanner.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/expression/ElasticsearchExpressionTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/expression/RewriteAnalyzedTextLike.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchUnsafeArrayPushdownTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
```

Focused unit, scalar-regression, style, and connector acceptance verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicatePushdownPlanner test
RESULT: BUILD SUCCESS; Tests run: 35, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeLikePushdownUsesTextAnalyzer,TestElasticsearch7ConnectorTest#testLike,TestElasticsearch7ConnectorTest#testRegexpLikeIsNotPushedDown test
RESULT: BUILD SUCCESS; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeLikePushdownUsesTextAnalyzer,TestElasticsearch8ConnectorTest#testLike,TestElasticsearch8ConnectorTest#testRegexpLikeIsNotPushedDown test
RESULT: BUILD SUCCESS; Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArrayLikeAndPrefixPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeAnalyzedArrayLikeAndPrefixPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch airstyle:check
RESULT: BUILD SUCCESS; All 144 files are already formatted.
```

Semantic findings: literal LIKE, `%literal%`, prefix LIKE, `starts_with`, and escaped LIKE
patterns use the scalar strategy and report `APPROXIMATE` for analyzed ARRAY elements in
UNSAFE. Multi-token phrase behavior is covered by the custom standard/lowercase/asciifolding
fixture. SAFE remains residual, and unsupported token-spanning LIKE remains local. Exact array
membership behavior and scalar LIKE/regexp regressions remain green. There is no architectural
contradiction with `ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `d4301a52f93`.
Current gate HEAD before this evidence commit: `d4301a52f93`.

### A5 — UNSAFE `regexp_like` parity inside `any_match`

**Status:** PASS

Array-element regexp translation now invokes the same
`CasePreservingElasticsearchMetadata.translateRegexpLike` classifier used by scalar regexp
pushdown through `ElasticsearchFullTextPredicateTranslator`. Valid simple, alternation, and
non-capturing-group patterns become `Regexp` IR nodes with explicit `APPROXIMATE` enforcement
for analyzed ARRAY elements in `UNSAFE`. Unsupported lookaround syntax produces a local
residual even under `UNSAFE`; raw SQL/Joni syntax is never sent directly to Lucene.

Files changed in A5:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchUnsafeArrayPushdownTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
```

Test-first RED check:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchArrayPredicateTranslator test
RESULT: expected failure; Tests run: 28, Failures: 1, Errors: 0, Skipped: 0 because the new valid regexp array expectation was still local before production wiring.
```

Focused and scalar-regression verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicatePushdownPlanner test
RESULT: BUILD SUCCESS; Tests run: 38, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testRegexpLikeIsNotPushedDown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testRegexpLikeIsNotPushedDown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArrayRegexpPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeAnalyzedArrayRegexpPushdown test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

The focused Maven runs also passed AirStyle/checkstyle before executing tests. Semantic findings:
valid analyzed ARRAY regexp translations are authoritative only in UNSAFE and have no residual;
SAFE remains local for the analyzed array; unsupported regex remains local in both the planner
contract and connector acceptance. Unicode/case-folded fixture values continue to exercise
analysis semantics. Scalar regexp acceptance/rejection is unchanged. There is no architectural
contradiction with `ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `764e41da035`.
Current gate HEAD before this evidence commit: `764e41da035`.

### A6 — UNSAFE OR composition within `any_match`

**Status:** PASS

Analyzed `any_match` OR branches are now translated recursively and composed through the
permanent `ElasticsearchPredicateComposer`. Every branch must provide a remote translation;
unsupported or invalid branches cause the composer to retain the complete enclosing
`any_match` as one residual. Supported equality, LIKE/prefix, and regexp branches are emitted
as one approximate `Or` IR node. This is safe for existential OR because
`exists(element, P(element) OR Q(element))` distributes to the OR of same-field existential
branches; no lambda AND is accepted by this path.

Files changed in A6:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchUnsafeArrayPushdownTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
```

Test-first RED check:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchArrayPredicateTranslator test
RESULT: expected failure; Tests run: 30, Failures: 2, Errors: 0, Skipped: 0 because supported analyzed OR had no remote predicate and the partial-OR contract had not yet been delegated to the composer.
```

Focused and connector acceptance verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateComposer,TestElasticsearchPredicateCompositionPolicy test
RESULT: BUILD SUCCESS; Tests run: 48, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArrayOrPushdownRequiresEveryBranch test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeAnalyzedArrayOrPushdownRequiresEveryBranch test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Semantic findings: fully translatable analyzed ORs are `APPROXIMATE` with no residual;
partial ORs are not pushed at all and remain one Trino residual. The cross-element AND trap is
still not handled by this implementation and remains reserved for A7 hardening. Composer
request/resource policy and diagnostics remain the single shared path. There is no architectural
contradiction with `ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `2217824367c`.
Current gate HEAD before this evidence commit: `2217824367c`.

### A7 — Same-element hardening and semantic traps

**Status:** PASS

No new remote capability was added in A7. The tests explicitly preserve the boundary between
document scope and ARRAY-element scope. Generic analyzed-text lambda AND remains unsupported;
the regression fixture contains `names = ['Nguyen Anh', 'Le Van']` and proves
`x LIKE 'Nguyen%' AND x LIKE '%Van%'` retains a Trino `FilterNode` rather than becoming two
independent remote predicates. Only the existing same-element-safe exact fused numeric range
path remains unchanged.

The acceptance fixture also covers NULL elements, an only-NULL array, an empty array, a missing
field, duplicate values, multi-token analyzed values, custom standard/lowercase/asciifolding
analysis, case folding, asciifolding, Unicode text, unsupported regexp syntax, and a top-level
OR mixing an approximate analyzed ARRAY branch with an exact keyword-subfield ARRAY branch.

Files changed in A7:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchFullTextPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/BaseElasticsearchUnsafeArrayPushdownTest.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
```

Focused and acceptance verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchArrayPredicateTranslator test
RESULT: BUILD SUCCESS; Tests run: 31, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArraySemanticBoundaries test
RESULT: initial fixture assertion failed because the correct local result includes single-element matches (IDs 2 and 6); no pushdown assertion was reached. The fixture expectation was corrected from empty to `VALUES VARCHAR '2', VARCHAR '6'`.

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch7ConnectorTest#testUnsafeAnalyzedArraySemanticBoundaries test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearch8ConnectorTest#testUnsafeAnalyzedArraySemanticBoundaries test
RESULT: BUILD SUCCESS; Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Semantic findings: the cross-element trap remains local on both backends; null/missing/empty
and duplicate source behavior is exercised without weakening the translation contract; analyzed
Unicode and folded values use full-text strategies; mixed exact/approximate OR is classified by
the shared composer as approximate. An independent review first added a red regression for a
lambda body whose LIKE/prefix/regexp operand was a different variable; the production helper
now verifies the actual lambda element before translating those forms. There is no architectural contradiction with
`ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `12ca0225aa7`.
Current gate HEAD before this evidence commit: `12ca0225aa7`.

### A8 — Diagnostics, statistics, and lifecycle integration

**Status:** PASS

No second diagnostics pipeline was introduced. Existing P1.3 accounting now observes
`APPROXIMATE_ARRAY` and `APPROXIMATE_ANY_MATCH` through the established approximate, ARRAY, and
ANY_MATCH counters; approximate `MatchPhrase` remote IR nodes use the existing recursive node
accounting. The statistics guard already treats any non-EXACT remote predicate as unknown, and
now has an explicit analyzed-array `MatchPhrase` regression. Dynamic filtering remains owned by
`ElasticsearchDynamicFilterPlanner`, which rejects both analyzed scalar and analyzed primitive
ARRAY columns before any approximate full-text translation can enter the dynamic-filter path.

Files changed in A8:

```text
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchDynamicFilterPlanner.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchPushdownDiagnostics.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchRemoteStatistics.java
```

Verification:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchPushdownDiagnostics,TestElasticsearchDynamicFilterPlanner,TestElasticsearchRemoteStatistics test
RESULT: first attempt stopped at AirStyle because the new dynamic-filter fixture needed formatting; no tests ran.

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch airstyle:format
RESULT: BUILD SUCCESS; formatted 1 file.

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Dtest=TestElasticsearchPushdownDiagnostics,TestElasticsearchDynamicFilterPlanner,TestElasticsearchRemoteStatistics test
RESULT: BUILD SUCCESS; Tests run: 30, Failures: 0, Errors: 0, Skipped: 0; AirStyle and checkstyle passed.
```

Semantic findings: approximate ARRAY and ANY_MATCH decisions are observable through the existing
P1.3 categories; filtered statistics remain conservative/unknown for approximate IR; dynamic
filters accept only exact term/range/domain paths, including analyzed primitive ARRAY columns.
There is no architectural contradiction with `ROADMAP-PUSHDOWN.md`.

Feature implementation commit: `e4a5dc00aca`.
Current gate HEAD before this evidence commit: `e4a5dc00aca`.

### A9 — Final acceptance, independent review, and release evidence

**Status:** BLOCKED — external validation remains unverified

The implementation review against both `ROADMAP-PUSHDOWN.md` and this plan found no semantic
or architectural contradiction. The final review specifically checked enforcement classification,
residual removal, partial OR rejection, same-element lambda scope, scalar/ARRAY translation reuse,
resource bounds, diagnostics/statistics, and dynamic-filter isolation. The last implementation
change adds an early shared-policy bound for analyzed ARRAY membership disjunctions and a query-byte
check for singleton analyzed phrases; over-budget values remain local.

Final focused verification at implementation HEAD `c261765de362d878be9721cd3cd407ff2a8fc76c`:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch \
  -Dtest=TestElasticsearchArrayPredicateTranslator,TestElasticsearchPredicateTranslation,TestElasticsearchPredicateComposer,TestElasticsearchPredicateCompositionPlanner,TestElasticsearchPredicateCompositionPolicy,TestElasticsearchPredicateCompositionRequestBudget,TestElasticsearchPredicatePushdownPlanner,TestElasticsearchPushdownDiagnostics,TestElasticsearchSourceValueSemantics,TestElasticsearchRemotePredicateTranslator,TestElasticsearchDynamicFilterPlanner,TestElasticsearchRemoteStatistics test
RESULT: BUILD SUCCESS; Tests run: 121, Failures: 0, Errors: 0, Skipped: 0

docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch airstyle:check
RESULT: BUILD SUCCESS; all 144 files formatted; checkstyle reported 0 violations.

docker compose exec -T maven ./mvnw -nsu -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch -Perrorprone-compiler clean verify -DskipTests
RESULT: BUILD SUCCESS; clean Error Prone compilation, test compilation, packaging, dependency checks,
AirStyle, checkstyle, modernizer, and static verification passed. Existing repository Error Prone
warnings remained warnings.
```

The required full module command was also executed at this implementation HEAD:

```text
docker compose exec -T maven ./mvnw -Dmaven.gitcommitid.skip=true \
  -pl :trino-elasticsearch test
RESULT: BUILD FAILURE; Tests run: 1563, Failures: 1, Errors: 0, Skipped: 682. The only failure was
the unrelated baseline TestElasticsearch7PointInTimeConnectorTest.testSelectAll HTTP 404
"Query not found" while fetching a Trino statement page. The normal TestElasticsearch7ConnectorTest
and TestElasticsearch8ConnectorTest classes each completed with Tests run: 328, Failures: 0,
Errors: 0, Skipped: 170. Re-running the failing point-in-time method alone at the same HEAD passed:
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0.
```

The same HTTP 404 appeared in earlier aggregate runs and disappeared in isolated reruns; it is a
test-harness/concurrency infrastructure flake, not a changed predicate path. The initially observed
Docker image download stall and subsequent Testcontainers Docker address-pool exhaustion were also
external infrastructure observations. The required abstract base-class selectors remain repository
non-runnable selectors (`Tests run: 0`, `No tests were executed`); their concrete ES7/ES8 inherited
tests were used instead.

GitHub CI is not yet verifiable: no PR was created or pushed from this workspace, so there is no CI
run ID for the exact final SHA. A9 therefore cannot be marked complete under the plan’s release gate.

Files changed during A9 evidence hardening:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/ElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/src/test/java/io/trino/plugin/elasticsearch/TestElasticsearchArrayPredicateTranslator.java
plugin/trino-elasticsearch/UNSAFE-ARRAY-PUSHDOWN-PLAN.md
```

Current implementation HEAD before this evidence update: `c261765de362d878be9721cd3cd407ff2a8fc76c`.

This document is an implementation handoff for extending `full_text_pushdown_mode=UNSAFE` to primitive Elasticsearch arrays, especially `ARRAY(VARCHAR)` backed by analyzed `text` fields.

The work must preserve the permanent architecture already established by `ROADMAP-PUSHDOWN.md`:

- Remote Predicate IR is the only remote predicate representation.
- `ElasticsearchPredicateTranslation` is the semantic translation contract.
- `EXACT`, `PREFILTER`, and `APPROXIMATE` enforcement must remain explicit.
- Same-element semantics inside `any_match` are owned by the array translator and must not be flattened into document-scope boolean logic.
- Dynamic filters remain exact-only.
- No temporary parallel predicate maps, synthetic domains, or throwaway planner paths may be introduced.

---

# 1. Objective

Bring primitive-array positive existential predicates in `UNSAFE` mode close to scalar analyzed-text pushdown parity.

The primary target is an Elasticsearch field declared to Trino as `ARRAY(VARCHAR)` through `_meta.trino.<field>.isArray=true`, where the Elasticsearch mapping is analyzed `text` and has no exact keyword subfield.

After this work, the following classes of SQL should be remotely executable in `UNSAFE` mode when the element predicate has a valid Elasticsearch translation:

```sql
contains(names, 'Nguyen Van')

arrays_overlap(names, ARRAY['Nguyen Van', 'Tran Thi'])

any_match(names, x -> x = 'Nguyen Van')

any_match(names, x -> x IN ('Nguyen Van', 'Tran Thi'))

any_match(names, x -> x LIKE 'Nguyen%')

any_match(names, x -> x LIKE '%Nguyen Van%')

any_match(names, x -> starts_with(x, 'Nguyen'))

any_match(names, x -> regexp_like(x, 'nguyen.*van'))

any_match(
    names,
    x -> x = 'Nguyen Van'
      OR x LIKE 'Tran%')
```

The intended remote enforcement for analyzed-text array predicates in `UNSAFE` is:

```text
APPROXIMATE
```

and, when the translation is accepted as authoritative by `UNSAFE`:

```text
Trino residual = none
```

This work does **not** make Elasticsearch full-text semantics equivalent to SQL string semantics. `UNSAFE` explicitly accepts analyzer/tokenization/regexp differences. The implementation must still preserve SQL operator identity and array logical scope.

---

# 2. Why this work is needed

## 2.1 Scalar analyzed text already has a meaningful UNSAFE path

The current scalar path already supports analyzed-text behavior such as:

```sql
text_column = 'foo bar'
text_column LIKE '%foo bar%'
text_column LIKE 'foo%'
regexp_like(text_column, 'foo.*bar')
```

under explicit `full_text_pushdown_mode=UNSAFE`.

The existing planner and expression translator can produce remote IR such as:

```text
MatchPhrase
MatchPhrasePrefix
Regexp
```

and mark these translations `APPROXIMATE` when Elasticsearch analysis semantics are authoritative.

`BaseElasticsearchFullTextPushdownTest` already contains acceptance coverage for:

- analyzed equality in `UNSAFE`;
- literal/contains-literal `LIKE`;
- multi-token phrase matching;
- prefix `LIKE`;
- custom lowercase + asciifolding analyzer behavior;
- `regexp_like` approximate translation;
- unsupported regexp syntax remaining in Trino.

## 2.2 Primitive array exact pushdown already exists

`ElasticsearchArrayPredicateTranslator` currently supports exact primitive-array forms including:

```text
contains
arrays_overlap
any_match equality
any_match IN
any_match numeric/timestamp ranges
any_match exact OR
any_match same-element-safe AND through fused ranges
```

Exact element types include numeric, boolean, timestamp, IP, keyword, and safe `text.keyword` cases.

`BaseElasticsearchP0PredicatePushdownTest` and `BaseElasticsearchAnyMatchPushdownTest` already verify this behavior.

## 2.3 The current architectural gap

The array translator currently returns only:

```java
Optional<ElasticsearchRemotePredicate>
```

while `ElasticsearchPredicatePushdownPlanner` treats any returned array predicate as exact.

Conceptually the current flow is:

```text
ElasticsearchArrayPredicateTranslator
        |
        v
Optional<RemotePredicate>
        |
        v
planner wraps as EXACT
```

This is valid for the current array feature set because analyzed-text-only element predicates are intentionally rejected.

It is **not** valid for the new feature set. If the translator simply starts returning `MatchPhrase`, `MatchPhrasePrefix`, or analyzed `Regexp`, the planner would incorrectly strengthen approximate semantics to `EXACT`.

Therefore the first production change must make array translation enforcement-aware before adding any new operator.

---

# 3. Semantic contract for UNSAFE

`UNSAFE` must be defined narrowly enough that it remains predictable.

## 3.1 What UNSAFE may approximate

`UNSAFE` may accept Elasticsearch field-analysis semantics as authoritative for a valid remote translation, including differences caused by:

- tokenization;
- lowercasing;
- asciifolding;
- stemming or stopword behavior;
- phrase matching;
- prefix matching over analyzed tokens;
- Lucene regexp behavior where the existing translator accepts the pattern;
- mapping-specific analyzed-field behavior such as `position_increment_gap`.

For example:

```sql
any_match(names, x -> x = 'NGÔ VĂN')
```

may lower to:

```text
MatchPhrase(names, "NGÔ VĂN")
APPROXIMATE
```

and Elasticsearch may match according to the configured analyzer rather than SQL source-string equality.

## 3.2 What UNSAFE must not redefine

`UNSAFE` must not change the identity of a SQL operator or the logical quantifier/scope of an array expression.

The following are forbidden transformations:

```text
whole-array equality -> membership
position access       -> unordered membership
cardinality           -> exists
same-element AND      -> document-scope AND
```

For example this SQL is **not** an alias for membership:

```sql
tags = ARRAY['a', 'b']
```

and this invalidly typed SQL is not a connector rewrite target:

```sql
tags = 'a'
```

If `tags` is `ARRAY(VARCHAR)`, `ARRAY(VARCHAR) = VARCHAR` is rejected by Trino type analysis before the connector can translate it. This project must not add an implicit `ARRAY<T> = T -> contains(array, value)` coercion.

Users must express element membership using supported array operators such as:

```sql
contains(tags, 'a')
any_match(tags, x -> x = 'a')
```

## 3.3 Same-element boundary

The permanent invariant from the roadmap remains in force:

```text
Document scope != same-element scope
```

Top-level SQL:

```sql
contains(tags, 'a') AND contains(tags, 'b')
```

allows two different array values to satisfy the two clauses.

Inside a lambda:

```sql
any_match(values, x -> P(x) AND Q(x))
```

requires one logical array element to satisfy both predicates.

A generic lowering such as:

```text
P(field) AND Q(field)
```

is invalid when Elasticsearch can satisfy `P` and `Q` using different field values.

The array translator may only lower `AND` when it can represent the whole same-element conjunction as one semantically valid remote predicate. The existing fused bounded range is the canonical example:

```sql
any_match(numbers, x -> x > 10 AND x < 20)
```

may become one:

```text
Range(numbers, lower=10 exclusive, upper=20 exclusive)
```

Generic analyzed-text `AND` is out of scope for this phase.

## 3.4 Single analyzed predicate versus composed predicates

A single remote full-text predicate such as:

```text
MatchPhrase(names, "Nguyen Van")
```

may inherit Elasticsearch multi-value/analyzer behavior in `UNSAFE`. That approximation is explicitly accepted by the mode.

This does **not** authorize decomposing a same-element lambda into multiple independent document-scope remote clauses.

The distinction is:

```text
one SQL element predicate -> one valid ES full-text predicate
    allowed in UNSAFE

P(x) AND Q(x) -> P(field) AND Q(field)
    forbidden unless same-element equivalence is proven
```

---

# 4. Target capability matrix

The implementation target is parity with the existing scalar UNSAFE translation surface, not an independent array-only query language.

| SQL element predicate | Exact keyword/structured array | Analyzed `text` + UNSAFE | Target enforcement | Notes |
| --- | --- | --- | --- | --- |
| `contains(a, C)` | existing `Term` | `MatchPhrase` | `APPROXIMATE` for analyzed text | positive membership |
| `arrays_overlap(a, ARRAY[C1,C2])` | existing `Term`/`Terms` | `Or(MatchPhrase...)` | `APPROXIMATE` | reject NULL constant members under current SQL-null safety rules |
| `any_match(a, x -> x = C)` | existing `Term` | `MatchPhrase` | `APPROXIMATE` | canonical equivalent of analyzed membership |
| `any_match(a, x -> x IN (...))` | existing `Term`/`Terms` | `Or(MatchPhrase...)` | `APPROXIMATE` | all constants must be valid/non-null |
| `any_match(a, x -> x < C)` etc. | existing `Range` for supported types | not a text feature | `EXACT` | preserve current behavior |
| `any_match(a, x -> x LIKE 'literal')` | exact string strategy where available | scalar-parity analyzed phrase strategy | `APPROXIMATE` | reuse scalar translation |
| `any_match(a, x -> x LIKE '%literal%')` | exact/regexp strategy where proven | `MatchPhrase` for scalar-supported shape | `APPROXIMATE` | do not duplicate LIKE parsing |
| `any_match(a, x -> x LIKE 'prefix%')` | `Prefix` | `MatchPhrasePrefix` | `APPROXIMATE` | scalar parity |
| `any_match(a, x -> starts_with(x, C))` | `Prefix` | analyzed prefix strategy | `APPROXIMATE` | use same helper as scalar prefix behavior |
| `any_match(a, x -> regexp_like(x, P))` | existing regexp semantics where supported | `Regexp` using existing regexp translator | `APPROXIMATE` | unsupported syntax remains residual |
| supported lambda `OR` | existing exact composition | `Or(...)` when every branch translates | derived `APPROXIMATE` | partial OR must not be pushed |
| generic lambda `AND` | only current same-element-safe forms | unsupported | residual | do not emit independent analyzed clauses |
| `NOT`, `<>`, `NOT IN`, `NOT LIKE` | not part of this work | not part of this work | — | future negative-predicate phase |
| `all_match`, `none_match` | not part of this work | not part of this work | — | quantifier/null semantics require separate proof |
| whole-array equality | engine only | engine only | — | operator identity cannot be reconstructed from ES multi-value index |
| `cardinality`, positional access, sequence/order functions | engine only | engine only | — | shape/order semantics are not generically indexed |

---

# 5. Permanent architecture target

## 5.1 Translation contract

The array translator must participate directly in the permanent translation-result architecture.

Preferred target shape:

```java
Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
        ConnectorSession session,
        ConnectorExpression expression,
        Map<String, ColumnHandle> assignments,
        FullTextPushdownMode fullTextMode)
```

Equivalent shapes are acceptable only if they preserve the same long-lived contract:

```text
remote predicate
+ enforcement
+ residual/remaining ownership
+ diagnostics decision
```

The planner must stop manufacturing `EXACT` merely because the array translator returned a remote predicate.

Target flow:

```text
ConnectorExpression
       |
       v
ElasticsearchArrayPredicateTranslator
       |
       v
ElasticsearchPredicateTranslation<ConnectorExpression>
       |
       +-- EXACT
       +-- APPROXIMATE
       +-- residual / unsupported
       |
       v
ElasticsearchPredicatePushdownPlanner
       |
       v
ElasticsearchTableHandle.remotePredicate
```

`ElasticsearchPredicateTranslation` already enforces critical invariants such as:

- exact translations cannot retain residual state;
- prefilters require residual/remaining state;
- approximate predicates cannot be silently strengthened to exact.

This work must consume that contract rather than introduce a second result type.

## 5.2 Do not duplicate scalar full-text semantics

Array support must not copy the SQL-LIKE parser or regexp-conversion logic into `ElasticsearchArrayPredicateTranslator`.

The implementation should extract/reuse a common analyzed-value translation helper from the existing scalar path where necessary.

Desired conceptual split:

```text
Array syntax / quantifier recognition
        |
        v
Array element semantic predicate
        |
        v
Common value/full-text translator
        |
        +-- MatchPhrase
        +-- MatchPhrasePrefix
        +-- Regexp
        +-- exact scalar IR where applicable
        |
        v
ElasticsearchPredicateTranslation
```

Array-specific code owns:

- confirming the source is an `ARRAY<T>`;
- binding the lambda variable to `T`;
- quantifier semantics;
- same-element proofs;
- extracting constant-array members;
- deciding whether the array syntax can be normalized to a supported element predicate.

Common scalar/full-text code owns:

- analyzed text detection;
- LIKE-shape recognition;
- SQL LIKE to remote pattern conversion;
- analyzed literal/contains-literal phrase strategy;
- analyzed prefix strategy;
- regexp syntax translation/classification;
- remote field selection;
- enforcement policy for full-text mode.

## 5.3 Canonicalize equivalent array forms

Equivalent positive existential forms should share one lowering path.

Conceptually:

```text
contains(a, C)
any_match(a, x -> x = C)
        |
        v
ELEMENT_EQ(a, C)
```

and:

```text
arrays_overlap(a, ARRAY[C1,C2])
any_match(a, x -> x IN (C1,C2))
        |
        v
ELEMENT_IN(a, [C1,C2])
```

The exact Java representation can be a small internal abstraction or direct helper calls. Do not create a second public predicate IR parallel to `ElasticsearchRemotePredicate`.

## 5.4 Field-name correctness

Exact keyword-backed arrays must continue to use `column.predicateName()`, including safe `.keyword` subfields.

Analyzed-text-only arrays must target the analyzed remote field and must not invent a nonexistent `.keyword` path.

Tests must prove:

```text
text + keyword subfield -> exact keyword path
analyzed-only text      -> analyzed remote field
```

## 5.5 Enforcement propagation

For analyzed text in `UNSAFE`:

```text
MatchPhrase / MatchPhrasePrefix / analyzed Regexp
        -> APPROXIMATE
```

No Trino residual is retained solely to preserve SQL text semantics when the translation is valid and `UNSAFE` explicitly accepts it.

For exact keyword/numeric/date/IP forms:

```text
existing EXACT behavior remains EXACT
```

Do not weaken exact array predicates merely because `full_text_pushdown_mode=UNSAFE` is enabled.

## 5.6 SAFE and DISABLED are frozen in this work package

This phase is intentionally UNSAFE-first.

Unless required to fix an existing correctness bug discovered by tests:

```text
DISABLED behavior = unchanged
SAFE behavior     = unchanged
```

In particular, analyzed array membership/LIKE/regexp should not gain new SAFE prefilters as a side effect of this work.

Any proposed SAFE extension must be separated into another change with a no-false-negative proof.

## 5.7 Dynamic filtering is unchanged

No approximate dynamic filtering may be introduced.

`ElasticsearchDynamicFilterPlanner` must remain exact-only because a dynamic filter with false negatives can permanently discard join rows before the engine has a chance to re-check them.

This phase should ideally not modify dynamic-filter production code at all.

---

# 6. Work packages and completion gates

No gate may be marked complete merely because code compiles. Each gate has an explicit semantic acceptance condition.

---

## A0 — Baseline freeze and reproducibility

### Goal

Prove the implementation starts from a known working baseline and record the current unsupported analyzed-array behavior before changing production code.

### Tasks

- [ ] Record implementation branch and exact base SHA.
- [ ] Confirm worktree/branch starts from the intended current `master` or an explicitly reviewed successor to the baseline in this document.
- [ ] Run focused existing full-text and array tests before any production change.
- [ ] Confirm existing scalar UNSAFE tests are green.
- [ ] Confirm existing exact array tests are green.
- [ ] Confirm analyzed-text array equality/membership currently remains residual.
- [ ] Save command outputs or CI links in the implementation PR description.

### Baseline cases that must remain observable

Existing tests already cover forms such as:

```sql
contains(text_tags, 'telegram')
any_match(text_tags, x -> x = 'telegram')
```

and currently expect a remaining `FilterNode` for analyzed-text-only arrays.

### Commands

Run the narrowest available concrete ES7/ES8 test classes or Maven selectors that exercise the base classes. At minimum:

```bash
./mvnw -pl :trino-elasticsearch \
  -Dtest=BaseElasticsearchFullTextPushdownTest test

./mvnw -pl :trino-elasticsearch \
  -Dtest=BaseElasticsearchAnyMatchPushdownTest test

./mvnw -pl :trino-elasticsearch \
  -Dtest=BaseElasticsearchP0PredicatePushdownTest test
```

If the repository's test hierarchy requires running concrete ES7/ES8 subclasses rather than abstract base classes, use the concrete suites and document the exact commands.

### A0 completion gate

A0 is complete only when:

```text
scalar UNSAFE baseline green
exact array baseline green
analyzed array currently residual confirmed
base SHA recorded
```

### Stop condition

If baseline tests fail before production changes, stop feature implementation and classify the baseline failure first. Do not fix unrelated failures inside this feature without documenting why they block the work.

---

## A1 — Make array translation enforcement-aware

### Goal

Remove the architectural assumption that every array translation is exact.

### Production work

Expected primary files:

```text
src/main/java/io/trino/plugin/elasticsearch/
    ElasticsearchArrayPredicateTranslator.java
    ElasticsearchPredicatePushdownPlanner.java
```

Potential supporting file only if required by the chosen permanent API:

```text
ElasticsearchPredicateTranslation.java
```

Do not change its semantic invariants merely to make implementation easier.

### Required changes

- [ ] Change array translation to return or produce `ElasticsearchPredicateTranslation<ConnectorExpression>` semantics.
- [ ] Pass `FullTextPushdownMode` to the array translation policy where necessary.
- [ ] Remove planner code that unconditionally wraps returned array predicates with `exact(...)`.
- [ ] Preserve diagnostics decisions through the same permanent translation contract.
- [ ] Preserve current unsupported-expression ownership: unsupported planner-owned expressions must not leak into a legacy path that can bypass the decision.

### No feature expansion in A1

A1 is a behavior-preserving architectural change.

The following must still be true immediately after A1:

```text
exact array predicates      -> EXACT and pushed
analyzed array predicates   -> still residual/unsupported
SAFE behavior               -> unchanged
DISABLED behavior           -> unchanged
```

### Required focused tests

Add unit/planner coverage proving:

- [ ] existing exact array translation reports `EXACT`;
- [ ] unsupported analyzed array translation does not produce remote IR yet;
- [ ] array planner result is not automatically strengthened by the planner;
- [ ] `ElasticsearchPredicateTranslation` decision/enforcement is preserved;
- [ ] repeated `applyFilter` composition still preserves inherited remote predicates.

### A1 completion gate

A1 is complete only when:

```text
new enforcement-aware contract exists
no new array operator is enabled
all previous array/full-text behavior remains unchanged
focused planner tests green
AirStyle green
```

Suggested commands:

```bash
./mvnw -pl :trino-elasticsearch -Dtest=<focused-planner-tests> test
./mvnw -pl :trino-elasticsearch airstyle:check
```

---

## A2 — Revalidate and migrate all existing exact array paths

### Goal

Prove the architecture change did not regress P0.3/P1.1 exact behavior.

### Existing forms that must remain exact

- [ ] `contains(keyword_array, constant)`.
- [ ] `contains(numeric_array, constant)`.
- [ ] `arrays_overlap(keyword_array, constant_array)`.
- [ ] `arrays_overlap(numeric_array, constant_array)`.
- [ ] safe `text.keyword` membership.
- [ ] `any_match(... = constant)`.
- [ ] `any_match(... IN (...))`.
- [ ] numeric/timestamp `<`, `<=`, `>`, `>=`.
- [ ] exact lambda `OR` where all branches are valid.
- [ ] fused same-element range `AND`.
- [ ] IP membership where already supported by the existing array translator.

### Regression data

Keep coverage for:

- [ ] missing array field;
- [ ] empty array;
- [ ] source array containing NULL plus a matching value;
- [ ] source array containing only NULL;
- [ ] duplicate constants;
- [ ] NULL inside a constant array causing the current residual behavior.

### A2 completion gate

A2 is complete only when all existing exact array tests pass without changing expected exactness.

The intended invariant is:

```text
A1/A2 changes architecture, not semantics.
```

---

## A3 — UNSAFE analyzed equality and membership

### Goal

Enable the highest-value positive existential analyzed-text predicates.

### Target forms

```sql
contains(names, 'Nguyen Van')

arrays_overlap(names, ARRAY['Nguyen Van', 'Tran Thi'])

any_match(names, x -> x = 'Nguyen Van')

any_match(names, x -> x IN ('Nguyen Van', 'Tran Thi'))
```

where `names` maps to analyzed `text` without a safe keyword subfield.

### Target IR

Single value:

```text
MatchPhrase(names, "Nguyen Van")
Enforcement = APPROXIMATE
```

Multiple values:

```text
Or(
    MatchPhrase(names, "Nguyen Van"),
    MatchPhrase(names, "Tran Thi"))
Enforcement = APPROXIMATE
```

### Design requirements

- [x] Reuse the same analyzed-text value semantics as scalar UNSAFE discrete domains.
- [x] Do not use `Term`/`Terms` against analyzed text merely because the source SQL is equality/IN.
- [x] Keep exact keyword/text.keyword arrays on the existing exact path.
- [x] Do not retain a SQL residual for a valid analyzed translation in `UNSAFE`.
- [x] Keep unsupported/invalid constants residual.
- [x] Preserve NULL handling of current array operators.

### Resource-safety requirement

Large analyzed `IN`/`arrays_overlap` can produce many `MatchPhrase` OR clauses, unlike exact `Terms` which compacts to one native terms query.

Before declaring A3 complete, audit the existing normalizer/request limits for non-`Terms` disjunctions.

If no shared bound exists, implement a shared bounded policy appropriate for both scalar analyzed discrete sets and array analyzed discrete sets. Do **not** introduce an arbitrary array-only magic constant.

The planner must prefer:

```text
unsupported/residual
```

over generating an Elasticsearch request likely to exceed clause/request limits.

### A3 focused tests

UNSAFE:

- [x] `contains(analyzed_array, single_token)` fully pushed.
- [x] `contains(analyzed_array, multi_token)` fully pushed.
- [x] `arrays_overlap(analyzed_array, two_values)` fully pushed.
- [x] `any_match(analyzed_array, x -> x = C)` fully pushed.
- [x] `any_match(analyzed_array, x -> x IN (...))` fully pushed.
- [x] IR enforcement is `APPROXIMATE`, never `EXACT`.
- [x] no residual `FilterNode` remains for accepted UNSAFE translations.

Control modes:

- [x] same queries under `DISABLED` behave exactly as before.
- [x] same queries under `SAFE` behave exactly as before.

Exact-field regression:

- [x] keyword arrays remain `EXACT` even when session mode is `UNSAFE`.
- [x] text-with-keyword arrays continue to target the keyword subfield and remain exact.

### A3 completion gate

A3 is complete only when equality/membership parity is demonstrated against both ES7 and ES8 integration suites and no unbounded analyzed-disjunction issue remains.

---

## A4 — UNSAFE LIKE and prefix parity inside `any_match`

### Goal

Support scalar-parity analyzed LIKE/prefix behavior when the string predicate is applied to an array element.

### Target SQL

Literal:

```sql
any_match(names, x -> x LIKE 'Nguyen Van')
```

Contains literal:

```sql
any_match(names, x -> x LIKE '%Nguyen Van%')
```

Prefix:

```sql
any_match(names, x -> x LIKE 'Nguyen Van%')
```

Function form:

```sql
any_match(names, x -> starts_with(x, 'Nguyen Van'))
```

### Expected analyzed-text strategy

Reuse scalar behavior as of the implementation base:

```text
literal / supported contains literal -> MatchPhrase
supported prefix                     -> MatchPhrasePrefix
other scalar-supported LIKE shape    -> same scalar remote strategy
unsupported LIKE shape               -> residual
```

### Architecture requirement

`RewriteAnalyzedTextLike` currently contains analyzed LIKE shape recognition. The agent must not copy its pattern parser into the array translator.

Preferred approach:

- extract a package-level/common helper with no scalar-only assumptions; or
- refactor the existing expression rule so array-element translation can invoke the same semantic conversion.

The helper must remain a translation helper, not a second remote predicate representation.

### Required tests

- [ ] analyzed array literal LIKE fully pushed in UNSAFE.
- [ ] analyzed array `%literal%` fully pushed in UNSAFE where scalar supports it.
- [ ] multi-token `%alpha beta%` follows scalar phrase behavior.
- [ ] analyzed array prefix LIKE uses `MatchPhrasePrefix`.
- [ ] `starts_with` analyzed array follows the same prefix strategy.
- [ ] escaped LIKE patterns retain scalar behavior.
- [ ] internal wildcard shapes unsupported by scalar remain unsupported by array.
- [ ] exact keyword array LIKE/prefix behavior, if supported by the chosen scalar-value helper, remains exact.
- [ ] SAFE/DISABLED unchanged.

### A4 completion gate

A4 is complete only when array LIKE behavior is demonstrably driven by the same semantic conversion as scalar UNSAFE rather than a duplicate implementation.

---

## A5 — UNSAFE `regexp_like` parity inside `any_match`

### Goal

Reuse existing regexp translation/classification for an analyzed array element.

### Target SQL

```sql
any_match(names, x -> regexp_like(x, 'nguyen.*van'))
```

### Requirements

- [ ] Use the existing SQL/Joni-to-Elasticsearch regexp translator/classifier.
- [ ] Do not send raw SQL regexp syntax directly to Lucene.
- [ ] A valid approximate translation becomes `Regexp(..., APPROXIMATE)` in `UNSAFE`.
- [ ] Unsupported syntax remains residual even in `UNSAFE`.
- [ ] Preserve scalar semantics for patterns already covered by `BaseElasticsearchFullTextPushdownTest`.

### Required regression patterns

Include at least:

```text
simple literal/character pattern
alternation
non-capturing group if current scalar translator accepts it as approximate
unsupported lookahead/lookbehind form from current scalar regression coverage
Unicode input
```

### A5 completion gate

A5 is complete only when array regexp acceptance/rejection matches the scalar translator on the same pattern set.

---

## A6 — UNSAFE OR composition within `any_match`

### Goal

Allow same-element existential OR when every branch has a valid remote translation.

### Example

```sql
any_match(
    names,
    x -> x = 'Nguyen Van'
      OR x LIKE 'Tran%'
      OR regexp_like(x, 'Le.*Anh'))
```

Target conceptual IR:

```text
Or(
    MatchPhrase(...),
    MatchPhrasePrefix(...),
    Regexp(...))
```

with effective enforcement:

```text
APPROXIMATE
```

### Correctness rule

For:

```text
P OR Q
```

array lowering is allowed only if every OR branch is representable under the selected mode.

If one branch is unsupported:

```text
P translatable
Q unsupported
```

then pushing remote `P` alone is invalid because rows satisfying only `Q` would be lost.

Required outcome:

```text
whole OR subtree remains residual/unsupported
```

unless the permanent composer can prove a no-false-negative candidate for every branch. This UNSAFE work package does not add such partial-OR behavior.

### A6 tests

- [ ] analyzed EQ OR analyzed EQ fully pushed.
- [ ] analyzed EQ OR prefix LIKE fully pushed.
- [ ] analyzed LIKE OR regexp fully pushed.
- [ ] exact branch OR approximate branch produces correct effective approximate enforcement where allowed.
- [ ] translatable OR unsupported branch remains in Trino.
- [ ] nested OR normalization preserves semantics.
- [ ] no branch is silently dropped.

### A6 completion gate

A6 is complete only when partial-OR rejection has explicit tests and effective enforcement remains visible in IR/diagnostics.

---

## A7 — Same-element safety hardening

### Goal

Prove the feature did not accidentally turn lambda-local boolean logic into document-scope Elasticsearch logic.

This is a mandatory semantic gate, not optional hardening.

### Cross-element trap fixture

Create a document similar to:

```json
{
  "names": [
    "Nguyen Anh",
    "Le Van"
  ]
}
```

Then test:

```sql
any_match(
    names,
    x -> x LIKE 'Nguyen%'
      AND x LIKE '%Van%')
```

No single logical source element satisfies both clauses.

The translator must **not** emit independent document-scope clauses such as:

```text
MatchPhrasePrefix(names, "Nguyen")
AND
MatchPhrase(names, "Van")
```

because Elasticsearch could satisfy them from different array values.

### Allowed AND forms

Keep the existing class of same-element-safe fused predicates, including bounded numeric/timestamp ranges where both conditions become one `Range` IR node.

### Additional safety regressions

- [ ] two analyzed equality predicates in the same lambda AND remain residual unless one remote predicate can represent the same-element condition.
- [ ] analyzed EQ AND LIKE remains residual.
- [ ] analyzed LIKE AND regexp remains residual.
- [ ] top-level `contains(a,'x') AND contains(a,'y')` is still allowed to compose at document scope; do not confuse this with lambda-local AND.
- [ ] existing exact bounded range lambda remains fully pushed.

### A7 completion gate

A7 is complete only when cross-element false-positive traps are present and the planner deliberately rejects unsafe same-element decomposition.

---

## A8 — Diagnostics, statistics, and execution-contract validation

### Goal

Ensure the new behavior participates correctly in existing permanent diagnostics and downstream planning contracts.

### Diagnostics requirements

P1.3 already established the diagnostics model. This feature must emit through that model rather than add log-only instrumentation.

The minimum observable semantics must distinguish:

```text
array / any_match category
APPROXIMATE enforcement
remote predicate present
residual absent for accepted UNSAFE translation
unsupported/residual outcome when translation is rejected
```

Avoid proliferating one `Reason` enum value for every Cartesian combination such as:

```text
UNSAFE_ARRAY_EQUAL
UNSAFE_ARRAY_LIKE
UNSAFE_ARRAY_REGEXP
...
```

If the current diagnostics model needs enhancement, prefer stable dimensions such as:

```text
scope       = ARRAY_ELEMENT
operator    = EQUALITY | IN | LIKE | PREFIX | REGEXP
strategy    = MATCH_PHRASE | MATCH_PHRASE_PREFIX | REGEXP
enforcement = APPROXIMATE
```

Any diagnostics refactor must remain compatible with the permanent P1.3 event/accounting architecture.

### Statistics behavior

`RuleBasedElasticsearchMetadata` already treats non-exact remote predicates conservatively for statistics.

Add/retain coverage proving an approximate array predicate does not cause connector statistics to be reported as if they described final post-SQL rows.

Expected principle:

```text
remote approximate row count != exact final SQL row count
```

so conservative/unknown filtered statistics remain correct.

### Execution behavior

Validate that remote approximate predicates survive:

- [ ] table-handle serialization/round trip where applicable;
- [ ] legal limit rewrites;
- [ ] legal aggregation handle rewrites under the current connector contract;
- [ ] scan query rendering;
- [ ] ES7 and ES8 request rendering.

Do not expand limit/aggregation semantics as part of this feature.

### Dynamic-filter guard

Add a focused regression only if needed to prove no code path allows analyzed/approximate array predicates to become remote dynamic filters.

### A8 completion gate

A8 is complete only when the new approximate array path is visible through existing diagnostics and remains conservative in statistics/resource/planning consumers.

---

## A9 — Full acceptance, CI, roadmap evidence, and release gate

### Goal

Prove the exact implementation candidate is complete rather than inferring completion from focused tests.

### Focused test gate

Run all affected focused suites. Expected classes include existing tests such as:

```text
BaseElasticsearchAnyMatchPushdownTest
BaseElasticsearchP0PredicatePushdownTest
BaseElasticsearchFullTextPushdownTest
BaseElasticsearchPredicateCompositionTest
```

Prefer adding UNSAFE array cases to the existing inheritance chain so both ES7 and ES8 concrete suites execute them automatically.

If a new base class such as:

```text
BaseElasticsearchUnsafeArrayPushdownTest
```

is introduced, verify both ES7 and ES8 concrete connector test hierarchies actually inherit/execute it. A standalone abstract base class that Maven never executes is not acceptance coverage.

### Formatting/style gate

```bash
./mvnw -pl :trino-elasticsearch airstyle:check
```

Use `airstyle:format` only intentionally and review the resulting diff before committing.

### Full connector module gate

At minimum:

```bash
./mvnw -pl :trino-elasticsearch test
```

For release-quality validation use the repository's established stronger connector verification, for example the current Error Prone profile where applicable:

```bash
./mvnw -nsu -pl :trino-elasticsearch -Perrorprone-compiler clean verify
```

Record:

```text
total tests
failures
errors
skips/capability skips
ES7 result
ES8 result
packaging result
exact tested SHA
```

### Git diff gate

Before PR finalization:

```bash
git diff --check
```

Review that production changes are limited to the intended architecture/operator scope.

### GitHub CI gate

The final implementation SHA must have green GitHub CI.

Do not claim completion because:

- a previous SHA was green;
- test-results upload succeeded;
- only one ES version passed;
- formatting passed but module tests did not;
- a retry passed on a different commit.

The CI evidence must correspond to the exact candidate SHA.

### Documentation gate

Update `ROADMAP-PUSHDOWN.md` only after implementation semantics and tests are final.

The roadmap update must state:

- exact implemented capability;
- explicit `UNSAFE` requirement;
- `APPROXIMATE` enforcement;
- same-element AND restriction;
- SAFE/DISABLED unchanged;
- dynamic filters remain exact-only;
- PR/commit/CI evidence.

### A9 completion gate

The work package may be marked `COMPLETE` only when all prior gates are green and the exact implementation SHA passes full local/module validation plus GitHub CI.

---

# 7. Detailed integration test fixture

A dedicated fixture should expose the important mapping distinctions in one index.

Suggested mapping shape:

```json
{
  "settings": {
    "analysis": {
      "analyzer": {
        "folded_text": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "asciifolding"]
        }
      }
    }
  },
  "mappings": {
    "_meta": {
      "trino": {
        "keyword_tags": { "isArray": true },
        "text_tags": { "isArray": true },
        "folded_names": { "isArray": true },
        "text_with_keyword": { "isArray": true },
        "numbers": { "isArray": true }
      }
    },
    "properties": {
      "id": { "type": "keyword" },
      "keyword_tags": { "type": "keyword" },
      "text_tags": { "type": "text" },
      "folded_names": { "type": "text", "analyzer": "folded_text" },
      "text_with_keyword": {
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "numbers": { "type": "integer" }
    }
  }
}
```

Suggested documents should separately cover:

```text
D1 normal multi-valued analyzed array
D2 alternate values/non-match
D3 empty arrays
D4 missing array fields
D5 source array containing NULL plus real value
D6 source array containing only NULL
D7 cross-element same-element trap
D8 Unicode/custom analyzer values
D9 duplicate array values where relevant
```

Example semantic data:

```json
{"id":"1","text_tags":["telegram","social network"],"folded_names":["NGÔ VĂN","TRẦN ANH"]}
{"id":"2","text_tags":["facebook"],"folded_names":["LÊ MINH"]}
{"id":"3","text_tags":[],"folded_names":[]}
{"id":"4"}
{"id":"7","text_tags":["Nguyen Anh","Le Van"]}
```

For NULL-containing arrays, use mutable Java lists in tests because immutable collection helpers commonly reject NULL values.

---

# 8. Detailed test matrix

Every capability below should have both result and pushdown-shape assertions where the test framework allows it.

## 8.1 Mode-control matrix

For each new analyzed-array operator:

| Mode | Expected |
| --- | --- |
| `DISABLED` | existing residual/unsupported behavior |
| `SAFE` | unchanged by this work package |
| `UNSAFE` | valid translation fully pushed with `APPROXIMATE` enforcement |

At least one test per operator family must explicitly compare all three modes.

## 8.2 Equality/membership matrix

Test:

```sql
contains(text_tags, 'telegram')
contains(text_tags, 'social network')
arrays_overlap(text_tags, ARRAY['telegram','missing'])
any_match(text_tags, x -> x = 'telegram')
any_match(text_tags, x -> x = 'social network')
any_match(text_tags, x -> x IN ('telegram','facebook'))
```

Verify:

- UNSAFE fully pushed;
- multi-token values use analyzed semantics;
- exact keyword controls remain case-sensitive/exact;
- text-with-keyword controls remain exact and use `.keyword`.

## 8.3 Analyzer matrix

Using lowercase + asciifolding analyzer:

```sql
any_match(folded_names, x -> x = 'ngô văn')
any_match(folded_names, x -> x LIKE '%ngô%')
any_match(folded_names, x -> x LIKE '%ngô văn%')
any_match(folded_names, x -> x LIKE 'ngô văn%')
```

UNSAFE acceptance should intentionally demonstrate Elasticsearch analyzer semantics rather than SQL source-string equality.

Use `skipResultsCorrectnessCheckForPushdown()` only where the test intentionally validates approximate semantics that differ from the reference engine. Prefer explicit expected rows against the Elasticsearch-backed source wherever possible.

## 8.4 LIKE matrix

Cover:

```text
literal
%literal%
multi-token literal
prefix%
escaped %
escaped _
internal wildcard shape supported by scalar
internal wildcard shape unsupported by scalar
Unicode literal/prefix
```

Array support must never exceed scalar translator support by accidentally using a different parser.

## 8.5 Regexp matrix

Cover the same translator categories as scalar:

```text
accepted straightforward pattern
accepted approximate pattern
unsupported pattern
Unicode pattern/value
```

Assertions must prove unsupported syntax stays residual even under `UNSAFE`.

## 8.6 Boolean matrix

Supported OR:

```sql
any_match(a, x -> x = 'foo' OR x = 'bar')
any_match(a, x -> x = 'foo' OR x LIKE 'bar%')
any_match(a, x -> x LIKE '%foo%' OR regexp_like(x, 'bar.*'))
```

Partial OR rejection:

```sql
any_match(a, x -> supported_predicate(x) OR unsupported_predicate(x))
```

Same-element AND rejection:

```sql
any_match(a, x -> x LIKE 'Nguyen%' AND x LIKE '%Van%')
```

Control exact AND:

```sql
any_match(numbers, x -> x > 10 AND x < 20)
```

must remain exact and pushed.

## 8.7 NULL/missing/empty matrix

Retain regression coverage for:

```text
missing field
null source field if fixture supports it
empty array
array [null]
array [null, matchingValue]
constant array containing NULL
```

Do not invent exact NULL-element semantics using Elasticsearch `exists` in this phase.

## 8.8 Operator-identity negative tests

Explicitly document/test that these are not translated as membership:

```sql
array_col = ARRAY['foo']
```

and, where planner/type testing is appropriate:

```sql
array_col = 'foo'
```

The latter should fail type analysis before connector pushdown; the feature must not add a coercion.

## 8.9 Resource-budget tests

If A3 introduces or reuses a clause/value/request bound for analyzed disjunctions, add tests at:

```text
below threshold
at threshold
above threshold
```

Above-threshold behavior should fail closed to residual/unsupported pushdown rather than generate a dangerous remote request.

## 8.10 Diagnostics tests

Verify at least:

```text
array approximate translation count/decision
any_match approximate translation count/decision
APPROXIMATE enforcement
remote present
residual absent under accepted UNSAFE
unsupported/residual decision under rejected translation
```

Do not assert diagnostics by parsing free-form log strings when structured counters/decisions are available.

---

# 9. Performance and resource constraints

UNSAFE changes correctness policy, not resource-safety policy.

The following remain mandatory:

- bounded request construction;
- no unbounded bool-clause explosion;
- no arbitrary script queries;
- no approximate dynamic filters;
- no silent fallback to remote query shapes known to be pathological merely because the user selected `UNSAFE`.

Particular attention is required for:

```text
arrays_overlap(analyzed_array, large_constant_array)
any_match(analyzed_array, x -> x IN (large_set))
large OR trees of MatchPhrase nodes
regexp complexity
leading/internal wildcard behavior
```

Exact arrays benefit from native `Terms`; analyzed arrays cannot generally compact multiple phrases into `Terms` without changing analyzer semantics.

If the scalar UNSAFE path already exposes an unbounded analyzed discrete-set problem, this work should either:

1. generalize the fix to scalar and array through a common permanent bound, or
2. stop A3 and split the resource-bound prerequisite into a reviewed first change.

Do not knowingly ship an array-only implementation that introduces a larger resource-risk surface than scalar UNSAFE.

---

# 10. Expected production files

Primary expected changes:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/
    ElasticsearchArrayPredicateTranslator.java
    ElasticsearchPredicatePushdownPlanner.java
```

Likely supporting changes, depending on the cleanest common abstraction:

```text
ElasticsearchPredicateTranslation.java
ElasticsearchRemotePredicateTranslator.java
ElasticsearchPushdownDiagnostics.java
```

Expression/full-text helpers may require refactoring under:

```text
plugin/trino-elasticsearch/src/main/java/io/trino/plugin/elasticsearch/expression/
    ElasticsearchExpressionTranslator.java
    RewriteAnalyzedTextLike.java
    <new common helper only if needed>
```

A new helper is acceptable only if it is a permanent scalar+array semantic translator/helper. Do not introduce an array-specific duplicate LIKE/regexp implementation.

Production files that should normally remain untouched by this feature unless a discovered correctness prerequisite is documented:

```text
ElasticsearchDynamicFilterPlanner.java
search execution lifecycle/page sources
aggregation implementation
decoders
```

---

# 11. Expected test files

Existing acceptance files likely affected:

```text
BaseElasticsearchAnyMatchPushdownTest.java
BaseElasticsearchP0PredicatePushdownTest.java
BaseElasticsearchFullTextPushdownTest.java
BaseElasticsearchPredicateCompositionTest.java
```

A new dedicated shared acceptance class is optional:

```text
BaseElasticsearchUnsafeArrayPushdownTest.java
```

Use it only if it is wired into both ES7 and ES8 concrete connector suites.

Focused unit/planner test classes should be added or extended for:

```text
array translator enforcement
predicate planner decisions
IR enforcement
same-element composition
resource-budget rejection
```

Use existing test naming/layout conventions. Do not create redundant suites that duplicate connector-wide fixture ownership.

---

# 12. Implementation branch and commit strategy

Recommended implementation branch:

```text
feature/elasticsearch-unsafe-array-full-text
```

Create it from the latest reviewed `master` after this plan is merged, or explicitly record another base SHA if implementation starts before the documentation branch is merged.

Recommended logical commits:

```text
1. refactor(elasticsearch): make array predicate translation enforcement-aware
2. feat(elasticsearch): push analyzed array equality and membership in unsafe mode
3. feat(elasticsearch): add unsafe array like prefix and regexp parity
4. test(elasticsearch): harden unsafe array semantics and same-element gates
5. docs(elasticsearch): record unsafe primitive-array completion evidence
```

The final history may be squashed/reorganized if repository contribution policy requires it, but each retained commit should be buildable/reviewable where practical.

Do not mix into this branch:

```text
SAFE analyzed-array prefilters
negative array predicates
all_match/none_match
nested-object array support
PIT/scroll execution changes
aggregation redesign
dynamic-filter approximation
```

---

# 13. Review checklist

The reviewer must explicitly answer all of the following before approval.

## Architecture

- [ ] Does every new remote predicate use `ElasticsearchRemotePredicate` IR?
- [ ] Does array translation use `ElasticsearchPredicateTranslation` semantics?
- [ ] Is any approximate translation accidentally strengthened to exact?
- [ ] Is scalar LIKE/regexp logic reused rather than copied?
- [ ] Is planner-owned residual state protected from legacy fallback?
- [ ] Is the same-element boundary still owned/proven by the array translator?

## Semantics

- [ ] Does `UNSAFE` only broaden analyzed search semantics, not operator identity?
- [ ] Are whole-array equality/cardinality/order still engine-only?
- [ ] Are partial OR branches rejected?
- [ ] Are generic same-element analyzed AND predicates rejected?
- [ ] Are exact keyword/numeric/date/IP paths unchanged?
- [ ] Are SAFE and DISABLED unchanged?
- [ ] Are dynamic filters still exact-only?

## Elasticsearch DSL

- [ ] Analyzed equality uses `MatchPhrase`, not `Term`.
- [ ] Analyzed set membership preserves analyzer semantics.
- [ ] Prefix uses the same scalar strategy.
- [ ] Regexp uses the existing regexp converter/classifier.
- [ ] text-with-keyword exact predicates use the keyword subfield.
- [ ] request/clause growth is bounded.

## Tests

- [ ] ES7 coverage present.
- [ ] ES8 coverage present.
- [ ] custom analyzer coverage present.
- [ ] Unicode coverage present.
- [ ] NULL/empty/missing regression retained.
- [ ] cross-element AND trap present.
- [ ] partial OR rejection present.
- [ ] unsupported regexp remains residual.
- [ ] mode-control tests cover DISABLED/SAFE/UNSAFE.
- [ ] exact tested SHA is recorded.

---

# 14. Definition of Done

This work package is complete only when all statements below are true.

## Functional capability

- [ ] `contains` on analyzed primitive arrays is pushed in UNSAFE where translation is valid.
- [ ] `arrays_overlap` on analyzed primitive arrays is pushed in UNSAFE where translation is valid and resource bounds permit it.
- [ ] `any_match` analyzed equality is pushed in UNSAFE.
- [ ] `any_match` analyzed IN is pushed in UNSAFE.
- [ ] `any_match` scalar-supported analyzed LIKE shapes are pushed in UNSAFE.
- [ ] analyzed prefix/`starts_with` parity is implemented for the accepted scope.
- [ ] `regexp_like` parity is implemented for patterns accepted by the existing scalar regexp translator.
- [ ] fully translatable OR composition works.

## Enforcement correctness

- [ ] analyzed array predicates are marked `APPROXIMATE`.
- [ ] accepted UNSAFE analyzed array predicates have no Trino residual solely for SQL string equivalence.
- [ ] exact array predicates remain `EXACT`.
- [ ] rejected translations remain residual/unsupported.

## Semantic safety

- [ ] generic same-element analyzed AND is not decomposed into document-scope AND.
- [ ] partial OR is not pushed.
- [ ] whole-array equality is not reinterpreted as membership.
- [ ] `ARRAY<T> = T` is not introduced as implicit membership syntax.
- [ ] NULL/missing/empty behavior is not silently redefined.
- [ ] dynamic filtering remains exact-only.

## Resource safety

- [ ] large analyzed membership/IN cannot create an unbounded remote clause tree.
- [ ] unsupported/excessive translations fall back without losing correctness outside the explicitly accepted UNSAFE semantic differences.

## Test/build quality

- [ ] focused unit/planner tests pass.
- [ ] array acceptance tests pass.
- [ ] scalar full-text regression tests pass.
- [ ] predicate-composition regression tests pass.
- [ ] ES7 connector suite passes.
- [ ] ES8 connector suite passes.
- [ ] AirStyle passes.
- [ ] full `:trino-elasticsearch` module passes.
- [ ] Error Prone/clean verify passes when used by the release gate.
- [ ] GitHub CI is green on the exact final candidate SHA.

## Documentation/evidence

- [ ] roadmap updated only after the implementation is stable.
- [ ] final PR records base SHA, head SHA, commands, test counts, CI run, and known remaining unsupported forms.
- [ ] this plan is updated with completion evidence or superseded by a final implementation audit document.

---

# 15. Explicit out-of-scope follow-up backlog

Do not expand the current PR to implement these. Record them as future work after the positive existential UNSAFE surface is green.

## Negative element predicates

```text
<>
NOT IN
NOT LIKE
NOT contains
NOT arrays_overlap
```

These require explicit missing/NULL/three-valued-logic analysis before deciding whether any UNSAFE form should become authoritative.

## Other array quantifiers

```text
all_match
none_match
```

These are not simple existential queries and require separate empty-array, NULL-array, NULL-element, and negation semantics.

## Shape-sensitive operators

```text
whole-array equality
cardinality
positional access
array_position
contains_sequence
first/last
nested arrays
```

Generic Elasticsearch multi-value indexing does not preserve enough source-array shape/order information to treat these as ordinary native pushdown candidates.

## Nested/object arrays

Arrays of `object`/`nested` values require a separate design because ordinary object arrays flatten field associations while Elasticsearch `nested` mappings have their own query scope.

---

# 16. Agent execution contract

The implementation agent should execute this plan in gate order.

Do not skip directly to feature code.

Required workflow:

```text
A0 baseline evidence
  -> A1 enforcement-aware architecture
  -> A2 exact regression proof
  -> A3 analyzed equality/membership
  -> A4 LIKE/prefix parity
  -> A5 regexp parity
  -> A6 OR composition
  -> A7 same-element hardening
  -> A8 diagnostics/statistics integration
  -> A9 full module + CI + docs evidence
```

At each gate the agent must:

1. state the exact HEAD;
2. list production files changed;
3. list tests added/changed;
4. state semantic impact;
5. run the focused test gate;
6. stop and investigate any regression before moving forward;
7. commit only after the gate is internally coherent.

The agent must not claim a gate is complete based only on code inspection.

The agent must not rewrite SAFE/DISABLED semantics to simplify UNSAFE implementation.

The agent must not use Elasticsearch scripts as a shortcut.

The agent must not introduce approximate dynamic filters.

The agent must not flatten same-element lambda AND into document-scope AND.

The agent must prefer residual/unsupported behavior over a remote translation whose operator/scope semantics are not understood.

---

# 17. Required final implementation report template

When implementation is complete, report using this structure:

```text
# UNSAFE Primitive Array Pushdown Completion Report

## Repository
Base SHA:
Final SHA:
Branch:
PR:
CI run:

## Production files changed
- ...

## Test files changed
- ...

## Capability delivered
- contains analyzed array:
- arrays_overlap analyzed array:
- any_match equality:
- any_match IN:
- LIKE literal:
- LIKE contains:
- LIKE prefix:
- starts_with:
- regexp_like:
- OR composition:

## Enforcement
Exact paths unchanged:
Approximate paths:
Residual behavior:

## Same-element safety
Cross-element regression:
Generic analyzed AND:
Fused exact range AND:

## Mode compatibility
DISABLED:
SAFE:
UNSAFE:

## Dynamic filtering
Exact-only invariant:

## Resource safety
Analyzed OR/value bound:
Oversize fallback behavior:

## Tests
Focused tests:
AirStyle:
Full module:
Error Prone clean verify:
ES7:
ES8:
Total tests:
Failures:
Errors:
Skips:

## CI
Exact final SHA green:
Run URL/ID:

## Remaining unsupported forms
- negative predicates
- all_match/none_match
- whole-array equality
- cardinality/order/position
- nested/object arrays
- ...

## Final status
COMPLETE / BLOCKED
```

---

# 18. Success criterion

The practical success criterion is not "every Trino ARRAY function is pushed".

It is:

> In explicit `UNSAFE` mode, primitive analyzed-text arrays support the same useful positive element-search operators as scalar analyzed text wherever Elasticsearch can build a valid remote query, while exact array behavior, same-element logical scope, dynamic-filter correctness, and resource safety remain intact.

When this criterion and every A0-A9 gate are satisfied, the positive existential UNSAFE primitive-array surface can be considered production-ready within the semantics explicitly accepted by `UNSAFE`.
