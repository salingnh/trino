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

import io.airlift.log.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "elasticsearch.benchmark", matches = "true")
public class TestSearchResponseDecoderBenchmark
{
    private static final Logger LOG = Logger.get(TestSearchResponseDecoderBenchmark.class);

    @Test
    public void measureRepresentativePages()
    {
        for (int count : List.of(100, 1000)) {
            String hits = IntStream.range(0, count)
                    .mapToObj(id -> """
                                    {"_id":"%s","sort":[%s],"_source":{"id":%s,"text":"ngô văn %s","nested":{"tags":["a",null,"b"],"timestamp":"2026-09-06T00:00:00Z"}}}
                                    """.formatted(id, id, id, "payload".repeat(100)).strip())
                    .collect(joining(","));
            byte[] json = ("{\"hits\":{\"hits\":[" + hits + "]}}").getBytes(UTF_8);
            List<SearchResponseDecoder> decoders = List.of(new MaterializedSearchResponseDecoder(), new StreamingSearchResponseDecoder());
            for (int round = 0; round < 2; round++) {
                for (SearchResponseDecoder decoder : round == 0 ? decoders : decoders.reversed()) {
                    for (int warmup = 0; warmup < 30; warmup++) {
                        assertThat(decoder.decode(new ByteArrayInputStream(json)).hits()).hasSize(count);
                    }
                    long collections = gcCollections();
                    long start = System.nanoTime();
                    for (int iteration = 0; iteration < 100; iteration++) {
                        assertThat(decoder.decode(new ByteArrayInputStream(json)).hits()).hasSize(count);
                    }
                    LOG.info("decoder=%s hits=%s round=%s ns/page=%s gcCollections=%s",
                            decoder.getClass().getSimpleName(),
                            count,
                            round,
                            (System.nanoTime() - start) / 100,
                            gcCollections() - collections);
                }
            }
        }
    }

    private static long gcCollections()
    {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
}
