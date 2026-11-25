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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.airlift.json.JsonCodec.jsonCodec;
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
    void testPredicateSafetyUsesEffectiveDefaultsButDoesNotRequireDocValues()
            throws Exception
    {
        var indices = JSON.readTree(
                """
                {"a":{"settings":{"index.mapping.ignore_malformed":"true"},"mappings":{"properties":{"value":{"type":"long","doc_values":false}}}}}
                """);
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, indices).fields().getFirst().predicateMappingSafe()).isFalse();
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, indices, Map.of("a", Set.of("value"))).fields().getFirst().predicateMappingSafe()).isFalse();
        var safe = ElasticsearchQueryShapeMapping.apply(SCHEMA, indices, Map.of("a", Set.of())).fields().getFirst();
        assertThat(safe.predicateMappingSafe()).isTrue();
        assertThat(safe.supportsQueryShapes()).isFalse();
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
    void testUnindexedValuesNeedDocValuesForPredicateSafety()
            throws Exception
    {
        var indices = JSON.readTree(
                """
                {"a":{"settings":{},"mappings":{"properties":{"value":{"type":"long","index":false,"doc_values":false}}}}}
                """);
        assertThat(ElasticsearchQueryShapeMapping.apply(SCHEMA, indices).fields().getFirst().predicateMappingSafe()).isFalse();
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

    @Test
    void testObjectArrayMappingProof()
            throws Exception
    {
        for (String type : List.of("object", "nested")) {
            String index = arrayIndex(type, "{}", "");
            var field = objectArrayField(index, index);
            assertThat(((IndexMetadata.ObjectType) field.type()).arrayMapping())
                    .contains(new IndexMetadata.ObjectArrayMapping(type.equals("nested"), Set.of("city")));
            // Predicate eligibility inside the array must not enable the old scalar or query-shape path.
            assertThat(field.supportsQueryShapes()).isFalse();
            var codec = jsonCodec(IndexMetadata.Field.class);
            assertThat(codec.fromJson(codec.toJson(field))).isEqualTo(field);
        }

        String nested = arrayIndex("nested", "{}", "");
        String object = arrayIndex("object", "{}", "");
        assertThat(objectArrayMapping(nested, object)).isEmpty();
        assertThat(objectArrayMapping(nested, arrayIndex("nested", "{\"index.mapping.source.mode\":\"synthetic\"}", ""))).isEmpty();
        assertThat(objectArrayMapping(nested, arrayIndex("nested", "{}", ",\"null_value\":\"missing\"")))
                .contains(new IndexMetadata.ObjectArrayMapping(true, Set.of()));
        String ignoreAbove = arrayIndex("nested", "{\"index.mapping.ignore_above\":256}", "");
        assertThat(objectArrayMapping(ignoreAbove, ignoreAbove)).contains(new IndexMetadata.ObjectArrayMapping(true, Set.of()));
    }

    private static Optional<IndexMetadata.ObjectArrayMapping> objectArrayMapping(String first, String second)
            throws Exception
    {
        return ((IndexMetadata.ObjectType) objectArrayField(first, second).type()).arrayMapping();
    }

    private static IndexMetadata.Field objectArrayField(String first, String second)
            throws Exception
    {
        var row = new IndexMetadata.ObjectType(List.of(new IndexMetadata.Field(false, false, "city", new IndexMetadata.PrimitiveType("keyword"))));
        var schema = new IndexMetadata.ObjectType(List.of(new IndexMetadata.Field(false, true, "users", row)));
        return ElasticsearchQueryShapeMapping.apply(schema, JSON.readTree("{\"a\":" + first + ",\"b\":" + second + "}")).fields().getFirst();
    }

    private static String arrayIndex(String type, String settings, String fieldOptions)
    {
        return """
               {"settings":%s,"mappings":{
                 "_meta":{"trino":{"users":{"isArray":true}}},
                 "properties":{"users":{"type":"%s","properties":{"city":{"type":"keyword"%s}}}}
               }}
               """.formatted(settings, type, fieldOptions);
    }

    private static boolean eligible(String mapping, String settings, String extra)
            throws Exception
    {
        String indices = "{\"a\":{\"settings\":" + settings + ",\"mappings\":{\"properties\":{\"value\":" + mapping + "}" + extra + "}}}";
        return ElasticsearchQueryShapeMapping.apply(SCHEMA, JSON.readTree(indices)).fields().getFirst().supportsQueryShapes();
    }
}
