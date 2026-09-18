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
import io.trino.metadata.TestingFunctionResolution;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.IndexMetadata;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import io.trino.testing.TestingConnectorSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchTableHandle.Type.SCAN;
import static io.trino.spi.expression.Constant.TRUE;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchKeywordMetadata
{
    @Test
    public void testResidualPropertiesAndLimitBoundaries()
            throws IOException
    {
        ElasticsearchConfig config = new ElasticsearchConfig()
                .setHosts(ImmutableList.of("127.0.0.1"))
                .setHttpThreadCount(1);
        // Do not initialize node discovery. These metadata operations must not perform network requests.
        ElasticsearchClient client = new ElasticsearchClient(config, Optional.empty(), Optional.empty());
        try {
            ElasticsearchMetadata metadata = new ElasticsearchMetadata(
                    new TestingFunctionResolution().getPlannerContext().getTypeManager(), client, config);
            TestingConnectorSession session = TestingConnectorSession.builder().build();
            ElasticsearchColumnHandle name = new ElasticsearchColumnHandle(
                    ImmutableList.of("name"), VARCHAR,
                    new IndexMetadata.PrimitiveType("text", Optional.of("raw")), new VarcharDecoder.Descriptor("name"), false);
            ElasticsearchColumnHandle status = new ElasticsearchColumnHandle(
                    ImmutableList.of("status"), VARCHAR,
                    new IndexMetadata.PrimitiveType("keyword"), new VarcharDecoder.Descriptor("status"), true);
            Domain nameDomain = Domain.singleValue(VARCHAR, utf8Slice("Alice"));
            Domain statusDomain = Domain.singleValue(VARCHAR, utf8Slice("active"));
            Constraint constraint = new Constraint(
                    TupleDomain.withColumnDomains(ImmutableMap.<ColumnHandle, Domain>of(name, nameDomain, status, statusDomain)),
                    TRUE,
                    ImmutableMap.of());
            ElasticsearchTableHandle table = new ElasticsearchTableHandle(SCAN, "default", "test", Optional.empty());

            var result = metadata.applyFilter(session, table, constraint).orElseThrow();
            ElasticsearchTableHandle filtered = (ElasticsearchTableHandle) result.getHandle();
            assertThat(filtered.constraint()).isEqualTo(constraint.getSummary());
            assertThat(result.getRemainingFilter()).isEqualTo(TupleDomain.withColumnDomains(ImmutableMap.of(name, nameDomain)));
            assertThat(result.getRemainingExpression()).contains(TRUE);
            assertThat(metadata.getTableProperties(session, filtered).getPredicate())
                    .isEqualTo(TupleDomain.withColumnDomains(ImmutableMap.of(status, statusDomain)));
            assertThat(metadata.applyFilter(session, filtered, constraint)).isEmpty();
            assertThat(metadata.applyLimit(session, filtered, 1)).isEmpty();

            ElasticsearchTableHandle limited = (ElasticsearchTableHandle) metadata.applyLimit(session, table, 1).orElseThrow().getHandle();
            Constraint keywordConstraint = new Constraint(
                    TupleDomain.withColumnDomains(ImmutableMap.<ColumnHandle, Domain>of(name, nameDomain)), TRUE, ImmutableMap.of());
            assertThat(metadata.applyFilter(session, limited, keywordConstraint)).isEmpty();
        }
        finally {
            client.close();
        }
    }
}
