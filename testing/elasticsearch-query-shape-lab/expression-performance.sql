-- Candidate replay. Benchmark indices must first be recreated with expression-performance.py.
-- Full session settings for each revision are preserved in raw evidence.
USE es8_compare.default;
-- feature 1 A-P1; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s4_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 4 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s8_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 8 shards; 10000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n50000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s4_n50000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 4 shards; 50000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = false;
SET SESSION es8_compare.scalar_script_pushdown_enabled = false;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s8_n100000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 8 shards; 100000 documents
SET SESSION es8_compare.array_script_pushdown_enabled = true;
SET SESSION es8_compare.scalar_script_pushdown_enabled = true;
SET SESSION es8_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE lower(foldedreview) LIKE '%the%';
-- Restore catalog defaults after replaying this backend.
RESET SESSION es8_compare.array_script_pushdown_enabled;
RESET SESSION es8_compare.scalar_script_pushdown_enabled;
RESET SESSION es8_compare.full_text_pushdown_mode;
USE es7_compare.default;
-- feature 1 A-P1; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s4_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 4 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s8_n10000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 8 shards; 10000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n10000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n50000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n50000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s1_n100000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 1 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s1_n100000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s4_n50000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 4 shards; 50000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s4_n50000_text WHERE lower(foldedreview) LIKE '%the%';
-- feature 1 A-P1; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 AND sin(x) > 0);
-- feature 2 A-P3; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> (x > 10 AND sin(x) > 0) OR (x < 0 AND cos(x) > 0));
-- feature 3 A-CAPTURE-PREFILTER; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers,x -> x>10 AND x>n);
-- feature 4 A-N11; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x NOT IN (5, 12));
-- feature 5 A-N10; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IN (5, 12, NULL));
-- feature 6 A-N15; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IN (5,12) AND x > 10);
-- feature 7 A-N18; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x IS NOT DISTINCT FROM 12);
-- feature 8 A-TXT06; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> starts_with(x,'a') AND starts_with(x,'ab'));
-- feature 9 A-TXT05; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> x LIKE '%a!_%' ESCAPE '!');
-- feature 10 A-TXT25; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> regexp_like(x,'abc'));
-- feature 11 A-I01; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x + 1 > 10);
-- feature 12 A-MOD-TINYINT; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(tiny_numbers, x -> mod(x, TINYINT '2') = TINYINT '0');
-- feature 13 A-I09; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 OR mod(x,2)=0);
-- feature 14 A-I10; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> x > 10 AND mod(x,2)=0);
-- feature 15 A-I11; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> NOT (mod(x,2)=0));
-- feature 16 A-REAL01; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(reals, x -> x + CAST(1.5 AS REAL) > CAST(2 AS REAL));
-- feature 17 A-DEC01; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> CAST(x AS DECIMAL(12,2)) + DECIMAL '1.25' > DECIMAL '10.00');
-- feature 18 S-NULL5; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE TRY_CAST(n AS TINYINT) IS NULL;
-- feature 19 A-MATH1; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> abs(x)>5);
-- feature 20 A-NULL4; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> coalesce(nullif(x,0),1)>10);
-- feature 21 A-NULL6; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(numbers, x -> CASE WHEN x IS NULL THEN false ELSE x>10 END);
-- feature 22 A-BOOL4; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(flags, x -> x IS NOT DISTINCT FROM true);
-- feature 23 A-TXT10; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> strpos(x,'😀')>0);
-- feature 24 A-TXT16; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> substring(x,2)='bc');
-- feature 25 A-TXT13; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> concat(substr(x,1,1),'Z')='aZ');
-- feature 26 A-TXT20; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(words, x -> lower(x) LIKE '%sá%');
-- feature 27 A-DATE2; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> month(x)=2);
-- feature 28 A-ADD-month; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> date_add('month',1,x)>TIMESTAMP '2024-03-01 00:00:00.000');
-- feature 29 A-DIFF-REVERSE; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE any_match(dates, x -> date_diff('day',TIMESTAMP '2025-01-01 00:00:00.000',x)<=0);
-- feature 30 S-I09; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE n > 10 OR mod(n,2)=0;
-- feature 31 S-NULL2; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE coalesce(n,100)>10;
-- feature 32 S-MIXED-OR; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'SAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_cases WHERE n>10 OR lower(word) LIKE '%sá%';
-- feature 33 TEXT-USER-SQL; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = false;
SET SESSION es7_compare.scalar_script_pushdown_enabled = false;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE LOWER(raw_review_comment) LIKE '%sách rất%';
-- feature 34 S-TEXT02; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE length(trim(review))>2;
-- feature 35 A-TEXT18; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE any_match(reviews, x -> x IS NULL);
-- feature 36 S-TEXT06; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE ends_with(lower(trim(review)),'tốt');
-- feature 37 TEXT-NATIVE-with_keyword-lower-match_phrase; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE lower(with_keyword) LIKE '%sách rất%';
-- feature 38 TEXT-COUNT; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT count(*) FROM expr_perf_20261118_s8_n100000_text WHERE length(trim(review))>2;
-- feature 39 TEXT-NESTED; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE length(profile.reviewcomment)>2;
-- feature 40 TEXT-ZERO-TOKENS; 8 shards; 100000 documents
SET SESSION es7_compare.array_script_pushdown_enabled = true;
SET SESSION es7_compare.scalar_script_pushdown_enabled = true;
SET SESSION es7_compare.full_text_pushdown_mode = 'UNSAFE';
SELECT id FROM expr_perf_20261118_s8_n100000_text WHERE lower(foldedreview) LIKE '%the%';
-- Restore catalog defaults after replaying this backend.
RESET SESSION es7_compare.array_script_pushdown_enabled;
RESET SESSION es7_compare.scalar_script_pushdown_enabled;
RESET SESSION es7_compare.full_text_pushdown_mode;
