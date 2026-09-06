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

import io.trino.plugin.elasticsearch.client.IndexMetadata.PrimitiveType;
import io.trino.plugin.elasticsearch.decoders.IntegerDecoder;
import io.trino.spi.expression.Call;
import io.trino.spi.expression.Constant;
import io.trino.spi.expression.FunctionName;
import io.trino.spi.expression.Variable;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.ArrayType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.IntegerType.INTEGER;
import static org.assertj.core.api.Assertions.assertThat;

public class TestElasticsearchSourceValueSemantics
{
    private static final PrimitiveType REWRITTEN_INTEGER = new PrimitiveType("integer", Optional.empty(), false, false);

    @Test
    public void testDynamicFilterRejectsRewrittenIndexedValues()
    {
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("value"),
                INTEGER,
                REWRITTEN_INTEGER,
                new IntegerDecoder.Descriptor("value"),
                true);

        assertThat(new ElasticsearchDynamicFilterPlanner().plan(TupleDomain.withColumnDomains(
                Map.of(column, Domain.singleValue(INTEGER, 0L)))))
                .isEmpty();
    }

    @Test
    public void testArrayPredicateRejectsRewrittenIndexedValues()
    {
        ArrayType arrayType = new ArrayType(INTEGER);
        ElasticsearchColumnHandle column = new ElasticsearchColumnHandle(
                List.of("values"),
                arrayType,
                REWRITTEN_INTEGER,
                new IntegerDecoder.Descriptor("values"),
                false);
        Call contains = new Call(
                BOOLEAN,
                new FunctionName("contains"),
                List.of(new Variable("values", arrayType), new Constant(0L, INTEGER)));

        assertThat(ElasticsearchArrayPredicateTranslator.translate(contains, Map.of("values", column)))
                .isEmpty();
    }
}
