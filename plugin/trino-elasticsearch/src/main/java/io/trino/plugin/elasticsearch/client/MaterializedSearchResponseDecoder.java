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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonMapperProvider;
import io.trino.spi.TrinoException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

import static io.trino.plugin.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_INVALID_RESPONSE;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class MaterializedSearchResponseDecoder
        implements SearchResponseDecoder
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();

    @Override
    public SearchResult decode(InputStream input, Consumer<String> scrollContext, Consumer<String> pointInTimeContext)
    {
        try {
            JsonNode root = JSON_MAPPER.readTree(input);
            Optional.ofNullable(root.get("_scroll_id")).map(JsonNode::asText).ifPresent(scrollContext);
            Optional.ofNullable(root.get("pit_id")).map(JsonNode::asText).ifPresent(pointInTimeContext);
            if (root.path("timed_out").asBoolean() || root.path("_shards").path("failed").asInt() > 0) {
                throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "Search returned incomplete results");
            }

            Optional<String> scrollId = Optional.ofNullable(root.get("_scroll_id"))
                    .map(JsonNode::asText);

            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray()) {
                throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "Search response has no hit array");
            }
            ImmutableList.Builder<SearchDocument> hits = ImmutableList.builder();

            for (JsonNode hitNode : hitsNode) {
                hits.add(decodeHit(hitNode));
            }

            ImmutableList.Builder<JsonNode> searchAfter = ImmutableList.builder();
            if (!hitsNode.isEmpty()) {
                hitsNode.get(hitsNode.size() - 1).path("sort").forEach(searchAfter::add);
            }
            return new SearchResult(scrollId, hits.build(), Optional.ofNullable(root.get("pit_id")).map(JsonNode::asText), searchAfter.build());
        }
        catch (IOException e) {
            throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, e);
        }
    }

    static SearchDocument decodeHit(JsonNode hitNode)
    {
        String id = hitNode.get("_id").asText();
        float score = hitNode.has("_score") && !hitNode.get("_score").isNull()
                ? hitNode.get("_score").floatValue()
                : Float.NaN;

        JsonNode sourceNode = hitNode.get("_source");
        String sourceAsString = sourceNode != null ? sourceNode.toString() : "{}";
        long sourceLength = sourceAsString.getBytes(UTF_8).length;
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceAsMap = sourceNode != null
                ? JSON_MAPPER.convertValue(sourceNode, Map.class)
                : ImmutableMap.of();

        Map<String, List<Object>> fields = ImmutableMap.of();
        if (hitNode.has("fields")) {
            Map<String, List<Object>> fieldsMap = new LinkedHashMap<>();
            for (Entry<String, JsonNode> fieldEntry : hitNode.get("fields").properties()) {
                List<Object> values = new ArrayList<>();
                for (JsonNode valueNode : fieldEntry.getValue()) {
                    values.add(nodeToValue(valueNode));
                }
                fieldsMap.put(fieldEntry.getKey(), values);
            }
            fields = ImmutableMap.copyOf(fieldsMap);
        }

        return new SearchDocument(id, score, sourceAsMap, sourceAsString, sourceLength, fields);
    }

    private static Object nodeToValue(JsonNode node)
    {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNull()) {
            return null;
        }
        throw new IllegalArgumentException("Unsupported node type: " + node.getClass());
    }
}
