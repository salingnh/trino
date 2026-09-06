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

import com.sun.net.httpserver.HttpServer;
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

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchRemoteStatistics
{
    private static final TestingConnectorSession SESSION = TestingConnectorSession.builder()
            .setPropertyMetadata(new ElasticsearchSessionProperties(new ElasticsearchConfig()).getSessionProperties())
            .build();
    private static final ElasticsearchTableHandle TABLE = new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty());

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

        public Server(boolean partial)
                throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    requests.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                    byte[] body = ("{\"timed_out\":" + partial + ",\"hits\":{\"total\":{\"value\":7,\"relation\":\"eq\"}}}").getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
            ElasticsearchConfig config = new ElasticsearchConfig()
                    .setStatisticsMaxIndexDocuments(5)
                    .setHosts(List.of(server.getAddress().getAddress().getHostAddress()))
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
