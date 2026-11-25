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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.sun.net.httpserver.HttpServer;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.base.TypeDeserializer;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.spi.Page;
import io.trino.spi.TrinoException;
import io.trino.spi.block.ArrayBlock;
import io.trino.spi.block.RowBlock;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_INVALID_METADATA;
import static io.trino.spi.StandardErrorCode.TYPE_MISMATCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TestElasticsearchInferredMetadata
{
    private static final JsonMapper JSON = new JsonMapperProvider().get();
    private static final String PROPERTIES =
            """
            "properties":{
              "users":{"type":"object","properties":{
                "city":{"type":"keyword"},
                "license_plate":{"type":"keyword"},
                "name":{"type":"keyword"}}},
              "flag":{"type":"keyword"},
              "age":{"type":"long"},
              "empty_age":{"type":"long"},
              "address":{"properties":{"updated":{"type":"date"}}},
              "ignored_age":{"type":"long","ignore_malformed":true},
              "local":{"type":"keyword","index":false},
              "alias_age":{"type":"long","doc_values":false},
              "published":{"type":"boolean"},
              "boolean_source":{"type":"boolean","doc_values":false},
              "occurred":{"type":"date","format":"yyyy/MM/dd HH:mm:ss||epoch_second"},
              "price":{"type":"scaled_float","scaling_factor":100},
              "ratio":{"type":"float"},
              "half_ratio":{"type":"half_float"}
            }
            """;

    @Test
    void testLazySamplingMappingRefreshAndRead()
            throws Exception
    {
        try (Server server = new Server(true)) {
            assertThat(server.samples).isEmpty();

            server.metadata.beginQuery(server.session);
            var table = server.metadata.getTableHandle(server.session, new SchemaTableName("default", "events"), Optional.empty(), Optional.empty());
            assertThat(table).isNotNull();
            assertThat(server.samples).hasSize(2);
            assertThat(server.samples).allSatisfy(request -> assertThat(request.path("size").asInt()).isEqualTo(3));
            assertThat(server.samples.get(0).at("/query/match_all").isObject()).isTrue();
            assertThat(server.samples.get(1).at("/query/exists/field").asText()).isEqualTo("users.license_plate");
            Map<String, ColumnHandle> columns = server.metadata.getColumnHandles(server.session, table);
            ElasticsearchColumnHandle users = (ElasticsearchColumnHandle) columns.get("users");
            ElasticsearchColumnHandle flag = (ElasticsearchColumnHandle) columns.get("flag");
            assertThat(users.type()).isEqualTo(new ArrayType(RowType.rowType(
                    RowType.field("city", VARCHAR),
                    RowType.field("license_plate", new ArrayType(new ArrayType(VARCHAR))),
                    RowType.field("name", VARCHAR))));
            assertThat(flag.supportsPredicates()).isTrue();
            assertThat(users.inferred()).isTrue();

            var mapper = new JsonMapperProvider().get();
            mapper.registerModule(new SimpleModule().addDeserializer(Type.class, new TypeDeserializer(TESTING_TYPE_MANAGER)));
            var codec = new JsonCodecFactory(mapper).jsonCodec(ElasticsearchColumnHandle.class);
            assertThat(codec.fromJson(codec.toJson(users))).isEqualTo(users);
            assertThat(server.readDocuments(table, List.of(users, flag))).satisfies(page -> {
                assertThat(page.getPositionCount()).isEqualTo(1);
                RowBlock row = (RowBlock) ((ArrayBlock) page.getBlock(0)).getArray(0);
                assertThat(VARCHAR.getSlice(row.getFieldBlock(0), 0).toStringUtf8()).isEqualTo("[\"Huế\"]");
                assertThat(VARCHAR.getSlice(page.getBlock(1), 0).toStringUtf8()).isEqualTo("true");
            });
            server.drift.set(true);
            TrinoException failure = catchThrowableOfType(TrinoException.class, () -> server.readDocuments(table, List.of(users)));
            assertThat(failure.getErrorCode()).isEqualTo(TYPE_MISMATCH.toErrorCode());
            assertThat(failure).hasMessageContainingAll("events", "document '4'", "users.name", "ARRAY", "_meta.trino");

            server.explicit.set(true);
            assertThat(server.metadata.getColumnHandles(server.session, table).get("users")).isEqualTo(users);
            server.metadata.cleanupQuery(server.session);

            ElasticsearchColumnHandle explicit = (ElasticsearchColumnHandle) server.metadata.getColumnHandles(server.session, table).get("users");
            assertThat(explicit.inferred()).isFalse();
            assertThat(explicit.type().getDisplayName()).startsWith("row(");
            assertThat(server.samples).hasSize(2);
        }
    }

    @Test
    void testMappedScalarsUseIndexedValuesAndPushRange()
            throws Exception
    {
        try (Server server = new Server(true)) {
            var table = server.metadata.getTableHandle(server.session, new SchemaTableName("default", "events"), Optional.empty(), Optional.empty());
            assertThat(table).isNotNull();
            Map<String, ColumnHandle> columns = server.metadata.getColumnHandles(server.session, table);
            ElasticsearchColumnHandle age = (ElasticsearchColumnHandle) columns.get("age");
            ElasticsearchColumnHandle ignored = (ElasticsearchColumnHandle) columns.get("ignored_age");
            ElasticsearchColumnHandle empty = (ElasticsearchColumnHandle) columns.get("empty_age");
            ElasticsearchColumnHandle address = (ElasticsearchColumnHandle) columns.get("address");
            assertThat(age.type()).isEqualTo(BIGINT);
            assertThat(age.inferred()).isTrue();
            assertThat(age.supportsPredicates()).isTrue();
            assertThat(ignored.supportsPredicates()).isTrue();
            assertThat(((ElasticsearchColumnHandle) columns.get("local")).supportsPredicates()).isFalse();
            assertThat(((ElasticsearchColumnHandle) columns.get("alias_age")).supportsPredicates()).isFalse();

            Domain range = Domain.create(ValueSet.ofRanges(Range.greaterThan(BIGINT, 1L)), false);
            var filtered = server.metadata.applyFilter(
                    server.session,
                    table,
                    new Constraint(TupleDomain.withColumnDomains(Map.of(age, range)))).orElseThrow();
            assertThat(filtered.getRemainingFilter().isAll()).isTrue();

            assertThat(server.readDocuments((ElasticsearchTableHandle) filtered.getHandle(), List.of(age, ignored, empty, address))).satisfies(page -> {
                assertThat(BIGINT.getLong(page.getBlock(0), 0)).isEqualTo(2);
                assertThat(page.getBlock(1).isNull(0)).isTrue();
                assertThat(page.getBlock(2).isNull(0)).isTrue();
                RowBlock addressRow = (RowBlock) page.getBlock(3);
                assertThat(TIMESTAMP_MILLIS.getLong(addressRow.getFieldBlock(0), 0)).isEqualTo(3_600_000_000L);
            });
            assertThat(server.scanRequests.getLast().path("docvalue_fields").toString()).isEqualTo("[\"age\",\"ignored_age\",\"empty_age\"]");
            assertThat(server.scanRequests.getLast().path("query").toString()).contains("\"range\"", "\"age\"");

            server.scalarDrift.set(true);
            TrinoException failure = catchThrowableOfType(TrinoException.class, () -> server.readDocuments(table, List.of(age)));
            assertThat(failure.getErrorCode()).isEqualTo(TYPE_MISMATCH.toErrorCode());
            assertThat(failure).hasMessageContainingAll("events", "document '4'", "age", "ARRAY", "_meta.trino");
        }
    }

    @Test
    void testBooleanAndFormattedDateUseMappedSemantics()
            throws Exception
    {
        try (Server server = new Server(true)) {
            var table = server.metadata.getTableHandle(server.session, new SchemaTableName("default", "events"), Optional.empty(), Optional.empty());
            var columns = server.metadata.getColumnHandles(server.session, table);
            var published = (ElasticsearchColumnHandle) columns.get("published");
            var source = (ElasticsearchColumnHandle) columns.get("boolean_source");
            var occurred = (ElasticsearchColumnHandle) columns.get("occurred");
            assertThat(published.type()).isEqualTo(BOOLEAN);
            assertThat(source.type()).isEqualTo(BOOLEAN);
            assertThat(occurred.type()).isEqualTo(TIMESTAMP_MILLIS);

            var filter = server.metadata.applyFilter(server.session, table,
                    new Constraint(TupleDomain.withColumnDomains(Map.of(
                            published, Domain.singleValue(BOOLEAN, true),
                            source, Domain.singleValue(BOOLEAN, false),
                            occurred, Domain.singleValue(TIMESTAMP_MILLIS, 1735689600000000L))))).orElseThrow();
            assertThat(filter.getRemainingFilter().isAll()).isTrue();
            var page = server.readDocuments((ElasticsearchTableHandle) filter.getHandle(), List.of(published, source, occurred));
            assertThat(BOOLEAN.getBoolean(page.getBlock(0), 0)).isTrue();
            assertThat(BOOLEAN.getBoolean(page.getBlock(1), 0)).isFalse();
            assertThat(TIMESTAMP_MILLIS.getLong(page.getBlock(2), 0)).isEqualTo(1735689600000000L);
            var request = server.scanRequests.getLast();
            assertThat(request.path("docvalue_fields").toString()).isEqualTo(
                    """
                    ["published",{"field":"occurred","format":"epoch_millis"}]""");
            assertThat(request.path("query").toString()).contains(
                    """
                    "term":{"published":true}""",
                    """
                    "term":{"boolean_source":false}""",
                    """
                    "occurred":{"gte":"1735689600000","lte":"1735689600000","format":"epoch_millis"}""");
        }
    }

    @Test
    void testMappedFloatingPointReadAndNativeRange()
            throws Exception
    {
        try (Server server = new Server(true)) {
            var table = server.metadata.getTableHandle(server.session, new SchemaTableName("default", "events"), Optional.empty(), Optional.empty());
            var columns = server.metadata.getColumnHandles(server.session, table);
            var price = (ElasticsearchColumnHandle) columns.get("price");
            var ratio = (ElasticsearchColumnHandle) columns.get("ratio");
            var halfRatio = (ElasticsearchColumnHandle) columns.get("half_ratio");
            assertThat(price.type()).isEqualTo(DOUBLE);
            assertThat(ratio.type()).isEqualTo(REAL);
            assertThat(halfRatio.type()).isEqualTo(DOUBLE);
            var filter = server.metadata.applyFilter(
                    server.session,
                    table,
                    new Constraint(TupleDomain.withColumnDomains(Map.of(
                            price, Domain.singleValue(DOUBLE, 2.34),
                            ratio, Domain.singleValue(REAL, (long) Float.floatToRawIntBits(0.1f)))))).orElseThrow();
            assertThat(filter.getRemainingFilter().isAll()).isTrue();
            var page = server.readDocuments((ElasticsearchTableHandle) filter.getHandle(), List.of(price, ratio, halfRatio));
            assertThat(DOUBLE.getDouble(page.getBlock(0), 0)).isEqualTo(2.34);
            assertThat(Float.intBitsToFloat(toIntExact(REAL.getLong(page.getBlock(1), 0)))).isEqualTo(0.1f);
            assertThat(DOUBLE.getDouble(page.getBlock(2), 0)).isEqualTo((double) Float.float16ToFloat(Float.floatToFloat16(0.1f)));
            var request = server.scanRequests.getLast();
            assertThat(request.path("query").toString()).contains(
                    """
                    "price":{"gte":2.335,"lte":2.345}""",
                    """
                    "term":{"ratio":0.1}""");
        }
    }

    @Test
    void testBatchedMsearchSamplingAndKeywordStatistics()
            throws Exception
    {
        try (Server server = new Server(true)) {
            server.metadata.beginQuery(server.session);
            try {
                var sparseTable = server.metadata.getTableHandle(server.session, new SchemaTableName("default", "sparse"), Optional.empty(), Optional.empty());
                var columns = server.metadata.getColumnHandles(server.session, sparseTable);
                var b = (ElasticsearchColumnHandle) columns.get("b");
                assertThat(((ElasticsearchColumnHandle) columns.get("a")).type()).isEqualTo(BIGINT);
                assertThat(b.type()).isEqualTo(VARCHAR);
                assertThat(server.msearchQueries).containsExactly("max_concurrent_searches=2");
                assertThat(server.msearchBodies)
                        .singleElement()
                        .satisfies(body -> assertThat(body).contains("{\"allow_partial_search_results\":false}\n"));

                var filtered = server.metadata.applyFilter(
                        server.session,
                        sparseTable,
                        new Constraint(TupleDomain.withColumnDomains(Map.of(b, Domain.singleValue(VARCHAR, utf8Slice("ok")))))).orElseThrow();
                var stats = server.metadata.getTableStatistics(server.session, filtered.getHandle());
                assertThat(stats.getColumnStatistics()).containsKey(b);
            }
            finally {
                server.metadata.cleanupQuery(server.session);
            }

            server.msearchFail.set(true);
            TrinoException failure = catchThrowableOfType(TrinoException.class, () -> server.client.getIndexMetadata("sparse_fail"));
            assertThat(failure.getErrorCode()).isEqualTo(ELASTICSEARCH_INVALID_METADATA.toErrorCode());
        }
    }

    @Test
    void testExplicitAndDisabledInferenceSkipSampling()
            throws Exception
    {
        try (Server server = new Server(false)) {
            assertThat(server.client.getIndexMetadata("events").schema().fields().getFirst().inferredMapping()).isEmpty();
            assertThat(server.samples).isEmpty();
        }
        try (Server server = new Server(true)) {
            server.explicit.set(true);
            assertThat(server.client.getIndexMetadata("events").schema().fields().getFirst().inferredMapping()).isEmpty();
            assertThat(server.samples).isEmpty();
        }
    }

    @Test
    void testEmptyAndFailedIndexSamplesAreIsolated()
            throws Exception
    {
        try (Server server = new Server(true)) {
            var empty = server.client.getIndexMetadata("empty").schema().fields().getFirst();
            assertThat(empty.asRawJson()).isFalse();
            assertThat(empty.inferredMapping()).isEmpty();
            server.populated.set(true);
            var populated = server.client.getIndexMetadata("empty").schema().fields().getFirst();
            assertThat(populated.isArray()).isTrue();
            assertThat(populated.inferredMapping()).isPresent();
            TrinoException failure = catchThrowableOfType(TrinoException.class, () -> server.client.getIndexMetadata("failed"));
            assertThat(failure.getErrorCode()).isEqualTo(ELASTICSEARCH_INVALID_METADATA.toErrorCode());
            assertThat(server.client.getIndexMetadata("events").schema().fields().getFirst().isArray()).isTrue();
            assertThat(server.samples).hasSize(2);
        }
    }

    private static final class Server
            implements AutoCloseable
    {
        private final HttpServer server;
        private final ElasticsearchClient client;
        private final ElasticsearchMetadata metadata;
        private final TestingConnectorSession session;
        private final AtomicBoolean populated = new AtomicBoolean();
        private final AtomicBoolean explicit = new AtomicBoolean();
        private final AtomicBoolean drift = new AtomicBoolean();
        private final AtomicBoolean scalarDrift = new AtomicBoolean();
        private final AtomicBoolean msearchFail = new AtomicBoolean();
        private final List<JsonNode> samples = new CopyOnWriteArrayList<>();
        private final List<String> msearchQueries = new CopyOnWriteArrayList<>();
        private final List<String> msearchBodies = new CopyOnWriteArrayList<>();
        private final List<JsonNode> scanRequests = new CopyOnWriteArrayList<>();

        private Server(boolean enabled)
                throws IOException
        {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    String response = switch (path) {
                        case "/_cat/indices" -> """
                                                [{"index":"empty","docs.count":"0","docs.deleted":"0"},
                                                 {"index":"failed","docs.count":"1","docs.deleted":"0"},
                                                 {"index":"events","docs.count":"4","docs.deleted":"0"}]
                                                """;
                        case "/empty" -> """
                                         {"empty":{"settings":{"index.uuid":"empty"},"mappings":{"properties":{"age":{"type":"long"}}}}}
                                         """;
                        case "/failed" -> """
                                          {"failed":{"settings":{"index.uuid":"failed"},"mappings":{"properties":{"age":{"type":"long"}}}}}
                                          """;
                        case "/sparse", "/sparse/_mappings" -> """
                                                               {"sparse":{"settings":{"index.uuid":"sparse"},"mappings":{"properties":{"a":{"type":"long"},"b":{"type":"keyword"}}}}}
                                                               """;
                        case "/sparse_fail", "/sparse_fail/_mappings" -> """
                                                                         {"sparse_fail":{"settings":{"index.uuid":"sparse_fail"},"mappings":{"properties":{"a":{"type":"long"},"b":{"type":"keyword"}}}}}
                                                                         """;
                        case "/empty/_search" -> populated.get()
                                ? """
                                  {"hits":{"hits":[{"_id":"1","_source":{"age":[1]}}]}}
                                  """
                                : """
                                  {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[]}}
                                  """;
                        case "/failed/_search" -> """
                                                  {"timed_out":true,"_shards":{"failed":0},"hits":{"hits":[]}}
                                                  """;
                        case "/sparse/_search", "/sparse_fail/_search" -> {
                            JsonNode request = JSON.readTree(exchange.getRequestBody());
                            if (request.path("size").asInt(-1) == 0) {
                                yield """
                                      {"timed_out":false,"_shards":{"failed":0},"hits":{"total":{"value":2,"relation":"eq"}},"aggregations":{
                                        "f0_card":{"value":2},
                                        "f0_count":{"value":2}
                                      }}
                                      """;
                            }
                            yield """
                                  {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[{"_id":"1","_source":{}}]}}
                                  """;
                        }
                        case "/sparse/_msearch", "/sparse_fail/_msearch" -> {
                            if (!msearchFail.get()) {
                                msearchQueries.add(exchange.getRequestURI().getRawQuery());
                                msearchBodies.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                                yield """
                                      {"responses":[
                                        {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[{"_id":"2","_source":{"a":1}}]}},
                                        {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[{"_id":"3","_source":{"b":"ok"}}]}}
                                      ]}
                                      """;
                            }
                            yield """
                                  {"responses":[
                                    {"timed_out":false,"_shards":{"failed":0},"hits":{"hits":[{"_id":"2","_source":{"a":1}}]}},
                                    {"timed_out":true,"_shards":{"failed":0},"hits":{"hits":[]}}
                                  ]}
                                  """;
                        }
                        case "/events", "/events/_mappings" -> """
                                                               {"events":{"settings":{"index.uuid":"a"},"mappings":{%s%s}}}
                                                               """.formatted(explicit.get() ? "\"_meta\":{\"trino\":{}}, " : "", PROPERTIES);
                        case "/events/_search" -> {
                            JsonNode request = JSON.readTree(exchange.getRequestBody());
                            if (request.path("size").asInt(-1) == 0) {
                                yield """
                                      {"timed_out":false,"_shards":{"failed":0},"hits":{"total":{"value":2,"relation":"eq"}},"aggregations":{
                                        "f0_card":{"value":2},
                                        "f0_count":{"value":2}
                                      }}
                                      """;
                            }
                            if (exchange.getRequestURI().getRawQuery() != null && exchange.getRequestURI().getRawQuery().contains("scroll=")) {
                                scanRequests.add(request);
                                yield """
                                      {"hits":{"hits":[{"_id":"4","fields":{"age":[2],"flag":["true"],"published":[true],
                                            "occurred":["1735689600000"],"price":[2.34],"ratio":[0.10000000149011612],"half_ratio":[0.0999755859375]},
                                        "_source":{"flag":true,"age":%s,"ignored_age":"N/A","empty_age":"","address":{"updated":"1970-01-01T02:00:00+0100"},"published":"true","boolean_source":"",
                                            "occurred":"2025/01/01 00:00:00","price":2.341,"ratio":0.1,"half_ratio":0.1,"users":[
                                        {"name":%s,"city":["Huế"],"license_plate":[["29"],["A54321"]]}]}}]}}
                                      """.formatted(scalarDrift.get() ? "[2]" : "\"2.5\"", drift.get() ? "[\"Bình\"]" : "\"Bình\"");
                            }
                            samples.add(request);
                            if (request.path("query").has("match_all")) {
                                yield """
                                      {"hits":{"hits":[
                                        {"_id":"1","_source":{"users":[{"name":"An","city":"Hà Nội"}],"flag":true,"age":1,"ignored_age":1,"empty_age":"","address":{"updated":0},"local":"hit","alias_age":1,
                                          "published":true,"boolean_source":false,"occurred":"2025/01/01 00:00:00","price":1,"ratio":1,"half_ratio":1}},
                                        {"_id":"2","_source":{"users":[{"name":"Bình","city":["Huế"]}],"flag":"false","age":"02","ignored_age":"N/A","local":"miss","alias_age":"02",
                                          "published":"false","boolean_source":"","occurred":1735689600,"price":2.341,"ratio":0.1,"half_ratio":0.1}},
                                        {"_id":"3","_source":{"users":[]}}]}}
                                      """;
                            }
                            yield """
                                  {"hits":{"hits":[{"_id":"4","_source":{"users":[{"name":"Bình","license_plate":[["29"],["A54321"]]}]}}]}}
                                  """;
                        }
                        default -> throw new IllegalArgumentException("Unexpected test request: " + path);
                    };
                    byte[] body = response.getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();

            ElasticsearchConfig config = new ElasticsearchConfig()
                    .setHosts(List.of(server.getAddress().getAddress().getHostAddress()))
                    .setPort(server.getAddress().getPort())
                    .setSchemaInferenceEnabled(enabled);
            client = new ElasticsearchClient(config, Optional.empty(), Optional.empty());
            metadata = new RuleBasedElasticsearchMetadata(TESTING_TYPE_MANAGER, client, config);
            session = TestingConnectorSession.builder().setPropertyMetadata(new ElasticsearchSessionProperties(config).getSessionProperties()).build();
        }

        private Page readDocuments(ElasticsearchTableHandle table, List<ElasticsearchColumnHandle> columns)
        {
            try (ScanQueryPageSource pageSource = new ScanQueryPageSource(
                    client,
                    TESTING_TYPE_MANAGER,
                    table,
                    new ElasticsearchSplit("events", 0, Optional.empty()),
                    columns)) {
                return pageSource.getNextSourcePage().getPage();
            }
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
