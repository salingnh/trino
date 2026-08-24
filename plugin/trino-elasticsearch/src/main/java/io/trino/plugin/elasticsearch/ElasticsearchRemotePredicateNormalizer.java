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
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Term;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Terms;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Canonicalizes Remote Predicate IR without changing predicate semantics.
 *
 * <p>This is the permanent normalization layer shared by planner composition, legacy canonicalization and dynamic
 * filtering. New normalizations belong here rather than in individual predicate translators.</p>
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
        if (flattened.isEmpty()) {
            return Optional.empty();
        }
        if (flattened.size() == 1) {
            return Optional.of(flattened.getFirst());
        }
        return Optional.of(new ElasticsearchRemotePredicate.And(flattened));
    }

    static Optional<ElasticsearchRemotePredicate> or(List<ElasticsearchRemotePredicate> predicates)
    {
        requireNonNull(predicates, "predicates is null");
        List<ElasticsearchRemotePredicate> flattened = new ArrayList<>();
        for (ElasticsearchRemotePredicate predicate : predicates) {
            addDisjunct(flattened, normalize(requireNonNull(predicate, "predicate is null")));
        }
        flattened = mergeExactTerms(flattened);
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

    private static List<ElasticsearchRemotePredicate> mergeExactTerms(List<ElasticsearchRemotePredicate> predicates)
    {
        Map<String, Set<Value>> valuesByField = new LinkedHashMap<>();
        Map<String, Integer> firstIndexByField = new LinkedHashMap<>();

        for (int index = 0; index < predicates.size(); index++) {
            ElasticsearchRemotePredicate predicate = predicates.get(index);
            if (predicate instanceof Term term) {
                valuesByField.computeIfAbsent(term.field(), _ -> new LinkedHashSet<>()).add(term.value());
                firstIndexByField.putIfAbsent(term.field(), index);
            }
            else if (predicate instanceof Terms terms) {
                valuesByField.computeIfAbsent(terms.field(), _ -> new LinkedHashSet<>()).addAll(terms.values());
                firstIndexByField.putIfAbsent(terms.field(), index);
            }
        }

        if (valuesByField.isEmpty()) {
            return predicates;
        }

        List<ElasticsearchRemotePredicate> result = new ArrayList<>();
        Set<String> emittedFields = new LinkedHashSet<>();
        for (int index = 0; index < predicates.size(); index++) {
            ElasticsearchRemotePredicate predicate = predicates.get(index);
            String field = switch (predicate) {
                case Term term -> term.field();
                case Terms terms -> terms.field();
                default -> null;
            };
            if (field == null) {
                result.add(predicate);
                continue;
            }
            if (index != firstIndexByField.get(field) || !emittedFields.add(field)) {
                continue;
            }
            List<Value> values = List.copyOf(valuesByField.get(field));
            if (values.size() == 1) {
                result.add(new Term(field, values.getFirst()));
            }
            else {
                result.add(new Terms(field, values));
            }
        }
        return result;
    }
}
