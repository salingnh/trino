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

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.trino.Session;
import io.trino.plugin.elasticsearch.expression.ElasticsearchRemotePredicate;
import io.trino.sql.planner.optimizations.PlanNodeSearcher;
import io.trino.sql.planner.plan.FilterNode;
import io.trino.sql.planner.plan.TableScanNode;
import io.trino.testing.QueryRunner;
import io.trino.testing.QueryRunner.MaterializedResultWithPlan;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.trino.testing.TestingNames.randomNameSuffix;
import static io.trino.testing.assertions.Assert.assertEqualsIgnoreOrder;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.1 acceptance contract for exact {@code any_match} pushdown on primitive Elasticsearch arrays.
 */
public abstract class BaseElasticsearchAnyMatchPushdownTest
        extends BaseElasticsearchP0PredicatePushdownTest
{
    private static final Logger LOG = Logger.get(BaseElasticsearchAnyMatchPushdownTest.class);
    private final RestClient client;

    protected BaseElasticsearchAnyMatchPushdownTest(ElasticsearchServer server)
    {
        super(server);
        this.client = server.getClient();
    }

    @Test
    public void testAnyMatchPrimitiveArrayExactPushdown()
            throws IOException
    {
        String indexName = "p1_any_match_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": {
                    "trino": {
                      "numbers": { "isArray": true },
                      "tags": { "isArray": true },
                      "text_tags": { "isArray": true }
                    }
                  },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" },
                    "tags": { "type": "keyword" },
                    "text_tags": { "type": "text" }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            index(indexName, ImmutableMap.of(
                    "id", "1",
                    "numbers", ImmutableList.of(1, 3, 5),
                    "tags", ImmutableList.of("CaseSensitive", "alpha"),
                    "text_tags", ImmutableList.of("telegram", "social network")));
            index(indexName, ImmutableMap.of(
                    "id", "2",
                    "numbers", ImmutableList.of(2, 4, 6),
                    "tags", ImmutableList.of("casesensitive", "beta"),
                    "text_tags", ImmutableList.of("facebook")));

            List<Integer> numbersWithNull = new ArrayList<>();
            numbersWithNull.add(null);
            numbersWithNull.add(3);
            index(indexName, ImmutableMap.of(
                    "id", "3",
                    "numbers", numbersWithNull,
                    "tags", ImmutableList.of("gamma"),
                    "text_tags", ImmutableList.of("other")));

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x = 3)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '3'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x IN (4, 9))"))
                    .matches("VALUES VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 4)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 2 AND x < 4)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '3'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x = 1 OR x = 6)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2'")
                    .isFullyPushedDown();

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(tags, x -> x = 'CaseSensitive')"))
                    .matches("VALUES VARCHAR '1'")
                    .isFullyPushedDown();

            // Analyzed text is not exact for SQL equality, so the lambda stays in Trino.
            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(text_tags, x -> x = 'telegram')"))
                    .matches("VALUES VARCHAR '1'")
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchNecessaryConditionPrefilter()
            throws IOException
    {
        String indexName = "any_match_prefilter_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": { "trino": { "numbers": { "isArray": true } } },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            index(indexName, ImmutableMap.of("id", "1", "numbers", List.of(2, 4)));
            index(indexName, ImmutableMap.of("id", "2", "numbers", List.of(11, 13)));
            index(indexName, ImmutableMap.of("id", "3", "numbers", List.of(3, 12)));
            // The conditions hold on different elements. A modulo-only residual would admit this row.
            index(indexName, ImmutableMap.of("id", "4", "numbers", List.of(2, 11)));
            index(indexName, ImmutableMap.of("id", "5", "numbers", Arrays.asList(null, 12)));
            index(indexName, ImmutableMap.of("id", "6", "numbers", Arrays.asList(null, 11)));
            index(indexName, ImmutableMap.of("id", "7", "numbers", List.of()));
            index(indexName, ImmutableMap.of("id", "8"));
            index(indexName, ImmutableMap.of("id", "9", "numbers", Arrays.asList((Integer) null)));
            index(indexName, ImmutableMap.of("id", "10", "numbers", List.of(-3)));
            index(indexName, ImmutableMap.of("id", "11", "numbers", List.of(-2)));
            Map<String, Object> nullArray = new HashMap<>();
            nullArray.put("id", "12");
            nullArray.put("numbers", null);
            index(indexName, nullArray);

            String predicate = "any_match(numbers, x -> x > 10 AND mod(x, 2) = 0)";
            String sql = "SELECT id FROM " + indexName + " WHERE " + predicate;
            assertThat(query(sql))
                    .matches("VALUES VARCHAR '3', VARCHAR '5'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query("SELECT count(*) FROM " + indexName + " WHERE " + predicate))
                    .matches("VALUES BIGINT '2'")
                    .isNotFullyPushedDown(FilterNode.class);
            assertThat(query(sql + " ORDER BY id LIMIT 1"))
                    .matches("VALUES VARCHAR '3'")
                    .isNotFullyPushedDown(FilterNode.class);

            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " +
                    "(x > 10 AND mod(x, 2) = 0) OR (x < 0 AND mod(x, 3) = 0))"))
                    .matches("VALUES VARCHAR '3', VARCHAR '5', VARCHAR '10'")
                    .isNotFullyPushedDown(FilterNode.class);
            // The unsupported OR branch must retain rows such as [2, 4], which have no value greater than 10.
            assertThat(query("SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0)"))
                    .matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '3', VARCHAR '4', VARCHAR '5', VARCHAR '6', VARCHAR '11'")
                    .isNotFullyPushedDown(FilterNode.class);

            QueryRunner runner = getQueryRunner();
            MaterializedResultWithPlan filtered = runner.executeWithPlan(getSession(), sql);
            MaterializedResultWithPlan unfiltered = runner.executeWithPlan(
                    getSession(),
                    "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> mod(x, 2) = 0)");
            long filteredRows = runner.getCoordinator().getQueryManager().getFullQueryInfo(filtered.queryId()).getQueryStats().getPhysicalInputPositions();
            long unfilteredRows = runner.getCoordinator().getQueryManager().getFullQueryInfo(unfiltered.queryId()).getQueryStats().getPhysicalInputPositions();
            assertThat(filteredRows).as("documents read after the necessary-condition prefilter").isEqualTo(5);
            assertThat(unfilteredRows).as("documents read without a native candidate").isEqualTo(12);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchArithmeticScriptPushdown()
            throws IOException
    {
        String indexName = "any_match_script_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": { "trino": {
                    "numbers": { "isArray": true }, "without_doc_values": { "isArray": true }
                  } },
                  "properties": {
                    "id": { "type": "keyword" },
                    "numbers": { "type": "integer" },
                    "without_doc_values": { "type": "integer", "doc_values": false }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            List<List<Integer>> values = List.of(
                    List.of(2),
                    List.of(11),
                    List.of(12),
                    List.of(2, 11),
                    List.of(3),
                    List.of(-3),
                    Arrays.asList(null, 2),
                    Arrays.asList((Integer) null),
                    List.of(),
                    List.of(0),
                    List.of(-2));
            for (int i = 0; i < values.size(); i++) {
                index(indexName, ImmutableMap.of("id", Integer.toString(i + 1), "numbers", values.get(i), "without_doc_values", values.get(i)));
            }
            index(indexName, ImmutableMap.of("id", "missing"));
            Session enabled = Session.builder(getSession())
                    .setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true")
                    .build();
            Session disabled = Session.builder(getSession())
                    .setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "false")
                    .build();
            Session localOnly = Session.builder(disabled)
                    .setSystemProperty("allow_pushdown_into_connectors", "false")
                    .build();

            for (String predicate : List.of(
                    "x > 5 AND x > 10 AND x < 20",
                    "x IN (2, 12) AND x > 10",
                    "(x < 0 OR x > 10) AND x < 12",
                    "NOT (x > 10)",
                    "x <> 2",
                    "x NOT IN (2, 11)",
                    "x IN (2, NULL)",
                    "x NOT IN (2, NULL)",
                    "x BETWEEN 2 AND 11",
                    "x > 20 AND x < 10",
                    "x IS NOT NULL")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(disabled, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), predicate);
                assertThat(query(disabled, sql)).isFullyPushedDown();
            }

            for (String predicate : List.of(
                    "x > 10 OR mod(x, 2) = 0",
                    "x > 10 AND mod(x, 2) = 0",
                    "NOT (mod(x, 2) <> 0)",
                    "(x % 2 >= 0 AND x % 3 <= 1) OR x < 0",
                    "mod(x, -2) = -1",
                    "x / 2 = 1 OR x / 2 > 5")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(
                        computeActual(enabled, sql).getMaterializedRows(),
                        computeActual(disabled, sql).getMaterializedRows(),
                        predicate);
                assertThat(query(enabled, sql)).isFullyPushedDown();
            }
            String nativeAndScript = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x IN (3, 11) OR mod(x, 2) = 0)";
            assertEqualsIgnoreOrder(computeActual(enabled, nativeAndScript).getMaterializedRows(), computeActual(localOnly, nativeAndScript).getMaterializedRows(), nativeAndScript);
            assertThat(query(enabled, nativeAndScript)).isFullyPushedDown();
            for (String predicate : List.of(
                    "x + 5 > 10 OR x * 2 < 0",
                    "x - 1 > 0 AND -x < 10",
                    "(x + 1) * (x - 1) >= 0 OR mod(x, 2) = 0")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(
                        computeActual(enabled, sql).getMaterializedRows(),
                        computeActual(disabled, sql).getMaterializedRows(),
                        predicate);
                assertThat(query(enabled, sql)).isNotFullyPushedDown(FilterNode.class);
            }

            String sql = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0)";
            assertThat(query(disabled, sql)).isNotFullyPushedDown(FilterNode.class);
            assertThat(query(enabled, "SELECT count(*) FROM " + indexName + " WHERE any_match(numbers, x -> x > 10 OR mod(x, 2) = 0)"))
                    .matches("VALUES BIGINT '7'")
                    .isFullyPushedDown();
            assertThat(computeActual(enabled, sql + " ORDER BY id LIMIT 2").getMaterializedRows())
                    .containsExactlyElementsOf(computeActual(disabled, sql + " ORDER BY id LIMIT 2").getMaterializedRows());
            String noDocValues = sql.replace("any_match(numbers,", "any_match(without_doc_values,");
            assertEqualsIgnoreOrder(
                    computeActual(enabled, noDocValues).getMaterializedRows(),
                    computeActual(disabled, noDocValues).getMaterializedRows(),
                    noDocValues);
            assertThat(query(enabled, noDocValues)).isNotFullyPushedDown(FilterNode.class);
            String nonStrict = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> TRUE OR x > 0)";
            assertEqualsIgnoreOrder(
                    computeActual(enabled, nonStrict).getMaterializedRows(),
                    computeActual(disabled, nonStrict).getMaterializedRows(),
                    nonStrict);

            QueryRunner runner = getQueryRunner();
            MaterializedResultWithPlan remote = runner.executeWithPlan(enabled, sql);
            MaterializedResultWithPlan local = runner.executeWithPlan(disabled, sql);
            assertThat(runner.getCoordinator().getQueryManager().getFullQueryInfo(remote.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(7);
            assertThat(runner.getCoordinator().getQueryManager().getFullQueryInfo(local.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(12);
            MaterializedResultWithPlan prefilter = runner.executeWithPlan(
                    enabled,
                    "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x + 5 > 10 OR x * 2 < 0)");
            assertThat(runner.getCoordinator().getQueryManager().getFullQueryInfo(prefilter.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(5);
            MaterializedResultWithPlan nativeIntersection = runner.executeWithPlan(
                    disabled,
                    "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> x IN (2, 12) AND x > 10)");
            assertThat(runner.getCoordinator().getQueryManager().getFullQueryInfo(nativeIntersection.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(1);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchKeywordNativePredicates()
            throws IOException
    {
        String indexName = "array_native_keyword_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": { "trino": {
                    "words": { "isArray": true }, "ignored_words": { "isArray": true },
                    "doc_value_words": { "isArray": true }, "indexed_words": { "isArray": true },
                    "unindexed": { "isArray": true }
                  } },
                  "properties": {
                    "id": { "type": "keyword" },
                    "words": { "type": "keyword" },
                    "doc_value_words": { "type": "keyword", "index": false },
                    "indexed_words": { "type": "keyword", "doc_values": false },
                    "ignored_words": { "type": "keyword", "ignore_above": 2 },
                    "unindexed": { "type": "integer", "index": false, "doc_values": false }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            List<List<String>> values = List.of(
                    List.of("ab", "cd"),
                    List.of("abc"),
                    List.of("abz", "z"),
                    List.of("a😀b", "abc\n"),
                    List.of("a_b", "a%b", "a\\b"),
                    List.of("😀", "\uE000"),
                    List.of("", "ABc"),
                    Arrays.asList(null, "abc"),
                    Arrays.asList((String) null),
                    List.of());
            for (int index = 0; index < values.size(); index++) {
                index(indexName, ImmutableMap.of(
                        "id", Integer.toString(index),
                        "words", values.get(index),
                        "ignored_words", values.get(index),
                        "doc_value_words", values.get(index),
                        "indexed_words", values.get(index),
                        "unindexed", List.of(1)));
            }
            index(indexName, ImmutableMap.of("id", "missing"));
            Session nativeOnly = Session.builder(getSession())
                    .setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "false")
                    .build();
            Session localOnly = Session.builder(nativeOnly).setSystemProperty("allow_pushdown_into_connectors", "false").build();
            Session scripted = Session.builder(nativeOnly).setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true").build();
            for (String predicate : List.of(
                    "starts_with(x, 'ab')",
                    "x LIKE 'ab%'",
                    "x LIKE 'a_b%'",
                    "x LIKE 'a!_%' ESCAPE '!'",
                    "starts_with(x, 'ab') AND starts_with(x, 'abc')",
                    "starts_with(x, 'ab') AND starts_with(x, 'cd')",
                    "starts_with(x, 'ab') OR x = 'z'",
                    "x LIKE '%😀%' OR x = 'z'",
                    "regexp_like(x, 'abc')",
                    "regexp_like(x, '^ab')",
                    "x IS NOT NULL")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(words, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(nativeOnly, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), predicate);
                assertThat(query(nativeOnly, sql)).isFullyPushedDown();
            }
            for (String predicate : List.of("x IS NULL", "NOT starts_with(x, 'ab')", "regexp_like(x, '(?i)abc')", "regexp_like(x, '^abc$')")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(words, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(nativeOnly, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), predicate);
                assertThat(query(nativeOnly, sql)).isNotFullyPushedDown(FilterNode.class);
            }
            for (String field : List.of("doc_value_words", "indexed_words")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(" + field + ", x -> starts_with(x, 'abc'))";
                assertEqualsIgnoreOrder(computeActual(nativeOnly, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), sql);
                if (field.equals("doc_value_words")) {
                    assertThat(query(nativeOnly, sql)).isNotFullyPushedDown(FilterNode.class);
                }
                else {
                    assertThat(query(nativeOnly, sql)).isFullyPushedDown();
                }
            }
            for (String predicate : List.of(
                    "length(x) > 2 AND starts_with(x, 'a')",
                    "x > '\uE000'",
                    "ends_with(x, 'b') OR strpos(x, '😀') > 0",
                    "replace(x, '_', '😀') LIKE '%😀%'",
                    "replace(x, '', '!') LIKE '%!😀!%'",
                    "substr(x, -2, 1) = 'b'",
                    "substring(x, 2) = 'bc'",
                    "trim(x) = 'abc'",
                    "ltrim(x) = 'abc' OR rtrim(x) = 'abc'",
                    "concat(substr(x, 1, 1), 'Z') = 'aZ'",
                    "lower(x) = 'abc' OR upper(x) = 'Z'",
                    "coalesce(x, '') = 'abc'",
                    "CASE WHEN x IS NULL THEN false ELSE length(x) > 2 END")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(words, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(scripted, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), predicate);
            }
            assertThat(query(scripted, "SELECT id FROM " + indexName + " WHERE any_match(words, x -> length(x) > 2 AND starts_with(x, 'a'))"))
                    .isFullyPushedDown();
            for (String expression : List.of("any_match(ignored_words, x -> starts_with(x, 'abc'))", "any_match(unindexed, x -> x IS NOT NULL)")) {
                String sql = "SELECT id FROM " + indexName + " WHERE " + expression;
                assertEqualsIgnoreOrder(computeActual(nativeOnly, sql).getMaterializedRows(), computeActual(localOnly, sql).getMaterializedRows(), expression);
                assertThat(query(nativeOnly, sql)).isNotFullyPushedDown(FilterNode.class);
            }
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchExtendedNumericExpressions()
            throws IOException
    {
        String indexName = "array_typed_numbers_" + randomNameSuffix();
        createIndex(indexName,
                """
                {"_meta":{"trino":{"numbers":{"isArray":true},"reals":{"isArray":true},"doubles":{"isArray":true},"flags":{"isArray":true}}},
                 "properties":{"id":{"type":"keyword"},"numbers":{"type":"integer"},"reals":{"type":"float"},"doubles":{"type":"double"},"flags":{"type":"boolean"}}}
                """);
        try {
            List<List<Integer>> integers = List.of(List.of(-12), List.of(-1), List.of(0), List.of(1), List.of(2, 11), List.of(12), List.of(128), Arrays.asList(null, 12), Arrays.asList((Integer) null), List.of());
            List<List<Double>> doubles = List.of(List.of(-2.5), List.of(-0.0), List.of(0.0), List.of(1.25), List.of(Double.MAX_VALUE), List.of(Double.MIN_NORMAL, (double) Float.MIN_VALUE), List.of(Double.MIN_VALUE), Arrays.asList(null, 2.5), Arrays.asList((Double) null), List.of());
            for (int index = 0; index < integers.size(); index++) {
                List<Float> reals = new ArrayList<>();
                for (Double value : doubles.get(index)) {
                    reals.add(value == null ? null : (float) Math.max(-Float.MAX_VALUE, Math.min(Float.MAX_VALUE, value)));
                }
                index(indexName, ImmutableMap.of("id", Integer.toString(index), "numbers", integers.get(index), "reals", reals, "doubles", doubles.get(index), "flags", index % 2 == 0 ? Arrays.asList(null, true) : List.of(false)));
            }
            index(indexName, ImmutableMap.of("id", "missing"));
            Session enabled = Session.builder(getSession()).setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true").build();
            Session local = Session.builder(enabled).setSystemProperty("allow_pushdown_into_connectors", "false").build();
            for (String predicate : List.of(
                    "abs(x) > 10 OR x = 2",
                    "round(x, -1) = 10",
                    "mod(abs(x), 3) = 0",
                    "CASE WHEN x < 0 THEN -x ELSE x END > 10",
                    "CASE WHEN x IS NULL THEN false ELSE x > 10 END",
                    "coalesce(x, 0) > 10",
                    "coalesce(nullif(x, 0), 1) > 10",
                    "CAST(x AS SMALLINT) > 10",
                    "TRY_CAST(x AS TINYINT) > 10",
                    "x IS NOT DISTINCT FROM 12",
                    "x IS DISTINCT FROM NULL",
                    "x IS DISTINCT FROM 12",
                    "CAST(x AS DECIMAL(12, 2)) + DECIMAL '1.25' > DECIMAL '10.00'",
                    "CAST(x AS DECIMAL(12, 2)) / DECIMAL '3.00' > DECIMAL '1.00'",
                    "mod(CAST(x AS DECIMAL(12, 2)), DECIMAL '3.00') = DECIMAL '0.00'",
                    "round(CAST(x AS DECIMAL(12, 2)), 1) >= DECIMAL '1.00'",
                    "CAST(x AS DECIMAL(30, 10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890'",
                    "round(CAST(x AS DECIMAL(12, 2)) + DECIMAL '0.25', 1) = DECIMAL '2.30'")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
            }
            for (String predicate : List.of("coalesce(x, 0) > 10", "coalesce(nullif(x, 0), 1) > 10", "TRY_CAST(x AS TINYINT) > 10", "CASE WHEN x IS NULL THEN false ELSE x > 10 END", "x IS NOT DISTINCT FROM 12", "x IS DISTINCT FROM NULL")) {
                assertThat(query(enabled, "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicate + ")")).isFullyPushedDown();
            }
            var filtered = getQueryRunner().executeWithPlan(enabled, "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> abs(x) > 10)");
            assertThat(getQueryRunner().getCoordinator().getQueryManager().getFullQueryInfo(filtered.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(5);
            for (String kind : List.of("REAL", "DOUBLE")) {
                String field = kind.equals("REAL") ? "reals" : "doubles";
                for (String predicate : List.of(
                        "x = CAST(0 AS " + kind + ")",
                        "x < CAST(0 AS " + kind + ")",
                        "x >= CAST(0 AS " + kind + ")",
                        "x IN (CAST(0 AS " + kind + "), CAST(1 AS " + kind + "))",
                        "x / x = CAST(nan() AS " + kind + ")",
                        "x / x <> CAST(nan() AS " + kind + ")",
                        "x / x IN (CAST(nan() AS " + kind + "), CAST(2 AS " + kind + "))",
                        "x / x < CAST(nan() AS " + kind + ")",
                        "x / x >= CAST(nan() AS " + kind + ")",
                        "x / x IS NOT DISTINCT FROM CAST(nan() AS " + kind + ")",
                        "x / CAST(0 AS " + kind + ") > CAST(0 AS " + kind + ")",
                        "x + CAST(1.5 AS " + kind + ") > CAST(2 AS " + kind + ") OR mod(x, CAST(2 AS " + kind + ")) = CAST(0 AS " + kind + ")",
                        "round(x, 1) >= CAST(1 AS " + kind + ")",
                        "ceil(x) = floor(x)",
                        "sqrt(abs(x)) > DOUBLE '1'",
                        "pow(x, DOUBLE '2') > DOUBLE '10'")) {
                    String sql = "SELECT id FROM " + indexName + " WHERE any_match(" + field + ", x -> " + predicate + ")";
                    assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
                    assertThat(query(enabled, sql)).isFullyPushedDown();
                }
            }
            Session nativeOnly = Session.builder(getSession()).setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "false").build();
            for (String field : List.of("reals", "doubles")) {
                for (String predicate : List.of("any_match(" + field + ", x -> x = 0)", "contains(" + field + ", 0)", "arrays_overlap(" + field + ", ARRAY[0])")) {
                    String sql = "SELECT id FROM " + indexName + " WHERE " + predicate;
                    assertEqualsIgnoreOrder(computeActual(nativeOnly, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
                    assertThat(query(nativeOnly, sql)).isNotFullyPushedDown(FilterNode.class);
                }
            }
            for (String predicate : List.of("x OR NOT x", "coalesce(x, false)")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(flags, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
                assertThat(query(enabled, sql)).isFullyPushedDown();
            }
            assertThat(query(enabled, "SELECT id FROM " + indexName + " WHERE any_match(flags, x -> coalesce(x, true))"))
                    .isNotFullyPushedDown(FilterNode.class);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testAnyMatchUtcTemporalExpressions()
            throws IOException
    {
        String indexName = "array_typed_dates_" + randomNameSuffix();
        createIndex(indexName,
                """
                {"_meta":{"trino":{"dates":{"isArray":true}}},"properties":{"id":{"type":"keyword"},"dates":{"type":"date"}}}
                """);
        try {
            List<List<String>> values = List.of(
                    List.of("2024-02-29T12:34:56.123Z"),
                    List.of("1969-12-31T23:59:59.999Z"),
                    List.of("2025-01-01T00:00:00Z", "2023-12-31T00:00:00Z"),
                    List.of("1582-10-10T00:00:00Z"),
                    List.of("2024-01-31T00:00:00Z"),
                    Arrays.asList(null, "2024-02-29T00:00:00Z"),
                    Arrays.asList((String) null),
                    List.of());
            for (int index = 0; index < values.size(); index++) {
                index(indexName, ImmutableMap.of("id", Integer.toString(index), "dates", values.get(index)));
            }
            index(indexName, ImmutableMap.of("id", "missing"));
            Session enabled = Session.builder(getSession()).setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true").build();
            Session local = Session.builder(enabled).setSystemProperty("allow_pushdown_into_connectors", "false").build();
            for (String predicate : List.of(
                    "year(x) = 2024",
                    "month(x) = 2 AND day(x) = 29",
                    "hour(x) = 12 OR minute(x) = 59",
                    "second(x) = 56",
                    "day_of_week(x) BETWEEN 1 AND 5",
                    "day_of_year(x) = 60",
                    "date_trunc('day', x) = TIMESTAMP '2024-02-29 00:00:00.000'",
                    "date_add('month', 1, x) > TIMESTAMP '2024-03-01 00:00:00.000'",
                    "date_diff('day', x, TIMESTAMP '2025-01-01 00:00:00.000') >= 0",
                    "day_of_week(date_trunc('week', x)) = 1",
                    "coalesce(year(x), 0) > 2000")) {
                String sql = "SELECT id FROM " + indexName + " WHERE any_match(dates, x -> " + predicate + ")";
                assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
            }
            var filtered = getQueryRunner().executeWithPlan(enabled, "SELECT id FROM " + indexName + " WHERE any_match(dates, x -> year(x) = 2024)");
            assertThat(getQueryRunner().getCoordinator().getQueryManager().getFullQueryInfo(filtered.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(3);
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testScalarExpressionPushdown()
            throws IOException
    {
        String indexName = "scalar_expressions_" + randomNameSuffix();
        createIndex(indexName,
                """
                {"properties":{
                  "id":{"type":"keyword"},"word":{"type":"keyword"},
                  "number":{"type":"integer"},"double_value":{"type":"double"},"real_value":{"type":"float"},
                  "date_value":{"type":"date"},"flag":{"type":"boolean"},
                  "unsafe_word":{"type":"keyword","ignore_above":3},"text_only":{"type":"text"}
                }}
                """);
        try {
            List<String> words = List.of("nice", "SÁCH đẹp", "sá", "đóng gói", "abc", "", "SÁCH khác");
            List<Integer> numbers = List.of(-12, 12, 2, 0, 128, -1, 1);
            List<Double> doubles = List.of(-2.5, -0.0, 0.0, 1.25, Double.MAX_VALUE, Double.MIN_NORMAL, Double.MIN_VALUE);
            List<String> dates = List.of(
                    "2024-02-29T12:34:56.123Z",
                    "1969-12-31T23:59:59.999Z",
                    "2025-01-01T00:00:00Z",
                    "2023-12-31T00:00:00Z",
                    "2024-01-31T00:00:00Z",
                    "1582-10-10T00:00:00Z",
                    "2024-03-01T00:00:00Z");
            for (int row = 0; row < words.size(); row++) {
                index(indexName, ImmutableMap.<String, Object>builder()
                        .put("id", Integer.toString(row)).put("word", words.get(row)).put("number", numbers.get(row))
                        .put("double_value", doubles.get(row)).put("real_value", (float) Math.min(doubles.get(row), Float.MAX_VALUE))
                        .put("date_value", dates.get(row)).put("flag", row % 2 == 0).put("unsafe_word", words.get(row)).put("text_only", words.get(row))
                        .buildOrThrow());
            }
            index(indexName, ImmutableMap.of("id", "missing"));
            Map<String, Object> nulls = new HashMap<>();
            nulls.put("id", "null");
            for (String field : List.of("word", "number", "double_value", "real_value", "date_value", "flag")) {
                nulls.put(field, null);
            }
            index(indexName, nulls);
            Session enabled = Session.builder(getSession())
                    .setCatalogSessionProperty("elasticsearch", "scalar_script_pushdown_enabled", "true")
                    .setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "false").build();
            Session local = Session.builder(enabled).setSystemProperty("allow_pushdown_into_connectors", "false").build();
            Session disabled = Session.builder(enabled).setCatalogSessionProperty("elasticsearch", "scalar_script_pushdown_enabled", "false").build();
            for (String predicate : List.of(
                    "lower(word) LIKE '%sá%'",
                    "upper(word) LIKE '%SÁ%'",
                    "lower(word) LIKE '%abc%'",
                    "length(word) > 2 AND starts_with(word, 'a')",
                    "ends_with(word, 'đẹp') OR strpos(word, 'sá') > 0",
                    "replace(word, '', '!') LIKE '%!s!á!%'",
                    "concat(substr(word, 1, 1), 'Z') = 'aZ'",
                    "trim(word) = 'abc'",
                    "number > 10 OR mod(number, 2) = 0",
                    "number > 10 AND mod(number, 2) = 0",
                    "NOT (number BETWEEN 0 AND 10)",
                    "abs(number) > 10",
                    "round(number, -1) = 10",
                    "coalesce(number, 100) > 10",
                    "coalesce(nullif(number, 0), 100) > 10",
                    "TRY_CAST(number AS TINYINT) IS NULL",
                    "CASE WHEN number IS NULL THEN true ELSE number > 10 END",
                    "CAST(number AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00'",
                    "coalesce(flag, true)",
                    "flag OR NOT flag",
                    "year(date_value) = 2024",
                    "day_of_week(date_trunc('week', date_value)) = 1",
                    "date_add('month', 1, date_value) > TIMESTAMP '2024-03-01 00:00:00.000'",
                    "date_diff('day', date_value, TIMESTAMP '2025-01-01 00:00:00.000') >= 0")) {
                String sql = "SELECT id FROM " + indexName + " WHERE " + predicate;
                assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), predicate);
            }
            for (String kind : List.of("REAL", "DOUBLE")) {
                String field = kind.equals("REAL") ? "real_value" : "double_value";
                for (String body : List.of(
                        "x = CAST(0 AS " + kind + ")",
                        "x / x = CAST(nan() AS " + kind + ")",
                        "x / x IS NOT DISTINCT FROM CAST(nan() AS " + kind + ")",
                        "round(x, 1) >= CAST(1 AS " + kind + ")",
                        "x + CAST(1.5 AS " + kind + ") > CAST(2 AS " + kind + ") OR mod(x, CAST(2 AS " + kind + ")) = CAST(0 AS " + kind + ")")) {
                    String sql = "SELECT id FROM " + indexName + " WHERE " + body.replace("x", field);
                    assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), body);
                    assertThat(query(enabled, sql)).isFullyPushedDown();
                }
            }
            for (String predicate : List.of(
                    "length(word) > 2",
                    "number > 10 OR mod(number, 2) = 0",
                    "coalesce(number, 100) > 10",
                    "TRY_CAST(number AS TINYINT) IS NULL",
                    "CASE WHEN number IS NULL THEN true ELSE number > 10 END")) {
                assertThat(query(enabled, "SELECT id FROM " + indexName + " WHERE " + predicate)).isFullyPushedDown();
            }
            String unicode = "SELECT id FROM " + indexName + " WHERE lower(word) LIKE '%sá%'";
            assertThat(query(enabled, unicode)).matches("VALUES VARCHAR '1', VARCHAR '2', VARCHAR '6'").isNotFullyPushedDown(FilterNode.class);
            assertThat(query(disabled, unicode)).isNotFullyPushedDown(FilterNode.class);
            var filtered = getQueryRunner().executeWithPlan(enabled, unicode);
            assertThat(getQueryRunner().getCoordinator().getQueryManager().getFullQueryInfo(filtered.queryId()).getQueryStats().getPhysicalInputPositions()).isEqualTo(4);
            assertThat(query(enabled, "SELECT count(*) FROM (" + unicode + " LIMIT 2)")).matches("VALUES BIGINT '2'");
            // Four remote candidates contain one non-matching Unicode string. LIMIT must count the three SQL matches.
            assertThat(query(enabled, "SELECT count(*) FROM (" + unicode + " LIMIT 3)")).matches("VALUES BIGINT '3'");
            for (String field : List.of("unsafe_word", "text_only")) {
                String sql = "SELECT id FROM " + indexName + " WHERE lower(" + field + ") LIKE '%sá%'";
                assertEqualsIgnoreOrder(computeActual(enabled, sql).getMaterializedRows(), computeActual(local, sql).getMaterializedRows(), field);
                assertThat(query(enabled, sql)).isNotFullyPushedDown(FilterNode.class);
            }
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    public void testScalarScriptPreservesSqlErrors()
            throws IOException
    {
        String indexName = "scalar_errors_" + randomNameSuffix();
        createIndex(indexName, "{\"properties\":{\"number\":{\"type\":\"integer\"},\"big\":{\"type\":\"long\"}}}");
        try {
            index(indexName, ImmutableMap.of("number", Integer.MAX_VALUE, "big", Long.MIN_VALUE));
            Session enabled = Session.builder(getSession()).setCatalogSessionProperty("elasticsearch", "scalar_script_pushdown_enabled", "true").build();
            assertQueryFails(enabled, "SELECT number FROM " + indexName + " WHERE number + 1 > 0", ".*integer addition overflow.*");
            assertQueryFails(enabled, "SELECT number FROM " + indexName + " WHERE number / (number - number) = 1", ".*Division by zero.*");
            assertQueryFails(enabled, "SELECT number FROM " + indexName + " WHERE CAST(number AS TINYINT) > 0", ".*Out of range for tinyint.*");
            assertQueryFails(enabled, "SELECT big FROM " + indexName + " WHERE abs(big) > 0", ".*out of range for abs.*");
        }
        finally {
            deleteIndex(indexName);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "elasticsearch.array-benchmark", matches = "true")
    public void benchmarkArrayPredicateStrategies()
            throws IOException
    {
        int documents = Integer.getInteger("elasticsearch.array-benchmark.documents", 10_000);
        assertThat(documents).isGreaterThanOrEqualTo(1000);
        assertThat(documents % 20).isZero();
        String indexName = "array_predicate_benchmark_" + randomNameSuffix();
        createIndex(indexName,
                """
                {"_meta":{"trino":{"numbers":{"isArray":true}}},
                 "properties":{"id":{"type":"keyword"},"numbers":{"type":"integer"}}}
                """);
        try {
            for (int batch = 0; batch < documents; batch += 1000) {
                StringBuilder bulk = new StringBuilder();
                for (int id = batch; id < Math.min(batch + 1000, documents); id++) {
                    bulk.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n");
                    bulk.append("{\"id\":\"").append(id).append("\",\"numbers\":[");
                    for (int position = 0; position < 63; position++) {
                        bulk.append(position * 2 + 1).append(',');
                    }
                    int last = 127;
                    if (id % 10 == 0) {
                        last = 2001;
                    }
                    if (id % 20 == 0) {
                        last = 2000;
                    }
                    bulk.append(last).append("]}\n");
                }
                Request load = new Request("POST", "/" + indexName + "/_bulk");
                load.setJsonEntity(bulk.toString());
                try (var input = client.performRequest(load).getEntity().getContent()) {
                    assertThat(new JsonMapper().readTree(input).path("errors").asBoolean()).isFalse();
                }
            }
            client.performRequest(new Request("POST", "/" + indexName + "/_refresh"));
            Session enabled = Session.builder(getSession()).setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true").build();
            Session local = Session.builder(enabled).setSystemProperty("allow_pushdown_into_connectors", "false").build();
            Map<String, String> predicates = Map.of(
                    "NATIVE", "x = 2000",
                    "HYBRID", "x > 1000 AND mod(x, 2) = 0",
                    "SCRIPT", "x / 2 > 500 AND mod(x, 2) = 0",
                    "LOCAL", "x > 1000 AND mod(x, 2) = 0");
            List<String> strategies = List.of("NATIVE", "HYBRID", "SCRIPT", "LOCAL");
            Map<String, List<Long>> elapsed = new HashMap<>();
            Map<String, List<Long>> cpu = new HashMap<>();
            for (String strategy : strategies) {
                elapsed.put(strategy, new ArrayList<>());
                cpu.put(strategy, new ArrayList<>());
            }
            for (int round = 0; round < 7; round++) {
                for (String strategy : round % 2 == 0 ? strategies : strategies.reversed()) {
                    long cpuBefore = elasticsearchCpuMillis();
                    long start = System.nanoTime();
                    MaterializedResultWithPlan result = getQueryRunner().executeWithPlan(
                            strategy.equals("LOCAL") ? local : enabled,
                            "SELECT id FROM " + indexName + " WHERE any_match(numbers, x -> " + predicates.get(strategy) + ")");
                    long nanos = System.nanoTime() - start;
                    long cpuMillis = elasticsearchCpuMillis() - cpuBefore;
                    assertThat(result.result().getRowCount()).isEqualTo(documents / 20);
                    if (round == 0) {
                        var scans = PlanNodeSearcher.searchFrom(result.queryPlan().orElseThrow().getRoot()).whereIsInstanceOfAny(TableScanNode.class).findAll();
                        assertThat(scans).hasSize(1);
                        var handle = (ElasticsearchTableHandle) ((TableScanNode) scans.getFirst()).getTable().connectorHandle();
                        switch (strategy) {
                            case "NATIVE" -> assertThat(handle.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.Term.class);
                            case "HYBRID" -> assertThat(handle.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.And.class);
                            case "SCRIPT" -> assertThat(handle.remotePredicate().orElseThrow()).isInstanceOf(ElasticsearchRemotePredicate.ArrayScript.class);
                            case "LOCAL" -> assertThat(handle.remotePredicate()).isEmpty();
                            default -> throw new IllegalArgumentException("Unexpected strategy " + strategy);
                        }
                    }
                    var statistics = getQueryRunner().getCoordinator().getQueryManager().getFullQueryInfo(result.queryId()).getQueryStats();
                    assertThat(statistics.getPhysicalInputPositions()).isEqualTo(strategy.equals("LOCAL") ? documents : documents / 20);
                    if (round >= 2) {
                        elapsed.get(strategy).add(nanos);
                        cpu.get(strategy).add(cpuMillis);
                        LOG.info("arrayBenchmark engine=%s strategy=%s round=%s documents=%s arrayLength=64 elapsedMs=%s esProcessCpuSampleDeltaMs=%s trinoCpuMs=%s trinoInputRows=%s trinoInputBytes=%s",
                                getClass().getSimpleName(),
                                strategy,
                                round,
                                documents,
                                nanos / 1_000_000,
                                cpuMillis,
                                statistics.getTotalCpuTime().toMillis(),
                                statistics.getPhysicalInputPositions(),
                                statistics.getPhysicalInputDataSize().toBytes());
                    }
                }
            }
            for (String strategy : strategies) {
                LOG.info("arrayBenchmarkMedian engine=%s strategy=%s documents=%s arrayLength=64 measuredRounds=5 elapsedMs=%s esProcessCpuSampleDeltaMs=%s",
                        getClass().getSimpleName(),
                        strategy,
                        documents,
                        elapsed.get(strategy).stream().sorted().toList().get(2) / 1_000_000,
                        cpu.get(strategy).stream().sorted().toList().get(2));
            }
        }
        finally {
            deleteIndex(indexName);
        }
    }

    private long elasticsearchCpuMillis()
            throws IOException
    {
        // Elasticsearch caches process samples. A zero delta on a subsecond query is not evidence of zero CPU use;
        // these coarse samples accompany, rather than replace, the elapsed time and Trino query-stat measurements.
        Request request = new Request("GET", "/_nodes/stats/process?filter_path=nodes.*.process.cpu.total_in_millis");
        try (var input = client.performRequest(request).getEntity().getContent()) {
            long total = 0;
            for (var node : new JsonMapper().readTree(input).path("nodes")) {
                total += node.path("process").path("cpu").path("total_in_millis").asLong();
            }
            return total;
        }
    }

    @Test
    public void testArrayScriptPreservesSqlArithmeticErrors()
            throws IOException
    {
        String indexName = "array_script_errors_" + randomNameSuffix();
        @Language("JSON")
        String properties =
                """
                {
                  "_meta": { "trino": {
                    "numbers": { "isArray": true }, "bigints": { "isArray": true },
                    "tinyints": { "isArray": true }, "smallints": { "isArray": true }
                  } },
                  "properties": {
                    "numbers": { "type": "integer" }, "bigints": { "type": "long" },
                    "tinyints": { "type": "byte" }, "smallints": { "type": "short" }
                  }
                }
                """;
        createIndex(indexName, properties);
        try {
            index(indexName, ImmutableMap.of(
                    "numbers", List.of(Integer.MAX_VALUE),
                    "bigints", List.of(Long.MIN_VALUE),
                    "tinyints", List.of(Byte.MAX_VALUE),
                    "smallints", List.of(Short.MAX_VALUE)));
            Session enabled = Session.builder(getSession())
                    .setCatalogSessionProperty("elasticsearch", "array_script_pushdown_enabled", "true")
                    .build();
            assertQueryFails(enabled, "SELECT numbers FROM " + indexName + " WHERE any_match(numbers, x -> x + 1 > 0)", ".*integer addition overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> x / BIGINT '-1' > 0)", ".*bigint division overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> x + BIGINT '-1' < 0)", ".*bigint addition overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> x - BIGINT '1' < 0)", ".*bigint subtraction overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> x * BIGINT '-1' > 0)", ".*bigint multiplication overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> -x > 0)", ".*bigint negation overflow.*");
            assertQueryFails(enabled, "SELECT tinyints FROM " + indexName + " WHERE any_match(tinyints, x -> x + TINYINT '1' > 0)", ".*tinyint addition overflow.*");
            assertQueryFails(enabled, "SELECT smallints FROM " + indexName + " WHERE any_match(smallints, x -> x + SMALLINT '1' > 0)", ".*smallint addition overflow.*");
            assertQueryFails(enabled, "SELECT numbers FROM " + indexName + " WHERE any_match(numbers, x -> x / (x - x) = 1)", ".*Division by zero.*");
            assertQueryFails(enabled, "SELECT numbers FROM " + indexName + " WHERE any_match(numbers, x -> x / 0 > 0)", ".*Division by zero.*");
            assertQueryFails(enabled, "SELECT numbers FROM " + indexName + " WHERE any_match(numbers, x -> CAST(x AS TINYINT) > 0)", ".*Out of range for tinyint.*");
            assertQueryFails(enabled, "SELECT numbers FROM " + indexName + " WHERE any_match(numbers, x -> round(x, -19) > 0)", ".*numerical overflow.*");
            assertQueryFails(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> abs(x) > 0)", ".*out of range for abs.*");
            assertThat(query(enabled, "SELECT bigints FROM " + indexName + " WHERE any_match(bigints, x -> mod(x, BIGINT '-1') = BIGINT '0')"))
                    .matches("SELECT ARRAY[BIGINT '-9223372036854775808']")
                    .isFullyPushedDown();
        }
        finally {
            deleteIndex(indexName);
        }
    }

    private void createIndex(String indexName, @Language("JSON") String properties)
            throws IOException
    {
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity("{\"mappings\": " + properties + "}");
        client.performRequest(request);
    }

    private void index(String index, Map<String, Object> document)
            throws IOException
    {
        String json = new JsonMapper().writeValueAsString(document);
        Request request = new Request("PUT", format("/%s/_doc/%s?refresh", index, System.nanoTime()));
        request.setJsonEntity(json);
        client.performRequest(request);
    }

    private void deleteIndex(String indexName)
            throws IOException
    {
        client.performRequest(new Request("DELETE", "/" + indexName));
    }
}
