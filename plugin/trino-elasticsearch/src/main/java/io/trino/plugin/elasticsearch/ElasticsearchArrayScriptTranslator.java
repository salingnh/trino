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
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.spi.expression.StandardFunctions.ADD_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.DIVIDE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.MODULO_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.MULTIPLY_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NEGATE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.SUBTRACT_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TinyintType.TINYINT;

/**
 * Compiles a positive any_match WHERE predicate over integral doc values. Every comparison must depend on the
 * lambda element, so the body is NULL on a NULL element; ignoring unindexed NULL elements preserves its TRUE set.
 * Fallible arithmetic admits the document on an arithmetic error and retains the entire SQL residual. It must
 * never silently wrap an overflow, skip a failing element, or turn an error into a false remote restriction.
 */
final class ElasticsearchArrayScriptTranslator
{
    private static final int MAX_EXPRESSION_NODES = 128;
    private static final FunctionName MOD_FUNCTION_NAME = new FunctionName("mod");
    private static final String SCRIPT =
            """
            long sqlCheck(long value, long minimum, long maximum) {
                if (value < minimum || value > maximum) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return value;
            }
            long sqlDivide(long left, long right) {
                if (left == Long.MIN_VALUE && right == -1) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return left / right;
            }
            long sqlAdd(long left, long right) {
                long result = left + right;
                if (((left ^ result) & (right ^ result)) < 0) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return result;
            }
            long sqlSubtract(long left, long right) {
                long result = left - right;
                if (((left ^ right) & (left ^ result)) < 0) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return result;
            }
            long sqlMultiply(long left, long right) {
                if (left == Long.MIN_VALUE && right == -1) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                long result = left * right;
                if (right != 0 && result / right != left) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return result;
            }
            long sqlNegate(long value) {
                if (value == Long.MIN_VALUE) {
                    throw new ArithmeticException('SQL integer overflow');
                }
                return -value;
            }
            if (!doc.containsKey(params.field)) {
                return false;
            }
            for (def value : doc[params.field]) {
                long x = value;
                try {
                    if (%s) {
                        return true;
                    }
                }
                catch (ArithmeticException e) {
                    return true;
                }
            }
            return false;
            """;

    private ElasticsearchArrayScriptTranslator() {}

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
            Call source,
            Lambda lambda,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (!column.sourceValueSemanticsExact()
                || !(column.type() instanceof ArrayType arrayType)
                || !(column.elasticsearchType() instanceof PrimitiveType mapping)
                || !mapping.aggregatable()
                || !isIntegral(arrayType.getElementType())) {
            return Optional.empty();
        }
        String mappingType = mapping.name();
        Type elementType = arrayType.getElementType();
        if (!(elementType.equals(TINYINT) && mappingType.equals("byte")
                || elementType.equals(SMALLINT) && mappingType.equals("short")
                || elementType.equals(INTEGER) && mappingType.equals("integer")
                || elementType.equals(BIGINT) && mappingType.equals("long"))) {
            return Optional.empty();
        }

        Compiler compiler = new Compiler(lambda.getArguments().getFirst(), policy);
        Optional<Expression> expression = compiler.booleanExpression(lambda.getBody(), 0);
        if (expression.isEmpty()) {
            return Optional.empty();
        }
        compiler.parameters.put("field", ElasticsearchRemotePredicate.Value.of(column.remoteName()));
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.ArrayScript(
                column.remoteName(), SCRIPT.formatted(expression.orElseThrow().source()), compiler.parameters);
        if (!ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
            return Optional.of(ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION));
        }
        if (expression.orElseThrow().fallible()) {
            return Optional.of(ElasticsearchPredicateTranslation.prefilter(predicate, source, PREFILTER_ARRAY_SCRIPT));
        }
        return Optional.of(ElasticsearchPredicateTranslation.exact(predicate, EXACT_ARRAY_SCRIPT));
    }

    private static final class Compiler
    {
        private final Variable element;
        private final ElasticsearchPredicateCompositionPolicy policy;
        private final Map<String, ElasticsearchRemotePredicate.Value> parameters = new LinkedHashMap<>();
        private int nodes;

        private Compiler(Variable element, ElasticsearchPredicateCompositionPolicy policy)
        {
            this.element = element;
            this.policy = policy;
        }

        private boolean admit(int depth)
        {
            return++nodes <= Math.min(MAX_EXPRESSION_NODES, policy.maxBooleanClauses()) && depth <= policy.maxBooleanDepth();
        }

        private Optional<Expression> booleanExpression(ConnectorExpression expression, int depth)
        {
            if (!admit(depth) || !expression.getType().equals(BOOLEAN) || !(expression instanceof Call call)) {
                return Optional.empty();
            }
            FunctionName name = call.getFunctionName();
            if (AND_FUNCTION_NAME.equals(name) || OR_FUNCTION_NAME.equals(name) || NOT_FUNCTION_NAME.equals(name)) {
                if (call.getArguments().isEmpty() || (NOT_FUNCTION_NAME.equals(name) && call.getArguments().size() != 1)) {
                    return Optional.empty();
                }
                List<String> children = new ArrayList<>();
                boolean fallible = false;
                for (ConnectorExpression argument : call.getArguments()) {
                    Optional<Expression> child = booleanExpression(argument, depth + 1);
                    if (child.isEmpty()) {
                        return Optional.empty();
                    }
                    children.add(child.orElseThrow().source());
                    fallible |= child.orElseThrow().fallible();
                }
                String source;
                if (NOT_FUNCTION_NAME.equals(name)) {
                    source = "!(" + children.getFirst() + ")";
                }
                else {
                    source = "(" + String.join(AND_FUNCTION_NAME.equals(name) ? " && " : " || ", children) + ")";
                }
                return Optional.of(new Expression(source, BOOLEAN, true, fallible, OptionalLong.empty()));
            }

            String operator;
            if (EQUAL_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = "==";
            }
            else if (NOT_EQUAL_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = "!=";
            }
            else if (LESS_THAN_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = "<";
            }
            else if (LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = "<=";
            }
            else if (GREATER_THAN_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = ">";
            }
            else if (GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(name)) {
                operator = ">=";
            }
            else {
                return Optional.empty();
            }
            if (call.getArguments().size() != 2) {
                return Optional.empty();
            }
            Optional<Expression> left = numericExpression(call.getArguments().get(0), depth + 1);
            Optional<Expression> right = numericExpression(call.getArguments().get(1), depth + 1);
            if (left.isEmpty() || right.isEmpty()
                    || !left.orElseThrow().type().equals(right.orElseThrow().type())
                    || !(left.orElseThrow().dependent() || right.orElseThrow().dependent())) {
                return Optional.empty();
            }
            return Optional.of(new Expression(
                    "(" + left.orElseThrow().source() + " " + operator + " " + right.orElseThrow().source() + ")",
                    BOOLEAN,
                    true,
                    left.orElseThrow().fallible() || right.orElseThrow().fallible(),
                    OptionalLong.empty()));
        }

        private Optional<Expression> numericExpression(ConnectorExpression expression, int depth)
        {
            if (!admit(depth) || !isIntegral(expression.getType())) {
                return Optional.empty();
            }
            Type type = expression.getType();
            if (expression.equals(element)) {
                return Optional.of(new Expression("x", type, true, false, OptionalLong.empty()));
            }
            if (expression instanceof Constant constant && constant.getValue() instanceof Long value) {
                Bounds bounds = bounds(type);
                if (value < bounds.minimum() || value > bounds.maximum()) {
                    return Optional.empty();
                }
                String parameter = "p" + parameters.size();
                parameters.put(parameter, ElasticsearchRemotePredicate.Value.of(value));
                return Optional.of(new Expression("((long) params." + parameter + ")", type, false, false, OptionalLong.of(value)));
            }
            if (!(expression instanceof Call call)) {
                return Optional.empty();
            }
            FunctionName name = call.getFunctionName();
            if ((CAST_FUNCTION_NAME.equals(name) || NEGATE_FUNCTION_NAME.equals(name)) && call.getArguments().size() == 1) {
                Optional<Expression> argument = numericExpression(call.getArguments().getFirst(), depth + 1);
                if (argument.isEmpty()) {
                    return Optional.empty();
                }
                Expression value = argument.orElseThrow();
                if (CAST_FUNCTION_NAME.equals(name)) {
                    if (bounds(value.type()).minimum() < bounds(type).minimum() || bounds(value.type()).maximum() > bounds(type).maximum()) {
                        return Optional.empty();
                    }
                    return Optional.of(new Expression(value.source(), type, value.dependent(), value.fallible(), value.constant()));
                }
                if (!value.type().equals(type)) {
                    return Optional.empty();
                }
                if (!value.dependent()) {
                    return Optional.empty();
                }
                return Optional.of(new Expression(checked("sqlNegate(" + value.source() + ")", type), type, value.dependent(), true, OptionalLong.empty()));
            }
            boolean modulo = MODULO_FUNCTION_NAME.equals(name) || MOD_FUNCTION_NAME.equals(name);
            if (!(ADD_FUNCTION_NAME.equals(name) || SUBTRACT_FUNCTION_NAME.equals(name) || MULTIPLY_FUNCTION_NAME.equals(name)
                    || DIVIDE_FUNCTION_NAME.equals(name) || modulo) || call.getArguments().size() != 2) {
                return Optional.empty();
            }
            Optional<Expression> left = numericExpression(call.getArguments().get(0), depth + 1);
            Optional<Expression> right = numericExpression(call.getArguments().get(1), depth + 1);
            if (left.isEmpty() || right.isEmpty() || !left.orElseThrow().type().equals(type) || !right.orElseThrow().type().equals(type)) {
                return Optional.empty();
            }
            Expression first = left.orElseThrow();
            Expression second = right.orElseThrow();
            boolean fallible = first.fallible() || second.fallible();
            String source;
            if (modulo || DIVIDE_FUNCTION_NAME.equals(name)) {
                if (second.constant().isPresent() && second.constant().orElseThrow() == 0) {
                    return Optional.empty();
                }
                if (modulo) {
                    source = "(" + first.source() + " % " + second.source() + ")";
                    fallible |= second.constant().isEmpty();
                }
                else {
                    source = checked("sqlDivide(" + first.source() + ", " + second.source() + ")", type);
                    fallible |= second.constant().isEmpty() || second.constant().orElseThrow() == -1;
                }
            }
            else {
                String method;
                if (ADD_FUNCTION_NAME.equals(name)) {
                    method = "sqlAdd";
                }
                else if (SUBTRACT_FUNCTION_NAME.equals(name)) {
                    method = "sqlSubtract";
                }
                else {
                    method = "sqlMultiply";
                }
                source = checked(method + "(" + first.source() + ", " + second.source() + ")", type);
                fallible = true;
            }
            boolean dependent = first.dependent() || second.dependent();
            if (fallible && !dependent) {
                return Optional.empty();
            }
            return Optional.of(new Expression(source, type, dependent, fallible, OptionalLong.empty()));
        }
    }

    private static String checked(String expression, Type type)
    {
        Bounds bounds = bounds(type);
        String minimum = bounds.minimum() == Long.MIN_VALUE ? "Long.MIN_VALUE" : bounds.minimum() + "L";
        String maximum = bounds.maximum() == Long.MAX_VALUE ? "Long.MAX_VALUE" : bounds.maximum() + "L";
        return "sqlCheck(" + expression + ", " + minimum + ", " + maximum + ")";
    }

    private static boolean isIntegral(Type type)
    {
        return type.equals(TINYINT) || type.equals(SMALLINT) || type.equals(INTEGER) || type.equals(BIGINT);
    }

    private static Bounds bounds(Type type)
    {
        if (type.equals(TINYINT)) {
            return new Bounds(Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        if (type.equals(SMALLINT)) {
            return new Bounds(Short.MIN_VALUE, Short.MAX_VALUE);
        }
        if (type.equals(INTEGER)) {
            return new Bounds(Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        return new Bounds(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private record Bounds(long minimum, long maximum) {}

    private record Expression(String source, Type type, boolean dependent, boolean fallible, OptionalLong constant) {}
}
