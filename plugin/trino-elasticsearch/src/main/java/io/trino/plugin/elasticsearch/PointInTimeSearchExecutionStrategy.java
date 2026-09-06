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

import com.fasterxml.jackson.databind.JsonNode;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.SearchResult;
import io.trino.spi.TrinoException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static io.trino.plugin.elasticsearch.ElasticsearchErrorCode.ELASTICSEARCH_INVALID_RESPONSE;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.POINT_IN_TIME_CLOSE;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.POINT_IN_TIME_OPEN;
import static java.util.Objects.requireNonNull;

final class PointInTimeSearchExecutionStrategy
        implements SearchExecutionStrategy
{
    private final ElasticsearchClient client;
    private final String index;
    private final int shard;
    private final JsonNode query;
    private final Optional<List<String>> fields;
    private final List<String> documentFields;
    private final List<JsonNode> sort;
    private final OptionalLong limit;
    private final ElasticsearchPushdownDiagnostics diagnostics;
    private String id;
    private List<JsonNode> searchAfter = List.of();
    private boolean more = true;

    PointInTimeSearchExecutionStrategy(
            ElasticsearchClient client,
            String index,
            int shard,
            JsonNode query,
            Optional<List<String>> fields,
            List<String> documentFields,
            List<JsonNode> sort,
            OptionalLong limit,
            ElasticsearchPushdownDiagnostics diagnostics)
    {
        this.client = requireNonNull(client, "client is null");
        this.index = requireNonNull(index, "index is null");
        this.shard = shard;
        this.query = requireNonNull(query, "query is null");
        this.fields = requireNonNull(fields, "fields is null");
        this.documentFields = List.copyOf(documentFields);
        this.sort = List.copyOf(sort);
        this.limit = requireNonNull(limit, "limit is null");
        this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");
    }

    @Override
    public SearchResult open()
    {
        diagnostics.recordRemoteRequest(POINT_IN_TIME_OPEN);
        id = client.openPointInTime(index, shard);
        return nextPage();
    }

    @Override
    public boolean hasNextPage()
    {
        return more;
    }

    @Override
    public SearchResult nextPage()
    {
        SearchResult result = client.searchPointInTime(requireNonNull(id, "id is null"), query, fields, documentFields, sort, limit, searchAfter, context -> id = context);
        id = result.pointInTimeId().orElse(id);
        if (!result.hits().isEmpty() && (result.searchAfter().isEmpty() || result.searchAfter().equals(searchAfter))) {
            throw new TrinoException(ELASTICSEARCH_INVALID_RESPONSE, "PIT response has a missing or non-progressing search_after token");
        }
        searchAfter = result.searchAfter();
        more = !result.hits().isEmpty();
        return result;
    }

    @Override
    public void close()
    {
        String context = id;
        id = null;
        more = false;
        if (context != null) {
            diagnostics.recordRemoteRequest(POINT_IN_TIME_CLOSE);
            client.closePointInTime(context);
        }
    }
}
