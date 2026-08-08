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
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.trino.spi.TrinoException;
import io.trino.spi.block.BlockBuilder;
import org.junit.jupiter.api.Test;

import static io.trino.plugin.elasticsearch.AggregationQueryPageSource.appendValue;
import static io.trino.spi.StandardErrorCode.TYPE_MISMATCH;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestAggregationQueryPageSource
{
    @Test
    public void testBooleanDecoding()
    {
        // Boolean node
        assertThat(decodeBoolean(BooleanNode.TRUE)).isTrue();
        assertThat(decodeBoolean(BooleanNode.FALSE)).isFalse();

        // Numeric node
        assertThat(decodeBoolean(new IntNode(1))).isTrue();
        assertThat(decodeBoolean(new IntNode(0))).isFalse();
        assertThat(decodeBoolean(new IntNode(5))).isTrue();

        // Textual node - valid
        assertThat(decodeBoolean(new TextNode("true"))).isTrue();
        assertThat(decodeBoolean(new TextNode("TRUE"))).isTrue();
        assertThat(decodeBoolean(new TextNode("1"))).isTrue();
        assertThat(decodeBoolean(new TextNode("false"))).isFalse();
        assertThat(decodeBoolean(new TextNode("FALSE"))).isFalse();
        assertThat(decodeBoolean(new TextNode("0"))).isFalse();
        assertThat(decodeBoolean(new TextNode(""))).isFalse();

        // Textual node - invalid
        assertThatThrownBy(() -> decodeBoolean(new TextNode("invalid")))
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
