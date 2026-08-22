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
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.canonicalize;
import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.combine;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchRemotePredicateTranslator
{
    private static final ElasticsearchColumnHandle USER_ID = new ElasticsearchColumnHandle(
            List.of("UserID"),
            INTEGER,
            new PrimitiveType("integer"),
            new IntegerDecoder.Descriptor("UserID"),
            true);

    @Test
    public void testMultiplePredicatesOnSameFieldArePreserved()
    {
        ElasticsearchRemotePredicate first = new ElasticsearchRemotePredicate.Term("UserID", 10L);
        ElasticsearchRemotePredicate second = new ElasticsearchRemotePredicate.Range(
                "UserID",
                Optional.of(new ElasticsearchRemotePredicate.Bound(1L, true)),
                Optional.of(new ElasticsearchRemotePredicate.Bound(100L, true)));

        ElasticsearchRemotePredicate combined = combine(Optional.of(first), Optional.of(second)).orElseThrow();

        assertThat(combined).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        assertThat(((ElasticsearchRemotePredicate.And) combined).predicates()).containsExactly(first, second);
    }

    @Test
    public void testCanonicalizeMovesLegacyStateIntoIr()
    {
        ElasticsearchTableHandle legacy = new ElasticsearchTableHandle(
                SCAN,
                "default",
                "events",
                TupleDomain.withColumnDomains(Map.<ColumnHandle, Domain>of(USER_ID, Domain.multipleValues(INTEGER, List.of(1L, 2L, 3L))),
                Map.of("UserID", "[0-9]+"),
                Map.of("UserID", "1"),
                Map.of(),
                Optional.empty(),
                OptionalLong.empty(),
                List.of(),
                Set.of(),
                Optional.empty());

        ElasticsearchTableHandle canonical = canonicalize(legacy, Optional.empty());

        assertThat(canonical.constraint().isAll()).isTrue();
        assertThat(canonical.regexes()).isEmpty();
        assertThat(canonical.prefixes()).isEmpty();
        assertThat(canonical.matchPhrasePrefixes()).isEmpty();
        assertThat(canonical.remotePredicate()).isPresent();
        ElasticsearchRemotePredicate predicate = canonical.remotePredicate().orElseThrow();
        assertThat(predicate).isInstanceOf(ElasticsearchRemotePredicate.And.class);
        assertThat(((ElasticsearchRemotePredicate.And) predicate).predicates())
                .contains(
                        new ElasticsearchRemotePredicate.Terms("UserID", List.of(1L, 2L, 3L)),
                        new ElasticsearchRemotePredicate.Regexp("UserID", "[0-9]+"),
                        new ElasticsearchRemotePredicate.Prefix("UserID", "1"));
    }

    @Test
    public void testDiscreteDomainKeepsRemoteFieldCase()
    {
        ElasticsearchRemotePredicate predicate = ElasticsearchRemotePredicateTranslator.translateDomain(
                        USER_ID,
                        Domain.multipleValues(INTEGER, List.of(1L, 2L)))
                .orElseThrow();

        assertThat(predicate).isEqualTo(new ElasticsearchRemotePredicate.Terms("UserID", List.of(1L, 2L)));
    }

    @Test
    public void testEmptyDomainMatchesNoDocuments()
    {
        ElasticsearchRemotePredicate predicate = ElasticsearchRemotePredicateTranslator.translateDomain(USER_ID, Domain.none(INTEGER))
                .orElseThrow();

        assertThat(predicate).isEqualTo(new ElasticsearchRemotePredicate.And(List.of(
                new ElasticsearchRemotePredicate.Exists("UserID"),
                new ElasticsearchRemotePredicate.Not(new ElasticsearchRemotePredicate.Exists("UserID")))));
    }
}
