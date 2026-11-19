# Sequential shard/document scaling benchmark

This benchmark uses the existing comparison VM at `172.16.11.138`, under
`/opt/trino-expression-comparison-a2af4b7203e`. It reuses the three already deployed
Trino variants and both Elasticsearch backends described in
[EXPRESSION-LAB.md](EXPRESSION-LAB.md). It does not redeploy, commit, or rebuild
connector code.

Follow-up work now targets **ES8 only**. The current harness defaults to `es8`;
commands below also specify `--backends es8`. The original two-backend benchmark
is historical evidence and is not rerun. Its exact executed harness is preserved
inside `expression-performance.json.gz`; its source hash intentionally differs
from the current harness with the updated default.

## Scope

The harness reads the 40 numbered rows in
[EXPRESSION-RELEASE-REPORT.md](../../plugin/trino-elasticsearch/EXPRESSION-RELEASE-REPORT.md)
and resolves their existing case IDs in `expression-compare.py`. Each feature
gets the same representative SQL and script/full-text settings as the functional
report. This is a scaling benchmark of those 40 examples, not of every overload
in every group. The existing 495-query functional comparison remains separate.

The three scaling axes are:

| Axis | Configurations `(primary shards, documents per index)` |
| --- | --- |
| Shards only | `(1, 10000)`, `(4, 10000)`, `(8, 10000)` |
| Documents only | `(1, 10000)`, `(1, 50000)`, `(1, 100000)` |
| Both | `(1, 10000)`, `(4, 50000)`, `(8, 100000)` |

The shared origin is executed once: seven distinct configurations, two backends,
40 features, three Trino versions, one warm-up and three measured repetitions =
6,720 SQL executions (5,040 measured, 1,680 warm-up), plus untimed reference queries
and EXPLAINs. No SQL benchmark queries run concurrently. Order rotates across
versions so each occupies every position in the three measured repetitions.

Every backend is a **single node**. Shard scaling measures parallelism and shard
overhead on that node, not horizontal node scaling. Each Trino has 2 CPUs and
7 GiB RAM. ES7 has 2 CPUs and 3 GiB; ES8 has 3 GiB and no Docker CPU quota. Compare
Trino versions within a backend, not ES7 versus ES8 as a controlled version study.
Other services remain on the VM; system load and Elasticsearch node statistics
are saved before and after each configuration.

## Data, correctness and cache policy

The harness reads the original `expr_a2af4b7203e_cases` (18 documents) and
`expr_a2af4b7203e_text` (20 documents), mappings and analyzers. It repeats the source
documents deterministically and assigns unique sequential IDs. It preserves
NULL/missing fields, numeric boundaries, arrays, Unicode, stopwords and text
keyword sub-fields. It does not use the original volume fixtures. Selectivity
remains nearly constant; the oracle accounts exactly for a partial final cycle.
These are small boundary document/array shapes, not a production trace.

An independent Trino local query, with `allow_pushdown_into_connectors=false`,
produces the eligible template IDs for every feature. Expected IDs at each scale
are computed from the deterministic replication, not from the candidate result.
Native approximate queries additionally have a direct Elasticsearch DSL oracle;
intentional SQL differences are recorded. COUNT is checked by value. Unordered
SELECT results are sorted and compared using count, leading IDs and a SHA-256
fingerprint; individual benchmark result rows are not duplicated in evidence.
The complete original template, oracle query/result and runtime SQL are stored.

All benchmark indices have replicas = 0 and request caching disabled. The harness
bulk-loads, refreshes, flushes, waits for green shards and no active merge, then
runs untimed EXPLAIN and a warm-up for each version/feature. It does not clear
cluster-wide query, OS or JVM caches. This measures a warm workload, including
planning and full result delivery through the recording proxy. It is not a cold
compile or cold disk experiment. Compilation rate is temporarily increased and
restored in `finally`, as in the functional comparison.

The primary latency is client wall time on the VM. The report shows median and
min–max of three samples, not p95/p99 or statistical significance. It also saves
Trino CPU, execution/planning time, memory/input stats, Elasticsearch request
`took`, search request count, request/response bytes and transferred source hits.
Sum of ES `took` is not end-to-end latency or Elasticsearch CPU time. A remote
COUNT may transfer no source documents while still evaluating data internally.

## Ownership and disk space

Only indices exactly matching
`expr_perf_20261118_s<shards>_n<documents>_(cases|text)` can be deleted by this
harness. Original functional fixtures, customer indices, containers and images
are preserved. At most one configuration's two indices is retained while a
backend runs. They are removed after measurement and on ordinary error cleanup;
there is a 2 GiB free disk check before index creation. A hard process kill or VM
failure can bypass Python cleanup; inspect `cleanup.json` and the exact owned
index names before resuming. Never delete by a broad cluster wildcard.

## Run on the VM

Stage the harness, renderer and a snapshot of the release report in the existing
lab directory alongside `expression-compare.py`. Do not overwrite the old
`results/` or its comparison artifacts.

```bash
cd /opt/trino-expression-comparison-a2af4b7203e
python3 -u expression-performance.py \
  --report performance-release-report.md \
  --backends es8 --out performance-results-es8-followup > performance-es8-followup.log 2>&1
python3 -u expression-performance.py \
  --report performance-release-report.md --backends es8 --out performance-drift-es8-followup \
  --config 1:10000 \
  --only '^(A-P1|A-I09|A-TXT20|TEXT-USER-SQL|S-TEXT02|TEXT-COUNT)$'
python3 expression-performance-report.py performance-results-es8-followup \
  --drift performance-drift-es8-followup --out performance-results-es8-followup
```

For a small smoke run, use a separate output directory:

```bash
python3 expression-performance.py \
  --report performance-release-report.md --out performance-smoke \
  --backends es8 --config 1:100 --repeats 1 \
  --only '^(A-P1|A-N11|A-I09|S-TEXT02|A-TEXT18|TEXT-USER-SQL|TEXT-COUNT|TEXT-ZERO-TOKENS)$'
```

Smoke data is not included in the full report. The full renderer expects the
seven configurations and three measured repetitions of the main benchmark.
The optional final six-feature control adds 144 executions, stored separately;
it checks drift at the original 1-shard/10k configuration after the main matrix.
Its measurements never enter the main medians. Raw evidence includes them and
the report shows their initial-versus-final timing ranges.

Use `--resume` only with the identical harness, report features, runtime revisions
and configuration. Fully completed configurations are skipped; an interrupted
configuration is reseeded and rerun from the beginning, including warm-up. This
avoids treating an incomplete measured set as a complete sample. Original
unfinished logs remain separate from the final per-configuration result.

## Recreate one configuration for manual SQL

Do this only after the benchmark/control has finished, so it cannot replace
indices that a running measurement is reading. The exported SQL is a collection
of all configurations; execute only the section matching the configuration you
have recreated. For example, on the VM, recreate ES8 / 8 shards / 100,000 docs:

```bash
cd /opt/trino-expression-comparison-a2af4b7203e
python3 - <<'PY'
import importlib.util
spec = importlib.util.spec_from_file_location('perf', 'expression-performance.py')
perf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perf)
print(perf.seed('es8', 8, 100000, perf.templates('es8')))
PY
```

Run the matching SQL and SET SESSION commands from `expression-performance.sql`
on the candidate. The exported collection resets each catalog's session switches
to its defaults at the end. If running just selected statements, use the matching
RESET SESSION statements when finished. Remove the two recreated indices after
manual testing, using the same checked ownership helper:

```bash
python3 - <<'PY'
import importlib.util
spec = importlib.util.spec_from_file_location('perf', 'expression-performance.py')
perf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perf)
for table in ('cases', 'text'):
    perf.delete_index('es8', perf.index_name(8, 100000, table))
PY
```

## Artifacts

- `EXPRESSION-PERFORMANCE.md`: method, 40-feature summary at largest scale,
  current-version trends along all three axes, and 560 four-column SQL comparisons.
- `expression-performance.csv`: 1,680 aggregated version/configuration/feature rows.
- `expression-performance-comparison.csv`: 560 SQL-versus-three-version rows.
- `expression-performance.sql`: candidate SQL/session replay (reseed first).
- `expression-performance.json.gz`: all 6,720 warm-up/measurement records, full
  plans on warm-up, actual DSL/runtime definitions, result fingerprints, metrics,
  fixtures, independent oracles, manifest and system/cleanup evidence.
- On the VM, `*-done.json`, `*-fixtures.json`, `*.jsonl.gz`, `manifest.json`,
  `progress.json`, `cleanup.json`, and `performance-20261118.log` preserve progress.

No credentials are written to these artifacts. Source/artifact provenance is
copied from the existing deployment manifest; live server revision IDs and
harness/report hashes are recorded separately.
