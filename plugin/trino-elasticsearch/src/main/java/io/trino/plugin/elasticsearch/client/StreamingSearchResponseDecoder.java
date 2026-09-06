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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonMapperProvider;
import io.trino.spi.TrinoException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS;
import static io.trino.plugin.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_INVALID_RESPONSE;

/**
 * Keeps at most one hit tree alive in addition to the decoded page.
 */
public final class StreamingSearchResponseDecoder
        implements SearchResponseDecoder
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get().rebuild().disable(FAIL_ON_TRAILING_TOKENS).build();
    private static final ObjectReader HIT_READER = JSON_MAPPER.readerFor(JsonNode.class);

    @Override
    public SearchResult decode(InputStream input, Consumer<String> scrollContext, Consumer<String> pointInTimeContext)
    {
        Optional<String> scrollId = Optional.empty();
        Optional<String> pointInTimeId = Optional.empty();
        ImmutableList.Builder<SearchDocument> hits = ImmutableList.builder();
        List<JsonNode> searchAfter = ImmutableList.of();
        boolean foundHits = false;
        boolean incomplete = false;
        RuntimeException hitFailure = null;
        try (JsonParser parser = JSON_MAPPER.createParser(input)) {
            expect(parser.nextToken(), JsonToken.START_OBJECT);
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                expect(parser.currentToken(), JsonToken.FIELD_NAME);
                String name = parser.currentName();
                parser.nextToken();
                switch (name) {
                    case "_scroll_id" -> {
                        scrollId = Optional.of(parser.getText());
                        scrollId.ifPresent(scrollContext);
                    }
                    case "pit_id" -> {
                        pointInTimeId = Optional.of(parser.getText());
                        pointInTimeId.ifPresent(pointInTimeContext);
                    }
                    case "timed_out" -> {
                        if (parser.getBooleanValue()) {
                            incomplete = true;
                        }
                    }
                    case "_shards" -> {
                        JsonNode shards = JSON_MAPPER.readTree(parser);
                        if (shards.path("failed").asInt() > 0) {
                            incomplete = true;
                        }
                    }
                    case "hits" -> {
                        expect(parser.currentToken(), JsonToken.START_OBJECT);
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            expect(parser.currentToken(), JsonToken.FIELD_NAME);
                            String hitsField = parser.currentName();
                            parser.nextToken();
                            if (!hitsField.equals("hits")) {
                                parser.skipChildren();
                                continue;
                            }
                            expect(parser.currentToken(), JsonToken.START_ARRAY);
                            foundHits = true;
                            JsonNode lastSort = null;
                            // Share one Jackson deserialization context for the hit sequence instead of
                            // allocating a fresh context for each readTree call. The outer parser owns the stream.
                            parser.clearCurrentToken();
                            try (MappingIterator<JsonNode> values = HIT_READER.readValues(parser)) {
                                while (values.hasNextValue()) {
                                    expect(parser.currentToken(), JsonToken.START_OBJECT);
                                    JsonNode hit = values.nextValue();
                                    if (hitFailure != null) {
                                        continue;
                                    }
                                    try {
                                        hits.add(MaterializedSearchResponseDecoder.decodeHit(hit));
                                    }
                                    catch (RuntimeException e) {
                                        // The hit tree is consumed. Drain the envelope to transfer later context IDs,
                                        // but never return a partial page or decode more hits after the first failure.
                                        hitFailure = e;
                                        continue;
                                    }
                                    lastSort = hit.get("sort");
                                }
                            }
                            // Only the final hit supplies the continuation token; do not copy every hit's sort array.
                            ImmutableList.Builder<JsonNode> sortValues = ImmutableList.builder();
                            if (lastSort != null) {
                                lastSort.forEach(sortValues::add);
                            }
                            searchAfter = sortValues.build();
                        }
                    }
                    default -> parser.skipChildren();
                }
            }
            // JSON field order is not significant. Retain context IDs even if failure metadata preceded them,
            // so the owning strategy can close the latest remote context before propagating the failure.
            if (incomplete) {
                throw incomplete();
            }
            if (!foundHits || parser.nextToken() != null) {
                throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "Invalid search response envelope");
            }
            if (hitFailure != null) {
                throw hitFailure;
            }
            return new SearchResult(scrollId, hits.build(), pointInTimeId, searchAfter);
        }
        catch (IOException e) {
            throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, e);
        }
    }

    private static void expect(JsonToken actual, JsonToken expected)
    {
        if (actual != expected) {
            throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "Invalid search response token: " + actual);
        }
    }

    private static TrinoException incomplete()
    {
        return new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "Search returned incomplete results");
    }
}
