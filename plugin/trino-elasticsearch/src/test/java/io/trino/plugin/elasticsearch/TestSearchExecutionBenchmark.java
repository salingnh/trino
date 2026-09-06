/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.elasticsearch;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.SearchDocument;
import io.trino.plugin.elasticsearch.client.SearchResult;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Term;
import io.trino.testing.TestingConnectorSession;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static com.google.common.io.Resources.getResource;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchServer.ELASTICSEARCH_7_IMAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchServer.ELASTICSEARCH_8_IMAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Isolated
@EnabledIfSystemProperty(named = "elasticsearch.benchmark", matches = "true")
public class TestSearchExecutionBenchmark
{
    private static final Logger LOG = Logger.get(TestSearchExecutionBenchmark.class);
    private static final int DOCUMENTS = Integer.getInteger("elasticsearch.benchmark.documents", 50_000);
    private static final int SLOW_SCAN_SECONDS = Integer.getInteger("elasticsearch.benchmark.slow-seconds", 60);
    private static final ElasticsearchTableHandle TABLE = new ElasticsearchTableHandle(SCAN, "default", "scan_benchmark", Optional.empty());

    @Test
    public void compareStrategiesOnMultipleShards()
            throws Exception
    {
        assertThat(DOCUMENTS).isGreaterThanOrEqualTo(5000);
        assertThat(SLOW_SCAN_SECONDS).isGreaterThanOrEqualTo(5);
        for (String image : List.of(ELASTICSEARCH_7_IMAGE, ELASTICSEARCH_8_IMAGE)) {
            try (ElasticsearchServer server = new ElasticsearchServer(image); var rest = server.getClient()) {
                Request create = new Request("PUT", "/scan_benchmark");
                create.setJsonEntity("{\"settings\":{\"number_of_shards\":3,\"number_of_replicas\":0}}");
                rest.performRequest(create);
                // Bounded bulk requests avoid making the fixture itself a large retained allocation.
                for (int batch = 0; batch < DOCUMENTS; batch += 1000) {
                    StringBuilder bulk = new StringBuilder();
                    for (int id = batch; id < Math.min(batch + 1000, DOCUMENTS); id++) {
                        bulk.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
                        bulk.append("{\"id\":").append(id).append(",\"category\":").append(id % 2)
                                .append(",\"text\":\"").append("ngô văn ".repeat(100)).append("\"}\n");
                    }
                    Request load = new Request("POST", "/scan_benchmark/_bulk");
                    load.setJsonEntity(bulk.toString());
                    try (var input = rest.performRequest(load).getEntity().getContent()) {
                        assertThat(JsonMapper.builder().build().readTree(input).path("errors").asBoolean()).isFalse();
                    }
                }
                rest.performRequest(new Request("POST", "/scan_benchmark/_refresh"));

                // Alternate order between ES versions. These are workload measurements, not timing assertions.
                List<ElasticsearchConfig.SearchStrategy> strategies = List.of(ElasticsearchConfig.SearchStrategy.values());
                for (ElasticsearchConfig.SearchStrategy strategy : image.equals(ELASTICSEARCH_7_IMAGE) ? strategies : strategies.reversed()) {
                    ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
                    ElasticsearchConfig config = new ElasticsearchConfig()
                            .setHosts(List.of(server.getAddress().getHost()))
                            .setPort(server.getAddress().getPort())
                            .setTlsEnabled(true)
                            .setTrustStorePath(new File(getResource("truststore.jks").toURI()))
                            .setTruststorePassword("123456")
                            .setVerifyHostnames(false)
                            .setScrollSize(100)
                            .setScrollTimeout(new Duration(1, SECONDS))
                            .setSearchStrategy(strategy);
                    ElasticsearchClient client = new ElasticsearchClient(
                            config,
                            Optional.empty(),
                            Optional.of(new PasswordConfig().setUser(ElasticsearchQueryRunner.USER).setPassword(ElasticsearchQueryRunner.PASSWORD)),
                            diagnostics);
                    try {
                        for (int round = 0; round < 4; round++) {
                            Measurement full = scan(client, diagnostics, TABLE, round == 3);
                            assertThat(full.ids()).hasSize(DOCUMENTS);
                            LOG.info("image=%s strategy=%s round=%s %s", image, strategy, round, full.summary());
                            assertNoContexts(rest);
                            if (round == 2) {
                                for (List<ElasticsearchColumnSort> sort : List.of(List.<ElasticsearchColumnSort>of(), List.of(new ElasticsearchColumnSort("id", true, false)))) {
                                    Measurement limited = scan(client, diagnostics, TABLE.withTopN(10, sort), false);
                                    assertThat(limited.ids()).hasSize(30);
                                    assertThat(limited.requests()).isEqualTo(3);
                                    assertThat(limited.pages()).isEqualTo(3);
                                    assertThat(limited.bytes()).isLessThan(full.bytes());
                                    assertThat(limited.requests()).isLessThan(full.requests());
                                    if (!sort.isEmpty()) {
                                        assertThat(limited.ids().stream().mapToInt(Integer::parseInt).sorted().limit(10).toArray())
                                                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
                                    }
                                    LOG.info("image=%s strategy=%s shape=%s %s", image, strategy, sort.isEmpty() ? "LIMIT" : "TopN", limited.summary());
                                    assertNoContexts(rest);
                                }
                            }
                        }
                        assertThat(diagnostics.getFailures()).isZero();
                        assertThat(diagnostics.getCancellations()).isZero();
                        measureCancellationAndFailure(client, diagnostics, rest, image, strategy);
                        measureStatistics(client, config, diagnostics, image);
                    }
                    finally {
                        client.close();
                    }
                }
            }
        }
    }

    private static Measurement scan(ElasticsearchClient client, ElasticsearchPushdownDiagnostics diagnostics, ElasticsearchTableHandle table, boolean slow)
            throws InterruptedException
    {
        Set<String> ids = new HashSet<>();
        long requestsBefore = diagnostics.getSearchRequests() + diagnostics.getNextPageRequests();
        long pagesBefore = diagnostics.getRemotePagesReceived();
        long bytesBefore = diagnostics.getSourceBytesDecoded();
        long start = System.nanoTime();
        BenchmarkMemory memory = new BenchmarkMemory();
        for (int shard = 0; shard < 3; shard++) {
            try (SearchExecution execution = new SearchExecution(strategy(client, table, shard, diagnostics), table.limit(), diagnostics)) {
                while (execution.hasNext()) {
                    SearchDocument document = execution.next();
                    assertThat(ids.add(document.id())).isTrue();
                    diagnostics.recordDecodedRows(1, document.sourceLength());
                    execution.documentDecoded();
                    if (slow && ids.size() % 100 == 0) {
                        // Outlive the one-second keep-alive many times without ever reopening the context.
                        Thread.sleep(Math.max(1, SLOW_SCAN_SECONDS * 1000L / (DOCUMENTS / 100)));
                    }
                }
            }
        }
        return new Measurement(
                ids,
                diagnostics.getSearchRequests() + diagnostics.getNextPageRequests() - requestsBefore,
                diagnostics.getRemotePagesReceived() - pagesBefore,
                diagnostics.getSourceBytesDecoded() - bytesBefore,
                (System.nanoTime() - start) / 1_000_000,
                memory.summary());
    }

    private static SearchExecutionStrategy strategy(ElasticsearchClient client, ElasticsearchTableHandle table, int shard, ElasticsearchPushdownDiagnostics diagnostics)
    {
        return SearchExecutionStrategies.create(
                client,
                table,
                new ElasticsearchSplit("scan_benchmark", shard, Optional.empty()),
                Optional.empty(),
                List.of(),
                diagnostics);
    }

    private static void measureCancellationAndFailure(ElasticsearchClient client, ElasticsearchPushdownDiagnostics diagnostics, RestClient rest, String image, ElasticsearchConfig.SearchStrategy strategy)
            throws Exception
    {
        long closes = diagnostics.getClearScrollCalls() + diagnostics.getPointInTimeCloses();
        long start;
        try (SearchExecution execution = new SearchExecution(strategy(client, TABLE, 0, diagnostics), OptionalLong.empty(), diagnostics)) {
            assertThat(execution.hasNext()).isTrue();
            execution.next();
            start = System.nanoTime();
            execution.close();
            execution.close();
        }
        LOG.info("image=%s strategy=%s cancellationCleanupMicros=%s", image, strategy, (System.nanoTime() - start) / 1000);
        assertThat(diagnostics.getCancellations()).isEqualTo(1);
        assertThat(diagnostics.getClearScrollCalls() + diagnostics.getPointInTimeCloses() - closes).isEqualTo(1);
        assertNoContexts(rest);

        SearchExecutionStrategy delegate = strategy(client, TABLE, 0, diagnostics);
        SearchExecutionStrategy failing = new SearchExecutionStrategy()
        {
            @Override
            public SearchResult open()
            {
                return delegate.open();
            }

            @Override
            public boolean hasNextPage()
            {
                return delegate.hasNextPage();
            }

            @Override
            public SearchResult nextPage()
            {
                throw new IllegalStateException("injected next-page failure");
            }

            @Override
            public void close()
            {
                delegate.close();
            }
        };
        closes = diagnostics.getClearScrollCalls() + diagnostics.getPointInTimeCloses();
        start = System.nanoTime();
        try (SearchExecution execution = new SearchExecution(failing, OptionalLong.empty(), diagnostics)) {
            assertThatThrownBy(() -> {
                while (execution.hasNext()) {
                    execution.next();
                }
            }).isInstanceOf(IllegalStateException.class).hasMessageContaining("injected next-page");
        }
        LOG.info("image=%s strategy=%s failureAndCleanupMicros=%s", image, strategy, (System.nanoTime() - start) / 1000);
        assertThat(diagnostics.getFailures()).isEqualTo(1);
        assertThat(diagnostics.getCancellations()).isEqualTo(1);
        assertThat(diagnostics.getClearScrollCalls() + diagnostics.getPointInTimeCloses() - closes).isEqualTo(1);
        assertNoContexts(rest);
        // Recovery is a separate query, never a transparent replay that could duplicate already emitted rows.
        Measurement recovery = scan(client, diagnostics, TABLE, false);
        assertThat(recovery.ids()).hasSize(DOCUMENTS);
        LOG.info("image=%s strategy=%s freshQueryRecovery %s", image, strategy, recovery.summary());
        assertNoContexts(rest);
    }

    private static void measureStatistics(ElasticsearchClient client, ElasticsearchConfig config, ElasticsearchPushdownDiagnostics diagnostics, String image)
    {
        var session = TestingConnectorSession.builder().setPropertyMetadata(new ElasticsearchSessionProperties(config).getSessionProperties()).build();
        ElasticsearchTableHandle filtered = withRemotePredicate(TABLE, Optional.of(new Term("category", 1)));
        for (boolean columns : List.of(false, true)) {
            config.setStatisticsMaxIndexDocuments(columns ? DOCUMENTS : 1);
            var metadata = new RuleBasedElasticsearchMetadata(TESTING_TYPE_MANAGER, client, config, diagnostics);
            for (int warmup = 0; warmup < 3; warmup++) {
                metadata.getTableStatistics(session, filtered);
            }
            long[] nanos = new long[20];
            long requests = diagnostics.getStatisticsRequests();
            BenchmarkMemory memory = new BenchmarkMemory();
            for (int iteration = 0; iteration < nanos.length; iteration++) {
                long start = System.nanoTime();
                var statistics = metadata.getTableStatistics(session, filtered);
                nanos[iteration] = System.nanoTime() - start;
                assertThat(statistics.getRowCount().getValue()).isEqualTo(Math.floor(DOCUMENTS / 2.0));
                assertThat(statistics.getColumnStatistics().size()).isEqualTo(columns ? 1 : 0);
            }
            Arrays.sort(nanos);
            assertThat(diagnostics.getStatisticsRequests() - requests).isEqualTo(nanos.length * (columns ? 2L : 1L));
            LOG.info("image=%s statisticsColumns=%s calls=%s searchRequests=%s mappingRequests=%s medianMicros=%s p95Micros=%s %s",
                    image,
                    columns,
                    nanos.length,
                    diagnostics.getStatisticsRequests() - requests,
                    columns ? nanos.length : 0,
                    nanos[nanos.length / 2] / 1000,
                    nanos[18] / 1000,
                    memory.summary());
        }
    }

    private static void assertNoContexts(RestClient rest)
            throws Exception
    {
        try (var input = rest.performRequest(new Request("GET", "/_nodes/stats/indices/search")).getEntity().getContent()) {
            var nodes = JsonMapper.builder().build().readTree(input).path("nodes");
            assertThat(nodes.isEmpty()).isFalse();
            for (var node : nodes) {
                assertThat(node.path("indices").path("search").path("open_contexts").asLong()).isZero();
            }
        }
    }

    private record Measurement(Set<String> ids, long requests, long pages, long bytes, long millis, String memory)
    {
        String summary()
        {
            return "rows=" + ids.size() + " elapsedMs=" + millis + " requests=" + requests + " pages=" + pages + " sourceBytes=" + bytes + " " + memory;
        }
    }
}
