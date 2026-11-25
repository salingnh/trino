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
import io.airlift.slice.Slice;
import io.trino.plugin.elasticsearch.client.IndexMetadata.ObjectArrayMapping;
import io.trino.plugin.elasticsearch.client.IndexMetadata.ObjectType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate.Value;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FieldDereference;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.VarcharType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_ANY_MATCH;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.spi.expression.StandardFunctions.AND_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.EQUAL_OPERATOR_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.IS_NULL_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.NOT_FUNCTION_NAME;
import static io.trino.spi.expression.StandardFunctions.OR_FUNCTION_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Translates positive existential filters over mapped arrays of objects.
 */
final class ElasticsearchObjectArrayPredicateTranslator
{
    private static final int MAX_SCRIPT_BYTES = 60_000;

    private ElasticsearchObjectArrayPredicateTranslator() {}

    static ElasticsearchPredicateTranslation<ConnectorExpression> translate(
            Call call,
            Lambda lambda,
            ElasticsearchColumnHandle column,
            ObjectType objectType,
            ElasticsearchPredicateCompositionPolicy policy,
            boolean arrayScriptPushdownEnabled)
    {
        Variable element = lambda.getArguments().getFirst();
        if (!(column.type() instanceof ArrayType array) || !(array.getElementType() instanceof RowType row)
                || !row.equals(element.getType()) || objectType.arrayMapping().isEmpty()) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }

        Optional<Condition> parsed = new Parser(element, row, objectType, policy).parse(lambda.getBody(), 0);
        if (parsed.isEmpty()) {
            return ElasticsearchPredicateTranslation.unsupported(call, UNSUPPORTED_EXPRESSION);
        }
        Condition condition = parsed.orElseThrow();
        ObjectArrayMapping mapping = objectType.arrayMapping().orElseThrow();
        Optional<NativeFilter> nativeFilter = Optional.empty();
        // Elasticsearch skips null nested elements. A matching null row would be invisible to any nested query.
        if (!mapping.nested() || !condition.matchesNullRow()) {
            nativeFilter = nativeFilter(condition, mapping, column.remoteName());
            if (mapping.nested()) {
                nativeFilter = nativeFilter.map(filter -> new NativeFilter(new ElasticsearchRemotePredicate.Nested(column.remoteName(), filter.predicate()), filter.exact()));
            }
            nativeFilter = nativeFilter.filter(filter -> ElasticsearchPredicateComposer.isWithinRequestBudget(filter.predicate(), policy));
        }
        if (nativeFilter.isPresent()) {
            NativeFilter filter = nativeFilter.orElseThrow();
            if (filter.exact()) {
                return ElasticsearchPredicateTranslation.exact(filter.predicate(), EXACT_ANY_MATCH);
            }
            return ElasticsearchPredicateTranslation.prefilter(filter.predicate(), call, PREFILTER_ANY_MATCH);
        }

        if (arrayScriptPushdownEnabled) {
            Optional<ElasticsearchRemotePredicate> script = sourceFilter(condition, column, policy);
            if (script.isPresent()) {
                return ElasticsearchPredicateTranslation.prefilter(script.orElseThrow(), call, PREFILTER_ARRAY_SCRIPT);
            }
        }
        return ElasticsearchPredicateTranslation.residual(call, UNSUPPORTED_EXPRESSION);
    }

    private static Optional<NativeFilter> nativeFilter(Condition condition, ObjectArrayMapping mapping, String path)
    {
        if (condition instanceof Leaf leaf) {
            if (!mapping.indexedFields().contains(leaf.field())) {
                return Optional.empty();
            }
            String field = path + "." + leaf.field();
            return switch (leaf.operation()) {
                case EQUAL -> Optional.of(new NativeFilter(new ElasticsearchRemotePredicate.Term(field, Value.of(leaf.value().orElseThrow())), true));
                case IS_NOT_NULL -> Optional.of(new NativeFilter(new ElasticsearchRemotePredicate.Exists(field), true));
                case IS_NULL -> {
                    if (mapping.nested()) {
                        yield Optional.of(new NativeFilter(new ElasticsearchRemotePredicate.Not(new ElasticsearchRemotePredicate.Exists(field)), true));
                    }
                    yield Optional.empty();
                }
            };
        }

        Junction junction = (Junction) condition;
        List<ElasticsearchRemotePredicate> predicates = new ArrayList<>();
        boolean exact = true;
        for (Condition child : junction.conditions()) {
            Optional<NativeFilter> translated = nativeFilter(child, mapping, path);
            if (translated.isEmpty()) {
                if (!junction.conjunction()) {
                    return Optional.empty();
                }
                exact = false;
                continue;
            }
            NativeFilter filter = translated.orElseThrow();
            predicates.add(filter.predicate());
            exact &= filter.exact();
        }
        if (predicates.isEmpty()) {
            return Optional.empty();
        }
        if (junction.conjunction() && !mapping.nested() && predicates.size() > 1) {
            // A flat index can satisfy the conjuncts using different array elements.
            exact = false;
        }
        ElasticsearchRemotePredicate predicate;
        if (junction.conjunction()) {
            predicate = ElasticsearchRemotePredicateNormalizer.and(predicates).orElseThrow();
        }
        else {
            predicate = ElasticsearchRemotePredicateNormalizer.or(predicates).orElseThrow();
        }
        return Optional.of(new NativeFilter(predicate, exact));
    }

    private static Optional<ElasticsearchRemotePredicate> sourceFilter(Condition condition, ElasticsearchColumnHandle column, ElasticsearchPredicateCompositionPolicy policy)
    {
        Map<String, Value> parameters = new LinkedHashMap<>();
        parameters.put("field", Value.of(column.remoteName()));
        parameters.put("pathLength", Value.of(column.path().size()));
        for (int index = 0; index < column.path().size(); index++) {
            parameters.put("path" + index, Value.of(column.path().get(index)));
        }
        String body = sourceCondition(condition, parameters);
        String source =
                """
                def sourceField(def object, def name) {
                    if (object == null) { return null; }
                    if (!(object instanceof Map)) { throw new IllegalArgumentException('Expected an object'); }
                    def field = object.get(name);
                    if (field == null) {
                        for (def key : object.keySet()) {
                            if (key.startsWith(name + '.')) { throw new IllegalArgumentException('Dotted source field'); }
                        }
                    }
                    return field;
                }
                def sourceString(def object, def name) {
                    def value = sourceField(object, name);
                    if (value == null || value instanceof String) { return value; }
                    throw new IllegalArgumentException('Expected a string');
                }
                try {
                    def value = params._source;
                    for (int i = 0; i < params.pathLength; i++) {
                        value = sourceField(value, params.get('path' + i));
                    }
                    if (value == null) { emit(false); return; }
                    def values = value instanceof List ? value : [value];
                    for (def row : values) {
                        if (%s) { emit(true); return; }
                    }
                    emit(false);
                }
                catch (IllegalArgumentException e) { emit(true); }
                """.formatted(body);
        // On an unrecognized source representation, keep the row so Trino can decode and evaluate it.
        ElasticsearchRemotePredicate script = new ElasticsearchRemotePredicate.SourceScript(column.remoteName(), source, parameters, true);
        if (source.getBytes(UTF_8).length > MAX_SCRIPT_BYTES || !ElasticsearchPredicateComposer.isWithinRequestBudget(script, policy)) {
            return Optional.empty();
        }
        return Optional.of(script);
    }

    private static String sourceCondition(Condition condition, Map<String, Value> parameters)
    {
        if (condition instanceof Leaf leaf) {
            String fieldParameter = "key" + parameters.size();
            parameters.put(fieldParameter, Value.of(leaf.field()));
            String field = "sourceString(row, params." + fieldParameter + ")";
            return switch (leaf.operation()) {
                case EQUAL -> {
                    String valueParameter = "value" + parameters.size();
                    parameters.put(valueParameter, Value.of(leaf.value().orElseThrow()));
                    yield "params." + valueParameter + ".equals(" + field + ")";
                }
                case IS_NULL -> field + " == null";
                case IS_NOT_NULL -> field + " != null";
            };
        }
        Junction junction = (Junction) condition;
        List<String> children = new ArrayList<>();
        for (Condition child : junction.conditions()) {
            children.add(sourceCondition(child, parameters));
        }
        return "(" + String.join(junction.conjunction() ? " && " : " || ", children) + ")";
    }

    private record NativeFilter(ElasticsearchRemotePredicate predicate, boolean exact) {}

    private enum Operation
    {
        EQUAL,
        IS_NULL,
        IS_NOT_NULL,
    }

    private sealed interface Condition
            permits Junction, Leaf
    {
        boolean matchesNullRow();
    }

    private record Junction(boolean conjunction, List<Condition> conditions)
            implements Condition
    {
        @Override
        public boolean matchesNullRow()
        {
            if (conjunction) {
                return conditions.stream().allMatch(Condition::matchesNullRow);
            }
            return conditions.stream().anyMatch(Condition::matchesNullRow);
        }
    }

    private record Leaf(String field, Operation operation, Optional<String> value)
            implements Condition
    {
        @Override
        public boolean matchesNullRow()
        {
            return operation == Operation.IS_NULL;
        }
    }

    private static final class Parser
    {
        private final Variable element;
        private final RowType row;
        private final ObjectType object;
        private final ElasticsearchPredicateCompositionPolicy policy;
        private int remainingNodes;

        private Parser(Variable element, RowType row, ObjectType object, ElasticsearchPredicateCompositionPolicy policy)
        {
            this.element = element;
            this.row = row;
            this.object = object;
            this.policy = policy;
            this.remainingNodes = policy.maxBooleanClauses();
        }

        private Optional<Condition> parse(ConnectorExpression expression, int depth)
        {
            if (depth > policy.maxBooleanDepth() || remainingNodes-- <= 0 || !(expression instanceof Call call)) {
                return Optional.empty();
            }
            var function = call.getFunctionName();
            List<ConnectorExpression> arguments = call.getArguments();
            if (AND_FUNCTION_NAME.equals(function) || OR_FUNCTION_NAME.equals(function)) {
                if (arguments.isEmpty()) {
                    return Optional.empty();
                }
                ImmutableList.Builder<Condition> children = ImmutableList.builder();
                for (ConnectorExpression child : arguments) {
                    Optional<Condition> parsed = parse(child, depth + 1);
                    if (parsed.isEmpty()) {
                        return Optional.empty();
                    }
                    children.add(parsed.orElseThrow());
                }
                return Optional.of(new Junction(AND_FUNCTION_NAME.equals(function), children.build()));
            }
            if (IS_NULL_FUNCTION_NAME.equals(function) && arguments.size() == 1) {
                return field(arguments.getFirst()).map(name -> new Leaf(name, Operation.IS_NULL, Optional.empty()));
            }
            if (NOT_FUNCTION_NAME.equals(function) && arguments.size() == 1 && arguments.getFirst() instanceof Call isNull
                    && IS_NULL_FUNCTION_NAME.equals(isNull.getFunctionName()) && isNull.getArguments().size() == 1) {
                return field(isNull.getArguments().getFirst()).map(name -> new Leaf(name, Operation.IS_NOT_NULL, Optional.empty()));
            }
            if (EQUAL_OPERATOR_FUNCTION_NAME.equals(function) && arguments.size() == 2) {
                ConnectorExpression field = arguments.getFirst();
                ConnectorExpression value = arguments.getLast();
                if (field instanceof Constant) {
                    field = arguments.getLast();
                    value = arguments.getFirst();
                }
                if (value instanceof Constant constant && constant.getType() instanceof VarcharType && constant.getValue() instanceof Slice slice) {
                    return field(field).map(name -> new Leaf(name, Operation.EQUAL, Optional.of(slice.toStringUtf8())));
                }
            }
            return Optional.empty();
        }

        private Optional<String> field(ConnectorExpression expression)
        {
            if (!(expression instanceof FieldDereference dereference) || !dereference.getTarget().equals(element)
                    || !(dereference.getType() instanceof VarcharType) || dereference.getField() >= row.getFields().size()) {
                return Optional.empty();
            }
            return row.getFields().get(dereference.getField()).getName()
                    .filter(name -> !name.contains(".") && object.fields().stream().anyMatch(field -> field.name().equals(name)
                            && !field.isArray() && !field.asRawJson() && field.type() instanceof PrimitiveType primitive && primitive.name().equals("keyword")));
        }
    }
}
