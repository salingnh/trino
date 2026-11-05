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
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.IndexMetadata.DateTimeType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.BigintDecoder;
import io.trino.plugin.elasticsearch.decoders.BooleanDecoder;
import io.trino.plugin.elasticsearch.decoders.DoubleDecoder;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.TimestampDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Term;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import io.trino.testing.TestingConnectorSession;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
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
    public void testDynamicFilterPreservesPredicateLimitSortAndProjection()
            throws Exception
    {
        for (List<ElasticsearchColumnSort> sort : List.of(List.<ElasticsearchColumnSort>of(), List.of(new ElasticsearchColumnSort("id", false, false)))) {
            try (SearchServer server = new SearchServer("42", false, false)) {
                var table = withRemotePredicate(TABLE.withTopN(1, sort), Optional.of(new Term("status", "active")));
                var provider = new ElasticsearchPageSourceProvider(server.client, TESTING_TYPE_MANAGER, new ElasticsearchConfig(), server.diagnostics);
                try (var source = provider.createPageSource(
                        ElasticsearchTransactionHandle.INSTANCE,
                        TestingConnectorSession.SESSION,
                        new ElasticsearchSplit("events", 0, Optional.empty()),
                        table,
                        Optional.empty(),
                        List.of(ID),
                        dynamicFilter(TupleDomain.withColumnDomains(Map.of(ID, Domain.singleValue(INTEGER, 42L)))))) {
                    assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
                    assertThat(source.isFinished()).isTrue();
                    assertThat(server.requests).containsExactly("POST /events/_search", "DELETE /_search/scroll");
                    var request = JsonMapper.builder().build().readTree(server.searchBodies.getFirst());
                    assertThat(request.path("size").asInt()).isEqualTo(1);
                    assertThat(request.path("query").toString()).contains("status", "active", "id", "42");
                    assertThat(request.path("_source").toString()).contains("id");
                    if (!sort.isEmpty()) {
                        assertThat(request.path("sort").get(0).path("id").path("order").asText()).isEqualTo("desc");
                    }
                    assertThat(server.diagnostics.getDynamicFilterPredicatesPushed()).isEqualTo(1);
                    assertThat(server.diagnostics.getCancellations()).isZero();
                }
            }
        }
    }

    private static DynamicFilter dynamicFilter(TupleDomain<ColumnHandle> predicate)
    {
        return new DynamicFilter()
        {
            @Override
            public Set<ColumnHandle> getColumnsCovered()
            {
                return Set.of(ID);
            }

            @Override
            public CompletableFuture<?> isBlocked()
            {
                return NOT_BLOCKED;
            }

            @Override
            public boolean isComplete()
            {
                return true;
            }

            @Override
            public boolean isAwaitable()
            {
                return false;
            }

            @Override
            public TupleDomain<ColumnHandle> getCurrentPredicate()
            {
                return predicate;
            }
        };
    }

    @Test
    public void testHitFailureClosesLaterScrollContext()
            throws Exception
    {
        for (ElasticsearchConfig.ResponseDecoder decoder : ElasticsearchConfig.ResponseDecoder.values()) {
            try (SearchServer server = new SearchServer("42", false, false, true, decoder)) {
                server.initialSearchResponse = "{\"hits\":{\"hits\":[{\"_id\":\"1\",\"_source\":42}]},\"_scroll_id\":\"newest\"}";
                assertThatThrownBy(() -> server.scan(TABLE)).isInstanceOf(IllegalArgumentException.class);
                assertThat(server.clearBodies).singleElement().asString().contains("newest");
                assertThat(server.requests).containsExactly("POST /events/_search", "DELETE /_search/scroll");
                assertThat(server.diagnostics.getFailures()).isEqualTo(1);
                assertThat(server.diagnostics.getCancellations()).isZero();
            }
        }
    }

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
    public void testLogicalEndpointRetainsConcreteSplitRestriction()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                ScanQueryPageSource source = new ScanQueryPageSource(
                        server.client,
                        TESTING_TYPE_MANAGER,
                        TABLE.withTopN(1, List.of(new ElasticsearchColumnSort("id", true, false))),
                        new ElasticsearchSplit("events-0001", 0, Optional.empty()),
                        List.of(ID),
                        server.diagnostics)) {
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1);
            assertThat(server.requests.getFirst()).isEqualTo("POST /events/_search");
            assertThat(server.searchBodies.getFirst()).contains("\"_index\":\"events-0001\"", "\"sort\"");
        }
    }

    @Test
    public void testGlobalAverageDoesNotDecodeSourceDocuments()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false)) {
            var avg = new ElasticsearchColumnHandle(
                    List.of("avg_result"),
                    DOUBLE,
                    new PrimitiveType("double"),
                    new DoubleDecoder.Descriptor("avg_result"),
                    false);
            var table = new ElasticsearchTableHandle(
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
                    Set.of(avg),
                    Optional.of(new ElasticsearchAggregation(
                            List.of(),
                            List.of(new ElasticsearchAggregate("avg_result", ElasticsearchAggregate.Function.AVG, Optional.of("id"), avg.type())))));
            server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"total\":{\"value\":50000,\"relation\":\"eq\"}},\"aggregations\":{\"avg_result\":{\"value\":-0.5}}}";
            try (var source = new AggregationQueryPageSource(server.client, table, List.of(avg), server.diagnostics, 10_000)) {
                var page = source.getNextSourcePage();
                assertThat(page.getPositionCount()).isEqualTo(1);
                assertThat(avg.type().getDouble(page.getBlock(0), 0)).isEqualTo(-0.5);
                assertThat(source.getNextSourcePage()).isNull();
            }
            var request = JsonMapper.builder().build().readTree(server.searchBodies.getFirst());
            assertThat(request.path("size").asInt()).isZero();
            assertThat(request.path("aggs").path("avg_result").path("avg").path("field").asText()).isEqualTo("id");
            assertThat(server.diagnostics.getAggregationRequests()).isEqualTo(1);
            assertThat(server.diagnostics.getAggregationRows()).isEqualTo(1);
            assertThat(server.diagnostics.getAggregationOutputBytes()).isPositive();
            assertThat(server.diagnostics.getRowsDecoded()).isZero();
            assertThat(server.diagnostics.getSourceBytesDecoded()).isZero();
        }
    }

    @Test
    public void testAggregationWithoutProjectedColumnsPreservesRowCount()
            throws Exception
    {
        for (boolean grouped : List.of(false, true)) {
            try (SearchServer server = new SearchServer("42", false, false);
                    var source = server.aggregation(grouped, 2, List.of())) {
                if (grouped) {
                    server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[{\"key\":{\"g0\":1},\"doc_count\":3}]}}}";
                }
                var page = source.getNextSourcePage();
                assertThat(page.getPositionCount()).isEqualTo(1);
                assertThat(page.getChannelCount()).isZero();
                assertThat(source.isFinished()).isTrue();
                assertThat(source.getNextSourcePage()).isNull();
            }
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
    public void testAggregationRejectsMissingCompletionMetadata()
            throws Exception
    {
        try (SearchServer server = new SearchServer("42", false, false);
                AggregationQueryPageSource source = server.aggregation()) {
            server.aggregationResponse = "{\"hits\":{\"total\":{\"relation\":\"eq\",\"value\":1}}}";
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
            assertThat(source.isFinished()).isTrue();
            assertThat(source.getNextSourcePage()).isNull();
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
            server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[" + buckets + "],\"after_key\":{\"g0\":1}}}}";
            assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(1000);
            assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class).hasMessageContaining("non-progressing");
            assertThat(source.isFinished()).isTrue();
            assertThat(server.diagnostics.getAggregationRows()).isEqualTo(1000);
            assertThat(server.diagnostics.getAggregationOutputBytes()).isPositive();
        }
    }

    @Test
    public void testTypedCompositeAfterKeyAndNullBucket()
            throws Exception
    {
        var time = new ElasticsearchColumnHandle(List.of("time"), TIMESTAMP_MILLIS, new DateTimeType(List.of()), new TimestampDecoder.Descriptor("time"), true);
        var flag = new ElasticsearchColumnHandle(List.of("flag"), BOOLEAN, new PrimitiveType("boolean"), new BooleanDecoder.Descriptor("flag"), true);
        var key = new ElasticsearchColumnHandle(List.of("key"), BIGINT, new PrimitiveType("long"), new BigintDecoder.Descriptor("key"), true);
        var columns = List.of(time, flag, key);
        var table = new ElasticsearchTableHandle(
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
                Set.copyOf(columns),
                Optional.of(new ElasticsearchAggregation(columns, List.of())));
        try (SearchServer server = new SearchServer("42", false, false);
                var source = new AggregationQueryPageSource(server.client, table, columns, server.diagnostics, 1)) {
            String after = "{\"g0\":1234,\"g1\":true,\"g2\":9007199254740993}";
            server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[{\"key\":" + after + ",\"doc_count\":1}],\"after_key\":" + after + "}}}";
            var page = source.getNextSourcePage();
            assertThat(TIMESTAMP_MILLIS.getLong(page.getBlock(0), 0)).isEqualTo(1_234_000);
            assertThat(BOOLEAN.getBoolean(page.getBlock(1), 0)).isTrue();
            assertThat(BIGINT.getLong(page.getBlock(2), 0)).isEqualTo(9007199254740993L);
            server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[{\"key\":{\"g0\":null,\"g1\":null,\"g2\":null},\"doc_count\":1}]}}}";
            var nullPage = source.getNextSourcePage();
            for (int channel = 0; channel < columns.size(); channel++) {
                assertThat(nullPage.getBlock(channel).isNull(0)).isTrue();
            }
            var json = JsonMapper.builder().build();
            var request = json.readTree(server.searchBodies.getLast());
            assertThat(request.path("aggs").path("groups").path("composite").path("after")).isEqualTo(json.readTree(after));
            assertThat(source.isFinished()).isTrue();
        }
    }

    @Test
    public void testGlobalAggregationRequiresExactNonnegativeHitCount()
            throws Exception
    {
        for (String total : List.of(
                "{\"value\":1,\"relation\":\"gte\"}",
                "{\"value\":-1,\"relation\":\"eq\"}",
                "{\"value\":1.5,\"relation\":\"eq\"}",
                "{\"value\":\"1\",\"relation\":\"eq\"}",
                "{\"value\":9223372036854775808,\"relation\":\"eq\"}",
                "{}")) {
            try (SearchServer server = new SearchServer("42", false, false);
                    var source = server.aggregation()) {
                server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"total\":" + total + "},\"aggregations\":{}}";
                assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
                assertThat(source.getNextSourcePage()).isNull();
                assertThat(server.searchBodies).hasSize(1);
            }
        }
    }

    @Test
    public void testConfiguredCompositePageSize()
            throws Exception
    {
        for (int size : List.of(1, 2, 1000, 10_000)) {
            try (SearchServer server = new SearchServer("42", false, false);
                    AggregationQueryPageSource source = server.aggregation(true, size)) {
                String bucket = "{\"key\":{\"g0\":1},\"doc_count\":1}";
                server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[" +
                        String.join(",", Collections.nCopies(size, bucket)) + "],\"after_key\":{\"g0\":1}}}}";
                assertThat(source.getNextSourcePage().getPositionCount()).isEqualTo(size);
                assertThat(source.isFinished()).isFalse();
                server.aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"aggregations\":{\"groups\":{\"buckets\":[]}}}";
                assertThat(source.getNextSourcePage().getPositionCount()).isZero();
                assertThat(source.isFinished()).isTrue();
                assertThat(server.searchBodies).hasSize(2);
                var request = JsonMapper.builder().build().readTree(server.searchBodies.getLast());
                assertThat(request.path("aggs").path("groups").path("composite").path("size").asInt()).isEqualTo(size);
                assertThat(request.path("aggs").path("groups").path("composite").path("after").path("g0").isIntegralNumber()).isTrue();
            }
        }
    }

    @Test
    public void testAggregationRejectsInvalidCompletion()
            throws Exception
    {
        for (String metadata : List.of(
                "\"timed_out\":true,\"_shards\":{\"failed\":0}",
                "\"timed_out\":false,\"_shards\":{\"failed\":1}",
                "\"timed_out\":false,\"_shards\":{\"failed\":4294967296}",
                "\"timed_out\":\"false\",\"_shards\":{\"failed\":0}")) {
            try (SearchServer server = new SearchServer("42", false, false);
                    AggregationQueryPageSource source = server.aggregation()) {
                server.aggregationResponse = "{" + metadata + ",\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"}},\"aggregations\":{}}";
                assertThatThrownBy(source::getNextSourcePage).isInstanceOf(TrinoException.class);
                assertThat(source.getNextSourcePage()).isNull();
                assertThat(server.searchBodies).hasSize(1);
            }
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
        private final List<String> searchBodies = new CopyOnWriteArrayList<>();
        private volatile String aggregationResponse = "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"total\":{\"value\":3,\"relation\":\"eq\"}},\"aggregations\":{}}";
        private volatile String initialSearchResponse;

        public SearchServer(String value, boolean failNextPage, boolean failClear)
                throws IOException
        {
            this(value, failNextPage, failClear, true);
        }

        public SearchServer(String value, boolean failNextPage, boolean failClear, boolean includeScroll)
                throws IOException
        {
            this(value, failNextPage, failClear, includeScroll, ElasticsearchConfig.ResponseDecoder.MATERIALIZED);
        }

        public SearchServer(String value, boolean failNextPage, boolean failClear, boolean includeScroll, ElasticsearchConfig.ResponseDecoder decoder)
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
                        searchBodies.add(requestBody);
                        if (requestBody.contains("\"track_total_hits\":true")) {
                            body = aggregationResponse;
                            status = failNextPage ? 500 : 200;
                        }
                        else {
                            String scroll = includeScroll ? "\"_scroll_id\":\"initial-scroll\"," : "";
                            body = "{" + scroll + "\"hits\":{\"hits\":[{\"_id\":\"1\",\"_source\":{\"id\":" + value + "}}]}}";
                            if (initialSearchResponse != null) {
                                body = initialSearchResponse;
                            }
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
                            .setResponseDecoder(decoder)
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
            return aggregation(grouped, 1000);
        }

        public AggregationQueryPageSource aggregation(boolean grouped, int size)
        {
            return aggregation(grouped, size, List.of(ID));
        }

        public AggregationQueryPageSource aggregation(boolean grouped, int size, List<ElasticsearchColumnHandle> outputs)
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
            return new AggregationQueryPageSource(client, table, outputs, diagnostics, size);
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
