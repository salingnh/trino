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
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.trino.plugin.elasticsearch.client.IndexMetadata.Field;
import io.trino.plugin.elasticsearch.client.IndexMetadata.InferredMapping;
import io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode;
import io.trino.plugin.elasticsearch.client.IndexMetadata.ObjectType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode.DOC_VALUES;
import static io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode.DOC_VALUES_IGNORE_MALFORMED;
import static io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode.MAPPED_SOURCE;
import static io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode.SOURCE;

final class ElasticsearchSchemaInference
{
    private static final int MAX_SAMPLE_BATCH_SIZE = 16;

    private ElasticsearchSchemaInference() {}

    static boolean needsInference(JsonNode mappings)
    {
        JsonNode metadata = mappings.path("_meta");
        return !metadata.has("trino") && !metadata.hasNonNull("presto") && mappings.path("properties").isObject();
    }

    // A query per missing path avoids drawing the same three documents for unrelated sparse fields.
    static ObjectType infer(ObjectType schema, JsonNode index, List<Sample> initialSamples, Function<List<ObjectNode>, List<Sample>> search)
    {
        JsonNode mappings = index.path("mappings");
        JsonNode properties = mappings.path("properties");
        JsonNode settings = index.path("settings");
        boolean ignoreMalformed = settings.path("index.mapping.ignore_malformed").asBoolean(false);
        boolean boundedKeywords = settings.has("index.mapping.ignore_above");
        Set<String> copyTargets = new HashSet<>();
        ElasticsearchQueryShapeMapping.collectCopyTargets(properties, copyTargets);
        List<SampleField> fields = schema.fields().stream()
                .map(field -> new SampleField(
                        field,
                        properties.path(field.name()),
                        "",
                        List.of(),
                        !copyTargets.contains(field.name()) && !mappings.path("runtime").has(field.name())
                                && !(boundedKeywords && properties.path(field.name()).path("type").asText().equals("keyword")),
                        ignoreMalformed))
                .toList();
        Set<String> sampledIds = new HashSet<>();
        observe(fields, sampledIds, initialSamples);
        ImmutableList.Builder<SampleField> missing = ImmutableList.builder();
        fields.forEach(field -> field.collectMissing(missing));
        for (List<SampleField> batch : Lists.partition(missing.build(), MAX_SAMPLE_BATCH_SIZE)) {
            List<ObjectNode> queries = batch.stream()
                    .filter(field -> !field.hasValueSample())
                    .map(SampleField::existsQuery)
                    .toList();
            if (!queries.isEmpty()) {
                observe(fields, sampledIds, search.apply(queries));
            }
        }
        return new ObjectType(fields.stream().map(SampleField::resolve).toList());
    }

    static ObjectType merge(List<ObjectType> schemas)
    {
        Map<String, List<Field>> fields = new LinkedHashMap<>();
        for (ObjectType schema : schemas) {
            for (Field field : schema.fields()) {
                fields.computeIfAbsent(field.name(), _ -> new ArrayList<>()).add(field);
            }
        }
        return new ObjectType(fields.values().stream().map(ElasticsearchSchemaInference::mergeField).toList());
    }

    private static Field mergeField(List<Field> fields)
    {
        Field first = fields.getFirst();
        if (fields.stream().allMatch(first::equals)) {
            return first;
        }
        if (fields.stream().allMatch(field -> field.inferredMapping().isEmpty())) {
            return mergeExplicitField(fields);
        }

        Set<String> observedTypes = new TreeSet<>();
        for (Field field : fields) {
            field.inferredMapping().ifPresent(mapping -> observedTypes.addAll(mapping.observedTypes()));
            if (field.inferredMapping().isEmpty() && field.type() instanceof PrimitiveType primitive) {
                observedTypes.add(switch (primitive.name()) {
                    case "long", "byte", "short", "integer" -> "INTEGER";
                    case "double", "float", "half_float" -> "REAL";
                    case "text", "keyword" -> "STRING";
                    case "boolean" -> "BOOLEAN";
                    default -> primitive.name();
                });
            }
        }

        int depth = arrayDepth(first);
        if (observedTypes.size() > 2 || fields.stream().anyMatch(field -> field.asRawJson() || arrayDepth(field) != depth)) {
            return raw(first, observedTypes);
        }
        if (fields.stream().allMatch(field -> field.type() instanceof ObjectType)) {
            ObjectType children = merge(fields.stream().map(field -> (ObjectType) field.type()).toList());
            return resolved(first, children, depth, Optional.empty(), observedTypes);
        }
        InferredScalarMode mode = first.inferredMapping().map(InferredMapping::scalarMode).orElse(SOURCE);
        if (mode != SOURCE && fields.stream().allMatch(field -> field.type().equals(first.type())
                && field.inferredMapping().map(InferredMapping::scalarMode).orElse(SOURCE) == mode)) {
            return resolved(first, first.type(), depth, Optional.empty(), observedTypes, false, mode);
        }
        if (observedTypes.size() == 2 && observedTypes.contains("STRING") &&
                (observedTypes.contains("INTEGER") || observedTypes.contains("REAL") || observedTypes.contains("BOOLEAN")) &&
                fields.stream().allMatch(field -> field.type() instanceof PrimitiveType)) {
            return resolved(first, first.type(), depth, Optional.of("keyword"), observedTypes);
        }
        if (observedTypes.equals(Set.of("INTEGER", "REAL")) &&
                fields.stream().allMatch(field -> field.inferredMapping().map(InferredMapping::integersFitDouble).orElse(false))) {
            return resolved(first, first.type(), depth, Optional.of("double"), observedTypes, true);
        }
        if (fields.stream().allMatch(field -> field.type().equals(first.type()) && inferredPrimitive(field).equals(inferredPrimitive(first)))) {
            return resolved(first, first.type(), depth, inferredPrimitive(first), observedTypes);
        }
        return raw(first, observedTypes);
    }

    private static Field mergeExplicitField(List<Field> fields)
    {
        Field first = fields.getFirst();
        if (fields.stream().anyMatch(field -> field.asRawJson() || field.isArray() != first.isArray())) {
            return explicitRaw(first);
        }
        if (fields.stream().allMatch(field -> field.type() instanceof ObjectType)) {
            ObjectType children = merge(fields.stream().map(field -> (ObjectType) field.type()).toList());
            return new Field(false, first.isArray(), first.name(), children);
        }
        if (first.type() instanceof PrimitiveType firstPrimitive
                && fields.stream().allMatch(field -> field.type() instanceof PrimitiveType primitive && primitive.name().equals(firstPrimitive.name()))) {
            Optional<String> keyword = fields.stream()
                    .map(field -> ((PrimitiveType) field.type()).keyword())
                    .allMatch(firstPrimitive.keyword()::equals)
                    ? firstPrimitive.keyword()
                    : Optional.empty();
            boolean aggregatable = fields.stream().allMatch(field -> ((PrimitiveType) field.type()).aggregatable());
            boolean sourceExact = fields.stream().allMatch(field -> ((PrimitiveType) field.type()).sourceValueSemanticsExact());
            boolean indexed = fields.stream().allMatch(field -> ((PrimitiveType) field.type()).indexed());
            return new Field(false, first.isArray(), first.name(), new PrimitiveType(firstPrimitive.name(), keyword, aggregatable, sourceExact, indexed));
        }
        if (first.type() instanceof IndexMetadata.DateTimeType firstDate
                && fields.stream().allMatch(field -> field.type() instanceof IndexMetadata.DateTimeType date && date.formats().equals(firstDate.formats()))) {
            boolean aggregatable = fields.stream().allMatch(field -> ((IndexMetadata.DateTimeType) field.type()).aggregatable());
            boolean sourceExact = fields.stream().allMatch(field -> ((IndexMetadata.DateTimeType) field.type()).sourceValueSemanticsExact());
            return new Field(false, first.isArray(), first.name(), new IndexMetadata.DateTimeType(firstDate.formats(), aggregatable, sourceExact));
        }
        if (first.type() instanceof IndexMetadata.ScaledFloatType firstScaled
                && fields.stream().allMatch(field -> field.type() instanceof IndexMetadata.ScaledFloatType scaled && Double.compare(scaled.scale(), firstScaled.scale()) == 0)) {
            boolean aggregatable = fields.stream().allMatch(field -> ((IndexMetadata.ScaledFloatType) field.type()).aggregatable());
            boolean sourceExact = fields.stream().allMatch(field -> ((IndexMetadata.ScaledFloatType) field.type()).sourceValueSemanticsExact());
            return new Field(false, first.isArray(), first.name(), new IndexMetadata.ScaledFloatType(firstScaled.scale(), aggregatable, sourceExact));
        }
        return explicitRaw(first);
    }

    private static Field explicitRaw(Field field)
    {
        return new Field(true, false, field.name(), field.type(), false, false);
    }

    private static Optional<String> inferredPrimitive(Field field)
    {
        return field.inferredMapping().flatMap(InferredMapping::primitiveType);
    }

    private static int arrayDepth(Field field)
    {
        return field.inferredMapping().map(InferredMapping::arrayDepth).orElse(field.isArray() ? 1 : 0);
    }

    private static void observe(List<SampleField> fields, Set<String> sampledIds, List<Sample> documents)
    {
        for (Sample sample : documents) {
            if (sampledIds.add(sample.id())) {
                fields.forEach(field -> field.observe(fieldValue(sample.source(), field.field.name()), 0));
            }
        }
    }

    private static JsonNode fieldValue(JsonNode object, String name)
    {
        JsonNode value = object.path(name);
        if (!value.isMissingNode() && !value.isNull()) {
            return value;
        }
        // Match the scan decoder's handling of literal dotted names.
        String prefix = name + ".";
        ObjectNode children = instance.objectNode();
        object.properties().forEach(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                children.set(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        });
        return children.isEmpty() ? value : children;
    }

    private static Field raw(Field field, Set<String> observedTypes)
    {
        return new Field(true, false, field.name(), field.type(), false, false, Optional.of(new InferredMapping(0, Optional.empty(), observedTypes, false)));
    }

    private static Field resolved(Field field, IndexMetadata.Type type, int depth, Optional<String> primitive, Set<String> observedTypes)
    {
        return resolved(field, type, depth, primitive, observedTypes, false);
    }

    private static Field resolved(Field field, IndexMetadata.Type type, int depth, Optional<String> primitive, Set<String> observedTypes, boolean integersFitDouble)
    {
        return resolved(field, type, depth, primitive, observedTypes, integersFitDouble, SOURCE);
    }

    private static Field resolved(Field field, IndexMetadata.Type type, int depth, Optional<String> primitive, Set<String> observedTypes, boolean integersFitDouble, InferredScalarMode scalarMode)
    {
        return new Field(false, depth > 0, field.name(), type, false, false, Optional.of(new InferredMapping(depth, primitive, observedTypes, integersFitDouble, scalarMode)));
    }

    record Sample(String id, JsonNode source) {}

    private static final class SampleField
    {
        private final Field field;
        private final String path;
        private final List<String> nestedPaths;
        private final List<SampleField> children;
        private final boolean queryable;
        private final InferredScalarMode scalarMode;
        private final boolean keywordDocValues;
        private final Set<Integer> leafDepths = new HashSet<>();
        private final Set<String> observedTypes = new TreeSet<>();
        private int minimumArrayDepth;
        private boolean integersFitLong = true;
        private boolean integersFitDouble = true;
        private boolean valuesFitMapping = true;

        SampleField(Field field, JsonNode definition, String parent, List<String> parents, boolean useMapping, boolean ignoreMalformedDefault)
        {
            this.field = field;
            scalarMode = useMapping ? scalarMode(field, definition, ignoreMalformedDefault) : SOURCE;
            keywordDocValues = scalarMode == MAPPED_SOURCE
                    && field.type() instanceof PrimitiveType primitive && primitive.name().equals("keyword")
                    && definition.path("doc_values").asBoolean(true) && !definition.has("ignore_above")
                    && !definition.has("normalizer");
            queryable = definition.path("enabled").asBoolean(true)
                    && (definition.path("index").asBoolean(true) || definition.path("doc_values").asBoolean(true));
            path = parent.isEmpty() ? field.name() : parent + "." + field.name();
            nestedPaths = definition.path("type").asText().equals("nested")
                    ? ImmutableList.<String>builder().addAll(parents).add(path).build()
                    : parents;
            children = field.type() instanceof ObjectType object
                    ? object.fields().stream()
                      .map(child -> new SampleField(child, definition.path("properties").path(child.name()), path, nestedPaths, false, false))
                      .toList()
                    : List.of();
        }

        void observe(JsonNode value, int depth)
        {
            if (value.isNull() || value.isMissingNode()) {
                return;
            }
            if (value.isArray()) {
                minimumArrayDepth = Math.max(minimumArrayDepth, depth + 1);
                value.forEach(element -> observe(element, depth + 1));
                return;
            }

            leafDepths.add(depth);
            observedTypes.add(value.isIntegralNumber() ? "INTEGER" : value.isFloatingPointNumber() ? "REAL" : value.getNodeType().name());
            if (value.isIntegralNumber()) {
                integersFitLong &= value.canConvertToLong();
                double doubleValue = value.doubleValue();
                integersFitDouble &= Double.isFinite(doubleValue) && new BigDecimal(doubleValue).compareTo(value.decimalValue()) == 0;
            }
            if (field.type() instanceof PrimitiveType primitive) {
                valuesFitMapping &= fitsMappedPrimitive(primitive.name(), value);
            }
            else if (field.type() instanceof IndexMetadata.ScaledFloatType) {
                valuesFitMapping &= value.isNumber() && Double.isFinite(value.doubleValue());
            }
            else if (field.type() instanceof IndexMetadata.DateTimeType) {
                valuesFitMapping &= value.isTextual() || (value.isIntegralNumber() && value.canConvertToLong());
            }
            if (value.isObject()) {
                children.forEach(child -> child.observe(fieldValue(value, child.field.name()), 0));
            }
        }

        void collectMissing(ImmutableList.Builder<SampleField> missing)
        {
            if (!hasValueSample() && children.isEmpty() && queryable) {
                missing.add(this);
            }
            children.forEach(child -> child.collectMissing(missing));
        }

        boolean hasValueSample()
        {
            return !leafDepths.isEmpty();
        }

        ObjectNode existsQuery()
        {
            ObjectNode query = instance.objectNode();
            query.putObject("exists").put("field", path);
            for (String nestedPath : nestedPaths.reversed()) {
                ObjectNode outer = instance.objectNode();
                ObjectNode nested = outer.putObject("nested");
                nested.put("path", nestedPath);
                nested.put("score_mode", "none");
                nested.set("query", query);
                query = outer;
            }
            return query;
        }

        Field resolve()
        {
            int depth = leafDepths.stream().findFirst().orElse(minimumArrayDepth);
            if ((!hasValueSample() && minimumArrayDepth == 0) || leafDepths.size() > 1 || minimumArrayDepth > depth || observedTypes.size() > 2) {
                return raw(field, observedTypes);
            }

            if (field.type() instanceof ObjectType) {
                if (children.isEmpty() || (!observedTypes.isEmpty() && !observedTypes.equals(Set.of(JsonNodeType.OBJECT.name())))) {
                    return raw(field, observedTypes);
                }
                return resolved(field, new ObjectType(children.stream().map(SampleField::resolve).toList()), depth, Optional.empty(), observedTypes);
            }

            if (observedTypes.contains(JsonNodeType.OBJECT.name())) {
                return raw(field, observedTypes);
            }

            if (depth == 0 && scalarMode != SOURCE && observedTypes.stream().allMatch(ElasticsearchSchemaInference::isScalarValue)) {
                if (scalarMode.usesDocValues() || observedTypes.equals(Set.of("STRING"))
                        || (field.type() instanceof PrimitiveType primitive && primitive.name().equals("boolean") && valuesFitMapping)) {
                    return resolved(field, field.type(), depth, Optional.empty(), observedTypes, integersFitDouble, scalarMode);
                }
                if (keywordDocValues) {
                    return resolved(field, field.type(), depth, Optional.empty(), observedTypes, integersFitDouble, DOC_VALUES);
                }
            }
            if (field.type() instanceof PrimitiveType primitive && primitive.name().equals("boolean") && valuesFitMapping) {
                return resolved(field, field.type(), depth, Optional.empty(), observedTypes, integersFitDouble);
            }
            if (field.type() instanceof IndexMetadata.DateTimeType && supported(field.type()) && valuesFitMapping) {
                return resolved(field, field.type(), depth, Optional.empty(), observedTypes, integersFitDouble);
            }
            if (observedTypes.size() == 2) {
                if (observedTypes.contains("STRING") &&
                        (observedTypes.contains("INTEGER") || observedTypes.contains("REAL") || observedTypes.contains("BOOLEAN"))) {
                    return resolved(field, field.type(), depth, Optional.of("keyword"), observedTypes);
                }
                if (observedTypes.equals(Set.of("INTEGER", "REAL")) && integersFitDouble) {
                    return resolved(field, field.type(), depth, Optional.of("double"), observedTypes, true);
                }
                return raw(field, observedTypes);
            }
            if (valuesFitMapping && supported(field.type())) {
                return resolved(field, field.type(), depth, Optional.empty(), observedTypes, integersFitDouble);
            }
            if (!(field.type() instanceof PrimitiveType)) {
                return raw(field, observedTypes);
            }

            String inferred = switch (observedTypes.stream().findFirst().orElse("")) {
                case "STRING" -> "keyword";
                case "INTEGER" -> integersFitLong ? "long" : null;
                case "REAL" -> "double";
                case "BOOLEAN" -> "boolean";
                default -> null;
            };
            if (inferred == null) {
                return raw(field, observedTypes);
            }
            return resolved(field, field.type(), depth, Optional.of(inferred), observedTypes, integersFitDouble);
        }
    }

    private static boolean isScalarValue(String type)
    {
        return switch (type) {
            case "INTEGER", "REAL", "STRING", "BOOLEAN" -> true;
            default -> false;
        };
    }

    private static InferredScalarMode scalarMode(Field field, JsonNode definition, boolean ignoreMalformedDefault)
    {
        if (definition.has("null_value") || definition.has("script")) {
            return SOURCE;
        }
        if (field.type() instanceof PrimitiveType primitive && (primitive.name().equals("keyword") || primitive.name().equals("text"))) {
            return MAPPED_SOURCE;
        }
        if (!definition.path("doc_values").asBoolean(true)) {
            if (field.type() instanceof PrimitiveType primitive && primitive.name().equals("boolean") && !definition.path("ignore_malformed").asBoolean(ignoreMalformedDefault)) {
                return MAPPED_SOURCE;
            }
            return SOURCE;
        }
        boolean mappedScalar = switch (field.type()) {
            case PrimitiveType primitive -> switch (primitive.name()) {
                case "byte", "short", "integer", "long", "float", "half_float", "double", "boolean" -> true;
                default -> false;
            };
            case IndexMetadata.DateTimeType _, IndexMetadata.ScaledFloatType _ -> true;
            default -> false;
        };
        if (!mappedScalar) {
            return SOURCE;
        }
        return definition.path("ignore_malformed").asBoolean(ignoreMalformedDefault) ? DOC_VALUES_IGNORE_MALFORMED : DOC_VALUES;
    }

    private static boolean fitsMappedPrimitive(String type, JsonNode value)
    {
        return switch (type) {
            case "byte" -> value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= Byte.MIN_VALUE && value.intValue() <= Byte.MAX_VALUE;
            case "short" -> value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= Short.MIN_VALUE && value.intValue() <= Short.MAX_VALUE;
            case "integer" -> value.isIntegralNumber() && value.canConvertToInt();
            case "long" -> value.isIntegralNumber() && value.canConvertToLong();
            case "float", "double" -> value.isNumber();
            case "text", "keyword" -> value.isTextual();
            case "boolean" -> value.isBoolean() || (value.isTextual() && switch (value.textValue()) {
                case "", "true", "false" -> true;
                default -> false;
            });
            case "ip", "binary" -> value.isTextual();
            default -> false;
        };
    }

    private static boolean supported(IndexMetadata.Type type)
    {
        return switch (type) {
            case PrimitiveType primitive -> switch (primitive.name()) {
                case "byte", "short", "integer", "long", "float", "double", "text", "keyword", "boolean", "ip", "binary" -> true;
                default -> false;
            };
            case IndexMetadata.DateTimeType date -> date.formats().isEmpty();
            case IndexMetadata.ScaledFloatType _ -> true;
            default -> false;
        };
    }
}
