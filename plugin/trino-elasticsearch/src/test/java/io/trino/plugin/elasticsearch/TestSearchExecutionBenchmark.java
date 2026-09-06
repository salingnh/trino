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

import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.SearchDocument;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static com.google.common.io.Resources.getResource;
import static io.trino.plugin.elasticsearch.ElasticsearchServer.ELASTICSEARCH_7_IMAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchServer.ELASTICSEARCH_8_IMAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "elasticsearch.benchmark", matches = "true")
public class TestSearchExecutionBenchmark
{
    private static final Logger LOG = Logger.get(TestSearchExecutionBenchmark.class);
    private static final int DOCUMENTS = 5000;

    @Test
    public void compareStrategiesOnMultipleShards()
            throws Exception
    {
        for (String image : List.of(ELASTICSEARCH_7_IMAGE, ELASTICSEARCH_8_IMAGE)) {
            try (ElasticsearchServer server = new ElasticsearchServer(image); var rest = server.getClient()) {
                Request create = new Request("PUT", "/scan_benchmark");
                create.setJsonEntity("{\"settings\":{\"number_of_shards\":3,\"number_of_replicas\":0}}");
                rest.performRequest(create);
                StringBuilder bulk = new StringBuilder();
                for (int id = 0; id < DOCUMENTS; id++) {
                    bulk.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
                    bulk.append("{\"id\":").append(id).append(",\"text\":\"").append("ngô văn ".repeat(100)).append("\"}\n");
                }
                Request load = new Request("POST", "/scan_benchmark/_bulk");
                load.addParameter("refresh", "true");
                load.setJsonEntity(bulk.toString());
                rest.performRequest(load);

                for (ElasticsearchConfig.SearchStrategy strategy : ElasticsearchConfig.SearchStrategy.values()) {
                    ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
                    ElasticsearchClient client = new ElasticsearchClient(new ElasticsearchConfig()
                            .setHosts(List.of(server.getAddress().getHost()))
                            .setPort(server.getAddress().getPort())
                            .setTlsEnabled(true)
                            .setTrustStorePath(new File(getResource("truststore.jks").toURI()))
                            .setTruststorePassword("123456")
                            .setVerifyHostnames(false)
                            .setScrollSize(100)
                            .setScrollTimeout(new Duration(1, SECONDS))
                            .setSearchStrategy(strategy),
                            Optional.empty(),
                            Optional.of(new PasswordConfig().setUser(ElasticsearchQueryRunner.USER).setPassword(ElasticsearchQueryRunner.PASSWORD)),
                            diagnostics);
                    try {
                        for (int round = 0; round < 4; round++) {
                            Set<String> ids = new HashSet<>();
                            long bytes = 0;
                            long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                            long gcBefore = collections();
                            long requestsBefore = diagnostics.getSearchRequests() + diagnostics.getNextPageRequests();
                            long start = System.nanoTime();
                            for (int shard = 0; shard < 3; shard++) {
                                ElasticsearchTableHandle table = new ElasticsearchTableHandle(SCAN, "default", "scan_benchmark", Optional.empty());
                                try (SearchExecution execution = new SearchExecution(SearchExecutionStrategies.create(
                                        client,
                                        table,
                                        new ElasticsearchSplit("scan_benchmark", shard, Optional.empty()),
                                        Optional.empty(),
                                        List.of(),
                                        diagnostics), OptionalLong.empty(), diagnostics)) {
                                    while (execution.hasNext()) {
                                        SearchDocument document = execution.next();
                                        assertThat(ids.add(document.id())).isTrue();
                                        bytes += document.sourceLength();
                                        if (round == 3 && ids.size() % 100 == 0) {
                                            // The scan outlives its keep-alive; each page must renew the same context.
                                            Thread.sleep(100);
                                        }
                                    }
                                }
                            }
                            assertThat(ids).hasSize(DOCUMENTS);
                            LOG.info("image=%s strategy=%s round=%s elapsedMs=%s requests=%s sourceBytes=%s heapBefore=%s heapAfter=%s gc=%s",
                                    image,
                                    strategy,
                                    round,
                                    (System.nanoTime() - start) / 1_000_000,
                                    diagnostics.getSearchRequests() + diagnostics.getNextPageRequests() - requestsBefore,
                                    bytes,
                                    heapBefore,
                                    ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                                    collections() - gcBefore);
                        }
                        assertThat(diagnostics.getFailures()).isZero();
                        assertThat(diagnostics.getCancellations()).isZero();
                    }
                    finally {
                        client.close();
                    }
                }
            }
        }
    }

    private static long collections()
    {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
}
