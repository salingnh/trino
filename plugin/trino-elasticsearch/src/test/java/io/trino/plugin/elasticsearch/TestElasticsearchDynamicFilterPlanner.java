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
import io.trino.plugin.elasticsearch.decoders.TimestampDecoder;
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
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.type.JsonType.JSON;
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
    private static final ElasticsearchColumnHandle EVENT_TIME = new ElasticsearchColumnHandle(
            List.of("EventTime"),
            TIMESTAMP_MILLIS,
            new PrimitiveType("date"),
            new TimestampDecoder.Descriptor("EventTime"),
            true);

    @Test
    public void testDynamicFilterOutcomes()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(100, 10, 10_000, diagnostics);
        assertThat(planner.plan(TupleDomain.all())).isEmpty();
        assertThat(planner.plan(TupleDomain.none())).isEmpty();
        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, Domain.singleValue(INTEGER, 1L))))).isPresent();
        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ANALYZED_TEXT, Domain.singleValue(VARCHAR, utf8Slice("value")))))).isEmpty();
        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(
                ID, Domain.singleValue(INTEGER, 1L),
                ANALYZED_TEXT, Domain.singleValue(VARCHAR, utf8Slice("value")))))).isPresent();

        assertThat(diagnostics.getDynamicFilterOutcomes()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "UNRESTRICTED", 1L,
                "EMPTY", 1L,
                "PUSHED", 1L,
                "REJECTED", 1L,
                "PARTIALLY_PUSHED", 1L));
        assertThat(diagnostics.snapshot().dynamicFilterOutcomes()).isEqualTo(diagnostics.getDynamicFilterOutcomes());
        assertThat(diagnostics.getDynamicFilterPlans()).isEqualTo(5);
    }

    @Test
    public void testDiagnosticsDoNotRequireOrderableUnsupportedDomain()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(100, 10, 10_000, diagnostics);
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("payload"),
                JSON,
                new PrimitiveType("text"),
                new VarcharDecoder.Descriptor("payload"),
                false);

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(column, Domain.onlyNull(JSON))))).isEmpty();
        assertThat(diagnostics.snapshot().dynamicFilterDomainsReceived()).isEqualTo(1);
        assertThat(diagnostics.snapshot().dynamicFilterDomainsRejected()).isEqualTo(1);
        assertThat(diagnostics.snapshot().dynamicFilterValuesReceived()).isZero();
    }

    @Test
    public void testSmallDiscreteFilterUsesTermsAndPreservesCase()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        Domain domain = Domain.multipleValues(INTEGER, values(10));

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain))).orElseThrow();

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

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain))).orElseThrow();

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

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain))).orElseThrow();

        assertThat(predicate).isInstanceOf(ElasticsearchRemotePredicate.Range.class);
    }

    @Test
    public void testSingleValueRangesUseNativeTerms()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        Domain domain = Domain.create(ValueSet.ofRanges(
                Range.equal(INTEGER, 10L),
                Range.equal(INTEGER, 20L)), false);

        ElasticsearchRemotePredicate predicate = planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain))).orElseThrow();

        assertThat(predicate).isEqualTo(new ElasticsearchRemotePredicate.Terms("UserID", List.of(10L, 20L)));
    }

    @Test
    public void testTooManyValuesFallBackWithoutNarrowingResults()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(5, 2, 1_048_576);
        Domain domain = Domain.multipleValues(INTEGER, values(6));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)))).isEmpty();
    }

    @Test
    public void testLargeDateFilterFallsBackBelowLuceneClauseLimit()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(10_000, 2_000, 1_048_576);
        Domain domain = Domain.multipleValues(TIMESTAMP_MILLIS, values(1_001));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(EVENT_TIME, domain)))).isEmpty();
    }

    @Test
    public void testQueryByteBudgetFallsBackWithoutNarrowingResults()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(100, 100, 16);
        Domain domain = Domain.multipleValues(INTEGER, values(10));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)))).isEmpty();
    }

    @Test
    public void testAnalyzedTextAlwaysFallsBack()
    {
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner();
        Domain domain = Domain.multipleValues(VARCHAR, List.of(utf8Slice("Alpha"), utf8Slice("Beta")));

        // Dynamic filtering must never use approximate analyzed-text matching: false negatives would corrupt join results.
        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ANALYZED_TEXT, domain)))).isEmpty();
    }

    @Test
    public void testDynamicFilterDiagnosticsAccountBatchesAndValues()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(10_000, 1_000, 1_048_576, diagnostics);
        Domain domain = Domain.multipleValues(INTEGER, values(2_500));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)))).isPresent();

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.dynamicFilterPlans()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterDomainsReceived()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterValuesReceived()).isEqualTo(2_500);
        assertThat(snapshot.dynamicFilterPredicatesPushed()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterValuesPushed()).isEqualTo(2_500);
        assertThat(snapshot.dynamicFilterTermsBatches()).isEqualTo(3);
        assertThat(snapshot.dynamicFilterDomainsRejected()).isZero();
        assertThat(snapshot.dynamicFilterEstimatedQueryBytes()).isPositive();
    }

    @Test
    public void testDynamicFilterDiagnosticsAccountRejectedDomain()
    {
        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        ElasticsearchDynamicFilterPlanner planner = new ElasticsearchDynamicFilterPlanner(5, 2, 1_048_576, diagnostics);
        Domain domain = Domain.multipleValues(INTEGER, values(6));

        assertThat(planner.plan(TupleDomain.withColumnDomains(Map.of(ID, domain)))).isEmpty();

        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.dynamicFilterPlans()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterDomainsReceived()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterValuesReceived()).isEqualTo(6);
        assertThat(snapshot.dynamicFilterPredicatesPushed()).isZero();
        assertThat(snapshot.dynamicFilterValuesPushed()).isZero();
        assertThat(snapshot.dynamicFilterTermsBatches()).isZero();
        assertThat(snapshot.dynamicFilterDomainsRejected()).isEqualTo(1);
        assertThat(snapshot.dynamicFilterEstimatedQueryBytes()).isZero();
    }

    private static List<Long> values(int count)
    {
        return IntStream.range(0, count)
                .mapToObj(value -> (long) value)
                .toList();
    }
}
