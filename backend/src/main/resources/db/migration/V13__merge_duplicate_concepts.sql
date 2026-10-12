-- ════════════════════════════════════════════════════════════════════
-- 13_migration_merge_duplicate_concepts.sql
-- Fold the duplicate concept spellings onto their kebab-case names
-- ════════════════════════════════════════════════════════════════════
--
-- The concepts table grew two vocabularies. The curriculum mapping and
-- src/main/resources/concepts/concept-catalog.json use lowercase kebab-case
-- slugs ("for-loop", "variable-declaration"); concept detection and the eval
-- runs wrote Capitalised names ("Arrays", "Variables"), and a German pass left
-- "Rekursion" and "Schleifen" behind. concepts.name is UNIQUE but
-- case-sensitive, so "recursion" and "Recursion" both fit and mean the same
-- thing.
--
-- Nothing had noticed yet, because no sequence has ever been generated from a
-- curriculum topic — the two vocabularies had not met. They meet as soon as
-- one is, and then library filters and concept counts split across both
-- spellings.
--
-- The catalogue decides the target: each loser below is the slug's own alias
-- or display name (or its plural), so this is not a guess about what the
-- names mean:
--   Arrays        -> array                 (alias "arrays")
--   Recursion     -> recursion             (identical but for case)
--   Rekursion     -> recursion             (displayNameDe "Rekursion")
--   Parameters    -> parameters-arguments  (alias "parameters")
--   Functions     -> function-definition   (alias "function")
--   Variables     -> variable-declaration  (alias "variable")
--   Return values -> return-value          (displayNameEn "Return Value")
--   Condition     -> if-else               (aliases "conditional", "selection")
--
-- DELIBERATELY NOT MERGED — these are coarser concepts, not duplicates, and
-- no single slug is the right target. Folding them would assert something
-- false about the sequences carrying them:
--   Loop        (64 sequences) — for-loop? while-loop? do-while-loop?
--   Collections (57 sequences) — list? set? dictionary? arraylist?
--   Sequence    (30 sequences) — no counterpart in the catalogue at all
--
-- "Schleifen" is dropped rather than merged: it has the same ambiguity as
-- "Loop" but no references anywhere, so there is nothing to preserve. The
-- delete is guarded so it only fires while that stays true.
--
-- HOW TO RUN (existing / server DB — db/init only runs on a fresh volume):
--   docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/13_migration_merge_duplicate_concepts.sql
--   psql "postgresql://USER:PASS@HOST:5432/codeowl" -f db/init/13_migration_merge_duplicate_concepts.sql
--
-- VERIFY afterwards (must return 0 rows — no two names differing only by case):
--   SELECT lower(name) FROM concepts GROUP BY 1 HAVING count(*) > 1;
--
-- IDEMPOTENT: a second run finds no loser names and does nothing. Safe to
-- re-run, and safe on a database that never had the duplicates.
-- ════════════════════════════════════════════════════════════════════

SET client_encoding TO 'UTF8';

BEGIN;

CREATE TEMP TABLE concept_merge (loser_id uuid, winner_id uuid) ON COMMIT DROP;

-- Only pairs where BOTH names exist are merged. If the kebab-case winner is
-- missing the loser is left alone rather than deleted, so a database that
-- never had the catalogue keeps its data.
INSERT INTO concept_merge (loser_id, winner_id)
SELECT loser.id, winner.id
FROM (VALUES
    ('Arrays',        'array'),
    ('Recursion',     'recursion'),
    ('Rekursion',     'recursion'),
    ('Parameters',    'parameters-arguments'),
    ('Functions',     'function-definition'),
    ('Variables',     'variable-declaration'),
    ('Return values', 'return-value'),
    ('Condition',     'if-else')
) AS pair(loser_name, winner_name)
JOIN concepts loser  ON loser.name  = pair.loser_name
JOIN concepts winner ON winner.name = pair.winner_name
WHERE loser.id <> winner.id;


-- ────────────────────────────────────────────────────────────────────
-- Repoint every reference. ON CONFLICT covers the rows that already carry
-- both spellings, which would otherwise violate the composite primary key.
-- ────────────────────────────────────────────────────────────────────

INSERT INTO tiered_task_sequence_concepts (sequence_id, concept_id)
SELECT link.sequence_id, m.winner_id
FROM tiered_task_sequence_concepts link
JOIN concept_merge m ON m.loser_id = link.concept_id
ON CONFLICT DO NOTHING;

INSERT INTO snippet_concept (snippet_name, concept_id)
SELECT link.snippet_name, m.winner_id
FROM snippet_concept link
JOIN concept_merge m ON m.loser_id = link.concept_id
ON CONFLICT DO NOTHING;

INSERT INTO concept_curriculum_topic (topic_id, concept_id)
SELECT link.topic_id, m.winner_id
FROM concept_curriculum_topic link
JOIN concept_merge m ON m.loser_id = link.concept_id
ON CONFLICT DO NOTHING;

-- The loser's own rows go with it: all three link tables cascade on concept_id.
DELETE FROM concepts WHERE id IN (SELECT loser_id FROM concept_merge);


-- ────────────────────────────────────────────────────────────────────
-- Unreferenced German stray. Guarded, so it survives if anything ever
-- started using it.
-- ────────────────────────────────────────────────────────────────────

DELETE FROM concepts c
WHERE c.name = 'Schleifen'
  AND NOT EXISTS (SELECT 1 FROM tiered_task_sequence_concepts x WHERE x.concept_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM snippet_concept x WHERE x.concept_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM concept_curriculum_topic x WHERE x.concept_id = c.id);

COMMIT;
