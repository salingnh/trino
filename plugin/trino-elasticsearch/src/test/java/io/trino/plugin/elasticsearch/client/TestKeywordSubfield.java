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
import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonMapperProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.trino.plugin.elasticsearch.client.ElasticsearchClient.keywordSubfield;
import static org.assertj.core.api.Assertions.assertThat;

public class TestKeywordSubfield
{
    private static final JsonMapper JSON = new JsonMapperProvider().get();

    @Test
    public void testMissingSubfields()
            throws IOException
    {
        assertThat(keywordSubfield(JSON.readTree("{\"type\":\"text\"}"))).isEmpty();
    }

    @Test
    public void testKeywordSubfieldName()
            throws IOException
    {
        assertThat(keywordSubfield(JSON.readTree("""
                {"type":"text","fields":{"RawValue":{"type":"keyword"}}}
                """))).contains("RawValue");
    }

    @Test
    public void testDeterministicSelection()
            throws IOException
    {
        assertThat(keywordSubfield(JSON.readTree("""
                {"type":"text","fields":{"z":{"type":"keyword"},"a":{"type":"keyword"}}}
                """))).contains("a");
        assertThat(keywordSubfield(JSON.readTree("""
                {"type":"text","fields":{"a":{"type":"keyword"},"z":{"type":"keyword"}}}
                """))).contains("a");
    }

    @Test
    public void testUnsupportedMappingOptions()
            throws IOException
    {
        for (String mapping : ImmutableList.of(
                "{\"type\":\"keyword\",\"ignore_above\":256}",
                "{\"type\":\"keyword\",\"normalizer\":\"lowercase\"}",
                "{\"type\":\"keyword\",\"null_value\":\"NULL\"}",
                "{\"type\":\"keyword\",\"index\":false}",
                "{\"type\":\"keyword\",\"script\":\"emit('x')\"}",
                "{\"type\":\"text\",\"analyzer\":\"english\"}")) {
            assertThat(keywordSubfield(JSON.readTree("{\"fields\":{\"raw\":" + mapping + "}}")))
                    .as("subfield mapping: %s", mapping)
                    .isEmpty();
        }
    }

    @Test
    public void testSkipUnsupportedSubfield()
            throws IOException
    {
        assertThat(keywordSubfield(JSON.readTree("""
                {"type":"text","fields":{
                  "a":{"type":"keyword","ignore_above":256},
                  "raw":{"type":"keyword","index":true,"doc_values":false}
                }}
                """))).contains("raw");
    }

    @Test
    public void testMetadataSerialization()
    {
        JsonCodec<IndexMetadata> codec = jsonCodec(IndexMetadata.class);
        IndexMetadata metadata = new IndexMetadata(new IndexMetadata.ObjectType(ImmutableList.of(
                new IndexMetadata.Field(false, false, "name", new IndexMetadata.PrimitiveType("text", Optional.of("raw"))),
                new IndexMetadata.Field(false, false, "plain", new IndexMetadata.PrimitiveType("text")))));
        assertThat(codec.fromJson(codec.toJson(metadata))).isEqualTo(metadata);
    }
}
