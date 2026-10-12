-- Reverses migration/V11__curriculum_i18n.sql: drops the three English columns
-- it added, and restores the transliterated (ae/oe/ue) German spelling that
-- migration/V2__curriculum_seed.sql originally wrote for every row V11 actually
-- changed. Rows V11 left untouched (no umlaut in the original) are not listed
-- here -- there is nothing to revert.

BEGIN;

UPDATE curriculum_topics AS ct SET
    name        = v.name,
    description = v.description
FROM (VALUES
    ('a0000000-0000-0000-0000-000000000011',
     'Jgst. 11 — NTG',
     'Graphen, Codierung/Verschluesselung, Kuenstliche Intelligenz'),
    ('7a3c9e2f-8b4d-4a1c-9f6e-3d5b7c1e8f02',
     'Jgst. 11 — Spaet beginnend',
     'Einstieg in Algorithmen und Programmierung (spaet beginnende Informatik)'),
    ('c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17',
     'Jgst. 12 — Grundlegend',
     'Rekursion, dynamische Datenstrukturen, Binaerbaeume'),
    ('9d2f5b8e-6c1a-4d7b-8e3f-1a9c5d7b2f4e',
     'Jgst. 12 — Erhoeht',
     'Zusaetzlich: Boolesche Algebra, formale Sprachen (Einstieg), Rechnerarchitektur'),
    ('f1b7c4e9-2a8d-4f5c-a3e6-b8d0f4a2c7e1',
     'Jgst. 13 — Erhoeht',
     'Zusaetzlich: NEA/DEA, Kellerautomaten, Turingmaschine, Prolog'),
    ('b9000000-0000-0000-0000-000000000003',
     'Kontrollstrukturen: Schleifen',
     'for-Schleife, while-Schleife, Zaehler- und bedingungsgesteuerte Wiederholung'),
    ('b9000000-0000-0000-0000-000000000004',
     'Methoden und Parameter',
     'Methodendefinition, Aufruf, Parameter, Rueckgabewerte'),
    ('b1000000-0000-0000-0000-000000000002',
     'Vererbung und Polymorphismus',
     'Generalisierung/Spezialisierung, Methodenueberschreibung, Polymorphismus (ansatzweise)'),
    ('b1100000-0000-0000-0000-000000000002',
     'Graphalgorithmen: BFS und Dijkstra',
     'Breitensuche, kuerzeste Wege, Dijkstra-Algorithmus'),
    ('b1100000-0000-0000-0000-000000000003',
     'Codierung und Verschluesselung',
     'Binaer-/Hexadezimalsystem, Caesar-Chiffre, Public-Key-Prinzip'),
    ('b1200000-0000-0000-0000-000000000001',
     'Rekursion',
     'Lineare und verzweigte Rekursion: Fakultaet, Fibonacci, ggT, Tuerme von Hanoi'),
    ('b1200000-0000-0000-0000-000000000003',
     'Binaerbaeume',
     'Binaerer Suchbaum, Traversierungen (Inorder/Preorder/Postorder)')
) AS v(id, name, description)
WHERE ct.id = v.id::uuid;

UPDATE curriculum_topics SET track = 'Spaet beginnend' WHERE track = 'Spät beginnend';
UPDATE curriculum_topics SET track = 'Erhoeht'         WHERE track = 'Erhöht';

ALTER TABLE curriculum_topics
    DROP COLUMN IF EXISTS name_en,
    DROP COLUMN IF EXISTS description_en,
    DROP COLUMN IF EXISTS track_en;

COMMIT;

DELETE FROM flyway_schema_history WHERE version='11';
