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

import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Bound;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Range;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Value;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.ValueType.DOUBLE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.ValueType.LONG;
import static java.util.Objects.requireNonNull;

/**
 * Canonicalizes Remote Predicate IR without changing predicate semantics.
 *
 * <p>This is the permanent semantic normalization layer shared by planner composition, legacy canonicalization and
 * dynamic filtering. Deterministic rewrites such as flattening, deduplication and compatible exact-range intersection
 * belong here. Resource-policy-dependent rewrites, such as compacting a large OR of terms into bounded {@code terms}
 * batches, belong to the predicate composer.</p>
 */
final class ElasticsearchRemotePredicateNormalizer
{
    private ElasticsearchRemotePredicateNormalizer() {}

    static Optional<ElasticsearchRemotePredicate> and(List<ElasticsearchRemotePredicate> predicates)
    {
        requireNonNull(predicates, "predicates is null");
        List<ElasticsearchRemotePredicate> flattened = new ArrayList<>();
        for (ElasticsearchRemotePredicate predicate : predicates) {
            addConjunct(flattened, normalize(requireNonNull(predicate, "predicate is null")));
        }
        List<ElasticsearchRemotePredicate> normalized = mergeCompatibleNumericRanges(flattened);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if (normalized.size() == 1) {
            return Optional.of(normalized.getFirst());
        }
        return Optional.of(new ElasticsearchRemotePredicate.And(normalized));
    }

    static Optional<ElasticsearchRemotePredicate> or(List<ElasticsearchRemotePredicate> predicates)
    {
        requireNonNull(predicates, "predicates is null");
        List<ElasticsearchRemotePredicate> flattened = new ArrayList<>();
        for (ElasticsearchRemotePredicate predicate : predicates) {
            addDisjunct(flattened, normalize(requireNonNull(predicate, "predicate is null")));
        }
        if (flattened.isEmpty()) {
            return Optional.empty();
        }
        if (flattened.size() == 1) {
            return Optional.of(flattened.getFirst());
        }
        return Optional.of(new ElasticsearchRemotePredicate.Or(flattened));
    }

    static ElasticsearchRemotePredicate normalize(ElasticsearchRemotePredicate predicate)
    {
        requireNonNull(predicate, "predicate is null");
        return switch (predicate) {
            case ElasticsearchRemotePredicate.And and -> and(and.predicates()).orElseThrow();
            case ElasticsearchRemotePredicate.Or or -> or(or.predicates()).orElseThrow();
            case ElasticsearchRemotePredicate.Not not -> new ElasticsearchRemotePredicate.Not(normalize(not.predicate()));
            case ElasticsearchRemotePredicate.Enforced enforced -> new ElasticsearchRemotePredicate.Enforced(
                    normalize(enforced.predicate()),
                    enforced.enforcement());
            default -> predicate;
        };
    }

    private static void addConjunct(List<ElasticsearchRemotePredicate> conjuncts, ElasticsearchRemotePredicate predicate)
    {
        if (predicate instanceof ElasticsearchRemotePredicate.And and) {
            and.predicates().forEach(child -> addConjunct(conjuncts, child));
            return;
        }
        if (!conjuncts.contains(predicate)) {
            conjuncts.add(predicate);
        }
    }

    private static void addDisjunct(List<ElasticsearchRemotePredicate> disjuncts, ElasticsearchRemotePredicate predicate)
    {
        if (predicate instanceof ElasticsearchRemotePredicate.Or or) {
            or.predicates().forEach(child -> addDisjunct(disjuncts, child));
            return;
        }
        if (!disjuncts.contains(predicate)) {
            disjuncts.add(predicate);
        }
    }

    private static List<ElasticsearchRemotePredicate> mergeCompatibleNumericRanges(List<ElasticsearchRemotePredicate> predicates)
    {
        List<ElasticsearchRemotePredicate> result = new ArrayList<>();
        for (ElasticsearchRemotePredicate predicate : predicates) {
            if (!(predicate instanceof Range range)) {
                result.add(predicate);
                continue;
            }

            boolean merged = false;
            for (int index = 0; index < result.size(); index++) {
                ElasticsearchRemotePredicate existing = result.get(index);
                if (!(existing instanceof Range existingRange) || !existingRange.field().equals(range.field())) {
                    continue;
                }
                Optional<Range> intersection = intersectCompatibleNumericRanges(existingRange, range);
                if (intersection.isPresent()) {
                    result.set(index, intersection.orElseThrow());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(range);
            }
        }
        return List.copyOf(result);
    }

    private static Optional<Range> intersectCompatibleNumericRanges(Range first, Range second)
    {
        Optional<ValueType> firstType = numericRangeType(first);
        Optional<ValueType> secondType = numericRangeType(second);
        if (firstType.isEmpty() || !firstType.equals(secondType)) {
            return Optional.empty();
        }

        ValueType type = firstType.orElseThrow();
        Optional<Bound> lower = strongerLower(first.lower(), second.lower(), type);
        Optional<Bound> upper = strongerUpper(first.upper(), second.upper(), type);
        if (lower.isPresent() && upper.isPresent()) {
            int comparison = compare(lower.orElseThrow().value(), upper.orElseThrow().value(), type);
            if (comparison > 0 || (comparison == 0 && (!lower.orElseThrow().inclusive() || !upper.orElseThrow().inclusive()))) {
                return Optional.empty();
            }
        }
        return Optional.of(new Range(first.field(), lower, upper));
    }

    private static Optional<ValueType> numericRangeType(Range range)
    {
        ValueType type = null;
        for (Bound bound : List.of(range.lower(), range.upper()).stream().flatMap(Optional::stream).toList()) {
            ValueType boundType = bound.value().type();
            if (boundType != LONG && boundType != DOUBLE) {
                return Optional.empty();
            }
            if (type != null && type != boundType) {
                return Optional.empty();
            }
            type = boundType;
        }
        return Optional.ofNullable(type);
    }

    private static Optional<Bound> strongerLower(Optional<Bound> first, Optional<Bound> second, ValueType type)
    {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        int comparison = compare(first.orElseThrow().value(), second.orElseThrow().value(), type);
        if (comparison > 0) {
            return first;
        }
        if (comparison < 0) {
            return second;
        }
        return Optional.of(new Bound(first.orElseThrow().value(), first.orElseThrow().inclusive() && second.orElseThrow().inclusive()));
    }

    private static Optional<Bound> strongerUpper(Optional<Bound> first, Optional<Bound> second, ValueType type)
    {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        int comparison = compare(first.orElseThrow().value(), second.orElseThrow().value(), type);
        if (comparison < 0) {
            return first;
        }
        if (comparison > 0) {
            return second;
        }
        return Optional.of(new Bound(first.orElseThrow().value(), first.orElseThrow().inclusive() && second.orElseThrow().inclusive()));
    }

    private static int compare(Value first, Value second, ValueType type)
    {
        return switch (type) {
            case LONG -> Long.compare(Long.parseLong(first.value()), Long.parseLong(second.value()));
            case DOUBLE -> Double.compare(Double.parseDouble(first.value()), Double.parseDouble(second.value()));
            default -> throw new IllegalArgumentException("Unsupported numeric range type: " + type);
        };
    }
}
