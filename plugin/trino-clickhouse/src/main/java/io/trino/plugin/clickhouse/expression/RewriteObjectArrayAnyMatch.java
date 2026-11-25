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
package io.trino.plugin.clickhouse.expression;

import com.google.common.collect.ImmutableList;
import io.airlift.slice.Slice;
import io.trino.plugin.jdbc.JdbcColumnHandle;
import io.trino.plugin.jdbc.QueryParameter;
import io.trino.plugin.jdbc.expression.ParameterizedExpression;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FieldDereference;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TinyintType.TINYINT;

public final class RewriteObjectArrayAnyMatch
{
    private static final FunctionName ANY_MATCH = new FunctionName("any_match");
    private static final List<Type> INTEGER_TYPES = List.of(TINYINT, SMALLINT, INTEGER, BIGINT);

    private RewriteObjectArrayAnyMatch() {}

    public static Optional<ParameterizedExpression> rewrite(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            UnaryOperator<String> quoted)
    {
        // This rewrite preserves the TRUE rows of a filter. It must only be applied to a whole
        // conjunct, since arrayExists collapses UNKNOWN to FALSE.
        if (!(expression instanceof Call call) ||
                !call.getFunctionName().equals(ANY_MATCH) ||
                !call.getType().equals(BOOLEAN) ||
                call.getArguments().size() != 2 ||
                !(call.getArguments().get(0) instanceof Variable array) ||
                !(array.getType() instanceof ArrayType arrayType) ||
                !(arrayType.getElementType() instanceof RowType rowType) ||
                !(assignments.get(array.getName()) instanceof JdbcColumnHandle column) ||
                !column.getColumnType().equals(arrayType) ||
                !(call.getArguments().get(1) instanceof Lambda lambda) ||
                lambda.getArguments().size() != 1 ||
                !lambda.getArguments().getFirst().getType().equals(rowType)) {
            return Optional.empty();
        }

        ImmutableList.Builder<QueryParameter> parameters = ImmutableList.builder();
        return rewritePredicate(lambda.getBody(), lambda.getArguments().getFirst(), parameters)
                .map(predicate -> new ParameterizedExpression(
                        "arrayExists(item -> ifNull(" + predicate + ", 0), " + quoted.apply(column.getColumnName()) + ")",
                        parameters.build()));
    }

    private static Optional<String> rewritePredicate(ConnectorExpression expression, Variable row, ImmutableList.Builder<QueryParameter> parameters)
    {
        if (!expression.getType().equals(BOOLEAN)) {
            return Optional.empty();
        }
        if (expression instanceof Constant constant) {
            return Optional.of(switch (constant.getValue()) {
                case null -> "CAST(NULL AS Nullable(UInt8))";
                case Boolean value -> value ? "1" : "0";
                default -> throw new IllegalArgumentException("Unexpected boolean constant: " + constant);
            });
        }
        if (!(expression instanceof Call call)) {
            return Optional.empty();
        }

        FunctionName function = call.getFunctionName();
        List<ConnectorExpression> arguments = call.getArguments();
        if (function.equals(AND_FUNCTION_NAME) || function.equals(OR_FUNCTION_NAME)) {
            if (arguments.size() < 2) {
                return Optional.empty();
            }
            ImmutableList.Builder<String> predicates = ImmutableList.builder();
            for (ConnectorExpression argument : arguments) {
                Optional<String> predicate = rewritePredicate(argument, row, parameters);
                if (predicate.isEmpty()) {
                    return Optional.empty();
                }
                predicates.add(predicate.get());
            }
            String operator = function.equals(AND_FUNCTION_NAME) ? " AND " : " OR ";
            return Optional.of("(" + String.join(operator, predicates.build()) + ")");
        }
        if (function.equals(IS_NULL_FUNCTION_NAME) && arguments.size() == 1) {
            return fieldAccess(arguments.getFirst(), row).map(field -> "isNull(" + field + ")");
        }
        if (function.equals(NOT_FUNCTION_NAME) &&
                arguments.size() == 1 &&
                arguments.getFirst() instanceof Call nested &&
                nested.getFunctionName().equals(IS_NULL_FUNCTION_NAME) &&
                nested.getArguments().size() == 1) {
            return fieldAccess(nested.getArguments().getFirst(), row).map(field -> "isNotNull(" + field + ")");
        }
        if (function.equals(EQUAL_OPERATOR_FUNCTION_NAME) && arguments.size() == 2) {
            if (arguments.get(1) instanceof Constant constant) {
                return rewriteEquality(arguments.getFirst(), constant, row, parameters);
            }
            if (arguments.getFirst() instanceof Constant constant) {
                return rewriteEquality(arguments.get(1), constant, row, parameters);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> rewriteEquality(
            ConnectorExpression expression,
            Constant constant,
            Variable row,
            ImmutableList.Builder<QueryParameter> parameters)
    {
        Optional<String> field = fieldAccess(expression, row);
        if (field.isEmpty()) {
            return Optional.empty();
        }
        if (constant.getValue() == null) {
            return Optional.of("CAST(NULL AS Nullable(UInt8))");
        }
        Type type = expression.getType();
        if (type instanceof VarcharType && constant.getType() instanceof VarcharType) {
            // Malformed source UTF-8 is decoded as U+FFFD by the JDBC driver.
            if (((Slice) constant.getValue()).toStringUtf8().indexOf('\uFFFD') >= 0) {
                return Optional.empty();
            }
        }
        else if (!type.equals(constant.getType())) {
            return Optional.empty();
        }

        parameters.add(new QueryParameter(constant.getType(), Optional.of(constant.getValue())));
        return Optional.of("(" + field.get() + " = ?)");
    }

    private static Optional<String> fieldAccess(ConnectorExpression expression, Variable row)
    {
        if (expression instanceof Call cast && cast.getFunctionName().equals(CAST_FUNCTION_NAME) && cast.getArguments().size() == 1) {
            ConnectorExpression input = cast.getArguments().getFirst();
            int inputRank = INTEGER_TYPES.indexOf(input.getType());
            if (inputRank >= 0 && inputRank < INTEGER_TYPES.indexOf(cast.getType())) {
                return fieldAccess(input, row);
            }
        }
        if (!(expression instanceof FieldDereference field) || !field.getTarget().equals(row)) {
            return Optional.empty();
        }
        Type type = field.getType();
        if (!(type instanceof VarcharType) && !type.equals(BOOLEAN) && !INTEGER_TYPES.contains(type)) {
            return Optional.empty();
        }
        return Optional.of("tupleElement(item, " + (field.getField() + 1) + ")");
    }
}
