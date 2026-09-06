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

import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.SearchResult;

import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

final class ScrollSearchExecutionStrategy
        implements SearchExecutionStrategy
{
    private final ElasticsearchClient client;
    private final Function<Consumer<String>, SearchResult> search;
    private final ElasticsearchPushdownDiagnostics diagnostics;
    private String scrollId;
    private boolean nextPage;

    ScrollSearchExecutionStrategy(ElasticsearchClient client, Function<Consumer<String>, SearchResult> search, ElasticsearchPushdownDiagnostics diagnostics)
    {
        this.client = requireNonNull(client, "client is null");
        this.search = requireNonNull(search, "search is null");
        this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");
    }

    @Override
    public SearchResult open()
    {
        return retain(search.apply(context -> scrollId = context));
    }

    @Override
    public boolean hasNextPage()
    {
        return nextPage;
    }

    @Override
    public SearchResult nextPage()
    {
        return retain(client.nextPage(requireNonNull(scrollId, "scrollId is null"), context -> scrollId = context));
    }

    private SearchResult retain(SearchResult result)
    {
        // A response without a scroll cannot be paged, but the last owned context still needs cleanup.
        scrollId = result.scrollId().orElse(scrollId);
        nextPage = result.scrollId().isPresent();
        return result;
    }

    @Override
    public void close()
    {
        String context = scrollId;
        scrollId = null;
        if (context != null) {
            diagnostics.recordClearScroll();
            client.clearScroll(context);
        }
    }
}
