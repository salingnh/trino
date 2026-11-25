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
package io.trino.plugin.clickhouse;

import com.clickhouse.data.ClickHouseColumn;
import com.google.common.collect.ImmutableList;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.plugin.jdbc.JdbcColumnHandle;
import io.trino.plugin.jdbc.JdbcTypeHandle;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Locale.ENGLISH;
import static java.util.stream.Collectors.joining;

final class ClickHouseFlattenedNested
{
    private static final Pattern FLAT_MEMBER = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");

    private ClickHouseFlattenedNested() {}

    static List<NestedColumn> group(List<PhysicalColumn> columns, boolean mapStringAsVarchar, boolean unalignedZip, UnaryOperator<String> quote)
    {
        Set<String> physicalNames = columns.stream()
                .map(column -> column.name().toLowerCase(ENGLISH))
                .collect(Collectors.toSet());
        Map<String, List<PhysicalColumn>> groups = new LinkedHashMap<>();
        for (PhysicalColumn column : columns) {
            int dot = column.name().indexOf('.');
            if (dot > 0 && column.type().startsWith("Array(")) {
                groups.computeIfAbsent(column.name().substring(0, dot), _ -> new ArrayList<>()).add(column);
            }
        }

        Set<String> collidingNames = groups.keySet().stream()
                .collect(Collectors.groupingBy(name -> name.toLowerCase(ENGLISH), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        ImmutableList.Builder<NestedColumn> nestedColumns = ImmutableList.builder();
        groups.forEach((name, members) -> {
            String normalizedName = name.toLowerCase(ENGLISH);
            if (!physicalNames.contains(normalizedName) && !collidingNames.contains(normalizedName)) {
                nestedColumn(name, members, mapStringAsVarchar, unalignedZip, quote).ifPresent(nestedColumns::add);
            }
        });
        return nestedColumns.build();
    }

    private static Optional<NestedColumn> nestedColumn(String name, List<PhysicalColumn> members, boolean mapStringAsVarchar, boolean unalignedZip, UnaryOperator<String> quote)
    {
        Set<String> fieldNames = new HashSet<>();
        ImmutableList.Builder<String> tupleMembers = ImmutableList.builder();
        for (PhysicalColumn physical : members) {
            var memberName = FLAT_MEMBER.matcher(physical.name());
            if (!memberName.matches()) {
                return Optional.empty();
            }
            String field = memberName.group(2);
            if (!fieldNames.add(field.toLowerCase(ENGLISH))) {
                return Optional.empty();
            }
            ClickHouseColumn array = ClickHouseColumn.of("", physical.type());
            ClickHouseColumn member = array.getArrayBaseColumn();
            if (!array.isArray() || array.getArrayNestedLevel() != 1 || member == null) {
                return Optional.empty();
            }
            String type = switch (member.getDataType()) {
                case String, Bool, Int8, Int16, Int32, Int64, UInt8, UInt16, UInt32 -> member.getDataType().name();
                default -> null;
            };
            if (type == null) {
                return Optional.empty();
            }
            // ClickHouse 24.4+ supports arrayZipUnaligned, padding shorter sibling arrays with NULLs.
            String memberType = unalignedZip || member.isNullable() ? "Nullable(" + type + ")" : type;
            tupleMembers.add(quote.apply(field) + " " + memberType);
        }

        String tupleArray = "Array(Tuple(" + String.join(", ", tupleMembers.build()) + "))";
        Optional<ColumnMapping> mapping = ClickHouseObjectArrayMapping.toColumnMapping(ClickHouseColumn.of(name, tupleArray), mapStringAsVarchar);
        if (mapping.isEmpty()) {
            return Optional.empty();
        }
        JdbcTypeHandle jdbcType = new JdbcTypeHandle(Types.ARRAY, Optional.of(tupleArray), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        JdbcColumnHandle handle = JdbcColumnHandle.builder()
                .setColumnName(name)
                .setJdbcTypeHandle(jdbcType)
                .setColumnType(mapping.orElseThrow().getType())
                .setNullable(false)
                .build();
        String arrayArguments = members.stream().map(PhysicalColumn::name).map(quote).collect(joining(", "));
        String zipFunction = unalignedZip ? "arrayZipUnaligned" : "arrayZip";
        return Optional.of(new NestedColumn(handle, format("CAST(%s(%s) AS %s) AS %s", zipFunction, arrayArguments, tupleArray, quote.apply(name))));
    }

    record PhysicalColumn(String name, String type) {}

    record NestedColumn(JdbcColumnHandle handle, String definition) {}
}
