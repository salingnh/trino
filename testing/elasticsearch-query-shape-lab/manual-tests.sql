-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Connect with --catalog es8_scroll --schema default.
-- Rerun the unqualified queries with es7_scroll, es7_pit, and es8_pit.

SHOW CATALOGS;
SHOW TABLES;
DESCRIBE lab_qs_shapes;
SELECT * FROM lab_qs_shapes ORDER BY id;

-- LIMIT / TopN, including more than one remote page.
SELECT id FROM lab_qs_shapes LIMIT 3;
SELECT id FROM lab_qs_shapes ORDER BY id DESC LIMIT 3;
SELECT id, agg_0 FROM lab_qs_shapes ORDER BY agg_0 ASC NULLS FIRST, id DESC LIMIT 3;
SELECT id, agg_0 FROM lab_qs_shapes ORDER BY agg_0 ASC NULLS LAST, id DESC LIMIT 3;
SELECT id, agg_0 FROM lab_qs_shapes ORDER BY agg_0 DESC NULLS FIRST, id DESC LIMIT 3;
SELECT id, agg_0 FROM lab_qs_shapes ORDER BY agg_0 DESC NULLS LAST, id DESC LIMIT 3;
SELECT count(*) FROM (SELECT id FROM lab_qs_volume ORDER BY id DESC LIMIT 2501);

-- An outer TopN/filter must operate on the already limited inner relation.
SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) ORDER BY id DESC LIMIT 2;
SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) WHERE id > 2498;

-- Alias filter/routing: only active IDs 1 and 10 are visible.
SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 1;
SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 10;

-- AVG inputs: b=TINYINT, s=SMALLINT, i=INTEGER, l=BIGINT, f=REAL, amount=DOUBLE.
SELECT avg(b), avg(s), avg(i), avg(l), avg(f), avg(amount) FROM lab_qs_shapes;
SELECT g, avg(b), avg(s), avg(i), avg(l) FROM lab_qs_shapes GROUP BY g;
SELECT avg(l) FROM lab_qs_boundaries WHERE g = 'near53';
SELECT avg(l) FROM lab_qs_boundaries WHERE g = 'max';
SELECT avg(l) FROM lab_qs_boundaries WHERE g = 'min';
SELECT avg(l) FROM lab_qs_shapes WHERE id < 0;
SELECT avg(i) FROM lab_qs_shapes WHERE id = 5;
SELECT b, CAST(b AS BIGINT) FROM lab_qs_shapes ORDER BY id;
SELECT avg(value) FROM lab_qs_volume;

-- agg_0 is an actual grouping column, so the metric output must use another name.
SELECT agg_0, count(*) FROM lab_qs_shapes GROUP BY agg_0;
SELECT dt, flag, agg_0, avg(i) FROM lab_qs_shapes GROUP BY dt, flag, agg_0;
SELECT agg_0, sum(amount) FROM lab_qs_shapes GROUP BY agg_0;
SELECT obj.value, count(*) FROM lab_qs_shapes GROUP BY obj.value;
SELECT approx_distinct(g) FROM lab_qs_shapes;

-- These aggregates intentionally stay in Trino.
SELECT sum(l) FROM lab_qs_shapes;
SELECT min(l), max(l) FROM lab_qs_shapes;
SELECT avg(DISTINCT i) FROM lab_qs_shapes;

-- Mapping safety: normalized/ignored/non-doc-value/null-value fields stay local.
SELECT city, count(*) FROM lab_qs_normalized GROUP BY city;
SELECT city, count(*) FROM lab_qs_ignored GROUP BY city;
SELECT avg(n) FROM lab_qs_nodoc;
SELECT avg(n) FROM lab_qs_nullvalue;

-- Signed-zero grouping and sort stay local.
SELECT amount, count(*) FROM lab_qs_shapes GROUP BY amount;
SELECT f, count(*) FROM lab_qs_shapes GROUP BY f;
SELECT id, amount FROM lab_qs_shapes ORDER BY amount ASC NULLS FIRST, id LIMIT 2;
SELECT id, amount FROM lab_qs_shapes ORDER BY amount ASC NULLS LAST, id LIMIT 2;
SELECT id, amount FROM lab_qs_shapes ORDER BY amount DESC NULLS FIRST, id LIMIT 2;
SELECT id, amount FROM lab_qs_shapes ORDER BY amount DESC NULLS LAST, id LIMIT 2;

-- Typed composite pagination at four configured page sizes.
SELECT dt, flag, agg_0, count(*) FROM es8_page1.default.lab_qs_shapes GROUP BY dt, flag, agg_0;
SELECT dt, flag, agg_0, count(*) FROM es8_page2.default.lab_qs_shapes GROUP BY dt, flag, agg_0;
SELECT dt, flag, agg_0, count(*) FROM es8_page1000.default.lab_qs_shapes GROUP BY dt, flag, agg_0;
SELECT dt, flag, agg_0, count(*) FROM es8_page10000.default.lab_qs_shapes GROUP BY dt, flag, agg_0;

-- Force enumeration of 50,000 unique grouping keys, returning only the count.
SELECT count(*) FROM (SELECT id FROM es8_page1000.default.lab_qs_volume GROUP BY id);
SELECT count(*) FROM (SELECT id FROM es8_page10000.default.lab_qs_volume GROUP BY id);

-- Check plans: pushed AVG has no local Aggregate; fallback SUM does.
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(i) FROM lab_qs_shapes;
EXPLAIN (TYPE DISTRIBUTED) SELECT sum(l) FROM lab_qs_shapes;
EXPLAIN (TYPE DISTRIBUTED) SELECT city, count(*) FROM lab_qs_normalized GROUP BY city;
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY id DESC LIMIT 3;

-- Existing catalog switch: same result (-0.5), different execution plan.
SET SESSION es8_scroll.aggregation_pushdown_enabled = false;
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(value) FROM es8_scroll.default.lab_qs_volume;
SELECT avg(value) FROM es8_scroll.default.lab_qs_volume;
RESET SESSION es8_scroll.aggregation_pushdown_enabled;
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(value) FROM es8_scroll.default.lab_qs_volume;
SELECT avg(value) FROM es8_scroll.default.lab_qs_volume;

-- Full engine baseline for other query shapes; reset before continuing.
SET SESSION allow_pushdown_into_connectors = false;
SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 1;
SELECT avg(value) FROM lab_qs_volume;
RESET SESSION allow_pushdown_into_connectors;
