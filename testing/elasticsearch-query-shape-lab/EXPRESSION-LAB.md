# Expression pushdown: three-revision comparison

This comparison is separate from the earlier query-shape lab in README.md.
It executes the same array and scalar SQL against master, internal/production,
and the candidate branch, with identical Elasticsearch fixtures. It checks
results against Trino with connector pushdown disabled and captures the actual
Elasticsearch HTTP Query DSL and source documents returned.

See [the executed SQL and results](EXPRESSION-COMPARISON.md),
[CSV](expression-comparison.csv), [SQL](expression-comparison.sql), and
[raw evidence](expression-comparison.json.gz). The raw evidence contains every
query ID, full distributed plan, request/response metrics, result/error, fixture
mapping, artifact checksum, runtime revision and session setting.

## Deployed environment

The comparison is deployed on `172.16.11.138` under
`/opt/trino-expression-comparison-a2af4b7203e`. No credentials are stored here.

| Variant | Source revision | Loopback port |
| --- | --- | --- |
| master | `2162c006a7171d90305e21d1b2dd2fd47ef72b2a` | 18091 |
| internal/production | `9abc69bc79ad0de1563031d7d5dd533b9212b3b6` | 18092 |
| candidate | `ffcf7b85a3b187b869cac8d8f7a56ec531b7630a` | 18093 |

Both catalogs, `es8_compare` and `es7_compare`, use schema `default`.
The candidate enables the array and scalar script catalog defaults, with full-text
SAFE. Baseline sessions only set properties supported by that baseline.
Scripts-OFF test cases explicitly disable both script session properties.
The existing Trino service on port 18090 is not replaced.

Use an SSH tunnel to access the candidate from another machine:

```bash
ssh -L 18093:127.0.0.1:18093 root@172.16.11.138
```

Then connect a Trino client to `http://127.0.0.1:18093` with a lab username,
catalog `es8_compare`, and schema `default`.

## Artifacts and deployment

`expression-deploy.py` requires the existing isolated Docker network
`trino-harbor-verification_default`, the Elasticsearch 8.11.3 container
`trino-harbor-verification-es8-1`, and the pinned internal base image
`registry.icomm.vn/fusioncenter/trino:485-internal-9abc69bc79a-amd64`.
It checks that image's immutable ID before building overlays. It provisions
Elasticsearch 7.17.27, a per-variant request recorder and owned Trino containers;
all exposed ports bind to loopback. Replacement requires the ownership label
`codex.lab=trino-expression-comparison`.

Stage under the deployment root:

```text
expression-deploy.py
expression-proxy.py
expression-compare.py
expression-report.py
artifacts/master/trino-main.jar
artifacts/master/plugin.zip
artifacts/master/server-main.jar
artifacts/current/trino-main-485-SNAPSHOT.jar
artifacts/current/trino-spi-485-SNAPSHOT.jar
artifacts/current/trino-elasticsearch-485-SNAPSHOT.zip
artifacts/current/server-main.jar
```

Clean-build the selected source revisions using JDK 25 and Maven 3.9.16:

```bash
./mvnw -pl :trino-main,:trino-elasticsearch,:trino-server-main clean package \
  -DskipTests -Dair.check.skip-all=true -Dmaven.javadoc.skip=true
```

Build candidate SPI as well when its source differs from the base. The deployed
candidate's core and SPI artifacts were built from `a2af4b7203e`; no source in
those modules changed in the subsequent fix commit. Connector and server launcher
were clean-built from `ffcf7b85a3b`. Their embedded manifests and SHA-256 values
are recorded in `deployment.json` and the raw evidence.

Master/internal production source differs only in the Elasticsearch connector;
the trino-main difference is test-only. The master overlay nevertheless replaces
trino-main, connector and launcher with clean-built master artifacts. Other
runtime modules reuse the identical source from the pinned base image. The
server's `/v1/info` reports a distinct, verified revision for each variant.

On the host, from the deployment root:

```bash
python3 expression-deploy.py --es7
# Redeploy only a staged candidate:
python3 expression-deploy.py --es7 --variants current
```

The deployment script pins the tested source revisions; update its candidate
revision when staging a different candidate. Do not relabel an existing artifact
as a different commit.

## Run and render

```bash
python3 expression-compare.py --seed > comparison.log 2>&1
python3 expression-report.py results/comparison.json.gz --out results
```

`--seed` recreates only the owned `expr_a2af4b7203e_cases` and
`expr_a2af4b7203e_volume` indices on both backends. Preserve previous results before
rerunning. Without `--seed`, retain `results/fixtures.json` from the seeded run.
For a focused rerun, copy that file to a separate output directory and use:

```bash
python3 expression-compare.py --only 'TXT27|DATE-NULL-ERROR|DIFF-REVERSE' \
  --out focused-results
```

The harness waits for catalogs, runs a local reference query for every SQL, then
executes EXPLAIN and SQL on three variants concurrently. Each variant/backend has
its own proxy listener and state; SQL cases within one listener run serially.
A candidate mismatch makes the process fail. Baseline mismatches remain recorded
without changing baseline code. Expected SQL errors are checked by error code;
unordered LIMIT is checked for valid rows and cardinality.

The generated Markdown and CSV each have exactly four columns: SQL and the three
Trino variants. Full result rows, DSL, plan and statistics remain in the compressed
JSON. Source-hit metrics mean documents transferred to Trino, not Elasticsearch's
internal document evaluations. Metadata `_search_shards` calls are excluded from
search response bytes and request totals. A count response may return no source
documents. Folded plans and queries with no remaining filter are distinct from
remote predicate pushdown. The harness recognizes `{"match_none": {}}` as a native
predicate; its empty body must not be confused with an index-only metadata filter.
To recompute derived labels from saved plans and requests without executing SQL:

```bash
python3 expression-compare.py --reclassify results
```

This leaves SQL, result rows/errors, query IDs, plans and HTTP evidence intact.

The harness temporarily raises the shared lab Elasticsearch script compilation
rate to handle hundreds of distinct expression shapes and restores its previous
transient value in `finally`. Before/after values are saved with the run. This is
an isolated functional/offload comparison, not an independent latency benchmark
or a test against production customer indices.

The initial failed matrix is retained on the host in `results-initial/`, with
`comparison-initial.log` and `temporal-probe-before.json`. The final report uses
the subsequent corrected deployment. Local verification included 220 connector
unit tests, four ES7/ES8 regression integration tests, CI checks and Error Prone.
