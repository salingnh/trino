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

import com.clickhouse.client.api.ClientConfigProperties;
import com.clickhouse.client.api.data_formats.RowBinaryFormatReader;
import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader.DefaultByteBufferAllocator;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.jdbc.internal.JdbcUtils;
import io.trino.plugin.jdbc.ColumnMapping;
import io.trino.spi.block.Block;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static io.trino.plugin.clickhouse.ClickHouseObjectArrayMapping.readArray;
import static io.trino.plugin.clickhouse.ClickHouseObjectArrayMapping.toColumnMapping;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.RowType.field;
import static io.trino.spi.type.RowType.rowType;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestClickHouseObjectArrayMapping
{
    private static final ClickHouseColumn USERS_COLUMN = ClickHouseColumn.of("users", "Array(Tuple(name String, city Nullable(String), active Bool, score UInt32))");
    private static final RowType USER_TYPE = rowType(field("name", VARCHAR), field("city", VARCHAR), field("active", BOOLEAN), field("score", BIGINT));

    @Test
    void testNamedTupleMapping()
    {
        ColumnMapping mapping = toColumnMapping(USERS_COLUMN, true).orElseThrow();
        assertThat(mapping.getType()).isEqualTo(new ArrayType(USER_TYPE));
        assertThat(toColumnMapping(USERS_COLUMN, false)).isEmpty();
        assertThat(toColumnMapping(ClickHouseColumn.of("users", "Array(Tuple(active Bool, score UInt32))"), false))
                .hasValueSatisfying(numeric -> assertThat(numeric.getType()).isEqualTo(new ArrayType(rowType(field("active", BOOLEAN), field("score", BIGINT)))));
    }

    @Test
    void testUnsupportedMappings()
    {
        for (String type : List.of(
                "Tuple(name String)",
                "Array(String)",
                "Array(Array(Tuple(name String)))",
                "Array(Tuple(String, String))",
                "Array(Tuple(name String, history Array(String)))",
                "Array(Nullable(Tuple(name String)))",
                "Array(Tuple(name FixedString(10)))")) {
            assertThat(toColumnMapping(ClickHouseColumn.of("users", type), true))
                    .as(type)
                    .isEmpty();
        }
    }

    @Test
    void testReadRowBinaryArray()
            throws Exception
    {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(3);
        writeString(data, "An");
        data.write(0); // Non-null city
        writeString(data, "Hà Nội");
        data.write(1); // Active
        data.writeBytes(new byte[] {1, 0, 0, 0});
        writeString(data, "Bình");
        data.write(1); // Null city
        data.write(0); // Inactive
        data.writeBytes(new byte[] {-1, -1, -1, -1});
        writeString(data, "An");
        data.write(0); // Non-null city
        writeString(data, "Huế");
        data.write(1); // Active
        data.writeBytes(new byte[] {2, 0, 0, 0});

        Block block = readArray(USER_TYPE, readJdbcValue(data.toByteArray()));
        assertThat(block.getPositionCount()).isEqualTo(3);
        assertThat(USER_TYPE.getObjectValue(block, 0)).isEqualTo(List.of("An", "Hà Nội", true, 1L));
        assertThat(USER_TYPE.getObjectValue(block, 1)).isEqualTo(Arrays.asList("Bình", null, false, 4_294_967_295L));
        assertThat(USER_TYPE.getObjectValue(block, 2)).isEqualTo(List.of("An", "Huế", true, 2L));
        assertThat(readArray(USER_TYPE, readJdbcValue(new byte[] {0})).getPositionCount()).isZero();
    }

    @Test
    void testRejectUnexpectedTupleShape()
    {
        assertThatThrownBy(() -> readArray(USER_TYPE, List.<Object>of(new Object[] {"An"})))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("tuple with 4 fields");
    }

    private static Object readJdbcValue(byte[] data)
            throws Exception
    {
        QuerySettings settings = new QuerySettings()
                .setUseTimeZone("UTC")
                .setOption(ClientConfigProperties.BINARY_STRING_SUPPORT.getKey(), true);
        try (RowBinaryFormatReader reader = new RowBinaryFormatReader(
                new ByteArrayInputStream(data),
                settings,
                new TableSchema(List.of(USERS_COLUMN)),
                new DefaultByteBufferAllocator())) {
            reader.next();
            return JdbcUtils.convert(reader.readValue(1), Object.class, USERS_COLUMN);
        }
    }

    private static void writeString(ByteArrayOutputStream output, String value)
    {
        byte[] bytes = value.getBytes(UTF_8);
        output.write(bytes.length);
        output.writeBytes(bytes);
    }
}
