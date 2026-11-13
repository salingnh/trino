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

import io.trino.plugin.base.expression.ConnectorExpressions;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.FloatingPointValueSet;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.ArrayType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.RealType.REAL;

/**
 * Single-column scalar expressions share the typed compiler and proven native value predicates with any_match.
 */
final class ElasticsearchScalarPredicateTranslator
{
    private ElasticsearchScalarPredicateTranslator() {}

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
            ConnectorSession session,
            ConnectorExpression source,
            Map<String, ColumnHandle> assignments,
            ElasticsearchPredicateCompositionPolicy policy,
            boolean scriptEnabled)
    {
        List<Variable> variables = ConnectorExpressions.extractVariables(source).stream().distinct().toList();
        if (variables.size() != 1) {
            return Optional.empty();
        }
        Variable variable = variables.getFirst();
        if (!(assignments.get(variable.getName()) instanceof ElasticsearchColumnHandle column)
                || column.type() instanceof ArrayType || !column.type().equals(variable.getType()) || !column.sourceValueSemanticsExact()) {
            return Optional.empty();
        }
        Optional<ElasticsearchRemotePredicate> nativePredicate = nativePredicate(session, source, variable, column, policy);
        if (nativePredicate.filter(predicate -> ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)).isPresent()) {
            return Optional.of(ElasticsearchPredicateTranslation.exact(nativePredicate.orElseThrow(), Reason.EXACT_SCALAR_NATIVE));
        }
        if (!scriptEnabled) {
            return Optional.empty();
        }
        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> script = ElasticsearchExpressionScriptTranslator.translateScalar(source, variable, column, policy);
        // Keep a fallible expression whole. An independent native condition must not hide an error.
        if (script.flatMap(ElasticsearchPredicateTranslation::enforcement).filter(PREFILTER::equals).isPresent()) {
            return script;
        }
        Optional<ElasticsearchRemotePredicate> hybrid = hybrid(session, source, variable, column, policy, 0);
        if (hybrid.filter(predicate -> ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)).isPresent()) {
            return Optional.of(ElasticsearchPredicateTranslation.exact(hybrid.orElseThrow(), Reason.EXACT_SCALAR_HYBRID));
        }
        return script;
    }

    static Optional<ElasticsearchPredicateTranslation<Domain>> translateFloatingDomain(
            ElasticsearchColumnHandle column,
            Domain domain,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        // Domain extraction can consume the entire SQL expression. Preserve the domain's explicit NaN membership;
        // ordinary SQL equality with NaN is false, whereas an identical/domain membership check can be true.
        if (!(ValueSet.normalize(domain.getValues()) instanceof FloatingPointValueSet values)
                || values.getOrderedValues().getRanges().getRangeCount() > policy.maxBooleanClauses()) {
            return Optional.empty();
        }
        Variable variable = new Variable("scalar_domain", column.type());
        List<ConnectorExpression> alternatives = new ArrayList<>();
        for (Range range : values.getOrderedValues().getRanges().getOrderedRanges()) {
            List<ConnectorExpression> bounds = new ArrayList<>();
            if (range.isSingleValue()) {
                bounds.add(comparison("$equal", variable, range.getSingleValue()));
            }
            else {
                range.getLowValue().ifPresent(value -> bounds.add(comparison(range.isLowInclusive() ? "$greater_than_or_equal" : "$greater_than", variable, value)));
                range.getHighValue().ifPresent(value -> bounds.add(comparison(range.isHighInclusive() ? "$less_than_or_equal" : "$less_than", variable, value)));
            }
            if (bounds.isEmpty()) {
                bounds.add(new Call(BOOLEAN, new FunctionName("$not"), List.of(new Call(BOOLEAN, new FunctionName("$is_null"), List.of(variable)))));
                bounds.add(new Call(BOOLEAN, new FunctionName("$not"), List.of(comparison("$identical", variable, nanValue(column)))));
            }
            alternatives.add(bounds.size() == 1 ? bounds.getFirst() : new Call(BOOLEAN, AND_FUNCTION_NAME, bounds));
        }
        if (values.isNaNAllowed()) {
            alternatives.add(comparison("$identical", variable, nanValue(column)));
        }
        if (domain.isNullAllowed()) {
            alternatives.add(new Call(BOOLEAN, new FunctionName("$is_null"), List.of(variable)));
        }
        ConnectorExpression expression = alternatives.isEmpty() ? Constant.FALSE
                : alternatives.size() == 1 ? alternatives.getFirst() : new Call(BOOLEAN, OR_FUNCTION_NAME, alternatives);
        return ElasticsearchExpressionScriptTranslator.translateScalar(expression, variable, column, policy)
                .filter(value -> value.enforcement().filter(EXACT::equals).isPresent())
                .flatMap(value -> value.remotePredicate().map(predicate -> ElasticsearchPredicateTranslation.exact(predicate, Reason.EXACT_SCALAR_SCRIPT)));
    }

    private static Call comparison(String name, Variable variable, Object value)
    {
        return new Call(BOOLEAN, new FunctionName(name), List.of(variable, new Constant(value, variable.getType())));
    }

    private static Object nanValue(ElasticsearchColumnHandle column)
    {
        if (column.type().equals(REAL)) {
            return (long) Float.floatToRawIntBits(Float.NaN);
        }
        return Double.NaN;
    }

    private static Optional<ElasticsearchRemotePredicate> nativePredicate(
            ConnectorSession session,
            ConnectorExpression source,
            Variable variable,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (!column.supportsPredicates()
                || (column.elasticsearchType() instanceof PrimitiveType mapping && !mapping.indexed() && !mapping.aggregatable())) {
            return Optional.empty();
        }
        // Keep scalar regexp's existing SAFE/UNSAFE policy and residual contract. The array converter's
        // exact-regexp allowlist must not silently strengthen that separate scalar full-text strategy.
        var pending = new ArrayDeque<ConnectorExpression>();
        pending.add(source);
        int nodes = 0;
        while (!pending.isEmpty()) {
            ConnectorExpression expression = pending.removeFirst();
            if (++nodes > policy.maxBooleanClauses() || (expression instanceof Call call && call.getFunctionName().getName().equals("regexp_like"))) {
                return Optional.empty();
            }
            pending.addAll(expression.getChildren());
        }
        // Reuse element TRUE-set algebra only when NULL cannot be TRUE. Scalar NULL-TRUE expressions use the
        // scalar script (or the existing domain translator); they must not inherit array NULL omission.
        ElasticsearchColumnHandle values = new ElasticsearchColumnHandle(
                column.path(),
                new ArrayType(column.type()),
                column.elasticsearchType(),
                column.decoderDescriptor(),
                false,
                column.supportsQueryShapes(),
                column.predicateMappingSafe());
        Optional<ElasticsearchRemotePredicate> result = ElasticsearchArrayNativePredicateTranslator.translate(session, source, variable, values, policy)
                .or(() -> ElasticsearchArrayPredicateTranslator.translateAnyMatchBody(source, variable, values, column.type()))
                .filter(ElasticsearchArrayPredicateTranslator::isSafeNecessaryCondition);
        if (result.isPresent()) {
            return result;
        }
        return ElasticsearchFullTextPredicateTranslator.translateLike(session, source, Map.of(variable.getName(), column), SAFE, policy)
                .filter(value -> value.enforcement().filter(EXACT::equals).isPresent())
                .flatMap(ElasticsearchPredicateTranslation::remotePredicate);
    }

    private static Optional<ElasticsearchRemotePredicate> hybrid(
            ConnectorSession session,
            ConnectorExpression source,
            Variable variable,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy,
            int depth)
    {
        if (!(source instanceof Call call) || depth > policy.maxBooleanDepth() || call.getArguments().isEmpty()
                || call.getArguments().size() > policy.maxBooleanClauses()) {
            return Optional.empty();
        }
        if (OR_FUNCTION_NAME.equals(call.getFunctionName())) {
            List<ElasticsearchRemotePredicate> children = new ArrayList<>();
            boolean hasNative = false;
            boolean hasScript = false;
            for (ConnectorExpression child : call.getArguments()) {
                Optional<ElasticsearchRemotePredicate> nativeChild = nativePredicate(session, child, variable, column, policy);
                if (nativeChild.isPresent()) {
                    children.add(nativeChild.orElseThrow());
                    hasNative = true;
                    continue;
                }
                Optional<ElasticsearchRemotePredicate> nested = hybrid(session, child, variable, column, policy, depth + 1);
                Optional<ElasticsearchRemotePredicate> script = ElasticsearchExpressionScriptTranslator.translateScalar(child, variable, column, policy)
                        .filter(value -> value.enforcement().filter(EXACT::equals).isPresent())
                        .flatMap(ElasticsearchPredicateTranslation::remotePredicate);
                Optional<ElasticsearchRemotePredicate> translated = nested.or(() -> script);
                if (translated.isEmpty()) {
                    return Optional.empty();
                }
                hasNative |= nested.isPresent();
                hasScript = true;
                children.add(translated.orElseThrow());
            }
            return hasNative && hasScript ? ElasticsearchRemotePredicateNormalizer.or(children) : Optional.empty();
        }
        if (AND_FUNCTION_NAME.equals(call.getFunctionName())) {
            Optional<ElasticsearchRemotePredicate> script = ElasticsearchExpressionScriptTranslator.translateScalar(source, variable, column, policy)
                    .filter(value -> value.enforcement().filter(EXACT::equals).isPresent())
                    .flatMap(ElasticsearchPredicateTranslation::remotePredicate);
            if (script.isEmpty()) {
                return Optional.empty();
            }
            List<ElasticsearchRemotePredicate> children = new ArrayList<>();
            for (ConnectorExpression child : call.getArguments()) {
                nativePredicate(session, child, variable, column, policy).ifPresent(children::add);
            }
            if (children.isEmpty()) {
                return Optional.empty();
            }
            children.add(script.orElseThrow());
            return ElasticsearchRemotePredicateNormalizer.and(children);
        }
        return Optional.empty();
    }
}
