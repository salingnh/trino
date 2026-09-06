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
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.trino.plugin.elasticsearch.client.ElasticsearchClient;
import io.trino.plugin.elasticsearch.client.SearchDocument;
import io.trino.plugin.elasticsearch.client.SearchResult;
import io.trino.plugin.elasticsearch.decoders.Decoder;
import io.trino.spi.Page;
import io.trino.spi.block.Block;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.block.PageBuilderStatus;
import io.trino.spi.connector.ConnectorPageSource;
import io.trino.spi.connector.SourcePage;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.trino.plugin.elasticsearch.BuiltinColumns.SOURCE;
import static io.trino.plugin.elasticsearch.BuiltinColumns.isBuiltinColumn;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.NEXT_PAGE;
import static io.trino.plugin.elasticsearch.ElasticsearchPushdownDiagnostics.RemoteRequestKind.SEARCH;
import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSearchQuery;
import static io.trino.plugin.elasticsearch.ElasticsearchQueryBuilder.buildSort;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.isEqual;
import static java.util.stream.Collectors.toList;

public class ScanQueryPageSource
        implements ConnectorPageSource
{
    private static final Logger LOG = Logger.get(ScanQueryPageSource.class);

    private final List<Decoder> decoders;

    private final SearchDocumentIterator iterator;
    private final BlockBuilder[] columnBuilders;
    private final List<ElasticsearchColumnHandle> columns;
    private final ElasticsearchPushdownDiagnostics diagnostics;
    private long totalBytes;
    private long readTimeNanos;

    public ScanQueryPageSource(
            ElasticsearchClient client,
            TypeManager typeManager,
            ElasticsearchTableHandle table,
            ElasticsearchSplit split,
            List<ElasticsearchColumnHandle> columns)
    {
        this(client, typeManager, table, split, columns, new ElasticsearchPushdownDiagnostics());
    }

    ScanQueryPageSource(
            ElasticsearchClient client,
            TypeManager typeManager,
            ElasticsearchTableHandle table,
            ElasticsearchSplit split,
            List<ElasticsearchColumnHandle> columns,
            ElasticsearchPushdownDiagnostics diagnostics)
    {
        requireNonNull(client, "client is null");
        requireNonNull(typeManager, "typeManager is null");
        requireNonNull(columns, "columns is null");
        this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");

        this.columns = ImmutableList.copyOf(columns);

        decoders = createDecoders(columns);

        // When the _source field is requested, we need to bypass column pruning when fetching the document
        boolean needAllFields = columns.stream()
                .map(ElasticsearchColumnHandle::name)
                .anyMatch(isEqual(SOURCE.getName()));

        // Columns to fetch as doc_fields instead of pulling them out of the JSON source
        // This is convenient for types such as DATE, TIMESTAMP, etc, which have multiple possible
        // representations in JSON, but a single normalized representation as doc_field.
        List<String> documentFields = flattenFields(columns).entrySet().stream()
                .filter(entry -> entry.getValue().equals(TIMESTAMP_MILLIS))
                .map(Entry::getKey)
                .collect(toImmutableList());

        columnBuilders = columns.stream()
                .map(ElasticsearchColumnHandle::type)
                .map(type -> type.createBlockBuilder(null, 1))
                .toArray(BlockBuilder[]::new);

        List<String> requiredFields = columns.stream()
                .map(ElasticsearchColumnHandle::name)
                .filter(name -> !isBuiltinColumn(name))
                .collect(toList());

        List<JsonNode> sort = buildSort(table.sortOrder(), table.query().isPresent());

        long start = System.nanoTime();
        SearchResult searchResult;
        diagnostics.recordRemoteRequest(SEARCH);
        try {
            searchResult = client.beginSearch(
                    split.index(),
                    split.shard(),
                    buildSearchQuery(table, diagnostics),
                    needAllFields ? Optional.empty() : Optional.of(requiredFields),
                    documentFields,
                    sort,
                    table.limit());
        }
        catch (RuntimeException e) {
            diagnostics.recordFailure();
            throw e;
        }
        readTimeNanos += System.nanoTime() - start;
        this.iterator = new SearchDocumentIterator(client, searchResult, table.limit(), diagnostics);
    }

    @Override
    public long getCompletedBytes()
    {
        return totalBytes;
    }

    @Override
    public long getReadTimeNanos()
    {
        return readTimeNanos + iterator.getReadTimeNanos();
    }

    @Override
    public boolean isFinished()
    {
        return iterator.isClosed() || !iterator.hasNext();
    }

    @Override
    public void close()
    {
        iterator.close();
    }

    @Override
    public SourcePage getNextSourcePage()
    {
        if (iterator.isClosed()) {
            return null;
        }
        try {
            return readPage();
        }
        catch (RuntimeException e) {
            iterator.fail();
            throw e;
        }
    }

    private SourcePage readPage()
    {
        long size = 0;
        while (size < PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES && iterator.hasNext()) {
            SearchDocument document = iterator.next();
            Map<String, Object> source = document.sourceAsMap();

            for (int i = 0; i < decoders.size(); i++) {
                ElasticsearchColumnHandle columnHandle = columns.get(i);
                if (columnHandle.path().size() == 1) {
                    decoders.get(i).decode(document, () -> getField(source, columnHandle.path().getFirst()), columnBuilders[i]);
                    continue;
                }
                Map<String, Object> resolvedField = resolveField(source, columnHandle);
                decoders.get(i)
                        .decode(
                                document,
                                () -> resolvedField == null ? null : getField(resolvedField, columnHandle.path().getLast()),
                                columnBuilders[i]);
            }

            totalBytes += document.sourceLength();
            diagnostics.recordDecodedRows(1, document.sourceLength());

            size = Arrays.stream(columnBuilders)
                    .mapToLong(BlockBuilder::getSizeInBytes)
                    .sum();
        }

        Block[] blocks = new Block[columnBuilders.length];
        for (int i = 0; i < columnBuilders.length; i++) {
            blocks[i] = columnBuilders[i].build();
            columnBuilders[i] = columnBuilders[i].newBlockBuilderLike(null);
        }

        diagnostics.recordPageReturned();
        return SourcePage.create(new Page(blocks));
    }

    private static Map<String, Object> resolveField(Map<String, Object> document, ElasticsearchColumnHandle columnHandle)
    {
        if (document == null) {
            return null;
        }
        Map<String, Object> value = (Map<String, Object>) getField(document, columnHandle.path().getFirst());
        if (value != null) {
            for (int i = 1; i < columnHandle.path().size() - 1; i++) {
                value = (Map<String, Object>) getField(value, columnHandle.path().get(i));
                if (value == null) {
                    break;
                }
            }
        }
        return value;
    }

    public static Object getField(Map<String, Object> document, String field)
    {
        Object value = document.get(field);
        if (value == null) {
            Map<String, Object> result = new HashMap<>();
            String prefix = field + ".";
            for (Entry<String, Object> entry : document.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(prefix)) {
                    result.put(key.substring(prefix.length()), entry.getValue());
                }
            }

            if (!result.isEmpty()) {
                return result;
            }
        }

        return value;
    }

    private Map<String, Type> flattenFields(List<ElasticsearchColumnHandle> columns)
    {
        Map<String, Type> result = new HashMap<>();

        for (ElasticsearchColumnHandle column : columns) {
            flattenFields(result, column.name(), column.type());
        }

        return result;
    }

    private void flattenFields(Map<String, Type> result, String fieldName, Type type)
    {
        if (type instanceof RowType rowType) {
            for (RowType.Field field : rowType.getFields()) {
                flattenFields(result, appendPath(fieldName, field.getName().get()), field.getType());
            }
        }
        else {
            result.put(fieldName, type);
        }
    }

    private List<Decoder> createDecoders(List<ElasticsearchColumnHandle> columns)
    {
        return columns.stream()
                .map(ElasticsearchColumnHandle::decoderDescriptor)
                .map(DecoderDescriptor::createDecoder)
                .collect(toImmutableList());
    }

    private static String appendPath(String base, String element)
    {
        if (base.isEmpty()) {
            return element;
        }

        return base + "." + element;
    }

    private static class SearchDocumentIterator
            extends AbstractIterator<SearchDocument>
    {
        private final ElasticsearchClient client;
        private final OptionalLong limit;
        private final ElasticsearchPushdownDiagnostics diagnostics;

        private List<SearchDocument> currentHits;
        private String scrollId;
        private int currentPosition;

        private long readTimeNanos;
        private long totalRecordCount;
        private boolean closed;
        private boolean failed;
        private boolean exhausted;

        public SearchDocumentIterator(
                ElasticsearchClient client,
                SearchResult first,
                OptionalLong limit,
                ElasticsearchPushdownDiagnostics diagnostics)
        {
            this.client = requireNonNull(client, "client is null");
            this.limit = requireNonNull(limit, "limit is null");
            this.diagnostics = requireNonNull(diagnostics, "diagnostics is null");
            this.totalRecordCount = 0;
            reset(requireNonNull(first, "first is null"));
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
                // No more record is necessary.
                exhausted = true;
                return endOfData();
            }

            if (currentPosition == currentHits.size() && !exhausted && scrollId != null) {
                long start = System.nanoTime();
                SearchResult result;
                diagnostics.recordRemoteRequest(NEXT_PAGE);
                try {
                    result = client.nextPage(scrollId);
                }
                catch (RuntimeException e) {
                    fail();
                    throw e;
                }
                readTimeNanos += System.nanoTime() - start;
                reset(result);
            }

            if (currentPosition == currentHits.size()) {
                exhausted = true;
                return endOfData();
            }

            SearchDocument document = currentHits.get(currentPosition);
            currentPosition++;
            totalRecordCount++;
            if (scrollId == null && currentPosition == currentHits.size()) {
                exhausted = true;
            }

            return document;
        }

        private void reset(SearchResult result)
        {
            diagnostics.recordRemotePageReceived();
            scrollId = result.scrollId().orElse(null);
            currentHits = result.hits();
            currentPosition = 0;
            exhausted = currentHits.isEmpty();
        }

        private boolean limitReached()
        {
            return limit.isPresent() && totalRecordCount >= limit.orElseThrow();
        }

        public void fail()
        {
            if (!failed) {
                failed = true;
                diagnostics.recordFailure();
            }
            close();
        }

        public void close()
        {
            if (closed) {
                return;
            }
            closed = true;
            // This is a downstream early close, not necessarily a user-cancelled query. A pushed LIMIT and
            // observed remote exhaustion are normal completion; failed scans are accounted separately.
            if (!failed && !exhausted && !limitReached()) {
                diagnostics.recordCancellation();
            }
            if (scrollId != null) {
                diagnostics.recordClearScroll();
                try {
                    client.clearScroll(scrollId);
                }
                catch (Exception e) {
                    diagnostics.recordFailure();
                    // ignore
                    LOG.debug(e, "Error clearing scroll");
                }
            }
        }
    }
}
