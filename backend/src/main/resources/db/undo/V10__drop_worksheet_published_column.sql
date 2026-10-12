-- Reverses migration/V10__worksheet_published.sql. There was no `published`
-- column at all before V10 -- not one defaulting some other way -- so the
-- column is dropped outright rather than only reverting its DEFAULT.

ALTER TABLE public.class_worksheets DROP COLUMN IF EXISTS published;

DELETE FROM flyway_schema_history WHERE version='10';
