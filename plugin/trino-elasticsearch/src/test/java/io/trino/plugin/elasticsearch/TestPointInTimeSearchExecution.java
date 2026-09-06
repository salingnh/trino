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
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.spi.TrinoException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestPointInTimeSearchExecution
{
    private static final ElasticsearchTableHandle TABLE = new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty());
    private static final ElasticsearchColumnHandle ID = new ElasticsearchColumnHandle(
            List.of("id"), INTEGER, new PrimitiveType("integer"), new IntegerDecoder.Descriptor("id"), true);

    @Test
    public void testPaginationAndLatestContextCleanup()
            throws Exception
    {
        try (Server server = new Server(false, false, false); ScanQueryPageSource source = server.scan(TABLE)) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(source.isFinished()).isTrue();
            source.close();
            assertThat(server.searchBodies).hasSize(2);
            assertThat(server.searchBodies.getFirst()).contains("\"_shard_doc\"").doesNotContain("\"_doc\"");
            assertThat(server.searchBodies.getLast()).contains("\"search_after\":[101]", "\"id\":\"updated\"");
            assertThat(server.closeBodies).containsExactly("{\"id\":\"latest\"}");
            assertThat(server.openQueries).singleElement().asString().contains("preference=_shards:3");
            assertThat(server.diagnostics.getPointInTimeOpens()).isEqualTo(1);
            assertThat(server.diagnostics.getPointInTimeCloses()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testLimitClosesWithoutNextRequest()
            throws Exception
    {
        try (Server server = new Server(false, false, false); ScanQueryPageSource source = server.scan(TABLE.withTopN(1, List.of()))) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(server.searchBodies).hasSize(1);
            assertThat(server.closeBodies).containsExactly("{\"id\":\"updated\"}");
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testEarlyCloseIsIdempotent()
            throws Exception
    {
        try (Server server = new Server(false, false, false); ScanQueryPageSource source = server.scan(TABLE)) {
            source.close();
            source.close();
            assertThat(server.closeBodies).hasSize(1);
            assertThat(server.diagnostics.getCancellations()).isEqualTo(1);
        }
    }

    @Test
    public void testFailedInitialSearchClosesOpenedContext()
            throws Exception
    {
        try (Server server = new Server(true, false, false)) {
            assertThatThrownBy(() -> server.scan(TABLE)).isInstanceOf(TrinoException.class);
            assertThat(server.closeBodies).containsExactly("{\"id\":\"opened\"}");
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
        }
    }

    @Test
    public void testDecodeFailureClosesLatestContext()
            throws Exception
    {
        try (Server server = new Server(false, true, false); ScanQueryPageSource source = server.scan(TABLE)) {
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
            assertThat(server.closeBodies).containsExactly("{\"id\":\"updated\"}");
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testNextPageFailureDoesNotReopenContext()
            throws Exception
    {
        try (Server server = new Server(true, false, false, 2); ScanQueryPageSource source = server.scan(TABLE)) {
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
            assertThat(server.openQueries).hasSize(1);
            assertThat(server.searchBodies).hasSize(2);
            assertThat(server.closeBodies).containsExactly("{\"id\":\"updated\"}");
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testCleanupFailureDoesNotMaskSearchFailure()
            throws Exception
    {
        try (Server server = new Server(true, false, true)) {
            assertThatThrownBy(() -> server.scan(TABLE)).isInstanceOf(TrinoException.class);
            assertThat(server.closeBodies).hasSize(1);
            assertThat(server.diagnostics.getFailures()).isEqualTo(2);
        }
    }

    @Test
    public void testHitFailureClosesLaterContext()
            throws Exception
    {
        for (ElasticsearchConfig.ResponseDecoder decoder : ElasticsearchConfig.ResponseDecoder.values()) {
            try (Server server = new Server(false, false, false, 1, decoder)) {
                server.initialSearchResponse = "{\"hits\":{\"hits\":[{\"_id\":\"1\",\"_source\":42}]},\"pit_id\":\"newest\"}";
                assertThatThrownBy(() -> server.scan(TABLE)).isInstanceOf(IllegalArgumentException.class);
                assertThat(server.closeBodies).containsExactly("{\"id\":\"newest\"}");
                assertThat(server.openQueries).hasSize(1);
                assertThat(server.searchBodies).hasSize(1);
                assertThat(server.diagnostics.getFailures()).isEqualTo(1);
                assertThat(server.diagnostics.getCancellations()).isZero();
            }
        }
    }

    @Test
    public void testIncompleteResponseClosesContextAfterFailureField()
            throws Exception
    {
        for (ElasticsearchConfig.ResponseDecoder decoder : ElasticsearchConfig.ResponseDecoder.values()) {
            try (Server server = new Server(false, false, false, 1, decoder)) {
                server.initialSearchResponse = "{\"timed_out\":true,\"hits\":{\"hits\":[]},\"pit_id\":\"newest\"}";
                assertThatThrownBy(() -> server.scan(TABLE)).isInstanceOf(TrinoException.class).hasMessageContaining("incomplete");
                assertThat(server.closeBodies).containsExactly("{\"id\":\"newest\"}");
                assertThat(server.openQueries).hasSize(1);
                assertThat(server.diagnostics.getFailures()).isEqualTo(1);
                assertThat(server.diagnostics.getCancellations()).isZero();
            }
        }
    }

    private static class Server
            implements AutoCloseable
    {
        private final HttpServer server;
        private final ElasticsearchClient client;
        private final ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        private final List<String> searchBodies = new CopyOnWriteArrayList<>();
        private final List<String> closeBodies = new CopyOnWriteArrayList<>();
        private final List<String> openQueries = new CopyOnWriteArrayList<>();
        private volatile String initialSearchResponse;

        public Server(boolean failSearch, boolean invalidValue, boolean failClose)
                throws IOException
        {
            this(failSearch, invalidValue, failClose, 1);
        }

        public Server(boolean failSearch, boolean invalidValue, boolean failClose, int failurePage)
                throws IOException
        {
            this(failSearch, invalidValue, failClose, failurePage, ElasticsearchConfig.ResponseDecoder.MATERIALIZED);
        }

        public Server(boolean failSearch, boolean invalidValue, boolean failClose, int failurePage, ElasticsearchConfig.ResponseDecoder decoder)
                throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    String request = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
                    int status = 200;
                    String body;
                    if (exchange.getRequestMethod().equals("DELETE")) {
                        closeBodies.add(request);
                        status = failClose ? 500 : 200;
                        body = "{}";
                    }
                    else if (path.equals("/")) {
                        body = "{\"version\":{\"number\":\"7.17.27\"}}";
                    }
                    else if (path.equals("/events/_pit")) {
                        openQueries.add(exchange.getRequestURI().getQuery());
                        body = "{\"id\":\"opened\"}";
                    }
                    else {
                        searchBodies.add(request);
                        status = failSearch && searchBodies.size() == failurePage ? 500 : 200;
                        if (searchBodies.size() == 1) {
                            body = "{\"pit_id\":\"updated\",\"hits\":{\"hits\":[{\"_id\":\"1\",\"sort\":[101],\"_source\":{\"id\":" +
                                    (invalidValue ? "\"invalid\"" : "42") + "}}]}}";
                            if (initialSearchResponse != null) {
                                body = initialSearchResponse;
                            }
                        }
                        else {
                            body = "{\"pit_id\":\"latest\",\"hits\":{\"hits\":[]}}";
                        }
                    }
                    byte[] bytes = body.getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            client = new ElasticsearchClient(new ElasticsearchConfig()
                    .setHosts(List.of(server.getAddress().getAddress().getHostAddress()))
                    .setPort(server.getAddress().getPort())
                    .setResponseDecoder(decoder)
                    .setSearchStrategy(ElasticsearchConfig.SearchStrategy.PIT), Optional.empty(), Optional.empty(), diagnostics);
        }

        public ScanQueryPageSource scan(ElasticsearchTableHandle table)
        {
            return new ScanQueryPageSource(client, TESTING_TYPE_MANAGER, table, new ElasticsearchSplit("events", 3, Optional.empty()), List.of(ID), diagnostics);
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
