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
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_UNSAFE_APPROXIMATE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.spi.type.BooleanType.BOOLEAN;

/**
 * Shared UNSAFE text routing: native analyzer predicates first, then source-backed typed expressions.
 */
final class ElasticsearchTextPredicateTranslator
{
    private ElasticsearchTextPredicateTranslator() {}

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
            ConnectorSession session,
            ConnectorExpression source,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode mode,
            ElasticsearchPredicateCompositionPolicy policy,
            boolean arrayScriptEnabled,
            boolean scalarScriptEnabled)
    {
        if (mode != UNSAFE) {
            return Optional.empty();
        }
        ConnectorExpression expression = source;
        Variable field;
        Variable element;
        boolean array = false;
        if (source instanceof Call call && call.getFunctionName().getName().equals("any_match")) {
            if (call.getArguments().size() != 2 || !(call.getArguments().getFirst() instanceof Variable variable)
                    || !(call.getArguments().get(1) instanceof Lambda lambda) || lambda.getArguments().size() != 1
                    || !(variable.getType() instanceof ArrayType type) || !(type.getElementType() instanceof VarcharType)) {
                return Optional.empty();
            }
            field = variable;
            element = lambda.getArguments().getFirst();
            expression = lambda.getBody();
            array = true;
        }
        else {
            List<Variable> variables = ConnectorExpressions.extractVariables(source).stream().distinct().toList();
            if (variables.size() != 1 || !(variables.getFirst().getType() instanceof VarcharType)) {
                return Optional.empty();
            }
            field = variables.getFirst();
            element = field;
        }
        if (!(assignments.get(field.getName()) instanceof ElasticsearchColumnHandle column)
                || !column.predicateMappingSafe() || !column.type().equals(field.getType())
                || !(column.elasticsearchType() instanceof PrimitiveType mapping) || !mapping.name().equals("text")) {
            return Optional.empty();
        }
        if (array) {
            Optional<ElasticsearchRemotePredicate> exact = ElasticsearchArrayNativePredicateTranslator.translate(session, expression, element, column, policy);
            if (exact.filter(predicate -> ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)).isPresent()) {
                return Optional.of(ElasticsearchPredicateTranslation.exact(exact.orElseThrow(), ElasticsearchPredicateTranslation.Reason.EXACT_ANY_MATCH));
            }
        }
        // Target the analyzed parent even if an optional keyword subfield has different normalization or length limits.
        ElasticsearchColumnHandle text = new ElasticsearchColumnHandle(column.path(), element.getType(), new PrimitiveType("text"), column.decoderDescriptor(), false, false, true);
        Optional<ElasticsearchRemotePredicate> nativePredicate = nativePredicate(session, expression, element, text, policy, 0);
        if (nativePredicate.isPresent()) {
            return Optional.of(ElasticsearchPredicateComposer.approximateWithinBudget(source, nativePredicate.orElseThrow(), FULL_TEXT_UNSAFE_APPROXIMATE, policy));
        }
        if (!(array ? arrayScriptEnabled : scalarScriptEnabled)) {
            return Optional.empty();
        }
        return ElasticsearchExpressionScriptTranslator.translateTextSource(source, expression, element, column, array, policy);
    }

    static Optional<ElasticsearchPredicateTranslation<Domain>> translateDomain(
            ConnectorSession session,
            ElasticsearchColumnHandle column,
            Domain domain,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (!(column.type() instanceof VarcharType) || !(column.elasticsearchType() instanceof PrimitiveType mapping) || !mapping.name().equals("text")) {
            return Optional.empty();
        }
        if (domain.getValues().getRanges().getRangeCount() > policy.maxBooleanClauses()) {
            return Optional.empty();
        }
        Variable variable = new Variable("text_domain", column.type());
        List<ConnectorExpression> alternatives = new ArrayList<>();
        for (Range range : domain.getValues().getRanges().getOrderedRanges()) {
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
            }
            alternatives.add(bounds.size() == 1 ? bounds.getFirst() : new Call(BOOLEAN, new FunctionName("$and"), bounds));
        }
        if (domain.isNullAllowed()) {
            alternatives.add(new Call(BOOLEAN, new FunctionName("$is_null"), List.of(variable)));
        }
        ConnectorExpression expression = alternatives.isEmpty() ? Constant.FALSE
                : alternatives.size() == 1 ? alternatives.getFirst() : new Call(BOOLEAN, new FunctionName("$or"), alternatives);
        return translate(session, expression, Map.of(variable.getName(), column), UNSAFE, policy, false, true)
                .filter(value -> value.enforcement().filter(EXACT::equals).isPresent())
                .flatMap(value -> value.remotePredicate().map(predicate -> ElasticsearchPredicateTranslation.exact(predicate, ElasticsearchPredicateTranslation.Reason.EXACT_SCALAR_SCRIPT)));
    }

    private static ConnectorExpression comparison(String name, Variable variable, Object value)
    {
        return new Call(BOOLEAN, new FunctionName(name), List.of(variable, new Constant(value, variable.getType())));
    }

    private static Optional<ElasticsearchRemotePredicate> nativePredicate(
            ConnectorSession session,
            ConnectorExpression expression,
            Variable variable,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy,
            int depth)
    {
        if (!(expression instanceof Call call) || depth > policy.maxBooleanDepth()) {
            return Optional.empty();
        }
        String name = call.getFunctionName().getName();
        if ((name.equals("$and") || name.equals("$or")) && !call.getArguments().isEmpty()) {
            List<ElasticsearchRemotePredicate> children = new ArrayList<>();
            for (ConnectorExpression argument : call.getArguments()) {
                Optional<ElasticsearchRemotePredicate> child = nativePredicate(session, argument, variable, column, policy, depth + 1);
                if (child.isEmpty()) {
                    return Optional.empty();
                }
                children.add(child.orElseThrow());
            }
            return Optional.of(name.equals("$and") ? new ElasticsearchRemotePredicate.And(children) : new ElasticsearchRemotePredicate.Or(children));
        }
        List<ConnectorExpression> arguments = new ArrayList<>(call.getArguments());
        if (arguments.isEmpty()) {
            return Optional.empty();
        }
        ConnectorExpression value = arguments.getFirst();
        int wrappers = 0;
        while (value instanceof Call function && List.of("lower", "upper").contains(function.getFunctionName().getName()) && function.getArguments().size() == 1) {
            if (++wrappers > policy.maxBooleanDepth()) {
                return Optional.empty();
            }
            value = function.getArguments().getFirst();
        }
        if (!value.equals(variable)) {
            return Optional.empty();
        }
        arguments.set(0, variable);
        Call normalized = new Call(call.getType(), call.getFunctionName(), arguments);
        Map<String, ColumnHandle> assignments = Map.of(variable.getName(), column);
        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> result = ElasticsearchFullTextPredicateTranslator.translateLike(session, normalized, assignments, UNSAFE, policy);
        if (result.isEmpty()) {
            result = ElasticsearchFullTextPredicateTranslator.translatePrefix(normalized, assignments, UNSAFE, policy);
        }
        if (result.isEmpty()) {
            result = ElasticsearchFullTextPredicateTranslator.translateRegexp(normalized, assignments, UNSAFE, policy);
        }
        return result.flatMap(ElasticsearchPredicateTranslation::remotePredicate);
    }
}
