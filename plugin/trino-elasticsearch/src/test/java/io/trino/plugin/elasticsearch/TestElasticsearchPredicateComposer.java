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

import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchPredicateComposer
{
    private static final ConnectorExpression A = new Variable("a", BOOLEAN);
    private static final ConnectorExpression B = new Variable("b", BOOLEAN);

    @Test
    public void testAndCanUseExactBranchAsCandidateWhenAnotherBranchIsUnowned()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> exact = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                Reason.EXACT_DOMAIN);
        ElasticsearchPredicateTranslation<ConnectorExpression> unsupported = ElasticsearchPredicateTranslation.unsupported(
                B,
                Reason.UNSUPPORTED_EXPRESSION);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact, unsupported));

        assertThat(result.remotePredicate()).contains(new ElasticsearchRemotePredicate.Term("status", "active"));
        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).contains(B);
        assertThat(result.residual()).isEmpty();
    }

    @Test
    public void testAndKeepsOnlyConnectorOwnedResidual()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> exact = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                Reason.EXACT_DOMAIN);
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.MatchPhrase("message", "fatal"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.and(
                ConnectorExpressions.and(List.of(A, B)),
                List.of(exact, prefilter));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(B);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
    }

    @Test
    public void testPartialOrBecomesPlannerOwnedResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> exact = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                Reason.EXACT_DOMAIN);
        ElasticsearchPredicateTranslation<ConnectorExpression> unsupported = ElasticsearchPredicateTranslation.unsupported(
                B,
                Reason.UNSUPPORTED_EXPRESSION);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(exact, unsupported));

        assertThat(result.remotePredicate()).isEmpty();
        assertThat(result.enforcement()).isEmpty();
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
    }

    @Test
    public void testOrWithPrefilterKeepsWholeOrResidual()
    {
        ConnectorExpression source = ConnectorExpressions.or(List.of(A, B));
        ElasticsearchPredicateTranslation<ConnectorExpression> exact = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                Reason.EXACT_DOMAIN);
        ElasticsearchPredicateTranslation<ConnectorExpression> prefilter = ElasticsearchPredicateTranslation.prefilter(
                new ElasticsearchRemotePredicate.MatchPhrase("message", "fatal"),
                B,
                Reason.FULL_TEXT_SAFE_PREFILTER);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                source,
                List.of(exact, prefilter));

        assertThat(result.enforcement()).contains(PREFILTER);
        assertThat(result.remaining()).isEmpty();
        assertThat(result.residual()).contains(source);
        assertThat(result.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Or.class);
    }

    @Test
    public void testExactOrStaysExact()
    {
        ElasticsearchPredicateTranslation<ConnectorExpression> first = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "active"),
                Reason.EXACT_DOMAIN);
        ElasticsearchPredicateTranslation<ConnectorExpression> second = ElasticsearchPredicateTranslation.exact(
                new ElasticsearchRemotePredicate.Term("status", "pending"),
                Reason.EXACT_DOMAIN);

        ElasticsearchPredicateTranslation<ConnectorExpression> result = ElasticsearchPredicateComposer.or(
                ConnectorExpressions.or(List.of(A, B)),
                List.of(first, second));

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
}
