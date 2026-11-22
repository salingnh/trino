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

import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader.ArrayValue;
import com.clickhouse.data.ClickHouseColumn;
import com.google.common.collect.ImmutableList;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.plugin.jdbc.ObjectReadFunction;
import io.trino.plugin.jdbc.ObjectWriteFunction;
import io.trino.spi.TrinoException;
import io.trino.spi.block.Block;
import io.trino.spi.block.RowBlockBuilder;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import io.trino.spi.type.Type;
import io.trino.spi.type.VarcharType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.trino.plugin.jdbc.PredicatePushdownController.DISABLE_PUSHDOWN;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.SmallintType.SMALLINT;
import static io.trino.spi.type.TinyintType.TINYINT;
import static io.trino.spi.type.TypeUtils.writeNativeValue;
import static io.trino.spi.type.VarcharType.VARCHAR;

final class ClickHouseObjectArrayMapping
{
    private ClickHouseObjectArrayMapping() {}

    public static Optional<ColumnMapping> toColumnMapping(ClickHouseColumn column, boolean mapStringAsVarchar)
    {
        if (!column.isArray() || column.isNullable() || column.getArrayNestedLevel() != 1) {
            return Optional.empty();
        }

        ClickHouseColumn tuple = column.getArrayBaseColumn();
        if (tuple == null || !tuple.isTuple() || tuple.isNullable() || tuple.isLowCardinality() || tuple.getNestedColumns().isEmpty()) {
            return Optional.empty();
        }

        ImmutableList.Builder<RowType.Field> fields = ImmutableList.builder();
        for (ClickHouseColumn member : tuple.getNestedColumns()) {
            Optional<Type> type = memberType(member, mapStringAsVarchar);
            if (member.getColumnName().isEmpty() || type.isEmpty()) {
                return Optional.empty();
            }
            fields.add(RowType.field(member.getColumnName(), type.get()));
        }

        RowType rowType = RowType.from(fields.build());
        return Optional.of(ColumnMapping.objectMapping(
                new ArrayType(rowType),
                new ObjectReadFunction()
                {
                    @Override
                    public Class<?> getJavaType()
                    {
                        return Block.class;
                    }

                    @Override
                    public boolean isNull(ResultSet resultSet, int columnIndex)
                            throws SQLException
                    {
                        return resultSet.getObject(columnIndex, Object.class) == null;
                    }

                    @Override
                    public Block readObject(ResultSet resultSet, int columnIndex)
                            throws SQLException
                    {
                        // The JDBC driver's getArray() treats each tuple as another array dimension.
                        return readArray(rowType, resultSet.getObject(columnIndex, Object.class));
                    }
                },
                ObjectWriteFunction.of(Block.class, (_, _, _) -> {
                    throw new TrinoException(NOT_SUPPORTED, "Writing ClickHouse arrays of tuples is not supported");
                }),
                DISABLE_PUSHDOWN));
    }

    private static Optional<Type> memberType(ClickHouseColumn member, boolean mapStringAsVarchar)
    {
        // The JDBC driver decodes strings inside tuples as UTF-8, including in binary string mode.
        return switch (member.getDataType()) {
            case String -> mapStringAsVarchar ? Optional.of(VARCHAR) : Optional.empty();
            case Bool -> Optional.of(BOOLEAN);
            case Int8 -> Optional.of(TINYINT);
            case Int16, UInt8 -> Optional.of(SMALLINT);
            case Int32, UInt16 -> Optional.of(INTEGER);
            case Int64, UInt32 -> Optional.of(BIGINT);
            default -> Optional.empty();
        };
    }

    static Block readArray(RowType rowType, Object value)
            throws SQLException
    {
        List<?> tuples = switch (value) {
            case ArrayValue array -> array.asList();
            case List<?> list -> list;
            case null, default -> throw new SQLException("Expected a ClickHouse array of tuples");
        };
        List<Type> fieldTypes = rowType.getTypeParameters();
        RowBlockBuilder rows = rowType.createBlockBuilder(null, tuples.size());
        for (Object element : tuples) {
            if (!(element instanceof Object[] tuple) || tuple.length != fieldTypes.size()) {
                throw new SQLException("Expected a ClickHouse tuple with " + fieldTypes.size() + " fields");
            }

            rows.buildEntry(fieldBuilders -> {
                for (int field = 0; field < tuple.length; field++) {
                    Type type = fieldTypes.get(field);
                    Object member = switch (tuple[field]) {
                        case null -> null;
                        case String string when type instanceof VarcharType -> utf8Slice(string);
                        case Boolean flag when type.equals(BOOLEAN) -> flag;
                        case Number number when type.getJavaType() == long.class -> number.longValue();
                        default -> throw new SQLException("Unexpected value for ClickHouse tuple field of type " + type);
                    };
                    writeNativeValue(type, fieldBuilders.get(field), member);
                }
            });
        }
        return rows.build();
    }
}
