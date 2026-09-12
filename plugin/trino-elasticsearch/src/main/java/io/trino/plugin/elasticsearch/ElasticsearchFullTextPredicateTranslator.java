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

import io.airlift.slice.Slice;
import io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchExpressionRewrite;
import io.trino.plugin.elasticsearch.expression.ElasticsearchExpressionTranslator;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.VarcharType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.SliceUtf8.countCodePoints;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_LIKE;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_PREFIX;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_DISABLED;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_SAFE_PREFILTER;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_SAFE_UNPROVEN;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.FULL_TEXT_UNSAFE_APPROXIMATE;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.DISABLED;
import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LIKE_FUNCTION_NAME;
import static java.util.Objects.requireNonNull;

/**
 * Shared scalar and ARRAY-element full-text translation rules.
 *
 * <p>The caller supplies the semantic source expression. Scalar callers use the LIKE or regexp call itself, while
 * ARRAY callers use the enclosing {@code any_match} call so unsupported element translations keep the complete lambda
 * local. This class owns strategy selection; it does not introduce a second predicate representation.</p>
 */
final class ElasticsearchFullTextPredicateTranslator
{
    private static final ElasticsearchExpressionTranslator EXPRESSION_TRANSLATOR = new ElasticsearchExpressionTranslator();

    private ElasticsearchFullTextPredicateTranslator() {}

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateLike(
            ConnectorSession session,
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        return translateLike(session, expression, assignments, fullTextMode, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateLike(
            ConnectorSession session,
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(policy, "policy is null");
        if (!(expression instanceof Call call) || !ElasticsearchMetadata.isSupportedLikeCall(call)) {
            return Optional.empty();
        }

        List<ConnectorExpression> arguments = call.getArguments();
        if (!(arguments.getFirst() instanceof Variable variable)) {
            return Optional.of(ElasticsearchPredicateTranslation.residual(expression, UNSUPPORTED_EXPRESSION));
        }
        ColumnHandle assigned = assignments.get(variable.getName());
        if (!(assigned instanceof ElasticsearchColumnHandle column) || !(column.type() instanceof VarcharType)) {
            return Optional.of(ElasticsearchPredicateTranslation.residual(expression, UNSUPPORTED_EXPRESSION));
        }

        return Optional.of(translateLikeCall(
                requireNonNull(session, "session is null"),
                expression,
                call,
                column,
                fullTextMode,
                FULL_TEXT_UNSAFE_APPROXIMATE,
                policy));
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateLikeElement(
            ConnectorSession session,
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason)
    {
        return translateLikeElement(
                session,
                source,
                call,
                element,
                column,
                fullTextMode,
                approximateReason,
                ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateLikeElement(
            ConnectorSession session,
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(session, "session is null");
        requireNonNull(source, "source is null");
        requireNonNull(call, "call is null");
        requireNonNull(element, "element is null");
        requireNonNull(column, "column is null");
        requireNonNull(fullTextMode, "fullTextMode is null");
        requireNonNull(approximateReason, "approximateReason is null");
        requireNonNull(policy, "policy is null");

        if (!LIKE_FUNCTION_NAME.equals(call.getFunctionName())) {
            return Optional.empty();
        }
        if (!ElasticsearchMetadata.isSupportedLikeCall(call)
                || !isLambdaElement(call, element)
                || !isAnalyzedTextOnly(column)) {
            return Optional.of(ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION));
        }
        return Optional.of(translateLikeCall(session, source, call, column, fullTextMode, approximateReason, policy));
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateRegexp(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        return translateRegexp(expression, assignments, fullTextMode, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateRegexp(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(policy, "policy is null");
        if (!(expression instanceof Call call) || !call.getFunctionName().getName().equals("regexp_like")) {
            return Optional.empty();
        }

        List<ConnectorExpression> arguments = call.getArguments();
        if (arguments.size() != 2 || !(arguments.getFirst() instanceof Variable variable)) {
            return Optional.of(ElasticsearchPredicateTranslation.residual(expression, UNSUPPORTED_EXPRESSION));
        }

        ColumnHandle assigned = assignments.get(variable.getName());
        if (!(assigned instanceof ElasticsearchColumnHandle column) || !(column.type() instanceof VarcharType)) {
            return Optional.of(ElasticsearchPredicateTranslation.residual(expression, UNSUPPORTED_EXPRESSION));
        }
        return Optional.of(translateRegexpCall(expression, call, column, fullTextMode, FULL_TEXT_UNSAFE_APPROXIMATE, policy));
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateRegexpElement(
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason)
    {
        return translateRegexpElement(
                source,
                call,
                element,
                column,
                fullTextMode,
                approximateReason,
                ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateRegexpElement(
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(source, "source is null");
        requireNonNull(call, "call is null");
        requireNonNull(element, "element is null");
        requireNonNull(column, "column is null");
        requireNonNull(fullTextMode, "fullTextMode is null");
        requireNonNull(approximateReason, "approximateReason is null");
        requireNonNull(policy, "policy is null");

        if (!call.getFunctionName().getName().equals("regexp_like")) {
            return Optional.empty();
        }
        if (!isLambdaElement(call, element) || !isAnalyzedTextOnly(column)) {
            return Optional.of(ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION));
        }
        return Optional.of(translateRegexpCall(source, call, column, fullTextMode, approximateReason, policy));
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translatePrefix(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode)
    {
        return translatePrefix(expression, assignments, fullTextMode, ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translatePrefix(
            ConnectorExpression expression,
            Map<String, ColumnHandle> assignments,
            FullTextPushdownMode fullTextMode,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(policy, "policy is null");
        if (!(expression instanceof Call call)) {
            return Optional.empty();
        }

        List<ConnectorExpression> arguments = call.getArguments();
        if (call.getFunctionName().getName().equals("starts_with")) {
            if (arguments.size() != 2
                    || !(arguments.getFirst() instanceof Variable variable)
                    || !(arguments.get(1) instanceof Constant constant)
                    || !(constant.getValue() instanceof Slice prefix)) {
                return Optional.of(ElasticsearchPredicateTranslation.unsupported(expression, UNSUPPORTED_EXPRESSION));
            }
            ColumnHandle assigned = assignments.get(variable.getName());
            if (!(assigned instanceof ElasticsearchColumnHandle column) || !(column.type() instanceof VarcharType)) {
                return Optional.of(ElasticsearchPredicateTranslation.unsupported(expression, UNSUPPORTED_EXPRESSION));
            }
            return Optional.of(translateStartsWith(
                    expression,
                    column,
                    prefix,
                    fullTextMode,
                    FULL_TEXT_UNSAFE_APPROXIMATE,
                    policy));
        }

        if (!EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName()) || arguments.size() != 2) {
            return Optional.empty();
        }

        for (int index = 0; index < 2; index++) {
            if (!(arguments.get(index) instanceof Call inner)
                    || !(inner.getFunctionName().getName().equals("substr") || inner.getFunctionName().getName().equals("substring"))
                    || inner.getArguments().size() != 3
                    || !(inner.getArguments().getFirst() instanceof Variable variable)
                    || !(inner.getArguments().get(1) instanceof Constant start)
                    || !(start.getValue() instanceof Long from)
                    || from != 1L
                    || !(inner.getArguments().get(2) instanceof Constant length)
                    || !(length.getValue() instanceof Long count)
                    || !(arguments.get(1 - index) instanceof Constant constant)
                    || !(constant.getValue() instanceof Slice prefix)
                    || count != countCodePoints(prefix)) {
                continue;
            }
            ColumnHandle assigned = assignments.get(variable.getName());
            if (assigned instanceof ElasticsearchColumnHandle column && supportsExactLikePushdown(column)) {
                return Optional.of(ElasticsearchPredicateTranslation.exact(
                        new ElasticsearchRemotePredicate.Prefix(column.predicateName(), prefix.toStringUtf8()),
                        EXACT_PREFIX));
            }
        }
        return Optional.empty();
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateStartsWithElement(
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason)
    {
        return translateStartsWithElement(
                source,
                call,
                element,
                column,
                fullTextMode,
                approximateReason,
                ElasticsearchPredicateCompositionPolicy.DEFAULT);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateStartsWithElement(
            ConnectorExpression source,
            Call call,
            Variable element,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        requireNonNull(source, "source is null");
        requireNonNull(call, "call is null");
        requireNonNull(element, "element is null");
        requireNonNull(column, "column is null");
        requireNonNull(fullTextMode, "fullTextMode is null");
        requireNonNull(approximateReason, "approximateReason is null");
        requireNonNull(policy, "policy is null");

        if (!call.getFunctionName().getName().equals("starts_with")) {
            return Optional.empty();
        }
        List<ConnectorExpression> arguments = call.getArguments();
        if (arguments.size() != 2
                || !isLambdaElement(call, element)
                || !(arguments.get(1) instanceof Constant constant)
                || !(constant.getValue() instanceof Slice prefix)
                || !isAnalyzedTextOnly(column)) {
            return Optional.of(ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION));
        }
        return Optional.of(translateStartsWith(source, column, prefix, fullTextMode, approximateReason, policy));
    }

    static boolean isAnalyzedTextOnly(ElasticsearchColumnHandle column)
    {
        return column != null
                && !column.supportsPredicates()
                && column.elasticsearchType() instanceof PrimitiveType primitiveType
                && primitiveType.name().equalsIgnoreCase("text")
                && primitiveType.keyword().isEmpty();
    }

    private static boolean isLambdaElement(Call call, Variable element)
    {
        return !call.getArguments().isEmpty()
                && call.getArguments().getFirst() instanceof Variable variable
                && variable.equals(element);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateLikeCall(
            ConnectorSession session,
            ConnectorExpression source,
            Call call,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        List<ConnectorExpression> arguments = call.getArguments();
        if (arguments.size() < 2
                || !(arguments.get(1) instanceof Constant patternConstant)
                || !(patternConstant.getValue() instanceof Slice pattern)) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }

        Optional<Slice> escape = Optional.empty();
        if (arguments.size() == 3) {
            if (!(arguments.get(2) instanceof Constant constant) || !(constant.getValue() instanceof Slice escapeSlice)) {
                return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
            }
            escape = Optional.of(escapeSlice);
        }

        if (supportsExactLikePushdown(column)) {
            Optional<String> prefix = ElasticsearchMetadata.likePrefix(pattern, escape);
            ElasticsearchRemotePredicate predicate = prefix
                    .<ElasticsearchRemotePredicate>map(value -> new ElasticsearchRemotePredicate.Prefix(column.predicateName(), value))
                    .orElse(new ElasticsearchRemotePredicate.Regexp(column.predicateName(), ElasticsearchMetadata.likeToRegexp(pattern, escape)));
            return ElasticsearchPredicateTranslation.exact(predicate, EXACT_LIKE);
        }

        if (!isAnalyzedTextOnly(column)) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }
        if (fullTextMode == DISABLED) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_DISABLED);
        }
        if (fullTextMode == SAFE) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_SAFE_UNPROVEN);
        }

        Optional<ElasticsearchExpressionRewrite> rewrite = EXPRESSION_TRANSLATOR.rewriteAnalyzedTextLike(session, call, column);
        if (rewrite.isPresent()) {
            ElasticsearchExpressionRewrite translated = rewrite.orElseThrow();
            return switch (translated.queryType()) {
                case MATCH_PHRASE -> approximateWithinBudget(
                        source,
                        new ElasticsearchRemotePredicate.MatchPhrase(translated.column().remoteName(), translated.value()),
                        approximateReason,
                        policy);
            };
        }

        Optional<String> prefix = ElasticsearchMetadata.likePrefix(pattern, escape);
        if (prefix.isPresent()) {
            return approximateWithinBudget(
                    source,
                    new ElasticsearchRemotePredicate.MatchPhrasePrefix(column.remoteName(), prefix.orElseThrow()),
                    approximateReason,
                    policy);
        }

        if (patternSpansTokens(pattern)) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }

        return approximateWithinBudget(
                source,
                new ElasticsearchRemotePredicate.Regexp(column.remoteName(), ElasticsearchMetadata.likeToRegexp(pattern, escape)),
                approximateReason,
                policy);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateRegexpCall(
            ConnectorExpression source,
            Call call,
            ElasticsearchColumnHandle column,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (fullTextMode == DISABLED) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_DISABLED);
        }

        List<ConnectorExpression> arguments = call.getArguments();
        if (arguments.size() != 2
                || !(arguments.get(1) instanceof Constant constant)
                || !(constant.getValue() instanceof Slice pattern)) {
            return ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION);
        }

        Optional<CasePreservingElasticsearchMetadata.RegexpTranslation> translated = CasePreservingElasticsearchMetadata.translateRegexpLike(pattern.toStringUtf8());
        if (translated.isEmpty()) {
            return ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION);
        }

        CasePreservingElasticsearchMetadata.RegexpTranslation translation = translated.orElseThrow();
        boolean safePrefilter = column.supportsPredicates() && translation.quality().safeForPrefilter();
        if (fullTextMode == SAFE && !safePrefilter) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_SAFE_UNPROVEN);
        }

        ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.Regexp(column.predicateName(), translation.pattern());
        if (!ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
            return ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION);
        }
        if (fullTextMode == SAFE) {
            return ElasticsearchPredicateTranslation.prefilter(predicate, source, FULL_TEXT_SAFE_PREFILTER);
        }
        return ElasticsearchPredicateTranslation.approximate(predicate, approximateReason);
    }

    private static ElasticsearchPredicateTranslation<ConnectorExpression> translateStartsWith(
            ConnectorExpression source,
            ElasticsearchColumnHandle column,
            Slice prefix,
            FullTextPushdownMode fullTextMode,
            Reason approximateReason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (supportsExactLikePushdown(column)) {
            return ElasticsearchPredicateTranslation.exact(
                    new ElasticsearchRemotePredicate.Prefix(column.predicateName(), prefix.toStringUtf8()),
                    EXACT_PREFIX);
        }
        if (!isAnalyzedTextOnly(column)) {
            return ElasticsearchPredicateTranslation.unsupported(source, UNSUPPORTED_EXPRESSION);
        }
        if (fullTextMode == DISABLED) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_DISABLED);
        }
        if (fullTextMode == SAFE) {
            return ElasticsearchPredicateTranslation.residual(source, FULL_TEXT_SAFE_UNPROVEN);
        }
        return approximateWithinBudget(
                source,
                new ElasticsearchRemotePredicate.MatchPhrasePrefix(column.remoteName(), prefix.toStringUtf8()),
                approximateReason,
                policy);
    }

    static <R> ElasticsearchPredicateTranslation<R> approximateWithinBudget(
            R source,
            ElasticsearchRemotePredicate predicate,
            Reason reason,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        if (!ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
            return ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION);
        }
        return ElasticsearchPredicateTranslation.approximate(predicate, reason);
    }

    private static boolean supportsExactLikePushdown(ElasticsearchColumnHandle column)
    {
        return column.elasticsearchType() instanceof PrimitiveType primitiveType
                && (primitiveType.name().equalsIgnoreCase("keyword") || primitiveType.keyword().isPresent());
    }

    private static boolean patternSpansTokens(Slice pattern)
    {
        return pattern.toStringUtf8().codePoints().anyMatch(Character::isWhitespace);
    }
}
