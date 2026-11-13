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
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FunctionType;
import io.trino.spi.type.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DecimalType.createDecimalType;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchExpressionScriptTranslator
{
    @Test
    void testFloatingArithmeticAndParameterizedBits()
    {
        for (Type type : List.of(REAL, DOUBLE)) {
            Constant zero = type.equals(REAL) ? new Constant(0L, REAL) : new Constant(0.0, DOUBLE);
            Constant nan = type.equals(REAL) ? new Constant((long) Float.floatToRawIntBits(Float.NaN), REAL) : new Constant(Double.NaN, DOUBLE);
            Call source = anyMatch(type, value -> call(BOOLEAN, "$equal", call(type, "$divide", value, zero), nan));
            var translated = translate(source).orElseThrow();
            assertThat(translated.enforcement()).contains(EXACT);
            assertThat(script(translated).source()).contains("sqlArithmetic(", "sqlCompare(");
            assertThat(script(translated).parameters().values()).allMatch(value -> value.type() != ElasticsearchRemotePredicate.ValueType.DOUBLE);
        }
    }

    @Test
    void testNullProofForCoalesceNullifAndCase()
    {
        var safe = translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$greater_than",
                call(INTEGER, "$coalesce", value, number(0)),
                number(10)))).orElseThrow();
        assertThat(safe.enforcement()).contains(EXACT);
        assertThat(translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$greater_than",
                call(INTEGER, "$coalesce", value, number(100)),
                number(10))))).isEmpty();
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$is_null", value)))).isEmpty();
        var nullif = translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$greater_than", call(INTEGER, "$nullif", value, number(0)), number(1)))).orElseThrow();
        assertThat(nullif.enforcement()).contains(EXACT);
        var searched = translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$case",
                call(BOOLEAN, "$is_null", value),
                Constant.FALSE,
                call(BOOLEAN, "$greater_than", value, number(10))))).orElseThrow();
        assertThat(searched.enforcement()).contains(EXACT);
        assertThat(script(searched).source()).contains("Boolean.TRUE.equals(");
        // A FALSE conjunct cannot prove away an independently failing expression on an unindexed NULL element.
        assertThat(translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$and",
                call(BOOLEAN, "$not", call(BOOLEAN, "$is_null", value)),
                call(BOOLEAN, "$equal", call(INTEGER, "$divide", call(INTEGER, "$coalesce", value, number(1)), number(0)), number(1)))))).isEmpty();
        assertThat(translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$case",
                call(BOOLEAN, "$is_null", value),
                Constant.TRUE,
                call(BOOLEAN, "$greater_than", value, number(10)))))).isEmpty();
    }

    @Test
    void testErrorsAndTryCast()
    {
        Call abs = anyMatch(INTEGER, value -> call(BOOLEAN, "$greater_than", call(INTEGER, "abs", value), number(10)));
        var result = translate(abs).orElseThrow();
        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.residual()).contains(abs);
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$greater_than", call(TINYINT, "$try_cast", value), new Constant(10L, TINYINT))))
                .orElseThrow().enforcement()).contains(EXACT);
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$is_null", call(TINYINT, "$try_cast", value))))).isEmpty();
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$greater_than", call(TINYINT, "$cast", value), new Constant(10L, TINYINT))))
                .orElseThrow().enforcement()).contains(PREFILTER);
        assertThat(translate(anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$or",
                call(BOOLEAN, "$equal", call(INTEGER, "$coalesce", value, number(0)), number(12)),
                call(BOOLEAN, "$equal", call(INTEGER, "mod", value, number(2)), number(0)))))
                .orElseThrow().enforcement()).contains(EXACT);
    }

    @Test
    void testStringUnicodeAndCaseConversionPolicy()
    {
        var length = translate(anyMatch(VARCHAR, value -> call(BOOLEAN, "$greater_than", call(BIGINT, "length", value), new Constant(1L, BIGINT)))).orElseThrow();
        assertThat(length.enforcement()).contains(EXACT);
        var lower = translate(anyMatch(VARCHAR, value -> call(BOOLEAN, "$equal", call(VARCHAR, "lower", value), text("abc")))).orElseThrow();
        assertThat(lower.enforcement()).contains(PREFILTER);
        assertThat(script(lower).source()).contains("Unicode case conversion requires Trino", "codePointCount", "codePointAt");
        assertThat(translate(anyMatch(VARCHAR, value -> call(BOOLEAN, "$like", value, text("a_😀%")))).orElseThrow().enforcement()).contains(EXACT);
    }

    @Test
    void testDecimalArithmeticAndCast()
    {
        Type decimal = createDecimalType(12, 2);
        Call source = anyMatch(INTEGER, value -> call(
                BOOLEAN,
                "$greater_than",
                call(decimal, "$add", call(decimal, "$cast", value), new Constant(125L, decimal)),
                new Constant(1000L, decimal)));
        var translated = translate(source).orElseThrow();
        assertThat(translated.enforcement()).contains(PREFILTER);
        assertThat(script(translated).source()).contains("sqlDecimalCheck", "RoundingMode.HALF_UP");
    }

    @Test
    void testTemporalNullProofChecksEveryArgument()
    {
        Constant epoch = new Constant(0L, TIMESTAMP_MILLIS);
        Call reverseDiff = anyMatch(TIMESTAMP_MILLIS, value -> call(BOOLEAN, "$less_than_or_equal", call(BIGINT, "date_diff", text("day"), epoch, value), new Constant(0L, BIGINT)));
        assertThat(translate(reverseDiff).orElseThrow().enforcement()).contains(PREFILTER);
        Call failingAmount = anyMatch(TIMESTAMP_MILLIS, value -> call(
                BOOLEAN,
                "$less_than",
                call(TIMESTAMP_MILLIS,
                        "date_add",
                        text("day"),
                        call(BIGINT, "$add", call(BIGINT, "$coalesce", call(BIGINT, "year", value), new Constant(Long.MAX_VALUE, BIGINT)), new Constant(1L, BIGINT)),
                        value),
                epoch));
        assertThat(translate(failingAmount)).isEmpty();
    }

    @Test
    void testUnknownFunctionsCapturedVariablesAndBudget()
    {
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$equal", new Variable("captured", INTEGER), value)))).isEmpty();
        assertThat(translate(anyMatch(INTEGER, value -> call(BOOLEAN, "$greater_than", call(INTEGER, "unregistered", value), number(0))))).isEmpty();
        Call source = anyMatch(VARCHAR, value -> call(BOOLEAN, "$greater_than", call(BIGINT, "length", value), new Constant(1L, BIGINT)));
        var result = ElasticsearchExpressionScriptTranslator.translate(source, (Lambda) source.getArguments().get(1), column(VARCHAR), new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 100));
        assertThat(result.orElseThrow().remotePredicate()).isEmpty();
        assertThat(result.orElseThrow().residual()).contains(source);
    }

    private static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(Call source)
    {
        Lambda lambda = (Lambda) source.getArguments().get(1);
        return ElasticsearchExpressionScriptTranslator.translate(source, lambda, column(lambda.getArguments().getFirst().getType()), ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    private static ElasticsearchRemotePredicate.ArrayScript script(ElasticsearchPredicateTranslation<ConnectorExpression> result)
    {
        var predicate = result.remotePredicate().orElseThrow();
        if (predicate instanceof ElasticsearchRemotePredicate.Enforced enforced) {
            predicate = enforced.predicate();
        }
        return (ElasticsearchRemotePredicate.ArrayScript) predicate;
    }

    private static Call anyMatch(Type type, Function<Variable, ConnectorExpression> expression)
    {
        Variable value = new Variable("x", type);
        return call(
                BOOLEAN,
                "any_match",
                new Variable("values", new ArrayType(type)),
                new Lambda(new FunctionType(List.of(type), BOOLEAN), List.of(value), expression.apply(value)));
    }

    private static ElasticsearchColumnHandle column(Type type)
    {
        String mapping = Map.of(INTEGER, "integer", BIGINT, "long", TINYINT, "byte", REAL, "float", DOUBLE, "double", VARCHAR, "keyword", BOOLEAN, "boolean", TIMESTAMP_MILLIS, "date").get(type);
        return new ElasticsearchColumnHandle(List.of("Values"), new ArrayType(type), type.equals(TIMESTAMP_MILLIS) ? new DateTimeType(List.of()) : new PrimitiveType(mapping), new IntegerDecoder.Descriptor("Values"), false);
    }

    private static Call call(Type type, String name, ConnectorExpression... arguments)
    {
        return new Call(type, new FunctionName(name), List.of(arguments));
    }

    private static Constant number(long value)
    {
        return new Constant(value, INTEGER);
    }

    private static Constant text(String value)
    {
        return new Constant(utf8Slice(value), VARCHAR);
    }
}
