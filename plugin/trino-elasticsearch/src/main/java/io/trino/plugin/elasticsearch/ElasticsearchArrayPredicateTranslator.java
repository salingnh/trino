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

import io.trino.plugin.elasticsearch.client.IndexMetadata.DateTimeType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.block.Block;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.APPROXIMATE_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.APPROXIMATE_ARRAY;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.ARRAY_CONSTRUCTOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IN_PREDICATE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.TypeUtils.readNativeValue;
import static java.util.Objects.requireNonNull;

/**
 * Elasticsearch predicate translation for primitive arrays.
 */
final class ElasticsearchArrayPredicateTranslator
{
    private ElasticsearchArrayPredicateTranslator() {}

    public static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
            ConnectorSession session,
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        requireNonNull(session, "session is null");
        requireNonNull(expression, "expression is null");
        requireNonNull(assignments, "assignments is null");
        requireNonNull(fullTextMode, "fullTextMode is null");

        if (!(expression instanceof Call call)) {
            return Optional.empty();
        }

        return switch (call.getFunctionName().getName()) {
            case "contains" -> Optional.of(translateContains(call, assignments, fullTextMode));
            case "arrays_overlap" -> Optional.of(translateArraysOverlap(call, assignments, fullTextMode));
            case "any_match" -> Optional.of(translateAnyMatch(session, call, assignments, fullTextMode));
            default -> Optional.empty();
        };
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> exactOrUnsupported(
            ConnectorExpression expression,
            Optional<ElasticsearchRemotePredicate> predicate,
            ElasticsearchPredicateTranslation.Reason exactReason)
    {
        return predicate
                .<ElasticsearchPredicateTranslation<ConnectorExpression>>map(value -> ElasticsearchPredicateTranslation.exact(value, exactReason))
                .orElseGet(() -> ElasticsearchPredicateTranslation.unsupported(
                        expression,
                        UNSUPPORTED_EXPRESSION));
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateContains(
            Call call,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        if (call.getArguments().size() != 2
                || !(call.getArguments().get(0) instanceof Variable variable)
                || !(call.getArguments().get(1) instanceof Constant constant)
                || constant.getValue() == null) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        ElasticsearchColumnHandle column = column(assignments, variable);
        Optional<Type> elementType = arrayElementType(column);
        if (elementType.isEmpty() || !constant.getType().equals(elementType.orElseThrow())) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        return translateElementMembership(
                call,
                column,
                elementType.orElseThrow(),
                List.of(ElasticsearchRemotePredicateTranslator.getValue(elementType.orElseThrow(), constant.getValue())),
                fullTextMode,
                EXACT_ARRAY,
                APPROXIMATE_ARRAY);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateArraysOverlap(
            Call call,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        if (call.getArguments().size() != 2
                || !(call.getArguments().get(0) instanceof Variable variable)) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        ElasticsearchColumnHandle column = column(assignments, variable);
        Optional<Type> elementType = arrayElementType(column);
        if (elementType.isEmpty()) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        return translateConstantArray(call.getArguments().get(1), elementType.orElseThrow())
                .map(values -> translateElementMembership(
                        call,
                        column,
                        elementType.orElseThrow(),
                        values,
                        fullTextMode,
                        EXACT_ARRAY,
                        APPROXIMATE_ARRAY))
                .orElseGet(() -> ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION));
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateAnyMatch(
            ConnectorSession session,
            Call call,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        if (call.getArguments().size() != 2
                || !(call.getArguments().get(0) instanceof Variable arrayVariable)
                || !(call.getArguments().get(1) instanceof Lambda lambda)
                || lambda.getArguments().size() != 1) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        ElasticsearchColumnHandle column = column(assignments, arrayVariable);
        Optional<Type> elementType = arrayElementType(column);
        if (elementType.isEmpty()) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        Variable lambdaVariable = lambda.getArguments().getFirst();
        if (!lambdaVariable.getType().equals(elementType.orElseThrow())) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        if (isAnalyzedTextArray(column)) {
            if (fullTextMode != FullTextPushdownMode.UNSAFE) {
                return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
            }
            return translateAnalyzedAnyMatch(
                    session,
                    call,
                    lambda.getBody(),
                    lambdaVariable,
                    column,
                    elementType.orElseThrow(),
                    fullTextMode);
        }

        return exactOrUnsupported(
                call,
                translateAnyMatchBody(lambda.getBody(), lambdaVariable, column, elementType.orElseThrow()),
                EXACT_ANY_MATCH);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateAnalyzedAnyMatch(
            ConnectorSession session,
            Call source,
            ConnectorExpression body,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType,
            FullTextPushdownMode fullTextMode)
    {
        if (!(body instanceof Call call)) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }

        if (OR_FUNCTION_NAME.equals(call.getFunctionName())) {
            if (call.getArguments().isEmpty()) {
                return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
            }
            List<ElasticsearchPredicateTranslation<ConnectorExpression>> branches = call.getArguments().stream()
                    .map(argument -> translateAnalyzedAnyMatch(
                            session,
                            source,
                            argument,
                            lambdaVariable,
                            column,
                            elementType,
                            fullTextMode))
                    .toList();
            return ElasticsearchPredicateComposer.or(source, branches);
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> like = ElasticsearchFullTextPredicateTranslator.translateLikeElement(
                session,
                source,
                call,
                lambdaVariable,
                column,
                fullTextMode,
                APPROXIMATE_ANY_MATCH);
        if (like.isPresent()) {
            return like.orElseThrow();
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> startsWith = ElasticsearchFullTextPredicateTranslator.translateStartsWithElement(
                source,
                call,
                lambdaVariable,
                column,
                fullTextMode,
                APPROXIMATE_ANY_MATCH);
        if (startsWith.isPresent()) {
            return startsWith.orElseThrow();
        }

        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> regexp = ElasticsearchFullTextPredicateTranslator.translateRegexpElement(
                source,
                call,
                lambdaVariable,
                column,
                fullTextMode,
                APPROXIMATE_ANY_MATCH);
        if (regexp.isPresent()) {
            return regexp.orElseThrow();
        }

        if (EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())) {
            if (call.getArguments().size() != 2) {
                return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
            }
            for (int variableIndex = 0; variableIndex < 2; variableIndex++) {
                if (isLambdaVariable(call.getArguments().get(variableIndex), lambdaVariable)
                        && call.getArguments().get(1 - variableIndex) instanceof Constant constant
                        && constant.getValue() != null
                        && constant.getType().equals(elementType)) {
                    return translateElementMembership(
                            source,
                            column,
                            elementType,
                            List.of(ElasticsearchRemotePredicateTranslator.getValue(elementType, constant.getValue())),
                            fullTextMode,
                            EXACT_ANY_MATCH,
                            APPROXIMATE_ANY_MATCH);
                }
            }
        }

        if (IN_PREDICATE_FUNCTION_NAME.equals(call.getFunctionName())
                && call.getArguments().size() == 2
                && isLambdaVariable(call.getArguments().get(0), lambdaVariable)) {
            return translateConstantArray(call.getArguments().get(1), elementType)
                    .map(values -> translateElementMembership(
                            source,
                            column,
                            elementType,
                            values,
                            fullTextMode,
                            EXACT_ANY_MATCH,
                            APPROXIMATE_ANY_MATCH))
                    .orElseGet(() -> ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION));
        }

        return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateElementMembership(
            ConnectorExpression source,
            ElasticsearchColumnHandle column,
            Type elementType,
            List<Object> values,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateTranslation.Reason exactReason,
            ElasticsearchPredicateTranslation.Reason approximateReason)
    {
        if (values.isEmpty()) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }

        if (exactArrayElementType(column).filter(elementType::equals).isPresent()) {
            ElasticsearchRemotePredicate predicate = values.size() == 1
                    ? new ElasticsearchRemotePredicate.Term(column.predicateName(), values.getFirst())
                    : new ElasticsearchRemotePredicate.Terms(column.predicateName(), values);
            return ElasticsearchPredicateTranslation.exact(predicate, exactReason);
        }

        if (fullTextMode == FullTextPushdownMode.UNSAFE && isAnalyzedTextArray(column) && elementType instanceof VarcharType) {
            ElasticsearchPredicateCompositionPolicy policy = ElasticsearchPredicateCompositionPolicy.DEFAULT;
            if (values.size() > policy.maxBooleanClauses()) {
                return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
            }
            List<ElasticsearchPredicateTranslation<ConnectorExpression>> predicates = values.stream()
                    .map(value -> ElasticsearchPredicateTranslation.<ConnectorExpression>approximate(
                            new ElasticsearchRemotePredicate.MatchPhrase(column.predicateName(), (String) value),
                            approximateReason))
                    .toList();
            if (predicates.size() == 1) {
                ElasticsearchRemotePredicate predicate = predicates.getFirst().remotePredicate().orElseThrow();
                if (!ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
                    return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
                }
                return predicates.getFirst();
            }
            return ElasticsearchPredicateComposer.or(source, predicates);
        }

        return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchBody(
            ConnectorExpression expression,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (!(expression instanceof Call call)) {
            return Optional.empty();
        }

        if (EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())) {
            return translateAnyMatchEquality(call, lambdaVariable, column, elementType);
        }
        if (IN_PREDICATE_FUNCTION_NAME.equals(call.getFunctionName())) {
            return translateAnyMatchIn(call, lambdaVariable, column, elementType);
        }
        if (LESS_THAN_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())
                || LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())
                || GREATER_THAN_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())
                || GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName())) {
            return translateAnyMatchRange(call, lambdaVariable, column, elementType);
        }
        if (OR_FUNCTION_NAME.equals(call.getFunctionName())) {
            return translateAnyMatchOr(call, lambdaVariable, column, elementType);
        }
        if (AND_FUNCTION_NAME.equals(call.getFunctionName())) {
            return translateAnyMatchAnd(call, lambdaVariable, column, elementType);
        }
        return Optional.empty();
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchEquality(
            Call call,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (call.getArguments().size() != 2) {
            return Optional.empty();
        }

        for (int variableIndex = 0; variableIndex < 2; variableIndex++) {
            if (isLambdaVariable(call.getArguments().get(variableIndex), lambdaVariable)
                    && call.getArguments().get(1 - variableIndex) instanceof Constant constant
                    && constant.getValue() != null
                    && constant.getType().equals(elementType)) {
                return Optional.of(new ElasticsearchRemotePredicate.Term(
                        column.predicateName(),
                        ElasticsearchRemotePredicateTranslator.getValue(elementType, constant.getValue())));
            }
        }
        return Optional.empty();
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchIn(
            Call call,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (call.getArguments().size() != 2 || !isLambdaVariable(call.getArguments().get(0), lambdaVariable)) {
            return Optional.empty();
        }

        return translateConstantArray(call.getArguments().get(1), elementType)
                .map(values -> values.size() == 1
                        ? new ElasticsearchRemotePredicate.Term(column.predicateName(), values.getFirst())
                        : new ElasticsearchRemotePredicate.Terms(column.predicateName(), values));
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchRange(
            Call call,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (!supportsExactRange(elementType) || call.getArguments().size() != 2) {
            return Optional.empty();
        }

        boolean variableOnLeft = isLambdaVariable(call.getArguments().get(0), lambdaVariable);
        boolean variableOnRight = isLambdaVariable(call.getArguments().get(1), lambdaVariable);
        if (variableOnLeft == variableOnRight) {
            return Optional.empty();
        }

        ConnectorExpression constantExpression = call.getArguments().get(variableOnLeft ? 1 : 0);
        if (!(constantExpression instanceof Constant constant)
                || constant.getValue() == null
                || !constant.getType().equals(elementType)) {
            return Optional.empty();
        }

        Object value = ElasticsearchRemotePredicateTranslator.getValue(elementType, constant.getValue());
        boolean lessThan = LESS_THAN_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName());
        boolean lessThanOrEqual = LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName());
        boolean greaterThan = GREATER_THAN_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName());
        boolean inclusive = lessThanOrEqual || GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName());

        boolean upperBound = variableOnLeft ? (lessThan || lessThanOrEqual) : (greaterThan || GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName()));
        ElasticsearchRemotePredicate.Bound bound = new ElasticsearchRemotePredicate.Bound(value, inclusive);
        return Optional.of(new ElasticsearchRemotePredicate.Range(
                column.predicateName(),
                upperBound ? Optional.empty() : Optional.of(bound),
                upperBound ? Optional.of(bound) : Optional.empty()));
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchOr(
            Call call,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (call.getArguments().isEmpty()) {
            return Optional.empty();
        }

        List<ElasticsearchRemotePredicate> predicates = new ArrayList<>(call.getArguments().size());
        for (ConnectorExpression argument : call.getArguments()) {
            Optional<ElasticsearchRemotePredicate> translated = translateAnyMatchBody(argument, lambdaVariable, column, elementType);
            if (translated.isEmpty()) {
                return Optional.empty();
            }
            predicates.add(translated.orElseThrow());
        }
        if (predicates.size() == 1) {
            return Optional.of(predicates.getFirst());
        }
        return Optional.of(new ElasticsearchRemotePredicate.Or(predicates));
    }

    private static Optional<ElasticsearchRemotePredicate> translateAnyMatchAnd(
            Call call,
            Variable lambdaVariable,
            ElasticsearchColumnHandle column,
            Type elementType)
    {
        if (call.getArguments().isEmpty()) {
            return Optional.empty();
        }

        Optional<ElasticsearchRemotePredicate.Bound> lower = Optional.empty();
        Optional<ElasticsearchRemotePredicate.Bound> upper = Optional.empty();
        for (ConnectorExpression argument : call.getArguments()) {
            Optional<ElasticsearchRemotePredicate> translated = translateAnyMatchBody(argument, lambdaVariable, column, elementType);
            if (translated.isEmpty() || !(translated.orElseThrow() instanceof ElasticsearchRemotePredicate.Range range)) {
                return Optional.empty();
            }
            if (range.lower().isPresent()) {
                if (lower.isPresent()) {
                    return Optional.empty();
                }
                lower = range.lower();
            }
            if (range.upper().isPresent()) {
                if (upper.isPresent()) {
                    return Optional.empty();
                }
                upper = range.upper();
            }
        }
        if (lower.isEmpty() && upper.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ElasticsearchRemotePredicate.Range(column.predicateName(), lower, upper));
    }

    private static Optional<List<Object>> translateConstantArray(ConnectorExpression expression, Type elementType)
    {
        if (expression instanceof Constant constant
                && constant.getType() instanceof ArrayType constantArrayType
                && constantArrayType.getElementType().equals(elementType)
                && constant.getValue() instanceof Block values) {
            if (values.getPositionCount() == 0) {
                return Optional.empty();
            }
            List<Object> translatedValues = new ArrayList<>(values.getPositionCount());
            for (int position = 0; position < values.getPositionCount(); position++) {
                if (values.isNull(position)) {
                    return Optional.empty();
                }
                translatedValues.add(ElasticsearchRemotePredicateTranslator.getValue(
                        elementType,
                        readNativeValue(elementType, values, position)));
            }
            return Optional.of(translatedValues);
        }

        if (expression instanceof Call arrayConstructor
                && ARRAY_CONSTRUCTOR_FUNCTION_NAME.equals(arrayConstructor.getFunctionName())
                && !arrayConstructor.getArguments().isEmpty()) {
            List<Object> translatedValues = new ArrayList<>(arrayConstructor.getArguments().size());
            for (ConnectorExpression argument : arrayConstructor.getArguments()) {
                if (!(argument instanceof Constant constant)
                        || constant.getValue() == null
                        || !constant.getType().equals(elementType)) {
                    return Optional.empty();
                }
                translatedValues.add(ElasticsearchRemotePredicateTranslator.getValue(elementType, constant.getValue()));
            }
            return Optional.of(translatedValues);
        }
        return Optional.empty();
    }

    private static boolean isLambdaVariable(ConnectorExpression expression, Variable lambdaVariable)
    {
        return expression instanceof Variable variable && variable.equals(lambdaVariable);
    }

    private static boolean supportsExactRange(Type elementType)
    {
        return elementType.equals(TINYINT)
                || elementType.equals(SMALLINT)
                || elementType.equals(INTEGER)
                || elementType.equals(BIGINT)
                || elementType.equals(REAL)
                || elementType.equals(DOUBLE)
                || elementType.equals(TIMESTAMP_MILLIS);
    }

    private static ElasticsearchColumnHandle column(Map<String, ColumnHandle> assignments, Variable variable)
    {
        ColumnHandle column = assignments.get(variable.getName());
        return column instanceof ElasticsearchColumnHandle elasticsearchColumn ? elasticsearchColumn : null;
    }

    private static Optional<Type> exactArrayElementType(ElasticsearchColumnHandle column)
    {
        Optional<Type> elementType = arrayElementType(column);
        if (elementType.isEmpty()) {
            return Optional.empty();
        }

        if (column.elasticsearchType() instanceof PrimitiveType primitiveType
                && primitiveType.name().equalsIgnoreCase("text")
                && primitiveType.keyword().isEmpty()) {
            return Optional.empty();
        }
        return elementType;
    }

    private static Optional<Type> arrayElementType(ElasticsearchColumnHandle column)
    {
        if (column == null || !column.sourceValueSemanticsExact() || !(column.type() instanceof ArrayType arrayType)) {
            return Optional.empty();
        }

        Type elementType = arrayType.getElementType();
        if (elementType.equals(TIMESTAMP_MILLIS)) {
            return column.elasticsearchType() instanceof DateTimeType ? Optional.of(elementType) : Optional.empty();
        }
        if (!(column.elasticsearchType() instanceof PrimitiveType)) {
            return Optional.empty();
        }

        boolean supportedElementType = elementType.equals(TINYINT)
                || elementType.equals(SMALLINT)
                || elementType.equals(INTEGER)
                || elementType.equals(BIGINT)
                || elementType.equals(REAL)
                || elementType.equals(DOUBLE)
                || elementType.equals(BOOLEAN)
                || elementType instanceof VarcharType
                || elementType.getBaseName().equalsIgnoreCase("ipaddress");
        return supportedElementType ? Optional.of(elementType) : Optional.empty();
    }

    private static boolean isAnalyzedTextArray(ElasticsearchColumnHandle column)
    {
        return column != null
                && !column.supportsPredicates()
                && column.type() instanceof ArrayType arrayType
                && arrayType.getElementType() instanceof VarcharType
                && column.elasticsearchType() instanceof PrimitiveType primitiveType
                && primitiveType.name().equalsIgnoreCase("text")
                && primitiveType.keyword().isEmpty();
    }
}
