# Remote Trino validation — 2026-11-04

Source revision: `cc55f51f82178143aa84fd497931df039a9acc78`.
Connector JAR SHA-256: `6edb354e407c92c347464d4541518d63b6c45b3c3b2f07be35ccd00ebdf95f5b`.
The hash on the Linux host matches the locally built connector JAR.

Deployment uses the source snapshot bind-mounted read-only into a Java 25 container,
with the source-built server runtime, Elasticsearch connector and JMX plugin.
The engine's displayed build version is `cb0fc90`; the connector/source revision
above identifies the backport under test. Elasticsearch versions are 7.17.27 and
8.11.3. Existing host services were left running.

## Results

- **202/202 live checks passed** against the actual Trino HTTP SQL API.
- **268 local unit/HTTP tests**, no failures/errors, one skipped opt-in benchmark,
  with Error Prone and standard connector checks; plugin packaging passed.
- Both ES versions were exercised through Scroll and PIT catalogs.
- The 50,000-document fixture has populated primary shards of **16,664 / 16,704 / 16,632** documents.
- SQL comparisons cover global/grouped AVG for integer and floating types,
  NULL/empty input, BIGINT near 2^53 and type boundaries, normalized/ignored/doc-value-disabled/null-value mapping fallback,
  output-name collision, timestamp/boolean/BIGINT/NULL grouping, integral SUM/BIGINT MIN/MAX fallback,
  DISTINCT fallback, approx_distinct, projection, signed zero, four sort/null orders,
  multi-key TopN, routed/filtered multi-index aliases, multi-page TopN and LIMIT boundaries.
- The proxy verified rejection of missing completion metadata, timeout, failed
  shards, an invalid shard-count type, inexact hit counts and negative counts.
  Each failed aggregation made one request and did not silently retry locally.

## Integer AVG correction discovered by live tests

The first live run found that eligibility alone did not make AVG on TINYINT,
SMALLINT and INTEGER push down: the engine inserts a widening CAST to BIGINT,
leaving a Project between the aggregation and scan.

Commit `cc55f51f821` supports only the lossless integer-to-BIGINT projection cast,
preserving mapping safety and Remote Predicate IR. Raw and cast projections get
distinct assignments and correctly typed decoders. Narrowing and floating-point
casts were not enabled. After redeployment, all narrow-integer AVG plan assertions
passed on ES7/ES8 with both search strategies.

## Global AVG of 50,000 documents

| Counter | Pushdown enabled | Pushdown disabled |
| --- | ---: | ---: |
| Aggregation requests | 1 | 0 |
| Initial search requests | 0 | 3 |
| Scroll continuation requests | 0 | 51 |
| Source rows decoded | 0 | 50,000 |
| Source bytes decoded | 0 | 752,784 |
| ES search/scroll JSON response bytes observed by proxy | 205 | 4,774,496 |
| Result rows | 1 | 1 |
| SQL result | -0.5 | -0.5 |
| Elapsed time in this run | 68 ms | 401 ms |

The pushed request used `size:0`, `track_total_hits:true` and ES `avg`. It returned
no source hits. Response-byte counters exclude HTTP headers and metadata endpoints.
This is one local workload measurement, not a general latency or peak-RAM guarantee.

## Composite grouping: 50,000 unique keys

| Measurement | Page size 1,000 | Page size 10,000 |
| --- | ---: | ---: |
| Output groups | 50,000 | 50,000 |
| Aggregation requests, including final empty page | 51 | 6 |
| Total response bytes | 1,750,534 | 1,740,242 |
| Largest response bytes | 35,229 | 350,230 |
| JVM process-wide allocated-byte delta | 54,190,384 | 39,415,736 |
| Elapsed seconds in this run | 0.367 | 0.217 |

Both cases warmed up the query. The allocation delta includes background JVM
activity and JMX observation queries; it is not operator-exclusive allocation or
peak retained heap. A larger page made each maximum response about ten times
larger, even though request count and total allocation decreased in this run.

## Evidence and remaining gates

The lab's `results/verification.json` contains query IDs, distributed plans,
per-request proxy observations, diagnostics and measurements. Failed pre-fix
runs are preserved separately. Image digests and actual container mounts were saved.

The live SQL suite does not replace the entire Maven connector/Testcontainers
suite or Sphinx build. Those and final-SHA GitHub Actions remain separate gates.
The user explicitly authorized direct SSH deployment for this isolated test;
no production merge, direct production push or GitHub deployment was performed.

## CLI follow-up

The manual CLI collection exposed an empty-projection page bug in
`SELECT count(*) FROM (SELECT id FROM lab_qs_volume GROUP BY id)`.
Commit `fe97945eb9c` preserves explicit page row counts even with zero output
columns. The source-built connector was redeployed to the lab with that revision.
The connector package passed 269 local unit/HTTP tests (one skipped benchmark)
with Error Prone. The full 202 live checks passed again, and the manual SQL file
completed successfully from the Windows CLI. The original measurements above
remain the recorded measurements at revision `cc55f51f821`.
