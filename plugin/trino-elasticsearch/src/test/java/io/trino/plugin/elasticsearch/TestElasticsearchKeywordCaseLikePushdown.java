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

import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.TupleDomain;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.KEYWORD_CASE_UNSAFE_APPROXIMATE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.DISABLED;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.APPROXIMATE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchKeywordCaseLikePushdown
{
    private static final Variable GENDER = new Variable("gender", VARCHAR);
    private static final ElasticsearchColumnHandle COLUMN = new ElasticsearchColumnHandle(
            List.of("Person", "Gender"), VARCHAR, new PrimitiveType("keyword"), new VarcharDecoder.Descriptor("Person.Gender"), true);
    private static final Map<String, ColumnHandle> ASSIGNMENTS = Map.of("gender", COLUMN);

    @Test
    void testLowerAndUpperPreferNativeRegexpWithoutResidual()
    {
        for (String function : List.of("lower", "upper")) {
            for (String pattern : List.of("%1%", "%NaM%", "%SÁ%", "ab_%", "[A].%", "")) {
                for (boolean scriptEnabled : List.of(false, true)) {
                    Call source = like(function, pattern);
                    var result = plan(source, UNSAFE, scriptEnabled);
                    assertThat(result.residualExpressions()).isEmpty();
                    assertThat(result.remainingConstraint().getExpression()).isEqualTo(Constant.TRUE);
                    assertThat(result.remotePredicate().orElseThrow().enforcement()).isEqualTo(APPROXIMATE);
                    assertThat(result.remotePredicate().orElseThrow().isExact()).isFalse();
                    var predicate = (ElasticsearchRemotePredicate.Enforced) result.remotePredicate().orElseThrow();
                    assertThat(predicate.predicate()).isEqualTo(new ElasticsearchRemotePredicate.Regexp(
                            "Person.Gender", ElasticsearchMetadata.likeToRegexp(utf8Slice(pattern), Optional.empty()), true));
                    assertThat(result.decisions()).anyMatch(decision -> decision.reason() == KEYWORD_CASE_UNSAFE_APPROXIMATE);
                    assertThat(ElasticsearchRemotePredicateQueryBuilder.build(predicate).toString()).doesNotContain("script");
                }
            }
        }
    }

    @Test
    void testSafeAndDisabledRetainExistingScriptAndResidual()
    {
        for (String function : List.of("lower", "upper")) {
            for (FullTextPushdownMode mode : List.of(DISABLED, SAFE)) {
                Call source = like(function, "%1%");
                var scripted = plan(source, mode, true);
                assertThat(scripted.remotePredicate().orElseThrow().enforcement()).isEqualTo(PREFILTER);
                assertThat(scripted.residualExpressions()).containsExactly(source);
                var local = plan(source, mode, false);
                assertThat(local.remotePredicate()).isEmpty();
                assertThat(local.remainingConstraint().getExpression()).isEqualTo(source);
            }
        }
    }

    @Test
    void testEscapeAndRequestBudget()
    {
        var source = new Call(BOOLEAN, new FunctionName("$like"), List.of(
                new Call(VARCHAR, new FunctionName("upper"), List.of(GENDER)), text("%A!_B!%%"), text("!")));
        var translated = ElasticsearchFullTextPredicateTranslator.translateKeywordCaseLike(
                TestingConnectorSession.SESSION, source, ASSIGNMENTS, UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow();
        var predicate = (ElasticsearchRemotePredicate.Enforced) translated.remotePredicate().orElseThrow();
        assertThat(predicate.predicate()).isEqualTo(new ElasticsearchRemotePredicate.Regexp("Person.Gender", ".*A_B%.*", true));
        var constrained = ElasticsearchFullTextPredicateTranslator.translateKeywordCaseLike(
                TestingConnectorSession.SESSION, source, ASSIGNMENTS, UNSAFE, new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 10)).orElseThrow();
        assertThat(constrained.remotePredicate()).isEmpty();
        assertThat(constrained.residual()).contains(source);
    }

    @Test
    void testMappingAndExpressionEligibility()
    {
        var source = like("lower", "%1%");
        for (PrimitiveType mapping : List.of(
                new PrimitiveType("text", Optional.of("keyword"), true),
                new PrimitiveType("keyword", Optional.empty(), true, false),
                new PrimitiveType("keyword", Optional.empty(), true, true, false))) {
            var column = new ElasticsearchColumnHandle(COLUMN.path(), VARCHAR, mapping, COLUMN.decoderDescriptor(), true);
            assertThat(ElasticsearchFullTextPredicateTranslator.translateKeywordCaseLike(
                    TestingConnectorSession.SESSION, source, Map.of("gender", column), UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
        }
        var unsafe = new ElasticsearchColumnHandle(COLUMN.path(), VARCHAR, new PrimitiveType("keyword"), COLUMN.decoderDescriptor(), true, true, false);
        assertThat(ElasticsearchFullTextPredicateTranslator.translateKeywordCaseLike(
                TestingConnectorSession.SESSION, source, Map.of("gender", unsafe), UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
        var dynamicPattern = new Call(BOOLEAN, new FunctionName("$like"), List.of(source.getArguments().getFirst(), GENDER));
        assertThat(ElasticsearchFullTextPredicateTranslator.translateKeywordCaseLike(
                TestingConnectorSession.SESSION, dynamicPattern, ASSIGNMENTS, UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT)).isEmpty();
        var plain = new Call(BOOLEAN, new FunctionName("$like"), List.of(GENDER, text("%1%")));
        assertThat(plan(plain, UNSAFE, false).remotePredicate().orElseThrow())
                .isEqualTo(new ElasticsearchRemotePredicate.Regexp("Person.Gender", ".*1.*"));
    }

    @Test
    void testRegexpSerializationAndLegacyDefault()
    {
        var codec = new JsonCodecFactory(new JsonMapperProvider().get()).jsonCodec(ElasticsearchRemotePredicate.class);
        var regexp = new ElasticsearchRemotePredicate.Regexp("gender", ".*NaM.*", true);
        assertThat(codec.fromJson(codec.toJson(regexp))).isEqualTo(regexp);
        var approximate = new ElasticsearchRemotePredicate.Enforced(regexp, APPROXIMATE);
        assertThat(codec.fromJson(codec.toJson(approximate))).isEqualTo(approximate);
        assertThat(codec.fromJson("{\"@type\":\"regexp\",\"field\":\"gender\",\"value\":\".*1.*\"}"))
                .isEqualTo(new ElasticsearchRemotePredicate.Regexp("gender", ".*1.*", false));
        assertThat(ElasticsearchRemotePredicateNormalizer.or(List.of(regexp, new ElasticsearchRemotePredicate.Regexp("gender", ".*NaM.*"))).orElseThrow())
                .isInstanceOf(ElasticsearchRemotePredicate.Or.class);
    }

    @Test
    void testNativeBooleanComposition()
    {
        var plain = new Call(BOOLEAN, new FunctionName("$like"), List.of(GENDER, text("%1%")));
        for (String operator : List.of("$and", "$or")) {
            var expression = new Call(BOOLEAN, new FunctionName(operator), List.of(like("lower", "%NaM%"), plain));
            var result = plan(expression, UNSAFE, true);
            assertThat(result.residualExpressions()).isEmpty();
            assertThat(result.remainingConstraint().getExpression()).isEqualTo(Constant.TRUE);
            assertThat(result.remotePredicate().orElseThrow().isExact()).isFalse();
            assertThat(ElasticsearchRemotePredicateQueryBuilder.build(result.remotePredicate().orElseThrow()).toString())
                    .contains("case_insensitive").doesNotContain("script");
        }
    }

    private static ElasticsearchPredicatePushdownPlanner.Result plan(ConnectorExpression expression, FullTextPushdownMode mode, boolean scriptEnabled)
    {
        return ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.SESSION,
                new Constraint(TupleDomain.all(), expression, ASSIGNMENTS),
                mode,
                ElasticsearchPredicateCompositionPolicy.DEFAULT,
                false,
                scriptEnabled);
    }

    private static Call like(String function, String pattern)
    {
        return new Call(BOOLEAN, new FunctionName("$like"), List.of(new Call(VARCHAR, new FunctionName(function), List.of(GENDER)), text(pattern)));
    }

    private static Constant text(String value)
    {
        return new Constant(utf8Slice(value), VARCHAR);
    }
}
