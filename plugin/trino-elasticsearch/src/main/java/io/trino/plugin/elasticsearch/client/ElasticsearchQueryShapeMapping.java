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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

/**
 * Mapping proofs for operations that consume indexed values instead of decoded source values.
 */
final class ElasticsearchQueryShapeMapping
{
    private ElasticsearchQueryShapeMapping() {}

    static IndexMetadata.ObjectType apply(IndexMetadata.ObjectType schema, JsonNode indices)
    {
        return apply(schema, indices, Map.of());
    }

    static IndexMetadata.ObjectType apply(IndexMetadata.ObjectType schema, JsonNode indices, Map<String, Set<String>> ignoredMalformedFields)
    {
        List<Mapping> mappings = new ArrayList<>();
        for (var entry : indices.properties()) {
            JsonNode index = entry.getValue();
            Set<String> targets = new HashSet<>();
            collectCopyTargets(index.path("mappings").path("properties"), targets);
            mappings.add(new Mapping(index, Set.copyOf(targets), Optional.ofNullable(ignoredMalformedFields.get(entry.getKey()))));
        }
        return apply(schema, mappings, "", true, true, true);
    }

    private static IndexMetadata.ObjectType apply(IndexMetadata.ObjectType schema, List<Mapping> indices, String parent, boolean ancestorShapeSafe, boolean ancestorPredicateSafe, boolean objectArrayPathAllowed)
    {
        List<IndexMetadata.Field> fields = schema.fields().stream()
                .map(field -> {
                    String path = parent.isEmpty() ? field.name() : parent + "." + field.name();
                    boolean object = field.type() instanceof IndexMetadata.ObjectType;
                    boolean mappedScalar = field.inferredMapping()
                            .map(inferred -> inferred.scalarMode() != IndexMetadata.InferredScalarMode.SOURCE)
                            .orElse(true);
                    boolean predicateSafe = ancestorPredicateSafe && mappedScalar && safeAcrossIndices(indices, path, object, field, false);
                    boolean shapeSafe = ancestorShapeSafe && mappedScalar && safeAcrossIndices(indices, path, object, field, true);
                    IndexMetadata.Type type = field.type();
                    if (type instanceof IndexMetadata.ObjectType children) {
                        boolean unambiguousPath = objectArrayPathAllowed && !field.name().contains(".");
                        IndexMetadata.ObjectType mapped = apply(children, indices, path, shapeSafe, predicateSafe, unambiguousPath && !field.isArray() && !field.asRawJson());
                        if (unambiguousPath && field.isArray() && field.inferredMapping().isEmpty()) {
                            mapped = new IndexMetadata.ObjectType(mapped.fields(), objectArrayMapping(indices, path, children));
                        }
                        type = mapped;
                    }
                    return new IndexMetadata.Field(field.asRawJson(), field.isArray(), field.name(), type, shapeSafe, predicateSafe, field.inferredMapping());
                })
                .toList();
        return new IndexMetadata.ObjectType(fields);
    }

    private static boolean safeAcrossIndices(List<Mapping> indices, String path, boolean object, IndexMetadata.Field field, boolean queryShape)
    {
        return safeAcrossIndices(indices, path, object, field, queryShape, Optional.empty());
    }

    private static boolean safeAcrossIndices(List<Mapping> indices, String path, boolean object, IndexMetadata.Field field, boolean queryShape, Optional<String> nestedPath)
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
            int prefixLength = -1;
            for (String part : path.split("\\.")) {
                if (!node.path("enabled").asBoolean(true)
                        || (node.path("type").asText().equals("nested") && (prefixLength < 0 || !nestedPath.orElse("").equals(path.substring(0, prefixLength))))) {
                    return false;
                }
                node = node.path("properties").path(part);
                meta = meta.path(part);
                prefixLength += part.length() + 1;
            }
            if (!node.isObject() || (first != null && (queryShape || !object) && !first.equals(node))
                    || meta.path("isArray").asBoolean() != field.isArray()
                    || meta.path("asRawJson").asBoolean() != field.asRawJson()) {
                return false;
            }
            first = node;
            Set<String> copyTargets = entry.copyTargets();
            if (!mappings.path("enabled").asBoolean(true)
                    || !mappings.path("_source").path("enabled").asBoolean(true)
                    || copyTargets.contains(path) || mappings.path("runtime").has(path)
                    || field.asRawJson() || (queryShape && field.isArray())
                    || !node.path("enabled").asBoolean(true) || node.path("type").asText().equals("nested")) {
                return false;
            }
            if (object) {
                continue;
            }
            JsonNode source = mappings.path("_source");
            if ((!source.path("includes").isMissingNode() && !source.path("includes").isEmpty() && !matches(source.path("includes"), path))
                    || matches(source.path("excludes"), path)
                    || node.has("script") || node.has("null_value") || node.has("normalizer") || node.has("ignore_above")
                    || (!node.path("index").asBoolean(true) && !node.path("doc_values").asBoolean(true))
                    || (field.inferredMapping().isPresent() && !node.path("index").asBoolean(true))
                    || (ignoresMalformed(entry, node, path) && field.inferredMapping()
                    .filter(inferred -> inferred.scalarMode() == IndexMetadata.InferredScalarMode.DOC_VALUES_IGNORE_MALFORMED).isEmpty())) {
                return false;
            }
            // A newly added multi-field need not be backfilled for existing source documents.
            if (queryShape && (!node.path("doc_values").asBoolean(true) || node.path("type").asText().equals("text"))) {
                return false;
            }
        }
        return true;
    }

    private static Optional<IndexMetadata.ObjectArrayMapping> objectArrayMapping(List<Mapping> indices, String path, IndexMetadata.ObjectType object)
    {
        if (indices.isEmpty()) {
            return Optional.empty();
        }
        String kind = null;
        String[] parts = path.split("\\.");
        for (Mapping entry : indices) {
            JsonNode mappings = entry.index().path("mappings");
            JsonNode source = mappings.path("_source");
            JsonNode settings = entry.index().path("settings");
            if (!source.path("enabled").asBoolean(true) || source.has("includes") || source.has("excludes")
                    || !source.path("mode").asText("stored").equals("stored")
                    || !settings.path("index.mapping.source.mode").asText("stored").equals("stored")
                    || !settings.path("index.mode").asText("standard").equals("standard")) {
                return Optional.empty();
            }

            JsonNode node = mappings;
            JsonNode meta = mappings.path("_meta").path("trino");
            if (meta.isMissingNode() || meta.isNull()) {
                meta = mappings.path("_meta").path("presto");
            }
            for (String part : parts) {
                if (!node.path("enabled").asBoolean(true) || !node.path("subobjects").asBoolean(true)
                        || !node.path("type").asText("object").equals("object")
                        || meta.path("isArray").asBoolean() || meta.path("asRawJson").asBoolean()) {
                    return Optional.empty();
                }
                node = node.path("properties").path(part);
                meta = meta.path(part);
            }
            String mappedKind = node.path("type").asText("object");
            if (!node.path("properties").isObject() || !node.path("enabled").asBoolean(true) || !node.path("subobjects").asBoolean(true)
                    || !meta.path("isArray").asBoolean() || meta.path("asRawJson").asBoolean()
                    || (!mappedKind.equals("object") && !mappedKind.equals("nested"))
                    || (kind != null && !kind.equals(mappedKind))) {
                return Optional.empty();
            }
            kind = mappedKind;
        }

        boolean nested = "nested".equals(kind);
        Optional<String> nestedPath = nested ? Optional.of(path) : Optional.empty();
        boolean indexIgnoresLongValues = indices.stream().anyMatch(entry -> entry.index().path("settings").has("index.mapping.ignore_above"));
        Set<String> indexedFields = object.fields().stream()
                .filter(field -> !indexIgnoresLongValues && !field.name().contains(".") && !field.isArray()
                        && field.type() instanceof IndexMetadata.PrimitiveType primitive && primitive.name().equals("keyword")
                        && primitive.indexed() && primitive.sourceValueSemanticsExact()
                        && safeAcrossIndices(indices, path + "." + field.name(), false, field, false, nestedPath))
                .map(IndexMetadata.Field::name)
                .collect(toImmutableSet());
        return Optional.of(new IndexMetadata.ObjectArrayMapping(nested, indexedFields));
    }

    static boolean requiresFieldMappingDefaults(JsonNode index)
    {
        JsonNode settings = index.path("settings");
        return !settings.isObject()
                || (settings.has("index.mapping.ignore_malformed")
                && !settings.path("index.mapping.ignore_malformed").asText().equals("false"));
    }

    private static boolean ignoresMalformed(Mapping mapping, JsonNode field, String path)
    {
        if (field.has("ignore_malformed")) {
            return field.path("ignore_malformed").asBoolean();
        }
        return requiresFieldMappingDefaults(mapping.index()) &&
                mapping.ignoredMalformedFields().map(fields -> fields.contains(path)).orElse(true);
    }

    private record Mapping(JsonNode index, Set<String> copyTargets, Optional<Set<String>> ignoredMalformedFields) {}

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

    static void collectCopyTargets(JsonNode properties, Set<String> targets)
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
