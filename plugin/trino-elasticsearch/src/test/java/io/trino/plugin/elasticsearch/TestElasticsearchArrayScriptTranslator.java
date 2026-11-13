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
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.Constraint;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FunctionType;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
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
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchArrayScriptTranslator
{
    @Test
    void testAllIntegralTypesAndComparisons()
    {
        for (Type type : List.of(TINYINT, SMALLINT, INTEGER, BIGINT)) {
            for (FunctionName comparison : List.of(
                    EQUAL_OPERATOR_FUNCTION_NAME,
                    NOT_EQUAL_OPERATOR_FUNCTION_NAME,
                    LESS_THAN_OPERATOR_FUNCTION_NAME,
                    LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME,
                    GREATER_THAN_OPERATOR_FUNCTION_NAME,
                    GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME)) {
                Call source = anyMatch(type, element -> compare(comparison, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0));
                ElasticsearchPredicateTranslation<ConnectorExpression> result = translate(source, column(type), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow();
                assertThat(result.enforcement()).contains(EXACT);
                assertThat(result.reason()).isEqualTo(EXACT_ARRAY_SCRIPT);
                assertThat(result.residual()).isEmpty();
                assertThat(script(result).source()).contains(" % ");
            }
        }
    }

    @Test
    void testArithmeticAndNegationRetainWholeResidual()
    {
        for (FunctionName operator : List.of(ADD_FUNCTION_NAME, SUBTRACT_FUNCTION_NAME, MULTIPLY_FUNCTION_NAME, DIVIDE_FUNCTION_NAME)) {
            Call source = anyMatch(INTEGER, element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(operator, element, -1), 10));
            ElasticsearchPredicateTranslation<ConnectorExpression> result = translate(source, column(INTEGER), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow();
            assertThat(result.enforcement()).contains(PREFILTER);
            assertThat(result.reason()).isEqualTo(PREFILTER_ARRAY_SCRIPT);
            assertThat(result.residual()).contains(source);
            assertThat(result.remaining()).isEmpty();
            assertThat(script(result).source()).contains("catch (ArithmeticException e)", "sqlCheck(");
        }
        Call negate = anyMatch(INTEGER, element -> compare(
                GREATER_THAN_OPERATOR_FUNCTION_NAME,
                new Call(INTEGER, NEGATE_FUNCTION_NAME, List.of(element)),
                10));
        assertThat(translate(negate, column(INTEGER), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow().enforcement()).contains(PREFILTER);

        Call divide = anyMatch(INTEGER, element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(DIVIDE_FUNCTION_NAME, element, 2), 10));
        assertThat(translate(divide, column(INTEGER), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow().enforcement()).contains(EXACT);
    }

    @Test
    void testSameElementLogicAndNot()
    {
        Call source = anyMatch(INTEGER, element -> new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(
                new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(
                        compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10),
                        compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0))),
                new Call(BOOLEAN, NOT_FUNCTION_NAME, List.of(compare(NOT_EQUAL_OPERATOR_FUNCTION_NAME, element, -3))))));
        ElasticsearchPredicateTranslation<ConnectorExpression> result = translate(source, column(INTEGER), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow();
        assertThat(result.enforcement()).contains(EXACT);
        assertThat(script(result).source()).contains(" && ", " || ", "!(", "for (def value : doc[params.field])");
    }

    @Test
    void testWideningCoercionAndModFunction()
    {
        Call source = anyMatch(TINYINT, element -> compare(
                EQUAL_OPERATOR_FUNCTION_NAME,
                new Call(INTEGER, new FunctionName("mod"), List.of(
                        new Call(INTEGER, CAST_FUNCTION_NAME, List.of(element)), new Constant(2L, INTEGER))),
                0));
        assertThat(translate(source, column(TINYINT), ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow().enforcement()).contains(EXACT);
    }

    @Test
    void testNativeFirstAndExplicitScriptOptIn()
    {
        Call source = anyMatch(INTEGER, element -> new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(
                compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10),
                compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0))));
        Constraint constraint = new Constraint(TupleDomain.all(), source, Map.of("numbers", column(INTEGER)));
        assertThat(ElasticsearchPredicatePushdownPlanner.plan(TestingConnectorSession.SESSION, constraint, SAFE).remotePredicate()).isEmpty();
        ElasticsearchPredicatePushdownPlanner.Result enabled = ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.SESSION, constraint, SAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, true);
        assertThat(enabled.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        assertThat(((ElasticsearchRemotePredicate.Or) enabled.remotePredicate().orElseThrow()).predicates())
                .anyMatch(ElasticsearchRemotePredicate.Range.class::isInstance)
                .anyMatch(ElasticsearchRemotePredicate.ArrayScript.class::isInstance);
        assertThat(enabled.residualExpressions()).isEmpty();

        Call nativeSource = anyMatch(INTEGER, element -> compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10));
        ElasticsearchPredicatePushdownPlanner.Result nativeResult = ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.SESSION,
                new Constraint(TupleDomain.all(), nativeSource, Map.of("numbers", column(INTEGER))),
                SAFE,
                ElasticsearchPredicateCompositionPolicy.DEFAULT,
                true);
        assertThat(nativeResult.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Range.class);
    }

    @Test
    void testRejectsNullNonStrictAndForeignExpressions()
    {
        for (Function<Variable, ConnectorExpression> body : List.<Function<Variable, ConnectorExpression>>of(
                element -> new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(element, new Constant(null, INTEGER))),
                element -> new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(Constant.TRUE, compare(EQUAL_OPERATOR_FUNCTION_NAME, element, 0))),
                _ -> compare(EQUAL_OPERATOR_FUNCTION_NAME, new Variable("captured", INTEGER), 0),
                element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, new Call(INTEGER, new FunctionName("abs"), List.of(element)), 0),
                element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(DIVIDE_FUNCTION_NAME, element, 0), 0),
                element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 0), 0))) {
            assertThat(translate(anyMatch(INTEGER, body), column(INTEGER), ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
        }
    }

    @Test
    void testMappingProofAndFieldParameterization()
    {
        Call source = anyMatch(INTEGER, element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0));
        for (ElasticsearchColumnHandle column : List.of(
                new ElasticsearchColumnHandle(List.of("Numbers"), new ArrayType(INTEGER), new PrimitiveType("integer", Optional.empty(), false), new IntegerDecoder.Descriptor("Numbers"), false),
                new ElasticsearchColumnHandle(List.of("Numbers"), new ArrayType(INTEGER), new PrimitiveType("integer"), new IntegerDecoder.Descriptor("Numbers"), false, true, false),
                new ElasticsearchColumnHandle(List.of("Numbers"), new ArrayType(INTEGER), new PrimitiveType("integer", Optional.empty(), true, false), new IntegerDecoder.Descriptor("Numbers"), false))) {
            assertThat(translate(source, column, ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
        }
        String field = "Numbers'; return true; //";
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(List.of(field), new ArrayType(INTEGER), new PrimitiveType("integer"), new IntegerDecoder.Descriptor(field), false);
        ElasticsearchRemotePredicate.ArrayScript script = script(translate(source, column, ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow());
        assertThat(script.source()).doesNotContain(field);
        assertThat(script.parameters()).containsEntry("field", ElasticsearchRemotePredicate.Value.of(field));
        assertThat(ElasticsearchRemotePredicateQueryBuilder.build(script).at("/script/script/params/field").asText()).isEqualTo(field);
    }

    @Test
    void testResourceFallback()
    {
        Call source = anyMatch(INTEGER, element -> compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0));
        for (ElasticsearchPredicateCompositionPolicy policy : List.of(
                new ElasticsearchPredicateCompositionPolicy(1, 1, 100, 100_000),
                new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 1))) {
            ElasticsearchPredicateTranslation<ConnectorExpression> result = translate(source, column(INTEGER), policy).orElseThrow();
            assertThat(result.remotePredicate()).isEmpty();
            assertThat(result.residual()).contains(source);
        }
        assertThat(translate(source, column(INTEGER), new ElasticsearchPredicateCompositionPolicy(100, 10, 1, 100_000))).isEmpty();
        assertThat(translate(source, column(INTEGER), new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 100_000, 1))).isEmpty();
    }

    @Test
    void testScriptBudgetRetainsNativeNecessaryCondition()
    {
        Call source = anyMatch(INTEGER, element -> new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(
                compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10),
                compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0))));
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchArrayPredicateTranslator.translate(
                TestingConnectorSession.SESSION,
                source,
                Map.of("numbers", column(INTEGER)),
                SAFE,
                new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 300),
                true).orElseThrow();
        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.residual()).contains(source);
        assertThat(((ElasticsearchRemotePredicate.Enforced) result.remotePredicate().orElseThrow()).predicate())
                .isInstanceOf(ElasticsearchRemotePredicate.Range.class);
    }

    @Test
    void testHybridUsesWholeAndScriptAndKeepsFallibleArithmeticSeparate()
    {
        Call conjunction = anyMatch(INTEGER, element -> new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(
                compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10),
                compare(EQUAL_OPERATOR_FUNCTION_NAME, arithmetic(MODULO_FUNCTION_NAME, element, 2), 0))));
        ElasticsearchPredicateTranslation<ConnectorExpression> hybrid = ElasticsearchArrayPredicateTranslator.translate(
                TestingConnectorSession.SESSION, conjunction, Map.of("numbers", column(INTEGER)), SAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, true).orElseThrow();
        assertThat(hybrid.reason()).isEqualTo(ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY_HYBRID);
        assertThat(hybrid.residual()).isEmpty();
        assertThat(hybrid.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        ElasticsearchRemotePredicate.And and = (ElasticsearchRemotePredicate.And) hybrid.remotePredicate().orElseThrow();
        assertThat(and.predicates()).hasSize(2);
        assertThat(((ElasticsearchRemotePredicate.ArrayScript) and.predicates().getLast()).source()).contains(" && ", " % ");

        Call fallible = anyMatch(INTEGER, element -> new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(
                compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, element, 10),
                compare(GREATER_THAN_OPERATOR_FUNCTION_NAME, arithmetic(ADD_FUNCTION_NAME, element, 1), 0))));
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchArrayPredicateTranslator.translate(
                TestingConnectorSession.SESSION, fallible, Map.of("numbers", column(INTEGER)), SAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, true).orElseThrow();
        assertThat(prefilter.reason()).isEqualTo(PREFILTER_ARRAY_SCRIPT);
        assertThat(prefilter.residual()).contains(fallible);
        assertThat(((ElasticsearchRemotePredicate.Enforced) prefilter.remotePredicate().orElseThrow()).predicate()).isInstanceOf(ElasticsearchRemotePredicate.ArrayScript.class);
    }

    private static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(Call source, ElasticsearchColumnHandle column, ElasticsearchPredicateCompositionPolicy policy)
    {
        return ElasticsearchArrayScriptTranslator.translate(source, (Lambda) source.getArguments().get(1), column, policy);
    }

    private static ElasticsearchRemotePredicate.ArrayScript script(ElasticsearchPredicateTranslation<ConnectorExpression> result)
    {
        ElasticsearchRemotePredicate predicate = result.remotePredicate().orElseThrow();
        if (predicate instanceof ElasticsearchRemotePredicate.Enforced enforced) {
            predicate = enforced.predicate();
        }
        return (ElasticsearchRemotePredicate.ArrayScript) predicate;
    }

    private static Call anyMatch(Type type, Function<Variable, ConnectorExpression> body)
    {
        Variable element = new Variable("element", type);
        return new Call(BOOLEAN, new FunctionName("any_match"), List.of(
                new Variable("numbers", new ArrayType(type)),
                new Lambda(new FunctionType(List.of(type), BOOLEAN), List.of(element), body.apply(element))));
    }

    private static Call arithmetic(FunctionName operator, Variable element, long value)
    {
        return new Call(element.getType(), operator, List.of(element, new Constant(value, element.getType())));
    }

    private static Call compare(FunctionName operator, ConnectorExpression value, long constant)
    {
        return new Call(BOOLEAN, operator, List.of(value, new Constant(constant, value.getType())));
    }

    private static ElasticsearchColumnHandle column(Type type)
    {
        String mapping;
        if (type.equals(TINYINT)) {
            mapping = "byte";
        }
        else if (type.equals(SMALLINT)) {
            mapping = "short";
        }
        else if (type.equals(INTEGER)) {
            mapping = "integer";
        }
        else {
            mapping = "long";
        }
        return new ElasticsearchColumnHandle(List.of("Numbers"), new ArrayType(type), new PrimitiveType(mapping), new IntegerDecoder.Descriptor("Numbers"), false);
    }
}
