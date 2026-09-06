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

import io.trino.plugin.base.expression.ConnectorExpressions;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.AND_FLATTENED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.BOOLEAN_COLLAPSED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.DUPLICATE_REMOVED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.OR_FLATTENED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.TERMS_BATCHED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Normalization.TERMS_COMPACTED;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateSemantics.effectiveEnforcement;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.APPROXIMATE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchPredicateComposer
{
    private static final ConnectorExpression A = new Variable("a", BOOLEAN);
    private static final ConnectorExpression B = new Variable("b", BOOLEAN);

    @Test
    public void testNormalizationEventsPreserveSemanticDecisionTree()
    {
        ElasticsearchRemotePredicate.Term first = new ElasticsearchRemotePredicate.Term("status", "active");
        ElasticsearchRemotePredicate.Term second = new ElasticsearchRemotePredicate.Term("status", "pending");
        ElasticsearchPredicateTranslation<ConnectorExpression> conjunction = ElasticsearchPredicateComposer.and(
                A,
                List.of(
                        ElasticsearchPredicateTranslation.exact(new ElasticsearchRemotePredicate.And(List.of(first, second)), Reason.EXACT_DOMAIN),
                        ElasticsearchPredicateTranslation.exact(first, Reason.EXACT_DOMAIN)));
        assertThat(conjunction.decision().normalizations()).containsExactly(AND_FLATTENED, DUPLICATE_REMOVED);
        assertThat(conjunction.remotePredicate()).contains(new ElasticsearchRemotePredicate.And(List.of(first, second)));
        assertThat(conjunction.decision().children()).hasSize(2);

        ElasticsearchPredicateTranslation<ConnectorExpression> disjunction = ElasticsearchPredicateComposer.or(
                A,
                List.of(
                        ElasticsearchPredicateTranslation.exact(new ElasticsearchRemotePredicate.Or(List.of(first, second)), Reason.EXACT_DOMAIN),
                        exact("status", "closed")),
                new ElasticsearchPredicateCompositionPolicy(10, 2, 10));
        assertThat(disjunction.decision().normalizations()).containsExactly(OR_FLATTENED, TERMS_BATCHED, TERMS_COMPACTED);
        assertThat(disjunction.remotePredicate()).contains(new ElasticsearchRemotePredicate.Or(List.of(
                new ElasticsearchRemotePredicate.Terms("status", List.of("active", "pending")),
                new ElasticsearchRemotePredicate.Term("status", "closed"))));

        ElasticsearchPredicateTranslation<ConnectorExpression> singleton = ElasticsearchPredicateComposer.and(A, List.of(exact("status", "active")));
        assertThat(singleton.decision().normalizations()).containsExactly(BOOLEAN_COLLAPSED);
        assertThat(singleton.remotePredicate()).contains(first);

        ElasticsearchPushdownDiagnostics diagnostics = new ElasticsearchPushdownDiagnostics();
        diagnostics.recordTranslation(conjunction.decision());
        diagnostics.recordTranslation(disjunction.decision());
        diagnostics.recordTranslation(singleton.decision());
        ElasticsearchPushdownDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertThat(snapshot.normalizationCounts().values()).containsOnly(1L);
        diagnostics.recordTranslation(conjunction.decision());
        assertThat(diagnostics.getNormalizationCounts()).containsEntry("AND_FLATTENED", 2L).containsEntry("DUPLICATE_REMOVED", 2L);
        assertThat(snapshot.normalizationCounts().values()).containsOnly(1L);
        assertThat(conjunction.decision().normalizations()).containsExactly(AND_FLATTENED, DUPLICATE_REMOVED);
    }

    @Test
    public void testExactAndExactStaysExact()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact("status", "active"), exact("tenant", "blue")));

        assertThat(result.enforcement()).contains(EXACT);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).isEmpty();
        assertThat(result.remotePredicate()).contains(new ElasticsearchRemotePredicate.And(List.of(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                new ElasticsearchRemotePredicate.Term("tenant", "blue"))));
    }

    @Test
    public void testAndCanUseExactBranchAsCandidateWhenAnotherBranchIsUnowned()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> exact = exact("status", "active");
        ElasticsearchPredicateTranslation<ConnectorExpression> unsupported = ElasticsearchPredicateTranslation.unsupported(
                B,
                Reason.UNSUPPORTED_EXPRESSION);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact, unsupported));

        assertThat(result.remotePredicate()).contains(new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                PREFILTER));
        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).contains(B);
        assertThat(result.residual()).isEmpty();
        assertThat(result.decision().children()).hasSize(2);
    }

    @Test
    public void testExactAndPrefilterIsPrefilter()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.Regexp("message", ".*(fatal).*"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact("status", "active"), prefilter));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(B);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        assertEffectiveEnforcement(result, PREFILTER);
    }

    @Test
    public void testPrefilterAndPrefilterKeepsBothResiduals()
    {
        ConnectorExpression source = ConnectorExpressions.and(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                source,
                List.of(prefilter("message", ".*(fatal).*", A), prefilter("detail", ".*(error).*", B)));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        assertEffectiveEnforcement(result, PREFILTER);
    }

    @Test
    public void testExactAndApproximateIsApproximate()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> approximate = ElasticsearchPredicateTranslation.approximate(
                new ElasticsearchRemotePredicate.MatchPhrase("message", "fatal"),
                Reason.FULL_TEXT_UNSAFE_APPROXIMATE);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact("status", "active"), approximate));

        assertThat(result.enforcement()).contains(APPROXIMATE);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).isEmpty();
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        assertEffectiveEnforcement(result, APPROXIMATE);
    }

    @Test
    public void testAndKeepsOnlyConnectorOwnedResidual()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.Regexp("message", ".*(fatal).*"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact("status", "active"), prefilter));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(B);
    }

    @Test
    public void testPartialOrBecomesPlannerOwnedResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> unsupported = ElasticsearchPredicateTranslation.unsupported(
                B,
                Reason.UNSUPPORTED_EXPRESSION);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(exact("status", "active"), unsupported));

        assertThat(result.remotePredicate()).isEmpty();
        assertThat(result.enforcement()).isEmpty();
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.decision().children()).hasSize(2);
    }

    @Test
    public void testExactOrPrefilterKeepsWholeOrResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.Regexp("message", ".*(fatal).*"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(exact("status", "active"), prefilter));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        assertEffectiveEnforcement(result, PREFILTER);
    }

    @Test
    public void testPrefilterOrPrefilterKeepsWholeOrResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(prefilter("message", ".*(fatal).*", A), prefilter("detail", ".*(error).*", B)));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        assertEffectiveEnforcement(result, PREFILTER);
    }

    @Test
    public void testExactOrApproximateIsApproximate()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> approximate = ElasticsearchPredicateTranslation.approximate(
                new ElasticsearchRemotePredicate.MatchPhrase("message", "fatal"),
                Reason.FULL_TEXT_UNSAFE_APPROXIMATE);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(exact("status", "active"), approximate));

        assertThat(result.enforcement()).contains(APPROXIMATE);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).isEmpty();
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        assertEffectiveEnforcement(result, APPROXIMATE);
    }

    @Test
    public void testApproximateOrPrefilterRemainsApproximateAndKeepsWholeResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> approximate = ElasticsearchPredicateTranslation.approximate(
                new ElasticsearchRemotePredicate.MatchPhrase("message", "fatal"),
                Reason.FULL_TEXT_UNSAFE_APPROXIMATE);
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.Regexp("status", ".*(active).*"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(approximate, prefilter));

        assertThat(result.enforcement()).contains(APPROXIMATE);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
        assertEffectiveEnforcement(result, APPROXIMATE);
    }

    @Test
    public void testExactOrStaysExactAndCompactsSameFieldTerms()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                ConnectorExpressions.or(List.of(A, B)),
                List.of(exact("status", "active"), exact("status", "pending")));

        assertThat(result.enforcement()).contains(EXACT);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).isEmpty();
        assertThat(result.remotePredicate()).contains(new ElasticsearchRemotePredicate.Terms("status", List.of("active", "pending")));
    }

    @Test
    public void testNotBecomesPlannerOwnedResidualUntilSemanticsAreProven()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.not(A);

        assertThat(result.remotePredicate()).isEmpty();
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(A);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> exact(String field, Object value)
    {
        return ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term(field, value),
                Reason.EXACT_DOMAIN);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> prefilter(
            String field,
            String value,
            ConnectorExpression residual)
    {
        return ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.Regexp(field, value),
                residual,
                Reason.FULL_TEXT_SAFE_PREFILTER);
    }

    private static void assertEffectiveEnforcement(
            ElasticsearchPredicateTranslation<ConnectorExpression> translation,
            ElasticsearchRemotePredicate.Enforcement expected)
    {
        assertThat(effectiveEnforcement(translation.remotePredicate().orElseThrow())).isEqualTo(expected);
        assertThat(translation.enforcement()).contains(expected);
    }
}
