-- ════════════════════════════════════════════════════════════════════
-- 11_migration_curriculum_i18n.sql
-- Curriculum topics in proper German, plus an English translation
-- ════════════════════════════════════════════════════════════════════
--
-- Fixes two things the curriculum page showed:
--   1. 02_curriculum_seed.sql spells German without umlauts ("Binaerbaeume",
--      "Codierung und Verschluesselung", track "Spaet beginnend"), so the
--      transliteration was what teachers saw.
--   2. There was only one language column, so the English UI served German.
--
-- Adds name_en / description_en / track_en next to the German columns and
-- backfills every topic seeded by 02_curriculum_seed.sql. A topic added later
-- only has to fill these columns in — the frontend picks the column by UI
-- language and needs no change when the curriculum grows.
--
-- German stays the source language: the *_en columns are overrides, and a NULL
-- or empty one falls back to German rather than rendering blank.
--
-- The `SET client_encoding` below is what keeps the umlauts intact. Fed to
-- psql from a Windows console (codepage 850), libpq otherwise takes the
-- console codepage as the client encoding and stores the UTF-8 bytes re-read
-- as CP850 — "Prüfung" becomes "Pr├╝fung", "—" becomes "ÔÇö". That is the
-- 2025-10-09 incident; db/maintenance/fix_utf8_cp850_mojibake.py repairs it
-- afterwards, this line prevents it. (Container-automatic seeding through
-- /docker-entrypoint-initdb.d never had the problem — that psql runs inside
-- the Alpine image, not on a Windows console.)
--
-- HOW TO RUN (existing / server DB — db/init only runs on a fresh volume):
--   docker exec -i codeowl-clean-db psql -U postgres -d codeowl < db/init/11_migration_curriculum_i18n.sql
--   psql "postgresql://USER:PASS@HOST:5432/codeowl" -f db/init/11_migration_curriculum_i18n.sql
--
-- VERIFY afterwards (must return 0 rows):
--   SELECT id, name FROM curriculum_topics WHERE name_en IS NULL OR name ~ '(ae|oe|ue)';
--
-- IDEMPOTENT: ADD COLUMN IF NOT EXISTS + UPDATE by fixed id — safe to re-run.
-- ════════════════════════════════════════════════════════════════════

SET client_encoding TO 'UTF8';

BEGIN;

ALTER TABLE curriculum_topics
    ADD COLUMN IF NOT EXISTS name_en        text,
    ADD COLUMN IF NOT EXISTS description_en text,
    ADD COLUMN IF NOT EXISTS track_en       text;

COMMENT ON COLUMN curriculum_topics.name_en IS
    'English name; NULL falls back to the German name column.';
COMMENT ON COLUMN curriculum_topics.description_en IS
    'English description; NULL falls back to the German description column.';
COMMENT ON COLUMN curriculum_topics.track_en IS
    'English track label (Ausbildungsrichtung); NULL falls back to track.';


-- ────────────────────────────────────────────────────────────────────
-- 1. TOPIC NAMES AND DESCRIPTIONS
-- ────────────────────────────────────────────────────────────────────
-- ids are the ones fixed in 02_curriculum_seed.sql.

UPDATE curriculum_topics AS ct SET
    name           = v.name,
    description    = v.description,
    name_en        = v.name_en,
    description_en = v.description_en
FROM (VALUES
    -- === Jahrgangsstufen (top level) ===
    ('a0000000-0000-0000-0000-000000000009',
     'Jgst. 9 — NTG',
     'Einstieg in die Programmierung: OOP-Grundlagen, Kontrollstrukturen, einfache Datentypen',
     'Grade 9 — NTG',
     'Getting started with programming: OOP basics, control structures, simple data types'),
    ('a0000000-0000-0000-0000-000000000010',
     'Jgst. 10 — NTG',
     'Datenstrukturen, fortgeschrittene OOP, Sortier-/Suchalgorithmen',
     'Grade 10 — NTG',
     'Data structures, advanced OOP, sorting and searching algorithms'),
    ('a0000000-0000-0000-0000-000000000011',
     'Jgst. 11 — NTG',
     'Graphen, Codierung/Verschlüsselung, Künstliche Intelligenz',
     'Grade 11 — NTG',
     'Graphs, encoding/encryption, artificial intelligence'),
    ('7a3c9e2f-8b4d-4a1c-9f6e-3d5b7c1e8f02',
     'Jgst. 11 — Spät beginnend',
     'Einstieg in Algorithmen und Programmierung (spät beginnende Informatik)',
     'Grade 11 — Late start',
     'Getting started with algorithms and programming (late-start computer science)'),
    ('c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17',
     'Jgst. 12 — Grundlegend',
     'Rekursion, dynamische Datenstrukturen, Binärbäume',
     'Grade 12 — Foundation',
     'Recursion, dynamic data structures, binary trees'),
    ('9d2f5b8e-6c1a-4d7b-8e3f-1a9c5d7b2f4e',
     'Jgst. 12 — Erhöht',
     'Zusätzlich: Boolesche Algebra, formale Sprachen (Einstieg), Rechnerarchitektur',
     'Grade 12 — Advanced',
     'Additionally: Boolean algebra, formal languages (introduction), computer architecture'),
    ('5a8d3c7e-9b2f-4e6a-b1c4-0d7f8a3b9e2c',
     'Jgst. 13 — Grundlegend',
     'Formale Sprachen, Automaten, Berechenbarkeit, KI vertieft',
     'Grade 13 — Foundation',
     'Formal languages, automata, computability, AI in depth'),
    ('f1b7c4e9-2a8d-4f5c-a3e6-b8d0f4a2c7e1',
     'Jgst. 13 — Erhöht',
     'Zusätzlich: NEA/DEA, Kellerautomaten, Turingmaschine, Prolog',
     'Grade 13 — Advanced',
     'Additionally: NFA/DFA, pushdown automata, Turing machine, Prolog'),

    -- === Lernbereiche (sub-topics), Jgst. 9 NTG ===
    ('b9000000-0000-0000-0000-000000000001',
     'Variablen und Datentypen',
     'Deklaration, Zuweisung, primitive Datentypen (int, double, boolean, String)',
     'Variables and data types',
     'Declaration, assignment, primitive data types (int, double, boolean, String)'),
    ('b9000000-0000-0000-0000-000000000002',
     'Kontrollstrukturen: Selektion',
     'if/else-Verzweigungen, geschachtelte Bedingungen',
     'Control structures: selection',
     'if/else branching, nested conditions'),
    ('b9000000-0000-0000-0000-000000000003',
     'Kontrollstrukturen: Schleifen',
     'for-Schleife, while-Schleife, Zähler- und bedingungsgesteuerte Wiederholung',
     'Control structures: loops',
     'for loop, while loop, counter-controlled and condition-controlled repetition'),
    ('b9000000-0000-0000-0000-000000000004',
     'Methoden und Parameter',
     'Methodendefinition, Aufruf, Parameter, Rückgabewerte',
     'Methods and parameters',
     'Method definition, calls, parameters, return values'),
    ('b9000000-0000-0000-0000-000000000005',
     'Klassen und Objekte',
     'Klassendiagramm, Attribute, Konstruktoren, Objekte erzeugen',
     'Classes and objects',
     'Class diagram, attributes, constructors, creating objects'),

    -- === Jgst. 10 NTG ===
    ('b1000000-0000-0000-0000-000000000001',
     'Arrays und Listen',
     'Eindimensionale Arrays, Indexzugriff, Iteration, einfache Listenoperationen',
     'Arrays and lists',
     'One-dimensional arrays, index access, iteration, simple list operations'),
    ('b1000000-0000-0000-0000-000000000002',
     'Vererbung und Polymorphismus',
     'Generalisierung/Spezialisierung, Methodenüberschreibung, Polymorphismus (ansatzweise)',
     'Inheritance and polymorphism',
     'Generalisation/specialisation, method overriding, polymorphism (introductory)'),
    ('b1000000-0000-0000-0000-000000000003',
     'Such- und Sortieralgorithmen',
     'Lineare Suche, Selection Sort, Insertion Sort',
     'Searching and sorting algorithms',
     'Linear search, selection sort, insertion sort'),

    -- === Jgst. 11 NTG ===
    ('b1100000-0000-0000-0000-000000000001',
     'Graphen und Adjazenzmatrix',
     'Graphendarstellung, Adjazenzmatrix, Knotengrad, Nachbarn',
     'Graphs and adjacency matrices',
     'Graph representation, adjacency matrix, node degree, neighbours'),
    ('b1100000-0000-0000-0000-000000000002',
     'Graphalgorithmen: BFS und Dijkstra',
     'Breitensuche, kürzeste Wege, Dijkstra-Algorithmus',
     'Graph algorithms: BFS and Dijkstra',
     'Breadth-first search, shortest paths, Dijkstra''s algorithm'),
    ('b1100000-0000-0000-0000-000000000003',
     'Codierung und Verschlüsselung',
     'Binär-/Hexadezimalsystem, Caesar-Chiffre, Public-Key-Prinzip',
     'Encoding and encryption',
     'Binary and hexadecimal systems, Caesar cipher, public-key principle'),

    -- === Jgst. 12 Grundlegend ===
    ('b1200000-0000-0000-0000-000000000001',
     'Rekursion',
     'Lineare und verzweigte Rekursion: Fakultät, Fibonacci, ggT, Türme von Hanoi',
     'Recursion',
     'Linear and branching recursion: factorial, Fibonacci, GCD, Towers of Hanoi'),
    ('b1200000-0000-0000-0000-000000000002',
     'Verkettete Listen',
     'Einfach verkettete Liste, Kompositum-Pattern, Stack und Queue',
     'Linked lists',
     'Singly linked list, composite pattern, stack and queue'),
    ('b1200000-0000-0000-0000-000000000003',
     'Binärbäume',
     'Binärer Suchbaum, Traversierungen (Inorder/Preorder/Postorder)',
     'Binary trees',
     'Binary search tree, traversals (in-order/pre-order/post-order)')
) AS v(id, name, description, name_en, description_en)
WHERE ct.id = v.id::uuid;


-- ────────────────────────────────────────────────────────────────────
-- 2. TRACK LABELS (Ausbildungsrichtung)
-- ────────────────────────────────────────────────────────────────────
-- `track` is a free-text label — nothing in the backend matches it against a
-- literal (PromptBuilder only defaults a NULL track to 'NTG'), so respelling
-- it is safe.

UPDATE curriculum_topics AS ct SET
    track    = v.track,
    track_en = v.track_en
FROM (VALUES
    ('NTG',             'NTG',            'NTG'),
    ('Spaet beginnend', 'Spät beginnend', 'Late start'),
    ('Spät beginnend',  'Spät beginnend', 'Late start'),
    ('Grundlegend',     'Grundlegend',    'Foundation'),
    ('Erhoeht',         'Erhöht',         'Advanced'),
    ('Erhöht',          'Erhöht',         'Advanced')
) AS v(old_track, track, track_en)
WHERE ct.track = v.old_track;

COMMIT;
