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

import com.google.common.collect.AbstractIterator;
import io.airlift.log.Logger;
import io.trino.plugin.elasticsearch.client.SearchDocument;
import io.trino.plugin.elasticsearch.client.SearchResult;

import java.util.List;
import java.util.OptionalLong;

import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.NEXT_PAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.SEARCH;
import static java.util.Objects.requireNonNull;

final class SearchExecution
        extends AbstractIterator<SearchDocument>
        implements AutoCloseable
{
    private static final Logger LOG = Logger.get(SearchExecution.class);

    private final SearchExecutionStrategy strategy;
    private final OptionalLong limit;
    private final ElasticsearchPushdownDiagnostics diagnostics;
    private List<SearchDocument> hits = List.of();
    private int position;
    private long records;
    private long readTimeNanos;
    private boolean closed;
    private boolean failed;
    private boolean exhausted;

    SearchExecution(SearchExecutionStrategy strategy, OptionalLong limit, ElasticsearchPushdownDiagnostics diagnostics)
    {
        this.strategy = requireNonNull(strategy, "strategy is null");
        this.limit = requireNonNull(limit, "limit is null");
        this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");
        if (limitReached()) {
            exhausted = true;
            close();
            return;
        }
        fetch(true);
    }

    public boolean isClosed()
    {
        return closed;
    }

    public long getReadTimeNanos()
    {
        return readTimeNanos;
    }

    @Override
    protected SearchDocument computeNext()
    {
        if (closed || limitReached()) {
            finish();
            return endOfData();
        }
        if (position == hits.size() && strategy.hasNextPage()) {
            fetch(false);
        }
        if (closed || position == hits.size()) {
            finish();
            return endOfData();
        }
        SearchDocument document = hits.get(position++);
        records++;
        return document;
    }

    private void fetch(boolean first)
    {
        long start = System.nanoTime();
        diagnostics.recordRemoteRequest(first ? SEARCH : NEXT_PAGE);
        try {
            SearchResult result = first ? strategy.open() : strategy.nextPage();
            diagnostics.recordRemotePageReceived();
            hits = result.hits();
            position = 0;
            if (hits.isEmpty()) {
                finish();
            }
        }
        catch (RuntimeException e) {
            fail();
            throw e;
        }
        finally {
            readTimeNanos += System.nanoTime() - start;
        }
    }

    private boolean limitReached()
    {
        return limit.isPresent() && records >= limit.orElseThrow();
    }

    public void documentDecoded()
    {
        if (limitReached() || (position == hits.size() && !strategy.hasNextPage())) {
            finish();
        }
    }

    private void finish()
    {
        exhausted = true;
        close();
    }

    public void fail()
    {
        if (!failed) {
            failed = true;
            diagnostics.recordFailure();
        }
        close();
    }

    @Override
    public void close()
    {
        if (closed) {
            return;
        }
        closed = true;
        if (!failed && !exhausted) {
            diagnostics.recordCancellation();
        }
        try {
            strategy.close();
        }
        catch (RuntimeException e) {
            diagnostics.recordFailure();
            LOG.debug(e, "Error closing search context");
        }
    }
}
