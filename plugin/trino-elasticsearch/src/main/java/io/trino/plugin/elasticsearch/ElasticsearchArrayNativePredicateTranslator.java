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
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.block.Block;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.FullTextPushdownMode.SAFE;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.ARRAY_CONSTRUCTOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.BETWEEN_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.CAST_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IN_PREDICATE_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.TypeUtils.readNativeValue;

/**
 * Native TRUE-set compilation inside a positive any_match. Boolean operations on a single integral element happen
 * before document queries are emitted. In particular, neither document-level AND nor must_not implements arbitrary
 * element-level conjunction or negation. TRUE and FALSE sets are separate because NOT must not turn UNKNOWN into TRUE.
 */
final class ElasticsearchArrayNativePredicateTranslator
{
    private ElasticsearchArrayNativePredicateTranslator() {}

    static Optional<ElasticsearchRemotePredicate> translate(
            ConnectorSession session,
            ConnectorExpression expression,
            Variable element,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        return translate(session, expression, element, column, policy, false);
    }

    static Optional<ElasticsearchRemotePredicate> translateNecessaryCondition(
            ConnectorSession session,
            ConnectorExpression expression,
            Variable element,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        return translate(session, expression, element, column, policy, true);
    }

    private static Optional<ElasticsearchRemotePredicate> translate(
            ConnectorSession session,
            ConnectorExpression expression,
            Variable element,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy,
            boolean necessaryCondition)
    {
        if (!column.sourceValueSemanticsExact()) {
            return Optional.empty();
        }
        if (integralMapping(element.getType(), column)) {
            Compiler compiler = new Compiler(element, policy);
            Optional<TruthSets> sets = compiler.compile(expression, 0);
            if (sets.isEmpty() || sets.orElseThrow().onNull() == Truth.TRUE) {
                // _source NULL array elements are absent from the index, so an existential TRUE on NULL is not native.
                return Optional.empty();
            }
            if (necessaryCondition && !sets.orElseThrow().trueValues().union(sets.orElseThrow().falseValues()).equals(compiler.universe)) {
                // An UNKNOWN-only branch is not a FALSE guard for an untranslated, potentially throwing sibling.
                return Optional.empty();
            }
            return Optional.of(compiler.render(sets.orElseThrow().trueValues(), column.predicateName()));
        }
        return translateKeyword(session, expression, element, column, policy, 0, new NodeBudget());
    }

    private static Optional<ElasticsearchRemotePredicate> translateKeyword(
            ConnectorSession session,
            ConnectorExpression expression,
            Variable element,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy,
            int depth,
            NodeBudget budget)
    {
        if (!(element.getType() instanceof VarcharType)
                || !(column.elasticsearchType() instanceof PrimitiveType mapping)
                || !(mapping.name().equals("keyword") || mapping.keyword().isPresent())
                // ES 7 cannot run prefix/regexp queries on doc-values-only keywords, although ES 8 can.
                || !mapping.indexed()
                || !(expression instanceof Call call)
                || !budget.admit(depth, policy)) {
            return Optional.empty();
        }
        if (OR_FUNCTION_NAME.equals(call.getFunctionName()) && !call.getArguments().isEmpty() && call.getArguments().size() <= policy.maxBooleanClauses()) {
            List<ElasticsearchRemotePredicate> children = new ArrayList<>();
            for (ConnectorExpression argument : call.getArguments()) {
                Optional<ElasticsearchRemotePredicate> child = translateKeyword(session, argument, element, column, policy, depth + 1, budget);
                if (child.isEmpty()) {
                    return Optional.empty();
                }
                children.add(child.orElseThrow());
            }
            return ElasticsearchRemotePredicateNormalizer.or(children);
        }
        if (AND_FUNCTION_NAME.equals(call.getFunctionName()) && !call.getArguments().isEmpty() && call.getArguments().size() <= policy.maxBooleanClauses()) {
            String prefix = "";
            boolean disjoint = false;
            for (ConnectorExpression argument : call.getArguments()) {
                Optional<ElasticsearchRemotePredicate> child = translateKeyword(session, argument, element, column, policy, depth + 1, budget);
                if (child.isEmpty() || !(child.orElseThrow() instanceof ElasticsearchRemotePredicate.Prefix candidate)) {
                    return Optional.empty();
                }
                if (candidate.value().startsWith(prefix)) {
                    prefix = candidate.value();
                }
                else if (!prefix.startsWith(candidate.value())) {
                    disjoint = true;
                }
            }
            if (disjoint) {
                return Optional.of(new ElasticsearchRemotePredicate.MatchNone());
            }
            return Optional.of(new ElasticsearchRemotePredicate.Prefix(column.predicateName(), prefix));
        }
        if (NOT_FUNCTION_NAME.equals(call.getFunctionName()) && call.getArguments().size() == 1
                && call.getArguments().getFirst() instanceof Call inner && isElementNullTest(inner, element)) {
            return Optional.of(new ElasticsearchRemotePredicate.Exists(column.predicateName()));
        }
        if (EQUAL_OPERATOR_FUNCTION_NAME.equals(call.getFunctionName()) || IN_PREDICATE_FUNCTION_NAME.equals(call.getFunctionName())) {
            Optional<ElasticsearchRemotePredicate> membership = ElasticsearchArrayPredicateTranslator.translateAnyMatchBody(call, element, column, element.getType());
            if (membership.isPresent()) {
                return membership;
            }
        }
        // Scalar translators operate on the same keyword values. The enclosing array caller retains ownership of
        // residuals; only their proven exact leaf translations are accepted here. Never inherit UNSAFE text semantics.
        ElasticsearchColumnHandle scalar = new ElasticsearchColumnHandle(
                column.path(), element.getType(), column.elasticsearchType(), column.decoderDescriptor(), true, column.supportsQueryShapes(), column.predicateMappingSafe());
        Map<String, ColumnHandle> assignments = Map.of(element.getName(), scalar);
        Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translated = ElasticsearchFullTextPredicateTranslator.translateLike(session, call, assignments, SAFE, policy);
        if (translated.isEmpty()) {
            translated = ElasticsearchFullTextPredicateTranslator.translatePrefix(call, assignments, SAFE, policy);
        }
        if (translated.flatMap(ElasticsearchPredicateTranslation::enforcement)
                .filter(value -> value == ElasticsearchRemotePredicate.Enforcement.EXACT).isPresent()) {
            return translated.orElseThrow().remotePredicate();
        }
        if (call.getFunctionName().equals(new FunctionName("regexp_like"))
                && call.getArguments().size() == 2 && call.getArguments().getFirst().equals(element)
                && call.getArguments().get(1) instanceof Constant constant
                && constant.getValue() instanceof Slice pattern
                // SQL regex '$' may also match before a final line terminator. A Lucene full-term query cannot
                // simply drop that anchor and claim equivalence for strings containing line terminators.
                && !pattern.toStringUtf8().contains("$")) {
            // The shared regex converter distinguishes exact translations from deliberately weakened candidates.
            return CasePreservingElasticsearchMetadata.translateRegexpLike(pattern.toStringUtf8())
                    .filter(value -> value.quality() == CasePreservingElasticsearchMetadata.TranslationQuality.EXACT)
                    .map(value -> new ElasticsearchRemotePredicate.Regexp(column.predicateName(), value.pattern()));
        }
        return Optional.empty();
    }

    private static boolean isElementNullTest(Call call, Variable element)
    {
        return IS_NULL_FUNCTION_NAME.equals(call.getFunctionName()) && call.getArguments().size() == 1 && call.getArguments().getFirst().equals(element);
    }

    private static final class NodeBudget
    {
        private int nodes;

        private boolean admit(int depth, ElasticsearchPredicateCompositionPolicy policy)
        {
            return++nodes <= policy.maxBooleanClauses() && depth <= policy.maxBooleanDepth();
        }
    }

    private static boolean integralMapping(Type type, ElasticsearchColumnHandle column)
    {
        if (!(column.type() instanceof ArrayType) || !(column.elasticsearchType() instanceof PrimitiveType mapping)) {
            return false;
        }
        return (type.equals(TINYINT) && mapping.name().equals("byte"))
                || (type.equals(SMALLINT) && mapping.name().equals("short"))
                || (type.equals(INTEGER) && mapping.name().equals("integer"))
                || (type.equals(BIGINT) && mapping.name().equals("long"));
    }

    private static boolean integral(Type type)
    {
        return type.equals(TINYINT) || type.equals(SMALLINT) || type.equals(INTEGER) || type.equals(BIGINT);
    }

    private static long minimum(Type type)
    {
        if (type.equals(TINYINT)) {
            return Byte.MIN_VALUE;
        }
        if (type.equals(SMALLINT)) {
            return Short.MIN_VALUE;
        }
        return type.equals(INTEGER) ? Integer.MIN_VALUE : Long.MIN_VALUE;
    }

    private static long maximum(Type type)
    {
        if (type.equals(TINYINT)) {
            return Byte.MAX_VALUE;
        }
        if (type.equals(SMALLINT)) {
            return Short.MAX_VALUE;
        }
        return type.equals(INTEGER) ? Integer.MAX_VALUE : Long.MAX_VALUE;
    }

    private enum Truth
    {
        TRUE, FALSE, UNKNOWN;

        Truth not()
        {
            return switch (this) {
                case TRUE -> FALSE;
                case FALSE -> TRUE;
                case UNKNOWN -> UNKNOWN;
            };
        }

        Truth and(Truth other)
        {
            if (this == FALSE || other == FALSE) {
                return FALSE;
            }
            return this == TRUE && other == TRUE ? TRUE : UNKNOWN;
        }

        Truth or(Truth other)
        {
            if (this == TRUE || other == TRUE) {
                return TRUE;
            }
            return this == FALSE && other == FALSE ? FALSE : UNKNOWN;
        }
    }

    private record TruthSets(ValueSet trueValues, ValueSet falseValues, Truth onNull)
    {
        TruthSets not()
        {
            return new TruthSets(falseValues, trueValues, onNull.not());
        }
    }

    private static final class Compiler
    {
        private final Variable element;
        private final ElasticsearchPredicateCompositionPolicy policy;
        private final ValueSet universe;
        private final ValueSet empty = ValueSet.none(BIGINT);
        private int nodes;

        private Compiler(Variable element, ElasticsearchPredicateCompositionPolicy policy)
        {
            this.element = element;
            this.policy = policy;
            universe = ValueSet.ofRanges(Range.range(BIGINT, minimum(element.getType()), true, maximum(element.getType()), true));
        }

        private Optional<TruthSets> compile(ConnectorExpression expression, int depth)
        {
            if (++nodes > policy.maxBooleanClauses() || depth > policy.maxBooleanDepth() || !expression.getType().equals(BOOLEAN)) {
                return Optional.empty();
            }
            if (expression instanceof Constant constant) {
                if (constant.getValue() == null) {
                    return Optional.of(new TruthSets(empty, empty, Truth.UNKNOWN));
                }
                boolean value = (Boolean) constant.getValue();
                return Optional.of(new TruthSets(value ? universe : empty, value ? empty : universe, value ? Truth.TRUE : Truth.FALSE));
            }
            if (!(expression instanceof Call call)) {
                return Optional.empty();
            }
            FunctionName name = call.getFunctionName();
            List<ConnectorExpression> arguments = call.getArguments();
            if ((AND_FUNCTION_NAME.equals(name) || OR_FUNCTION_NAME.equals(name)) && !arguments.isEmpty()) {
                boolean and = AND_FUNCTION_NAME.equals(name);
                TruthSets result = new TruthSets(and ? universe : empty, and ? empty : universe, and ? Truth.TRUE : Truth.FALSE);
                for (ConnectorExpression argument : arguments) {
                    Optional<TruthSets> compiled = compile(argument, depth + 1);
                    if (compiled.isEmpty()) {
                        return Optional.empty();
                    }
                    TruthSets child = compiled.orElseThrow();
                    result = new TruthSets(
                            and ? result.trueValues().intersect(child.trueValues()) : result.trueValues().union(child.trueValues()),
                            and ? result.falseValues().union(child.falseValues()) : result.falseValues().intersect(child.falseValues()),
                            and ? result.onNull().and(child.onNull()) : result.onNull().or(child.onNull()));
                    if (!withinSetBudget(result)) {
                        return Optional.empty();
                    }
                }
                return Optional.of(result);
            }
            if (NOT_FUNCTION_NAME.equals(name) && arguments.size() == 1) {
                return compile(arguments.getFirst(), depth + 1).map(TruthSets::not);
            }
            if (IS_NULL_FUNCTION_NAME.equals(name) && arguments.size() == 1 && isElement(arguments.getFirst())) {
                return Optional.of(new TruthSets(empty, universe, Truth.TRUE));
            }
            if (BETWEEN_FUNCTION_NAME.equals(name) && arguments.size() == 3) {
                Optional<TruthSets> lower = comparison(GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME, arguments.get(0), arguments.get(1));
                Optional<TruthSets> upper = comparison(LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME, arguments.get(0), arguments.get(2));
                if (lower.isEmpty() || upper.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new TruthSets(
                        lower.orElseThrow().trueValues().intersect(upper.orElseThrow().trueValues()),
                        lower.orElseThrow().falseValues().union(upper.orElseThrow().falseValues()),
                        Truth.UNKNOWN));
            }
            if (IN_PREDICATE_FUNCTION_NAME.equals(name) && arguments.size() == 2 && isElement(arguments.getFirst())) {
                return membership(arguments.get(1));
            }
            if (arguments.size() == 2) {
                return comparison(name, arguments.get(0), arguments.get(1));
            }
            return Optional.empty();
        }

        private boolean isElement(ConnectorExpression expression)
        {
            for (int depth = 0; depth <= policy.maxBooleanDepth(); depth++) {
                if (expression.equals(element)) {
                    return true;
                }
                if (!(expression instanceof Call call) || !CAST_FUNCTION_NAME.equals(call.getFunctionName())
                        || call.getArguments().size() != 1 || !integral(call.getType())
                        || !integral(call.getArguments().getFirst().getType())
                        || minimum(call.getType()) > minimum(call.getArguments().getFirst().getType())
                        || maximum(call.getType()) < maximum(call.getArguments().getFirst().getType())) {
                    return false;
                }
                expression = call.getArguments().getFirst();
            }
            return false;
        }

        private Optional<TruthSets> comparison(FunctionName name, ConnectorExpression left, ConnectorExpression right)
        {
            boolean equal = EQUAL_OPERATOR_FUNCTION_NAME.equals(name);
            boolean notEqual = NOT_EQUAL_OPERATOR_FUNCTION_NAME.equals(name);
            boolean less = LESS_THAN_OPERATOR_FUNCTION_NAME.equals(name);
            boolean lessEqual = LESS_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(name);
            boolean greater = GREATER_THAN_OPERATOR_FUNCTION_NAME.equals(name);
            boolean greaterEqual = GREATER_THAN_OR_EQUAL_OPERATOR_FUNCTION_NAME.equals(name);
            if (!(equal || notEqual || less || lessEqual || greater || greaterEqual)) {
                return Optional.empty();
            }
            boolean reversed = isElement(right);
            ConnectorExpression value = reversed ? left : right;
            if (!isElement(reversed ? right : left) || !(value instanceof Constant constant) || !integral(constant.getType())) {
                return Optional.empty();
            }
            if (constant.getValue() == null) {
                return Optional.of(new TruthSets(empty, empty, Truth.UNKNOWN));
            }
            if (!(constant.getValue() instanceof Long number) || number < minimum(constant.getType()) || number > maximum(constant.getType())) {
                return Optional.empty();
            }
            ValueSet values;
            if (equal || notEqual) {
                values = ValueSet.of(BIGINT, number).intersect(universe);
                if (notEqual) {
                    values = universe.subtract(values);
                }
            }
            else {
                boolean upper = reversed ? greater || greaterEqual : less || lessEqual;
                boolean inclusive = lessEqual || greaterEqual;
                Range range;
                if (upper) {
                    range = inclusive ? Range.lessThanOrEqual(BIGINT, number) : Range.lessThan(BIGINT, number);
                }
                else {
                    range = inclusive ? Range.greaterThanOrEqual(BIGINT, number) : Range.greaterThan(BIGINT, number);
                }
                values = ValueSet.ofRanges(range).intersect(universe);
            }
            return Optional.of(new TruthSets(values, universe.subtract(values), Truth.UNKNOWN));
        }

        private Optional<TruthSets> membership(ConnectorExpression expression)
        {
            List<Object> values = new ArrayList<>();
            boolean hasNull = false;
            if (expression instanceof Constant constant && constant.getType() instanceof ArrayType array
                    && integral(array.getElementType()) && constant.getValue() instanceof Block block) {
                if (block.getPositionCount() == 0 || block.getPositionCount() > policy.maxTermsValues()) {
                    return Optional.empty();
                }
                for (int index = 0; index < block.getPositionCount(); index++) {
                    if (block.isNull(index)) {
                        hasNull = true;
                    }
                    else {
                        values.add(readNativeValue(array.getElementType(), block, index));
                    }
                }
            }
            else if (expression instanceof Call array && ARRAY_CONSTRUCTOR_FUNCTION_NAME.equals(array.getFunctionName())
                    && !array.getArguments().isEmpty() && array.getArguments().size() <= policy.maxTermsValues()) {
                for (ConnectorExpression argument : array.getArguments()) {
                    if (!(argument instanceof Constant constant) || !integral(constant.getType())) {
                        return Optional.empty();
                    }
                    if (constant.getValue() == null) {
                        hasNull = true;
                    }
                    else {
                        values.add(constant.getValue());
                    }
                }
            }
            else {
                return Optional.empty();
            }
            ValueSet matches = ValueSet.copyOf(BIGINT, values).intersect(universe);
            return Optional.of(new TruthSets(matches, hasNull ? empty : universe.subtract(matches), Truth.UNKNOWN));
        }

        private boolean withinSetBudget(TruthSets sets)
        {
            long maximumRanges = (long) policy.maxTermsValues() + policy.maxBooleanClauses();
            return sets.trueValues().getRanges().getRangeCount() <= maximumRanges && sets.falseValues().getRanges().getRangeCount() <= maximumRanges;
        }

        private ElasticsearchRemotePredicate render(ValueSet values, String field)
        {
            if (values.isNone()) {
                return new ElasticsearchRemotePredicate.MatchNone();
            }
            if (values.equals(universe)) {
                return new ElasticsearchRemotePredicate.Exists(field);
            }
            List<ElasticsearchRemotePredicate> predicates = new ArrayList<>();
            List<Object> discrete = new ArrayList<>();
            for (Range range : values.getRanges().getOrderedRanges()) {
                if (range.isSingleValue()) {
                    discrete.add(range.getSingleValue());
                    continue;
                }
                Optional<ElasticsearchRemotePredicate.Bound> lower = range.getLowValue()
                        .filter(value -> !value.equals(minimum(element.getType())) || !range.isLowInclusive())
                        .map(value -> new ElasticsearchRemotePredicate.Bound(value, range.isLowInclusive()));
                Optional<ElasticsearchRemotePredicate.Bound> upper = range.getHighValue()
                        .filter(value -> !value.equals(maximum(element.getType())) || !range.isHighInclusive())
                        .map(value -> new ElasticsearchRemotePredicate.Bound(value, range.isHighInclusive()));
                predicates.add(new ElasticsearchRemotePredicate.Range(field, lower, upper));
            }
            if (!discrete.isEmpty()) {
                predicates.add(discrete.size() == 1 ? new ElasticsearchRemotePredicate.Term(field, discrete.getFirst()) : new ElasticsearchRemotePredicate.Terms(field, discrete));
            }
            return ElasticsearchRemotePredicateNormalizer.or(predicates).orElseThrow();
        }
    }
}
