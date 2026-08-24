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
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforced;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.APPROXIMATE;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.EXACT;
import static io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Enforcement.PREFILTER;
import static java.util.Objects.requireNonNull;

/**
 * Permanent semantic result of translating a connector-owned predicate subtree.
 *
 * <p>{@code remaining} is predicate state that this planner does not own and may still be offered to the legacy
 * compatibility boundary. {@code residual} is predicate state that this planner does own, but Trino must re-check
 * after Elasticsearch has applied a safe candidate predicate. Keeping these concepts separate is required for
 * correct boolean composition, especially OR.</p>
 */
record ElasticsearchPredicateTranslation<R>(
        Optional<ElasticsearchRemotePredicate> remotePredicate,
        Optional<Enforcement> enforcement,
        Optional<R> remaining,
        Optional<R> residual,
        Reason reason)
{
    enum Reason
    {
        NOOP,
        EXACT_DOMAIN,
        EXACT_ARRAY,
        EXACT_LIKE,
        EXACT_REGEXP,
        EXACT_PREFIX,
        FULL_TEXT_SAFE_PREFILTER,
        FULL_TEXT_UNSAFE_APPROXIMATE,
        BOOLEAN_AND,
        BOOLEAN_OR,
        BOOLEAN_NOT_UNPROVEN,
        UNSUPPORTED_DOMAIN,
        UNSUPPORTED_EXPRESSION,
    }

    ElasticsearchPredicateTranslation
    {
        remotePredicate = requireNonNull(remotePredicate, "remotePredicate is null");
        enforcement = requireNonNull(enforcement, "enforcement is null");
        remaining = requireNonNull(remaining, "remaining is null");
        residual = requireNonNull(residual, "residual is null");
        requireNonNull(reason, "reason is null");

        checkArgument(remotePredicate.isPresent() == enforcement.isPresent(), "remote predicate and enforcement must be present together");
        if (enforcement.isPresent() && enforcement.orElseThrow() == EXACT) {
            checkArgument(remaining.isEmpty() && residual.isEmpty(), "EXACT translation cannot have remaining or residual state");
        }
        if (enforcement.isPresent() && enforcement.orElseThrow() == PREFILTER) {
            checkArgument(remaining.isPresent() || residual.isPresent(), "PREFILTER translation requires remaining or residual state");
        }
        if (remotePredicate.isEmpty()) {
            checkArgument(residual.isEmpty(), "translation without a remote predicate cannot have connector-owned residual state");
        }
    }

    static <R> ElasticsearchPredicateTranslation<R> exact(ElasticsearchRemotePredicate predicate, Reason reason)
    {
        requireNonNull(predicate, "predicate is null");
        checkArgument(predicate.enforcement() == EXACT, "exact predicate has non-exact enforcement");
        return new ElasticsearchPredicateTranslation<>(
                Optional.of(predicate),
                Optional.of(EXACT),
                Optional.empty(),
                Optional.empty(),
                reason);
    }

    static <R> ElasticsearchPredicateTranslation<R> prefilter(ElasticsearchRemotePredicate predicate, R residual, Reason reason)
    {
        return new ElasticsearchPredicateTranslation<>(
                Optional.of(enforce(predicate, PREFILTER)),
                Optional.of(PREFILTER),
                Optional.empty(),
                Optional.of(requireNonNull(residual, "residual is null")),
                reason);
    }

    static <R> ElasticsearchPredicateTranslation<R> approximate(ElasticsearchRemotePredicate predicate, Reason reason)
    {
        return new ElasticsearchPredicateTranslation<>(
                Optional.of(enforce(predicate, APPROXIMATE)),
                Optional.of(APPROXIMATE),
                Optional.empty(),
                Optional.empty(),
                reason);
    }

    static <R> ElasticsearchPredicateTranslation<R> unsupported(R remaining, Reason reason)
    {
        return new ElasticsearchPredicateTranslation<>(
                Optional.empty(),
                Optional.empty(),
                Optional.of(requireNonNull(remaining, "remaining is null")),
                Optional.empty(),
                reason);
    }

    static <R> ElasticsearchPredicateTranslation<R> noop(Reason reason)
    {
        return new ElasticsearchPredicateTranslation<>(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                reason);
    }

    static <R> ElasticsearchPredicateTranslation<R> composed(
            Optional<ElasticsearchRemotePredicate> predicate,
            Optional<Enforcement> enforcement,
            Optional<R> remaining,
            Optional<R> residual,
            Reason reason)
    {
        return new ElasticsearchPredicateTranslation<>(predicate, enforcement, remaining, residual, reason);
    }

    private static ElasticsearchRemotePredicate enforce(ElasticsearchRemotePredicate predicate, Enforcement enforcement)
    {
        requireNonNull(predicate, "predicate is null");
        requireNonNull(enforcement, "enforcement is null");
        if (enforcement == EXACT) {
            return predicate;
        }
        if (predicate instanceof Enforced enforced && enforced.enforcement() == enforcement) {
            return predicate;
        }
        return new Enforced(predicate, enforcement);
    }
}
