-- Reverses migration/V14__lowercase_concept_names.sql. Unlike most migrations
-- here, V14's own comment names exactly which rows it lowercased and what they
-- were before -- "Loop", "Collections", "Sequence" -- because 13 had already
-- folded every case-only duplicate away, leaving these three as the only
-- mixed-case names left to touch. That is what makes restoring the original
-- casing possible at all here, unlike the general case (folding IS the loss
-- of information, ordinarily).
--
-- Caveat: this matches by current name, not by which specific rows V14 touched.
-- If a concept literally named "loop", "collections" or "sequence" (already
-- lowercase, not the ones V14 wrote) was inserted after V14 ran, this restores
-- its casing too. That did not happen as of V14 -- lowercase is the only style
-- PostgreConceptDAO has ever written -- but it is the reason this restores by
-- value rather than by id: there were no ids recorded to restore by.

ALTER TABLE concepts DROP CONSTRAINT IF EXISTS concepts_name_lowercase_check;

UPDATE concepts SET name = 'Loop'        WHERE name = 'loop';
UPDATE concepts SET name = 'Collections' WHERE name = 'collections';
UPDATE concepts SET name = 'Sequence'    WHERE name = 'sequence';

DELETE FROM flyway_schema_history WHERE version='14';
