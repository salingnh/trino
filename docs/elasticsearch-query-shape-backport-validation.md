# Elasticsearch query-shape backport validation

Branch: `feat/elasticsearch-query-shape-backport`.
Base: `origin/internal/production` at `cb0fc90dc9014ed2e1a84a6b28b94ec5b8e46069`.
The base was fetched again after implementation; no additional base commits were available.

## Implemented scope

1. Mapping capability propagated through fields, ancestors, column handles and projections; all backing indices must agree. Query-shape eligibility is separate from scalar predicate eligibility.
2. Floating-point TopN and grouping fall back to Trino for signed-zero semantics. Existing LIMIT boundaries, same-sort narrowing and global merge guarantees remain. Scroll/PIT searches retain the logical endpoint and restrict each concrete split through `_index`.
3. Generated aggregate names reserve assignment names, physical column names, grouping names and SUM helper names.
4. Aggregation responses require typed completion metadata, reject timeout/shard failures and require exact nonnegative global hit counts. Requests disable partial search results. Custom close/failure/diagnostics and pagination progress guards remain.
5. Default ES date grouping decodes epoch milliseconds into Trino timestamp microseconds. Composite after-keys remain JSON values without string conversion.
6. `elasticsearch.aggregation-page-size` defaults to 10,000, validates a minimum of 1, and controls requests and pagination termination.
7. Integer AVG uses the existing aggregation executor and returns DOUBLE; the existing aggregation switch controls it. Integral SUM and BIGINT MIN/MAX remain local.

## Local results

- The initial regression run reproduced three failures: floating-point query-shape eligibility, output-name collision and missing completion metadata. The other 25 tests passed.
- Final connector unit/HTTP run with Error Prone: **267 tests, 0 failures, 0 errors, 1 skipped opt-in benchmark**. AirStyle, Checkstyle, licenses, dependency analysis, duplicate dependencies/classes and Modernizer passed in the same run.
- Independent commit regression runs passed: mapping/TopN 256 tests, aggregation correctness 259, integer AVG 261, timestamp grouping 262 (each includes one skipped benchmark).
- Serialization tests cover both capability values. Projection tests cover unsafe ancestors, unsafe children and skipped unsupported mapping fields.
- HTTP tests cover Scroll/PIT logical endpoints plus concrete-index restrictions, integer AVG policy, timestamp decoding, page sizes 1/2/1,000/10,000, pagination progress and close/failure behavior.
- The AVG HTTP regression verifies `size:0`, one aggregation request, one result row, zero decoded source rows and zero decoded source bytes for an exact reported total of 50,000. This is a controlled response fixture, **not a measurement against a running Elasticsearch server**.

## Remaining release gates

- ES7/ES8 Scroll/PIT integration and the 50,000-document SQL fixture have been added, but require Docker. A local ES8 attempt failed during Testcontainers initialization: `Could not find a valid Docker environment`.
- Docker Desktop itself reports failure initializing its Inference manager listener. No Docker reset or data deletion was attempted.
- The existing opt-in benchmark now records global AVG request/row/output-byte/source-decode counters and grouped page counts, largest output page and thread allocation at page sizes 1,000 and 10,000. Actual ES measurements remain pending; no peak-RAM reduction is claimed.
- Full module integration, Sphinx documentation build and GitHub Actions on the final candidate remain pending. The Sphinx build uses Docker. A reactor documentation-check attempt stopped earlier at AirStyle on 40 unchanged `trino-testing-services` files; the connector itself passes AirStyle.
- Push dry-run to `salingnh/trino` failed with HTTP 403: active account `danieop` has READ permission. Push/PR/Actions require a writable account or an authorized fork.

## Commands on a Docker-capable host

```bash
./mvnw -B -pl :trino-elasticsearch -am -DskipTests -Dair.check.skip-all=true -Dmaven.javadoc.skip=true install
./mvnw -B -pl :trino-elasticsearch test
./mvnw -B -pl :trino-elasticsearch -P errorprone-compiler -DskipTests test
./mvnw -B -pl :trino-elasticsearch -Dtest=TestSearchExecutionBenchmark -Delasticsearch.benchmark=true -Delasticsearch.benchmark.documents=50000 -Delasticsearch.benchmark.slow-seconds=5 test
./mvnw -B -pl :trino-docs -am package -DskipTests
```

Benchmark allocation is measured on the calling thread; heap-pool peaks are upper bounds, not a simultaneous live-set measurement. A larger composite page can increase per-response memory even when it reduces requests. Keep the PR unmerged until the integration, documentation and final-SHA CI gates pass.
