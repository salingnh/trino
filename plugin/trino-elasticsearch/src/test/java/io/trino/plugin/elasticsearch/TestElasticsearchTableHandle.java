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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.JsonMapperProvider;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.predicate.TupleDomain;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchTableHandle
{
    private static final JsonCodec<ElasticsearchTableHandle> TABLE_CODEC = new JsonCodecFactory(new JsonMapperProvider().get())
            .jsonCodec(ElasticsearchTableHandle.class);

    @Test
    public void testRemotePredicateJsonRoundTrip()
    {
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.And(ImmutableList.of(
                new ElasticsearchRemotePredicate.Term("status.keyword", "active"),
                new ElasticsearchRemotePredicate.Terms("id", ImmutableList.of(1L, 2L, 3L)),
                new ElasticsearchRemotePredicate.Range(
                        "score",
                        Optional.of(new ElasticsearchRemotePredicate.Bound(1.5, true)),
                        Optional.empty())));

        ElasticsearchTableHandle handle = new ElasticsearchTableHandle(
                SCAN,
                "default",
                "events",
                TupleDomain.all(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                Optional.empty(),
                OptionalLong.empty(),
                ImmutableList.of(),
                ImmutableSet.of(),
                Optional.empty(),
                Optional.of(predicate));

        String json = TABLE_CODEC.toJson(handle);
        ElasticsearchTableHandle copy = TABLE_CODEC.fromJson(json);

        assertThat(json).contains("\"@type\":\"and\"");
        assertThat(copy).isEqualTo(handle);
        assertThat(copy.remotePredicate()).contains(predicate);
    }

    @Test
    public void testCopyOperationsPreserveRemotePredicate()
    {
        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.Term("status.keyword", "active");
        ElasticsearchTableHandle handle = new ElasticsearchTableHandle(
                SCAN,
                "default",
                "events",
                TupleDomain.all(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                Optional.empty(),
                OptionalLong.empty(),
                ImmutableList.of(),
                ImmutableSet.of(),
                Optional.empty(),
                Optional.of(predicate));

        assertThat(handle.withColumns(ImmutableSet.of()).remotePredicate()).contains(predicate);
        assertThat(handle.withConstraint(TupleDomain.<ColumnHandle>all()).remotePredicate()).contains(predicate);
        assertThat(handle.withTopN(10, ImmutableList.of()).remotePredicate()).contains(predicate);
    }

    @Test
    public void testLegacyConstructorHasNoRemotePredicate()
    {
        ElasticsearchTableHandle handle = new ElasticsearchTableHandle(SCAN, "default", "events", Optional.empty());

        assertThat(handle.remotePredicate()).isEmpty();
    }
}
