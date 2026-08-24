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
import io.trino.Session;
import io.trino.sql.planner.plan.FilterNode;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.2 acceptance contract for permanent document-scope predicate composition.
 *
 * <p>These tests intentionally keep document-scope array conjunction separate from P1.1 same-element
 * {@code any_match} semantics. They also verify that an unsafe partial OR is retained by Trino while a SAFE
 * full-text OR may use Elasticsearch only as a no-false-negative candidate filter.</p>
 */
public abstract class BaseElasticsearchPredicateCompositionTest
        extends BaseElasticsearchAnyMatchPushdownTest
{
    private final RestClient client;

    protected BaseElasticsearchPredicateCompositionTest(ElasticsearchServer server)
    {
        super(server);
        this.client = server.getClient();
    }

    @Test
    public void testPermanentPredicateComposition()
            throws IOException
    {
        String indexName = "p1_predicate_composition_" + randomNameSuffix();
        @Language("JSON")
        String mapping =
                """
                {
                  "_meta": {
                    "trino": {
                      "numbers": { "isArray": true },
                      "tags": { "isArray": true }
                    }
                  },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" },
                    "tags": { "type": "keyword" },
                    "message": { "type": "text" }
                  }
                }
                """;
        createIndex(indexName, mapping);
        try {
            index(indexName, ImmutableMap.of(
                    "id", "1",
                    "numbers", ImmutableList.of(1, 2),
                    "tags", ImmutableList.of("a", "b"),
                    "message", "fatal alpha"));
            index(indexName, ImmutableMap.of(
                    "id", "2",
                    "numbers", ImmutableList.of(1, 3),
                    "tags", ImmutableList.of("a"),
                    "message", "error beta"));
            index(indexName, ImmutableMap.of(
                    "id", "3",
                    "numbers", ImmutableList.of(2),
                    "tags", ImmutableList.of("b"),
                    "message", "other"));
            index(indexName, ImmutableMap.of(
                    "id", "4",
                    "numbers", ImmutableList.of(1, 2),
                    "tags", ImmutableList.of("c")));

            // Exact OR is translated by the permanent composer and normalized to one native Terms predicate.
            assertThat(query("SELECT id FROM " + indexName + " WHERE contains(tags, 'a') OR contains(tags, 'b')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '3'")
                    .isFullyPushedDown();

            // Top-level conjunction is document-scope: different array elements may satisfy the independent terms.
            assertThat(query("SELECT id FROM " + indexName + " WHERE contains(numbers, 1) AND contains(numbers, 2)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '4'")
                    .isFullyPushedDown();

            // P1.1's same-element boundary remains intact under the new document-scope composer.
            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 1 AND x < 3)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '3', VARCHAR '4'")
                    .isFullyPushedDown();

            // A translatable branch cannot be pushed alone under OR because it could remove rows satisfying only the
            // untranslatable branch.
            assertThat(query("SELECT id FROM " + indexName + " WHERE contains(tags, 'a') OR cardinality(numbers) = 1"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '3'")
                    .isNotFullyPushedDown(FilterNode.class);

            String catalogName = getSession().getCatalog().orElseThrow();
            Session safe = Session.builder(getSession())
                    .setCatalogSessionProperty(catalogName, "full_text_pushdown_mode", "SAFE")
                    .build();

            // Every SAFE OR branch has a no-false-negative remote candidate, but the complete SQL OR remains as the
            // authoritative Trino residual. The document with a missing message must not leak through SQL NULL logic.
            assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE message LIKE 'fatal%' OR message LIKE 'error%'"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2'")
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    private void createIndex(String indexName, @Language("JSON") String mapping)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity("{\"mappings\": " + mapping + "}");
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
