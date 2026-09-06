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
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.DynamicFilterOutcome.EMPTY;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.DynamicFilterOutcome.PARTIALLY_PUSHED;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.DynamicFilterOutcome.PUSHED;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.DynamicFilterOutcome.REJECTED;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.DynamicFilterOutcome.UNRESTRICTED;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.conjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.disjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.getValue;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static java.util.Objects.requireNonNull;

/**
 * Converts runtime dynamic filters into bounded exact Elasticsearch predicates without TupleDomain simplification.
 *
 * <p>The old execution path simplified domains at 1000 values, which could turn a selective discrete join-key set
 * into a broad range. This planner keeps discrete values discrete and batches them into native {@code terms} queries.
 * If a predicate exceeds the configured safety budget, it is omitted; the join still re-checks every row, so this
 * fallback affects only performance, never correctness.</p>
 *
 * <p>Analyzed-text-only fields are deliberately not supported here, even when approximate full-text pushdown is
 * enabled for ordinary query predicates. A join can re-check false positives, but it cannot recover a row that an
 * approximate dynamic filter removed as a false negative.</p>
 */
final class ElasticsearchDynamicFilterPlanner
{
    private static final int MAX_DATE_TERMS = 1_000;

    static final int DEFAULT_MAX_VALUES = 50_000;
    static final int DEFAULT_TERMS_BATCH_SIZE = 1_000;
    static final int DEFAULT_MAX_QUERY_BYTES = 1_048_576;

    private final int maxValues;
    private final int termsBatchSize;
    private final int maxQueryBytes;
    private final ElasticsearchPushdownDiagnostics diagnostics;

    public ElasticsearchDynamicFilterPlanner()
    {
        this(DEFAULT_MAX_VALUES, DEFAULT_TERMS_BATCH_SIZE, DEFAULT_MAX_QUERY_BYTES, new ElasticsearchPushdownDiagnostics());
    }

    ElasticsearchDynamicFilterPlanner(int maxValues, int termsBatchSize, int maxQueryBytes)
    {
        this(maxValues, termsBatchSize, maxQueryBytes, new ElasticsearchPushdownDiagnostics());
    }

    ElasticsearchDynamicFilterPlanner(int maxValues, int termsBatchSize, int maxQueryBytes, ElasticsearchPushdownDiagnostics diagnostics)
    {
        if (maxValues < 1 || termsBatchSize < 1 || maxQueryBytes < 1) {
            throw new IllegalArgumentException("Dynamic filter limits must be positive");
        }
        this.maxValues = maxValues;
        this.termsBatchSize = termsBatchSize;
        this.maxQueryBytes = maxQueryBytes;
        this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");
    }

    public Optional<ElasticsearchRemotePredicate> plan(TupleDomain<ElasticsearchColumnHandle> dynamicFilter)
    {
        if (dynamicFilter.isAll() || dynamicFilter.isNone()) {
            diagnostics.recordDynamicFilterPlan(dynamicFilter.isNone() ? EMPTY : UNRESTRICTED, 0, 0, 0, 0, 0, 0, 0);
            return Optional.empty();
        }

        Map<ElasticsearchColumnHandle, Domain> domains = dynamicFilter.getDomains().orElseThrow();
        List<ElasticsearchRemotePredicate> predicates = new ArrayList<>();
        long valuesReceived = 0;
        long valuesPushed = 0;
        long termsBatches = 0;
        long rejectedDomains = 0;
        int usedBytes = 0;
        for (var entry : domains.entrySet()) {
            valuesReceived += countDiscreteValues(entry.getValue());
            Optional<ElasticsearchRemotePredicate> predicate = planDomain(entry.getKey(), entry.getValue());
            if (predicate.isEmpty()) {
                rejectedDomains++;
                continue;
            }

            ElasticsearchRemotePredicate plannedPredicate = predicate.orElseThrow();
            int predicateBytes = ElasticsearchRemotePredicateQueryBuilder.build(plannedPredicate)
            .toString()
            .getBytes(StandardCharsets.UTF_8)
            .length;
            if (predicateBytes > maxQueryBytes - usedBytes) {
                rejectedDomains++;
                continue;
            }
            predicates.add(plannedPredicate);
            usedBytes += predicateBytes;
            valuesPushed += countPushedValues(plannedPredicate);
            termsBatches += countTermsPredicates(plannedPredicate);
        }
        diagnostics.recordDynamicFilterPlan(
                predicates.isEmpty() ? REJECTED : rejectedDomains == 0 ? PUSHED : PARTIALLY_PUSHED,
                domains.size(),
                valuesReceived,
                predicates.size(),
                valuesPushed,
                termsBatches,
                rejectedDomains,
                usedBytes);
        return conjunction(predicates);
    }

    private Optional<ElasticsearchRemotePredicate> planDomain(ElasticsearchColumnHandle column, Domain domain)
    {
        if (!column.supportsPredicates()) {
            return Optional.empty();
        }
        return planExactDomain(column, domain);
    }

    private Optional<ElasticsearchRemotePredicate> planExactDomain(ElasticsearchColumnHandle column, Domain domain)
    {
        List<Object> values;
        if (domain.getValues().isDiscreteSet()) {
            values = domain.getValues().getDiscreteSet();
        }
        else {
            List<Range> ranges = domain.getValues().getRanges().getOrderedRanges();
            if (ranges.stream().anyMatch(range -> !range.isSingleValue())) {
                return ElasticsearchRemotePredicateTranslator.translateDomain(column, domain);
            }
            values = ranges.stream()
                    .map(Range::getSingleValue)
                    .toList();
        }

        // Elasticsearch rewrites date terms into Lucene clauses instead of a point-set query. Stay below the
        // traditional 1024-clause floor; omitting a dynamic filter is safe because the join re-checks every row.
        if (values.size() > maxValues
                || (column.type().equals(TIMESTAMP_MILLIS) && values.size() > Math.min(termsBatchSize, MAX_DATE_TERMS))) {
            return Optional.empty();
        }
        if (values.isEmpty()) {
            return ElasticsearchRemotePredicateTranslator.translateDomain(column, domain);
        }

        String field = column.predicateName();
        List<ElasticsearchRemotePredicate> batches = new ArrayList<>();
        for (int offset = 0; offset < values.size(); offset += termsBatchSize) {
            int end = Math.min(offset + termsBatchSize, values.size());
            List<Object> batch = values.subList(offset, end).stream()
                    .map(value -> getValue(column.type(), value))
                    .toList();
            if (batch.size() == 1) {
                batches.add(new ElasticsearchRemotePredicate.Term(field, batch.getFirst()));
            }
            else {
                batches.add(new ElasticsearchRemotePredicate.Terms(field, batch));
            }
        }

        Optional<ElasticsearchRemotePredicate> valuesPredicate = disjunction(batches);
        if (!domain.isNullAllowed()) {
            return valuesPredicate;
        }
        return disjunction(List.of(
                valuesPredicate.orElseThrow(),
                new ElasticsearchRemotePredicate.Not(new ElasticsearchRemotePredicate.Exists(field))));
    }

    private static long countDiscreteValues(Domain domain)
    {
        return domain.getValues().getValuesProcessor().transform(
                ranges -> ranges.getOrderedRanges().stream().allMatch(Range::isSingleValue) ? (long) ranges.getRangeCount() : 0L,
                values -> values.isInclusive() ? (long) values.getValuesCount() : 0L,
                _ -> 0L);
    }

    private static long countPushedValues(ElasticsearchRemotePredicate predicate)
    {
        return switch (predicate) {
            case ElasticsearchRemotePredicate.And and -> and.predicates().stream().mapToLong(ElasticsearchDynamicFilterPlanner::countPushedValues).sum();
            case ElasticsearchRemotePredicate.Or or -> or.predicates().stream().mapToLong(ElasticsearchDynamicFilterPlanner::countPushedValues).sum();
            case ElasticsearchRemotePredicate.Not not -> countPushedValues(not.predicate());
            case ElasticsearchRemotePredicate.Enforced enforced -> countPushedValues(enforced.predicate());
            case ElasticsearchRemotePredicate.Term _ -> 1;
            case ElasticsearchRemotePredicate.Terms terms -> terms.values().size();
            case ElasticsearchRemotePredicate.Range _,
                 ElasticsearchRemotePredicate.Prefix _,
                 ElasticsearchRemotePredicate.Regexp _,
                 ElasticsearchRemotePredicate.MatchPhrase _,
                 ElasticsearchRemotePredicate.MatchPhrasePrefix _,
                 ElasticsearchRemotePredicate.Exists _ -> 0;
        };
    }

    private static long countTermsPredicates(ElasticsearchRemotePredicate predicate)
    {
        return switch (predicate) {
            case ElasticsearchRemotePredicate.And and -> and.predicates().stream().mapToLong(ElasticsearchDynamicFilterPlanner::countTermsPredicates).sum();
            case ElasticsearchRemotePredicate.Or or -> or.predicates().stream().mapToLong(ElasticsearchDynamicFilterPlanner::countTermsPredicates).sum();
            case ElasticsearchRemotePredicate.Not not -> countTermsPredicates(not.predicate());
            case ElasticsearchRemotePredicate.Enforced enforced -> countTermsPredicates(enforced.predicate());
            case ElasticsearchRemotePredicate.Terms _ -> 1;
            case ElasticsearchRemotePredicate.Term _,
                 ElasticsearchRemotePredicate.Range _,
                 ElasticsearchRemotePredicate.Prefix _,
                 ElasticsearchRemotePredicate.Regexp _,
                 ElasticsearchRemotePredicate.MatchPhrase _,
                 ElasticsearchRemotePredicate.MatchPhrasePrefix _,
                 ElasticsearchRemotePredicate.Exists _ -> 0;
        };
    }
}
