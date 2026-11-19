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
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.FunctionType;
import io.trino.spi.type.Type;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.UNSAFE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.APPROXIMATE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestElasticsearchTextPredicateTranslator
{
    private static final Variable TEXT = new Variable("comment", VARCHAR);
    private static final ElasticsearchColumnHandle COLUMN = new ElasticsearchColumnHandle(List.of("ReviewComment"), VARCHAR, new PrimitiveType("text"), new VarcharDecoder.Descriptor("ReviewComment"), false);

    @Test
    void testNativeCaseConversionUsesAnalyzerWithoutScript()
    {
        for (String function : List.of("lower", "upper")) {
            Call source = call(BOOLEAN, "$like", call(VARCHAR, function, TEXT), text("%sách rất%"));
            var result = translate(source, false).orElseThrow();
            assertThat(result.enforcement()).contains(APPROXIMATE);
            assertThat(result.residual()).isEmpty();
            assertThat(ElasticsearchRemotePredicateQueryBuilder.build(result.remotePredicate().orElseThrow()).toString())
                    .contains("match_phrase", "ReviewComment", "sách rất").doesNotContain("script");
        }
    }

    @Test
    void testRuntimeCompilerSupportsCompositionAndNullInsteadOfTokens()
    {
        for (Call source : List.of(
                call(BOOLEAN, "$greater_than", call(BIGINT, "length", call(VARCHAR, "trim", TEXT)), new Constant(5L, BIGINT)),
                call(BOOLEAN, "$is_null", TEXT),
                call(BOOLEAN, "$equal", call(VARCHAR, "$coalesce", TEXT, text("fallback")), text("fallback")))) {
            var result = translate(source, true).orElseThrow();
            assertThat(result.enforcement()).contains(EXACT);
            var runtime = (ElasticsearchRemotePredicate.SourceScript) unwrap(result.remotePredicate().orElseThrow());
            assertThat(runtime.source()).contains("params._source", "sourceText");
            assertThat(runtime.array()).isFalse();
            assertThat(jsonCodec(ElasticsearchRemotePredicate.class).fromJson(jsonCodec(ElasticsearchRemotePredicate.class).toJson(runtime))).isEqualTo(runtime);
            var mappings = ElasticsearchRemotePredicateQueryBuilder.runtimeMappings(result.remotePredicate());
            assertThat(mappings.has(runtime.runtimeField())).isTrue();
            assertThat(mappings.toString()).contains("ReviewComment", "painless");
        }
    }

    @Test
    void testUnicodeCasePolicyAndFallibleResidual()
    {
        Call source = call(BOOLEAN, "ends_with", call(VARCHAR, "lower", call(VARCHAR, "trim", TEXT)), text("rất tốt"));
        var result = translate(source, true).orElseThrow();
        assertThat(result.enforcement()).contains(APPROXIMATE);
        assertThat(((ElasticsearchRemotePredicate.SourceScript) unwrap(result.remotePredicate().orElseThrow())).source()).contains("sourceCase(", "Locale.ROOT");
        var fallible = translate(call(BOOLEAN, "$like", call(VARCHAR, "replace", TEXT, text("a"), text("b")), text("%b%")), true).orElseThrow();
        assertThat(fallible.enforcement()).contains(PREFILTER);
        assertThat(fallible.residual()).isPresent();
    }

    @Test
    void testModesFlagsAndBudget()
    {
        Call source = call(BOOLEAN, "$greater_than", call(BIGINT, "length", TEXT), new Constant(2L, BIGINT));
        assertThat(translate(source, false)).isEmpty();
        for (FullTextPushdownMode mode : List.of(FullTextPushdownMode.DISABLED, FullTextPushdownMode.SAFE)) {
            assertThat(ElasticsearchTextPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.of("comment", COLUMN), mode, ElasticsearchPredicateCompositionPolicy.DEFAULT, true, true)).isEmpty();
        }
        var budget = ElasticsearchTextPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.of("comment", COLUMN), UNSAFE, new ElasticsearchPredicateCompositionPolicy(100, 10, 100, 100), true, true);
        assertThat(budget.orElseThrow().remotePredicate()).isEmpty();
    }

    @Test
    void testArraySourceCanEvaluateUnindexedNullElements()
    {
        ArrayType type = new ArrayType(VARCHAR);
        Variable array = new Variable("comments", type);
        Variable x = new Variable("x", VARCHAR);
        Call predicate = call(BOOLEAN, "$equal", call(VARCHAR, "$coalesce", x, text("fallback")), text("fallback"));
        Call source = call(BOOLEAN, "any_match", array, new Lambda(new FunctionType(List.of(VARCHAR), BOOLEAN), List.of(x), predicate));
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(COLUMN.path(), type, COLUMN.elasticsearchType(), COLUMN.decoderDescriptor(), false);
        var result = ElasticsearchTextPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.of("comments", column), UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, true, true).orElseThrow();
        assertThat(result.enforcement()).contains(EXACT);
        var runtime = (ElasticsearchRemotePredicate.SourceScript) unwrap(result.remotePredicate().orElseThrow());
        assertThat(runtime.array()).isTrue();
        assertThat(runtime.source()).contains("for (def item : values)", "sourceText(item)");
    }

    @Test
    void testAnalyzedArrayConjunctionIsApproximateOnlyInUnsafeMode()
    {
        ArrayType type = new ArrayType(VARCHAR);
        Variable array = new Variable("comments", type);
        Variable x = new Variable("x", VARCHAR);
        Call predicate = call(BOOLEAN, "$and", call(BOOLEAN, "$like", x, text("Nguyen%")), call(BOOLEAN, "$like", x, text("%Van%")));
        Call source = call(BOOLEAN, "any_match", array, new Lambda(new FunctionType(List.of(VARCHAR), BOOLEAN), List.of(x), predicate));
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(COLUMN.path(), type, COLUMN.elasticsearchType(), COLUMN.decoderDescriptor(), false);
        for (FullTextPushdownMode mode : FullTextPushdownMode.values()) {
            var translated = ElasticsearchTextPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.of("comments", column), mode, ElasticsearchPredicateCompositionPolicy.DEFAULT, false, false);
            if (mode != UNSAFE) {
                assertThat(translated).isEmpty();
                continue;
            }
            var result = translated.orElseThrow();
            assertThat(result.enforcement()).contains(APPROXIMATE);
            assertThat(result.residual()).isEmpty();
            assertThat(unwrap(result.remotePredicate().orElseThrow())).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        }
    }

    @Test
    void testTextRangeDomainUsesOriginalSource()
    {
        for (boolean nullable : List.of(false, true)) {
            Domain domain = Domain.create(ValueSet.ofRanges(Range.greaterThan(VARCHAR, utf8Slice("abc"))), nullable);
            var translated = ElasticsearchTextPredicateTranslator.translateDomain(TestingConnectorSession.builder().build(), COLUMN, domain, ElasticsearchPredicateCompositionPolicy.DEFAULT).orElseThrow();
            assertThat(translated.enforcement()).contains(EXACT);
            assertThat(unwrap(translated.remotePredicate().orElseThrow())).isInstanceOf(ElasticsearchRemotePredicate.SourceScript.class);
        }
    }

    private static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(Call source, boolean scripts)
    {
        return ElasticsearchTextPredicateTranslator.translate(TestingConnectorSession.builder().build(), source, Map.<String, ColumnHandle>of("comment", COLUMN), UNSAFE, ElasticsearchPredicateCompositionPolicy.DEFAULT, scripts, scripts);
    }

    private static ElasticsearchRemotePredicate unwrap(ElasticsearchRemotePredicate predicate)
    {
        return predicate instanceof ElasticsearchRemotePredicate.Enforced enforced ? enforced.predicate() : predicate;
    }

    private static Constant text(String value)
    {
        return new Constant(utf8Slice(value), VARCHAR);
    }

    private static Call call(Type type, String name, ConnectorExpression... arguments)
    {
        return new Call(type, new FunctionName(name), List.of(arguments));
    }
}
