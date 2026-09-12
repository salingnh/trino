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
import java.util.Arrays;
import java.util.Map;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance contract for analyzed primitive-array full-text pushdown in UNSAFE mode.
 *
 * <p>The mapping intentionally uses a custom analyzer so that these tests prove the array path uses analyzed
 * Elasticsearch values rather than accidentally using term-level predicates. The same test is inherited by the ES7
 * and ES8 connector suites through {@link BaseElasticsearchParallelConnectorTest}.</p>
 */
public abstract class BaseElasticsearchUnsafeArrayPushdownTest
        extends BaseElasticsearchPredicateCompositionTest
{
    private final RestClient client;

    protected BaseElasticsearchUnsafeArrayPushdownTest(ElasticsearchServer server)
    {
        super(server);
        this.client = server.getClient();
    }

    @Test
    public void testUnsafeAnalyzedArrayEqualityAndMembershipPushdown()
            throws IOException
    {
        String indexName = "unsafe_array_membership_" + randomNameSuffix();
        @Language("JSON")
        String body =
                """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "folded_text": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "_meta": {
                      "trino": {
                        "names": { "isArray": true },
                        "exact_names": { "isArray": true }
                      }
                    },
                    "properties": {
                      "id": { "type": "keyword" },
                      "names": { "type": "text", "analyzer": "folded_text" },
                      "exact_names": {
                        "type": "text",
                        "fields": { "keyword": { "type": "keyword" } }
                      }
                    }
                  }
                }
                """;
        createIndex(indexName, body);
        try {
            index(indexName, ImmutableMap.of(
                    "id", "1",
                    "names", ImmutableList.of("NGÔ VĂN", "Nguyen Van", "Nguyen Anh"),
                    "exact_names", ImmutableList.of("Nguyen Van", "other")));
            index(indexName, ImmutableMap.of(
                    "id", "2",
                    "names", ImmutableList.of("TRẦN VĂN", "Tran Thi", "Le Van"),
                    "exact_names", ImmutableList.of("Tran Thi")));
            index(indexName, ImmutableMap.of(
                    "id", "3",
                    "names", ImmutableList.of("social network", "telegram"),
                    "exact_names", ImmutableList.of("telegram")));
            index(indexName, ImmutableMap.of(
                    "id", "4",
                    "names", ImmutableList.of("Nguyen Anh", "Le Van"),
                    "exact_names", ImmutableList.of("Nguyen Anh")));
            index(indexName, ImmutableMap.of(
                    "id", "8",
                    "names", ImmutableList.of("Nguyen Van", "Nguyen Van"),
                    "exact_names", ImmutableList.of("Nguyen Van", "Nguyen Van")));

            Session unsafe = sessionWithFullTextMode("UNSAFE");
            Session safe = sessionWithFullTextMode("SAFE");
            Session disabled = sessionWithFullTextMode("DISABLED");

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE contains(names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            // The analyzer folds accents and case. This intentionally differs from SQL source-value equality and is
            // therefore an UNSAFE result-correctness exception, not an exact ARRAY translation.
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE contains(names, 'ngo van')"))
                    .matches("VALUES VARCHAR '1'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE arrays_overlap(names, ARRAY['Nguyen Van', 'Tran Thi'])"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x = 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x IN ('Nguyen Van', 'Tran Thi'))"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            // SAFE and DISABLED retain the pre-A3 local ARRAY behavior.
            assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE contains(names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query(disabled, "SELECT id FROM " + indexName + " WHERE contains(names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .isNotFullyPushedDown(FilterNode.class);

            // A text field with a keyword subfield remains an exact ARRAY membership translation in UNSAFE mode.
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE contains(exact_names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .isFullyPushedDown();
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testUnsafeAnalyzedArrayLikeAndPrefixPushdown()
            throws IOException
    {
        String indexName = "unsafe_array_like_" + randomNameSuffix();
        @Language("JSON")
        String body =
                """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "folded_text": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "_meta": { "trino": { "names": { "isArray": true } } },
                    "properties": {
                      "id": { "type": "keyword" },
                      "names": { "type": "text", "analyzer": "folded_text" }
                    }
                  }
                }
                """;
        createIndex(indexName, body);
        try {
            index(indexName, ImmutableMap.of("id", "1", "names", ImmutableList.of("NGÔ VĂN", "Nguyen Van", "Nguyen Anh")));
            index(indexName, ImmutableMap.of("id", "2", "names", ImmutableList.of("TRẦN VĂN", "Tran Thi", "Le Van")));
            index(indexName, ImmutableMap.of("id", "3", "names", ImmutableList.of("social network", "telegram")));
            index(indexName, ImmutableMap.of("id", "4", "names", ImmutableList.of("Nguyen Anh", "Le Van")));
            index(indexName, ImmutableMap.of("id", "8", "names", ImmutableList.of("Nguyen Van", "Nguyen Van")));

            Session unsafe = sessionWithFullTextMode("UNSAFE");
            Session safe = sessionWithFullTextMode("SAFE");

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE '%Nguyen Van%')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE 'Nguyen%')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '4', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> starts_with(x, 'Nguyen'))"))
                    .matches("VALUES VARCHAR '1', VARCHAR '4', VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE '%social network%')"))
                    .matches("VALUES VARCHAR '3'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE 'Nguyen%')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '4', VARCHAR '8'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE 'Ng% yen')"))
                    .returnsEmptyResult()
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testUnsafeAnalyzedArrayRegexpPushdown()
            throws IOException
    {
        String indexName = "unsafe_array_regexp_" + randomNameSuffix();
        @Language("JSON")
        String body =
                """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "folded_text": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "_meta": { "trino": { "names": { "isArray": true } } },
                    "properties": {
                      "id": { "type": "keyword" },
                      "names": { "type": "text", "analyzer": "folded_text" }
                    }
                  }
                }
                """;
        createIndex(indexName, body);
        try {
            index(indexName, ImmutableMap.of("id", "1", "names", ImmutableList.of("NGÔ VĂN", "Nguyen Van")));
            index(indexName, ImmutableMap.of("id", "2", "names", ImmutableList.of("TRẦN VĂN", "Tran Thi")));
            index(indexName, ImmutableMap.of("id", "3", "names", ImmutableList.of("social network", "telegram")));
            index(indexName, ImmutableMap.of("id", "4", "names", ImmutableList.of("Nguyen Anh", "Le Van")));
            index(indexName, ImmutableMap.of("id", "8", "names", ImmutableList.of("Nguyen Van", "Nguyen Van")));

            Session unsafe = sessionWithFullTextMode("UNSAFE");
            Session safe = sessionWithFullTextMode("SAFE");

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> regexp_like(x, 'nguyen.*van'))"))
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> regexp_like(x, '(?:tran|nguyen)'))"))
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> regexp_like(x, 'ngo'))"))
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> regexp_like(x, 'nguyen.*van'))"))
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> regexp_like(x, '(?=nguyen)van'))"))
                    .returnsEmptyResult()
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testUnsafeAnalyzedArrayOrPushdownRequiresEveryBranch()
            throws IOException
    {
        String indexName = "unsafe_array_or_" + randomNameSuffix();
        @Language("JSON")
        String body =
                """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "folded_text": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "_meta": { "trino": { "names": { "isArray": true } } },
                    "properties": {
                      "id": { "type": "keyword" },
                      "names": { "type": "text", "analyzer": "folded_text" }
                    }
                  }
                }
                """;
        createIndex(indexName, body);
        try {
            index(indexName, ImmutableMap.of("id", "1", "names", ImmutableList.of("Nguyen Van", "Nguyen Anh")));
            index(indexName, ImmutableMap.of("id", "2", "names", ImmutableList.of("Tran Thi", "Le Van")));
            index(indexName, ImmutableMap.of("id", "8", "names", ImmutableList.of("Nguyen Van", "Nguyen Van")));

            Session unsafe = sessionWithFullTextMode("UNSAFE");

            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x = 'Nguyen Van' OR x LIKE 'Tran%' OR regexp_like(x, 'Le.*Anh'))"))
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x = 'Nguyen Van' OR x LIKE 'Ng% yen')"))
                    .matches("VALUES VARCHAR '1', VARCHAR '8'")
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testUnsafeAnalyzedArraySemanticBoundaries()
            throws IOException
    {
        String indexName = "unsafe_array_boundaries_" + randomNameSuffix();
        @Language("JSON")
        String body =
                """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "folded_text": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "_meta": {
                      "trino": {
                        "names": { "isArray": true },
                        "exact_names": { "isArray": true }
                      }
                    },
                    "properties": {
                      "id": { "type": "keyword" },
                      "names": { "type": "text", "analyzer": "folded_text" },
                      "exact_names": {
                        "type": "text",
                        "fields": { "keyword": { "type": "keyword" } }
                      }
                    }
                  }
                }
                """;
        createIndex(indexName, body);
        try {
            index(indexName, ImmutableMap.of(
                    "id", "1",
                    "names", Arrays.asList("Nguyen Anh", "Le Van"),
                    "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of(
                    "id", "2",
                    "names", Arrays.asList("Nguyen Van", null),
                    "exact_names", ImmutableList.of("Nguyen Van")));
            index(indexName, ImmutableMap.of(
                    "id", "3",
                    "names", Arrays.asList((String) null),
                    "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of(
                    "id", "4",
                    "names", ImmutableList.of(),
                    "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of("id", "5", "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of(
                    "id", "6",
                    "names", Arrays.asList("Nguyen Van", "Nguyen Van"),
                    "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of(
                    "id", "7",
                    "names", ImmutableList.of("social network"),
                    "exact_names", ImmutableList.of("unrelated")));
            index(indexName, ImmutableMap.of(
                    "id", "8",
                    "names", ImmutableList.of("NGÔ VĂN"),
                    "exact_names", ImmutableList.of("unrelated")));

            Session unsafe = sessionWithFullTextMode("UNSAFE");

            // Different elements satisfy the two sides. The generic analyzed-text AND must remain local.
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE 'Nguyen%' AND x LIKE '%Van%')"))
                    .matches("VALUES VARCHAR '2', VARCHAR '6'")
                    .isNotFullyPushedDown(FilterNode.class);

            // NULL, empty, missing, and duplicate source values remain ordinary ARRAY cases around the approximate
            // positive membership candidate.
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE contains(names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '2', VARCHAR '6'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE '%social network%')"))
                    .matches("VALUES VARCHAR '7'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE any_match(names, x -> x LIKE '%ngo%')"))
                    .matches("VALUES VARCHAR '8'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();

            // The top-level OR deliberately mixes an approximate analyzed ARRAY branch with an exact keyword
            // subfield ARRAY branch. The composer must retain the strongest effective APPROXIMATE enforcement.
            assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE contains(names, 'Nguyen Van') OR contains(exact_names, 'Nguyen Van')"))
                    .matches("VALUES VARCHAR '2', VARCHAR '6'")
                    .skipResultsCorrectnessCheckForPushdown()
                    .isFullyPushedDown();
        }
        finally {
            deleteIndex(indexName);
        }
    }

    private Session sessionWithFullTextMode(String mode)
    {
        String catalogName = getSession().getCatalog().orElseThrow();
        return Session.builder(getSession())
                .setCatalogSessionProperty(catalogName, "full_text_pushdown_mode", mode)
                .build();
    }

    private void createIndex(String indexName, @Language("JSON") String body)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(body);
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
