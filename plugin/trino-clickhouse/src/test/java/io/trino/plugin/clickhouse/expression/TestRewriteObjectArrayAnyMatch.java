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

import io.trino.plugin.jdbc.JdbcColumnHandle;
import io.trino.plugin.jdbc.JdbcTypeHandle;
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
import io.trino.spi.type.FunctionType;
import io.trino.spi.type.RowType;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RowType.field;
import static io.trino.spi.type.RowType.rowType;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.spi.type.VarcharType.createVarcharType;
import static org.assertj.core.api.Assertions.assertThat;

class TestRewriteObjectArrayAnyMatch
{
    private static final FunctionName ANY_MATCH = new FunctionName("any_match");
    private static final RowType USER_TYPE = rowType(field("name", VARCHAR), field("city", VARCHAR), field("priority", TINYINT));
    private static final ArrayType USERS_TYPE = new ArrayType(USER_TYPE);
    private static final Variable USERS = new Variable("users_0", USERS_TYPE);
    private static final Variable USER = new Variable("u", USER_TYPE);
    private static final FieldDereference NAME = new FieldDereference(VARCHAR, USER, 0);
    private static final FieldDereference CITY = new FieldDereference(VARCHAR, USER, 1);
    private static final FieldDereference PRIORITY = new FieldDereference(TINYINT, USER, 2);
    private static final JdbcColumnHandle COLUMN = new JdbcColumnHandle(
            "remote users",
            new JdbcTypeHandle(Types.ARRAY, Optional.of("Array(Tuple(name String, city Nullable(String), priority Int8))"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
            USERS_TYPE);
    private static final Map<String, ColumnHandle> ASSIGNMENTS = Map.of(USERS.getName(), COLUMN);

    @Test
    void testSameTupleForAnd()
    {
        Constant name = string("Bình");
        Constant city = string("Huế");
        assertThat(rewrite(anyMatch(predicate(AND_FUNCTION_NAME, predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, name), predicate(EQUAL_OPERATOR_FUNCTION_NAME, CITY, city)))))
                .contains(new ParameterizedExpression(
                        "arrayExists(item -> ifNull(((tupleElement(item, 1) = ?) AND (tupleElement(item, 2) = ?)), 0), \"remote users\")",
                        List.of(parameter(name), parameter(city))));
    }

    @Test
    void testOrWithNullableMembers()
    {
        Constant name = string("O'Neil");
        Call cityIsNull = predicate(IS_NULL_FUNCTION_NAME, CITY);
        Call nameIsNotNull = predicate(NOT_FUNCTION_NAME, predicate(IS_NULL_FUNCTION_NAME, NAME));
        Call nameMatches = predicate(EQUAL_OPERATOR_FUNCTION_NAME, name, NAME);
        assertThat(rewrite(anyMatch(predicate(OR_FUNCTION_NAME, cityIsNull, predicate(AND_FUNCTION_NAME, nameIsNotNull, nameMatches)))))
                .contains(new ParameterizedExpression(
                        "arrayExists(item -> ifNull((isNull(tupleElement(item, 2)) OR (isNotNull(tupleElement(item, 1)) AND (tupleElement(item, 1) = ?))), 0), \"remote users\")",
                        List.of(parameter(name))));
    }

    @Test
    void testUnknownPreservedInsideLambda()
    {
        Constant nullVarchar = new Constant(null, VARCHAR);
        Call equalityToNull = predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, nullVarchar);
        assertThat(rewrite(anyMatch(predicate(OR_FUNCTION_NAME, equalityToNull, predicate(IS_NULL_FUNCTION_NAME, CITY)))))
                .contains(new ParameterizedExpression(
                        "arrayExists(item -> ifNull((CAST(NULL AS Nullable(UInt8)) OR isNull(tupleElement(item, 2))), 0), \"remote users\")",
                        List.of()));
        assertThat(rewrite(anyMatch(new Constant(null, BOOLEAN))))
                .contains(new ParameterizedExpression(
                        "arrayExists(item -> ifNull(CAST(NULL AS Nullable(UInt8)), 0), \"remote users\")",
                        List.of()));
    }

    @Test
    void testWideningIntegerCoercion()
    {
        Constant value = new Constant(120L, INTEGER);
        Call widenPriority = new Call(INTEGER, CAST_FUNCTION_NAME, List.of(PRIORITY));
        assertThat(rewrite(anyMatch(predicate(EQUAL_OPERATOR_FUNCTION_NAME, widenPriority, value))))
                .contains(new ParameterizedExpression(
                        "arrayExists(item -> ifNull((tupleElement(item, 3) = ?), 0), \"remote users\")",
                        List.of(parameter(value))));
    }

    @Test
    void testUnsupportedExpressionsStayInTrino()
    {
        Call supported = anyMatch(predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, string("Bình")));
        assertThat(rewrite(predicate(NOT_FUNCTION_NAME, supported))).isEmpty();
        assertThat(rewrite(predicate(IS_NULL_FUNCTION_NAME, supported))).isEmpty();
        assertThat(rewrite(predicate(OR_FUNCTION_NAME, supported, supported))).isEmpty();
        assertThat(rewrite(anyMatch(predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, string("\uFFFD"))))).isEmpty();
        assertThat(rewrite(anyMatch(predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, CITY)))).isEmpty();
        assertThat(rewrite(anyMatch(predicate(EQUAL_OPERATOR_FUNCTION_NAME, NAME, new Variable("captured", VARCHAR))))).isEmpty();
        assertThat(rewrite(anyMatch(predicate(AND_FUNCTION_NAME, predicate(IS_NULL_FUNCTION_NAME, NAME), predicate(new FunctionName("unsupported"), CITY))))).isEmpty();
        assertThat(RewriteObjectArrayAnyMatch.rewrite(supported, Map.of(), name -> name)).isEmpty();
    }

    private static Optional<ParameterizedExpression> rewrite(ConnectorExpression expression)
    {
        return RewriteObjectArrayAnyMatch.rewrite(expression, ASSIGNMENTS, name -> "\"" + name + "\"");
    }

    private static Call anyMatch(ConnectorExpression body)
    {
        return predicate(ANY_MATCH, USERS, new Lambda(new FunctionType(List.of(USER_TYPE), BOOLEAN), List.of(USER), body));
    }

    private static Call predicate(FunctionName name, ConnectorExpression... arguments)
    {
        return new Call(BOOLEAN, name, List.of(arguments));
    }

    private static Constant string(String value)
    {
        return new Constant(utf8Slice(value), createVarcharType(value.length()));
    }

    private static QueryParameter parameter(Constant constant)
    {
        return new QueryParameter(constant.getType(), Optional.of(constant.getValue()));
    }
}
