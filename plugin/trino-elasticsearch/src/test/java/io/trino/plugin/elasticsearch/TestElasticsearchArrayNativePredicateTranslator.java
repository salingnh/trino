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
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.ARRAY_CONSTRUCTOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.BETWEEN_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IN_PREDICATE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LIKE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.testing.TestingConnectorSession.SESSION;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchArrayNativePredicateTranslator
{
    private static final Variable ELEMENT = new Variable("x", INTEGER);
    private static final ElasticsearchColumnHandle NUMBERS = new ElasticsearchColumnHandle(
            List.of("Numbers"), new ArrayType(INTEGER), new PrimitiveType("integer"), new IntegerDecoder.Descriptor("Numbers"), false);

    @Test
    void testIntersectionsUnionsAndComplements()
    {
        ConnectorExpression between = call(AND_FUNCTION_NAME, compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 10), compare(LESS_THAN_OPERATOR_FUNCTION_NAME, 20));
        assertThat(render(call(AND_FUNCTION_NAME, compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 5), between)))
                .isEqualTo("{\"range\":{\"Numbers\":{\"gt\":10,\"lt\":20}}}");
        assertThat(render(call(AND_FUNCTION_NAME, membership(5L, 12L), compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 10))))
                .isEqualTo("{\"term\":{\"Numbers\":12}}");
        assertThat(render(call(AND_FUNCTION_NAME, compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 20), compare(LESS_THAN_OPERATOR_FUNCTION_NAME, 10))))
                .isEqualTo("{\"match_none\":{}}");
        assertThat(render(call(NOT_FUNCTION_NAME, compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 10))))
                .isEqualTo("{\"range\":{\"Numbers\":{\"lte\":10}}}");
        ElasticsearchRemotePredicate notEqual = translate(compare(NOT_EQUAL_OPERATOR_FUNCTION_NAME, 12)).orElseThrow();
        assertThat(matches(notEqual, List.of(12L, 13L))).isTrue();
        assertThat(matches(notEqual, List.of(12L))).isFalse();
        assertThat(render(call(BETWEEN_FUNCTION_NAME, ELEMENT, constant(10), constant(20))))
                .isEqualTo("{\"range\":{\"Numbers\":{\"gte\":10,\"lte\":20}}}");
    }

    @Test
    void testUnknownAndNullElements()
    {
        assertThat(render(membership(1L, null))).isEqualTo("{\"term\":{\"Numbers\":1}}");
        assertThat(render(call(NOT_FUNCTION_NAME, membership(1L, null)))).isEqualTo("{\"match_none\":{}}");
        ConnectorExpression unknown = new Constant(null, BOOLEAN);
        assertThat(render(call(OR_FUNCTION_NAME, unknown, compare(EQUAL_OPERATOR_FUNCTION_NAME, 12))))
                .isEqualTo("{\"term\":{\"Numbers\":12}}");
        assertThat(render(call(NOT_FUNCTION_NAME, call(OR_FUNCTION_NAME, unknown, compare(EQUAL_OPERATOR_FUNCTION_NAME, 12)))))
                .isEqualTo("{\"match_none\":{}}");
        assertThat(translate(call(IS_NULL_FUNCTION_NAME, ELEMENT))).isEmpty();
        assertThat(render(call(NOT_FUNCTION_NAME, call(IS_NULL_FUNCTION_NAME, ELEMENT))))
                .isEqualTo("{\"exists\":{\"field\":\"Numbers\"}}");
        assertThat(translate(call(OR_FUNCTION_NAME, Constant.TRUE, compare(EQUAL_OPERATOR_FUNCTION_NAME, 12)))).isEmpty();
        assertThat(ElasticsearchArrayNativePredicateTranslator.translateNecessaryCondition(
                SESSION,
                new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(ELEMENT, new Constant(null, INTEGER))),
                ELEMENT,
                NUMBERS,
                ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
    }

    @Test
    void testIntegralBoundsWideningAndResourceLimits()
    {
        for (var type : List.of(TINYINT, SMALLINT, INTEGER, BIGINT)) {
            String mapping = Map.of(TINYINT, "byte", SMALLINT, "short", INTEGER, "integer", BIGINT, "long").get(type);
            Variable element = new Variable("x", type);
            ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(List.of("Numbers"), new ArrayType(type), new PrimitiveType(mapping), new IntegerDecoder.Descriptor("Numbers"), false);
            ConnectorExpression widened = new Call(BIGINT, CAST_FUNCTION_NAME, List.of(element));
            ConnectorExpression predicate = call(
                    AND_FUNCTION_NAME,
                    call(GREATER_THAN_OPERATOR_FUNCTION_NAME, widened, new Constant(10L, BIGINT)),
                    call(LESS_THAN_OPERATOR_FUNCTION_NAME, widened, new Constant(20L, BIGINT)));
            assertThat(ElasticsearchArrayNativePredicateTranslator.translate(SESSION, predicate, element, column, ElasticsearchPredicateCompositionPolicy.DEFAULT))
                    .contains(new ElasticsearchRemotePredicate.Range("Numbers", Optional.of(new ElasticsearchRemotePredicate.Bound(10L, false)), Optional.of(new ElasticsearchRemotePredicate.Bound(20L, false))));
        }
        assertThat(render(compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, Integer.MAX_VALUE))).isEqualTo("{\"match_none\":{}}");
        assertThat(render(call(NOT_FUNCTION_NAME, compare(LESS_THAN_OPERATOR_FUNCTION_NAME, Integer.MIN_VALUE))))
                .isEqualTo("{\"exists\":{\"field\":\"Numbers\"}}");
        ConnectorExpression deep = compare(EQUAL_OPERATOR_FUNCTION_NAME, 1);
        for (int depth = 0; depth < 40; depth++) {
            deep = call(NOT_FUNCTION_NAME, deep);
        }
        assertThat(translate(deep)).isEmpty();
        assertThat(ElasticsearchArrayNativePredicateTranslator.translate(
                SESSION,
                membership(1L, 2L),
                ELEMENT,
                NUMBERS,
                new ElasticsearchPredicateCompositionPolicy(1, 1, 100))).isEmpty();
    }

    @Test
    void testKeywordNativeLeavesAndSameElementPrefixes()
    {
        Variable word = new Variable("x", VARCHAR);
        ElasticsearchColumnHandle words = new ElasticsearchColumnHandle(
                List.of("Words"), new ArrayType(VARCHAR), new PrimitiveType("keyword"), new VarcharDecoder.Descriptor("Words"), false);
        ConnectorExpression prefix = call(new FunctionName("starts_with"), word, new Constant(utf8Slice("ab"), VARCHAR));
        ConnectorExpression narrower = call(LIKE_FUNCTION_NAME, word, new Constant(utf8Slice("abc%"), VARCHAR));
        assertThat(nativeKeyword(call(AND_FUNCTION_NAME, prefix, narrower), word, words))
                .contains(new ElasticsearchRemotePredicate.Prefix("Words", "abc"));
        assertThat(nativeKeyword(call(AND_FUNCTION_NAME, prefix, call(LIKE_FUNCTION_NAME, word, new Constant(utf8Slice("cd%"), VARCHAR))), word, words))
                .contains(new ElasticsearchRemotePredicate.MatchNone());
        assertThat(nativeKeyword(call(LIKE_FUNCTION_NAME, word, new Constant(utf8Slice("a_b%"), VARCHAR)), word, words).orElseThrow())
                .isInstanceOf(ElasticsearchRemotePredicate.Regexp.class);
        assertThat(nativeKeyword(call(new FunctionName("regexp_like"), word, new Constant(utf8Slice("abc"), VARCHAR)), word, words).orElseThrow())
                .isInstanceOf(ElasticsearchRemotePredicate.Regexp.class);
        assertThat(nativeKeyword(call(new FunctionName("regexp_like"), word, new Constant(utf8Slice("(?i)abc"), VARCHAR)), word, words)).isEmpty();
        assertThat(nativeKeyword(call(OR_FUNCTION_NAME, prefix, call(EQUAL_OPERATOR_FUNCTION_NAME, word, new Constant(utf8Slice("z"), VARCHAR))), word, words).orElseThrow())
                .isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        ElasticsearchColumnHandle analyzed = new ElasticsearchColumnHandle(
                List.of("Words"), new ArrayType(VARCHAR), new PrimitiveType("text"), new VarcharDecoder.Descriptor("Words"), false);
        assertThat(nativeKeyword(prefix, word, analyzed)).isEmpty();
        ElasticsearchColumnHandle docValuesOnly = new ElasticsearchColumnHandle(
                List.of("Words"), new ArrayType(VARCHAR), new PrimitiveType("keyword", Optional.empty(), true, true, false), new VarcharDecoder.Descriptor("Words"), false);
        assertThat(nativeKeyword(prefix, word, docValuesOnly)).isEmpty();
    }

    @Test
    void testGeneratedExpressionsAgainstThreeValuedReference()
    {
        List<Example> leaves = List.of(
                new Example(compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, 0), value -> value == null ? null : value > 0),
                new Example(compare(LESS_THAN_OPERATOR_FUNCTION_NAME, 2), value -> value == null ? null : value < 2),
                new Example(compare(EQUAL_OPERATOR_FUNCTION_NAME, 1), value -> value == null ? null : value == 1),
                new Example(membership(1L, null), value -> (value != null && value == 1) ? true : null),
                new Example(call(IS_NULL_FUNCTION_NAME, ELEMENT), value -> value == null),
                new Example(Constant.FALSE, _ -> false),
                new Example(Constant.TRUE, _ -> true),
                new Example(new Constant(null, BOOLEAN), _ -> null));
        Random random = new Random(713);
        List<List<Long>> arrays = List.of(
                List.of(),
                Arrays.asList((Long) null),
                Arrays.asList(null, 0L),
                List.of(-2L, 1L),
                List.of(1L, 3L),
                List.of(0L, 2L),
                List.of((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE));
        int compiled = 0;
        for (int index = 0; index < 400; index++) {
            Example example = generate(random, leaves, 4);
            Optional<ElasticsearchRemotePredicate> remote = translate(example.expression());
            if (Boolean.TRUE.equals(example.evaluate().apply(null))) {
                assertThat(remote).as("NULL elements must remain visible: %s", example.expression()).isEmpty();
                continue;
            }
            assertThat(remote).as("expression %s", example.expression()).isPresent();
            compiled++;
            for (List<Long> values : arrays) {
                boolean expected = values.stream().anyMatch(value -> Boolean.TRUE.equals(example.evaluate().apply(value)));
                assertThat(matches(remote.orElseThrow(), values)).as("expression %s, values %s", example.expression(), values).isEqualTo(expected);
            }
        }
        assertThat(compiled).isGreaterThan(200);
    }

    private static Example generate(Random random, List<Example> leaves, int depth)
    {
        if (depth == 0 || random.nextInt(4) == 0) {
            return leaves.get(random.nextInt(leaves.size()));
        }
        Example left = generate(random, leaves, depth - 1);
        int operator = random.nextInt(3);
        if (operator == 0) {
            return new Example(call(NOT_FUNCTION_NAME, left.expression()), value -> {
                Boolean result = left.evaluate().apply(value);
                return result == null ? null : !result;
            });
        }
        Example right = generate(random, leaves, depth - 1);
        return new Example(
                call(operator == 1 ? AND_FUNCTION_NAME : OR_FUNCTION_NAME, left.expression(), right.expression()),
                value -> logical(left.evaluate().apply(value), right.evaluate().apply(value), operator == 1));
    }

    private static Boolean logical(Boolean left, Boolean right, boolean and)
    {
        if (and && (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right))) {
            return false;
        }
        if (!and && (Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right))) {
            return true;
        }
        if (left == null || right == null) {
            return null;
        }
        return and;
    }

    private record Example(ConnectorExpression expression, Function<Long, Boolean> evaluate) {}

    private static boolean matches(ElasticsearchRemotePredicate predicate, List<Long> values)
    {
        return switch (predicate) {
            case ElasticsearchRemotePredicate.MatchNone _ -> false;
            case ElasticsearchRemotePredicate.Exists _ -> values.stream().anyMatch(value -> value != null);
            case ElasticsearchRemotePredicate.Term term -> values.stream().anyMatch(value -> value != null && value.equals(Long.valueOf(term.value().value())));
            case ElasticsearchRemotePredicate.Terms terms -> terms.values().stream().anyMatch(term -> matches(new ElasticsearchRemotePredicate.Term(terms.field(), term), values));
            case ElasticsearchRemotePredicate.Range range -> values.stream().filter(value -> value != null).anyMatch(value ->
                    range.lower().map(bound -> bound.inclusive() ? value >= Long.parseLong(bound.value().value()) : value > Long.parseLong(bound.value().value())).orElse(true)
                            && range.upper().map(bound -> bound.inclusive() ? value <= Long.parseLong(bound.value().value()) : value < Long.parseLong(bound.value().value())).orElse(true));
            case ElasticsearchRemotePredicate.Or or -> or.predicates().stream().anyMatch(child -> matches(child, values));
            default -> throw new IllegalArgumentException("Unexpected native node " + predicate);
        };
    }

    private static Optional<ElasticsearchRemotePredicate> nativeKeyword(ConnectorExpression expression, Variable element, ElasticsearchColumnHandle column)
    {
        return ElasticsearchArrayNativePredicateTranslator.translate(SESSION, expression, element, column, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    private static Optional<ElasticsearchRemotePredicate> translate(ConnectorExpression expression)
    {
        return ElasticsearchArrayNativePredicateTranslator.translate(SESSION, expression, ELEMENT, NUMBERS, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    private static String render(ConnectorExpression expression)
    {
        return ElasticsearchRemotePredicateQueryBuilder.build(translate(expression).orElseThrow()).toString();
    }

    private static Call call(FunctionName name, ConnectorExpression... arguments)
    {
        return new Call(BOOLEAN, name, List.of(arguments));
    }

    private static Constant constant(long value)
    {
        return new Constant(value, INTEGER);
    }

    private static Call compare(FunctionName name, long value)
    {
        return call(name, ELEMENT, constant(value));
    }

    private static Call membership(Long... values)
    {
        List<ConnectorExpression> constants = new ArrayList<>();
        for (Long value : values) {
            constants.add(new Constant(value, INTEGER));
        }
        return call(IN_PREDICATE_FUNCTION_NAME, ELEMENT, new Call(new ArrayType(INTEGER), ARRAY_CONSTRUCTOR_FUNCTION_NAME, constants));
    }
}
