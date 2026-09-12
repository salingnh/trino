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

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.plugin.base.expression.ConnectorExpressions;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Decision;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.Constraint;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.SliceUtf8.getCodePointAt;
import static io.airlift.slice.SliceUtf8.lengthOfCodePoint;
import static io.airlift.slice.SliceUtf8.setCodePointAt;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.conjunction;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.translateDomain;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.DISABLED;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;
import static io.trino.spi.expression.Constant.FALSE;
import static io.trino.spi.expression.Constant.TRUE;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static java.util.Objects.requireNonNull;

/**
 * Produces the connector-owned predicate plan used by {@link RuleBasedElasticsearchMetadata}.
 *
 * <p>Every planner-owned predicate is represented by {@link ElasticsearchPredicateTranslation}. The translation
 * contract keeps remote, compatibility-boundary remaining state and connector-owned Trino residual state separate,
 * so AND/OR composition cannot accidentally turn a partial translation into a false-negative remote filter.</p>
 */
final class ElasticsearchPredicatePushdownPlanner
{
    private ElasticsearchPredicatePushdownPlanner() {}

    public static Result plan(ConnectorSession session, Constraint constraint, FullTextPushdownMode fullTextMode)
    {
        return plan(session, constraint, fullTextMode, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Result plan(
            ConnectorSession session,
            Constraint constraint,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(session, "session is null");
        requireNonNull(constraint, "constraint is null");
        requireNonNull(fullTextMode, "fullTextMode is null");
        requireNonNull(policy, "policy is null");

        Constraint normalizedConstraint = fullTextMode == UNSAFE
                ? removeUnsafeAnalyzedPrefixSyntheticDomains(constraint)
                : constraint;

        List<ElasticsearchRemotePredicate> remotePredicates = new ArrayList<>();
        List<Decision> decisions = new ArrayList<>();
        Map<ColumnHandle, Domain> remainingDomains = new HashMap<>(normalizedConstraint.getSummary().getDomains().orElse(Map.of()));
        Map<ColumnHandle, Domain> residualDomains = new HashMap<>();

        for (Map.Entry<ColumnHandle, Domain> entry : normalizedConstraint.getSummary().getDomains().orElse(Map.of()).entrySet()) {
            ElasticsearchColumnHandle column = (ElasticsearchColumnHandle) entry.getKey();
            Domain domain = entry.getValue();
            ElasticsearchPredicateTranslation<Domain> translation = translateDomainPredicate(column, domain, fullTextMode, policy);

            decisions.add(translation.decision());
            translation.remotePredicate().ifPresent(remotePredicates::add);
            if (translation.remaining().isEmpty() || translation.residual().isPresent()) {
                remainingDomains.remove(column);
            }
            translation.residual().ifPresent(residual -> residualDomains.put(column, residual));
        }

        ElasticsearchPredicateTranslation<ConnectorExpression> expressionTranslation = translateExpression(
                session,
                normalizedConstraint.getExpression(),
                normalizedConstraint.getAssignments(),
                fullTextMode,
                policy);
        decisions.add(expressionTranslation.decision());
        expressionTranslation.remotePredicate().ifPresent(remotePredicates::add);

        Optional<ElasticsearchRemotePredicate> remotePredicate = conjunction(remotePredicates);
        if (remotePredicate.filter(predicate -> !ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)).isPresent()) {
            return localResult(normalizedConstraint, decisions);
        }

        Constraint remainingConstraint = new Constraint(
                TupleDomain.withColumnDomains(remainingDomains),
                expressionTranslation.remaining().orElse(TRUE),
                normalizedConstraint.getAssignments());
        return new Result(
                remainingConstraint,
                remotePredicate,
                TupleDomain.withColumnDomains(residualDomains),
                expressionTranslation.residual().map(List::of).orElseGet(List::of),
                decisions);
    }

    private static ElasticsearchPredicateTranslation<Domain> translateDomainPredicate(
            ElasticsearchColumnHandle column,
            Domain domain,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (column.supportsPredicates()) {
            Optional<ElasticsearchRemotePredicate> translated = translateDomain(column, domain);
            if (translated.isEmpty()) {
                return ElasticsearchPredicateTranslation.unsupported(domain, Reason.UNSUPPORTED_DOMAIN);
            }
            if (!ElasticsearchPredicateComposer.isWithinRequestBudget(translated.orElseThrow(), policy)) {
                return ElasticsearchPredicateTranslation.residual(domain, Reason.UNSUPPORTED_DOMAIN);
            }
            return ElasticsearchPredicateTranslation.exact(translated.orElseThrow(), Reason.EXACT_DOMAIN);
        }

        boolean analyzedDiscrete = ElasticsearchFullTextPredicateTranslator.isAnalyzedTextOnly(column) && domain.getValues().isDiscreteSet();
        if (!analyzedDiscrete) {
            return ElasticsearchPredicateTranslation.unsupported(domain, Reason.UNSUPPORTED_DOMAIN);
        }
        if (fullTextMode == DISABLED) {
            return ElasticsearchPredicateTranslation.residual(domain, Reason.FULL_TEXT_DISABLED);
        }
        if (fullTextMode == SAFE) {
            return ElasticsearchPredicateTranslation.residual(domain, Reason.FULL_TEXT_SAFE_UNPROVEN);
        }

        Optional<ElasticsearchRemotePredicate> translated = translateDomain(column, domain);
        if (translated.isEmpty()) {
            return ElasticsearchPredicateTranslation.residual(domain, Reason.UNSUPPORTED_DOMAIN);
        }
        if (!ElasticsearchPredicateComposer.isWithinRequestBudget(translated.orElseThrow(), policy)) {
            return ElasticsearchPredicateTranslation.residual(domain, Reason.UNSUPPORTED_DOMAIN);
        }
        return ElasticsearchPredicateTranslation.approximate(translated.orElseThrow(), Reason.FULL_TEXT_UNSAFE_APPROXIMATE);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateExpression(
            ConnectorSession session,
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(expression, "expression is null");
        if (expression.equals(TRUE)) {
            return ElasticsearchPredicateTranslation.noop(Reason.NOOP);
        }

        if (expression instanceof Call call && AND_FUNCTION_NAME.equals(call.getFunctionName())) {
            if (call.getArguments().stream().anyMatch(FALSE::equals)) {
                return ElasticsearchPredicateTranslation.unsupported(expression, Reason.BOOLEAN_AND);
            }
            List<ElasticsearchPredicateTranslation<ConnectorExpression>> children = call.getArguments().stream()
                    .filter(argument -> !argument.equals(TRUE))
                    .map(argument -> translateExpression(session, argument, assignments, fullTextMode, policy))
                    .toList();
            if (children.isEmpty()) {
                return ElasticsearchPredicateTranslation.noop(Reason.BOOLEAN_AND);
            }
            return ElasticsearchPredicateComposer.and(expression, children, policy);
        }

        if (expression instanceof Call call && OR_FUNCTION_NAME.equals(call.getFunctionName())) {
            if (call.getArguments().stream().anyMatch(TRUE::equals)) {
                return ElasticsearchPredicateTranslation.noop(Reason.BOOLEAN_OR);
            }
            List<ConnectorExpression> arguments = call.getArguments().stream()
                    .filter(argument -> !argument.equals(FALSE))
                    .toList();
            if (arguments.isEmpty()) {
                return ElasticsearchPredicateTranslation.unsupported(expression, Reason.BOOLEAN_OR);
            }
            List<ElasticsearchPredicateTranslation<ConnectorExpression>> children = arguments.stream()
                    .map(argument -> translateExpression(session, argument, assignments, fullTextMode, policy))
                    .toList();
            return ElasticsearchPredicateComposer.or(expression, children, policy);
        }

        if (expression instanceof Call call && NOT_FUNCTION_NAME.equals(call.getFunctionName())) {
            return ElasticsearchPredicateComposer.not(expression);
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> arrayTranslation = ElasticsearchArrayPredicateTranslator.translate(
                session,
                expression,
                assignments,
                fullTextMode,
                policy);
        if (arrayTranslation.isPresent()) {
            return arrayTranslation.orElseThrow();
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> regexpPredicate = ElasticsearchFullTextPredicateTranslator.translateRegexp(expression, assignments, fullTextMode, policy);
        if (regexpPredicate.isPresent()) {
            return regexpPredicate.orElseThrow();
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> prefixPredicate = ElasticsearchFullTextPredicateTranslator.translatePrefix(expression, assignments, fullTextMode, policy);
        if (prefixPredicate.isPresent()) {
            return prefixPredicate.orElseThrow();
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> likePredicate = ElasticsearchFullTextPredicateTranslator.translateLike(session, expression, assignments, fullTextMode, policy);
        if (likePredicate.isPresent()) {
            return likePredicate.orElseThrow();
        }

        return ElasticsearchPredicateTranslation.unsupported(expression, Reason.UNSUPPORTED_EXPRESSION);
    }

    private static Result localResult(Constraint constraint, List<Decision> decisions)
    {
        return new Result(
                new Constraint(TupleDomain.all(), TRUE, constraint.getAssignments()),
                Optional.empty(),
                constraint.getSummary(),
                constraint.getExpression().equals(TRUE) ? List.of() : List.of(constraint.getExpression()),
                decisions);
    }

    /**
     * DomainTranslator represents LIKE 'prefix%' as [prefix, nextPrefix). For analyzed text this range is not an exact
     * Elasticsearch predicate. In UNSAFE mode the LIKE expression itself is authoritative after translation to
     * match_phrase_prefix, so the synthetic range must not survive as a Trino residual.
     */
    private static Constraint removeUnsafeAnalyzedPrefixSyntheticDomains(Constraint constraint)
    {
        Map<ColumnHandle, Domain> domains = new HashMap<>(constraint.getSummary().getDomains().orElse(Map.of()));
        if (domains.isEmpty()) {
            return constraint;
        }

        List<ConnectorExpression> conjuncts = ConnectorExpressions.extractConjuncts(constraint.getExpression());
        boolean changed = false;
        for (ConnectorExpression expression : conjuncts) {
            if (!(expression instanceof Call call) || !ElasticsearchMetadata.isSupportedLikeCall(call)) {
                continue;
            }

            List<ConnectorExpression> arguments = call.getArguments();
            Variable variable = (Variable) arguments.get(0);
            ColumnHandle assigned = constraint.getAssignments().get(variable.getName());
            if (!(assigned instanceof ElasticsearchColumnHandle column)
                    || !ElasticsearchFullTextPredicateTranslator.isAnalyzedTextOnly(column)
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

            Optional<String> prefix = ElasticsearchMetadata.likePrefix(pattern, escape);
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
                    Slices.utf8Slice(prefix.orElseThrow()));
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

    private static boolean referencesVariable(ConnectorExpression expression, String variableName)
    {
        if (expression instanceof Variable variable) {
            return variable.getName().equals(variableName);
        }
        return expression.getChildren().stream()
                .anyMatch(child -> referencesVariable(child, variableName));
    }

    public record Result(
            Constraint remainingConstraint,
            Optional<ElasticsearchRemotePredicate> remotePredicate,
            TupleDomain<ColumnHandle> residualFilter,
            List<ConnectorExpression> residualExpressions,
            List<Decision> decisions)
    {
        public Result
        {
            requireNonNull(remainingConstraint, "remainingConstraint is null");
            requireNonNull(remotePredicate, "remotePredicate is null");
            requireNonNull(residualFilter, "residualFilter is null");
            residualExpressions = List.copyOf(requireNonNull(residualExpressions, "residualExpressions is null"));
            decisions = List.copyOf(requireNonNull(decisions, "decisions is null"));
        }
    }
}
