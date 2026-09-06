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
import com.sun.net.httpserver.HttpServer;
import io.airlift.units.Duration;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.And;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforced;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Term;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchRemoteStatistics
{
    private static final TestingConnectorSession SESSION = TestingConnectorSession.builder()
            .setPropertyMetadata(new ElasticsearchSessionProperties(new ElasticsearchConfig()).getSessionProperties())
            .build();
    private static final ElasticsearchTableHandle TABLE = new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty());
    private static final String ROW_COUNT = "{\"timed_out\":false,\"hits\":{\"total\":{\"value\":7,\"relation\":\"eq\"}}}";
    private static final String MAPPING = "{\"events\":{\"mappings\":{\"properties\":{\"amount\":{\"type\":\"double\"},\"other\":{\"type\":\"double\"},\"rounded\":{\"type\":\"scaled_float\",\"scaling_factor\":10}}}}}";
    private static final String COLUMN_STATISTICS = "{\"hits\":{\"total\":{\"value\":7,\"relation\":\"eq\"}},\"aggregations\":{\"f0_card\":{\"value\":3},\"f0_count\":{\"value\":6},\"f0_min\":{\"value\":1},\"f0_max\":{\"value\":9}}}";

    @Test
    public void testPerColumnStatisticsPreservePredicateAndColumnBound()
            throws Exception
    {
        try (Server server = new Server(
                new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10).setStatisticsMaxColumns(1),
                (path, body) -> path.endsWith("_mappings") ? MAPPING : body.contains("\"aggs\"") ? COLUMN_STATISTICS : ROW_COUNT)) {
            ElasticsearchTableHandle handle = withRemotePredicate(TABLE, Optional.of(new And(List.of(new Term("amount", 1), new Term("other", 1)))));
            var statistics = server.metadata.getTableStatistics(SESSION, handle);
            assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
            assertThat(statistics.getColumnStatistics()).hasSize(1);
            var column = statistics.getColumnStatistics().values().iterator().next();
            assertThat(column.getDistinctValuesCount().getValue()).isEqualTo(3);
            assertThat(column.getNullsFraction().getValue()).isEqualTo(1.0 / 7);
            assertThat(column.getRange().orElseThrow().getMin()).isEqualTo(1);
            assertThat(column.getRange().orElseThrow().getMax()).isEqualTo(9);
            assertThat(server.paths).containsExactly("/events/_search", "/events/_mappings", "/events/_search");
            JsonMapper mapper = JsonMapper.builder().build();
            var first = mapper.readTree(server.requests.getFirst());
            var last = mapper.readTree(server.requests.getLast());
            assertThat(last.get("query")).isEqualTo(first.get("query"));
            assertThat(last.get("timeout").asText()).isEqualTo("5000ms");
            assertThat(last.get("aggs").size()).isEqualTo(4);
            assertThat(server.diagnostics.getStatisticsRequests()).isEqualTo(2);
        }
    }

    @Test
    public void testColumnFailurePreservesRowEstimate()
            throws Exception
    {
        for (String response : List.of("{}", "{\"timed_out\":true}", "{\"_shards\":{\"failed\":1}}", "{\"hits\":{\"total\":{\"value\":7,\"relation\":\"gte\"}}}")) {
            try (Server server = new Server(
                    new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10),
                    (path, body) -> path.endsWith("_mappings") ? MAPPING : body.contains("\"aggs\"") ? response : ROW_COUNT)) {
                var statistics = server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(new Term("amount", 1))));
                assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
                assertThat(statistics.getColumnStatistics()).isEmpty();
                assertThat(server.paths).hasSize(3);
                assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            }
        }
    }

    @Test
    public void testMappingFailurePreservesRowEstimate()
            throws Exception
    {
        try (Server server = new Server(
                new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10),
                (path, _) -> path.endsWith("_mappings") ? "{}" : ROW_COUNT)) {
            var statistics = server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(new Term("amount", 1))));
            assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
            assertThat(statistics.getColumnStatistics()).isEmpty();
            assertThat(server.paths).containsExactly("/events/_search", "/events/_mappings");
        }
    }

    @Test
    public void testRoundedDocValuesDoNotBecomeSourceStatistics()
            throws Exception
    {
        try (Server server = new Server(
                new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10),
                (path, _) -> path.endsWith("_mappings") ? MAPPING : ROW_COUNT)) {
            var statistics = server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(new Term("rounded", 1))));
            assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
            assertThat(statistics.getColumnStatistics()).isEmpty();
            assertThat(server.diagnostics.getStatisticsRequests()).isEqualTo(1);
        }
    }

    @Test
    public void testMappingTimeoutPreservesRowEstimate()
            throws Exception
    {
        try (Server server = new Server(new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10).setStatisticsRequestTimeout(new Duration(100, MILLISECONDS)),
                (path, _) -> {
                    if (path.endsWith("_mappings")) {
                        try {
                            Thread.sleep(3000);
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        return MAPPING;
                    }
                    return ROW_COUNT;
                })) {
            var statistics = server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(new Term("amount", 1))));
            assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
            assertThat(statistics.getColumnStatistics()).isEmpty();
            assertThat(server.paths).containsExactly("/events/_search", "/events/_mappings");
        }
    }

    @Test
    public void testColumnTimeoutDoesNotRetryOrDiscardRowEstimate()
            throws Exception
    {
        try (Server server = new Server(new ElasticsearchConfig().setStatisticsMaxIndexDocuments(10).setStatisticsRequestTimeout(new Duration(100, MILLISECONDS)),
                (path, body) -> {
                    if (path.endsWith("_mappings")) {
                        return MAPPING;
                    }
                    if (body.contains("\"aggs\"")) {
                        try {
                            Thread.sleep(3000);
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    }
                    return ROW_COUNT;
                })) {
            var statistics = server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(new Term("amount", 1))));
            assertThat(statistics.getRowCount().getValue()).isEqualTo(7);
            assertThat(statistics.getColumnStatistics()).isEmpty();
            assertThat(server.diagnostics.getStatisticsRequests()).isEqualTo(2);
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.paths).hasSize(3);
        }
    }

    @Test
    public void testExactRemoteFilterReachesStatistics()
            throws Exception
    {
        try (Server server = new Server(false)) {
            ElasticsearchTableHandle handle = withRemotePredicate(TABLE, Optional.of(new Term("status", "active")));
            assertThat(server.metadata.getTableStatistics(SESSION, handle).getRowCount().getValue()).isEqualTo(7);
            assertThat(server.requests).singleElement().asString().contains("\"term\"", "status", "active");
            assertThat(server.diagnostics.getStatisticsRequests()).isEqualTo(1);
        }
    }

    @Test
    public void testNestedNonExactFilterRemainsUnknown()
            throws Exception
    {
        try (Server server = new Server(false)) {
            for (Enforcement enforcement : List.of(Enforcement.PREFILTER, Enforcement.APPROXIMATE)) {
                ElasticsearchRemotePredicate predicate = new And(List.of(new Term("id", 1), new Enforced(new Term("name", "x"), enforcement)));
                assertThat(server.metadata.getTableStatistics(SESSION, withRemotePredicate(TABLE, Optional.of(predicate))).getRowCount().isUnknown()).isTrue();
            }
            assertThat(server.requests).isEmpty();
        }
    }

    @Test
    public void testLimitBoundsRowEstimate()
            throws Exception
    {
        try (Server server = new Server(false)) {
            assertThat(server.metadata.getTableStatistics(SESSION, TABLE.withTopN(3, List.of())).getRowCount().getValue()).isEqualTo(3);
        }
    }

    @Test
    public void testPartialResponseIsUnknown()
            throws Exception
    {
        try (Server server = new Server(true)) {
            assertThat(server.metadata.getTableStatistics(SESSION, TABLE).getRowCount().isUnknown()).isTrue();
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
        }
    }

    private static class Server
            implements AutoCloseable
    {
        private final HttpServer server;
        private final ElasticsearchClient client;
        private final RuleBasedElasticsearchMetadata metadata;
        private final ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<String> paths = new CopyOnWriteArrayList<>();

        public Server(boolean partial)
                throws IOException
        {
            this(new ElasticsearchConfig().setStatisticsMaxIndexDocuments(5),
                    (_, _) -> "{\"timed_out\":" + partial + ",\"hits\":{\"total\":{\"value\":7,\"relation\":\"eq\"}}}");
        }

        public Server(ElasticsearchConfig config, BiFunction<String, String, String> response)
                throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    String request = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
                    paths.add(path);
                    requests.add(request);
                    byte[] body = response.apply(path, request).getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            config.setHosts(List.of(server.getAddress().getAddress().getHostAddress()))
                    .setPort(server.getAddress().getPort());
            client = new ElasticsearchClient(config, Optional.empty(), Optional.empty(), diagnostics);
            metadata = new RuleBasedElasticsearchMetadata(TESTING_TYPE_MANAGER, client, config, diagnostics);
        }

        @Override
        public void close()
                throws IOException
        {
            try {
                client.close();
            }
            finally {
                server.stop(0);
            }
        }
    }
}
