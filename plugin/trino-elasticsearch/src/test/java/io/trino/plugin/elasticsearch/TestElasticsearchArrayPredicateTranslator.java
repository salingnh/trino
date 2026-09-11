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
import io.trino.plugin.elasticsearch.decoders.BooleanDecoder;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.TimestampDecoder;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FunctionType;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.APPROXIMATE_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.APPROXIMATE_ARRAY;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_UNSAFE_APPROXIMATE;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.APPROXIMATE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IN_PREDICATE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LIKE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchArrayPredicateTranslator
{
    @Test
    public void testContainsPrimitiveArray()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");

        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("numbers", arrayType), new Constant(42L, INTEGER)));

        assertThat(translate(contains, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Term("Numbers", 42L));
    }

    @Test
    public void testContainsBooleanArray()
    {
        ArrayType arrayType = new ArrayType(BOOLEAN);
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("Flags"),
                arrayType,
                new PrimitiveType("boolean"),
                new BooleanDecoder.Descriptor("Flags"),
                false);

        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("flags", arrayType), new Constant(true, BOOLEAN)));

        assertThat(translate(contains, Map.of("flags", column)))
                .contains(new ElasticsearchRemotePredicate.Term("Flags", true));
    }

    @Test
    public void testContainsTimestampArray()
    {
        ArrayType arrayType = new ArrayType(TIMESTAMP_MILLIS);
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("Times"),
                arrayType,
                new DateTimeType(List.of("strict_date_optional_time", "epoch_millis")),
                new TimestampDecoder.Descriptor("Times"),
                false);

        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("times", arrayType), new Constant(1_000_000L, TIMESTAMP_MILLIS)));

        assertThat(translate(contains, Map.of("times", column)))
                .contains(new ElasticsearchRemotePredicate.Term("Times", "1970-01-01T00:00:01"));
    }

    @Test
    public void testContainsTextArrayUsesKeywordSubfield()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("Tags"),
                arrayType,
                new PrimitiveType("text", Optional.of("keyword")),
                new VarcharDecoder.Descriptor("Tags"),
                false);

        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("tags", arrayType), new Constant(utf8Slice("ExactValue"), VARCHAR)));

        assertThat(translate(contains, Map.of("tags", column)))
                .contains(new ElasticsearchRemotePredicate.Term("Tags.keyword", "ExactValue"));
    }

    @Test
    public void testAnalyzedTextArrayRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("tags", arrayType), new Constant(utf8Slice("value"), VARCHAR)));

        assertThat(translate(contains, Map.of("tags", column))).isEmpty();
    }

    @Test
    public void testArraysOverlapUsesTerms()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call arraysOverlap = new Call(
                BOOLEAN,
                new FunctionName("arrays_overlap"),
                List.of(new Variable("numbers", arrayType), new Constant(integerBlock(1, 2, 3), arrayType)));

        assertThat(translate(arraysOverlap, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Terms("Numbers", List.of(1L, 2L, 3L)));
    }

    @Test
    public void testArraysOverlapWithEmptyConstantArrayRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call arraysOverlap = new Call(
                BOOLEAN,
                new FunctionName("arrays_overlap"),
                List.of(new Variable("numbers", arrayType), new Constant(integerBlock(), arrayType)));

        assertThat(translate(arraysOverlap, Map.of("numbers", column))).isEmpty();
    }

    @Test
    public void testArraysOverlapWithNullElementRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        BlockBuilder builder = INTEGER.createBlockBuilder(null, 2);
        INTEGER.writeLong(builder, 1);
        builder.appendNull();
        Call arraysOverlap = new Call(
                BOOLEAN,
                new FunctionName("arrays_overlap"),
                List.of(new Variable("numbers", arrayType), new Constant(builder.build(), arrayType)));

        assertThat(translate(arraysOverlap, Map.of("numbers", column))).isEmpty();
    }

    @Test
    public void testAnyMatchEqualityUsesTerm()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                List.of(element, new Constant(42L, INTEGER))));

        assertThat(translate(anyMatch, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Term("Numbers", 42L));
    }

    @Test
    public void testAnyMatchInUsesTerms()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                IN_PREDICATE_FUNCTION_NAME,
                List.of(element, new Constant(integerBlock(1, 2, 3), arrayType))));

        assertThat(translate(anyMatch, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Terms("Numbers", List.of(1L, 2L, 3L)));
    }

    @Test
    public void testAnyMatchRangeUsesRange()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                GREATER_THAN_OPERATOR_FUNCTION_NAME,
                List.of(element, new Constant(10L, INTEGER))));

        assertThat(translate(anyMatch, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Range(
                        "Numbers",
                        Optional.of(new ElasticsearchRemotePredicate.Bound(10L, false)),
                        Optional.empty()));
    }

    @Test
    public void testAnyMatchRangeConjunctionUsesSingleRange()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                AND_FUNCTION_NAME,
                List.of(
                        new Call(BOOLEAN, GREATER_THAN_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(10L, INTEGER))),
                        new Call(BOOLEAN, LESS_THAN_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(20L, INTEGER))))));

        assertThat(translate(anyMatch, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Range(
                        "Numbers",
                        Optional.of(new ElasticsearchRemotePredicate.Bound(10L, false)),
                        Optional.of(new ElasticsearchRemotePredicate.Bound(20L, false))));
    }

    @Test
    public void testAnyMatchDisjunctionPreservesExistentialSemantics()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                OR_FUNCTION_NAME,
                List.of(
                        new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(1L, INTEGER))),
                        new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(2L, INTEGER))))));

        assertThat(translate(anyMatch, Map.of("numbers", column)))
                .contains(new ElasticsearchRemotePredicate.Or(List.of(
                        new ElasticsearchRemotePredicate.Term("Numbers", 1L),
                        new ElasticsearchRemotePredicate.Term("Numbers", 2L))));
    }

    @Test
    public void testAnyMatchUnsafeConjunctionRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                AND_FUNCTION_NAME,
                List.of(
                        new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(1L, INTEGER))),
                        new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(2L, INTEGER))))));

        assertThat(translate(anyMatch, Map.of("numbers", column))).isEmpty();
    }

    @Test
    public void testAnyMatchAnalyzedTextRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call anyMatch = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("value"), VARCHAR))));

        assertThat(translate(anyMatch, Map.of("tags", column))).isEmpty();
    }

    @Test
    public void testUnsafeContainsAnalyzedTextArrayUsesApproximateMatchPhrase()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("tags", arrayType), new Constant(utf8Slice("Nguyen Van"), VARCHAR)));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                contains,
                Map.of("tags", column),
                UNSAFE).orElseThrow();

        assertThat(translation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Nguyen Van"),
                APPROXIMATE));
        assertThat(translation.enforcement()).contains(APPROXIMATE);
        assertThat(translation.reason()).isEqualTo(APPROXIMATE_ARRAY);
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testUnsafeArraysOverlapAnalyzedTextArrayUsesApproximateMatchPhrases()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call arraysOverlap = new Call(
                BOOLEAN,
                new FunctionName("arrays_overlap"),
                List.of(new Variable("tags", arrayType), new Constant(varcharBlock("Nguyen Van", "Tran Thi"), arrayType)));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                arraysOverlap,
                Map.of("tags", column),
                UNSAFE).orElseThrow();

        assertThat(translation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Or(List.of(
                new ElasticsearchRemotePredicate.Enforced(new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Nguyen Van"), APPROXIMATE),
                new ElasticsearchRemotePredicate.Enforced(new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Tran Thi"), APPROXIMATE))));
        assertThat(translation.enforcement()).contains(APPROXIMATE);
        assertThat(translation.reason()).isEqualTo(ElasticsearchPredicateTranslation.Reason.BOOLEAN_OR);
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testUnsafeAnyMatchEqualityAndInAnalyzedTextArrayAreApproximate()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call equality = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("Nguyen Van"), VARCHAR))));
        Call in = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                IN_PREDICATE_FUNCTION_NAME,
                List.of(element, new Constant(varcharBlock("Nguyen Van", "Tran Thi"), arrayType))));

        ElasticsearchPredicateTranslation<ConnectorExpression> equalityTranslation = translateWithContract(
                equality,
                Map.of("tags", column),
                UNSAFE).orElseThrow();
        ElasticsearchPredicateTranslation<ConnectorExpression> inTranslation = translateWithContract(
                in,
                Map.of("tags", column),
                UNSAFE).orElseThrow();

        assertThat(equalityTranslation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Nguyen Van"),
                APPROXIMATE));
        assertThat(equalityTranslation.enforcement()).contains(APPROXIMATE);
        assertThat(equalityTranslation.reason()).isEqualTo(APPROXIMATE_ANY_MATCH);
        assertThat(equalityTranslation.remaining()).isEmpty();
        assertThat(equalityTranslation.residual()).isEmpty();

        assertThat(inTranslation.enforcement()).contains(APPROXIMATE);
        assertThat(inTranslation.remotePredicate()).isPresent();
        assertThat(inTranslation.remaining()).isEmpty();
        assertThat(inTranslation.residual()).isEmpty();
    }

    @Test
    public void testUnsafeAnyMatchLikeAndPrefixAnalyzedTextArrayUseScalarStrategies()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call literalLike = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                LIKE_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("Nguyen Van"), VARCHAR))));
        Call containsLike = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                LIKE_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("%Nguyen Van%"), VARCHAR))));
        Call prefixLike = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                LIKE_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("Nguyen%"), VARCHAR))));
        Call startsWith = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                new FunctionName("starts_with"),
                List.of(element, new Constant(utf8Slice("Nguyen"), VARCHAR))));

        assertThat(translateWithContract(literalLike, Map.of("tags", column), UNSAFE).orElseThrow().remotePredicate())
                .contains(new ElasticsearchRemotePredicate.Enforced(
                        new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Nguyen Van"),
                        APPROXIMATE));
        assertThat(translateWithContract(containsLike, Map.of("tags", column), UNSAFE).orElseThrow().remotePredicate())
                .contains(new ElasticsearchRemotePredicate.Enforced(
                        new ElasticsearchRemotePredicate.MatchPhrase("Tags", "Nguyen Van"),
                        APPROXIMATE));
        assertThat(translateWithContract(prefixLike, Map.of("tags", column), UNSAFE).orElseThrow().remotePredicate())
                .contains(new ElasticsearchRemotePredicate.Enforced(
                        new ElasticsearchRemotePredicate.MatchPhrasePrefix("Tags", "Nguyen"),
                        APPROXIMATE));
        assertThat(translateWithContract(startsWith, Map.of("tags", column), UNSAFE).orElseThrow().remotePredicate())
                .contains(new ElasticsearchRemotePredicate.Enforced(
                        new ElasticsearchRemotePredicate.MatchPhrasePrefix("Tags", "Nguyen"),
                        APPROXIMATE));
    }

    @Test
    public void testUnsafeAnyMatchUnsupportedLikeShapeRemainsLocal()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call unsupportedLike = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                LIKE_FUNCTION_NAME,
                List.of(element, new Constant(utf8Slice("Ng% yen"), VARCHAR))));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                unsupportedLike,
                Map.of("tags", column),
                UNSAFE).orElseThrow();

        assertThat(translation.remotePredicate()).isEmpty();
        assertThat(translation.remaining()).contains(unsupportedLike);
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testUnsafeAnyMatchEscapedLikeUsesScalarStrategy()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call escapedLike = anyMatch("tags", arrayType, element -> new Call(
                BOOLEAN,
                LIKE_FUNCTION_NAME,
                List.of(
                        element,
                        new Constant(utf8Slice("Nguyen\\%"), VARCHAR),
                        new Constant(utf8Slice("\\"), VARCHAR))));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                escapedLike,
                Map.of("tags", column),
                UNSAFE).orElseThrow();

        assertThat(translation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.Regexp("Tags", "Nguyen%"),
                APPROXIMATE));
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testAnyMatchInWithNullConstantRemainsResidual()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        BlockBuilder builder = INTEGER.createBlockBuilder(null, 2);
        INTEGER.writeLong(builder, 1);
        builder.appendNull();
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                IN_PREDICATE_FUNCTION_NAME,
                List.of(element, new Constant(builder.build(), arrayType))));

        assertThat(translate(anyMatch, Map.of("numbers", column))).isEmpty();
    }

    @Test
    public void testExactArrayTranslationReportsExactEnforcement()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("numbers", arrayType), new Constant(42L, INTEGER)));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                contains,
                Map.of("numbers", column),
                SAFE).orElseThrow();

        assertThat(translation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Term("Numbers", 42L));
        assertThat(translation.enforcement()).contains(EXACT);
        assertThat(translation.reason()).isEqualTo(EXACT_ARRAY);
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testExactAnyMatchTranslationReportsExactEnforcement()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = integerArrayColumn("Numbers");
        Call anyMatch = anyMatch("numbers", arrayType, element -> new Call(
                BOOLEAN,
                EQUAL_OPERATOR_FUNCTION_NAME,
                List.of(element, new Constant(42L, INTEGER))));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                anyMatch,
                Map.of("numbers", column),
                SAFE).orElseThrow();

        assertThat(translation.remotePredicate()).contains(new ElasticsearchRemotePredicate.Term("Numbers", 42L));
        assertThat(translation.enforcement()).contains(EXACT);
        assertThat(translation.reason()).isEqualTo(EXACT_ANY_MATCH);
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    @Test
    public void testAnalyzedArrayTranslationRemainsLocalOutsideUnsafe()
    {
        ArrayType arrayType = new ArrayType(VARCHAR);
        ElasticsearchColumnHandle column = analyzedTextArrayColumn("Tags");
        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("tags", arrayType), new Constant(utf8Slice("value"), VARCHAR)));

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = translateWithContract(
                contains,
                Map.of("tags", column),
                SAFE).orElseThrow();

        assertThat(translation.remotePredicate()).isEmpty();
        assertThat(translation.enforcement()).isEmpty();
        assertThat(translation.remaining()).contains(contains);
        assertThat(translation.residual()).isEmpty();
        assertThat(translation.reason()).isEqualTo(UNSUPPORTED_EXPRESSION);
    }

    @Test
    public void testApproximateArrayTranslationIsRepresentableWithoutExactStrengthening()
    {
        ElasticsearchRemotePredicate approximatePredicate = new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.MatchPhrase("Tags", "value"),
                ElasticsearchRemotePredicate.Enforcement.APPROXIMATE);

        ElasticsearchPredicateTranslation<ConnectorExpression> translation = ElasticsearchPredicateTranslation.approximate(
                approximatePredicate,
                FULL_TEXT_UNSAFE_APPROXIMATE);

        assertThat(translation.enforcement()).contains(ElasticsearchRemotePredicate.Enforcement.APPROXIMATE);
        assertThat(translation.remotePredicate()).contains(approximatePredicate);
        assertThat(translation.remaining()).isEmpty();
        assertThat(translation.residual()).isEmpty();
    }

    private static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateWithContract(
            ConnectorExpression expression,
            Map<String, io.trino.spi.connector.ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        return ElasticsearchArrayPredicateTranslator.translate(
                TestingConnectorSession.builder().build(),
                expression,
                assignments,
                fullTextMode);
    }

    private static Optional<ElasticsearchRemotePredicate> translate(
            ConnectorExpression expression,
            Map<String, io.trino.spi.connector.ColumnHandle> assignments)
    {
        return translateWithContract(expression, assignments, SAFE)
                .flatMap(ElasticsearchPredicateTranslation::remotePredicate);
    }

    private static Call anyMatch(String arrayName, ArrayType arrayType, Function<Variable, ConnectorExpression> body)
    {
        Variable element = new Variable("element", arrayType.getElementType());
        Lambda lambda = new Lambda(
                new FunctionType(List.of(arrayType.getElementType()), BOOLEAN),
                List.of(element),
                body.apply(element));
        return new Call(
                BOOLEAN,
                new FunctionName("any_match"),
                List.of(new Variable(arrayName, arrayType), lambda));
    }

    private static ElasticsearchColumnHandle integerArrayColumn(String remoteName)
    {
        return new ElasticsearchColumnHandle(
                List.of(remoteName),
                new ArrayType(INTEGER),
                new PrimitiveType("integer"),
                new IntegerDecoder.Descriptor(remoteName),
                false);
    }

    private static ElasticsearchColumnHandle analyzedTextArrayColumn(String remoteName)
    {
        return new ElasticsearchColumnHandle(
                List.of(remoteName),
                new ArrayType(VARCHAR),
                new PrimitiveType("text"),
                new VarcharDecoder.Descriptor(remoteName),
                false);
    }

    private static Block integerBlock(long... values)
    {
        BlockBuilder builder = INTEGER.createBlockBuilder(null, values.length);
        for (long value : values) {
            INTEGER.writeLong(builder, value);
        }
        return builder.build();
    }

    private static Block varcharBlock(String... values)
    {
        BlockBuilder builder = VARCHAR.createBlockBuilder(null, values.length);
        for (String value : values) {
            VARCHAR.writeSlice(builder, utf8Slice(value));
        }
        return builder.build();
    }
}
