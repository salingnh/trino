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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record IndexMetadata(ObjectType schema)
{
    public IndexMetadata
    {
        requireNonNull(schema, "schema is null");
    }

    public record Field(boolean asRawJson, boolean isArray, String name, Type type, boolean supportsQueryShapes, boolean predicateMappingSafe, Optional<InferredMapping> inferredMapping)
    {
        public Field(boolean asRawJson, boolean isArray, String name, Type type, boolean supportsQueryShapes, boolean predicateMappingSafe)
        {
            this(asRawJson, isArray, name, type, supportsQueryShapes, predicateMappingSafe, Optional.empty());
        }

        public Field(boolean asRawJson, boolean isArray, String name, Type type)
        {
            this(asRawJson, isArray, name, type, true, true);
        }

        public Field(boolean asRawJson, boolean isArray, String name, Type type, boolean supportsQueryShapes)
        {
            this(asRawJson, isArray, name, type, supportsQueryShapes, true);
        }

        public Field
        {
            checkArgument(
                    !asRawJson || !isArray,
                    "A column, (%s) cannot be declared as a Trino array and also be rendered as json.",
                    name);
            requireNonNull(name, "name is null");
            requireNonNull(type, "type is null");
            requireNonNull(inferredMapping, "inferredMapping is null");
        }
    }

    public enum InferredScalarMode
    {
        SOURCE,
        MAPPED_SOURCE,
        DOC_VALUES,
        DOC_VALUES_IGNORE_MALFORMED;

        public boolean usesDocValues()
        {
            return this == DOC_VALUES || this == DOC_VALUES_IGNORE_MALFORMED;
        }
    }

    public record InferredMapping(int arrayDepth, Optional<String> primitiveType, Set<String> observedTypes, boolean integersFitDouble, InferredScalarMode scalarMode)
    {
        public InferredMapping(int arrayDepth, Optional<String> primitiveType, Set<String> observedTypes, boolean integersFitDouble)
        {
            this(arrayDepth, primitiveType, observedTypes, integersFitDouble, InferredScalarMode.SOURCE);
        }

        public InferredMapping
        {
            requireNonNull(primitiveType, "primitiveType is null");
            requireNonNull(scalarMode, "scalarMode is null");
            observedTypes = ImmutableSet.copyOf(requireNonNull(observedTypes, "observedTypes is null"));
        }
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DateTimeType.class, name = "date_time"),
            @JsonSubTypes.Type(value = ObjectType.class, name = "object"),
            @JsonSubTypes.Type(value = PrimitiveType.class, name = "primitive"),
            @JsonSubTypes.Type(value = ScaledFloatType.class, name = "scaled_float"),
    })
    public interface Type
    {
        default boolean sourceValueSemanticsExact()
        {
            return true;
        }
    }

    public record PrimitiveType(String name, Optional<String> keyword, boolean aggregatable, boolean sourceValueSemanticsExact, boolean indexed)
            implements Type
    {
        public PrimitiveType(String name, Optional<String> keyword, boolean aggregatable, boolean sourceValueSemanticsExact)
        {
            this(name, keyword, aggregatable, sourceValueSemanticsExact, true);
        }

        public PrimitiveType(String name, Optional<String> keyword, boolean aggregatable)
        {
            this(name, keyword, aggregatable, true);
        }

        public PrimitiveType(String name, Optional<String> keyword)
        {
            this(name, keyword, isDocValueType(name) || keyword.isPresent(), true);
        }

        public PrimitiveType(String name)
        {
            this(name, Optional.empty());
        }

        public PrimitiveType
        {
            requireNonNull(name, "name is null");
            requireNonNull(keyword, "keyword is null");
        }

        public static boolean isDocValueType(String name)
        {
            return switch (name) {
                case "byte", "short", "integer", "long", "float", "half_float", "double", "keyword", "boolean", "ip" -> true;
                default -> false;
            };
        }
    }

    public record DateTimeType(List<String> formats, boolean aggregatable, boolean sourceValueSemanticsExact)
            implements Type
    {
        public DateTimeType(List<String> formats, boolean aggregatable)
        {
            this(formats, aggregatable, true);
        }

        public DateTimeType(List<String> formats)
        {
            this(formats, true, true);
        }

        public DateTimeType
        {
            requireNonNull(formats, "formats is null");
            formats = ImmutableList.copyOf(formats);
        }
    }

    public record ObjectType(List<Field> fields, Optional<ObjectArrayMapping> arrayMapping)
            implements Type
    {
        public ObjectType(List<Field> fields)
        {
            this(fields, Optional.empty());
        }

        public ObjectType
        {
            fields = ImmutableList.copyOf(requireNonNull(fields, "fields is null"));
            requireNonNull(arrayMapping, "arrayMapping is null");
        }
    }

    public record ObjectArrayMapping(boolean nested, Set<String> indexedFields)
    {
        public ObjectArrayMapping
        {
            indexedFields = ImmutableSet.copyOf(requireNonNull(indexedFields, "indexedFields is null"));
        }
    }

    public record ScaledFloatType(double scale, boolean aggregatable, boolean sourceValueSemanticsExact)
            implements Type
    {
        public ScaledFloatType(double scale, boolean aggregatable)
        {
            this(scale, aggregatable, true);
        }

        public ScaledFloatType(double scale)
        {
            this(scale, true, true);
        }
    }
}
