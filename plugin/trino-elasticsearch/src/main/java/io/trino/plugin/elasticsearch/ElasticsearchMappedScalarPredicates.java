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

import com.google.common.collect.ImmutableList;
import io.trino.plugin.elasticsearch.client.IndexMetadata.DateTimeType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.ScaledFloatType;
import io.trino.plugin.elasticsearch.decoders.InferredValueDecoder;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Bound;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;

import java.math.BigDecimal;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchRemotePredicateTranslator.disjunction;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.Timestamps.MICROSECONDS_PER_MILLISECOND;
import static java.lang.Math.floorDiv;

final class ElasticsearchMappedScalarPredicates
{
    private ElasticsearchMappedScalarPredicates() {}

    static boolean handles(ElasticsearchColumnHandle column)
    {
        return column.inferred() &&
                column.decoderDescriptor() instanceof InferredValueDecoder.Descriptor inferred && inferred.scalarMode().usesDocValues() &&
                switch (column.elasticsearchType()) {
                    case DateTimeType _, ScaledFloatType _ -> true;
                    case PrimitiveType primitive -> column.type().equals(DOUBLE) && (primitive.name().equals("float") || primitive.name().equals("half_float"));
                    default -> false;
                };
    }

    static Optional<ElasticsearchRemotePredicate> translate(ElasticsearchColumnHandle column, Domain domain)
    {
        String field = column.predicateName();
        if (domain.isAll()) {
            return Optional.empty();
        }
        ElasticsearchRemotePredicate exists = new ElasticsearchRemotePredicate.Exists(field);
        ElasticsearchRemotePredicate missing = new ElasticsearchRemotePredicate.Not(exists);
        if (domain.getValues().isNone()) {
            return domain.isNullAllowed() ? Optional.of(missing) : Optional.empty();
        }
        if (domain.getValues().isAll()) {
            return Optional.of(exists);
        }

        boolean date = column.elasticsearchType() instanceof DateTimeType;
        ImmutableList.Builder<ElasticsearchRemotePredicate> alternatives = ImmutableList.builder();
        for (Range range : domain.getValues().getRanges().getOrderedRanges()) {
            Optional<Bound> lower = range.getLowValue().flatMap(value -> bound(column, value, range.isLowInclusive(), true));
            Optional<Bound> upper = range.getHighValue().flatMap(value -> bound(column, value, range.isHighInclusive(), false));
            if ((!range.isLowUnbounded() && lower.isEmpty()) || (!range.isHighUnbounded() && upper.isEmpty())) {
                return Optional.empty();
            }
            alternatives.add(new ElasticsearchRemotePredicate.Range(field, lower, upper, date ? Optional.of("epoch_millis") : Optional.empty()));
        }
        if (domain.isNullAllowed()) {
            alternatives.add(missing);
        }
        return disjunction(alternatives.build());
    }

    private static Optional<Bound> bound(ElasticsearchColumnHandle column, Object value, boolean inclusive, boolean lower)
    {
        return switch (column.elasticsearchType()) {
            case DateTimeType _ -> Optional.of(new Bound(Long.toString(floorDiv((long) value, MICROSECONDS_PER_MILLISECOND)), inclusive));
            case ScaledFloatType scaled -> scaledBound((double) value, scaled.scale(), inclusive, lower);
            case PrimitiveType primitive -> floatingBound((double) value, primitive.name().equals("half_float"), inclusive, lower);
            default -> throw new IllegalArgumentException("Unexpected mapped scalar type: " + column.elasticsearchType());
        };
    }

    private static Optional<Bound> floatingBound(double value, boolean half, boolean inclusive, boolean lower)
    {
        float rounded = (float) value;
        if (half) {
            rounded = Float.float16ToFloat(Float.floatToFloat16(rounded));
        }
        if (!Float.isFinite(rounded)) {
            return Optional.empty();
        }
        boolean includeRounded = rounded == value ? inclusive : (lower ? rounded > value : rounded < value);
        return Optional.of(new Bound((double) rounded, includeRounded));
    }

    private static Optional<Bound> scaledBound(double value, double scale, boolean inclusive, boolean lower)
    {
        double estimate = value * scale;
        // A midpoint on the scaled grid needs at least one spare precision bit on either side.
        if (!Double.isFinite(estimate) || !Double.isFinite(1 / scale) || Math.abs(estimate) >= 0x1.0p49) {
            return Optional.empty();
        }

        // Find the change in truth on adjacent stored longs. Both the doc-value
        // and division representations must imply the same boundary.
        long nearest = Math.round(estimate);
        for (long key = nearest - 2; key <= nearest + 2; key++) {
            long outside = lower ? key - 1 : key + 1;
            if (scaledMatches(key * (1 / scale), value, inclusive, lower)
                    && !scaledMatches(outside * (1 / scale), value, inclusive, lower)
                    && scaledMatches(key / scale, value, inclusive, lower)
                    && !scaledMatches(outside / scale, value, inclusive, lower)) {
                // Use a point between stored values. Elasticsearch applies ceil to lower
                // bounds and floor to upper bounds. Verify the wire value rounds to the
                // intended long under both arithmetic and decimal scaling.
                double midpoint = (key + (lower ? -0.5 : 0.5)) / scale;
                if (!Double.isFinite(midpoint)) {
                    return Optional.empty();
                }
                double multiplied = midpoint * scale;
                double decimal = BigDecimal.valueOf(midpoint).multiply(BigDecimal.valueOf(scale)).doubleValue();
                if (between(multiplied, key, outside) && between(decimal, key, outside)) {
                    return Optional.of(new Bound(midpoint, true));
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean scaledMatches(double indexed, double bound, boolean inclusive, boolean lower)
    {
        return (inclusive && indexed == bound) || (lower ? indexed > bound : indexed < bound);
    }

    private static boolean between(double value, long first, long second)
    {
        return value > Math.min(first, second) && value < Math.max(first, second);
    }
}
