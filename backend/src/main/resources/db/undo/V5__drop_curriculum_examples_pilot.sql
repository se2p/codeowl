-- Reverses migration/V5__curriculum_examples_pilot.sql: the 4 curated code
-- examples it inserted under fixed ids. Run before undo/V2 -- two of these
-- reference curriculum topics V2 seeded, and deleting the topic first would
-- cascade-delete these rows anyway, out from under this script's WHERE IN.

DELETE FROM curriculum_code_examples
WHERE id IN (
    'c9000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000002',
    'c9000000-0000-0000-0000-000000000003',
    'c9000000-0000-0000-0000-000000000004'
);

DELETE FROM flyway_schema_history WHERE version='5';
