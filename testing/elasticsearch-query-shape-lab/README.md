# Elasticsearch query-shape integration lab

This is an isolated, single-node Trino test deployment built from this repository.
It runs Elasticsearch 7.17.27 and 8.11.3, Scroll/PIT catalogs, configurable-page-size
catalogs, JMX diagnostics, and an ES8 request-recording/fault-injection proxy.
It contains no SSH credentials.

The source snapshot is bind-mounted read-only at `/workspace`; Java runs the
source-built server-main JAR and runtime dependencies from that snapshot.
Elasticsearch and JMX plugins are unpacked from source-built plugin archives.
`SOURCE_REVISION` and the connector JAR checksum identify the tested code.
The server's reported engine version can retain the engine build's base revision;
use the source revision plus connector checksum to identify the backport.

## Build and stage

Use JDK 25 and the repository Maven wrapper (3.9.16). From the repository root:

```bash
./mvnw -B -pl :trino-server-main,:trino-elasticsearch,:trino-jmx -am -DskipTests -Dair.check.skip-all=true -Dmaven.javadoc.skip=true install
./mvnw -B -pl :trino-server-main -DincludeScope=runtime -DoutputDirectory=target/runtime-deps dependency:copy-dependencies
```

On the test host, create a dedicated lab directory and put these scripts in it.
Stage a source snapshot in `source/`, preserving these built paths:

```text
source/core/trino-server-main/target/trino-server-main-484-SNAPSHOT.jar
source/core/trino-server-main/target/runtime-deps/*.jar
source/plugin/trino-elasticsearch/target/trino-elasticsearch-484-SNAPSHOT.zip
source/plugin/trino-jmx/target/trino-jmx-484-SNAPSHOT.zip
SOURCE_REVISION
```

Then run:

```bash
python3 prepare.py
docker compose -f compose.yaml up -d
python3 -c 'import verify; print(verify.query("SHOW CATALOGS")["rows"])'
python3 verify.py --seed > results/verification.log 2>&1
```

Wait until Trino has initialized every catalog before running verification.
`--seed` recreates only the named `lab_qs_` fixtures in the two isolated ES services.
Omit it to rerun SQL verification against the existing fixtures.

## Connections

- Trino: host port `18081`; test username `lab`; no password in this isolated lab.
- Catalogs: `es7_scroll`, `es7_pit`, `es8_scroll`, `es8_pit`, `es8_proxy`,
  `es8_page1`, `es8_page2`, `es8_page1000`, `es8_page10000`; schema `default`.
- ES7/ES8/proxy host ports `19200`/`19201`/`19202` are bound to loopback.
- Data volumes and the compose network belong to project `trino-query-shape-lab`.

Example SQL:

```sql
SELECT avg(value) FROM es8_scroll.default.lab_qs_volume;
SELECT dt, flag, agg_0, avg(i)
FROM es8_pit.default.lab_qs_shapes
GROUP BY dt, flag, agg_0;
SELECT id FROM es7_scroll.default.lab_qs_active ORDER BY id DESC LIMIT 1;
```

## Verification and measurements

`verify.py` compares pushdown-enabled and engine-disabled SQL results and inspects
distributed plans. It covers integer/float AVG, type boundaries, NULL/empty input,
mapping fallback, signed zero, output-name collision, typed grouping, alias
filter/routing, multi-key TopN, LIMIT boundaries, multiple pages, and aggregation
failure responses. Fixtures use three populated shards; only alias fixtures use
explicit tenant routing.

The AVG benchmark uses 50,000 source documents. It records connector diagnostics
and actual proxy request/response sizes, including scroll continuation responses.
The proxy injects malformed completion metadata only when explicitly armed by
the verification script; normal queries are forwarded unchanged.

Page-size measurements warm up the query and record request count, response bytes,
largest response, and the JVM's process-wide `TotalThreadAllocatedBytes` delta.
That delta includes background activity and measurement-query overhead. It is
not a peak heap measurement or an allocation figure exclusively attributable to
one operator. SQL result comparison rounds finite floats to six significant
digits, treating signed zero as equal; exact integral keys remain exact.

Detailed output is in `results/verification.json` and the console log. Preserve
failed runs separately when correcting implementation or harness issues.

## CLI and manual SQL

A source-built CLI is available locally at
`client/trino-cli/target/trino-cli-484-SNAPSHOT-executable.jar` and on the deployed
lab at `/runtime/trino-cli.jar` inside the Trino container. Use JDK 25.

On the lab host:

```bash
cd /opt/trino-query-shape-lab
docker compose -f compose.yaml exec trino java --enable-native-access=ALL-UNNAMED -jar /runtime/trino-cli.jar --server http://localhost:8080 --user lab --catalog es8_scroll --schema default
```

From a workstation with Java 25, run the local executable JAR with
`--server http://<lab-host>:18081 --user lab --catalog es8_scroll --schema default`.
Add `--file testing/elasticsearch-query-shape-lab/manual-tests.sql` to run the
manual query collection in batch. Change catalogs to exercise ES7 and PIT.
The SQL collection resets the session switches after comparing enabled/disabled
pushdown; it does not recreate fixtures or inject failures.
