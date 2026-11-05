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
package io.trino.plugin.elasticsearch.client;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchQueryShapeMapping
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final IndexMetadata.ObjectType SCHEMA = new IndexMetadata.ObjectType(List.of(
            new IndexMetadata.Field(false, false, "value", new IndexMetadata.PrimitiveType("long"))));

    @Test
    void testMappingOptionsAndDefaults()
            throws Exception
    {
        assertThat(eligible("{\"type\":\"long\"}", "{}", "")).isTrue();
        for (String option : List.of("\"doc_values\":false", "\"script\":{}", "\"null_value\":0", "\"ignore_malformed\":true")) {
            assertThat(eligible("{\"type\":\"long\"," + option + "}", "{}", "")).isFalse();
        }
        assertThat(eligible("{\"type\":\"long\"}", "{\"index.mapping.ignore_malformed\":\"true\"}", "")).isFalse();
        assertThat(eligible("{\"type\":\"long\",\"ignore_malformed\":false}", "{\"index.mapping.ignore_malformed\":\"true\"}", "")).isTrue();
        assertThat(eligible("{\"type\":\"keyword\",\"ignore_above\":3}", "{}", "")).isFalse();
    }

    @Test
    void testSourceFiltersRuntimeAndCopyTargets()
            throws Exception
    {
        assertThat(eligible("{\"type\":\"long\"}", "{}", ",\"_source\":{\"excludes\":[\"val*\"]}")).isFalse();
        assertThat(eligible("{\"type\":\"long\"}", "{}", ",\"_source\":{\"includes\":[\"other\"]}")).isFalse();
        assertThat(eligible("{\"type\":\"long\"}", "{}", ",\"runtime\":{\"value\":{\"type\":\"long\"}}")).isFalse();
        String indices = "{\"a\":{\"settings\":{},\"mappings\":{\"properties\":{\"value\":{\"type\":\"long\"},\"other\":{\"type\":\"long\",\"copy_to\":\"value\"}}}}}";
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, JSON.readTree(indices)).fields().getFirst().supportsQueryShapes()).isFalse();
    }

    @Test
    void testAllIndicesMustAgree()
            throws Exception
    {
        String valid = "{\"settings\":{},\"mappings\":{\"properties\":{\"value\":{\"type\":\"long\"}}}}";
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, JSON.readTree("{\"a\":" + valid + ",\"b\":" + valid + "}")).fields().getFirst().supportsQueryShapes()).isTrue();
        String missing = "{\"settings\":{},\"mappings\":{\"properties\":{}}}";
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, JSON.readTree("{\"a\":" + valid + ",\"b\":" + missing + "}")).fields().getFirst().supportsQueryShapes()).isFalse();
    }

    @Test
    void testAncestorAndSourceRestrictions()
            throws Exception
    {
        var schema = new IndexMetadata.ObjectType(List.of(new IndexMetadata.Field(false, false, "obj", SCHEMA)));
        for (String options : List.of("\"type\":\"nested\",", "\"enabled\":false,", "")) {
            String mappings = "{\"a\":{\"settings\":{},\"mappings\":{\"properties\":{\"obj\":{" + options + "\"properties\":{\"value\":{\"type\":\"long\"}}}}}}}";
            var result = ElasticsearchQueryShapeMapping.apply(schema, JSON.readTree(mappings));
            var child = ((IndexMetadata.ObjectType) result.fields().getFirst().type()).fields().getFirst();
            assertThat(child.supportsQueryShapes()).isEqualTo(options.isEmpty());
        }
        assertThat(eligible("{\"type\":\"long\"}", "{}", ",\"_source\":{\"enabled\":false}")).isFalse();
        assertThat(eligible("{\"type\":\"long\"}", "{}", ",\"_meta\":{\"trino\":{\"value\":{\"isArray\":true}}}")).isFalse();
    }

    private static boolean eligible(String mapping, String settings, String extra)
            throws Exception
    {
        String indices = "{\"a\":{\"settings\":" + settings + ",\"mappings\":{\"properties\":{\"value\":" + mapping + "}" + extra + "}}}";
        return ElasticsearchQueryShapeMapping.apply(SCHEMA, JSON.readTree(indices)).fields().getFirst().supportsQueryShapes();
    }
}
