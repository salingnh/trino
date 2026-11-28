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
import com.google.common.collect.ImmutableMap;
import io.trino.Session;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.trino.plugin.elasticsearch.ElasticsearchServer.ELASTICSEARCH_8_IMAGE;
import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearch8ConnectorTest
        extends BaseElasticsearchParallelConnectorTest
{
    public TestElasticsearch8ConnectorTest()
            throws IOException
    {
        super(new ElasticsearchServer(ELASTICSEARCH_8_IMAGE));
    }

    @Test
    public void testKeywordLowerAndUpperLikeUseCaseInsensitiveRegexp()
            throws IOException
    {
        String indexName = "keyword_case_like_" + randomNameSuffix();
        createKeywordCaseIndex(indexName,
                """
                {"properties":{
                  "word":{"type":"keyword","fields":{"text":{"type":"text"}}},
                  "id":{"type":"keyword"}
                }}
                """);
        try {
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "1", "word", "NAM1"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "2", "word", "Nam1"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "3", "word", "nam1"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "4", "word", "A11"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "5"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "6", "word", "Nam_1%"));
            indexKeywordCaseDocument(indexName, ImmutableMap.of("id", "7", "word", "ĐẶNG1"));
            String catalog = getSession().getCatalog().orElseThrow();
            for (boolean scripts : List.of(false, true)) {
                Session unsafe = Session.builder(getSession())
                        .setCatalogSessionProperty(catalog, "full_text_pushdown_mode", "UNSAFE")
                        .setCatalogSessionProperty(catalog, "scalar_script_pushdown_enabled", Boolean.toString(scripts))
                        .build();
                // The pattern intentionally uses the opposite case. A retained SQL residual would reject these rows.
                assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE lower(word) LIKE '%NAM1%'"))
                        .matches("VALUES VARCHAR '1', '2', '3'")
                        .skipResultsCorrectnessCheckForPushdown()
                        .isFullyPushedDown();
                assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE upper(word) LIKE '%nam1%'"))
                        .matches("VALUES VARCHAR '1', '2', '3'")
                        .skipResultsCorrectnessCheckForPushdown()
                        .isFullyPushedDown();
                assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE lower(word) LIKE '%nam!_1!%%' ESCAPE '!'"))
                        .matches("VALUES VARCHAR '6'").isFullyPushedDown();
                assertThat(query(unsafe, "SELECT id FROM " + indexName + " WHERE upper(word) LIKE '%1%'"))
                        .matches("VALUES VARCHAR '1', '2', '3', '4', '6', '7'").isFullyPushedDown();
                assertThat(query(unsafe, "SELECT count(*) FROM (SELECT id FROM " + indexName + " WHERE lower(word) LIKE '%1%' LIMIT 2)"))
                        .matches("VALUES BIGINT '2'");
            }
            for (String mode : List.of("SAFE", "DISABLED")) {
                Session safe = Session.builder(getSession()).setCatalogSessionProperty(catalog, "full_text_pushdown_mode", mode).build();
                assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE lower(word) LIKE '%NAM1%'"))
                        .matches("SELECT VARCHAR 'unused' WHERE false");
                assertThat(query(safe, "SELECT id FROM " + indexName + " WHERE upper(word) LIKE '%nam1%'"))
                        .matches("SELECT VARCHAR 'unused' WHERE false");
            }
        }
        finally {
            deleteKeywordCaseIndex(indexName);
        }
    }

    private void createKeywordCaseIndex(String indexName, String properties)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity("{\"mappings\": " + properties + "}");
        fullTextClient().performRequest(request);
    }

    private void indexKeywordCaseDocument(String index, Map<String, Object> document)
            throws IOException
    {
        Request request = new Request("PUT", format("/%s/_doc/%s?refresh", index, System.nanoTime()));
        request.setJsonEntity(new JsonMapper().writeValueAsString(document));
        fullTextClient().performRequest(request);
    }

    private void deleteKeywordCaseIndex(String indexName)
            throws IOException
    {
        fullTextClient().performRequest(new Request("DELETE", "/" + indexName));
    }
}
