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

import com.google.common.collect.ImmutableList;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateQueryBuilder.build;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestElasticsearchRemotePredicateQueryBuilder
{
    @Test
    public void testArrayScriptParameters()
    {
        ElasticsearchRemotePredicate.ArrayScript script = new ElasticsearchRemotePredicate.ArrayScript(
                "Numbers",
                "return doc[params.field].size() > params.minimum;",
                Map.of(
                        "field", ElasticsearchRemotePredicate.Value.of("Numbers"),
                        "minimum", ElasticsearchRemotePredicate.Value.of(0L)));
        assertThat(build(script).at("/script/script/lang").asText()).isEqualTo("painless");
        assertThat(build(script).at("/script/script/source").asText()).isEqualTo(script.source());
        assertThat(build(script).at("/script/script/params/field").asText()).isEqualTo("Numbers");
        assertThat(build(script).at("/script/script/params/minimum").isIntegralNumber()).isTrue();
        assertThatThrownBy(() -> new ElasticsearchRemotePredicate.ArrayScript("Other", script.source(), script.parameters()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field parameter");
    }

    @Test
    public void testTermAndTerms()
    {
        assertThat(build(new ElasticsearchRemotePredicate.Term("status", "active")).toString())
                .isEqualTo("{\"term\":{\"status\":\"active\"}}");
        assertThat(build(new ElasticsearchRemotePredicate.Terms("id", ImmutableList.of(1L, 2L, 3L))).toString())
                .isEqualTo("{\"terms\":{\"id\":[1,2,3]}}");
    }

    @Test
    public void testCanonicalValueTypes()
    {
        assertThat(build(new ElasticsearchRemotePredicate.Term("enabled", true)).toString())
                .isEqualTo("{\"term\":{\"enabled\":true}}");
        assertThat(build(new ElasticsearchRemotePredicate.Term("count", 7)).toString())
                .isEqualTo("{\"term\":{\"count\":7}}");
        assertThat(build(new ElasticsearchRemotePredicate.Term("score", 1.5f)).toString())
                .isEqualTo("{\"term\":{\"score\":1.5}}");
        assertThat(new ElasticsearchRemotePredicate.Term("count", 7).value().type())
                .isEqualTo(ElasticsearchRemotePredicate.ValueType.LONG);
        assertThat(new ElasticsearchRemotePredicate.Term("score", 1.5f).value().type())
                .isEqualTo(ElasticsearchRemotePredicate.ValueType.DOUBLE);
    }

    @Test
    public void testRange()
    {
        assertThat(build(new ElasticsearchRemotePredicate.MatchNone()).toString()).isEqualTo("{\"match_none\":{}}");
        ElasticsearchRemotePredicate.Range range = new ElasticsearchRemotePredicate.Range(
                "age",
                Optional.of(new ElasticsearchRemotePredicate.Bound(18L, true)),
                Optional.of(new ElasticsearchRemotePredicate.Bound(65L, false)));

        assertThat(build(range).toString())
                .isEqualTo("{\"range\":{\"age\":{\"gte\":18,\"lt\":65}}}");
    }

    @Test
    public void testTextPredicates()
    {
        assertThat(build(new ElasticsearchRemotePredicate.Prefix("name.keyword", "tri")).toString())
                .isEqualTo("{\"prefix\":{\"name.keyword\":\"tri\"}}");
        assertThat(build(new ElasticsearchRemotePredicate.Regexp("name.keyword", "tr.*")).toString())
                .isEqualTo("{\"regexp\":{\"name.keyword\":\"tr.*\"}}");
        assertThat(build(new ElasticsearchRemotePredicate.MatchPhrase("name", "apache trino")).toString())
                .isEqualTo("{\"match_phrase\":{\"name\":\"apache trino\"}}");
        assertThat(build(new ElasticsearchRemotePredicate.MatchPhrasePrefix("name", "apache tri")).toString())
                .isEqualTo("{\"match_phrase_prefix\":{\"name\":\"apache tri\"}}");
    }

    @Test
    public void testEnforcementMetadataDoesNotChangeDsl()
    {
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.MatchPhrase("name", "apache trino"),
                PREFILTER);

        assertThat(predicate.enforcement()).isEqualTo(PREFILTER);
        assertThat(build(predicate).toString())
                .isEqualTo("{\"match_phrase\":{\"name\":\"apache trino\"}}");
    }

    @Test
    public void testExistsAndBooleanComposition()
    {
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.And(ImmutableList.of(
                new ElasticsearchRemotePredicate.Exists("tags"),
                new ElasticsearchRemotePredicate.Or(ImmutableList.of(
                        new ElasticsearchRemotePredicate.Term("tags.keyword", "trino"),
                        new ElasticsearchRemotePredicate.Term("tags.keyword", "elasticsearch"))),
                new ElasticsearchRemotePredicate.Not(new ElasticsearchRemotePredicate.Term("status", "deleted"))));

        assertThat(build(predicate).toString())
                .isEqualTo("{\"bool\":{\"filter\":[{\"exists\":{\"field\":\"tags\"}},{\"bool\":{\"should\":[{\"term\":{\"tags.keyword\":\"trino\"}},{\"term\":{\"tags.keyword\":\"elasticsearch\"}}],\"minimum_should_match\":1}},{\"bool\":{\"must_not\":[{\"term\":{\"status\":\"deleted\"}}]}}]}}");
    }

    @Test
    public void testPredicateValidation()
    {
        assertThatThrownBy(() -> new ElasticsearchRemotePredicate.And(ImmutableList.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AND predicates is empty");
        assertThatThrownBy(() -> new ElasticsearchRemotePredicate.Terms("id", ImmutableList.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("values is empty");
        assertThatThrownBy(() -> new ElasticsearchRemotePredicate.Range("age", Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("range has no bounds");
        assertThatThrownBy(() -> ElasticsearchRemotePredicate.Value.of(new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported remote predicate value type");
        assertThatThrownBy(() -> new ElasticsearchRemotePredicate.Enforced(
                new ElasticsearchRemotePredicate.Term("id", 1L),
                EXACT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXACT predicates do not require an enforcement wrapper");
    }
}
