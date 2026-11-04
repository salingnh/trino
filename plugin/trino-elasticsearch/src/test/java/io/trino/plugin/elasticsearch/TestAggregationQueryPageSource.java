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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.trino.spi.TrinoException;
import io.trino.spi.block.BlockBuilder;
import org.junit.jupiter.api.Test;

import static io.trino.plugin.elasticsearch.AggregationQueryPageSource.appendValue;
import static io.trino.spi.StandardErrorCode.TYPE_MISMATCH;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.TimestampType.TIMESTAMP_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestAggregationQueryPageSource
{
    @Test
    public void testBooleanDecoding()
    {
        assertThat(decodeBoolean(BooleanNode.TRUE)).isTrue();
        assertThat(decodeBoolean(BooleanNode.FALSE)).isFalse();

        assertThat(decodeBoolean(new IntNode(1))).isTrue();
        assertThat(decodeBoolean(new IntNode(0))).isFalse();
        assertThat(decodeBoolean(new DoubleNode(1.0))).isTrue();
        assertThat(decodeBoolean(new DoubleNode(0.0))).isFalse();
        assertInvalidBoolean(new IntNode(5));
        assertInvalidBoolean(new IntNode(-1));
        assertInvalidBoolean(new DoubleNode(0.5));

        assertThat(decodeBoolean(new TextNode("true"))).isTrue();
        assertThat(decodeBoolean(new TextNode("TRUE"))).isTrue();
        assertThat(decodeBoolean(new TextNode("1"))).isTrue();
        assertThat(decodeBoolean(new TextNode("false"))).isFalse();
        assertThat(decodeBoolean(new TextNode("FALSE"))).isFalse();
        assertThat(decodeBoolean(new TextNode("0"))).isFalse();
        assertThat(decodeBoolean(new TextNode(""))).isFalse();

        assertInvalidBoolean(new TextNode("invalid"));
    }

    @Test
    public void testTimestampDecoding()
    {
        for (long millis : new long[] {-1_234_567_890L, 0, 1_234_567_890L}) {
            var type = TIMESTAMP_MILLIS;
            BlockBuilder builder = type.createBlockBuilder(null, 1);
            appendValue(builder, type, new LongNode(millis));
            assertThat(type.getLong(builder.build(), 0)).isEqualTo(millis * 1000);
        }
        var type = TIMESTAMP_MILLIS;
        BlockBuilder builder = type.createBlockBuilder(null, 1);
        appendValue(builder, type, NullNode.instance);
        assertThat(builder.build().isNull(0)).isTrue();
        assertThatThrownBy(() -> appendValue(type.createBlockBuilder(null, 1), type, new TextNode("2020-01-01")))
                .isInstanceOf(TrinoException.class);
    }

    private static void assertInvalidBoolean(JsonNode jsonNode)
    {
        assertThatThrownBy(() -> decodeBoolean(jsonNode))
                .isInstanceOf(TrinoException.class)
                .hasMessageContaining("Cannot parse value for field as BOOLEAN")
                .extracting(e -> ((TrinoException) e).getErrorCode())
                .isEqualTo(TYPE_MISMATCH.toErrorCode());
    }

    private static boolean decodeBoolean(JsonNode jsonNode)
    {
        BlockBuilder builder = BOOLEAN.createBlockBuilder(null, 1);
        appendValue(builder, BOOLEAN, jsonNode);
        return BOOLEAN.getBoolean(builder.build(), 0);
    }
}
