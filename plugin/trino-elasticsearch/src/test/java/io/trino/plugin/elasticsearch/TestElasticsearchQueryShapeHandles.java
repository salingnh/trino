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

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.IndexMetadata;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.RowDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Term;
import io.trino.spi.expression.FieldDereference;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import io.trino.type.TypeDeserializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.type.InternalTypeManager.TESTING_TYPE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchQueryShapeHandles
{
    @Test
    void testCapabilitySerialization()
    {
        var mapper = new JsonMapperProvider().get();
        mapper.registerModule(new SimpleModule().addDeserializer(Type.class, new TypeDeserializer(TESTING_TYPE_MANAGER)));
        var codec = new JsonCodecFactory(mapper).jsonCodec(ElasticsearchColumnHandle.class);
        for (boolean safe : List.of(false, true)) {
            var field = new IndexMetadata.Field(false, false, "value", new IndexMetadata.PrimitiveType("integer"), safe);
            var column = new ElasticsearchColumnHandle(List.of("value"), INTEGER, field.type(), new IntegerDecoder.Descriptor("value"), true, safe);
            assertThat(codec.fromJson(codec.toJson(column))).isEqualTo(column);
            var fieldCodec = new JsonCodecFactory(mapper).jsonCodec(IndexMetadata.Field.class);
            assertThat(fieldCodec.fromJson(fieldCodec.toJson(field))).isEqualTo(field);
        }
    }

    @Test
    void testProjectionPreservesAncestorCapabilityAndUsesFieldName()
            throws Exception
    {
        ElasticsearchConfig config = new ElasticsearchConfig().setHosts(List.of("localhost"));
        var client = new ElasticsearchClient(config, Optional.empty(), Optional.empty());
        try {
            var metadata = new RuleBasedElasticsearchMetadata(TESTING_TYPE_MANAGER, client, config);
            var session = TestingConnectorSession.builder().setPropertyMetadata(new ElasticsearchSessionProperties(config).getSessionProperties()).build();
            var row = RowType.rowType(RowType.field("value", INTEGER));
            var descriptor = new RowDecoder.Descriptor("obj", List.of(new RowDecoder.NameAndDescriptor("value", new IntegerDecoder.Descriptor("obj.value"))));
            for (boolean ancestor : List.of(false, true)) {
                for (boolean child : List.of(false, true)) {
                    var mapping = new IndexMetadata.ObjectType(List.of(
                            new IndexMetadata.Field(false, false, "unsupported", new IndexMetadata.PrimitiveType("unsupported")),
                            new IndexMetadata.Field(false, false, "value", new IndexMetadata.PrimitiveType("integer"), child)));
                    var column = new ElasticsearchColumnHandle(List.of("obj"), row, mapping, descriptor, false, ancestor);
                    var input = withRemotePredicate(new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty()), Optional.of(new Term("id", 1L)));
                    var result = metadata.applyProjection(
                            session,
                            input,
                            List.of(new FieldDereference(INTEGER, new Variable("obj", row), 0)),
                            Map.of("obj", column)).orElseThrow();
                    var projected = (ElasticsearchColumnHandle) result.getAssignments().getFirst().getColumn();
                    assertThat(projected.name()).isEqualTo("obj.value");
                    assertThat(projected.supportsQueryShapes()).isEqualTo(ancestor && child);
                    assertThat(projected.elasticsearchType()).isEqualTo(new IndexMetadata.PrimitiveType("integer"));
                    assertThat(((ElasticsearchTableHandle) result.getHandle()).remotePredicate()).isEqualTo(input.remotePredicate());
                }
            }
        }
        finally {
            client.close();
        }
    }
}
