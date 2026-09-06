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

import io.trino.spi.TrinoException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestSearchResponseDecoder
{
    private static final List<SearchResponseDecoder> DECODERS = List.of(new MaterializedSearchResponseDecoder(), new StreamingSearchResponseDecoder());

    @Test
    public void testEquivalentNestedSourceAndDocValues()
    {
        String json =
                """
                {
                  "pit_id":"context", "_scroll_id":"scroll", "timed_out":false, "_shards":{"failed":0},
                  "hits":{"total":{"value":2,"relation":"eq"},"hits":[
                    {"_id":"a","_score":1.5,"sort":[12,"ngô",null],"_source":{
                      "nested":{"date":"2026-09-06T01:02:03Z","binary":"YWJj","missing":null},
                      "array":[1,null,3],"objects":[{"a":true},null],"raw":"ngô văn",
                      "big":9223372036854775807
                    },"fields":{"date":["2026-09-06T01:02:03.000Z"],"nullable":[null],"number":[42]}},
                    {"_id":"b","_score":null,"sort":[13,"văn",null]}
                  ]},"ignored":{"deep":[1,2,3]}
                }
                """;
        SearchResult materialized = decode(DECODERS.getFirst(), json);
        assertThat(decode(DECODERS.getLast(), json)).isEqualTo(materialized);
        assertThat(materialized.hits()).hasSize(2);
        assertThat(materialized.hits().getFirst().sourceLength()).isEqualTo(materialized.hits().getFirst().sourceAsString().getBytes(UTF_8).length);
        assertThat(materialized.hits().getLast().sourceAsMap()).isEmpty();
        assertThat(materialized.searchAfter()).hasSize(3);
    }

    @Test
    public void testEmptyPage()
    {
        for (SearchResponseDecoder decoder : DECODERS) {
            assertThat(decode(decoder, "{\"hits\":{\"hits\":[]}}").hits()).isEmpty();
        }
    }

    @Test
    public void testIncompleteResponseRetainsContextsRegardlessOfFieldOrder()
    {
        for (SearchResponseDecoder decoder : DECODERS) {
            for (String failure : List.of("\"timed_out\":true", "\"_shards\":{\"failed\":1}")) {
                List<String> contexts = new ArrayList<>();
                byte[] json = ("{" + failure + ",\"hits\":{\"hits\":[]},\"_scroll_id\":\"scroll\",\"pit_id\":\"pit\"}").getBytes(UTF_8);
                assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(json), contexts::add, contexts::add))
                        .isInstanceOf(TrinoException.class)
                        .hasMessageContaining("incomplete");
                assertThat(contexts).containsExactly("scroll", "pit");
            }
        }
    }

    @Test
    public void testHitFailureRetainsLaterContexts()
    {
        for (SearchResponseDecoder decoder : DECODERS) {
            for (String hit : List.of("{\"_id\":\"1\",\"_source\":42}", "{\"_source\":{}}")) {
                List<String> contexts = new ArrayList<>();
                byte[] json = ("{\"hits\":{\"hits\":[" + hit + ",{\"_id\":\"2\",\"_source\":{}}]},\"_scroll_id\":\"scroll\",\"pit_id\":\"pit\"}").getBytes(UTF_8);
                assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(json), contexts::add, contexts::add))
                        .isInstanceOf(RuntimeException.class);
                assertThat(contexts).containsExactly("scroll", "pit");
            }
        }
    }

    @Test
    public void testContextOwnershipPrecedesValidation()
    {
        for (SearchResponseDecoder decoder : DECODERS) {
            List<String> contexts = new ArrayList<>();
            byte[] json = "{\"_scroll_id\":\"scroll\",\"pit_id\":\"pit\",\"timed_out\":true,\"hits\":{\"hits\":[]}}".getBytes(UTF_8);
            assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(json), contexts::add, contexts::add))
                    .isInstanceOf(TrinoException.class);
            assertThat(contexts).containsExactly("scroll", "pit");
        }
    }

    @Test
    public void testIncompleteSearchIsRejected()
    {
        for (SearchResponseDecoder decoder : DECODERS) {
            assertThatThrownBy(() -> decode(decoder, "{\"timed_out\":true,\"hits\":{\"hits\":[]}}"))
                    .isInstanceOf(TrinoException.class).hasMessageContaining("incomplete");
            assertThatThrownBy(() -> decode(decoder, "{\"hits\":{\"hits\":[]},\"_shards\":{\"failed\":1}}"))
                    .isInstanceOf(TrinoException.class).hasMessageContaining("incomplete");
            assertThatThrownBy(() -> decode(decoder, "{\"hits\":"))
                    .isInstanceOf(TrinoException.class);
        }
    }

    private static SearchResult decode(SearchResponseDecoder decoder, String json)
    {
        return decoder.decode(new ByteArrayInputStream(json.getBytes(UTF_8)));
    }
}
