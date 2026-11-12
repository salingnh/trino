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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.sql.planner.plan.FilterNode;
import io.trino.testing.QueryRunner;
import io.trino.testing.QueryRunner.MaterializedResultWithPlan;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.1 acceptance contract for exact {@code any_match} pushdown on primitive Elasticsearch arrays.
 */
public abstract class BaseElasticsearchAnyMatchPushdownTest
        extends BaseElasticsearchP0PredicatePushdownTest
{
    private final RestClient client;

    protected BaseElasticsearchAnyMatchPushdownTest(ElasticsearchServer server)
    {
        super(server);
        this.client = server.getClient();
    }

    @Test
    public void testAnyMatchPrimitiveArrayExactPushdown()
            throws IOException
    {
        String indexName = "p1_any_match_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": {
                    "trino": {
                      "numbers": { "isArray": true },
                      "tags": { "isArray": true },
                      "text_tags": { "isArray": true }
                    }
                  },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" },
                    "tags": { "type": "keyword" },
                    "text_tags": { "type": "text" }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            index(indexName, ImmutableMap.of(
                    "id", "1",
                    "numbers", ImmutableList.of(1, 3, 5),
                    "tags", ImmutableList.of("CaseSensitive", "alpha"),
                    "text_tags", ImmutableList.of("telegram", "social network")));
            index(indexName, ImmutableMap.of(
                    "id", "2",
                    "numbers", ImmutableList.of(2, 4, 6),
                    "tags", ImmutableList.of("casesensitive", "beta"),
                    "text_tags", ImmutableList.of("facebook")));

            List<Integer> numbersWithNull = new ArrayList<>();
            numbersWithNull.add(null);
            numbersWithNull.add(3);
            index(indexName, ImmutableMap.of(
                    "id", "3",
                    "numbers", numbersWithNull,
                    "tags", ImmutableList.of("gamma"),
                    "text_tags", ImmutableList.of("other")));

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x = 3)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '3'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x IN (4, 9))"))
                    .matches("VALUES VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 4)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 2 AND x < 4)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '3'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x = 1 OR x = 6)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(tags, x -> x = 'CaseSensitive')"))
                    .matches("VALUES VARCHAR '1'")
                    .isFullyPushedDown();

            // Analyzed text is not exact for SQL equality, so the lambda stays in Trino.
            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(text_tags, x -> x = 'telegram')"))
                    .matches("VALUES VARCHAR '1'")
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchNecessaryConditionPrefilter()
            throws IOException
    {
        String indexName = "any_match_prefilter_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": { "trino": { "numbers": { "isArray": true } } },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            index(indexName, ImmutableMap.of("id", "1", "numbers", List.of(2, 4)));
            index(indexName, ImmutableMap.of("id", "2", "numbers", List.of(11, 13)));
            index(indexName, ImmutableMap.of("id", "3", "numbers", List.of(3, 12)));
            // The conditions hold on different elements. A modulo-only residual would admit this row.
            index(indexName, ImmutableMap.of("id", "4", "numbers", List.of(2, 11)));
            index(indexName, ImmutableMap.of("id", "5", "numbers", Arrays.asList(null, 12)));
            index(indexName, ImmutableMap.of("id", "6", "numbers", Arrays.asList(null, 11)));
            index(indexName, ImmutableMap.of("id", "7", "numbers", List.of()));
            index(indexName, ImmutableMap.of("id", "8"));
            index(indexName, ImmutableMap.of("id", "9", "numbers", Arrays.asList((Integer) null)));
            index(indexName, ImmutableMap.of("id", "10", "numbers", List.of(-3)));
            index(indexName, ImmutableMap.of("id", "11", "numbers", List.of(-2)));
            Map<String, Object> nullArray = new HashMap<>();
            nullArray.put("id", "12");
            nullArray.put("numbers", null);
            index(indexName, nullArray);

            String predicate = "any_match(numbers, x -> x > 10 AND mod(x, 2) = 0)";
            String sql = "SELECT id FROM " + indexName + " WHERE " + predicate;
            assertThat(query(sql))
                    .matches("VALUES VARCHAR '3', VARCHAR '5'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query("SELECT count(*) FROM " + indexName + " WHERE " + predicate))
                    .matches("VALUES BIGINT '2'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query(sql + " ORDER BY id LIMIT 1"))
                    .matches("VALUES VARCHAR '3'")
                    .isNotFullyPushedDown(FilterNode.class);

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " +
                    "(x > 10 AND mod(x, 2) = 0) OR (x < 0 AND mod(x, 3) = 0))"))
                    .matches("VALUES VARCHAR '3', VARCHAR '5', VARCHAR '10'")
                    .isNotFullyPushedDown(FilterNode.class);
            // The unsupported OR branch must retain rows such as [2, 4], which have no value greater than 10.
            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '3', VARCHAR '4', VARCHAR '5', VARCHAR '6', VARCHAR '11'")
                    .isNotFullyPushedDown(FilterNode.class);

            QueryRunner runner = getQueryRunner();
            MaterializedResultWithPlan filtered = runner.executeWithPlan(getSession(), sql);
            MaterializedResultWithPlan unfiltered = runner.executeWithPlan(
                    getSession(),
                    "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> mod(x, 2) = 0)");
            long filteredRows = runner.getCoordinator().getQueryManager().getFullQueryInfo(filtered.queryId()).getQueryStats().getPhysicalInputPositions();
            long unfilteredRows = runner.getCoordinator().getQueryManager().getFullQueryInfo(unfiltered.queryId()).getQueryStats().getPhysicalInputPositions();
            assertThat(filteredRows).as("documents read after the necessary-condition prefilter").isEqualTo(5);
            assertThat(unfilteredRows).as("documents read without a native candidate").isEqualTo(12);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    private void createIndex(String indexName, @Language("JSON") String properties)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity("{\"mappings\": " + properties + "}");
        client.performRequest(request);
    }

    private void index(String index, Map<String, Object> document)
            throws IOException
    {
        String json = new JsonMapper().writeValueAsString(document);
        Request request = new Request("PUT", format("/%s/_doc/%s?refresh", index, System.nanoTime()));
        request.setJsonEntity(json);
        client.performRequest(request);
    }

    private void deleteIndex(String indexName)
            throws IOException
    {
        client.performRequest(new Request("DELETE", "/" + indexName));
    }
}
