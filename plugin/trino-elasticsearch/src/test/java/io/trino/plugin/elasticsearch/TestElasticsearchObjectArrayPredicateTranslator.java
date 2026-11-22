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

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.trino.plugin.elasticsearch.client.IndexMetadata;
import io.trino.plugin.elasticsearch.decoders.ArrayDecoder;
import io.trino.plugin.elasticsearch.decoders.RowDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.Constraint;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FieldDereference;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FunctionType;
import io.trino.spi.type.RowType;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.expression.Constant.TRUE;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.RowType.field;
import static io.trino.spi.type.RowType.rowType;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchObjectArrayPredicateTranslator
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RowType ROW = rowType(field("Name", VARCHAR), field("City", VARCHAR));
    private static final ArrayType ARRAY = new ArrayType(ROW);
    private static final Variable ELEMENT = new Variable("u", ROW);
    private static final FieldDereference NAME = new FieldDereference(VARCHAR, ELEMENT, 0);
    private static final FieldDereference CITY = new FieldDereference(VARCHAR, ELEMENT, 1);
    private static final Call BINH = new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(NAME, new Constant(utf8Slice("Bình"), VARCHAR)));
    private static final Call HUE = new Call(BOOLEAN, EQUAL_OPERATOR_FUNCTION_NAME, List.of(CITY, new Constant(utf8Slice("Huế"), VARCHAR)));
    private static final Call CITY_NULL = new Call(BOOLEAN, IS_NULL_FUNCTION_NAME, List.of(CITY));
    private static final Call CITY_PRESENT = new Call(BOOLEAN, NOT_FUNCTION_NAME, List.of(CITY_NULL));

    @Test
    void testNestedAndKeepsBothConditionsInOneQuery()
            throws Exception
    {
        Call filter = anyMatch(new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(BINH, HUE)));
        var translation = translate(filter, true, false);
        assertThat(translation.enforcement()).contains(EXACT);
        assertThat(translation.residual()).isEmpty();
        assertThat(translation.remaining()).isEmpty();

        ElasticsearchRemotePredicate predicate = translation.remotePredicate().orElseThrow();
        var codec = jsonCodec(ElasticsearchRemotePredicate.class);
        assertThat(codec.fromJson(codec.toJson(predicate))).isEqualTo(predicate);
        assertThat(ElasticsearchRemotePredicateQueryBuilder.build(predicate)).isEqualTo(JSON.readTree(
                """
                {"nested":{"path":"Users","score_mode":"none","query":{"bool":{"filter":[
                    {"term":{"Users.Name":"Bình"}},
                    {"term":{"Users.City":"Huế"}}
                ]}}}}
                """));
    }

    @Test
    void testObjectAndIsOnlyPrefilterAndSimpleOrIsExact()
    {
        Call and = anyMatch(new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(BINH, HUE)));
        assertThat(translate(and, false, true).enforcement()).contains(PREFILTER);
        assertThat(translate(and, false, true).residual()).contains(and);
        assertThat(translate(and, false, true).remotePredicate().orElseThrow())
                .isInstanceOfSatisfying(ElasticsearchRemotePredicate.Enforced.class, enforced ->
                        assertThat(enforced.predicate()).isInstanceOf(ElasticsearchRemotePredicate.And.class));

        Call or = anyMatch(new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(BINH, HUE)));
        for (boolean nested : List.of(false, true)) {
            var translation = translate(or, nested, false);
            assertThat(translation.enforcement()).contains(EXACT);
            var query = ElasticsearchRemotePredicateQueryBuilder.build(translation.remotePredicate().orElseThrow());
            assertThat(query.at(nested ? "/nested/query/bool/minimum_should_match" : "/bool/minimum_should_match").asInt()).isEqualTo(1);
        }

        Call mixedOr = anyMatch(new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(BINH, HUE)), CITY_PRESENT)));
        assertThat(translate(mixedOr, false, false).residual()).contains(mixedOr);
        assertThat(translate(mixedOr, true, false).enforcement()).contains(EXACT);
    }

    @Test
    void testNullFilterNeedsAVisibleNestedElement()
    {
        Call guardedNull = anyMatch(new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(BINH, CITY_NULL)));
        var nested = translate(guardedNull, true, false);
        assertThat(nested.enforcement()).contains(EXACT);
        assertThat(ElasticsearchRemotePredicateQueryBuilder.build(nested.remotePredicate().orElseThrow())
                .at("/nested/query/bool/filter/1/bool/must_not/0/exists/field").asText()).isEqualTo("Users.City");
        var object = translate(guardedNull, false, false);
        assertThat(object.residual()).contains(guardedNull);
        assertThat(ElasticsearchRemotePredicateQueryBuilder.build(object.remotePredicate().orElseThrow())
                .at("/term/Users.Name").asText()).isEqualTo("Bình");

        for (Call filter : List.of(anyMatch(CITY_NULL), anyMatch(new Call(BOOLEAN, OR_FUNCTION_NAME, List.of(BINH, CITY_NULL))))) {
            for (boolean nestedMapping : List.of(false, true)) {
                assertThat(translate(filter, nestedMapping, false).remotePredicate()).isEmpty();
                assertThat(translate(filter, nestedMapping, false).residual()).contains(filter);
                var scripted = translate(filter, nestedMapping, true);
                assertThat(scripted.enforcement()).contains(PREFILTER);
                assertThat(scripted.residual()).contains(filter);
                assertThat(scripted.remotePredicate().orElseThrow()).isInstanceOfSatisfying(ElasticsearchRemotePredicate.Enforced.class, enforced ->
                        assertThat(enforced.predicate()).isInstanceOfSatisfying(ElasticsearchRemotePredicate.SourceScript.class, source -> {
                            assertThat(source.parameters()).containsValue(ElasticsearchRemotePredicate.Value.of("City"));
                            assertThat(source.source()).contains("== null").doesNotContain("City", "Bình");
                        }));
            }
        }
    }

    @Test
    void testIsNotNullUsesExistsWithinTheCorrectScope()
    {
        for (boolean nested : List.of(false, true)) {
            var translation = translate(anyMatch(CITY_PRESENT), nested, false);
            assertThat(translation.enforcement()).contains(EXACT);
            assertThat(ElasticsearchRemotePredicateQueryBuilder.build(translation.remotePredicate().orElseThrow())
                    .at(nested ? "/nested/query/exists/field" : "/exists/field").asText()).isEqualTo("Users.City");
        }
    }

    @Test
    void testSeparateAnyMatchCallsKeepSeparateNestedScopes()
    {
        Call filter = new Call(BOOLEAN, AND_FUNCTION_NAME, List.of(anyMatch(BINH), anyMatch(HUE)));
        var plan = ElasticsearchPredicatePushdownPlanner.plan(
                TestingConnectorSession.builder().build(),
                new Constraint(TupleDomain.all(), filter, Map.of("users", column(true))),
                SAFE);
        assertThat(plan.remainingConstraint().getExpression()).isEqualTo(TRUE);
        assertThat(plan.residualExpressions()).isEmpty();
        var query = ElasticsearchRemotePredicateQueryBuilder.build(plan.remotePredicate().orElseThrow());
        assertThat(query.at("/bool/filter/0/nested/query/term/Users.Name").asText()).isEqualTo("Bình");
        assertThat(query.at("/bool/filter/1/nested/query/term/Users.City").asText()).isEqualTo("Huế");
    }

    private static Call anyMatch(ConnectorExpression body)
    {
        Lambda lambda = new Lambda(new FunctionType(List.of(ROW), BOOLEAN), List.of(ELEMENT), body);
        return new Call(BOOLEAN, new FunctionName("any_match"), List.of(new Variable("users", ARRAY), lambda));
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translate(Call call, boolean nested, boolean scripts)
    {
        return ElasticsearchArrayPredicateTranslator.translate(
                        TestingConnectorSession.builder().build(), call, Map.of("users", column(nested)), SAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, scripts)
                .orElseThrow();
    }

    private static ElasticsearchColumnHandle column(boolean nested)
    {
        IndexMetadata.ObjectType mapping = new IndexMetadata.ObjectType(
                List.of(new IndexMetadata.Field(false, false, "Name", new IndexMetadata.PrimitiveType("keyword")),
                        new IndexMetadata.Field(false, false, "City", new IndexMetadata.PrimitiveType("keyword"))),
                Optional.of(new IndexMetadata.ObjectArrayMapping(nested, Set.of("Name", "City"))));
        return new ElasticsearchColumnHandle(
                List.of("Users"),
                ARRAY,
                mapping,
                new ArrayDecoder.Descriptor(new RowDecoder.Descriptor("Users", List.of())),
                false,
                false,
                !nested);
    }
}
