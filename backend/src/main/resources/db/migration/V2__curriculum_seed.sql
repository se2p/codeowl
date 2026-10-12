-- ════════════════════════════════════════════════════════════
-- AP19: Curriculum Seed Data — LehrplanPLUS Bayern Informatik (G9)
-- Runs AFTER z_codeowl_schema.sql (alphabetical: zz_ > z_)
--
-- NOTE: the German below is deliberately transliterated (ae/oe/ue) and there
-- is no English here. 11_migration_curriculum_i18n.sql runs after this file
-- and rewrites both: it respells these names with umlauts and fills the
-- name_en/description_en/track_en columns. Add new topics here, then add
-- their translations there.
-- ════════════════════════════════════════════════════════════


-- ────────────────────────────────────────────────────────────
-- 1. TOP-LEVEL CURRICULUM TOPICS (Jahrgangsstufen)
-- ────────────────────────────────────────────────────────────

INSERT INTO curriculum_topics (id, name, description, grade_level, track, learning_area, parent_id, sort_order)
VALUES
  -- Jgst. 9 NTG
  ('a0000000-0000-0000-0000-000000000009', 'Jgst. 9 — NTG', 'Einstieg in die Programmierung: OOP-Grundlagen, Kontrollstrukturen, einfache Datentypen', 9, 'NTG', NULL, NULL, 9),
  -- Jgst. 10 NTG
  ('a0000000-0000-0000-0000-000000000010', 'Jgst. 10 — NTG', 'Datenstrukturen, fortgeschrittene OOP, Sortier-/Suchalgorithmen', 10, 'NTG', NULL, NULL, 10),
  -- Jgst. 11 NTG
  ('a0000000-0000-0000-0000-000000000011', 'Jgst. 11 — NTG', 'Graphen, Codierung/Verschluesselung, Kuenstliche Intelligenz', 11, 'NTG', NULL, NULL, 11),
  -- Jgst. 11 Spaet
  ('7a3c9e2f-8b4d-4a1c-9f6e-3d5b7c1e8f02', 'Jgst. 11 — Spaet beginnend', 'Einstieg in Algorithmen und Programmierung (spaet beginnende Informatik)', 11, 'Spaet beginnend', NULL, NULL, 1011),
  -- Jgst. 12 Grundlegend
  ('c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17', 'Jgst. 12 — Grundlegend', 'Rekursion, dynamische Datenstrukturen, Binaerbaeume', 12, 'Grundlegend', NULL, NULL, 12),
  -- Jgst. 12 Erhoeht
  ('9d2f5b8e-6c1a-4d7b-8e3f-1a9c5d7b2f4e', 'Jgst. 12 — Erhoeht', 'Zusaetzlich: Boolesche Algebra, formale Sprachen (Einstieg), Rechnerarchitektur', 12, 'Erhoeht', NULL, NULL, 1012),
  -- Jgst. 13 Grundlegend
  ('5a8d3c7e-9b2f-4e6a-b1c4-0d7f8a3b9e2c', 'Jgst. 13 — Grundlegend', 'Formale Sprachen, Automaten, Berechenbarkeit, KI vertieft', 13, 'Grundlegend', NULL, NULL, 13),
  -- Jgst. 13 Erhoeht
  ('f1b7c4e9-2a8d-4f5c-a3e6-b8d0f4a2c7e1', 'Jgst. 13 — Erhoeht', 'Zusaetzlich: NEA/DEA, Kellerautomaten, Turingmaschine, Prolog', 13, 'Erhoeht', NULL, NULL, 1013)
ON CONFLICT (id) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 2. SUB-TOPICS (Lernbereiche)
-- ────────────────────────────────────────────────────────────

INSERT INTO curriculum_topics (id, name, description, grade_level, track, learning_area, parent_id, sort_order)
VALUES
  -- === Jgst. 9 NTG ===
  ('b9000000-0000-0000-0000-000000000001', 'Variablen und Datentypen', 'Deklaration, Zuweisung, primitive Datentypen (int, double, boolean, String)', 9, 'NTG', 'Inf9 LB3: Softwareentwicklung', 'a0000000-0000-0000-0000-000000000009', 1),
  ('b9000000-0000-0000-0000-000000000002', 'Kontrollstrukturen: Selektion', 'if/else-Verzweigungen, geschachtelte Bedingungen', 9, 'NTG', 'Inf9 LB3: Softwareentwicklung', 'a0000000-0000-0000-0000-000000000009', 2),
  ('b9000000-0000-0000-0000-000000000003', 'Kontrollstrukturen: Schleifen', 'for-Schleife, while-Schleife, Zaehler- und bedingungsgesteuerte Wiederholung', 9, 'NTG', 'Inf9 LB3: Softwareentwicklung', 'a0000000-0000-0000-0000-000000000009', 3),
  ('b9000000-0000-0000-0000-000000000004', 'Methoden und Parameter', 'Methodendefinition, Aufruf, Parameter, Rueckgabewerte', 9, 'NTG', 'Inf9 LB3: Softwareentwicklung', 'a0000000-0000-0000-0000-000000000009', 4),
  ('b9000000-0000-0000-0000-000000000005', 'Klassen und Objekte', 'Klassendiagramm, Attribute, Konstruktoren, Objekte erzeugen', 9, 'NTG', 'Inf9 LB3: Softwareentwicklung', 'a0000000-0000-0000-0000-000000000009', 5),

  -- === Jgst. 10 NTG ===
  ('b1000000-0000-0000-0000-000000000001', 'Arrays und Listen', 'Eindimensionale Arrays, Indexzugriff, Iteration, einfache Listenoperationen', 10, 'NTG', 'Inf10 LB2: Datenstrukturen und OOP', 'a0000000-0000-0000-0000-000000000010', 1),
  ('b1000000-0000-0000-0000-000000000002', 'Vererbung und Polymorphismus', 'Generalisierung/Spezialisierung, Methodenueberschreibung, Polymorphismus (ansatzweise)', 10, 'NTG', 'Inf10 LB2: Datenstrukturen und OOP', 'a0000000-0000-0000-0000-000000000010', 2),
  ('b1000000-0000-0000-0000-000000000003', 'Such- und Sortieralgorithmen', 'Lineare Suche, Selection Sort, Insertion Sort', 10, 'NTG', 'Inf10 LB2: Datenstrukturen und OOP', 'a0000000-0000-0000-0000-000000000010', 3),

  -- === Jgst. 11 NTG ===
  ('b1100000-0000-0000-0000-000000000001', 'Graphen und Adjazenzmatrix', 'Graphendarstellung, Adjazenzmatrix, Knotengrad, Nachbarn', 11, 'NTG', 'Inf11 LB1: Graphen', 'a0000000-0000-0000-0000-000000000011', 1),
  ('b1100000-0000-0000-0000-000000000002', 'Graphalgorithmen: BFS und Dijkstra', 'Breitensuche, kuerzeste Wege, Dijkstra-Algorithmus', 11, 'NTG', 'Inf11 LB1: Graphen', 'a0000000-0000-0000-0000-000000000011', 2),
  ('b1100000-0000-0000-0000-000000000003', 'Codierung und Verschluesselung', 'Binaer-/Hexadezimalsystem, Caesar-Chiffre, Public-Key-Prinzip', 11, 'NTG', 'Inf11 LB2: Codierung', 'a0000000-0000-0000-0000-000000000011', 3),

  -- === Jgst. 12 Grundlegend ===
  ('b1200000-0000-0000-0000-000000000001', 'Rekursion', 'Lineare und verzweigte Rekursion: Fakultaet, Fibonacci, ggT, Tuerme von Hanoi', 12, 'Grundlegend', 'Inf12 LB1: Rekursion', 'c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17', 1),
  ('b1200000-0000-0000-0000-000000000002', 'Verkettete Listen', 'Einfach verkettete Liste, Kompositum-Pattern, Stack und Queue', 12, 'Grundlegend', 'Inf12 LB2: Dynamische Datenstrukturen', 'c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17', 2),
  ('b1200000-0000-0000-0000-000000000003', 'Binaerbaeume', 'Binaerer Suchbaum, Traversierungen (Inorder/Preorder/Postorder)', 12, 'Grundlegend', 'Inf12 LB2: Dynamische Datenstrukturen', 'c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17', 3)
ON CONFLICT (id) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 3. CONCEPT ↔ CURRICULUM TOPIC MAPPINGS
-- ────────────────────────────────────────────────────────────
-- Uses concept names to look up IDs dynamically.

-- Jgst. 9: Variablen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b9000000-0000-0000-0000-000000000001'::uuid FROM concepts c WHERE c.name IN ('variable-declaration', 'data-types', 'arithmetic-operators', 'comparison-operators') ON CONFLICT DO NOTHING;

-- Jgst. 9: Selektion
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b9000000-0000-0000-0000-000000000002'::uuid FROM concepts c WHERE c.name IN ('if-else', 'boolean-logic', 'comparison-operators') ON CONFLICT DO NOTHING;

-- Jgst. 9: Schleifen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b9000000-0000-0000-0000-000000000003'::uuid FROM concepts c WHERE c.name IN ('for-loop', 'while-loop', 'break-continue') ON CONFLICT DO NOTHING;

-- Jgst. 9: Methoden
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b9000000-0000-0000-0000-000000000004'::uuid FROM concepts c WHERE c.name IN ('function-definition', 'parameters-arguments', 'return-value') ON CONFLICT DO NOTHING;

-- Jgst. 9: Klassen und Objekte
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b9000000-0000-0000-0000-000000000005'::uuid FROM concepts c WHERE c.name IN ('class-definition', 'object-instantiation', 'constructor', 'getter-setter') ON CONFLICT DO NOTHING;

-- Jgst. 10: Arrays und Listen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1000000-0000-0000-0000-000000000001'::uuid FROM concepts c WHERE c.name IN ('array', 'for-loop', 'enhanced-for-loop', 'nested-loops') ON CONFLICT DO NOTHING;

-- Jgst. 10: Vererbung
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1000000-0000-0000-0000-000000000002'::uuid FROM concepts c WHERE c.name IN ('inheritance', 'polymorphism', 'method-overriding', 'abstract-class') ON CONFLICT DO NOTHING;

-- Jgst. 10: Suchen und Sortieren
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1000000-0000-0000-0000-000000000003'::uuid FROM concepts c WHERE c.name IN ('sorting', 'linear-search', 'array', 'for-loop', 'nested-loops') ON CONFLICT DO NOTHING;

-- Jgst. 11: Graphen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1100000-0000-0000-0000-000000000001'::uuid FROM concepts c WHERE c.name IN ('2d-array', 'for-loop', 'nested-loops', 'array') ON CONFLICT DO NOTHING;

-- Jgst. 11: Graphalgorithmen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1100000-0000-0000-0000-000000000002'::uuid FROM concepts c WHERE c.name IN ('queue', 'for-loop', 'while-loop', 'array', '2d-array') ON CONFLICT DO NOTHING;

-- Jgst. 12: Rekursion
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1200000-0000-0000-0000-000000000001'::uuid FROM concepts c WHERE c.name IN ('recursion', 'function-definition', 'parameters-arguments', 'return-value', 'if-else') ON CONFLICT DO NOTHING;

-- Jgst. 12: Verkettete Listen
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1200000-0000-0000-0000-000000000002'::uuid FROM concepts c WHERE c.name IN ('linked-list', 'stack', 'queue', 'class-definition', 'recursion') ON CONFLICT DO NOTHING;

-- Jgst. 12: Binaerbaeume
INSERT INTO concept_curriculum_topic (concept_id, topic_id)
SELECT c.id, 'b1200000-0000-0000-0000-000000000003'::uuid FROM concepts c WHERE c.name IN ('recursion', 'class-definition', 'if-else') ON CONFLICT DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 4. CURATED CODE EXAMPLES
-- ────────────────────────────────────────────────────────────
-- To be populated manually by the teacher/admin.
-- Insert into curriculum_code_examples (topic_id, language, title, code, problem_statement, difficulty).
