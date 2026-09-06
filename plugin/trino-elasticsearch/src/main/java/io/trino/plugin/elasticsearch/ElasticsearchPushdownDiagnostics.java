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

import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Decision;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement;
import org.weakref.jmx.Managed;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import static java.util.Objects.requireNonNull;

/**
 * Permanent connector-wide accounting model for predicate planning and remote execution.
 *
 * <p>This class consumes the semantic models established by P0-P1.2 instead of deriving diagnostics from SQL text or
 * Elasticsearch JSON. P1.4 moves execution event production behind its search-execution lifecycle while keeping this
 * diagnostics contract unchanged except for additive counters.</p>
 */
public class ElasticsearchPushdownDiagnostics
{
    private static final Logger LOG = Logger.get(ElasticsearchPushdownDiagnostics.class);

    private final LongAdder translationNodes = new LongAdder();
    private final LongAdder exactTranslations = new LongAdder();
    private final LongAdder prefilterTranslations = new LongAdder();
    private final LongAdder approximateTranslations = new LongAdder();
    private final LongAdder residualTranslations = new LongAdder();
    private final LongAdder remainingTranslations = new LongAdder();
    private final LongAdder unsupportedTranslations = new LongAdder();
    private final LongAdder arrayMembershipTranslations = new LongAdder();
    private final LongAdder anyMatchTranslations = new LongAdder();
    private final LongAdder booleanAndTranslations = new LongAdder();
    private final LongAdder booleanOrTranslations = new LongAdder();
    private final LongAdder booleanNotTranslations = new LongAdder();
    private final EnumMap<Reason, LongAdder> translationReasonCounts = new EnumMap<>(Reason.class);
    private final EnumMap<Normalization, LongAdder> normalizationCounts = new EnumMap<>(Normalization.class);

    private final LongAdder remotePredicateNodes = new LongAdder();
    private final LongAdder renderedQueries = new LongAdder();
    private final LongAdder renderedQueryBytes = new LongAdder();
    private final LongAdder andPredicates = new LongAdder();
    private final LongAdder orPredicates = new LongAdder();
    private final LongAdder notPredicates = new LongAdder();
    private final LongAdder enforcementPredicates = new LongAdder();
    private final LongAdder termPredicates = new LongAdder();
    private final LongAdder termsPredicates = new LongAdder();
    private final LongAdder termsValues = new LongAdder();
    private final LongAdder rangePredicates = new LongAdder();
    private final LongAdder prefixPredicates = new LongAdder();
    private final LongAdder regexpPredicates = new LongAdder();
    private final LongAdder matchPhrasePredicates = new LongAdder();
    private final LongAdder matchPhrasePrefixPredicates = new LongAdder();
    private final LongAdder existsPredicates = new LongAdder();

    private final LongAdder dynamicFilterPlans = new LongAdder();
    private final EnumMap<DynamicFilterOutcome, LongAdder> dynamicFilterOutcomes = new EnumMap<>(DynamicFilterOutcome.class);
    private final LongAdder dynamicFilterDomainsReceived = new LongAdder();
    private final LongAdder dynamicFilterValuesReceived = new LongAdder();
    private final LongAdder dynamicFilterPredicatesPushed = new LongAdder();
    private final LongAdder dynamicFilterValuesPushed = new LongAdder();
    private final LongAdder dynamicFilterTermsBatches = new LongAdder();
    private final LongAdder dynamicFilterDomainsRejected = new LongAdder();
    private final LongAdder dynamicFilterEstimatedQueryBytes = new LongAdder();

    private final LongAdder searchRequests = new LongAdder();
    private final LongAdder nextPageRequests = new LongAdder();
    private final LongAdder countRequests = new LongAdder();
    private final LongAdder rowsDecoded = new LongAdder();
    private final LongAdder sourceBytesDecoded = new LongAdder();
    private final LongAdder pagesReturned = new LongAdder();
    private final LongAdder remotePagesReceived = new LongAdder();
    private final LongAdder retryAttempts = new LongAdder();
    private final LongAdder cancellations = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder clearScrollCalls = new LongAdder();

    public ElasticsearchPushdownDiagnostics()
    {
        for (Reason reason : Reason.values()) {
            translationReasonCounts.put(reason, new LongAdder());
        }
        for (DynamicFilterOutcome outcome : DynamicFilterOutcome.values()) {
            dynamicFilterOutcomes.put(outcome, new LongAdder());
        }
        for (Normalization normalization : Normalization.values()) {
            normalizationCounts.put(normalization, new LongAdder());
        }
    }

    public void recordTranslation(Decision decision)
    {
        requireNonNull(decision, "decision is null");

        translationNodes.increment();
        translationReasonCounts.get(decision.reason()).increment();
        decision.normalizations().forEach(normalization -> normalizationCounts.get(normalization).increment());
        decision.enforcement().ifPresent(this::recordEnforcement);
        if (decision.residualPresent()) {
            residualTranslations.increment();
        }
        if (decision.remainingPresent()) {
            remainingTranslations.increment();
        }
        switch (decision.reason()) {
            case EXACT_ARRAY -> arrayMembershipTranslations.increment();
            case EXACT_ANY_MATCH -> anyMatchTranslations.increment();
            case BOOLEAN_AND -> booleanAndTranslations.increment();
            case BOOLEAN_OR -> booleanOrTranslations.increment();
            case BOOLEAN_NOT_UNPROVEN -> booleanNotTranslations.increment();
            case UNSUPPORTED_DOMAIN, UNSUPPORTED_EXPRESSION -> unsupportedTranslations.increment();
            default -> {}
        }

        LOG.debug(
                "Predicate translation: reason=%s, enforcement=%s, remote=%s, remaining=%s, residual=%s, children=%s, normalizations=%s",
                decision.reason(),
                decision.enforcement().map(Enum::name).orElse("NONE"),
                decision.remotePredicatePresent(),
                decision.remainingPresent(),
                decision.residualPresent(),
                decision.children().size(),
                decision.normalizations());

        decision.children().forEach(this::recordTranslation);
    }

    public void recordRemotePredicate(ElasticsearchRemotePredicate predicate)
    {
        requireNonNull(predicate, "predicate is null");
        LOG.debug("Remote predicate: type=%s, enforcement=%s", predicate.type(), predicate.enforcement());
        recordRemotePredicateNode(predicate);
    }

    /**
     * Records a successfully rendered query filter, excluding the enclosing search/count/aggregation request.
     */
    public void recordRenderedQuery(Optional<ElasticsearchRemotePredicate> predicate, long queryBytes)
    {
        requireNonNull(predicate, "predicate is null");
        if (queryBytes < 0) {
            throw new IllegalArgumentException("Query byte count must be non-negative");
        }
        renderedQueries.increment();
        renderedQueryBytes.add(queryBytes);
        predicate.ifPresent(this::recordRemotePredicate);
        LOG.debug("Rendered query: remotePredicate=%s, queryBytes=%s", predicate.isPresent(), queryBytes);
    }

    private void recordRemotePredicateNode(ElasticsearchRemotePredicate predicate)
    {
        remotePredicateNodes.increment();
        switch (predicate) {
            case ElasticsearchRemotePredicate.And and -> {
                andPredicates.increment();
                and.predicates().forEach(this::recordRemotePredicateNode);
            }
            case ElasticsearchRemotePredicate.Or or -> {
                orPredicates.increment();
                or.predicates().forEach(this::recordRemotePredicateNode);
            }
            case ElasticsearchRemotePredicate.Not not -> {
                notPredicates.increment();
                recordRemotePredicateNode(not.predicate());
            }
            case ElasticsearchRemotePredicate.Enforced enforced -> {
                enforcementPredicates.increment();
                recordRemotePredicateNode(enforced.predicate());
            }
            case ElasticsearchRemotePredicate.Term _ -> termPredicates.increment();
            case ElasticsearchRemotePredicate.Terms terms -> {
                termsPredicates.increment();
                termsValues.add(terms.values().size());
            }
            case ElasticsearchRemotePredicate.Range _ -> rangePredicates.increment();
            case ElasticsearchRemotePredicate.Prefix _ -> prefixPredicates.increment();
            case ElasticsearchRemotePredicate.Regexp _ -> regexpPredicates.increment();
            case ElasticsearchRemotePredicate.MatchPhrase _ -> matchPhrasePredicates.increment();
            case ElasticsearchRemotePredicate.MatchPhrasePrefix _ -> matchPhrasePrefixPredicates.increment();
            case ElasticsearchRemotePredicate.Exists _ -> existsPredicates.increment();
        }
    }

    public void recordDynamicFilterPlan(
            DynamicFilterOutcome outcome,
            long domainsReceived,
            long valuesReceived,
            long predicatesPushed,
            long valuesPushed,
            long termsBatches,
            long domainsRejected,
            long estimatedQueryBytes)
    {
        dynamicFilterOutcomes.get(requireNonNull(outcome, "outcome is null")).increment();
        dynamicFilterPlans.increment();
        dynamicFilterDomainsReceived.add(domainsReceived);
        dynamicFilterValuesReceived.add(valuesReceived);
        dynamicFilterPredicatesPushed.add(predicatesPushed);
        dynamicFilterValuesPushed.add(valuesPushed);
        dynamicFilterTermsBatches.add(termsBatches);
        dynamicFilterDomainsRejected.add(domainsRejected);
        dynamicFilterEstimatedQueryBytes.add(estimatedQueryBytes);

        LOG.debug(
                "Dynamic filter plan: outcome=%s, domains=%s, values=%s, pushed=%s, pushedValues=%s, batches=%s, rejected=%s, bytes=%s",
                outcome,
                domainsReceived,
                valuesReceived,
                predicatesPushed,
                valuesPushed,
                termsBatches,
                domainsRejected,
                estimatedQueryBytes);
    }

    public void recordRemoteRequest(RemoteRequestKind kind)
    {
        switch (requireNonNull(kind, "kind is null")) {
            case SEARCH -> searchRequests.increment();
            case NEXT_PAGE -> nextPageRequests.increment();
            case COUNT -> countRequests.increment();
        }
    }

    public void recordDecodedRows(long rows, long sourceBytes)
    {
        if (rows < 0 || sourceBytes < 0) {
            throw new IllegalArgumentException("Decoded row and byte counts must be non-negative");
        }
        rowsDecoded.add(rows);
        sourceBytesDecoded.add(sourceBytes);
    }

    public void recordPageReturned()
    {
        pagesReturned.increment();
    }

    public void recordRemotePageReceived()
    {
        remotePagesReceived.increment();
    }

    public void recordRetryAttempt()
    {
        retryAttempts.increment();
    }

    public void recordCancellation()
    {
        cancellations.increment();
    }

    public void recordFailure()
    {
        failures.increment();
    }

    public void recordClearScroll()
    {
        clearScrollCalls.increment();
    }

    public Snapshot snapshot()
    {
        return new Snapshot(
                translationNodes.sum(),
                exactTranslations.sum(),
                prefilterTranslations.sum(),
                approximateTranslations.sum(),
                residualTranslations.sum(),
                remainingTranslations.sum(),
                unsupportedTranslations.sum(),
                arrayMembershipTranslations.sum(),
                anyMatchTranslations.sum(),
                booleanAndTranslations.sum(),
                booleanOrTranslations.sum(),
                booleanNotTranslations.sum(),
                getTranslationReasonCounts(),
                getNormalizationCounts(),
                renderedQueries.sum(),
                renderedQueryBytes.sum(),
                remotePredicateNodes.sum(),
                andPredicates.sum(),
                orPredicates.sum(),
                notPredicates.sum(),
                enforcementPredicates.sum(),
                termPredicates.sum(),
                termsPredicates.sum(),
                termsValues.sum(),
                rangePredicates.sum(),
                prefixPredicates.sum(),
                regexpPredicates.sum(),
                matchPhrasePredicates.sum(),
                matchPhrasePrefixPredicates.sum(),
                existsPredicates.sum(),
                dynamicFilterPlans.sum(),
                getDynamicFilterOutcomes(),
                dynamicFilterDomainsReceived.sum(),
                dynamicFilterValuesReceived.sum(),
                dynamicFilterPredicatesPushed.sum(),
                dynamicFilterValuesPushed.sum(),
                dynamicFilterTermsBatches.sum(),
                dynamicFilterDomainsRejected.sum(),
                dynamicFilterEstimatedQueryBytes.sum(),
                searchRequests.sum(),
                nextPageRequests.sum(),
                countRequests.sum(),
                rowsDecoded.sum(),
                sourceBytesDecoded.sum(),
                pagesReturned.sum(),
                remotePagesReceived.sum(),
                retryAttempts.sum(),
                cancellations.sum(),
                failures.sum(),
                clearScrollCalls.sum());
    }

    @Managed
    public long getTranslationNodes()
    {
        return translationNodes.sum();
    }

    @Managed
    public long getExactTranslations()
    {
        return exactTranslations.sum();
    }

    @Managed
    public long getPrefilterTranslations()
    {
        return prefilterTranslations.sum();
    }

    @Managed
    public long getApproximateTranslations()
    {
        return approximateTranslations.sum();
    }

    @Managed
    public long getResidualTranslations()
    {
        return residualTranslations.sum();
    }

    @Managed
    public long getRemainingTranslations()
    {
        return remainingTranslations.sum();
    }

    @Managed
    public long getUnsupportedTranslations()
    {
        return unsupportedTranslations.sum();
    }

    @Managed
    public long getArrayMembershipTranslations()
    {
        return arrayMembershipTranslations.sum();
    }

    @Managed
    public long getAnyMatchTranslations()
    {
        return anyMatchTranslations.sum();
    }

    @Managed
    public long getBooleanAndTranslations()
    {
        return booleanAndTranslations.sum();
    }

    @Managed
    public long getBooleanOrTranslations()
    {
        return booleanOrTranslations.sum();
    }

    @Managed
    public long getBooleanNotTranslations()
    {
        return booleanNotTranslations.sum();
    }

    @Managed
    public long getRenderedQueries()
    {
        return renderedQueries.sum();
    }

    @Managed
    public long getRenderedQueryBytes()
    {
        return renderedQueryBytes.sum();
    }

    @Managed
    public long getRemotePredicateNodes()
    {
        return remotePredicateNodes.sum();
    }

    @Managed
    public long getAndPredicates()
    {
        return andPredicates.sum();
    }

    @Managed
    public long getOrPredicates()
    {
        return orPredicates.sum();
    }

    @Managed
    public long getNotPredicates()
    {
        return notPredicates.sum();
    }

    @Managed
    public long getEnforcementPredicates()
    {
        return enforcementPredicates.sum();
    }

    @Managed
    public long getTermPredicates()
    {
        return termPredicates.sum();
    }

    @Managed
    public long getTermsPredicates()
    {
        return termsPredicates.sum();
    }

    @Managed
    public long getTermsValues()
    {
        return termsValues.sum();
    }

    @Managed
    public long getRangePredicates()
    {
        return rangePredicates.sum();
    }

    @Managed
    public long getPrefixPredicates()
    {
        return prefixPredicates.sum();
    }

    @Managed
    public long getRegexpPredicates()
    {
        return regexpPredicates.sum();
    }

    @Managed
    public long getMatchPhrasePredicates()
    {
        return matchPhrasePredicates.sum();
    }

    @Managed
    public long getMatchPhrasePrefixPredicates()
    {
        return matchPhrasePrefixPredicates.sum();
    }

    @Managed
    public long getExistsPredicates()
    {
        return existsPredicates.sum();
    }

    @Managed
    public Map<String, Long> getTranslationReasonCounts()
    {
        ImmutableMap.Builder<String, Long> counts = ImmutableMap.builder();
        translationReasonCounts.forEach((reason, count) -> counts.put(reason.name(), count.sum()));
        return counts.buildOrThrow();
    }

    @Managed
    public Map<String, Long> getNormalizationCounts()
    {
        ImmutableMap.Builder<String, Long> counts = ImmutableMap.builder();
        normalizationCounts.forEach((normalization, count) -> counts.put(normalization.name(), count.sum()));
        return counts.buildOrThrow();
    }

    @Managed
    public Map<String, Long> getDynamicFilterOutcomes()
    {
        ImmutableMap.Builder<String, Long> counts = ImmutableMap.builder();
        dynamicFilterOutcomes.forEach((outcome, count) -> counts.put(outcome.name(), count.sum()));
        return counts.buildOrThrow();
    }

    @Managed
    public long getDynamicFilterPlans()
    {
        return dynamicFilterPlans.sum();
    }

    @Managed
    public long getDynamicFilterDomainsReceived()
    {
        return dynamicFilterDomainsReceived.sum();
    }

    @Managed
    public long getDynamicFilterValuesReceived()
    {
        return dynamicFilterValuesReceived.sum();
    }

    @Managed
    public long getDynamicFilterPredicatesPushed()
    {
        return dynamicFilterPredicatesPushed.sum();
    }

    @Managed
    public long getDynamicFilterValuesPushed()
    {
        return dynamicFilterValuesPushed.sum();
    }

    @Managed
    public long getDynamicFilterTermsBatches()
    {
        return dynamicFilterTermsBatches.sum();
    }

    @Managed
    public long getDynamicFilterDomainsRejected()
    {
        return dynamicFilterDomainsRejected.sum();
    }

    @Managed
    public long getDynamicFilterEstimatedQueryBytes()
    {
        return dynamicFilterEstimatedQueryBytes.sum();
    }

    @Managed
    public long getSearchRequests()
    {
        return searchRequests.sum();
    }

    @Managed
    public long getNextPageRequests()
    {
        return nextPageRequests.sum();
    }

    @Managed
    public long getCountRequests()
    {
        return countRequests.sum();
    }

    @Managed
    public long getRowsDecoded()
    {
        return rowsDecoded.sum();
    }

    @Managed
    public long getSourceBytesDecoded()
    {
        return sourceBytesDecoded.sum();
    }

    @Managed
    public long getPagesReturned()
    {
        return pagesReturned.sum();
    }

    @Managed
    public long getRemotePagesReceived()
    {
        return remotePagesReceived.sum();
    }

    @Managed
    public long getRetryAttempts()
    {
        return retryAttempts.sum();
    }

    @Managed
    public long getCancellations()
    {
        return cancellations.sum();
    }

    @Managed
    public long getFailures()
    {
        return failures.sum();
    }

    @Managed
    public long getClearScrollCalls()
    {
        return clearScrollCalls.sum();
    }

    private void recordEnforcement(Enforcement enforcement)
    {
        switch (enforcement) {
            case EXACT -> exactTranslations.increment();
            case PREFILTER -> prefilterTranslations.increment();
            case APPROXIMATE -> approximateTranslations.increment();
        }
    }

    public enum RemoteRequestKind
    {
        SEARCH,
        NEXT_PAGE,
        COUNT,
    }

    public enum DynamicFilterOutcome
    {
        UNRESTRICTED,
        EMPTY,
        PUSHED,
        PARTIALLY_PUSHED,
        REJECTED,
    }

    public record Snapshot(
            long translationNodes,
            long exactTranslations,
            long prefilterTranslations,
            long approximateTranslations,
            long residualTranslations,
            long remainingTranslations,
            long unsupportedTranslations,
            long arrayMembershipTranslations,
            long anyMatchTranslations,
            long booleanAndTranslations,
            long booleanOrTranslations,
            long booleanNotTranslations,
            Map<String, Long> translationReasonCounts,
            Map<String, Long> normalizationCounts,
            long renderedQueries,
            long renderedQueryBytes,
            long remotePredicateNodes,
            long andPredicates,
            long orPredicates,
            long notPredicates,
            long enforcementPredicates,
            long termPredicates,
            long termsPredicates,
            long termsValues,
            long rangePredicates,
            long prefixPredicates,
            long regexpPredicates,
            long matchPhrasePredicates,
            long matchPhrasePrefixPredicates,
            long existsPredicates,
            long dynamicFilterPlans,
            Map<String, Long> dynamicFilterOutcomes,
            long dynamicFilterDomainsReceived,
            long dynamicFilterValuesReceived,
            long dynamicFilterPredicatesPushed,
            long dynamicFilterValuesPushed,
            long dynamicFilterTermsBatches,
            long dynamicFilterDomainsRejected,
            long dynamicFilterEstimatedQueryBytes,
            long searchRequests,
            long nextPageRequests,
            long countRequests,
            long rowsDecoded,
            long sourceBytesDecoded,
            long pagesReturned,
            long remotePagesReceived,
            long retryAttempts,
            long cancellations,
            long failures,
            long clearScrollCalls)
    {
        public Snapshot
        {
            translationReasonCounts = ImmutableMap.copyOf(requireNonNull(translationReasonCounts, "translationReasonCounts is null"));
            normalizationCounts = ImmutableMap.copyOf(requireNonNull(normalizationCounts, "normalizationCounts is null"));
            dynamicFilterOutcomes = ImmutableMap.copyOf(requireNonNull(dynamicFilterOutcomes, "dynamicFilterOutcomes is null"));
        }
    }
}
