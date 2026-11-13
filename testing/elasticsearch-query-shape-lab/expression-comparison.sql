-- Executed SQL matrix. Run with username expression-comparison and time zone UTC.
-- Use current runtime on port 18093; scripts are enabled by catalog defaults.

USE es8_compare.default;
SET TIME ZONE 'UTC';
-- A-N01: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x = 12);

-- S-N01: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n = 12;

-- A-N02: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x <> 12);

-- S-N02: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n <> 12;

-- A-N03: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x < 10);

-- S-N03: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n < 10;

-- A-N04: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x <= 10);

-- S-N04: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n <= 10;

-- A-N05: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10);

-- S-N05: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10;

-- A-N06: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x >= 10);

-- S-N06: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n >= 10;

-- A-N07: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x BETWEEN 5 AND 12);

-- S-N07: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n BETWEEN 5 AND 12;

-- A-N08: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT BETWEEN 5 AND 12);

-- S-N08: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT BETWEEN 5 AND 12;

-- A-N09: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5, 12));

-- S-N09: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12);

-- A-N10: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));

-- S-N10: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12, NULL);

-- A-N11: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));

-- S-N11: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, 12);

-- A-N12: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT IN (5, NULL));

-- S-N12: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, NULL);

-- A-N13: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> NOT (x = 12));

-- S-N13: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (n = 12);

-- A-N14: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NOT NULL);

-- S-N14: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT NULL;

-- A-N15: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);

-- S-N15: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n > 10;

-- A-N16: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x < 0 OR x > 10);

-- S-N16: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n < 0 OR n > 10;

-- A-N17: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND x < 5);

-- S-N17: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND n < 5;

-- A-N18: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);

-- S-N18: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT DISTINCT FROM 12;

-- A-N19: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS DISTINCT FROM NULL);

-- S-N19: Native integral / ba giá trị
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM NULL;

-- A-NNULL: Giữ semantics NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NULL);

-- S-NNULL: Giữ semantics NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NULL;

-- A-NDISTINCT: Giữ semantics NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS DISTINCT FROM 12);

-- S-NDISTINCT: Giữ semantics NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM 12;

-- A-NTRUE: Giữ semantics NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRUE OR x > 10);

-- M1: Membership hiện có
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(numbers, 12);

-- M2: Membership hiện có
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[5,12]);

-- M3: Membership hiện có
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(words, 'abc');

-- M4: Membership hiện có
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(flags, true);

-- M5: Membership hiện có
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[12,CAST(NULL AS INTEGER)]);

-- A-P1: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);

-- S-P1: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND sin(n) > 0;

-- A-P2: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 OR sin(x) > 0);

-- S-P2: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 OR sin(n) > 0;

-- A-P3: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));

-- S-P3: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE (n > 10 AND sin(n) > 0) OR (n < 0 AND cos(n) > 0);

-- A-P4: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);

-- S-P4: Điều kiện cần; scripts OFF
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n > 10;

-- A-I01: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x + 1 > 10);

-- S-I01: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n + 1 > 10;

-- A-I02: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x - 1 > 10);

-- S-I02: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n - 1 > 10;

-- A-I03: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x * 2 > 20);

-- S-I03: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n * 2 > 20;

-- A-I04: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x / 2 > 5);

-- S-I04: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n / 2 > 5;

-- A-I05: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x % 2 = 0);

-- S-I05: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n % 2 = 0;

-- A-I06: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,2)=0);

-- S-I06: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2)=0;

-- A-I07: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,-1)=0);

-- S-I07: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,-1)=0;

-- A-I08: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> -x > 5);

-- S-I08: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -n > 5;

-- A-I09: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);

-- S-I09: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 OR mod(n,2)=0;

-- A-I10: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);

-- S-I10: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND mod(n,2)=0;

-- A-I11: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));

-- S-I11: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (mod(n,2)=0);

-- A-I12: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(x) > 10 OR x = 2);

-- S-I12: Số học integral / hybrid
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n) > 10 OR n = 2;

-- A-WIDTH-TINYINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -> x + TINYINT '1' > TINYINT '10');

-- S-WIDTH-TINYINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE tiny + TINYINT '1' > TINYINT '10';

-- A-MOD-TINYINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');

-- S-MOD-TINYINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(tiny, TINYINT '2') = TINYINT '0';

-- A-WIDTH-SMALLINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -> x + SMALLINT '1' > SMALLINT '10');

-- S-WIDTH-SMALLINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE small + SMALLINT '1' > SMALLINT '10';

-- A-MOD-SMALLINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -> mod(x, SMALLINT '2') = SMALLINT '0');

-- S-MOD-SMALLINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(small, SMALLINT '2') = SMALLINT '0';

-- A-WIDTH-BIGINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -> x + BIGINT '1' > BIGINT '10');

-- S-WIDTH-BIGINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE big + BIGINT '1' > BIGINT '10';

-- A-MOD-BIGINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -> mod(x, BIGINT '2') = BIGINT '0');

-- S-MOD-BIGINT: Độ rộng kiểu số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(big, BIGINT '2') = BIGINT '0';

-- A-MATH1: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(x)>5);

-- S-MATH1: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n)>5;

-- A-MATH2: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> ceil(x)>=1);

-- S-MATH2: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(n)>=1;

-- A-MATH3: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> floor(x)<1);

-- S-MATH3: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE floor(n)<1;

-- A-MATH4: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(x, -1)=10);

-- S-MATH4: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(n, -1)=10;

-- A-MATH5: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> truncate(x)>5);

-- S-MATH5: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(n)>5;

-- A-MATH6: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sign(x)=-1);

-- S-MATH6: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(n)=-1;

-- A-MATH7: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sqrt(abs(x))>DOUBLE '2');

-- S-MATH7: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sqrt(abs(n))>DOUBLE '2';

-- A-MATH8: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> pow(x,DOUBLE '2')>DOUBLE '100');

-- S-MATH8: Hàm số
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE pow(n,DOUBLE '2')>DOUBLE '100';

-- A-REAL01: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));

-- S-REAL01: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f + CAST(1.5 AS REAL) > CAST(2 AS REAL);

-- A-REAL02: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x - CAST(1.5 AS REAL) < CAST(0 AS REAL));

-- S-REAL02: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f - CAST(1.5 AS REAL) < CAST(0 AS REAL);

-- A-REAL03: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x * CAST(2 AS REAL) > CAST(1 AS REAL));

-- S-REAL03: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f * CAST(2 AS REAL) > CAST(1 AS REAL);

-- A-REAL04: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / CAST(0 AS REAL) > CAST(0 AS REAL));

-- S-REAL04: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / CAST(0 AS REAL) > CAST(0 AS REAL);

-- A-REAL05: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> mod(x, CAST(2 AS REAL)) = CAST(0 AS REAL));

-- S-REAL05: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(f, CAST(2 AS REAL)) = CAST(0 AS REAL);

-- A-REAL06: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> -x > CAST(0 AS REAL));

-- S-REAL06: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -f > CAST(0 AS REAL);

-- A-REAL07: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x = CAST(0 AS REAL));

-- S-REAL07: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f = CAST(0 AS REAL);

-- A-REAL08: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x >= CAST(0 AS REAL));

-- S-REAL08: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f >= CAST(0 AS REAL);

-- A-REAL09: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x = CAST(nan() AS REAL));

-- S-REAL09: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f = CAST(nan() AS REAL);

-- A-REAL10: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x <> CAST(nan() AS REAL));

-- S-REAL10: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f <> CAST(nan() AS REAL);

-- A-REAL11: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x < CAST(nan() AS REAL));

-- S-REAL11: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f < CAST(nan() AS REAL);

-- A-REAL12: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x IS NOT DISTINCT FROM CAST(nan() AS REAL));

-- S-REAL12: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IS NOT DISTINCT FROM CAST(nan() AS REAL);

-- A-REAL13: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x IN (CAST(nan() AS REAL), CAST(2 AS REAL)));

-- S-REAL13: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IN (CAST(nan() AS REAL), CAST(2 AS REAL));

-- A-REAL14: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> round(x,1)>=CAST(1 AS REAL));

-- S-REAL14: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(f,1)>=CAST(1 AS REAL);

-- A-REAL15: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> ceil(x)=floor(x));

-- S-REAL15: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(f)=floor(f);

-- A-REAL16: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> truncate(x)>CAST(0 AS REAL));

-- S-REAL16: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(f)>CAST(0 AS REAL);

-- A-REAL17: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> sign(x)=CAST(-1 AS REAL));

-- S-REAL17: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(f)=CAST(-1 AS REAL);

-- A-DOUBLE01: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x + CAST(1.5 AS DOUBLE) > CAST(2 AS DOUBLE));

-- S-DOUBLE01: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d + CAST(1.5 AS DOUBLE) > CAST(2 AS DOUBLE);

-- A-DOUBLE02: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x - CAST(1.5 AS DOUBLE) < CAST(0 AS DOUBLE));

-- S-DOUBLE02: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d - CAST(1.5 AS DOUBLE) < CAST(0 AS DOUBLE);

-- A-DOUBLE03: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x * CAST(2 AS DOUBLE) > CAST(1 AS DOUBLE));

-- S-DOUBLE03: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d * CAST(2 AS DOUBLE) > CAST(1 AS DOUBLE);

-- A-DOUBLE04: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / CAST(0 AS DOUBLE) > CAST(0 AS DOUBLE));

-- S-DOUBLE04: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / CAST(0 AS DOUBLE) > CAST(0 AS DOUBLE);

-- A-DOUBLE05: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> mod(x, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE));

-- S-DOUBLE05: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(d, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE);

-- A-DOUBLE06: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> -x > CAST(0 AS DOUBLE));

-- S-DOUBLE06: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -d > CAST(0 AS DOUBLE);

-- A-DOUBLE07: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x = CAST(0 AS DOUBLE));

-- S-DOUBLE07: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d = CAST(0 AS DOUBLE);

-- A-DOUBLE08: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x >= CAST(0 AS DOUBLE));

-- S-DOUBLE08: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d >= CAST(0 AS DOUBLE);

-- A-DOUBLE09: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x = CAST(nan() AS DOUBLE));

-- S-DOUBLE09: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d = CAST(nan() AS DOUBLE);

-- A-DOUBLE10: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x <> CAST(nan() AS DOUBLE));

-- S-DOUBLE10: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d <> CAST(nan() AS DOUBLE);

-- A-DOUBLE11: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x < CAST(nan() AS DOUBLE));

-- S-DOUBLE11: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d < CAST(nan() AS DOUBLE);

-- A-DOUBLE12: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x IS NOT DISTINCT FROM CAST(nan() AS DOUBLE));

-- S-DOUBLE12: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IS NOT DISTINCT FROM CAST(nan() AS DOUBLE);

-- A-DOUBLE13: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE)));

-- S-DOUBLE13: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE));

-- A-DOUBLE14: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> round(x,1)>=CAST(1 AS DOUBLE));

-- S-DOUBLE14: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(d,1)>=CAST(1 AS DOUBLE);

-- A-DOUBLE15: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> ceil(x)=floor(x));

-- S-DOUBLE15: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(d)=floor(d);

-- A-DOUBLE16: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> truncate(x)>CAST(0 AS DOUBLE));

-- S-DOUBLE16: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(d)>CAST(0 AS DOUBLE);

-- A-DOUBLE17: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> sign(x)=CAST(-1 AS DOUBLE));

-- S-DOUBLE17: REAL/DOUBLE; signed zero; NaN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(d)=CAST(-1 AS DOUBLE);

-- A-DEC01: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC01: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC02: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) - DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC02: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) - DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC03: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) * DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC03: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) * DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC04: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00');

-- S-DEC04: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00';

-- A-DEC05: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(CAST(x AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00');

-- S-DEC05: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(CAST(n AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00';

-- A-DEC06: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(CAST(x AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30');

-- S-DEC06: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(CAST(n AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30';

-- A-DEC07: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(30,10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890');

-- S-DEC07: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(30,10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890';

-- A-DEC08: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(CAST(x AS DECIMAL(12,2)))>DECIMAL '10.00');

-- S-DEC08: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(CAST(n AS DECIMAL(12,2)))>DECIMAL '10.00';

-- A-DEC09: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sign(CAST(x AS DECIMAL(12,2)))=DECIMAL '-1');

-- S-DEC09: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(CAST(n AS DECIMAL(12,2)))=DECIMAL '-1';

-- A-DEC10: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> floor(CAST(x AS DECIMAL(12,2)))=ceil(CAST(x AS DECIMAL(12,2))));

-- S-DEC10: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE floor(CAST(n AS DECIMAL(12,2)))=ceil(CAST(n AS DECIMAL(12,2)));

-- A-DEC11: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(CAST(x AS DECIMAL(12,2)) AS INTEGER)>10);

-- S-DEC11: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS INTEGER)>10;

-- A-DEC12: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(CAST(x AS DECIMAL(12,2)) AS DOUBLE)>DOUBLE '10');

-- S-DEC12: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS DOUBLE)>DOUBLE '10';

-- A-DEC13: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS SMALLINT)>10);

-- S-DEC13: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS SMALLINT)>10;

-- A-DEC14: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRY_CAST(x AS TINYINT)>10);

-- S-DEC14: DECIMAL và CAST
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT)>10;

-- A-NULL1: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(x,0)>10);

-- S-NULL1: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,0)>10;

-- A-NULL2: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(x,100)>10);

-- S-NULL2: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,100)>10;

-- A-NULL3: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> nullif(x,0)>1);

-- S-NULL3: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE nullif(n,0)>1;

-- A-NULL4: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);

-- S-NULL4: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(nullif(n,0),1)>10;

-- A-NULL5: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRY_CAST(x AS TINYINT) IS NULL);

-- S-NULL5: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;

-- A-NULL6: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);

-- S-NULL6: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN false ELSE n>10 END;

-- A-NULL7: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN true ELSE x>10 END);

-- S-NULL7: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN true ELSE n>10 END;

-- A-NULL8: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> IF(x>10,true,false));

-- S-NULL8: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE IF(n>10,true,false);

-- A-BOOL1: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> x OR NOT x);

-- S-BOOL1: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE flag OR NOT flag;

-- A-BOOL2: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> coalesce(x,false));

-- S-BOOL2: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,false);

-- A-BOOL3: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> coalesce(x,true));

-- S-BOOL3: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,true);

-- A-BOOL4: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);

-- S-BOOL4: BOOLEAN
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE flag IS NOT DISTINCT FROM true;

-- A-TXT01: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'abc'));

-- S-TXT01: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc');

-- A-TXT02: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE 'ab%');

-- S-TXT02: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'ab%';

-- A-TXT03: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE '%b%');

-- S-TXT03: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%b%';

-- A-TXT04: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE 'a_b');

-- S-TXT04: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'a_b';

-- A-TXT05: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');

-- S-TXT05: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%a!_%' ESCAPE '!';

-- A-TXT06: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));

-- S-TXT06: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'a') AND starts_with(word,'ab');

-- A-TXT07: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'ab') OR x='nice');

-- S-TXT07: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'ab') OR word='nice';

-- A-TXT08: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> length(x)>2);

-- S-TXT08: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)>2;

-- A-TXT09: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> ends_with(x,'b'));

-- S-TXT09: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ends_with(word,'b');

-- A-TXT10: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> strpos(x,'😀')>0);

-- S-TXT10: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE strpos(word,'😀')>0;

-- A-TXT11: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> replace(x,'_','😀') LIKE '%😀%');

-- S-TXT11: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'_','😀') LIKE '%😀%';

-- A-TXT12: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> replace(x,'','!') LIKE '%!s!á!%');

-- S-TXT12: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'','!') LIKE '%!s!á!%';

-- A-TXT13: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');

-- S-TXT13: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE concat(substr(word,1,1),'Z')='aZ';

-- A-TXT14: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> (x || 'Z')='abcZ');

-- S-TXT14: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE (word || 'Z')='abcZ';

-- A-TXT15: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> substr(x,-2,1)='b');

-- S-TXT15: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE substr(word,-2,1)='b';

-- A-TXT16: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> substring(x,2)='bc');

-- S-TXT16: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE substring(word,2)='bc';

-- A-TXT17: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> trim(x)='abc');

-- S-TXT17: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE trim(word)='abc';

-- A-TXT18: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> ltrim(x)='abc');

-- S-TXT18: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ltrim(word)='abc';

-- A-TXT19: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> rtrim(x)='abc');

-- S-TXT19: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE rtrim(word)='abc';

-- A-TXT20: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');

-- S-TXT20: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%';

-- A-TXT21: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> upper(x) LIKE '%SÁ%');

-- S-TXT21: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE upper(word) LIKE '%SÁ%';

-- A-TXT22: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x > '');

-- S-TXT22: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word > '';

-- A-TXT23: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> length(x)>2 AND starts_with(x,'a'));

-- S-TXT23: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)>2 AND starts_with(word,'a');

-- A-TXT24: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'abc') AND lower(x) LIKE '%b%');

-- S-TXT24: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc') AND lower(word) LIKE '%b%';

-- A-TXT25: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'abc'));

-- S-TXT25: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'abc');

-- A-TXT26: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'(?i)^abc'));

-- S-TXT26: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'(?i)^abc');

-- A-TXT27: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'^abc$'));

-- S-TXT27: Keyword / Unicode / chuỗi
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'^abc$');

-- A-MAP-IGNORE: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(ignored_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-IGNORE: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(ignored_word) LIKE '%sá%';

-- A-MAP-NORMAL: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(normalized_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-NORMAL: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(normalized_word) LIKE '%sá%';

-- A-MAP-NODV: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(nodv_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-NODV: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(nodv_word) LIKE '%sá%';

-- A-MAP-DOCONLY: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doconly_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-DOCONLY: Mapping guard / doc values
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(doconly_word) LIKE '%sá%';

-- S-CASEPATH: Tên field phân biệt hoa thường
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(reviewcomment)>2;

-- A-DATE1: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> year(x)=2024);

-- S-DATE1: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE year(dt)=2024;

-- A-DATE2: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> month(x)=2);

-- S-DATE2: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE month(dt)=2;

-- A-DATE3: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day(x)=29);

-- S-DATE3: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day(dt)=29;

-- A-DATE4: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> hour(x)=12);

-- S-DATE4: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE hour(dt)=12;

-- A-DATE5: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> minute(x)=59);

-- S-DATE5: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE minute(dt)=59;

-- A-DATE6: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> second(x)=56);

-- S-DATE6: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE second(dt)=56;

-- A-DATE7: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day_of_week(x) BETWEEN 1 AND 5);

-- S-DATE7: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_week(dt) BETWEEN 1 AND 5;

-- A-DATE8: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day_of_year(x)=60);

-- S-DATE8: TIMESTAMP(3) UTC / extract
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_year(dt)=60;

-- A-TRUNC-millisecond: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('millisecond',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-millisecond: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('millisecond',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-millisecond: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('millisecond',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-millisecond: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('millisecond',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-second: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('second',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-second: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('second',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-second: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('second',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-second: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('second',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-minute: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('minute',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-minute: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('minute',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-minute: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('minute',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-minute: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('minute',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-hour: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('hour',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-hour: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('hour',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-hour: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('hour',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-hour: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('hour',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-day: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('day',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-day: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('day',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-day: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('day',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-day: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-week: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('week',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-week: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('week',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-week: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('week',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-week: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('week',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-month: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('month',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-month: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('month',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-month: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-month: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('month',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-quarter: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('quarter',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-quarter: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('quarter',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-quarter: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('quarter',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-quarter: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('quarter',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-year: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('year',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-year: date_trunc UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('year',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-year: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('year',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-year: date_add UTC
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('year',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-DIFF-millisecond: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('millisecond',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-millisecond: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('millisecond',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-second: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('second',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-second: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('second',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-minute: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('minute',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-minute: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('minute',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-hour: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('hour',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-hour: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('hour',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-day: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('day',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-day: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-week: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('week',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-week: date_diff đơn vị cố định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('week',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-REVERSE: date_diff đảo đối số; NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);

-- S-DIFF-REVERSE: date_diff đảo đối số; NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',dt)<=0;

-- A-DATE-NULL-ERROR: Lỗi trên phần tử NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('day',coalesce(year(x),BIGINT '9223372036854775807')+BIGINT '1',x)<TIMESTAMP '0001-01-01 00:00:00.000');

-- S-DATE-NULL-ERROR: Lỗi trên phần tử NULL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',coalesce(year(dt),BIGINT '9223372036854775807')+BIGINT '1',dt)<TIMESTAMP '0001-01-01 00:00:00.000';

-- A-DIFF-MONTH: Fallback được chủ định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('month',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-MONTH: Fallback được chủ định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('month',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-CAPTURE: Fallback được chủ định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -> x>n);

-- A-CAPTURE-PREFILTER: Điều kiện cần với biến capture
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -> x>10 AND x>n);

-- S-MULTICOL: Fallback được chủ định
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n+small>10;

-- S-MIXED-AND: Ghép các cột scalar
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n>10 AND lower(word) LIKE '%sá%';

-- S-MIXED-OR: Ghép các cột scalar
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n>10 OR lower(word) LIKE '%sá%';

-- A-BUDGET: Resource budget fallback
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199));

-- S-BUDGET: Resource budget fallback
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199);

-- A-ERR-OVERFLOW: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -> x + 1 > 0);

-- S-ERR-OVERFLOW: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_int + 1 > 0;

-- A-ERR-DIV0: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x / 0 > 0);

-- S-ERR-DIV0: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n / 0 > 0;

-- A-ERR-MOD0: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,0)=0);

-- S-ERR-MOD0: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,0)=0;

-- A-ERR-LONGDIV: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -> x / BIGINT '-1' > 0);

-- S-ERR-LONGDIV: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_long / BIGINT '-1' > 0;

-- A-ERR-ABS: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -> abs(x)>0);

-- S-ERR-ABS: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(error_long)>0;

-- A-ERR-CAST: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -> CAST(x AS TINYINT)>0);

-- S-ERR-CAST: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(error_int AS TINYINT)>0;

-- A-ERR-ROUND: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(x,-19)>0);

-- S-ERR-ROUND: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(n,-19)>0;

-- A-ERR-TINY: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_tinys, x -> x + TINYINT '1' > TINYINT '0');

-- S-ERR-TINY: Bảo toàn lỗi SQL
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_tiny + TINYINT '1' > TINYINT '0';

-- LIMIT-2: LIMIT sau residual
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 2;

-- A-LIMIT-2: LIMIT sau residual
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -> lower(x) LIKE '%sá%') LIMIT 2;

-- LIMIT-3: LIMIT sau residual
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 3;

-- A-LIMIT-3: LIMIT sau residual
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -> lower(x) LIKE '%sá%') LIMIT 3;

-- VOL-NATIVE: 10.000 document
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500);

-- VOL-HYBRID: 10.000 document
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500 AND mod(x,2)=0);

-- VOL-SCRIPT: 10.000 document
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> mod(x,17)=0 AND mod(x,19)=0);

-- VOL-LOWER: 10.000 document
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE lower(word) LIKE '%sá%';

-- VOL-COUNT: 10.000 document + COUNT
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SELECT count(*) FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500 AND mod(x,2)=0);

USE es7_compare.default;
SET TIME ZONE 'UTC';
-- A-N01: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x = 12);

-- S-N01: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n = 12;

-- A-N02: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x <> 12);

-- S-N02: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n <> 12;

-- A-N03: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x < 10);

-- S-N03: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n < 10;

-- A-N04: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x <= 10);

-- S-N04: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n <= 10;

-- A-N05: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10);

-- S-N05: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10;

-- A-N06: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x >= 10);

-- S-N06: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n >= 10;

-- A-N07: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x BETWEEN 5 AND 12);

-- S-N07: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n BETWEEN 5 AND 12;

-- A-N08: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT BETWEEN 5 AND 12);

-- S-N08: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT BETWEEN 5 AND 12;

-- A-N09: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5, 12));

-- S-N09: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12);

-- A-N10: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));

-- S-N10: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5, 12, NULL);

-- A-N11: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));

-- S-N11: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, 12);

-- A-N12: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x NOT IN (5, NULL));

-- S-N12: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n NOT IN (5, NULL);

-- A-N13: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> NOT (x = 12));

-- S-N13: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (n = 12);

-- A-N14: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NOT NULL);

-- S-N14: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT NULL;

-- A-N15: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);

-- S-N15: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n > 10;

-- A-N16: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x < 0 OR x > 10);

-- S-N16: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n < 0 OR n > 10;

-- A-N17: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND x < 5);

-- S-N17: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND n < 5;

-- A-N18: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);

-- S-N18: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NOT DISTINCT FROM 12;

-- A-N19: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS DISTINCT FROM NULL);

-- S-N19: Native integral / ba giá trị
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM NULL;

-- A-NNULL: Giữ semantics NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS NULL);

-- S-NNULL: Giữ semantics NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS NULL;

-- A-NDISTINCT: Giữ semantics NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IS DISTINCT FROM 12);

-- S-NDISTINCT: Giữ semantics NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IS DISTINCT FROM 12;

-- A-NTRUE: Giữ semantics NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRUE OR x > 10);

-- M1: Membership hiện có
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(numbers, 12);

-- M2: Membership hiện có
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[5,12]);

-- M3: Membership hiện có
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(words, 'abc');

-- M4: Membership hiện có
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE contains(flags, true);

-- M5: Membership hiện có
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE arrays_overlap(numbers, ARRAY[12,CAST(NULL AS INTEGER)]);

-- A-P1: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);

-- S-P1: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND sin(n) > 0;

-- A-P2: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 OR sin(x) > 0);

-- S-P2: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 OR sin(n) > 0;

-- A-P3: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));

-- S-P3: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE (n > 10 AND sin(n) > 0) OR (n < 0 AND cos(n) > 0);

-- A-P4: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);

-- S-P4: Điều kiện cần; scripts OFF
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SELECT id FROM expr_a2af4b7203e_cases WHERE n IN (5,12) AND n > 10;

-- A-I01: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x + 1 > 10);

-- S-I01: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n + 1 > 10;

-- A-I02: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x - 1 > 10);

-- S-I02: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n - 1 > 10;

-- A-I03: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x * 2 > 20);

-- S-I03: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n * 2 > 20;

-- A-I04: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x / 2 > 5);

-- S-I04: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n / 2 > 5;

-- A-I05: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x % 2 = 0);

-- S-I05: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n % 2 = 0;

-- A-I06: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,2)=0);

-- S-I06: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2)=0;

-- A-I07: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,-1)=0);

-- S-I07: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,-1)=0;

-- A-I08: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> -x > 5);

-- S-I08: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -n > 5;

-- A-I09: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);

-- S-I09: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 OR mod(n,2)=0;

-- A-I10: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);

-- S-I10: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n > 10 AND mod(n,2)=0;

-- A-I11: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));

-- S-I11: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE NOT (mod(n,2)=0);

-- A-I12: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(x) > 10 OR x = 2);

-- S-I12: Số học integral / hybrid
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n) > 10 OR n = 2;

-- A-WIDTH-TINYINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -> x + TINYINT '1' > TINYINT '10');

-- S-WIDTH-TINYINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE tiny + TINYINT '1' > TINYINT '10';

-- A-MOD-TINYINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');

-- S-MOD-TINYINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(tiny, TINYINT '2') = TINYINT '0';

-- A-WIDTH-SMALLINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -> x + SMALLINT '1' > SMALLINT '10');

-- S-WIDTH-SMALLINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE small + SMALLINT '1' > SMALLINT '10';

-- A-MOD-SMALLINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(small_numbers, x -> mod(x, SMALLINT '2') = SMALLINT '0');

-- S-MOD-SMALLINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(small, SMALLINT '2') = SMALLINT '0';

-- A-WIDTH-BIGINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -> x + BIGINT '1' > BIGINT '10');

-- S-WIDTH-BIGINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE big + BIGINT '1' > BIGINT '10';

-- A-MOD-BIGINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(big_numbers, x -> mod(x, BIGINT '2') = BIGINT '0');

-- S-MOD-BIGINT: Độ rộng kiểu số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(big, BIGINT '2') = BIGINT '0';

-- A-MATH1: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(x)>5);

-- S-MATH1: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(n)>5;

-- A-MATH2: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> ceil(x)>=1);

-- S-MATH2: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(n)>=1;

-- A-MATH3: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> floor(x)<1);

-- S-MATH3: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE floor(n)<1;

-- A-MATH4: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(x, -1)=10);

-- S-MATH4: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(n, -1)=10;

-- A-MATH5: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> truncate(x)>5);

-- S-MATH5: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(n)>5;

-- A-MATH6: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sign(x)=-1);

-- S-MATH6: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(n)=-1;

-- A-MATH7: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sqrt(abs(x))>DOUBLE '2');

-- S-MATH7: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sqrt(abs(n))>DOUBLE '2';

-- A-MATH8: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> pow(x,DOUBLE '2')>DOUBLE '100');

-- S-MATH8: Hàm số
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE pow(n,DOUBLE '2')>DOUBLE '100';

-- A-REAL01: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));

-- S-REAL01: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f + CAST(1.5 AS REAL) > CAST(2 AS REAL);

-- A-REAL02: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x - CAST(1.5 AS REAL) < CAST(0 AS REAL));

-- S-REAL02: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f - CAST(1.5 AS REAL) < CAST(0 AS REAL);

-- A-REAL03: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x * CAST(2 AS REAL) > CAST(1 AS REAL));

-- S-REAL03: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f * CAST(2 AS REAL) > CAST(1 AS REAL);

-- A-REAL04: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / CAST(0 AS REAL) > CAST(0 AS REAL));

-- S-REAL04: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / CAST(0 AS REAL) > CAST(0 AS REAL);

-- A-REAL05: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> mod(x, CAST(2 AS REAL)) = CAST(0 AS REAL));

-- S-REAL05: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(f, CAST(2 AS REAL)) = CAST(0 AS REAL);

-- A-REAL06: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> -x > CAST(0 AS REAL));

-- S-REAL06: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -f > CAST(0 AS REAL);

-- A-REAL07: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x = CAST(0 AS REAL));

-- S-REAL07: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f = CAST(0 AS REAL);

-- A-REAL08: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x >= CAST(0 AS REAL));

-- S-REAL08: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f >= CAST(0 AS REAL);

-- A-REAL09: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x = CAST(nan() AS REAL));

-- S-REAL09: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f = CAST(nan() AS REAL);

-- A-REAL10: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x <> CAST(nan() AS REAL));

-- S-REAL10: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f <> CAST(nan() AS REAL);

-- A-REAL11: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x < CAST(nan() AS REAL));

-- S-REAL11: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f < CAST(nan() AS REAL);

-- A-REAL12: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x IS NOT DISTINCT FROM CAST(nan() AS REAL));

-- S-REAL12: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IS NOT DISTINCT FROM CAST(nan() AS REAL);

-- A-REAL13: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> x / x IN (CAST(nan() AS REAL), CAST(2 AS REAL)));

-- S-REAL13: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE f / f IN (CAST(nan() AS REAL), CAST(2 AS REAL));

-- A-REAL14: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> round(x,1)>=CAST(1 AS REAL));

-- S-REAL14: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(f,1)>=CAST(1 AS REAL);

-- A-REAL15: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> ceil(x)=floor(x));

-- S-REAL15: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(f)=floor(f);

-- A-REAL16: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> truncate(x)>CAST(0 AS REAL));

-- S-REAL16: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(f)>CAST(0 AS REAL);

-- A-REAL17: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(reals, x -> sign(x)=CAST(-1 AS REAL));

-- S-REAL17: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(f)=CAST(-1 AS REAL);

-- A-DOUBLE01: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x + CAST(1.5 AS DOUBLE) > CAST(2 AS DOUBLE));

-- S-DOUBLE01: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d + CAST(1.5 AS DOUBLE) > CAST(2 AS DOUBLE);

-- A-DOUBLE02: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x - CAST(1.5 AS DOUBLE) < CAST(0 AS DOUBLE));

-- S-DOUBLE02: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d - CAST(1.5 AS DOUBLE) < CAST(0 AS DOUBLE);

-- A-DOUBLE03: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x * CAST(2 AS DOUBLE) > CAST(1 AS DOUBLE));

-- S-DOUBLE03: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d * CAST(2 AS DOUBLE) > CAST(1 AS DOUBLE);

-- A-DOUBLE04: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / CAST(0 AS DOUBLE) > CAST(0 AS DOUBLE));

-- S-DOUBLE04: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / CAST(0 AS DOUBLE) > CAST(0 AS DOUBLE);

-- A-DOUBLE05: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> mod(x, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE));

-- S-DOUBLE05: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(d, CAST(2 AS DOUBLE)) = CAST(0 AS DOUBLE);

-- A-DOUBLE06: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> -x > CAST(0 AS DOUBLE));

-- S-DOUBLE06: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE -d > CAST(0 AS DOUBLE);

-- A-DOUBLE07: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x = CAST(0 AS DOUBLE));

-- S-DOUBLE07: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d = CAST(0 AS DOUBLE);

-- A-DOUBLE08: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x >= CAST(0 AS DOUBLE));

-- S-DOUBLE08: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d >= CAST(0 AS DOUBLE);

-- A-DOUBLE09: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x = CAST(nan() AS DOUBLE));

-- S-DOUBLE09: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d = CAST(nan() AS DOUBLE);

-- A-DOUBLE10: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x <> CAST(nan() AS DOUBLE));

-- S-DOUBLE10: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d <> CAST(nan() AS DOUBLE);

-- A-DOUBLE11: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x < CAST(nan() AS DOUBLE));

-- S-DOUBLE11: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d < CAST(nan() AS DOUBLE);

-- A-DOUBLE12: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x IS NOT DISTINCT FROM CAST(nan() AS DOUBLE));

-- S-DOUBLE12: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IS NOT DISTINCT FROM CAST(nan() AS DOUBLE);

-- A-DOUBLE13: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> x / x IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE)));

-- S-DOUBLE13: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE d / d IN (CAST(nan() AS DOUBLE), CAST(2 AS DOUBLE));

-- A-DOUBLE14: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> round(x,1)>=CAST(1 AS DOUBLE));

-- S-DOUBLE14: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(d,1)>=CAST(1 AS DOUBLE);

-- A-DOUBLE15: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> ceil(x)=floor(x));

-- S-DOUBLE15: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ceil(d)=floor(d);

-- A-DOUBLE16: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> truncate(x)>CAST(0 AS DOUBLE));

-- S-DOUBLE16: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE truncate(d)>CAST(0 AS DOUBLE);

-- A-DOUBLE17: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doubles, x -> sign(x)=CAST(-1 AS DOUBLE));

-- S-DOUBLE17: REAL/DOUBLE; signed zero; NaN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(d)=CAST(-1 AS DOUBLE);

-- A-DEC01: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC01: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC02: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) - DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC02: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) - DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC03: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) * DECIMAL '1.25' > DECIMAL '10.00');

-- S-DEC03: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) * DECIMAL '1.25' > DECIMAL '10.00';

-- A-DEC04: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00');

-- S-DEC04: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(12,2)) / DECIMAL '3.00' > DECIMAL '1.00';

-- A-DEC05: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(CAST(x AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00');

-- S-DEC05: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(CAST(n AS DECIMAL(12,2)), DECIMAL '3.00') = DECIMAL '0.00';

-- A-DEC06: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(CAST(x AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30');

-- S-DEC06: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(CAST(n AS DECIMAL(12,2)) + DECIMAL '0.25',1)=DECIMAL '2.30';

-- A-DEC07: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(30,10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890');

-- S-DEC07: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS DECIMAL(30,10)) + DECIMAL '0.1234567890' > DECIMAL '10.1234567890';

-- A-DEC08: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> abs(CAST(x AS DECIMAL(12,2)))>DECIMAL '10.00');

-- S-DEC08: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(CAST(n AS DECIMAL(12,2)))>DECIMAL '10.00';

-- A-DEC09: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> sign(CAST(x AS DECIMAL(12,2)))=DECIMAL '-1');

-- S-DEC09: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE sign(CAST(n AS DECIMAL(12,2)))=DECIMAL '-1';

-- A-DEC10: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> floor(CAST(x AS DECIMAL(12,2)))=ceil(CAST(x AS DECIMAL(12,2))));

-- S-DEC10: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE floor(CAST(n AS DECIMAL(12,2)))=ceil(CAST(n AS DECIMAL(12,2)));

-- A-DEC11: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(CAST(x AS DECIMAL(12,2)) AS INTEGER)>10);

-- S-DEC11: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS INTEGER)>10;

-- A-DEC12: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(CAST(x AS DECIMAL(12,2)) AS DOUBLE)>DOUBLE '10');

-- S-DEC12: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(CAST(n AS DECIMAL(12,2)) AS DOUBLE)>DOUBLE '10';

-- A-DEC13: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CAST(x AS SMALLINT)>10);

-- S-DEC13: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(n AS SMALLINT)>10;

-- A-DEC14: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRY_CAST(x AS TINYINT)>10);

-- S-DEC14: DECIMAL và CAST
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT)>10;

-- A-NULL1: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(x,0)>10);

-- S-NULL1: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,0)>10;

-- A-NULL2: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(x,100)>10);

-- S-NULL2: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(n,100)>10;

-- A-NULL3: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> nullif(x,0)>1);

-- S-NULL3: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE nullif(n,0)>1;

-- A-NULL4: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);

-- S-NULL4: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(nullif(n,0),1)>10;

-- A-NULL5: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> TRY_CAST(x AS TINYINT) IS NULL);

-- S-NULL5: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;

-- A-NULL6: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);

-- S-NULL6: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN false ELSE n>10 END;

-- A-NULL7: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN true ELSE x>10 END);

-- S-NULL7: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CASE WHEN n IS NULL THEN true ELSE n>10 END;

-- A-NULL8: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> IF(x>10,true,false));

-- S-NULL8: COALESCE / NULLIF / TRY_CAST / CASE
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE IF(n>10,true,false);

-- A-BOOL1: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> x OR NOT x);

-- S-BOOL1: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE flag OR NOT flag;

-- A-BOOL2: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> coalesce(x,false));

-- S-BOOL2: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,false);

-- A-BOOL3: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> coalesce(x,true));

-- S-BOOL3: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE coalesce(flag,true);

-- A-BOOL4: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);

-- S-BOOL4: BOOLEAN
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE flag IS NOT DISTINCT FROM true;

-- A-TXT01: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'abc'));

-- S-TXT01: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc');

-- A-TXT02: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE 'ab%');

-- S-TXT02: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'ab%';

-- A-TXT03: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE '%b%');

-- S-TXT03: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%b%';

-- A-TXT04: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE 'a_b');

-- S-TXT04: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE 'a_b';

-- A-TXT05: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');

-- S-TXT05: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word LIKE '%a!_%' ESCAPE '!';

-- A-TXT06: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));

-- S-TXT06: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'a') AND starts_with(word,'ab');

-- A-TXT07: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'ab') OR x='nice');

-- S-TXT07: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'ab') OR word='nice';

-- A-TXT08: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> length(x)>2);

-- S-TXT08: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)>2;

-- A-TXT09: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> ends_with(x,'b'));

-- S-TXT09: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ends_with(word,'b');

-- A-TXT10: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> strpos(x,'😀')>0);

-- S-TXT10: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE strpos(word,'😀')>0;

-- A-TXT11: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> replace(x,'_','😀') LIKE '%😀%');

-- S-TXT11: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'_','😀') LIKE '%😀%';

-- A-TXT12: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> replace(x,'','!') LIKE '%!s!á!%');

-- S-TXT12: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE replace(word,'','!') LIKE '%!s!á!%';

-- A-TXT13: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');

-- S-TXT13: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE concat(substr(word,1,1),'Z')='aZ';

-- A-TXT14: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> (x || 'Z')='abcZ');

-- S-TXT14: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE (word || 'Z')='abcZ';

-- A-TXT15: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> substr(x,-2,1)='b');

-- S-TXT15: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE substr(word,-2,1)='b';

-- A-TXT16: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> substring(x,2)='bc');

-- S-TXT16: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE substring(word,2)='bc';

-- A-TXT17: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> trim(x)='abc');

-- S-TXT17: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE trim(word)='abc';

-- A-TXT18: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> ltrim(x)='abc');

-- S-TXT18: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE ltrim(word)='abc';

-- A-TXT19: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> rtrim(x)='abc');

-- S-TXT19: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE rtrim(word)='abc';

-- A-TXT20: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');

-- S-TXT20: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%';

-- A-TXT21: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> upper(x) LIKE '%SÁ%');

-- S-TXT21: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE upper(word) LIKE '%SÁ%';

-- A-TXT22: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> x > '');

-- S-TXT22: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE word > '';

-- A-TXT23: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> length(x)>2 AND starts_with(x,'a'));

-- S-TXT23: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(word)>2 AND starts_with(word,'a');

-- A-TXT24: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> starts_with(x,'abc') AND lower(x) LIKE '%b%');

-- S-TXT24: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE starts_with(word,'abc') AND lower(word) LIKE '%b%';

-- A-TXT25: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'abc'));

-- S-TXT25: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'abc');

-- A-TXT26: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'(?i)^abc'));

-- S-TXT26: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'(?i)^abc');

-- A-TXT27: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words, x -> regexp_like(x,'^abc$'));

-- S-TXT27: Keyword / Unicode / chuỗi
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE regexp_like(word,'^abc$');

-- A-MAP-IGNORE: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(ignored_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-IGNORE: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(ignored_word) LIKE '%sá%';

-- A-MAP-NORMAL: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(normalized_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-NORMAL: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(normalized_word) LIKE '%sá%';

-- A-MAP-NODV: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(nodv_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-NODV: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(nodv_word) LIKE '%sá%';

-- A-MAP-DOCONLY: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(doconly_words, x -> lower(x) LIKE '%sá%');

-- S-MAP-DOCONLY: Mapping guard / doc values
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(doconly_word) LIKE '%sá%';

-- S-CASEPATH: Tên field phân biệt hoa thường
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE length(reviewcomment)>2;

-- A-DATE1: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> year(x)=2024);

-- S-DATE1: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE year(dt)=2024;

-- A-DATE2: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> month(x)=2);

-- S-DATE2: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE month(dt)=2;

-- A-DATE3: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day(x)=29);

-- S-DATE3: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day(dt)=29;

-- A-DATE4: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> hour(x)=12);

-- S-DATE4: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE hour(dt)=12;

-- A-DATE5: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> minute(x)=59);

-- S-DATE5: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE minute(dt)=59;

-- A-DATE6: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> second(x)=56);

-- S-DATE6: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE second(dt)=56;

-- A-DATE7: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day_of_week(x) BETWEEN 1 AND 5);

-- S-DATE7: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_week(dt) BETWEEN 1 AND 5;

-- A-DATE8: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> day_of_year(x)=60);

-- S-DATE8: TIMESTAMP(3) UTC / extract
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE day_of_year(dt)=60;

-- A-TRUNC-millisecond: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('millisecond',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-millisecond: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('millisecond',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-millisecond: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('millisecond',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-millisecond: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('millisecond',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-second: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('second',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-second: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('second',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-second: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('second',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-second: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('second',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-minute: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('minute',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-minute: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('minute',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-minute: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('minute',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-minute: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('minute',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-hour: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('hour',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-hour: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('hour',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-hour: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('hour',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-hour: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('hour',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-day: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('day',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-day: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('day',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-day: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('day',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-day: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-week: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('week',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-week: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('week',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-week: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('week',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-week: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('week',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-month: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('month',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-month: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('month',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-month: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-month: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('month',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-quarter: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('quarter',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-quarter: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('quarter',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-quarter: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('quarter',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-quarter: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('quarter',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-TRUNC-year: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_trunc('year',x)>=TIMESTAMP '2024-01-01 00:00:00.000');

-- S-TRUNC-year: date_trunc UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_trunc('year',dt)>=TIMESTAMP '2024-01-01 00:00:00.000';

-- A-ADD-year: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('year',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');

-- S-ADD-year: date_add UTC
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('year',1,dt)>TIMESTAMP '2024-03-01 00:00:00.000';

-- A-DIFF-millisecond: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('millisecond',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-millisecond: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('millisecond',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-second: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('second',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-second: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('second',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-minute: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('minute',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-minute: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('minute',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-hour: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('hour',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-hour: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('hour',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-day: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('day',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-day: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-week: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('week',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-week: date_diff đơn vị cố định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('week',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-DIFF-REVERSE: date_diff đảo đối số; NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);

-- S-DIFF-REVERSE: date_diff đảo đối số; NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',dt)<=0;

-- A-DATE-NULL-ERROR: Lỗi trên phần tử NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_add('day',coalesce(year(x),BIGINT '9223372036854775807')+BIGINT '1',x)<TIMESTAMP '0001-01-01 00:00:00.000');

-- S-DATE-NULL-ERROR: Lỗi trên phần tử NULL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_add('day',coalesce(year(dt),BIGINT '9223372036854775807')+BIGINT '1',dt)<TIMESTAMP '0001-01-01 00:00:00.000';

-- A-DIFF-MONTH: Fallback được chủ định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(dates, x -> date_diff('month',x,TIMESTAMP '2025-01-01 00:00:00.000')>=0);

-- S-DIFF-MONTH: Fallback được chủ định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE date_diff('month',dt,TIMESTAMP '2025-01-01 00:00:00.000')>=0;

-- A-CAPTURE: Fallback được chủ định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -> x>n);

-- A-CAPTURE-PREFILTER: Điều kiện cần với biến capture
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers,x -> x>10 AND x>n);

-- S-MULTICOL: Fallback được chủ định
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n+small>10;

-- S-MIXED-AND: Ghép các cột scalar
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n>10 AND lower(word) LIKE '%sá%';

-- S-MIXED-OR: Ghép các cột scalar
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n>10 OR lower(word) LIKE '%sá%';

-- A-BUDGET: Resource budget fallback
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199));

-- S-BUDGET: Resource budget fallback
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,2) IN (0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199);

-- A-ERR-OVERFLOW: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -> x + 1 > 0);

-- S-ERR-OVERFLOW: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_int + 1 > 0;

-- A-ERR-DIV0: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> x / 0 > 0);

-- S-ERR-DIV0: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE n / 0 > 0;

-- A-ERR-MOD0: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> mod(x,0)=0);

-- S-ERR-MOD0: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE mod(n,0)=0;

-- A-ERR-LONGDIV: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -> x / BIGINT '-1' > 0);

-- S-ERR-LONGDIV: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_long / BIGINT '-1' > 0;

-- A-ERR-ABS: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_longs, x -> abs(x)>0);

-- S-ERR-ABS: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE abs(error_long)>0;

-- A-ERR-CAST: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_ints, x -> CAST(x AS TINYINT)>0);

-- S-ERR-CAST: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE CAST(error_int AS TINYINT)>0;

-- A-ERR-ROUND: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(numbers, x -> round(x,-19)>0);

-- S-ERR-ROUND: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE round(n,-19)>0;

-- A-ERR-TINY: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(error_tinys, x -> x + TINYINT '1' > TINYINT '0');

-- S-ERR-TINY: Bảo toàn lỗi SQL
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE error_tiny + TINYINT '1' > TINYINT '0';

-- LIMIT-2: LIMIT sau residual
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 2;

-- A-LIMIT-2: LIMIT sau residual
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -> lower(x) LIKE '%sá%') LIMIT 2;

-- LIMIT-3: LIMIT sau residual
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE lower(word) LIKE '%sá%' LIMIT 3;

-- A-LIMIT-3: LIMIT sau residual
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_cases WHERE any_match(words,x -> lower(x) LIKE '%sá%') LIMIT 3;

-- VOL-NATIVE: 10.000 document
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500);

-- VOL-HYBRID: 10.000 document
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500 AND mod(x,2)=0);

-- VOL-SCRIPT: 10.000 document
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> mod(x,17)=0 AND mod(x,19)=0);

-- VOL-LOWER: 10.000 document
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT id FROM expr_a2af4b7203e_volume WHERE lower(word) LIKE '%sá%';

-- VOL-COUNT: 10.000 document + COUNT
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SELECT count(*) FROM expr_a2af4b7203e_volume WHERE any_match(numbers,x -> x>1500 AND mod(x,2)=0);
