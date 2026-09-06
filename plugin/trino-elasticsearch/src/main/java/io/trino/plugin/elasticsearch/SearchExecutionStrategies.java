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

import java.util.List;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSearchQuery;
import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSort;

final class SearchExecutionStrategies
{
    private SearchExecutionStrategies() {}

    static SearchExecutionStrategy create(
            ElasticsearchClient client,
            ElasticsearchTableHandle table,
            ElasticsearchSplit split,
            Optional<List<String>> fields,
            List<String> documentFields,
            ElasticsearchPushdownDiagnostics diagnostics)
    {
        JsonNode query = buildSearchQuery(table, diagnostics);
        List<JsonNode> sort = buildSort(table.sortOrder(), table.query().isPresent());
        if (client.isPointInTimeSearchEnabled()) {
            return new PointInTimeSearchExecutionStrategy(client, split.index(), split.shard(), query, fields, documentFields, sort, table.limit(), diagnostics);
        }
        return new ScrollSearchExecutionStrategy(
                client,
                context -> client.beginSearch(split.index(), split.shard(), query, fields, documentFields, sort, table.limit(), context),
                diagnostics);
    }
}
