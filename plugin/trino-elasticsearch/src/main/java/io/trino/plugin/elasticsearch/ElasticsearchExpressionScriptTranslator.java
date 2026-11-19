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
import io.airlift.slice.SliceUtf8;
import io.trino.plugin.elasticsearch.client.IndexMetadata.DateTimeType;
import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.spi.block.Block;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.ConnectorExpression;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.Lambda;
import io.trino.spi.expression.Variable;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.DecimalType;
import io.trino.spi.type.Decimals;
import io.trino.spi.type.Int128;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.EXACT_SCALAR_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_ARRAY_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.PREFILTER_SCALAR_SCRIPT;
import static io.trino.plugin.elasticsearch.ElasticsearchPredicateTranslation.Reason.UNSUPPORTED_EXPRESSION;
import static io.trino.spi.expression.StandardFunctions.ARRAY_CONSTRUCTOR_FUNCTION_NAME;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DateType.DATE;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.RealType.REAL;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.TypeUtils.readNativeValue;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Shared scalar and array expression compiler with SQL nullable values and three-valued booleans.
 * Array translation separately proves that skipping unindexed NULL elements cannot lose TRUE or suppress an error;
 * unknown array proofs stay local. Scalar translation evaluates NULL directly. Fallible operations admit errors and
 * retain the whole SQL expression.
 */
final class ElasticsearchExpressionScriptTranslator
{
    private static final String HELPERS = ElasticsearchScriptFunctions.SOURCE;
    private static final int MAX_NODES = 128;
    private static final int MAX_SCRIPT_BYTES = 60_000;

    private ElasticsearchExpressionScriptTranslator() {}

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(Call source, Lambda lambda, ElasticsearchColumnHandle column, ElasticsearchPredicateCompositionPolicy policy)
    {
        if (!(column.type() instanceof ArrayType array)) {
            return Optional.empty();
        }
        return translate(source, lambda.getBody(), lambda.getArguments().getFirst(), column, array.getElementType(), policy, true);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateScalar(
            ConnectorExpression source,
            Variable variable,
            ElasticsearchColumnHandle column,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        return translate(source, source, variable, column, column.type(), policy, false);
    }

    static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translateTextSource(
            ConnectorExpression source,
            ConnectorExpression expression,
            Variable variable,
            ElasticsearchColumnHandle column,
            boolean array,
            ElasticsearchPredicateCompositionPolicy policy)
    {
        try {
            Compiler compiler = new Compiler(variable, policy, true);
            Compiled body = compiler.compile(expression, 0);
            if (!body.type().equals(BOOLEAN)) {
                return Optional.empty();
            }
            // Fallible expressions retain SQL residuals. Their candidate must therefore use the exact case policy.
            if (body.fallible()) {
                compiler = new Compiler(variable, policy);
                body = compiler.compile(expression, 0);
            }
            compiler.parameters.put("field", ElasticsearchRemotePredicate.Value.of(column.remoteName()));
            compiler.parameters.put("pathLength", ElasticsearchRemotePredicate.Value.of(column.path().size()));
            for (int index = 0; index < column.path().size(); index++) {
                compiler.parameters.put("path" + index, ElasticsearchRemotePredicate.Value.of(column.path().get(index)));
            }
            String helpers = HELPERS + "\ndef sourceText(def value) { if (value == null) return null; "
                    + "if (value instanceof String || value instanceof Number) return value.toString(); "
                    + "throw new IllegalArgumentException('Expected a string or numeric source value'); }\n"
                    + "def sourceCase(def value, boolean upper) { if (value == null) return null; "
                    + "return upper ? ((String)value).toUpperCase(Locale.ROOT) : ((String)value).toLowerCase(Locale.ROOT); }\n";
            String binding = "def value = params._source;\nfor (int i = 0; i < params.pathLength; i++) { "
                    + "if (value == null) break; if (!(value instanceof Map)) throw new IllegalArgumentException('Invalid source field path'); "
                    + "value = value.get(params.get('path' + i)); }\n";
            String errors = "} catch (ArithmeticException e) { emit(true); return; } catch (IllegalArgumentException e) { emit(true); return; } catch (DateTimeException e) { emit(true); return; }\n";
            String script;
            if (array) {
                script = helpers + binding + "if (value == null) { emit(false); return; }\n"
                        + "def values = value instanceof List ? value : [value];\nfor (def item : values) {\ndef x = sourceText(item);\ntry {\n"
                        + "if (Boolean.TRUE.equals(" + body.code() + ")) { emit(true); return; }\n" + errors + "}\nemit(false);\n";
            }
            else {
                script = helpers + binding + "def x = sourceText(value);\ntry {\nemit(Boolean.TRUE.equals(" + body.code() + "));\n" + errors;
            }
            ElasticsearchRemotePredicate predicate = new ElasticsearchRemotePredicate.SourceScript(column.remoteName(), script, compiler.parameters, array);
            if (script.getBytes(UTF_8).length > MAX_SCRIPT_BYTES || !ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
                return Optional.of(ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION));
            }
            if (body.fallible()) {
                return Optional.of(ElasticsearchPredicateTranslation.prefilter(predicate, source, array ? PREFILTER_ARRAY_SCRIPT : PREFILTER_SCALAR_SCRIPT));
            }
            if (compiler.sourceCaseUsed) {
                return Optional.of(ElasticsearchPredicateTranslation.approximate(predicate, ElasticsearchPredicateTranslation.Reason.FULL_TEXT_UNSAFE_APPROXIMATE));
            }
            return Optional.of(ElasticsearchPredicateTranslation.exact(predicate, array ? EXACT_ARRAY_SCRIPT : EXACT_SCALAR_SCRIPT));
        }
        catch (UnsupportedExpression _) {
            return Optional.empty();
        }
    }

    private static Optional<ElasticsearchPredicateTranslation<ConnectorExpression>> translate(
            ConnectorExpression source,
            ConnectorExpression expression,
            Variable variable,
            ElasticsearchColumnHandle column,
            Type type,
            ElasticsearchPredicateCompositionPolicy policy,
            boolean array)
    {
        if (!column.sourceValueSemanticsExact() || !eligible(type, column)) {
            return Optional.empty();
        }
        try {
            Compiler compiler = new Compiler(variable, policy);
            Compiled body = compiler.compile(expression, 0);
            if (!body.type().equals(BOOLEAN) || (array && (!body.onNull().known() || Boolean.TRUE.equals(body.onNull().value())))) {
                return Optional.empty();
            }
            compiler.parameters.put("field", ElasticsearchRemotePredicate.Value.of(column.remoteName()));
            String binding;
            if (type.equals(TIMESTAMP_MILLIS)) {
                binding = "sqlTimestampCheck(value.toInstant().toEpochMilli())";
            }
            else if (type.equals(REAL)) {
                binding = "(float) value";
            }
            else if (type.equals(DOUBLE)) {
                binding = "(double) value";
            }
            else {
                binding = "value";
            }
            String errors = "} catch (ArithmeticException e) { return true; } catch (IllegalArgumentException e) { return true; }"
                    + " catch (DateTimeException e) { return true; }\n";
            String script;
            ElasticsearchRemotePredicate predicate;
            if (array) {
                script = HELPERS + "\nif (!doc.containsKey(params.field)) return false;\nfor (def value : doc[params.field]) {\ntry {\n"
                        + "def x = " + binding + ";\nif (Boolean.TRUE.equals(" + body.code() + ")) return true;\n"
                        + errors + "}\nreturn false;\n";
                predicate = new ElasticsearchRemotePredicate.ArrayScript(column.remoteName(), script, compiler.parameters);
            }
            else {
                // Unlike an existential array, a missing scalar field is SQL NULL and must evaluate the body.
                // Never pick an arbitrary value from malformed multi-valued data declared as a scalar column.
                script = HELPERS + "\nif (doc.containsKey(params.field) && doc[params.field].size() > 1) "
                        + "throw new IllegalArgumentException('Expected a scalar SQL field');\ntry {\ndef x = null;\n"
                        + "if (doc.containsKey(params.field) && doc[params.field].size() == 1) { def value = doc[params.field].value; x = " + binding + "; }\n"
                        + "return Boolean.TRUE.equals(" + body.code() + ");\n" + errors;
                predicate = new ElasticsearchRemotePredicate.ScalarScript(column.remoteName(), script, compiler.parameters);
            }
            if (script.getBytes(UTF_8).length > MAX_SCRIPT_BYTES || !ElasticsearchPredicateComposer.isWithinRequestBudget(predicate, policy)) {
                return Optional.of(ElasticsearchPredicateTranslation.residual(source, UNSUPPORTED_EXPRESSION));
            }
            if (body.fallible() || type.equals(TIMESTAMP_MILLIS)) {
                return Optional.of(ElasticsearchPredicateTranslation.prefilter(predicate, source, array ? PREFILTER_ARRAY_SCRIPT : PREFILTER_SCALAR_SCRIPT));
            }
            return Optional.of(ElasticsearchPredicateTranslation.exact(predicate, array ? EXACT_ARRAY_SCRIPT : EXACT_SCALAR_SCRIPT));
        }
        catch (UnsupportedExpression _) {
            return Optional.empty();
        }
    }

    private static boolean eligible(Type type, ElasticsearchColumnHandle column)
    {
        if (type.equals(TIMESTAMP_MILLIS)) {
            return column.elasticsearchType() instanceof DateTimeType mapping && mapping.aggregatable();
        }
        if (!(column.elasticsearchType() instanceof PrimitiveType mapping) || !mapping.aggregatable()) {
            return false;
        }
        return (type.equals(TINYINT) && mapping.name().equals("byte"))
                || (type.equals(SMALLINT) && mapping.name().equals("short"))
                || (type.equals(INTEGER) && mapping.name().equals("integer"))
                || (type.equals(BIGINT) && mapping.name().equals("long"))
                || (type.equals(REAL) && mapping.name().equals("float"))
                || (type.equals(DOUBLE) && mapping.name().equals("double"))
                || (type instanceof VarcharType && mapping.name().equals("keyword"))
                || (type.equals(BOOLEAN) && mapping.name().equals("boolean"));
    }

    private record Known(boolean known, Object value)
    {
        static Known of(Object value)
        {
            return new Known(true, value);
        }

        static Known unknown()
        {
            return new Known(false, null);
        }
    }

    private record Compiled(String code, Type type, boolean dependent, boolean fallible, Known onNull) {}

    private static final class UnsupportedExpression
            extends RuntimeException {}

    private static final class Compiler
    {
        private final Variable element;
        private final ElasticsearchPredicateCompositionPolicy policy;
        private final Map<String, ElasticsearchRemotePredicate.Value> parameters = new LinkedHashMap<>();
        private int nodes;
        private final boolean sourceCaseEnabled;
        private boolean sourceCaseUsed;

        private Compiler(Variable element, ElasticsearchPredicateCompositionPolicy policy)
        {
            this(element, policy, false);
        }

        private Compiler(Variable element, ElasticsearchPredicateCompositionPolicy policy, boolean sourceCaseEnabled)
        {
            this.element = element;
            this.policy = policy;
            this.sourceCaseEnabled = sourceCaseEnabled;
        }

        private Compiled compile(ConnectorExpression expression, int depth)
        {
            Compiled result = compileExpression(expression, depth);
            if (result.code().length() > MAX_SCRIPT_BYTES) {
                throw new UnsupportedExpression();
            }
            return result;
        }

        private Compiled compileExpression(ConnectorExpression expression, int depth)
        {
            if (++nodes > Math.min(MAX_NODES, policy.maxBooleanClauses()) || depth > policy.maxBooleanDepth()) {
                throw new UnsupportedExpression();
            }
            if (expression.equals(element)) {
                return new Compiled("x", element.getType(), true, false, Known.of(null));
            }
            if (expression instanceof Constant constant) {
                return constant(constant);
            }
            if (!(expression instanceof Call call) || call.getFunctionName().getCatalogSchema().isPresent()) {
                throw new UnsupportedExpression();
            }
            String name = call.getFunctionName().getName();
            if (name.equals("$in")) {
                return membership(call, depth);
            }
            if (name.equals("$like")) {
                return like(call, depth);
            }
            List<Compiled> arguments = new ArrayList<>();
            for (ConnectorExpression argument : call.getArguments()) {
                arguments.add(compile(argument, depth + 1));
            }
            boolean dependent = arguments.stream().anyMatch(Compiled::dependent);
            boolean fallible = arguments.stream().anyMatch(Compiled::fallible);
            Type type = call.getType();
            if (name.equals("$and") || name.equals("$or")) {
                if (arguments.isEmpty() || arguments.stream().anyMatch(value -> !value.type().equals(BOOLEAN))) {
                    throw new UnsupportedExpression();
                }
                Compiled result = arguments.getFirst();
                for (Compiled next : arguments.subList(1, arguments.size())) {
                    Compiled left = result;
                    Known value = logical(left.onNull(), next.onNull(), name.equals("$and"));
                    result = new Compiled("sqlBoolean(" + left.code() + ", " + next.code() + ", " + (name.equals("$and") ? 0 : 1) + ")", BOOLEAN, dependent, fallible, value);
                }
                return result;
            }
            if (name.equals("$not") && arguments.size() == 1 && arguments.getFirst().type().equals(BOOLEAN)) {
                Known value = arguments.getFirst().onNull();
                return new Compiled(
                        "sqlBoolean(" + arguments.getFirst().code() + ", null, 2)",
                        BOOLEAN,
                        dependent,
                        fallible,
                        value.known() ? Known.of(value.value() == null ? null : !((Boolean) value.value())) : Known.unknown());
            }
            if (name.equals("$is_null") && arguments.size() == 1) {
                Known value = arguments.getFirst().onNull();
                return new Compiled("(" + arguments.getFirst().code() + " == null)", BOOLEAN, dependent, fallible, value.known() ? Known.of(value.value() == null) : Known.unknown());
            }
            if (name.equals("$coalesce") && arguments.size() >= 2) {
                if (arguments.stream().anyMatch(value -> !value.type().equals(type))) {
                    throw new UnsupportedExpression();
                }
                Known value = Known.of(null);
                for (Compiled argument : arguments) {
                    value = argument.onNull();
                    if (!value.known() || value.value() != null) {
                        break;
                    }
                }
                return new Compiled("sqlCoalesce(" + list(arguments) + ")", type, dependent, fallible, value);
            }
            if (name.equals("$case") && arguments.size() >= 3 && arguments.size() % 2 == 1) {
                Compiled otherwise = arguments.getLast();
                if (!otherwise.type().equals(type)) {
                    throw new UnsupportedExpression();
                }
                String code = otherwise.code();
                Known value = otherwise.onNull();
                for (int index = arguments.size() - 3; index >= 0; index -= 2) {
                    Compiled condition = arguments.get(index);
                    Compiled result = arguments.get(index + 1);
                    if (!condition.type().equals(BOOLEAN) || !result.type().equals(type)) {
                        throw new UnsupportedExpression();
                    }
                    code = "(Boolean.TRUE.equals(" + condition.code() + ") ? " + result.code() + " : " + code + ")";
                    if (!condition.onNull().known()) {
                        value = Known.unknown();
                    }
                    else if (Boolean.TRUE.equals(condition.onNull().value())) {
                        value = result.onNull();
                    }
                }
                return new Compiled(code, type, dependent, fallible, value);
            }
            int comparison = List.of("$equal", "$not_equal", "$less_than", "$less_than_or_equal", "$greater_than", "$greater_than_or_equal", "$identical").indexOf(name);
            if (comparison >= 0 && arguments.size() == 2 && arguments.get(0).type().equals(arguments.get(1).type())) {
                int kind = kind(arguments.getFirst().type());
                return new Compiled(
                        "sqlCompare(" + codes(arguments) + ", " + kind + ", " + comparison + ")",
                        BOOLEAN,
                        dependent,
                        fallible,
                        evaluate(arguments, values -> compare(values.get(0), values.get(1), kind, comparison)));
            }
            if (name.equals("$between") && arguments.size() == 3 && arguments.stream().allMatch(value -> value.type().equals(arguments.getFirst().type()))) {
                int kind = kind(arguments.getFirst().type());
                String code = "sqlBoolean(sqlCompare(" + arguments.get(0).code() + ", " + arguments.get(1).code() + ", " + kind + ", 5), sqlCompare("
                        + arguments.get(0).code() + ", " + arguments.get(2).code() + ", " + kind + ", 3), 0)";
                Known lower = evaluate(arguments, values -> compare(values.get(0), values.get(1), kind, 5));
                Known upper = evaluate(arguments, values -> compare(values.get(0), values.get(2), kind, 3));
                return new Compiled(code, BOOLEAN, dependent, fallible, logical(lower, upper, true));
            }
            if (name.equals("$nullif") && arguments.size() == 2 && arguments.get(0).type().equals(type) && arguments.get(1).type().equals(type)) {
                int kind = kind(type);
                return new Compiled(
                        "sqlNullIf(" + codes(arguments) + ", " + kind + ")",
                        type,
                        dependent,
                        fallible,
                        evaluate(arguments, values -> Boolean.TRUE.equals(compare(values.get(0), values.get(1), kind, 0)) ? null : values.get(0)));
            }
            if ((name.equals("$cast") || name.equals("$try_cast")) && arguments.size() == 1) {
                return cast(arguments.getFirst(), type, name.equals("$try_cast"));
            }
            int operation = List.of("$add", "$subtract", "$multiply", "$divide", "$modulo").indexOf(name);
            if (name.equals("mod")) {
                operation = 4;
            }
            if (operation >= 0 && arguments.size() == 2 && numeric(type) && arguments.stream().allMatch(value -> value.type().equals(type) || (type instanceof DecimalType && value.type() instanceof DecimalType))) {
                int operator = operation;
                if (type instanceof DecimalType decimal) {
                    int scale = operator == 2 ? ((DecimalType) arguments.get(0).type()).getScale() + ((DecimalType) arguments.get(1).type()).getScale()
                            : Math.max(((DecimalType) arguments.get(0).type()).getScale(), ((DecimalType) arguments.get(1).type()).getScale());
                    if (operator != 3 && decimal.getScale() < scale) {
                        throw new UnsupportedExpression();
                    }
                }
                int kind = kind(type);
                boolean canFail = kind == 0 || kind == 3;
                Compiled divisor = arguments.get(1);
                if (kind == 0 && (operator == 3 || operator == 4) && !divisor.dependent() && divisor.onNull().known()
                        && divisor.onNull().value() instanceof Long number && number != 0 && (operator == 4 || number != -1)) {
                    canFail = false;
                }
                return new Compiled(
                        "sqlArithmetic(" + codes(arguments) + ", " + kind + ", " + operator + ", " + limits(type) + ")",
                        type,
                        dependent,
                        fallible || canFail,
                        strict(arguments, values -> arithmetic(values.get(0), values.get(1), type, operator)));
            }
            if (name.equals("$negate") && arguments.size() == 1 && numeric(type)) {
                return math(arguments, type, 5);
            }
            int math = List.of("abs", "ceil", "floor", "round", "truncate", "negate", "sign", "sqrt", "pow").indexOf(name);
            if (name.equals("ceiling")) {
                math = 1;
            }
            if (name.equals("power")) {
                math = 8;
            }
            if (math >= 0 && numeric(type) && (arguments.size() == 1 || arguments.size() == 2)) {
                return math(arguments, type, math);
            }
            if (name.equals("concat") && arguments.size() >= 2 && arguments.stream().allMatch(value -> value.type() instanceof VarcharType)) {
                return new Compiled(
                        "sqlConcat(" + list(arguments) + ")",
                        type,
                        dependent,
                        true,
                        strict(arguments, values -> {
                            String text = String.join("", values.stream().map(String.class::cast).toList());
                            if (text.getBytes(UTF_8).length > 1_048_576) {
                                throw new ArithmeticException("SQL concat limit");
                            }
                            return text;
                        }));
            }
            if ((name.equals("substr") || name.equals("substring")) && (arguments.size() == 2 || arguments.size() == 3) && arguments.getFirst().type() instanceof VarcharType
                    && arguments.subList(1, arguments.size()).stream().allMatch(value -> integral(value.type()))) {
                Compiled length = arguments.size() == 2 ? constant(new Constant(Long.MAX_VALUE, BIGINT)) : arguments.get(2);
                List<Compiled> values = List.of(arguments.get(0), arguments.get(1), length);
                boolean bounded = arguments.size() == 3;
                return new Compiled(
                        "sqlSubstring(" + codes(values) + ", " + bounded + ")",
                        type,
                        dependent,
                        fallible || bounded,
                        strict(values, items -> {
                            if (bounded && ((Number) items.get(2)).longValue() > 1_000_000) {
                                throw new ArithmeticException();
                            }
                            return substring((String) items.get(0), ((Number) items.get(1)).longValue(), ((Number) items.get(2)).longValue());
                        }));
            }
            int string = List.of("length", "lower", "upper", "starts_with", "ends_with", "strpos", "replace", "trim", "ltrim", "rtrim").indexOf(name);
            if (string >= 0 && !arguments.isEmpty() && arguments.getFirst().type() instanceof VarcharType) {
                return string(arguments, type, string);
            }
            return temporal(name, arguments, type, dependent);
        }

        private Compiled constant(Constant constant)
        {
            Type type = constant.getType();
            Object value = constant.getValue();
            if (value == null) {
                return new Compiled("null", type, false, false, Known.of(null));
            }
            String code;
            if (type.equals(REAL)) {
                value = Float.intBitsToFloat(((Long) value).intValue());
                code = "Float.intBitsToFloat((int)(long)" + parameter(constant.getValue()) + ")";
            }
            else if (type.equals(DOUBLE)) {
                code = "Double.longBitsToDouble((long)" + parameter(Double.doubleToRawLongBits((Double) value)) + ")";
            }
            else if (type instanceof DecimalType decimal) {
                String text = value instanceof Long number ? Decimals.toString(number, decimal.getScale()) : Decimals.toString((Int128) value, decimal.getScale());
                value = new BigDecimal(text);
                code = "new BigDecimal((String)" + parameter(text) + ")";
            }
            else if (type instanceof VarcharType && value instanceof Slice slice) {
                value = slice.toStringUtf8();
                code = parameter(value);
            }
            else if (type.equals(TIMESTAMP_MILLIS)) {
                long micros = (Long) value;
                if (micros % 1000 != 0) {
                    throw new UnsupportedExpression();
                }
                value = micros / 1000;
                code = parameter(value);
            }
            else if (integral(type) || type.equals(DATE) || type.equals(BOOLEAN)) {
                code = parameter(value);
            }
            else {
                throw new UnsupportedExpression();
            }
            return new Compiled(code, type, false, false, Known.of(value));
        }

        private String parameter(Object value)
        {
            if (parameters.size() >= policy.maxTermsValues()) {
                throw new UnsupportedExpression();
            }
            String name = "p" + parameters.size();
            parameters.put(name, ElasticsearchRemotePredicate.Value.of(value));
            return "params." + name;
        }

        private Compiled cast(Compiled value, Type target, boolean tryCast)
        {
            Type from = value.type();
            if (from.equals(target) || (from instanceof VarcharType source && target instanceof VarcharType result && (result.isUnbounded() || (!source.isUnbounded() && result.getBoundedLength() >= source.getBoundedLength())))) {
                return new Compiled(value.code(), target, value.dependent(), value.fallible(), value.onNull());
            }
            if (!numeric(from) || !numeric(target) || ((from.equals(REAL) || from.equals(DOUBLE)) && (integral(target) || target instanceof DecimalType))) {
                throw new UnsupportedExpression();
            }
            boolean safe = (integral(from) && integral(target) && minimum(target) <= minimum(from) && maximum(target) >= maximum(from))
                    || target.equals(REAL) || target.equals(DOUBLE)
                    || (integral(from) && target instanceof DecimalType result && result.getPrecision() - result.getScale() >= BigInteger.valueOf(minimum(from)).abs().toString().length())
                    || (from instanceof DecimalType source && target instanceof DecimalType result && result.getScale() >= source.getScale()
                    && result.getPrecision() - result.getScale() >= source.getPrecision() - source.getScale());
            Known onNull = evaluate(List.of(value), values -> {
                try {
                    return castValue(values.getFirst(), target);
                }
                catch (ArithmeticException e) {
                    if (tryCast) {
                        return null;
                    }
                    throw e;
                }
            });
            return new Compiled(
                    "sqlCast(" + value.code() + ", " + kind(from) + ", " + kind(target) + ", " + limits(target) + ", " + tryCast + ")",
                    target,
                    value.dependent(),
                    value.fallible() || (!tryCast && !safe),
                    onNull);
        }

        private Compiled math(List<Compiled> arguments, Type type, int operation)
        {
            if (operation >= 7 && !type.equals(DOUBLE)) {
                throw new UnsupportedExpression();
            }
            if (arguments.size() == 2 && operation != 3 && operation != 8) {
                throw new UnsupportedExpression();
            }
            Compiled argument = arguments.size() == 2 ? arguments.get(1) : constant(new Constant(0L, INTEGER));
            if (operation == 3 && !integral(argument.type())) {
                throw new UnsupportedExpression();
            }
            if (operation == 3 && type instanceof DecimalType && (argument.dependent() || !argument.onNull().known()
                    || !(argument.onNull().value() instanceof Long places) || places < -38 || places > 38)) {
                throw new UnsupportedExpression();
            }
            List<Compiled> values = List.of(arguments.getFirst(), argument);
            boolean fallible = values.stream().anyMatch(Compiled::fallible) || integral(type) || type instanceof DecimalType;
            return new Compiled(
                    "sqlMath(" + codes(values) + ", " + kind(type) + ", " + operation + ", " + limits(type) + ")",
                    type,
                    values.stream().anyMatch(Compiled::dependent),
                    fallible,
                    strict(values, items -> mathValue(items.get(0), items.get(1), type, operation)));
        }

        private Compiled string(List<Compiled> arguments, Type type, int operation)
        {
            int required = operation >= 3 && operation <= 6 ? 2 : 1;
            if (arguments.size() < required || arguments.size() > (operation == 6 ? 3 : required)
                    || arguments.stream().anyMatch(value -> !(value.type() instanceof VarcharType))) {
                throw new UnsupportedExpression();
            }
            if (sourceCaseEnabled && (operation == 1 || operation == 2)) {
                sourceCaseUsed = true;
                Compiled value = arguments.getFirst();
                return new Compiled(
                        "sourceCase(" + value.code() + ", " + (operation == 2) + ")",
                        type,
                        value.dependent(),
                        value.fallible(),
                        value.onNull().known() && value.onNull().value() == null ? Known.of(null) : Known.unknown());
            }
            Compiled empty = constant(new Constant(utf8Slice(""), VARCHAR));
            List<Compiled> values = List.of(arguments.getFirst(), arguments.size() > 1 ? arguments.get(1) : empty, arguments.size() > 2 ? arguments.get(2) : empty);
            return new Compiled(
                    "sqlString(" + codes(values) + ", " + operation + ")",
                    type,
                    arguments.stream().anyMatch(Compiled::dependent),
                    arguments.stream().anyMatch(Compiled::fallible) || operation == 1 || operation == 2 || operation == 6,
                    strict(values, items -> stringValue((String) items.get(0), (String) items.get(1), (String) items.get(2), operation)));
        }

        private Compiled membership(Call call, int depth)
        {
            if (call.getArguments().size() != 2) {
                throw new UnsupportedExpression();
            }
            Compiled value = compile(call.getArguments().get(0), depth + 1);
            ConnectorExpression candidates = call.getArguments().get(1);
            List<Compiled> values = new ArrayList<>();
            if (candidates instanceof Call array && ARRAY_CONSTRUCTOR_FUNCTION_NAME.equals(array.getFunctionName())) {
                for (ConnectorExpression argument : array.getArguments()) {
                    if (!(argument instanceof Constant) || !argument.getType().equals(value.type())) {
                        throw new UnsupportedExpression();
                    }
                    values.add(compile(argument, depth + 1));
                }
            }
            else if (candidates instanceof Constant constant && constant.getType() instanceof ArrayType array && array.getElementType().equals(value.type()) && constant.getValue() instanceof Block block) {
                if (block.getPositionCount() > MAX_NODES) {
                    throw new UnsupportedExpression();
                }
                for (int index = 0; index < block.getPositionCount(); index++) {
                    values.add(compile(new Constant(readNativeValue(value.type(), block, index), value.type()), depth + 1));
                }
            }
            else {
                throw new UnsupportedExpression();
            }
            if (values.isEmpty()) {
                throw new UnsupportedExpression();
            }
            List<Compiled> all = new ArrayList<>(List.of(value));
            all.addAll(values);
            int kind = kind(value.type());
            return new Compiled("sqlIn(" + value.code() + ", " + list(values) + ", " + kind + ")", BOOLEAN, value.dependent(), value.fallible(),
                    evaluate(all, items -> {
                        if (items.getFirst() == null) {
                            return null;
                        }
                        boolean unknown = false;
                        for (Object candidate : items.subList(1, items.size())) {
                            if (candidate == null) {
                                unknown = true;
                            }
                            else if (Boolean.TRUE.equals(compare(items.getFirst(), candidate, kind, 0))) {
                                return true;
                            }
                        }
                        return unknown ? null : false;
                    }));
        }

        private Compiled like(Call call, int depth)
        {
            if (call.getArguments().size() < 2 || call.getArguments().size() > 3 || !(call.getArguments().get(1) instanceof Constant pattern)
                    || !(pattern.getValue() instanceof Slice text)) {
                throw new UnsupportedExpression();
            }
            Compiled value = compile(call.getArguments().getFirst(), depth + 1);
            if (!(value.type() instanceof VarcharType)) {
                throw new UnsupportedExpression();
            }
            int escape = -1;
            if (call.getArguments().size() == 3) {
                if (!(call.getArguments().get(2) instanceof Constant constant) || !(constant.getValue() instanceof Slice escaped) || escaped.toStringUtf8().codePointCount(0, escaped.toStringUtf8().length()) != 1) {
                    throw new UnsupportedExpression();
                }
                escape = escaped.toStringUtf8().codePointAt(0);
            }
            List<Integer> tokens = new ArrayList<>();
            int[] points = text.toStringUtf8().codePoints().toArray();
            if (points.length > 1024) {
                throw new UnsupportedExpression();
            }
            for (int index = 0; index < points.length; index++) {
                int point = points[index];
                if (point == escape) {
                    if (++index >= points.length || (points[index] != escape && points[index] != '%' && points[index] != '_')) {
                        throw new UnsupportedExpression();
                    }
                    tokens.add(points[index]);
                }
                else {
                    tokens.add(point == '%' ? -1 : point == '_' ? -2 : point);
                }
            }
            String literal = "new int[]{" + String.join(",", tokens.stream().map(Object::toString).toList()) + "}";
            Known onNull = value.onNull().known() && value.onNull().value() == null ? Known.of(null) : Known.unknown();
            return new Compiled("sqlLike(" + value.code() + ", " + literal + ")", BOOLEAN, value.dependent(), value.fallible(), onNull);
        }

        private Compiled temporal(String name, List<Compiled> arguments, Type type, boolean dependent)
        {
            int operation = List.of("year", "month", "day", "hour", "minute", "second", "day_of_week", "day_of_year").indexOf(name);
            if (name.equals("day_of_month")) {
                operation = 2;
            }
            if (name.equals("dow")) {
                operation = 6;
            }
            if (name.equals("doy")) {
                operation = 7;
            }
            int unit = 0;
            Compiled value;
            Compiled amount = constant(new Constant(0L, BIGINT));
            if (operation >= 0 && arguments.size() == 1) {
                value = arguments.getFirst();
            }
            else if ((name.equals("date_trunc") && arguments.size() == 2) || (name.equals("date_add") && arguments.size() == 3)) {
                if (!arguments.getFirst().onNull().known() || arguments.getFirst().dependent() || !(arguments.getFirst().onNull().value() instanceof String text)) {
                    throw new UnsupportedExpression();
                }
                unit = List.of("millisecond", "second", "minute", "hour", "day", "week", "month", "quarter", "year").indexOf(text.toLowerCase(Locale.ENGLISH));
                if (unit < 0) {
                    throw new UnsupportedExpression();
                }
                value = arguments.getLast();
                operation = name.equals("date_trunc") ? 8 : 9;
                if (operation == 9) {
                    amount = arguments.get(1);
                    if (!integral(amount.type())) {
                        throw new UnsupportedExpression();
                    }
                }
            }
            else if (name.equals("date_diff") && arguments.size() == 3 && arguments.get(1).type().equals(TIMESTAMP_MILLIS) && arguments.get(2).type().equals(TIMESTAMP_MILLIS)) {
                if (!arguments.getFirst().onNull().known() || arguments.getFirst().dependent() || !(arguments.getFirst().onNull().value() instanceof String text)) {
                    throw new UnsupportedExpression();
                }
                Long factor = Map.of("millisecond", 1L, "second", 1000L, "minute", 60_000L, "hour", 3_600_000L, "day", 86_400_000L, "week", 604_800_000L).get(text.toLowerCase(Locale.ENGLISH));
                if (factor == null) {
                    throw new UnsupportedExpression();
                }
                Known onNull = strictNull(List.of(arguments.get(1), arguments.get(2)));
                return new Compiled("sqlDateDiff(" + arguments.get(1).code() + ", " + arguments.get(2).code() + ", " + factor + "L)", type, dependent, true, onNull);
            }
            else {
                throw new UnsupportedExpression();
            }
            if (!value.type().equals(TIMESTAMP_MILLIS)) {
                throw new UnsupportedExpression();
            }
            Known onNull = strictNull(List.of(value, amount));
            return new Compiled("sqlTemporal(" + value.code() + ", " + amount.code() + ", " + operation + ", " + unit + ")", type, dependent, true, onNull);
        }
    }

    private static String codes(List<Compiled> values)
    {
        return String.join(", ", values.stream().map(Compiled::code).toList());
    }

    private static String list(List<Compiled> values)
    {
        return "[" + codes(values) + "]";
    }

    private static boolean integral(Type type)
    {
        return type.equals(TINYINT) || type.equals(SMALLINT) || type.equals(INTEGER) || type.equals(BIGINT);
    }

    private static boolean numeric(Type type)
    {
        return integral(type) || type.equals(REAL) || type.equals(DOUBLE) || type instanceof DecimalType;
    }

    private static int kind(Type type)
    {
        if (integral(type) || type.equals(DATE) || type.equals(TIMESTAMP_MILLIS)) {
            return 0;
        }
        if (type.equals(REAL)) {
            return 1;
        }
        if (type.equals(DOUBLE)) {
            return 2;
        }
        if (type instanceof DecimalType) {
            return 3;
        }
        if (type instanceof VarcharType) {
            return 4;
        }
        if (type.equals(BOOLEAN)) {
            return 5;
        }
        throw new UnsupportedExpression();
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

    private static String limits(Type type)
    {
        return (minimum(type) == Long.MIN_VALUE ? "Long.MIN_VALUE" : minimum(type) + "L") + ", " + (maximum(type) == Long.MAX_VALUE ? "Long.MAX_VALUE" : maximum(type) + "L") + ", " + (type instanceof DecimalType decimal ? decimal.getPrecision() : 0) + ", " + (type instanceof DecimalType decimal ? decimal.getScale() : 0);
    }

    private static Known evaluate(List<Compiled> arguments, Function<List<Object>, Object> function)
    {
        if (arguments.stream().anyMatch(value -> !value.onNull().known())) {
            return Known.unknown();
        }
        List<Object> values = new ArrayList<>();
        arguments.forEach(value -> values.add(value.onNull().value()));
        try {
            return Known.of(function.apply(values));
        }
        catch (ArithmeticException | IllegalArgumentException e) {
            return Known.unknown();
        }
    }

    private static Known strict(List<Compiled> arguments, Function<List<Object>, Object> function)
    {
        return evaluate(arguments, values -> values.stream().anyMatch(value -> value == null) ? null : function.apply(values));
    }

    private static Known strictNull(List<Compiled> arguments)
    {
        // An unindexed NULL element may still evaluate a failing argument before the strict function returns NULL.
        if (arguments.stream().allMatch(argument -> argument.onNull().known()) && arguments.stream().anyMatch(argument -> argument.onNull().value() == null)) {
            return Known.of(null);
        }
        return Known.unknown();
    }

    private static Known logical(Known left, Known right, boolean and)
    {
        if (!left.known() || !right.known()) {
            return Known.unknown();
        }
        if (and && ((left.known() && Boolean.FALSE.equals(left.value())) || (right.known() && Boolean.FALSE.equals(right.value())))) {
            return Known.of(false);
        }
        if (!and && ((left.known() && Boolean.TRUE.equals(left.value())) || (right.known() && Boolean.TRUE.equals(right.value())))) {
            return Known.of(true);
        }
        if (left.value() == null || right.value() == null) {
            return Known.of(null);
        }
        return Known.of(and);
    }

    private static Object compare(Object left, Object right, int kind, int operation)
    {
        if (operation == 6 && (left == null || right == null)) {
            return left == null && right == null;
        }
        if (left == null || right == null) {
            return null;
        }
        int compared;
        if (kind == 0) {
            compared = Long.compare(((Number) left).longValue(), ((Number) right).longValue());
        }
        else if (kind == 1 || kind == 2) {
            double a = ((Number) left).doubleValue();
            double b = ((Number) right).doubleValue();
            return switch (operation) {
                case 6 -> a == b || (Double.isNaN(a) && Double.isNaN(b));
                case 0 -> a == b;
                case 1 -> a != b;
                case 2 -> a < b;
                case 3 -> a <= b;
                case 4 -> a > b;
                case 5 -> a >= b;
                default -> throw new UnsupportedExpression();
            };
        }
        else if (kind == 3) {
            compared = ((BigDecimal) left).compareTo((BigDecimal) right);
        }
        else if (kind == 4) {
            compared = Arrays.compare(((String) left).codePoints().toArray(), ((String) right).codePoints().toArray());
        }
        else {
            compared = Boolean.compare((Boolean) left, (Boolean) right);
        }
        return switch (operation) {
            case 0, 6 -> compared == 0;
            case 1 -> compared != 0;
            case 2 -> compared < 0;
            case 3 -> compared <= 0;
            case 4 -> compared > 0;
            case 5 -> compared >= 0;
            default -> throw new UnsupportedExpression();
        };
    }

    private static Object castValue(Object value, Type to)
    {
        if (value == null) {
            return null;
        }
        if (integral(to)) {
            long number = value instanceof BigDecimal decimal ? decimal.setScale(0, RoundingMode.HALF_UP).longValueExact() : ((Number) value).longValue();
            if (number < minimum(to) || number > maximum(to)) {
                throw new ArithmeticException();
            }
            return number;
        }
        if (to.equals(REAL)) {
            return ((Number) value).floatValue();
        }
        if (to.equals(DOUBLE)) {
            return ((Number) value).doubleValue();
        }
        return decimalCheck(value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString()), (DecimalType) to);
    }

    private static BigDecimal decimalCheck(BigDecimal value, DecimalType type)
    {
        BigDecimal result = value.setScale(type.getScale(), RoundingMode.HALF_UP);
        if (result.precision() > type.getPrecision()) {
            throw new ArithmeticException();
        }
        return result;
    }

    private static Object arithmetic(Object left, Object right, Type type, int operation)
    {
        if (type instanceof DecimalType decimal) {
            BigDecimal a = (BigDecimal) left;
            BigDecimal b = (BigDecimal) right;
            BigDecimal result = switch (operation) {
                case 0 -> a.add(b);
                case 1 -> a.subtract(b);
                case 2 -> a.multiply(b);
                case 3 -> a.divide(b, decimal.getScale(), RoundingMode.HALF_UP);
                case 4 -> a.remainder(b);
                default -> throw new UnsupportedExpression();
            };
            return decimalCheck(result, decimal);
        }
        if (integral(type)) {
            BigInteger a = BigInteger.valueOf(((Number) left).longValue());
            BigInteger b = BigInteger.valueOf(((Number) right).longValue());
            BigInteger result = switch (operation) {
                case 0 -> a.add(b);
                case 1 -> a.subtract(b);
                case 2 -> a.multiply(b);
                case 3 -> a.divide(b);
                case 4 -> a.remainder(b);
                default -> throw new UnsupportedExpression();
            };
            return castValue(result.longValueExact(), type);
        }
        if (type.equals(REAL)) {
            float a = ((Number) left).floatValue();
            float b = ((Number) right).floatValue();
            return switch (operation) {
                case 0 -> a + b;
                case 1 -> a - b;
                case 2 -> a * b;
                case 3 -> a / b;
                case 4 -> a % b;
                default -> throw new UnsupportedExpression();
            };
        }
        double a = ((Number) left).doubleValue();
        double b = ((Number) right).doubleValue();
        return switch (operation) {
            case 0 -> a + b;
            case 1 -> a - b;
            case 2 -> a * b;
            case 3 -> a / b;
            case 4 -> a % b;
            default -> throw new UnsupportedExpression();
        };
    }

    private static Object mathValue(Object value, Object argument, Type type, int operation)
    {
        if (integral(type) || type instanceof DecimalType) {
            BigDecimal number = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
            if (integral(type) && operation == 3) {
                long places = ((Number) argument).longValue();
                if (places < -18) {
                    throw new ArithmeticException();
                }
                if (places >= 0) {
                    return castValue(value, type);
                }
            }
            BigDecimal result = switch (operation) {
                case 0 -> number.abs();
                case 1 -> number.setScale(0, RoundingMode.CEILING);
                case 2 -> number.setScale(0, RoundingMode.FLOOR);
                case 3 -> number.setScale(((Number) argument).intValue(), RoundingMode.HALF_UP);
                case 4 -> number.setScale(0, RoundingMode.DOWN);
                case 5 -> number.negate();
                case 6 -> BigDecimal.valueOf(number.signum());
                default -> throw new UnsupportedExpression();
            };
            return type instanceof DecimalType decimal ? decimalCheck(result, decimal) : castValue(result, type);
        }
        double number = ((Number) value).doubleValue();
        double result = switch (operation) {
            case 0 -> Math.abs(number);
            case 1 -> Math.ceil(number);
            case 2 -> Math.floor(number);
            case 3 -> round(number, ((Number) argument).longValue());
            case 4 -> Math.signum(number) * Math.floor(Math.abs(number));
            case 5 -> -number;
            case 6 -> Math.signum(number);
            case 7 -> Math.sqrt(number);
            case 8 -> Math.pow(number, ((Number) argument).doubleValue());
            default -> throw new UnsupportedExpression();
        };
        if (type.equals(REAL)) {
            return (float) result;
        }
        return result;
    }

    private static double round(double value, long places)
    {
        if (!Double.isFinite(value)) {
            return value;
        }
        double factor = Math.pow(10, (double) places);
        int sign = value < 0 ? -1 : 1;
        if (factor == 0) {
            return sign * 0.0;
        }
        double rescaled = sign * value * factor;
        long rounded = Math.round(rescaled);
        if (rounded != Long.MAX_VALUE) {
            return sign * (rounded / factor);
        }
        if (Double.isInfinite(rescaled)) {
            return value;
        }
        return sign * rescaled / factor;
    }

    private static String substring(String text, long beginning, long length)
    {
        if (beginning == 0 || length <= 0) {
            return "";
        }
        int points = text.codePointCount(0, text.length());
        long position = beginning > 0 ? beginning - 1 : points + beginning;
        if (position < 0 || position >= points) {
            return "";
        }
        int from = text.offsetByCodePoints(0, (int) position);
        return text.substring(from, text.offsetByCodePoints(from, (int) Math.min(length, points - position)));
    }

    private static Object stringValue(String text, String argument, String replacement, int operation)
    {
        return switch (operation) {
            case 0 -> (long) text.codePointCount(0, text.length());
            case 1, 2 -> {
                if (!text.chars().allMatch(value -> value <= 127)) {
                    throw new IllegalArgumentException();
                }
                yield operation == 1 ? text.toLowerCase(Locale.ENGLISH) : text.toUpperCase(Locale.ENGLISH);
            }
            case 3 -> text.startsWith(argument);
            case 4 -> text.endsWith(argument);
            case 5 -> {
                int position = text.indexOf(argument);
                yield position < 0 ? 0L : (long) text.codePointCount(0, position) + 1;
            }
            case 6 -> {
                if (argument.isEmpty()) {
                    throw new IllegalArgumentException();
                }
                yield text.replace(argument, replacement);
            }
            case 7 -> SliceUtf8.trim(utf8Slice(text)).toStringUtf8();
            case 8 -> SliceUtf8.leftTrim(utf8Slice(text)).toStringUtf8();
            case 9 -> SliceUtf8.rightTrim(utf8Slice(text)).toStringUtf8();
            default -> throw new UnsupportedExpression();
        };
    }
}
