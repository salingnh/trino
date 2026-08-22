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

import com.google.inject.Inject;
import io.airlift.slice.Slice;
import io.trino.plugin.base.expression.ConnectorExpressions;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchExpressionRewrite;
import io.trino.plugin.elasticsearch.expression.ElasticsearchExpressionTranslator;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.AggregateFunction;
import io.trino.spi.connector.AggregationApplicationResult;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.LimitApplicationResult;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.TypeManager;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.SliceUtf8.getCodePointAt;
import static io.airlift.slice.SliceUtf8.lengthOfCodePoint;
import static io.airlift.slice.SliceUtf8.setCodePointAt;
import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.canonicalize;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.combine;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.conjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.withRemotePredicate;
import static io.trino.plugin.elasticsearch.ElasticsearchSessionProperties.getFullTextPushdownMode;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.DISABLED;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;

/**
 * Rule-based Elasticsearch metadata facade.
 *
 * <p>The legacy metadata implementation still performs proven SQL-expression recognition, but its connector-owned
 * output is immediately normalized into {@link ElasticsearchRemotePredicate}. New predicates are added directly to
 * the IR, which removes the one-domain-per-column limitation of {@link TupleDomain} and provides a single execution
 * representation for static and dynamic pushdown.</p>
 */
public class RuleBasedElasticsearchMetadata
        extends CasePreservingElasticsearchMetadata
{
    private static final ElasticsearchExpressionTranslator EXPRESSION_TRANSLATOR = new ElasticsearchExpressionTranslator();

    @Inject
    public RuleBasedElasticsearchMetadata(TypeManager typeManager, ElasticsearchClient client, ElasticsearchConfig config)
    {
        super(typeManager, client, config);
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(
            ConnectorSession session,
            ConnectorTableHandle table,
            Constraint constraint)
    {
        ElasticsearchTableHandle input = (ElasticsearchTableHandle) table;
        FullTextPushdownMode fullTextMode = getFullTextPushdownMode(session);
        RemoteExpressionRewrite expressionRewrite = rewriteRemoteExpressions(session, constraint, fullTextMode);
        Constraint preparedConstraint = expressionRewrite.constraint();

        Optional<ElasticsearchRemotePredicate> inheritedPredicate = combine(input.remotePredicate(), expressionRewrite.remotePredicate());
        Optional<ConstraintApplicationResult<ConnectorTableHandle>> legacyResult = super.applyFilter(session, table, preparedConstraint);

        if (legacyResult.isPresent()) {
            ConstraintApplicationResult<ConnectorTableHandle> result = legacyResult.orElseThrow();
            ElasticsearchTableHandle canonicalHandle = canonicalize((ElasticsearchTableHandle) result.getHandle(), inheritedPredicate);
            ConnectorExpression remainingExpression = appendResidualExpressions(
                    result.getRemainingExpression().orElse(preparedConstraint.getExpression()),
                    expressionRewrite.residualExpressions());
            return Optional.of(new ConstraintApplicationResult<>(
                    canonicalHandle,
                    result.getRemainingFilter(),
                    remainingExpression,
                    result.isPrecalculateStatistics()));
        }

        if (expressionRewrite.remotePredicate().isEmpty()) {
            return Optional.empty();
        }

        ElasticsearchTableHandle rewrittenHandle = withRemotePredicate(input, inheritedPredicate);
        return Optional.of(new ConstraintApplicationResult<>(
                rewrittenHandle,
                preparedConstraint.getSummary(),
                appendResidualExpressions(preparedConstraint.getExpression(), expressionRewrite.residualExpressions()),
                false));
    }

    @Override
    public Optional<LimitApplicationResult<ConnectorTableHandle>> applyLimit(ConnectorSession session, ConnectorTableHandle table, long limit)
    {
        ElasticsearchTableHandle input = (ElasticsearchTableHandle) table;
        return super.applyLimit(session, table, limit)
                .map(result -> new LimitApplicationResult<>(
                        withRemotePredicate((ElasticsearchTableHandle) result.getHandle(), input.remotePredicate()),
                        result.isLimitGuaranteed(),
                        result.isPrecalculateStatistics()));
    }

    @Override
    public Optional<AggregationApplicationResult<ConnectorTableHandle>> applyAggregation(
            ConnectorSession session,
            ConnectorTableHandle table,
            List<AggregateFunction> aggregates,
            Map<String, ColumnHandle> assignments,
            List<List<ColumnHandle>> groupingSets)
    {
        ElasticsearchTableHandle input = (ElasticsearchTableHandle) table;
        return super.applyAggregation(session, table, aggregates, assignments, groupingSets)
                .map(result -> new AggregationApplicationResult<>(
                        withRemotePredicate((ElasticsearchTableHandle) result.getHandle(), input.remotePredicate()),
                        result.getProjections(),
                        result.getAssignments(),
                        result.getGroupingColumnMapping(),
                        result.isPrecalculateStatistics()));
    }

    private static RemoteExpressionRewrite rewriteRemoteExpressions(
            ConnectorSession session,
            Constraint constraint,
            FullTextPushdownMode fullTextMode)
    {
        Constraint expressionConstraint = fullTextMode == UNSAFE ? removeSyntheticPrefixLikeDomains(constraint) : constraint;
        List<ConnectorExpression> remainingExpressions = new ArrayList<>();
        List<ConnectorExpression> residualExpressions = new ArrayList<>();
        List<ElasticsearchRemotePredicate> remotePredicates = new ArrayList<>();

        for (ConnectorExpression expression : ConnectorExpressions.extractConjuncts(expressionConstraint.getExpression())) {
            Optional<ElasticsearchRemotePredicate> arrayPredicate = ElasticsearchArrayPredicateTranslator.translate(expression, expressionConstraint.getAssignments());
            if (arrayPredicate.isPresent()) {
                remotePredicates.add(arrayPredicate.orElseThrow());
                continue;
            }

            Optional<RegexpRemoteRewrite> regexpRewrite = translateRegexp(expression, expressionConstraint.getAssignments());
            if (fullTextMode != DISABLED && regexpRewrite.isPresent()) {
                RegexpRemoteRewrite translated = regexpRewrite.orElseThrow();
                boolean safePrefilter = translated.column().supportsPredicates() && translated.translation().quality().safeForPrefilter();
                if (fullTextMode == UNSAFE || safePrefilter) {
                    remotePredicates.add(new ElasticsearchRemotePredicate.Regexp(
                            translated.column().predicateName(),
                            translated.translation().pattern()));
                    if (fullTextMode == SAFE) {
                        // SAFE uses the remote regexp only to reduce candidates. Trino remains authoritative.
                        residualExpressions.add(expression);
                    }
                    continue;
                }
            }

            if (fullTextMode == UNSAFE) {
                Optional<ElasticsearchExpressionRewrite> rewrite = EXPRESSION_TRANSLATOR.rewrite(session, expression, expressionConstraint.getAssignments());
                if (rewrite.isPresent()) {
                    ElasticsearchExpressionRewrite translated = rewrite.orElseThrow();
                    switch (translated.queryType()) {
                        case MATCH_PHRASE -> remotePredicates.add(new ElasticsearchRemotePredicate.MatchPhrase(
                                translated.column().remoteName(),
                                translated.value()));
                    }
                    continue;
                }
            }
            remainingExpressions.add(expression);
        }

        if (remotePredicates.isEmpty() && expressionConstraint == constraint) {
            return new RemoteExpressionRewrite(constraint, Optional.empty(), List.of());
        }
        return new RemoteExpressionRewrite(
                new Constraint(
                        expressionConstraint.getSummary(),
                        ConnectorExpressions.and(remainingExpressions),
                        expressionConstraint.getAssignments()),
                conjunction(remotePredicates),
                residualExpressions);
    }

    private static Optional<RegexpRemoteRewrite> translateRegexp(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments)
    {
        if (!(expression instanceof Call call) || !call.getFunctionName().getName().equals("regexp_like")) {
            return Optional.empty();
        }
        List<ConnectorExpression> arguments = call.getArguments();
        if (arguments.size() != 2
                || !(arguments.get(0) instanceof Variable variable)
                || !(arguments.get(1) instanceof Constant constant)
                || !(constant.getValue() instanceof Slice pattern)) {
            return Optional.empty();
        }
        ColumnHandle assigned = assignments.get(variable.getName());
        if (!(assigned instanceof ElasticsearchColumnHandle column) || !(column.type() instanceof VarcharType)) {
            return Optional.empty();
        }
        return CasePreservingElasticsearchMetadata.translateRegexpLike(pattern.toStringUtf8())
                .map(translation -> new RegexpRemoteRewrite(column, translation));
    }

    private static ConnectorExpression appendResidualExpressions(
            ConnectorExpression expression,
            List<ConnectorExpression> residualExpressions)
    {
        if (residualExpressions.isEmpty()) {
            return expression;
        }
        List<ConnectorExpression> conjuncts = new ArrayList<>(ConnectorExpressions.extractConjuncts(expression));
        conjuncts.addAll(residualExpressions);
        return ConnectorExpressions.and(conjuncts);
    }

    /**
     * Compatibility helper retained for focused regression tests of the old lowering bridge. Runtime lowering now
     * targets {@link ElasticsearchRemotePredicate} directly and no longer encodes a MATCH_PHRASE as a synthetic domain.
     */
    static Constraint rewriteUnsafeFullTextConstraint(ConnectorSession session, Constraint constraint)
    {
        if (constraint.getSummary().isNone()) {
            return constraint;
        }

        Constraint rewrittenConstraint = removeSyntheticPrefixLikeDomains(constraint);

        List<ConnectorExpression> conjuncts = ConnectorExpressions.extractConjuncts(rewrittenConstraint.getExpression());
        List<Optional<ElasticsearchExpressionRewrite>> translations = conjuncts.stream()
                .map(expression -> EXPRESSION_TRANSLATOR.rewrite(session, expression, rewrittenConstraint.getAssignments()))
                .toList();

        Map<ColumnHandle, Domain> originalDomains = rewrittenConstraint.getSummary().getDomains().orElse(Map.of());
        Map<ElasticsearchColumnHandle, Integer> translationsPerColumn = new HashMap<>();
        translations.stream()
                .flatMap(Optional::stream)
                .forEach(rewrite -> translationsPerColumn.merge(rewrite.column(), 1, Integer::sum));

        Map<ColumnHandle, Domain> translatedDomains = new HashMap<>();
        List<ConnectorExpression> remainingExpressions = new ArrayList<>();

        for (int index = 0; index < conjuncts.size(); index++) {
            ConnectorExpression expression = conjuncts.get(index);
            Optional<ElasticsearchExpressionRewrite> translation = translations.get(index);
            if (translation.isEmpty()) {
                remainingExpressions.add(expression);
                continue;
            }

            ElasticsearchExpressionRewrite rewrite = translation.orElseThrow();
            ElasticsearchColumnHandle column = rewrite.column();

            if (originalDomains.containsKey(column) || translationsPerColumn.getOrDefault(column, 0) != 1) {
                remainingExpressions.add(expression);
                continue;
            }

            switch (rewrite.queryType()) {
                case MATCH_PHRASE -> translatedDomains.put(
                        column,
                        Domain.singleValue(column.type(), utf8Slice(rewrite.value())));
            }
        }

        if (translatedDomains.isEmpty()) {
            return rewrittenConstraint;
        }

        TupleDomain<ColumnHandle> translatedSummary = rewrittenConstraint.getSummary()
                .intersect(TupleDomain.withColumnDomains(translatedDomains));
        return new Constraint(
                translatedSummary,
                ConnectorExpressions.and(remainingExpressions),
                rewrittenConstraint.getAssignments());
    }

    /**
     * DomainTranslator represents {@code LIKE 'prefix%'} as the synthetic range {@code [prefix, nextPrefix)}. The
     * legacy Elasticsearch pushdown recognizes the LIKE expression independently and, in UNSAFE mode, replaces it
     * with {@code match_phrase_prefix}. Leaving the synthetic range in the remaining TupleDomain would therefore add
     * a redundant Trino FilterNode and prevent full pushdown.
     */
    private static Constraint removeSyntheticPrefixLikeDomains(Constraint constraint)
    {
        Map<ColumnHandle, Domain> domains = new HashMap<>(constraint.getSummary().getDomains().orElse(Map.of()));
        if (domains.isEmpty()) {
            return constraint;
        }

        List<ConnectorExpression> conjuncts = ConnectorExpressions.extractConjuncts(constraint.getExpression());
        boolean changed = false;
        for (ConnectorExpression expression : conjuncts) {
            if (!(expression instanceof Call call) || !isSupportedLikeCall(call)) {
                continue;
            }

            List<ConnectorExpression> arguments = call.getArguments();
            Variable variable = (Variable) arguments.get(0);
            ElasticsearchColumnHandle column = (ElasticsearchColumnHandle) constraint.getAssignments().get(variable.getName());
            if (!isAnalyzedTextOnly(column)
                    || !(arguments.get(1) instanceof Constant constant)
                    || !(constant.getValue() instanceof Slice pattern)) {
                continue;
            }

            Optional<Slice> escape = Optional.empty();
            if (arguments.size() == 3) {
                Object escapeValue = ((Constant) arguments.get(2)).getValue();
                if (!(escapeValue instanceof Slice escapeSlice)) {
                    continue;
                }
                escape = Optional.of(escapeSlice);
            }

            Optional<String> prefix = likePrefix(pattern, escape);
            if (prefix.isEmpty()) {
                continue;
            }

            long conjunctsOnColumn = conjuncts.stream()
                    .filter(conjunct -> referencesVariable(conjunct, variable.getName()))
                    .count();
            if (conjunctsOnColumn != 1) {
                continue;
            }

            Domain actualDomain = domains.get(column);
            if (actualDomain == null) {
                continue;
            }

            Optional<Domain> expectedDomain = createLikePrefixDomain(
                    (VarcharType) column.type(),
                    utf8Slice(prefix.orElseThrow()));
            if (expectedDomain.isPresent() && actualDomain.equals(expectedDomain.orElseThrow())) {
                domains.remove(column);
                changed = true;
            }
        }

        if (!changed) {
            return constraint;
        }

        return new Constraint(
                TupleDomain.withColumnDomains(domains),
                constraint.getExpression(),
                constraint.getAssignments());
    }

    private static boolean isAnalyzedTextOnly(ElasticsearchColumnHandle column)
    {
        return column != null
                && !column.supportsPredicates()
                && column.type() instanceof VarcharType
                && column.elasticsearchType() instanceof PrimitiveType primitiveType
                && primitiveType.name().equalsIgnoreCase("text")
                && primitiveType.keyword().isEmpty();
    }

    private static boolean referencesVariable(ConnectorExpression expression, String variableName)
    {
        if (expression instanceof Variable variable) {
            return variable.getName().equals(variableName);
        }
        return expression.getChildren().stream()
                .anyMatch(child -> referencesVariable(child, variableName));
    }

    static Optional<Domain> createLikePrefixDomain(VarcharType type, Slice prefix)
    {
        int lastIncrementable = -1;
        for (int position = 0; position < prefix.length(); position += lengthOfCodePoint(prefix, position)) {
            if (getCodePointAt(prefix, position) < 127) {
                lastIncrementable = position;
            }
        }

        if (lastIncrementable == -1) {
            return Optional.empty();
        }

        Slice lowerBound = prefix;
        Slice upperBound = prefix.slice(
                        0,
                        lastIncrementable + lengthOfCodePoint(prefix, lastIncrementable))
                .copy();
        setCodePointAt(getCodePointAt(prefix, lastIncrementable) + 1, upperBound, lastIncrementable);

        return Optional.of(Domain.create(
                ValueSet.ofRanges(Range.range(type, lowerBound, true, upperBound, false)),
                false));
    }

    private record RegexpRemoteRewrite(
            ElasticsearchColumnHandle column,
            CasePreservingElasticsearchMetadata.RegexpTranslation translation) {}

    private record RemoteExpressionRewrite(
            Constraint constraint,
            Optional<ElasticsearchRemotePredicate> remotePredicate,
            List<ConnectorExpression> residualExpressions) {}
}
