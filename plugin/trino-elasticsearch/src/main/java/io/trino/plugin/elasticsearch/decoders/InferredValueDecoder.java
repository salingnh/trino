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

package io.trino.plugin.elasticsearch.decoders;

import io.trino.plugin.elasticsearch.DecoderDescriptor;
import io.trino.plugin.elasticsearch.client.IndexMetadata.InferredScalarMode;
import io.trino.plugin.elasticsearch.client.SearchDocument;
import io.trino.spi.TrinoException;
import io.trino.spi.block.BlockBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static io.trino.spi.StandardErrorCode.INVALID_CAST_ARGUMENT;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.StandardErrorCode.TYPE_MISMATCH;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public final class InferredValueDecoder
        implements Decoder
{
    private final Descriptor descriptor;
    private final Decoder delegate;

    private InferredValueDecoder(Descriptor descriptor)
    {
        this.descriptor = descriptor;
        delegate = descriptor.delegate().createDecoder();
    }

    @Override
    public void decode(SearchDocument document, Supplier<Object> getter, BlockBuilder output)
    {
        Object value = getter.get();
        String actualType = valueType(value);
        if (value != null && !descriptor.acceptedTypes().contains(actualType)) {
            throw mismatch(actualType, null);
        }
        if (!descriptor.scalarMode().usesDocValues() && actualType.equals("INTEGER") && !fitsInferredNumericType((Number) value, descriptor.expectedType())) {
            throw mismatch(actualType + " outside the exact range", null);
        }
        if (descriptor.acceptedTypes().contains("ARRAY")) {
            // Errors from array elements already describe the failing member rather than its enclosing array.
            delegate.decode(document, () -> value, output);
            return;
        }
        Object decoded = descriptor.scalarMode().usesDocValues()
                ? mappedValue(document, value, actualType)
                : (descriptor.stringify() && value instanceof Boolean ? value.toString() : value);
        try {
            delegate.decode(document, () -> decoded, output);
        }
        catch (TrinoException e) {
            if (e.getErrorCode().equals(TYPE_MISMATCH.toErrorCode()) ||
                    e.getErrorCode().equals(NOT_SUPPORTED.toErrorCode()) ||
                    e.getErrorCode().equals(INVALID_CAST_ARGUMENT.toErrorCode())) {
                throw mismatch(actualType, e);
            }
            throw e;
        }
        catch (DateTimeException | IllegalArgumentException e) {
            throw mismatch(actualType, e);
        }
    }

    private Object mappedValue(SearchDocument document, Object sourceValue, String actualType)
    {
        List<Object> values = document.fields().get(descriptor.path());
        if (values == null || values.isEmpty()) {
            if (sourceValue == null || descriptor.scalarMode() == InferredScalarMode.DOC_VALUES_IGNORE_MALFORMED || isEmptyNumericValue(sourceValue)) {
                return null;
            }
            throw mismatch(actualType + " without an indexed value", null);
        }
        if (values.size() != 1) {
            throw mismatch("multiple indexed values", null);
        }
        Object indexedValue = values.getFirst();
        if (descriptor.expectedType().equals("BOOLEAN") && indexedValue instanceof Number number) {
            if (number.doubleValue() == 0) {
                return false;
            }
            if (number.doubleValue() == 1) {
                return true;
            }
            throw mismatch("invalid indexed boolean", null);
        }
        return indexedValue;
    }

    private boolean isEmptyNumericValue(Object sourceValue)
    {
        return "".equals(sourceValue) && switch (descriptor.delegate()) {
            case TinyintDecoder.Descriptor _, SmallintDecoder.Descriptor _, IntegerDecoder.Descriptor _, BigintDecoder.Descriptor _,
                 RealDecoder.Descriptor _, DoubleDecoder.Descriptor _ -> true;
            default -> false;
        };
    }

    private static boolean fitsInferredNumericType(Number number, String type)
    {
        return switch (type) {
            case "BIGINT" -> !(number instanceof BigInteger bigInteger) || bigInteger.bitLength() < Long.SIZE;
            case "DOUBLE" -> Double.isFinite(number.doubleValue()) &&
                    new BigDecimal(number.doubleValue()).compareTo(new BigDecimal(number.toString())) == 0;
            default -> true;
        };
    }

    private static String valueType(Object value)
    {
        return switch (value) {
            case null -> "NULL";
            case List<?> _ -> "ARRAY";
            case Map<?, ?> _ -> "OBJECT";
            case String _ -> "STRING";
            case Boolean _ -> "BOOLEAN";
            case Double _, Float _, BigDecimal _ -> "REAL";
            case Number _ -> "INTEGER";
            default -> value.getClass().getSimpleName();
        };
    }

    private TrinoException mismatch(String actualType, Throwable cause)
    {
        return new TrinoException(TYPE_MISMATCH, format("Expected %s at Elasticsearch field '%s', found %s", descriptor.expectedType(), descriptor.path(), actualType), cause);
    }

    public record Descriptor(String path, String expectedType, Set<String> acceptedTypes, boolean stringify, InferredScalarMode scalarMode, DecoderDescriptor delegate)
            implements DecoderDescriptor
    {
        public Descriptor
        {
            requireNonNull(path, "path is null");
            requireNonNull(expectedType, "expectedType is null");
            acceptedTypes = Set.copyOf(acceptedTypes);
            requireNonNull(scalarMode, "scalarMode is null");
            requireNonNull(delegate, "delegate is null");
        }

        @Override
        public Decoder createDecoder()
        {
            return new InferredValueDecoder(this);
        }
    }
}
