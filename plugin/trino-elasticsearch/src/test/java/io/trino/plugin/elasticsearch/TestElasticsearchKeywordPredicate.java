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

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.elasticsearch.client.IndexMetadata;
import io.trino.plugin.elasticsearch.decoders.ArrayDecoder;
import io.trino.plugin.elasticsearch.decoders.RawJsonDecoder;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.ArrayType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSearchQuery;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchKeywordPredicate
{
    private static final JsonMapper JSON = new JsonMapperProvider().get();
    private static final IndexMetadata.PrimitiveType TEXT_WITH_KEYWORD = new IndexMetadata.PrimitiveType("text", Optional.of("raw"));
    private static final ElasticsearchColumnHandle NAME = new ElasticsearchColumnHandle(
            ImmutableList.of("name"), VARCHAR, TEXT_WITH_KEYWORD, new VarcharDecoder.Descriptor("name"), false);

    @Test
    public void testEqualityQueryKeepsMissingSubfield()
            throws IOException
    {
        Domain domain = Domain.singleValue(VARCHAR, utf8Slice("Alice"));
        assertThat(ElasticsearchKeywordPredicate.isSupported(NAME, domain)).isTrue();
        assertThat(buildSearchQuery(TupleDomain.withColumnDomains(ImmutableMap.of(NAME, domain)), Optional.empty(), ImmutableMap.of()))
                .isEqualTo(JSON.readTree("""
                        {"bool":{"filter":[{"bool":{
                          "should":[
                            {"terms":{"name.raw":["Alice"]}},
                            {"bool":{"must_not":[{"exists":{"field":"name.raw"}}]}}
                          ],
                          "minimum_should_match":1
                        }}]}}
                        """));
        assertThat(NAME.name()).isEqualTo("name");
        assertThat(NAME.supportsPredicates()).isFalse();
    }

    @Test
    public void testInValuesAreNotAnalyzed()
            throws IOException
    {
        Domain domain = Domain.multipleValues(VARCHAR, ImmutableList.of(utf8Slice(""), utf8Slice("New York"), utf8Slice("ngô văn")));
        assertThat(ElasticsearchKeywordPredicate.buildQuery(NAME, domain).at("/bool/should/0/terms/name.raw"))
                .isEqualTo(JSON.readTree("[\"\",\"New York\",\"ngô văn\"]"));
    }

    @Test
    public void testUnsupportedDomains()
    {
        for (Domain domain : ImmutableList.of(
                Domain.all(VARCHAR),
                Domain.none(VARCHAR),
                Domain.onlyNull(VARCHAR),
                Domain.notNull(VARCHAR),
                Domain.singleValue(VARCHAR, utf8Slice("Alice"), true),
                Domain.create(ValueSet.ofRanges(Range.greaterThan(VARCHAR, utf8Slice("Alice"))), false),
                Domain.singleValue(VARCHAR, utf8Slice("Alice")).complement())) {
            assertThat(ElasticsearchKeywordPredicate.isSupported(NAME, domain)).as("domain: %s", domain).isFalse();
        }
    }

    @Test
    public void testUnsupportedColumns()
    {
        Domain domain = Domain.singleValue(VARCHAR, utf8Slice("Alice"));
        for (ElasticsearchColumnHandle column : ImmutableList.of(
                new ElasticsearchColumnHandle(ImmutableList.of("name"), VARCHAR, new IndexMetadata.PrimitiveType("text"), new VarcharDecoder.Descriptor("name"), false),
                new ElasticsearchColumnHandle(ImmutableList.of("name"), VARCHAR, TEXT_WITH_KEYWORD, new RawJsonDecoder.Descriptor("name"), false),
                new ElasticsearchColumnHandle(ImmutableList.of("name"), new ArrayType(VARCHAR), TEXT_WITH_KEYWORD, new ArrayDecoder.Descriptor(new VarcharDecoder.Descriptor("name")), false),
                new ElasticsearchColumnHandle(ImmutableList.of("person", "name"), VARCHAR, TEXT_WITH_KEYWORD, new VarcharDecoder.Descriptor("person.name"), false))) {
            assertThat(ElasticsearchKeywordPredicate.isSupported(column, domain)).as("column: %s", column).isFalse();
        }
    }

    @Test
    public void testNativeKeywordQueryIsUnchanged()
            throws IOException
    {
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                ImmutableList.of("name"), VARCHAR, new IndexMetadata.PrimitiveType("keyword"), new VarcharDecoder.Descriptor("name"), true);
        Domain domain = Domain.singleValue(VARCHAR, utf8Slice("Alice"));
        assertThat(buildSearchQuery(TupleDomain.withColumnDomains(ImmutableMap.of(column, domain)), Optional.empty(), ImmutableMap.of()))
                .isEqualTo(JSON.readTree("{\"bool\":{\"filter\":[{\"term\":{\"name\":\"Alice\"}}]}}"));
    }

    @Test
    public void testValueLimits()
    {
        Domain tooManyValues = Domain.multipleValues(VARCHAR, IntStream.range(0, 1001)
                .mapToObj(value -> utf8Slice(Integer.toString(value)))
                .collect(toImmutableList()));
        assertThat(ElasticsearchKeywordPredicate.isSupported(NAME, tooManyValues)).isFalse();
        assertThat(ElasticsearchKeywordPredicate.isSupported(NAME, Domain.singleValue(VARCHAR, utf8Slice("x".repeat(32767))))).isFalse();
    }
}
