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

import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.DISABLED;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchDynamicFilterPlanner
{
    private static final ElasticsearchColumnHandle ID = new ElasticsearchColumnHandle(
            List.of("UserID"),
            INTEGER,
            new PrimitiveType("integer"),
            new IntegerDecoder.Descriptor("UserID"),
            true);
    private static final ElasticsearchColumnHandle ANALYZED_TEXT = new ElasticsearchColumnHandle(
            List.of("Message"),
            VARCHAR,
            new PrimitiveType("text"),
            new VarcharDecoder.Descriptor("Message"),
            false);

    @Test
    public void testSmallDiscreteFilterUsesTermsAndPreservesCase()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        Domain domain = Domain.multipleValues(INTEGER, values(10));

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)), DISABLED).orElseThrow();

        assertThat(predicate).isInstanceOf(ElasticsearchRemotePredicate.Terms.class);
        ElasticsearchRemotePredicate.Terms terms = (ElasticsearchRemotePredicate.Terms) predicate;
        assertThat(terms.field()).isEqualTo("UserID");
        assertThat(terms.values()).hasSize(10);
    }

    @Test
    public void testLargeDiscreteFilterIsBatchedWithoutRangeCompaction()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(10_000, 1_000, 1_048_576);
        Domain domain = Domain.multipleValues(INTEGER, values(2_500));

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)), DISABLED).orElseThrow();

        assertThat(predicate).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        List<ElasticsearchRemotePredicate> batches = ((ElasticsearchRemotePredicate.Or) predicate).predicates();
        assertThat(batches).hasSize(3).allMatch(ElasticsearchRemotePredicate.Terms.class::isInstance);
        assertThat(((ElasticsearchRemotePredicate.Terms) batches.get(0)).values()).hasSize(1_000);
        assertThat(((ElasticsearchRemotePredicate.Terms) batches.get(1)).values()).hasSize(1_000);
        assertThat(((ElasticsearchRemotePredicate.Terms) batches.get(2)).values()).hasSize(500);
    }

    @Test
    public void testRangeDynamicFilterStaysRange()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        Domain domain = Domain.create(ValueSet.ofRanges(Range.range(INTEGER, 10L, true, 20L, false)), false);

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)), DISABLED).orElseThrow();

        assertThat(predicate).isInstanceOf(ElasticsearchRemotePredicate.Range.class);
    }

    @Test
    public void testTooManyValuesFallBackWithoutNarrowingResults()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(5, 2, 1_048_576);
        Domain domain = Domain.multipleValues(INTEGER, values(6));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)), DISABLED)).isEmpty();
    }

    @Test
    public void testQueryByteBudgetFallsBackWithoutNarrowingResults()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(100, 100, 16);
        Domain domain = Domain.multipleValues(INTEGER, values(10));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)), DISABLED)).isEmpty();
    }

    @Test
    public void testAnalyzedTextHasStricterBound()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        List<io.airlift.slice.Slice> values = IntStream.range(0, 600)
                .mapToObj(value -> utf8Slice("value-" + value))
                .toList();
        Domain domain = Domain.multipleValues(VARCHAR, values);

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ANALYZED_TEXT, domain)), SAFE)).isEmpty();
    }

    private static List<Long> values(int count)
    {
        return IntStream.range(0, count)
                .mapToObj(value -> (long) value)
                .toList();
    }
}
