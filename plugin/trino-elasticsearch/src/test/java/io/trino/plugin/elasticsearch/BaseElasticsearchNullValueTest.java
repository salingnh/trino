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

import io.trino.sql.planner.plan.AggregationNode;
import io.trino.sql.planner.plan.FilterNode;
import io.trino.sql.planner.plan.TopNNode;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Verifies that Elasticsearch mapping-time value rewrites never change Trino's source-value SQL semantics.
 */
@TestInstance(PER_CLASS)
public abstract class BaseElasticsearchNullValueTest
        extends AbstractTestQueryFramework
{
    private ElasticsearchServer server;
    private RestClient client;

    protected BaseElasticsearchNullValueTest(ElasticsearchServer server)
    {
        this.server = requireNonNull(server, "server is null");
        this.client = server.getClient();
    }

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        return ElasticsearchQueryRunner.builder(server).build();
    }

    @AfterAll
    public final void destroy()
            throws IOException
    {
        server.close();
        server = null;
        client.close();
        client = null;
    }

    @Test
    public void testNullValueMappingRemainsEngineSide()
            throws IOException
    {
        String indexName = "null_value_semantics";
        Request create = new Request("PUT", "/" + indexName);
        create.setJsonEntity("{\"mappings\":{\"properties\":{\"amount\":{\"type\":\"double\",\"null_value\":0},\"id\":{\"type\":\"integer\"}}}}");
        client.performRequest(create);

        try {
            indexDocument(indexName, "1", "{\"id\":1,\"amount\":null}");
            indexDocument(indexName, "2", "{\"id\":2,\"amount\":5.0}");

            assertThat(query("SELECT count(amount) FROM " + indexName))
                    .matches("VALUES BIGINT '1'")
                    .isNotFullyPushedDown(AggregationNode.class);
            assertThat(query("SELECT min(amount) FROM " + indexName))
                    .matches("VALUES DOUBLE '5.0'")
                    .isNotFullyPushedDown(AggregationNode.class);
            assertThat(query("SELECT max(amount) FROM " + indexName))
                    .matches("VALUES DOUBLE '5.0'")
                    .isNotFullyPushedDown(AggregationNode.class);
            assertThat(query("SELECT sum(amount) FROM " + indexName))
                    .matches("VALUES DOUBLE '5.0'")
                    .isNotFullyPushedDown(AggregationNode.class);
            assertThat(query("SELECT avg(amount) FROM " + indexName))
                    .matches("VALUES DOUBLE '5.0'")
                    .isNotFullyPushedDown(AggregationNode.class);

            assertThat(query("SELECT amount, count(*) FROM " + indexName + " GROUP BY amount"))
                    .matches("VALUES (CAST(NULL AS double), BIGINT '1'), (DOUBLE '5.0', BIGINT '1')")
                    .isNotFullyPushedDown(AggregationNode.class);

            assertThat(query("SELECT id FROM " + indexName + " WHERE amount IS NULL"))
                    .matches("VALUES INTEGER '1'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertQueryReturnsEmptyResult("SELECT id FROM " + indexName + " WHERE amount = 0");
            assertThat(query("SELECT id FROM " + indexName + " WHERE amount = 0"))
                    .isNotFullyPushedDown(FilterNode.class);

            assertThat(query("SELECT id FROM " + indexName + " ORDER BY amount ASC NULLS LAST, id ASC LIMIT 1"))
                    .matches("VALUES INTEGER '2'")
                    .isNotFullyPushedDown(TopNNode.class);
        }
        finally {
            client.performRequest(new Request("DELETE", "/" + indexName));
        }
    }

    private void indexDocument(String indexName, String id, String document)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName + "/_doc/" + id + "?refresh=true");
        request.setJsonEntity(document);
        client.performRequest(request);
    }
}
