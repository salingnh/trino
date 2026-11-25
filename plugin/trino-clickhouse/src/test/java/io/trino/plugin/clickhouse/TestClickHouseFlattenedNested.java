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

import io.trino.plugin.clickhouse.ClickHouseFlattenedNested.NestedColumn;
import io.trino.plugin.clickhouse.ClickHouseFlattenedNested.PhysicalColumn;
import io.trino.spi.type.ArrayType;
import io.trino.spi.type.RowType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

class TestClickHouseFlattenedNested
{
    @Test
    void testGroupedColumnsKeepMemberNamesAndOrder()
    {
        List<NestedColumn> columns = group(
                new PhysicalColumn("id", "UInt32"),
                new PhysicalColumn("users.select", "Array(Nullable(String))"),
                new PhysicalColumn("users.age", "Array(UInt32)"));

        assertThat(columns).hasSize(1);
        NestedColumn users = columns.getFirst();
        assertThat(users.handle().getColumnName()).isEqualTo("users");
        assertThat(users.handle().getColumnType()).isEqualTo(new ArrayType(RowType.rowType(
                RowType.field("select", VARCHAR),
                RowType.field("age", BIGINT))));
        assertThat(users.definition()).isEqualTo("CAST(arrayZipUnaligned(\"users.select\", \"users.age\") AS Array(Tuple(\"select\" Nullable(String), \"age\" Nullable(UInt32)))) AS \"users\"");
    }

    @Test
    void testDoesNotExposeAmbiguousOrIncompleteGroups()
    {
        assertThat(group(new PhysicalColumn("users", "String"), new PhysicalColumn("users.name", "Array(String)"))).isEmpty();
        assertThat(group(new PhysicalColumn("Users.name", "Array(String)"), new PhysicalColumn("users.city", "Array(String)"))).isEmpty();
        assertThat(group(new PhysicalColumn("users.Name", "Array(String)"), new PhysicalColumn("users.name", "Array(String)"))).isEmpty();
        assertThat(group(new PhysicalColumn("users.name", "Array(String)"), new PhysicalColumn("users.details.zip", "Array(String)"))).isEmpty();
        assertThat(group(new PhysicalColumn("users.name", "Array(String)"), new PhysicalColumn("users.score", "Array(Float64)"))).isEmpty();
    }

    private static List<NestedColumn> group(PhysicalColumn... columns)
    {
        return ClickHouseFlattenedNested.group(List.of(columns), true, name -> "\"" + name + "\"");
    }
}
