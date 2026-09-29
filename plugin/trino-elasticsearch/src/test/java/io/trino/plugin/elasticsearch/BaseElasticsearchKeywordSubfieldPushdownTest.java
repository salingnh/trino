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

import io.trino.sql.planner.plan.FilterNode;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaseElasticsearchKeywordSubfieldPushdownTest
        extends AbstractTestQueryFramework
{
    private final String image;
    private RestClient client;

    protected BaseElasticsearchKeywordSubfieldPushdownTest(String image)
    {
        this.image = requireNonNull(image, "image is null");
    }

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        ElasticsearchServer server = closeAfterClass(new ElasticsearchServer(image));
        client = closeAfterClass(server.getClient());
        return ElasticsearchQueryRunner.builder(server).build();
    }

    @AfterAll
    public void clearClient()
    {
        client = null;
    }

    @Test
    public void testEqualityAndIn()
            throws IOException
    {
        String index = "keyword_predicate_" + randomNameSuffix();
        request("PUT", "/" + index, """
                {"settings":{"number_of_shards":1,"number_of_replicas":0},
                 "mappings":{"properties":{
                   "id":{"type":"integer"},
                   "name":{"type":"text","fields":{"raw":{"type":"keyword"}}}
                 }}}
                """);
        try {
            request("PUT", "/" + index + "/_doc/1", "{\"id\":1,\"name\":\"Alice\"}");
            request("PUT", "/" + index + "/_doc/2", "{\"id\":2,\"name\":\"alice\"}");
            request("PUT", "/" + index + "/_doc/3", "{\"id\":3,\"name\":\"New York\"}");
            request("PUT", "/" + index + "/_doc/4", "{\"id\":4,\"name\":\"ngô văn\"}");
            request("PUT", "/" + index + "/_doc/5", "{\"id\":5,\"name\":\"\"}");
            request("PUT", "/" + index + "/_doc/6", "{\"id\":6,\"name\":null}");
            request("PUT", "/" + index + "/_doc/7", "{\"id\":7}");
            request("POST", "/" + index + "/_refresh", null);

            assertQuery("SELECT id FROM " + index + " WHERE name = 'Alice'", "VALUES 1");
            assertQuery("SELECT id FROM " + index + " WHERE name = 'New York'", "VALUES 3");
            assertQuery("SELECT id FROM " + index + " WHERE name IN ('Alice', 'ngô văn')", "VALUES 1, 4");
            assertQuery("SELECT id FROM " + index + " WHERE name = ''", "VALUES 5");
            assertQuery("SELECT id FROM " + index + " WHERE name IS NULL", "VALUES 6, 7");
            assertQuery("SELECT count(*) FROM " + index + " WHERE name <> 'Alice'", "VALUES BIGINT '4'");
            assertThat(query("SELECT name FROM " + index + " WHERE name = 'Alice'"))
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            request("DELETE", "/" + index, null);
        }
    }

    @Test
    public void testSubfieldAddedAfterDocuments()
            throws IOException
    {
        String index = "keyword_backfill_" + randomNameSuffix();
        request("PUT", "/" + index, """
                {"settings":{"number_of_shards":1,"number_of_replicas":0},
                 "mappings":{"properties":{"id":{"type":"integer"},"name":{"type":"text"}}}}
                """);
        try {
            request("PUT", "/" + index + "/_doc/1", "{\"id\":1,\"name\":\"Bob\"}");
            request("PUT", "/" + index + "/_doc/2", "{\"id\":2,\"name\":\"Alice\"}");
            request("POST", "/" + index + "/_refresh", null);
            request("PUT", "/" + index + "/_mapping", """
                    {"properties":{"name":{"type":"text","fields":{"raw":{"type":"keyword"}}}}}
                    """);
            request("PUT", "/" + index + "/_doc/3", "{\"id\":3,\"name\":\"Alice\"}");
            request("PUT", "/" + index + "/_doc/4", "{\"id\":4,\"name\":\"ALICE\"}");
            request("POST", "/" + index + "/_refresh", null);

            // A plain terms query would lose id=2. Dropping the residual would incorrectly include id=1.
            assertQuery("SELECT id FROM " + index + " WHERE name = 'Alice'", "VALUES 2, 3");
            assertQuery("SELECT name FROM " + index + " WHERE name = 'Alice'", "VALUES 'Alice', 'Alice'");
            assertQuery("SELECT count(*) FROM " + index + " WHERE name = 'Alice'", "VALUES BIGINT '2'");
            assertQuery("SELECT name FROM " + index + " WHERE name = 'Alice' LIMIT 2", "VALUES 'Alice', 'Alice'");
            assertQuery("SELECT id FROM " + index + " WHERE name = 'Alice' OR id = 1", "VALUES 1, 2, 3");
            assertQueryReturnsEmptyResult("SELECT name FROM (SELECT id, name FROM " + index + " ORDER BY id LIMIT 1) WHERE name = 'Alice'");
            assertThat(query("SELECT name FROM " + index + " WHERE name = 'Alice'"))
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            request("DELETE", "/" + index, null);
        }
    }

    @Test
    public void testBoundedAndNormalizedSubfields()
            throws IOException
    {
        String index = "keyword_unsupported_" + randomNameSuffix();
        request("PUT", "/" + index, """
                {"settings":{
                   "number_of_shards":1,"number_of_replicas":0,
                   "analysis":{"normalizer":{"fold":{"type":"custom","filter":["lowercase"]}}}
                 },"mappings":{"properties":{
                   "id":{"type":"integer"},
                   "bounded":{"type":"text","fields":{"raw":{"type":"keyword","ignore_above":5}}},
                   "normalized":{"type":"text","fields":{"raw":{"type":"keyword","normalizer":"fold"}}}
                 }}}
                """);
        try {
            request("PUT", "/" + index + "/_doc/1", "{\"id\":1,\"bounded\":\"Long Value\",\"normalized\":\"MiXeD\"}");
            request("POST", "/" + index + "/_refresh", null);
            assertQuery("SELECT id FROM " + index + " WHERE bounded = 'Long Value'", "VALUES 1");
            assertQuery("SELECT id FROM " + index + " WHERE normalized = 'MiXeD'", "VALUES 1");
            assertQueryReturnsEmptyResult("SELECT id FROM " + index + " WHERE normalized = 'mixed'");
        }
        finally {
            request("DELETE", "/" + index, null);
        }
    }

    @Test
    public void testAliasWithDifferentSubfieldMappings()
            throws IOException
    {
        String suffix = randomNameSuffix();
        String first = "keyword_alias_a_" + suffix;
        String second = "keyword_alias_b_" + suffix;
        String alias = "keyword_alias_" + suffix;
        request("PUT", "/" + first, """
                {"mappings":{"properties":{
                  "id":{"type":"integer"},
                  "name":{"type":"text","fields":{"raw":{"type":"keyword"}}}
                }}}
                """);
        try {
            request("PUT", "/" + second, """
                    {"settings":{"analysis":{"normalizer":{"fold":{"type":"custom","filter":["lowercase"]}}}},
                     "mappings":{"properties":{
                       "id":{"type":"integer"},
                       "name":{"type":"text","fields":{"raw":{"type":"keyword","normalizer":"fold"}}}
                     }}}
                    """);
            try {
                request("PUT", "/" + first + "/_doc/1", "{\"id\":1,\"name\":\"MiXeD\"}");
                request("PUT", "/" + second + "/_doc/2", "{\"id\":2,\"name\":\"MiXeD\"}");
                request("POST", "/" + first + "," + second + "/_refresh", null);
                request("POST", "/_aliases", "{\"actions\":[{\"add\":{\"indices\":[\"" + first + "\",\"" + second + "\"],\"alias\":\"" + alias + "\"}}]}");
                assertQuery("SELECT id FROM " + alias + " WHERE name = 'MiXeD'", "VALUES 1, 2");
                assertQueryReturnsEmptyResult("SELECT id FROM " + alias + " WHERE name = 'mixed'");
            }
            finally {
                request("DELETE", "/" + second, null);
            }
        }
        finally {
            request("DELETE", "/" + first, null);
        }
    }

    private void request(String method, String path, String body)
            throws IOException
    {
        Request request = new Request(method, path);
        if (body != null) {
            request.setJsonEntity(body);
        }
        EntityUtils.consume(client.performRequest(request).getEntity());
    }
}
