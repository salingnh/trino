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
import io.trino.plugin.elasticsearch.client.IndexMetadata.ScaledFloatType;
import io.trino.plugin.elasticsearch.decoders.DoubleDecoder;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchScalarPredicateTranslator
{
    private static final Variable NUMBER = new Variable("number", INTEGER);
    private static final Variable WORD = new Variable("word", VARCHAR);
    private static final ElasticsearchColumnHandle NUMBER_COLUMN = new ElasticsearchColumnHandle(
            List.of("Number"),
            INTEGER,
            new PrimitiveType("integer"),
            new IntegerDecoder.Descriptor("Number"),
            true);
    private static final ElasticsearchColumnHandle WORD_COLUMN = new ElasticsearchColumnHandle(
            List.of("Review", "Comment"),
            VARCHAR,
            new PrimitiveType("keyword"),
            new VarcharDecoder.Descriptor("Review.Comment"),
            true);
    private static final Map<String, ColumnHandle> ASSIGNMENTS = Map.of("number", NUMBER_COLUMN, "word", WORD_COLUMN);

    @Test
    void testScalarFlagIsIndependentOfArrayFlag()
    {
        Call source = call(BOOLEAN, "$like", call(VARCHAR, "lower", WORD), text("%sá%"));
        var disabled = plan(source, true, false);
        assertThat(disabled.remotePredicate()).isEmpty();
        assertThat(disabled.remainingConstraint().getExpression()).isEqualTo(source);
        var enabled = plan(source, false, true);
        assertThat(enabled.remotePredicate().orElseThrow().enforcement()).isEqualTo(PREFILTER);
        assertThat(enabled.residualExpressions()).containsExactly(source);
        var enforced = (ElasticsearchRemotePredicate.Enforced) enabled.remotePredicate().orElseThrow();
        var script = (ElasticsearchRemotePredicate.ScalarScript) enforced.predicate();
        assertThat(script.field()).isEqualTo("Review.Comment");
        assertThat(script.parameters()).containsEntry("field", ElasticsearchRemotePredicate.Value.of("Review.Comment"));
    }

    @Test
    void testNativePriorityAndHybridOr()
    {
        Call nativeSource = call(BOOLEAN, "$and", call(BOOLEAN, "$greater_than", NUMBER, number(10)), call(BOOLEAN, "$less_than", NUMBER, number(20)));
        assertThat(plan(nativeSource, false, true).remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Range.class);
        Call source = call(
                BOOLEAN,
                "$or",
                call(BOOLEAN, "$greater_than", NUMBER, number(10)),
                call(BOOLEAN, "$equal", call(INTEGER, "mod", NUMBER, number(2)), number(0)));
        var result = plan(source, false, true);
        assertThat(result.residualExpressions()).isEmpty();
        assertThat(result.remainingConstraint().getExpression()).isEqualTo(Constant.TRUE);
        var or = (ElasticsearchRemotePredicate.Or) result.remotePredicate().orElseThrow();
        assertThat(or.predicates()).anyMatch(ElasticsearchRemotePredicate.Range.class::isInstance)
                .anyMatch(ElasticsearchRemotePredicate.ScalarScript.class::isInstance);
    }

    @Test
    void testMissingScalarIsEvaluatedAsNull()
    {
        Call source = call(BOOLEAN, "$greater_than", call(INTEGER, "$coalesce", NUMBER, number(100)), number(10));
        var result = plan(source, false, true);
        assertThat(result.remotePredicate().orElseThrow().enforcement()).isEqualTo(EXACT);
        assertThat(result.residualExpressions()).isEmpty();
        assertThat(((ElasticsearchRemotePredicate.ScalarScript) result.remotePredicate().orElseThrow()).source())
                .contains("def x = null", "Expected a scalar SQL field");
        Call nullCast = call(BOOLEAN, "$is_null", call(TINYINT, "$try_cast", NUMBER));
        assertThat(plan(nullCast, false, true).residualExpressions()).isEmpty();
    }

    @Test
    void testFallibleExpressionRemainsWhole()
    {
        Call source = call(
                BOOLEAN,
                "$and",
                call(BOOLEAN, "$greater_than", NUMBER, number(10)),
                call(BOOLEAN, "$equal", call(INTEGER, "$divide", NUMBER, number(0)), number(1)));
        var result = plan(source, false, true);
        assertThat(result.residualExpressions()).containsExactly(source);
        var enforced = (ElasticsearchRemotePredicate.Enforced) result.remotePredicate().orElseThrow();
        assertThat(enforced.enforcement()).isEqualTo(PREFILTER);
        assertThat(enforced.predicate()).isInstanceOf(ElasticsearchRemotePredicate.ScalarScript.class);
    }

    @Test
    void testUnsupportedOrAndCapturedColumnsRemainLocal()
    {
        Call unsupported = call(BOOLEAN, "$equal", call(INTEGER, "unregistered", NUMBER), number(1));
        assertThat(plan(call(BOOLEAN, "$or", call(BOOLEAN, "$greater_than", NUMBER, number(10)), unsupported), false, true)
                .remotePredicate()).isEmpty();
        Variable other = new Variable("other", INTEGER);
        var constraint = new Constraint(
                TupleDomain.all(),
                call(BOOLEAN, "$greater_than", call(INTEGER, "$add", NUMBER, other), number(10)),
                Map.of("number", NUMBER_COLUMN, "other", new ElasticsearchColumnHandle(List.of("Other"), INTEGER, new PrimitiveType("integer"), new IntegerDecoder.Descriptor("Other"), true)));
        assertThat(ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.builder().build(),
                constraint,
                SAFE,
                ElasticsearchPredicateCompositionPolicy.DEFAULT,
                false,
                true).remotePredicate()).isEmpty();
    }

    @Test
    void testUnsafeMappingsAndBudgetDoNotGenerateScript()
    {
        var source = call(BOOLEAN, "$like", call(VARCHAR, "lower", WORD), text("%sá%"));
        var unsafe = new ElasticsearchColumnHandle(WORD_COLUMN.path(), VARCHAR, new PrimitiveType("keyword"), WORD_COLUMN.decoderDescriptor(), true, true, false);
        assertThat(ElasticsearchScalarPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.of("word", unsafe), ElasticsearchPredicateCompositionPolicy.DEFAULT, true)).isEmpty();
        var budget = new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 100);
        assertThat(ElasticsearchScalarPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, ASSIGNMENTS, budget, true)
                .orElseThrow().remotePredicate()).isEmpty();
    }

    @Test
    void testFloatingDomainsRetainZeroAndNanSemantics()
    {
        var column = new ElasticsearchColumnHandle(List.of("DoubleValue"), DOUBLE, new PrimitiveType("double"), new DoubleDecoder.Descriptor("DoubleValue"), true);
        for (Domain domain : List.of(Domain.singleValue(DOUBLE, 0.0), Domain.multipleValues(DOUBLE, List.of(0.0, Double.NaN), true))) {
            var constraint = new Constraint(TupleDomain.withColumnDomains(Map.of(column, domain)));
            var result = ElasticsearchPredicatePushdownPlanner.plan(
                    TestingConnectorSession.builder().build(),
                    constraint,
                    SAFE,
                    ElasticsearchPredicateCompositionPolicy.DEFAULT,
                    false,
                    true);
            assertThat(result.remainingConstraint().getSummary().isAll()).isTrue();
            assertThat(result.residualFilter().isAll()).isTrue();
            assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.ScalarScript.class);
            assertThat(result.remotePredicate().orElseThrow().enforcement()).isEqualTo(EXACT);
            assertThat(ElasticsearchPredicatePushdownPlanner.plan(
                    TestingConnectorSession.builder().build(),
                    constraint,
                    SAFE,
                    ElasticsearchPredicateCompositionPolicy.DEFAULT,
                    false,
                    false).remotePredicate()).isEmpty();
        }
    }

    @Test
    void testScalarNativeDoesNotBypassUnsupportedMapping()
    {
        var column = new ElasticsearchColumnHandle(List.of("Scaled"), DOUBLE, new ScaledFloatType(100), new DoubleDecoder.Descriptor("Scaled"), false);
        var variable = new Variable("scaled", DOUBLE);
        var source = call(BOOLEAN, "$greater_than", variable, new Constant(1.25, DOUBLE));
        assertThat(ElasticsearchScalarPredicateTranslator.translate(
                TestingConnectorSession.builder().build(),
                source,
                Map.of("scaled", column),
                ElasticsearchPredicateCompositionPolicy.DEFAULT,
                true)).isEmpty();
    }

    private static ElasticsearchPredicatePushdownPlanner.Result plan(ConnectorExpression expression, boolean arrayEnabled, boolean scalarEnabled)
    {
        return ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.builder().build(),
                new Constraint(TupleDomain.all(), expression, ASSIGNMENTS),
                SAFE,
                ElasticsearchPredicateCompositionPolicy.DEFAULT,
                arrayEnabled,
                scalarEnabled);
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
