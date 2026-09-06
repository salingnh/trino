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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestElasticsearchPageSourceDiagnostics
{
    private static final ElasticsearchColumnHandle ID = new ElasticsearchColumnHandle(
            List.of("id"), INTEGER, new PrimitiveType("integer"), new IntegerDecoder.Descriptor("id"), true);
    private static final ElasticsearchTableHandle TABLE = new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty());

    @Test
    public void testCloseBeforeReadingClearsInitialScrollExactlyOnce()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false)) {
            ScanQueryPageSource source = server.scan(TABLE);
            source.close();
            source.close();

            assertThat(server.requests).containsExactly("POST /events/_search", "DELETE /_search/scroll");
            assertThat(server.clearBodies).singleElement().asString().contains("initial-scroll");
            assertThat(source.isFinished()).isTrue();
            assertThat(server.diagnostics.getCancellations()).isEqualTo(1);
            assertThat(server.diagnostics.getClearScrollCalls()).isEqualTo(1);
            assertThat(server.diagnostics.getRowsDecoded()).isZero();
        }
    }

    @Test
    public void testExhaustionAccountsRowsAndClearsLatestScroll()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                ScanQueryPageSource source = server.scan(TABLE)) {
            var page = source.getNextSourcePage();
            assertThat(page.getPositionCount()).isEqualTo(1);
            assertThat(INTEGER.getInt(page.getBlock(0), 0)).isEqualTo(42);
            assertThat(source.isFinished()).isTrue();
            source.close();
            assertThat(server.clearBodies).singleElement().asString().contains("next-scroll");
            assertThat(server.diagnostics.getSearchRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getNextPageRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getPagesReturned()).isEqualTo(1);
            assertThat(server.diagnostics.getRemotePagesReceived()).isEqualTo(2);
            assertThat(server.diagnostics.getRowsDecoded()).isEqualTo(1);
            assertThat(server.diagnostics.getSourceBytesDecoded()).isEqualTo("{\"id\":42}".getBytes(UTF_8).length);
            assertThat(server.diagnostics.getRenderedQueries()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
            assertThat(server.diagnostics.getFailures()).isZero();
        }
    }

    @Test
    public void testResponseWithoutScrollIsConsumedOnce()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false, false);
                ScanQueryPageSource source = server.scan(TABLE)) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(source.isFinished()).isTrue();
            source.close();
            assertThat(source.getNextSourcePage()).isNull();
            assertThat(server.requests).containsExactly("POST /events/_search");
            assertThat(server.diagnostics.getCancellations()).isZero();
            assertThat(server.diagnostics.getNextPageRequests()).isZero();
        }
    }

    @Test
    public void testPushedLimitDoesNotFetchOrCountCancellation()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                ScanQueryPageSource source = server.scan(TABLE.withTopN(1, List.of()))) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(source.isFinished()).isTrue();
            source.close();
            assertThat(server.requests).containsExactly("POST /events/_search", "DELETE /_search/scroll");
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testDecodeFailureIsCountedAndScrollIsClosed()
            throws Exception
    {
        try (SearchServer server = new SearchServer("\"invalid\"", false, false);
                ScanQueryPageSource source = server.scan(TABLE)) {
            assertThatThrownBy(source::getNextSourcePage)
                    .isInstanceOf(TrinoException.class)
                    .hasMessageContaining("Cannot parse value");
            assertThat(server.clearBodies).singleElement().asString().contains("initial-scroll");
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
            assertThat(server.diagnostics.getRowsDecoded()).isZero();
        }
    }

    @Test
    public void testPageFailureIsCountedOnceAndCleanupDoesNotMaskIt()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", true, false);
                ScanQueryPageSource source = server.scan(TABLE)) {
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
            assertThat(server.clearBodies).singleElement().asString().contains("initial-scroll");
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getNextPageRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testCountAccounting()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                CountQueryPageSource source = server.count()) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(10_000);
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(source.isFinished()).isTrue();
            assertThat(server.requests).containsExactly("GET /events/_count");
            assertThat(server.diagnostics.getCountRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getPagesReturned()).isEqualTo(2);
            assertThat(server.diagnostics.getRenderedQueries()).isEqualTo(1);
            assertThat(server.diagnostics.getRowsDecoded()).isZero();
            assertThat(server.diagnostics.getFailures()).isZero();
        }
    }

    @Test
    public void testAggregationAccountingAndClose()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                AggregationQueryPageSource source = server.aggregation()) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(source.isFinished()).isTrue();
            source.close();
            assertThat(source.getNextSourcePage()).isNull();
            assertThat(server.diagnostics.getAggregationRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getRemotePagesReceived()).isEqualTo(1);
            assertThat(server.diagnostics.getPagesReturned()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testAggregationCloseBeforeReading()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                AggregationQueryPageSource source = server.aggregation()) {
            source.close();
            source.close();
            assertThat(source.getNextSourcePage()).isNull();
            assertThat(server.requests).isEmpty();
            assertThat(server.diagnostics.getCancellations()).isEqualTo(1);
        }
    }

    @Test
    public void testAggregationFailureEndsExecution()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", true, false);
                AggregationQueryPageSource source = server.aggregation()) {
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
            assertThat(source.isFinished()).isTrue();
            assertThat(source.getNextSourcePage()).isNull();
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getCancellations()).isZero();
        }
    }

    @Test
    public void testCompositePaginationMustProgress()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                AggregationQueryPageSource source = server.aggregation(true)) {
            String buckets = String.join(",", Collections.nCopies(1000, "{\"key\":{\"g0\":1},\"doc_count\":1}"));
            server.aggregationResponse = "{\"aggregations\":{\"groups\":{\"buckets\":[" + buckets + "],\"after_key\":{\"g0\":1}}}}";
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1000);
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class).hasMessageContaining("non-progressing");
            assertThat(source.isFinished()).isTrue();
            assertThat(server.diagnostics.getAggregationRows()).isEqualTo(1000);
            assertThat(server.diagnostics.getAggregationOutputBytes()).isPositive();
        }
    }

    @Test
    public void testCountFailureAccounting()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", true, false)) {
            assertThatThrownBy(server::count).isInstanceOf(TrinoException.class);
            assertThat(server.diagnostics.getCountRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getPagesReturned()).isZero();
        }
    }

    @Test
    public void testClearFailureIsCountedWithoutThrowingOrRetryingClose()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, true);
                ScanQueryPageSource source = server.scan(TABLE)) {
            source.close();
            source.close();
            assertThat(server.clearBodies).hasSize(1);
            assertThat(server.diagnostics.getFailures()).isEqualTo(1);
            assertThat(server.diagnostics.getClearScrollCalls()).isEqualTo(1);
        }
    }

    private static class SearchServer
            implements AutoCloseable
    {
        private final HttpServer server;
        private final ElasticsearchClient client;
        private final ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<String> clearBodies = new CopyOnWriteArrayList<>();
        private volatile String aggregationResponse = "{\"hits\":{\"total\":{\"value\":3,\"relation\":\"eq\"}},\"aggregations\":{}}";

        public SearchServer(String value, boolean failNextPage, boolean failClear)
                throws IOException
        {
            this(value, failNextPage, failClear, true);
        }

        public SearchServer(String value, boolean failNextPage, boolean failClear, boolean includeScroll)
                throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    requests.add(exchange.getRequestMethod() + " " + path);
                    String requestBody = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
                    int status = 200;
                    String body;
                    if (exchange.getRequestMethod().equals("DELETE")) {
                        clearBodies.add(requestBody);
                        status = failClear ? 500 : 200;
                        body = "{}";
                    }
                    else if (path.equals("/events/_search")) {
                        if (requestBody.contains("\"track_total_hits\":true")) {
                            body = aggregationResponse;
                            status = failNextPage ? 500 : 200;
                        }
                        else {
                            String scroll = includeScroll ? "\"_scroll_id\":\"initial-scroll\"," : "";
                            body = "{" + scroll + "\"hits\":{\"hits\":[{\"_id\":\"1\",\"_source\":{\"id\":" + value + "}}]}}";
                        }
                    }
                    else if (path.equals("/events/_count")) {
                        status = failNextPage ? 500 : 200;
                        body = "{\"count\":10001}";
                    }
                    else {
                        status = failNextPage ? 500 : 200;
                        body = "{\"_scroll_id\":\"next-scroll\",\"hits\":{\"hits\":[]}}";
                    }
                    byte[] bytes = body.getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
            client = new ElasticsearchClient(
                    new ElasticsearchConfig()
                            .setHosts(List.of(server.getAddress().getAddress().getHostAddress()))
                            .setPort(server.getAddress().getPort()),
                    Optional.empty(),
                    Optional.empty(),
                    diagnostics);
        }

        public ScanQueryPageSource scan(ElasticsearchTableHandle table)
        {
            return new ScanQueryPageSource(client, TESTING_TYPE_MANAGER, table, new ElasticsearchSplit("events", 0, Optional.empty()), List.of(ID), diagnostics);
        }

        public CountQueryPageSource count()
        {
            return new CountQueryPageSource(client, TABLE, new ElasticsearchSplit("events", 0, Optional.empty()), diagnostics);
        }

        public AggregationQueryPageSource aggregation()
        {
            return aggregation(false);
        }

        public AggregationQueryPageSource aggregation(boolean grouped)
        {
            ElasticsearchTableHandle table = new ElasticsearchTableHandle(
                    SCAN,
                    "default",
                    "events",
                    TABLE.constraint(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Optional.empty(),
                    OptionalLong.empty(),
                    List.of(),
                    Set.of(ID),
                    Optional.of(new ElasticsearchAggregation(grouped ? List.of(ID) : List.of(), List.of(new ElasticsearchAggregate("id", ElasticsearchAggregate.Function.COUNT_ALL, Optional.empty(), INTEGER)))));
            return new AggregationQueryPageSource(client, table, List.of(ID), diagnostics);
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
