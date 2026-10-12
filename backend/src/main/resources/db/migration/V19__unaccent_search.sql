-- Umlaut-insensitive full-text search, in both directions.
--
-- FullTextSearch.foldGermanUmlauts (and the two OR-branches in TSQUERY_EXPR built from
-- it) fold a *query* typed with real umlauts to the digraph spelling old rows carry --
-- "Prüfung" also becomes "Pruefung" so rows written before the semantic-tagger prompt
-- was fixed still turn up. It never worked the other way: the tagger now writes real
-- umlauts, and foldGermanUmlauts("fakultaet") is "fakultaet" unchanged, since there is
-- no umlaut in it to fold. A teacher who types the ASCII spelling -- quicker on many
-- keyboards -- found nothing for "Fakultät", "Prüfung", "Größe" and every other umlaut
-- word the tagger has written since.
--
-- Fix: stop folding only the query, and instead normalise both sides of the match
-- through the same function, so it no longer matters which spelling was stored and
-- which was typed.
--
-- unaccent() alone is not that function: it strips combining diacritics down to the
-- bare Latin letter (ä -> a, ö -> o, ü -> u), not the German digraph (ae/oe/ue) a
-- teacher actually types in their place. fold_umlauts runs first to produce exactly the
-- digraph spelling foldGermanUmlauts used to, then immutable_unaccent (ß -> ss, and
-- every other language's accents -- café/cafe, naïve/naive -- for free, which the old
-- per-language digraph table never attempted). immutable_unaccent exists only because
-- unaccent() itself is STABLE (it resolves its dictionary through search_path), which
-- PostgreSQL refuses inside a generated column or the expression index behind it; naming
-- the dictionary explicitly and wrapping the call in an IMMUTABLE function is the
-- documented way around that, and is safe here because the dictionary a fixed name
-- resolves to does not change underneath a running server.
--
-- search_normalize is applied to every generated search_vector column below and,
-- symmetrically, to the query text in FullTextSearch.TSQUERY_EXPR -- see that class for
-- the query side. A generated column's expression cannot be altered in place, so each
-- column is dropped and recreated; STORED forces a full rewrite of the table (and
-- PostgreSQL rebuilds the GIN index that follows it) for every row already in it, which
-- is the reindex this approach accepts in exchange for not special-casing "ae"/"oe"/
-- "ue"/"ss" as ambiguous query branches (Aeon, Israel, Klasse, ...).
--
-- Re-running is safe: CREATE EXTENSION / CREATE OR REPLACE FUNCTION / DROP COLUMN IF
-- EXISTS / CREATE INDEX IF NOT EXISTS are all idempotent.

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;

CREATE OR REPLACE FUNCTION public.immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE OR REPLACE FUNCTION public.fold_umlauts(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
    AS $$
        SELECT replace(replace(replace(replace(replace(replace($1,
            'ä', 'ae'), 'ö', 'oe'), 'ü', 'ue'), 'Ä', 'Ae'), 'Ö', 'Oe'), 'Ü', 'Ue')
    $$;

CREATE OR REPLACE FUNCTION public.search_normalize(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.immutable_unaccent(public.fold_umlauts($1)) $$;

ALTER TABLE public.tasks DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tasks ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(statement, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(solution, ''))), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_tasks_fts ON public.tasks USING gin (search_vector);

ALTER TABLE public.tiered_task_sequences DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.tiered_task_sequences ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(task_description, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize((COALESCE(semantic_summary, ''::character varying))::text)), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_sequences_fts ON public.tiered_task_sequences USING gin (search_vector);

ALTER TABLE public.worksheets DROP COLUMN IF EXISTS search_vector;
ALTER TABLE public.worksheets ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(name, ''))), 'A') ||
    setweight(to_tsvector('simple', public.search_normalize(COALESCE(description, ''))), 'B')
) STORED;
CREATE INDEX IF NOT EXISTS idx_worksheets_search_vector ON public.worksheets USING GIN (search_vector);
