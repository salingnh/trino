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

final class ElasticsearchScriptFunctions
{
    private ElasticsearchScriptFunctions() {}

    static final String SOURCE =
            """

            Boolean sqlBoolean(def left, def right, int operation) {
                if (operation == 2) {
                    if (left == null) return null;
                    return !((boolean) left);
                }
                if (operation == 0) {
                    if (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right)) return false;
                    if (left == null || right == null) return null;
                    return true;
                }
                if (Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right)) return true;
                if (left == null || right == null) return null;
                return false;
            }
            int sqlStringCompare(String left, String right) {
                int a = 0; int b = 0;
                while (a < left.length() && b < right.length()) {
                    int x = left.codePointAt(a); int y = right.codePointAt(b);
                    if (x != y) return x < y ? -1 : 1;
                    a += Character.charCount(x); b += Character.charCount(y);
                }
                if (a == left.length() && b == right.length()) return 0;
                return a == left.length() ? -1 : 1;
            }
            Boolean sqlCompare(def left, def right, int kind, int operation) {
                if (operation == 6 && (left == null || right == null)) return left == null && right == null;
                if (left == null || right == null) return null;
                int compared;
                if (kind == 0) {
                    long a = (long) left; long b = (long) right;
                    compared = a == b ? 0 : a < b ? -1 : 1;
                }
                else if (kind == 1 || kind == 2) {
                    double a = (double) left; double b = (double) right;
                    if (operation == 6) return a == b || (Double.isNaN(a) && Double.isNaN(b));
                    if (operation == 0) return a == b;
                    if (operation == 1) return a != b;
                    if (operation == 2) return a < b;
                    if (operation == 3) return a <= b;
                    if (operation == 4) return a > b;
                    return a >= b;
                }
                else if (kind == 3) compared = ((BigDecimal) left).compareTo((BigDecimal) right);
                else if (kind == 4) compared = sqlStringCompare((String) left, (String) right);
                else compared = Boolean.compare((boolean) left, (boolean) right);
                if (operation == 0 || operation == 6) return compared == 0;
                if (operation == 1) return compared != 0;
                if (operation == 2) return compared < 0;
                if (operation == 3) return compared <= 0;
                if (operation == 4) return compared > 0;
                return compared >= 0;
            }
            Boolean sqlIn(def value, List values, int kind) {
                if (value == null) return null;
                boolean unknown = false;
                for (def candidate : values) {
                    if (candidate == null) unknown = true;
                    else if (Boolean.TRUE.equals(sqlCompare(value, candidate, kind, 0))) return true;
                }
                if (unknown) return null;
                return false;
            }
            def sqlNullIf(def left, def right, int kind) {
                if (Boolean.TRUE.equals(sqlCompare(left, right, kind, 0))) return null;
                return left;
            }
            def sqlCoalesce(List values) {
                for (def value : values) if (value != null) return value;
                return null;
            }
            long sqlLongCheck(BigInteger value, long minimum, long maximum) {
                long result = value.longValueExact();
                if (result < minimum || result > maximum) throw new ArithmeticException('SQL integer overflow');
                return result;
            }
            BigDecimal sqlDecimalCheck(BigDecimal value, int precision, int scale) {
                BigDecimal result = value.setScale(scale, RoundingMode.HALF_UP);
                if (result.precision() > precision) throw new ArithmeticException('SQL decimal overflow');
                return result;
            }
            def sqlArithmetic(def left, def right, int kind, int operation, long minimum, long maximum, int precision, int scale) {
                if (left == null || right == null) return null;
                if (kind == 0) {
                    BigInteger a = new BigInteger(left.toString()); BigInteger b = new BigInteger(right.toString());
                    BigInteger result;
                    if (operation == 0) result = a.add(b);
                    else if (operation == 1) result = a.subtract(b);
                    else if (operation == 2) result = a.multiply(b);
                    else if (operation == 3) result = a.divide(b);
                    else result = a.remainder(b);
                    return sqlLongCheck(result, minimum, maximum);
                }
                if (kind == 1) {
                    float a = (float) left; float b = (float) right;
                    if (operation == 0) return a + b;
                    if (operation == 1) return a - b;
                    if (operation == 2) return a * b;
                    if (operation == 3) return a / b;
                    return a % b;
                }
                if (kind == 2) {
                    double a = (double) left; double b = (double) right;
                    if (operation == 0) return a + b;
                    if (operation == 1) return a - b;
                    if (operation == 2) return a * b;
                    if (operation == 3) return a / b;
                    return a % b;
                }
                BigDecimal a = (BigDecimal) left; BigDecimal b = (BigDecimal) right;
                BigDecimal result;
                if (operation == 0) result = a.add(b);
                else if (operation == 1) result = a.subtract(b);
                else if (operation == 2) result = a.multiply(b);
                else if (operation == 3) {
                    BigDecimal[] parts = a.movePointRight(scale).divideAndRemainder(b);
                    BigDecimal quotient = parts[0];
                    if (parts[1].abs().multiply(new BigDecimal('2')).compareTo(b.abs()) >= 0) {
                        quotient = quotient.add(new BigDecimal(Integer.toString(a.signum() * b.signum())));
                    }
                    result = quotient.movePointLeft(scale);
                }
                else result = a.remainder(b);
                return sqlDecimalCheck(result, precision, scale);
            }
            def sqlCast(def value, int from, int target, long minimum, long maximum, int precision, int scale, boolean tryCast) {
                if (value == null) return null;
                try {
                    if (target == 0) {
                        if (from == 0) return sqlLongCheck(new BigInteger(value.toString()), minimum, maximum);
                        if (from == 3) return sqlLongCheck(((BigDecimal) value).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact(), minimum, maximum);
                        throw new ArithmeticException('Unsupported SQL cast');
                    }
                    if (target == 1) {
                        if (from == 3) return ((BigDecimal) value).floatValue();
                        return (float) value;
                    }
                    if (target == 2) {
                        if (from == 3) return ((BigDecimal) value).doubleValue();
                        return (double) value;
                    }
                    BigDecimal number;
                    if (from == 3) number = (BigDecimal) value;
                    else if (from == 0) number = new BigDecimal(value.toString());
                    else throw new ArithmeticException('Unsupported SQL decimal cast');
                    return sqlDecimalCheck(number, precision, scale);
                }
                catch (ArithmeticException e) {
                    if (tryCast) return null;
                    throw e;
                }
            }
            double sqlRound(double number, long decimals) {
                if (Double.isNaN(number) || Double.isInfinite(number)) return number;
                double factor = Math.pow(10, decimals);
                int sign = number < 0 ? -1 : 1;
                if (factor == 0) return sign * 0.0;
                double rescaled = sign * number * factor;
                long rounded = Math.round(rescaled);
                if (rounded != Long.MAX_VALUE) return sign * (rounded / factor);
                if (Double.isInfinite(rescaled)) return number;
                return sign * rescaled / factor;
            }
            def sqlMath(def value, def argument, int kind, int operation, long minimum, long maximum, int precision, int scale) {
                if (value == null || argument == null) return null;
                if (kind == 0) {
                    BigInteger number = new BigInteger(value.toString());
                    if (operation == 0) number = number.abs();
                    else if (operation == 5) number = number.negate();
                    else if (operation == 6) number = BigInteger.valueOf(number.signum());
                    else if (operation == 3 && (long) argument < 0) {
                        long places = (long) argument;
                        if (places < -18) throw new ArithmeticException('SQL round factor overflow');
                        number = new BigDecimal(number.toString()).setScale((int) places, RoundingMode.HALF_UP).toBigIntegerExact();
                    }
                    return sqlLongCheck(number, minimum, maximum);
                }
                if (kind == 3) {
                    BigDecimal number = (BigDecimal) value;
                    if (operation == 0) number = number.abs();
                    else if (operation == 1) number = number.setScale(0, RoundingMode.CEILING);
                    else if (operation == 2) number = number.setScale(0, RoundingMode.FLOOR);
                    else if (operation == 3) number = number.setScale((int) argument, RoundingMode.HALF_UP);
                    else if (operation == 4) number = number.setScale(0, RoundingMode.DOWN);
                    else if (operation == 5) number = number.negate();
                    else if (operation == 6) number = new BigDecimal(Integer.toString(number.signum()));
                    return sqlDecimalCheck(number, precision, scale);
                }
                double number = (double) value;
                double result;
                if (operation == 0) result = Math.abs(number);
                else if (operation == 1) result = Math.ceil(number);
                else if (operation == 2) result = Math.floor(number);
                else if (operation == 3) result = sqlRound(number, (long) argument);
                else if (operation == 4) result = Math.signum(number) * Math.floor(Math.abs(number));
                else if (operation == 5) result = -number;
                else if (operation == 6) result = Math.signum(number);
                else if (operation == 7) result = Math.sqrt(number);
                else result = Math.pow(number, (double) argument);
                if (kind == 1) return (float) result;
                return result;
            }
            boolean sqlWhitespace(int c) {
                return (c >= 9 && c <= 13) || (c >= 28 && c <= 32) || c == 5760 || (c >= 8192 && c <= 8198)
                    || (c >= 8200 && c <= 8202) || c == 8232 || c == 8233 || c == 8287 || c == 12288;
            }
            def sqlString(def value, def argument, def replacement, int operation) {
                if (value == null || argument == null || replacement == null) return null;
                String text = (String) value; String other = (String) argument;
                if (operation == 0) return (long) text.codePointCount(0, text.length());
                if (operation == 1 || operation == 2) {
                    for (int i = 0; i < text.length(); i++) if (text.charAt(i) > 127) throw new ArithmeticException('Unicode case conversion requires Trino');
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < text.length(); i++) {
                        int c = text.charAt(i);
                        if (operation == 1 && c >= 65 && c <= 90) c += 32;
                        if (operation == 2 && c >= 97 && c <= 122) c -= 32;
                        result.appendCodePoint(c);
                    }
                    return result.toString();
                }
                if (operation == 3) return text.startsWith(other);
                if (operation == 4) return text.endsWith(other);
                if (operation == 5) {
                    int position = text.indexOf(other);
                    return position < 0 ? 0L : (long) text.codePointCount(0, position) + 1;
                }
                if (operation == 6) {
                    // Admit large expansions to Trino before allocating a large intermediate string.
                    long occurrences = other.isEmpty() ? (long) text.codePointCount(0, text.length()) + 1 : 0;
                    if (!other.isEmpty()) {
                        int offset = 0;
                        while ((offset = text.indexOf(other, offset)) >= 0) { occurrences++; offset += other.length(); }
                    }
                    long output = text.length() + occurrences * (((String) replacement).length() - (long) other.length());
                    if (output > 1048576) throw new ArithmeticException('Large SQL replace requires Trino');
                    if (!other.isEmpty()) return text.replace(other, (String) replacement);
                    StringBuilder result = new StringBuilder((String) replacement);
                    for (int offset = 0; offset < text.length();) {
                        int c = text.codePointAt(offset); offset += Character.charCount(c);
                        result.appendCodePoint(c).append((String) replacement);
                    }
                    return result.toString();
                }
                int start = 0; int end = text.length();
                if (operation != 9) {
                    while (start < end && sqlWhitespace(text.codePointAt(start))) start += Character.charCount(text.codePointAt(start));
                }
                if (operation != 8) {
                    while (end > start && sqlWhitespace(text.codePointBefore(end))) end -= Character.charCount(text.codePointBefore(end));
                }
                return text.substring(start, end);
            }
            def sqlSubstring(def value, def beginning, def count, boolean bounded) {
                if (value == null || beginning == null || count == null) return null;
                String text = (String) value; long start = (long) beginning; long length = (long) count;
                if (bounded && length > 1000000) throw new ArithmeticException('Large SQL substring length requires Trino');
                if (start == 0 || length <= 0) return '';
                int points = text.codePointCount(0, text.length());
                long position = start > 0 ? start - 1 : points + start;
                if (position < 0 || position >= points) return '';
                int from = text.offsetByCodePoints(0, (int) position);
                int to = text.offsetByCodePoints(from, (int) Math.min(length, points - position));
                return text.substring(from, to);
            }
            def sqlConcat(List values) {
                StringBuilder text = new StringBuilder();
                for (def value : values) {
                    if (value == null) return null;
                    String part = (String) value;
                    // UTF-16 units give a lower bound for UTF-8 bytes, so this also bounds allocation.
                    if ((long) text.length() + part.length() > 1048576) throw new ArithmeticException('SQL concat limit');
                    text.append(part);
                }
                long bytes = 0;
                for (int offset = 0; offset < text.length();) {
                    int c = text.codePointAt(offset); offset += Character.charCount(c);
                    bytes += c < 128 ? 1 : c < 2048 ? 2 : c < 65536 ? 3 : 4;
                    if (bytes > 1048576) throw new ArithmeticException('SQL concat limit');
                }
                return text.toString();
            }
            Boolean sqlLike(def value, int[] pattern) {
                if (value == null) return null;
                String text = (String) value;
                int offset = 0; int token = 0; int star = -1; int retry = 0;
                while (offset < text.length()) {
                    int c = text.codePointAt(offset);
                    if (token < pattern.length && (pattern[token] == -2 || pattern[token] == c)) {
                        offset += Character.charCount(c); token++;
                    }
                    else if (token < pattern.length && pattern[token] == -1) {
                        star = token++; retry = offset;
                    }
                    else if (star >= 0) {
                        if (retry >= text.length()) return false;
                        retry += Character.charCount(text.codePointAt(retry)); offset = retry; token = star + 1;
                    }
                    else return false;
                }
                while (token < pattern.length && pattern[token] == -1) token++;
                return token == pattern.length;
            }
            long sqlTimestampCheck(long value) {
                if (value < Long.MIN_VALUE / 1000 || value > Long.MAX_VALUE / 1000) throw new ArithmeticException('SQL timestamp overflow');
                return value;
            }
            def sqlTemporal(def value, def amount, int operation, int unit) {
                if (value == null || amount == null) return null;
                ZonedDateTime date = Instant.ofEpochMilli((long) value).atZone(ZoneOffset.UTC);
                if (operation == 0) return (long) date.getYear();
                if (operation == 1) return (long) date.getMonthValue();
                if (operation == 2) return (long) date.getDayOfMonth();
                if (operation == 3) return (long) date.getHour();
                if (operation == 4) return (long) date.getMinute();
                if (operation == 5) return (long) date.getSecond();
                if (operation == 6) return (long) date.getDayOfWeek().getValue();
                if (operation == 7) return (long) date.getDayOfYear();
                if (operation == 8) {
                    if (unit == 0) return (long) value;
                    if (unit == 1) date = date.withNano(0);
                    else if (unit == 2) date = date.withSecond(0).withNano(0);
                    else if (unit == 3) date = date.withMinute(0).withSecond(0).withNano(0);
                    else {
                        date = date.toLocalDate().atStartOfDay(ZoneOffset.UTC);
                        if (unit == 5) date = date.minusDays(date.getDayOfWeek().getValue() - 1);
                        if (unit == 6) date = date.withDayOfMonth(1);
                        if (unit == 7) date = date.withDayOfMonth(1).withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1);
                        if (unit == 8) date = date.withDayOfYear(1);
                    }
                    return sqlTimestampCheck(date.toInstant().toEpochMilli());
                }
                long number = (long) amount;
                if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new ArithmeticException('SQL date_add amount overflow');
                if (unit == 7 && (number * 3 < Integer.MIN_VALUE || number * 3 > Integer.MAX_VALUE)) throw new ArithmeticException('SQL quarter amount overflow');
                if (unit == 0) date = date.plusNanos(number * 1000000L);
                else if (unit == 1) date = date.plusSeconds(number);
                else if (unit == 2) date = date.plusMinutes(number);
                else if (unit == 3) date = date.plusHours(number);
                else if (unit == 4) date = date.plusDays(number);
                else if (unit == 5) date = date.plusWeeks(number);
                else if (unit == 6) date = date.plusMonths(number);
                else if (unit == 7) date = date.plusMonths(number * 3);
                else date = date.plusYears(number);
                return sqlTimestampCheck(date.toInstant().toEpochMilli());
            }
            def sqlDateDiff(def left, def right, long factor) {
                if (left == null || right == null) return null;
                return new BigInteger(right.toString()).subtract(new BigInteger(left.toString())).divide(BigInteger.valueOf(factor)).longValueExact();
            }
            """;
}
