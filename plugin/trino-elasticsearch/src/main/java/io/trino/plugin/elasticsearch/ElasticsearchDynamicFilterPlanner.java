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
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.VarcharType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.conjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.disjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.getValue;

/**
 * Converts runtime dynamic filters into bounded Elasticsearch predicates without TupleDomain simplification.
 *
 * <p>The old execution path simplified domains at 1000 values, which could turn a selective discrete join-key set
 * into a broad range. This planner keeps discrete values discrete and batches them into native {@code terms} queries.
 * If a predicate exceeds the configured safety budget, it is omitted; the join still re-checks every row, so this
 * fallback affects only performance, never correctness.</p>
 */
final class ElasticsearchDynamicFilterPlanner
{
    static final int DEFAULT_MAX_VALUES = 50_000;
    static final int DEFAULT_TERMS_BATCH_SIZE = 1_000;
    static final int DEFAULT_MAX_QUERY_BYTES = 1_048_576;
    private static final int MAX_ANALYZED_TEXT_VALUES = 512;

    private final int maxValues;
    private final int termsBatchSize;
    private final int maxQueryBytes;

    public ElasticsearchDynamicFilterPlanner()
    {
        this(DEFAULT_MAX_VALUES, DEFAULT_TERMS_BATCH_SIZE, DEFAULT_MAX_QUERY_BYTES);
    }

    ElasticsearchDynamicFilterPlanner(int maxValues, int termsBatchSize, int maxQueryBytes)
    {
        if (maxValues < 1 || termsBatchSize < 1 || maxQueryBytes < 1) {
            throw new IllegalArgumentException("Dynamic filter limits must be positive");
        }
        this.maxValues = maxValues;
        this.termsBatchSize = termsBatchSize;
        this.maxQueryBytes = maxQueryBytes;
    }

    public Optional<ElasticsearchRemotePredicate> plan(
            TupleDomain<ElasticsearchColumnHandle> dynamicFilter,
            FullTextPushdownMode fullTextMode)
    {
        if (dynamicFilter.isAll() || dynamicFilter.isNone()) {
            return Optional.empty();
        }

        List<ElasticsearchRemotePredicate> predicates = new ArrayList<>();
        int usedBytes = 0;
        for (var entry : dynamicFilter.getDomains().orElseThrow().entrySet()) {
            Optional<ElasticsearchRemotePredicate> predicate = planDomain(entry.getKey(), entry.getValue(), fullTextMode);
            if (predicate.isEmpty()) {
                continue;
            }

            int predicateBytes = ElasticsearchRemotePredicateQueryBuilder.build(predicate.orElseThrow())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8)
                    .length;
            if (predicateBytes > maxQueryBytes - usedBytes) {
                continue;
            }
            predicates.add(predicate.orElseThrow());
            usedBytes += predicateBytes;
        }
        return conjunction(predicates);
    }

    private Optional<ElasticsearchRemotePredicate> planDomain(
            ElasticsearchColumnHandle column,
            Domain domain,
            FullTextPushdownMode fullTextMode)
    {
        if (column.supportsPredicates()) {
            return planExactDomain(column, domain);
        }
        if (fullTextMode != FullTextPushdownMode.DISABLED && column.type() instanceof VarcharType) {
            if (!domain.getValues().isDiscreteSet() || domain.getValues().getDiscreteSet().size() > MAX_ANALYZED_TEXT_VALUES) {
                return Optional.empty();
            }
            return ElasticsearchRemotePredicateTranslator.translateDomain(column, domain);
        }
        return Optional.empty();
    }

    private Optional<ElasticsearchRemotePredicate> planExactDomain(ElasticsearchColumnHandle column, Domain domain)
    {
        if (!domain.getValues().isDiscreteSet()) {
            return ElasticsearchRemotePredicateTranslator.translateDomain(column, domain);
        }

        List<Object> values = domain.getValues().getDiscreteSet();
        if (values.size() > maxValues) {
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
}
