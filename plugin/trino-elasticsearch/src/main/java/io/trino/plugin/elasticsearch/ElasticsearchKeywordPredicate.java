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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airlift.slice.Slice;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.VarcharDecoder;
import io.trino.spi.predicate.Domain;
import io.trino.spi.type.VarcharType;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

final class ElasticsearchKeywordPredicate
{
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final int MAX_VALUES = 1000;
    private static final int MAX_TERM_BYTES = 32766;

    private ElasticsearchKeywordPredicate() {}

    static boolean isSupported(ElasticsearchColumnHandle column, Domain domain)
    {
        if (keywordField(column).isEmpty() || domain.isNullAllowed() || !domain.getValues().isDiscreteSet()) {
            return false;
        }
        List<Object> values = domain.getValues().getDiscreteSet();
        return !values.isEmpty()
                && values.size() <= MAX_VALUES
                && values.stream().allMatch(value -> ((Slice) value).length() <= MAX_TERM_BYTES);
    }

    private static Optional<String> keywordField(ElasticsearchColumnHandle column)
    {
        if (column.path().size() != 1
                || !(column.type() instanceof VarcharType)
                || !(column.decoderDescriptor() instanceof VarcharDecoder.Descriptor)
                || !(column.elasticsearchType() instanceof PrimitiveType primitiveType)
                || !primitiveType.name().equals("text")) {
            return Optional.empty();
        }
        return primitiveType.keyword().map(keyword -> column.name() + "." + keyword);
    }

    static JsonNode buildQuery(ElasticsearchColumnHandle column, Domain domain)
    {
        checkArgument(isSupported(column, domain), "Unsupported keyword predicate for %s", column.name());
        String field = keywordField(column).orElseThrow();
        ArrayNode values = JSON.arrayNode();
        domain.getValues().getDiscreteSet().forEach(value -> values.add(((Slice) value).toStringUtf8()));

        ObjectNode terms = JSON.objectNode().set("terms", JSON.objectNode().set(field, values));
        ObjectNode exists = JSON.objectNode().set("exists", JSON.objectNode().put("field", field));
        ObjectNode missing = JSON.objectNode().set("bool", JSON.objectNode().set("must_not", JSON.arrayNode().add(exists)));

        // Adding a multi-field does not backfill existing documents. Keep those documents as candidates, and retain
        // the SQL predicate in Trino. A residual alone could not recover documents rejected by an unguarded terms query.
        ObjectNode predicate = JSON.objectNode();
        predicate.set("should", JSON.arrayNode().add(terms).add(missing));
        predicate.put("minimum_should_match", 1);
        return JSON.objectNode().set("bool", predicate);
    }
}
