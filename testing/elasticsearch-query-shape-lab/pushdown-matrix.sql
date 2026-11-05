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


-- EXPLAIN cases collected on VM 172.16.11.138, 2026-11-05.
-- USE es8_scroll.default; use es7_scroll.default for ES7.
-- Original 5a5b158; old cb0fc90; new 58b28a2.
-- E02 and W01/W02 apply only to custom prod old/new.
-- LIMIT/TopN may retain global engine operators despite connector pushdown.

-- M01: table/row 1.1; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT obj.value, count(*) FROM lab_qs_shapes GROUP BY obj.value;

-- M02: table/row 1.1; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT city, count(*) FROM lab_qs_ignored GROUP BY city;

-- M03: table/row 1.1; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT city, count(*) FROM lab_qs_normalized GROUP BY city;

-- Z01: table/row 1.2; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY amount, id LIMIT 2;

-- Z02: table/row 1.2; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT amount, count(*) FROM lab_qs_shapes GROUP BY amount;

-- C01: table/row 1.3; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT agg_0, count(*) FROM lab_qs_shapes GROUP BY agg_0;

-- R01: table/row 1.4; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(amount) FROM lab_qs_shapes;

-- T01: table/row 1.5; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT dt, count(*) FROM lab_qs_shapes GROUP BY dt;

-- P01: table/row 1.6; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id, count(*) FROM lab_qs_volume GROUP BY id;

-- Ab: table/row 1.7; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(b) FROM lab_qs_shapes;

-- As: table/row 1.7; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(s) FROM lab_qs_shapes;

-- Ai: table/row 1.7; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(i) FROM lab_qs_shapes;

-- Al: table/row 1.7; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(l) FROM lab_qs_shapes;

-- L01: table/row 2.1; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM (SELECT id FROM lab_qs_shapes LIMIT 5) LIMIT 2;

-- L02: table/row 2.2; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM (SELECT id FROM lab_qs_shapes ORDER BY id LIMIT 5) ORDER BY id LIMIT 2;

-- L03: table/row 2.3; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_volume ORDER BY id DESC LIMIT 2501;

-- N01: table/row 2.4; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY agg_0 ASC NULLS FIRST, id DESC LIMIT 3;

-- N02: table/row 2.4; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY agg_0 ASC NULLS LAST, id DESC LIMIT 3;

-- N03: table/row 2.4; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY agg_0 DESC NULLS FIRST, id DESC LIMIT 3;

-- N04: table/row 2.4; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes ORDER BY agg_0 DESC NULLS LAST, id DESC LIMIT 3;

-- B01: table/row 2.5; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT count(*) FROM (SELECT id FROM lab_qs_shapes LIMIT 2);

-- B02: table/row 2.5; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM (SELECT id FROM lab_qs_volume ORDER BY id LIMIT 2501) ORDER BY id DESC LIMIT 2;

-- G01: table/row 2.6; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT count(*), sum(amount), avg(amount), min(amount), max(amount) FROM lab_qs_shapes;

-- G02: table/row 2.7; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT g, flag, agg_0, count(*) FROM lab_qs_shapes GROUP BY g, flag, agg_0;

-- S01: table/row 2.8; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT sum(amount) FROM lab_qs_shapes WHERE id = 5;

-- F01: table/row 2.9; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(DISTINCT i) FROM lab_qs_shapes;

-- F02: table/row 2.9; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT sum(amount) FILTER (WHERE id > 1) FROM lab_qs_shapes;

-- F03: table/row 2.9; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT array_agg(i ORDER BY id) FROM lab_qs_shapes;

-- F04: table/row 2.9; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT g, flag, count(*) FROM lab_qs_shapes GROUP BY GROUPING SETS ((g), (flag));

-- I01: table/row 2.10; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT sum(i) FROM lab_qs_shapes;

-- I02: table/row 2.10; expected new pushdown: False
EXPLAIN (TYPE DISTRIBUTED) SELECT min(l), max(l) FROM lab_qs_boundaries;

-- D01: table/row 2.11; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT approx_distinct(g) FROM lab_qs_shapes;

-- IR01: table/row 2.12; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM "lab_qs_shapes:g:a" ORDER BY id LIMIT 2;

-- IR02: table/row 2.12; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_shapes WHERE any_match(tags, t -> t = 'a') ORDER BY id LIMIT 2;

-- E01: table/row 2.13; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM lab_qs_active ORDER BY id DESC LIMIT 1;

-- E02: table/row 2.13; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT id FROM es8_pit.default.lab_qs_shapes ORDER BY id LIMIT 2;

-- Q01: table/row 2.14; expected new pushdown: True
EXPLAIN (TYPE DISTRIBUTED) SELECT g, count(*) FROM lab_qs_shapes GROUP BY g;

-- W01: table/row 2.15; expected new pushdown: True
SET SESSION es8_scroll.aggregation_pushdown_enabled=true;
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(amount) FROM lab_qs_shapes;
RESET SESSION es8_scroll.aggregation_pushdown_enabled;

-- W02: table/row 2.15; expected new pushdown: False
SET SESSION es8_scroll.aggregation_pushdown_enabled=false;
EXPLAIN (TYPE DISTRIBUTED) SELECT avg(amount) FROM lab_qs_shapes;
RESET SESSION es8_scroll.aggregation_pushdown_enabled;
