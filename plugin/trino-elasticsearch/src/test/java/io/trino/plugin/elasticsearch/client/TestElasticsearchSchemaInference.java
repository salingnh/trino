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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.elasticsearch.client.ElasticsearchSchemaInference.Sample;
import io.trino.plugin.elasticsearch.client.IndexMetadata.Field;
import io.trino.plugin.elasticsearch.client.IndexMetadata.InferredMapping;
import io.trino.plugin.elasticsearch.client.IndexMetadata.ObjectType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchSchemaInference
{
    private static final JsonMapper JSON = new JsonMapperProvider().get();

    @Test
    void testIndexEligibility()
            throws Exception
    {
        assertThat(ElasticsearchSchemaInference.needsInference(JSON.readTree(
                """
                {"_meta":{"application":"test"},"properties":{}}
                """))).isTrue();
        assertThat(ElasticsearchSchemaInference.needsInference(JSON.readTree(
                """
                {"_meta":{"trino":{}},"properties":{}}
                """))).isFalse();
        assertThat(ElasticsearchSchemaInference.needsInference(JSON.readTree(
                """
                {"_meta":{"presto":{}},"properties":{}}
                """))).isFalse();
    }

    @Test
    void testTargetedSamplesCoverNestedFields()
            throws Exception
    {
        ObjectType schema = new ObjectType(List.of(new Field(false, false, "users", new ObjectType(List.of(
                field("city", "keyword"),
                field("age", "long"),
                field("missing", "keyword"),
                field("source_only", "keyword"))))));
        JsonNode properties = JSON.readTree(
                """
                {"users":{"type":"nested","properties":{
                  "city":{"type":"keyword"},
                  "age":{"type":"long"},
                  "missing":{"type":"keyword"},
                  "source_only":{"type":"keyword","index":false,"doc_values":false}}}}
                """);
        List<JsonNode> queries = new ArrayList<>();
        List<List<Sample>> responses = List.of(
                List.of(sample("1",
                        """
                        {"users":[{"city":"Hà Nội","age":null}]}
                        """)),
                List.of(
                        sample("1",
                                """
                                {"users":[{"city":["ignored duplicate"],"age":"ignored duplicate"}]}
                                """),
                        sample("2",
                                """
                                {"users":[{"city":["Hà Nội","Huế"]},{"age":28}]}
                                """)),
                List.of());
        ObjectType result = ElasticsearchSchemaInference.infer(schema, mapping(properties), responses.getFirst(), batch -> {
            queries.addAll(batch);
            return responses.get(1);
        });

        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).at("/nested/path").asText()).isEqualTo("users");
        assertThat(queries.get(0).at("/nested/query/exists/field").asText()).isEqualTo("users.age");
        assertThat(queries.get(1).at("/nested/query/exists/field").asText()).isEqualTo("users.missing");
        Field users = result.fields().getFirst();
        assertThat(users.isArray()).isTrue();
        assertThat(users.asRawJson()).isFalse();
        List<Field> children = ((ObjectType) users.type()).fields();
        assertThat(children.get(0).asRawJson()).isTrue();
        assertThat(children.get(1).inferredMapping().orElseThrow().observedTypes()).containsExactly("INTEGER");
        assertThat(children.get(2).asRawJson()).isTrue();
        assertThat(children.get(3).asRawJson()).isTrue();
    }

    @Test
    void testObjectArrayConflictAndArrayDepth()
            throws Exception
    {
        ObjectType schema = new ObjectType(List.of(
                new Field(false, false, "users", new ObjectType(List.of(field("name", "keyword")))),
                field("plate", "keyword"),
                field("ambiguous", "keyword"),
                field("empty", "long")));
        ObjectType result = infer(schema, List.of(
                sample("1",
                        """
                        {"users":{"name":"An"},"plate":[["29","30"],["A54321"]],"ambiguous":["x",["y"]],"empty":[]}
                        """),
                sample("2",
                        """
                        {"users":[{"name":"Bình"}],"plate":[["40"]],"empty":[null]}
                        """)));
        assertThat(result.fields().get(0).asRawJson()).isTrue();
        assertThat(result.fields().get(1).inferredMapping().orElseThrow().arrayDepth()).isEqualTo(2);
        assertThat(result.fields().get(2).asRawJson()).isTrue();
        assertThat(result.fields().get(3)).satisfies(field -> {
            assertThat(field.asRawJson()).isFalse();
            assertThat(field.isArray()).isTrue();
        });
    }

    @Test
    void testPrimitiveTypeConflictLimitAndPrecision()
            throws Exception
    {
        ObjectType schema = new ObjectType(List.of(
                field("two", "long"),
                field("bool", "keyword"),
                field("three", "keyword"),
                field("numeric", "double"),
                field("unsafe", "double")));
        ObjectType result = infer(schema, List.of(
                sample("1",
                        """
                        {"two":1,"bool":true,"three":1,"numeric":1,"unsafe":9007199254740993}
                        """),
                sample("2",
                        """
                        {"two":"2","bool":"false","three":"2","numeric":2.5,"unsafe":2.5}
                        """),
                sample("3",
                        """
                        {"two":null,"three":2.5}
                        """)));
        assertThat(result.fields().get(0).inferredMapping().orElseThrow().scalarMode()).isEqualTo(IndexMetadata.InferredScalarMode.DOC_VALUES);
        assertThat(result.fields().get(1).inferredMapping().orElseThrow().scalarMode()).isEqualTo(IndexMetadata.InferredScalarMode.DOC_VALUES);
        assertThat(result.fields().get(2).asRawJson()).isTrue();
        assertThat(result.fields().get(3).type()).isEqualTo(new PrimitiveType("double"));
        assertThat(result.fields().get(4).inferredMapping().orElseThrow().scalarMode()).isEqualTo(IndexMetadata.InferredScalarMode.DOC_VALUES);
    }

    @Test
    void testAliasMergeCombinesObservedTypesAndChildren()
            throws Exception
    {
        ObjectType firstSchema = new ObjectType(List.of(
                field("amount", "long"),
                new Field(false, false, "user", new ObjectType(List.of(field("name", "keyword"))))));
        ObjectType secondSchema = new ObjectType(List.of(
                field("amount", "double"),
                new Field(false, false, "user", new ObjectType(List.of(field("age", "long"))))));
        ObjectType first = infer(firstSchema, List.of(sample("1",
                """
                {"amount":1,"user":{"name":"An"}}
                """)));
        ObjectType second = infer(secondSchema, List.of(sample("1",
                """
                {"amount":2.5,"user":{"age":28}}
                """)));
        ObjectType merged = ElasticsearchSchemaInference.merge(List.of(first, second));
        assertThat(primitive(merged, 0)).isEqualTo("double");
        assertThat(((ObjectType) merged.fields().get(1).type()).fields()).extracting(Field::name).containsExactly("name", "age");
        assertThat(merged.fields().getFirst().inferredMapping().map(InferredMapping::observedTypes)).contains(Set.of("INTEGER", "REAL"));
    }

    @Test
    void testAliasRetainsCompatibleScalarMappings()
            throws Exception
    {
        ObjectType schema = new ObjectType(List.of(field("age", "long")));
        ObjectType first = infer(schema, List.of(sample("1",
                """
                {"age":1}
                """)));
        ObjectType second = infer(schema, List.of(sample("1",
                """
                {"age":"02"}
                """)));
        Field merged = ElasticsearchSchemaInference.merge(List.of(first, second)).fields().getFirst();
        assertThat(merged.type()).isEqualTo(new PrimitiveType("long"));
        assertThat(merged.inferredMapping().orElseThrow().scalarMode()).isEqualTo(IndexMetadata.InferredScalarMode.DOC_VALUES);
        assertThat(merged.inferredMapping().orElseThrow().primitiveType()).isEmpty();
    }

    @Test
    void testMergePreservesExplicitNonInferredFields()
    {
        ObjectType first = new ObjectType(List.of(
                new Field(false, false, "msg", new PrimitiveType("text", Optional.of("keyword"), true)),
                new Field(false, false, "user", new ObjectType(List.of(field("name", "keyword")))),
                field("amount", "long")));
        ObjectType second = new ObjectType(List.of(
                new Field(false, false, "msg", new PrimitiveType("text", Optional.empty(), false)),
                new Field(false, false, "user", new ObjectType(List.of(field("age", "long")))),
                field("amount", "double")));

        ObjectType merged = ElasticsearchSchemaInference.merge(List.of(first, second));
        Field msg = merged.fields().get(0);
        assertThat(msg.asRawJson()).isFalse();
        assertThat(msg.inferredMapping()).isEmpty();
        assertThat(msg.type()).isEqualTo(new PrimitiveType("text", Optional.empty(), false));

        Field user = merged.fields().get(1);
        assertThat(user.asRawJson()).isFalse();
        assertThat(user.inferredMapping()).isEmpty();
        assertThat(((ObjectType) user.type()).fields()).extracting(Field::name).containsExactly("name", "age");
        assertThat(((ObjectType) user.type()).fields()).allSatisfy(child -> assertThat(child.inferredMapping()).isEmpty());

        Field amount = merged.fields().get(2);
        assertThat(amount.asRawJson()).isTrue();
        assertThat(amount.inferredMapping()).isEmpty();
    }

    private static JsonNode mapping(JsonNode properties)
    {
        return JSON.createObjectNode().set("mappings", JSON.createObjectNode().set("properties", properties));
    }

    private static ObjectType infer(ObjectType schema, List<Sample> samples)
    {
        return ElasticsearchSchemaInference.infer(schema, mapping(JSON.createObjectNode()), samples, _ -> List.of());
    }

    private static String primitive(ObjectType result, int index)
    {
        return result.fields().get(index).inferredMapping().orElseThrow().primitiveType().orElseThrow();
    }

    private static Field field(String name, String type)
    {
        return new Field(false, false, name, new PrimitiveType(type));
    }

    private static Sample sample(String id, String source)
            throws Exception
    {
        return new Sample(id, JSON.readTree(source));
    }
}
