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

import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchRemotePredicateNormalizer
{
    @Test
    public void testCompatibleLongRangesMergeToIntersection()
    {
        ElasticsearchRemotePredicate.Range lower = range("score", 10L, true, null, false);
        ElasticsearchRemotePredicate.Range upper = range("score", null, false, 20L, false);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(lower, upper)))
                .contains(range("score", 10L, true, 20L, false));
    }

    @Test
    public void testStrongerBoundsWinAndEqualBoundsTightenInclusivity()
    {
        ElasticsearchRemotePredicate.Range first = range("score", 10L, true, 30L, true);
        ElasticsearchRemotePredicate.Range second = range("score", 10L, false, 20L, false);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(first, second)))
                .contains(range("score", 10L, false, 20L, false));
    }

    @Test
    public void testCompatibleDoubleRangesMerge()
    {
        ElasticsearchRemotePredicate.Range first = range("ratio", 1.5, true, null, false);
        ElasticsearchRemotePredicate.Range second = range("ratio", null, false, 2.5, true);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(first, second)))
                .contains(range("ratio", 1.5, true, 2.5, true));
    }

    @Test
    public void testDifferentFieldsAreNotMerged()
    {
        ElasticsearchRemotePredicate.Range first = range("a", 1L, true, null, false);
        ElasticsearchRemotePredicate.Range second = range("b", null, false, 10L, true);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(first, second)))
                .contains(new ElasticsearchRemotePredicate.And(List.of(first, second)));
    }

    @Test
    public void testStringRangesAreNotMergedWithoutOrderingProof()
    {
        ElasticsearchRemotePredicate.Range first = range("timestamp", "2026-01-01T00:00:00", true, null, false);
        ElasticsearchRemotePredicate.Range second = range("timestamp", null, false, "2026-12-31T23:59:59", true);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(first, second)))
                .contains(new ElasticsearchRemotePredicate.And(List.of(first, second)));
    }

    @Test
    public void testContradictoryRangesAreNotCollapsedWithoutMatchNoneIr()
    {
        ElasticsearchRemotePredicate.Range first = range("score", 20L, true, null, false);
        ElasticsearchRemotePredicate.Range second = range("score", null, false, 10L, true);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(first, second)))
                .contains(new ElasticsearchRemotePredicate.And(List.of(first, second)));
    }

    @Test
    public void testEnforcedRangeIsNotMergedWithExactRange()
    {
        ElasticsearchRemotePredicate.Range exact = range("score", 10L, true, null, false);
        ElasticsearchRemotePredicate.Enforced prefilter = new ElasticsearchRemotePredicate.Enforced(
                range("score", null, false, 20L, true),
                PREFILTER);

        assertThat(ElasticsearchRemotePredicateNormalizer.and(List.of(exact, prefilter)))
                .contains(new ElasticsearchRemotePredicate.And(List.of(exact, prefilter)));
    }

    private static ElasticsearchRemotePredicate.Range range(
            String field,
            Object lower,
            boolean lowerInclusive,
            Object upper,
            boolean upperInclusive)
    {
        return new ElasticsearchRemotePredicate.Range(
                field,
                Optional.ofNullable(lower).map(value -> new ElasticsearchRemotePredicate.Bound(value, lowerInclusive)),
                Optional.ofNullable(upper).map(value -> new ElasticsearchRemotePredicate.Bound(value, upperInclusive)));
    }
}
