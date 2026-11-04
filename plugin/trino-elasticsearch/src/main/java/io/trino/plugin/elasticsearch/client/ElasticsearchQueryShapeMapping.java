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
package io.trino.plugin.elasticsearch.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

/**
 * Mapping proofs for operations that consume indexed values instead of decoded source values.
 */
final class ElasticsearchQueryShapeMapping
{
    private ElasticsearchQueryShapeMapping() {}

    static IndexMetadata.ObjectType apply(IndexMetadata.ObjectType schema, JsonNode indices)
    {
        List<Mapping> mappings = new ArrayList<>();
        for (JsonNode index : indices) {
            Set<String> targets = new HashSet<>();
            collectCopyTargets(index.path("mappings").path("properties"), targets);
            mappings.add(new Mapping(index, Set.copyOf(targets)));
        }
        return apply(schema, mappings, "", true);
    }

    private static IndexMetadata.ObjectType apply(IndexMetadata.ObjectType schema, List<Mapping> indices, String parent, boolean ancestorSafe)
    {
        List<IndexMetadata.Field> fields = schema.fields().stream()
                .map(field -> {
                    String path = parent.isEmpty() ? field.name() : parent + "." + field.name();
                    boolean object = field.type() instanceof IndexMetadata.ObjectType;
                    boolean safe = ancestorSafe && safeAcrossIndices(indices, path, object, field);
                    IndexMetadata.Type type = field.type();
                    if (type instanceof IndexMetadata.ObjectType children) {
                        type = apply(children, indices, path, safe);
                    }
                    return new IndexMetadata.Field(field.asRawJson(), field.isArray(), field.name(), type, safe);
                })
                .toList();
        return new IndexMetadata.ObjectType(fields);
    }

    private static boolean safeAcrossIndices(List<Mapping> indices, String path, boolean object, IndexMetadata.Field field)
    {
        JsonNode first = null;
        if (indices.isEmpty()) {
            return false;
        }
        for (Mapping entry : indices) {
            JsonNode index = entry.index();
            JsonNode mappings = index.path("mappings");
            JsonNode node = mappings;
            JsonNode meta = mappings.path("_meta").path("trino");
            if (meta.isMissingNode()) {
                meta = mappings.path("_meta").path("presto");
            }
            for (String part : path.split("\\.")) {
                if (!node.path("enabled").asBoolean(true) || node.path("type").asText().equals("nested")) {
                    return false;
                }
                node = node.path("properties").path(part);
                meta = meta.path(part);
            }
            if (!node.isObject() || (first != null && !first.equals(node))
                    || meta.path("isArray").asBoolean() != field.isArray()
                    || meta.path("asRawJson").asBoolean() != field.asRawJson()) {
                return false;
            }
            first = node;
            Set<String> copyTargets = entry.copyTargets();
            if (!mappings.path("enabled").asBoolean(true)
                    || !mappings.path("_source").path("enabled").asBoolean(true)
                    || copyTargets.contains(path) || mappings.path("runtime").has(path)
                    || field.asRawJson() || field.isArray()
                    || !node.path("enabled").asBoolean(true) || node.path("type").asText().equals("nested")) {
                return false;
            }
            if (object) {
                continue;
            }
            JsonNode source = mappings.path("_source");
            if ((!source.path("includes").isMissingNode() && !source.path("includes").isEmpty() && !matches(source.path("includes"), path))
                    || matches(source.path("excludes"), path)
                    || !index.path("settings").isObject()
                    || !node.path("doc_values").asBoolean(true)
                    || node.has("script") || node.has("null_value") || node.has("normalizer") || node.has("ignore_above")
                    || node.path("ignore_malformed").asBoolean(index.path("settings").path("index.mapping.ignore_malformed").asBoolean(false))) {
                return false;
            }
            // A newly added multi-field need not be backfilled for existing source documents.
            if (node.path("type").asText().equals("text")) {
                return false;
            }
        }
        return true;
    }

    private record Mapping(JsonNode index, Set<String> copyTargets) {}

    private static boolean matches(JsonNode patterns, String path)
    {
        if (patterns.isTextual()) {
            String regex = Arrays.stream(patterns.asText().split("\\*", -1))
                    .map(Pattern::quote)
                    .collect(joining(".*"));
            return Pattern.compile(regex + "(?:\\..*)?", Pattern.DOTALL).matcher(path).matches();
        }
        for (JsonNode pattern : patterns) {
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static void collectCopyTargets(JsonNode properties, Set<String> targets)
    {
        for (JsonNode mapping : properties) {
            JsonNode copy = mapping.path("copy_to");
            if (copy.isTextual()) {
                targets.add(copy.asText());
            }
            else {
                copy.forEach(value -> targets.add(value.asText()));
            }
            collectCopyTargets(mapping.path("properties"), targets);
            collectCopyTargets(mapping.path("fields"), targets);
        }
    }
}
