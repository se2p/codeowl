import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { prefersEnglish, topicName, topicDescription, topicTrack } from "../../src/lib/curriculumLabels";
import type { CurriculumTopic } from "../../src/types/models";

const topic = (over: Partial<CurriculumTopic> = {}): CurriculumTopic => ({
    id: "b1200000-0000-0000-0000-000000000003",
    name: "Binärbäume",
    description: "Binärer Suchbaum, Traversierungen",
    gradeLevel: 12,
    track: "Grundlegend",
    learningArea: "Inf12 LB2",
    parentId: "c8e4a1d7-3f9b-4c2e-a5d8-6b0f9e3c2a17",
    sortOrder: 3,
    nameEn: "Binary trees",
    descriptionEn: "Binary search tree, traversals",
    trackEn: "Foundation",
    educationLevel: "SCHOOL",
    sequenceCount: 0,
    ...over,
});

describe("curriculum label language", () => {
    it("serves German by default", () => {
        expect(topicName(topic(), "de")).toBe("Binärbäume");
        expect(topicDescription(topic(), "de")).toBe("Binärer Suchbaum, Traversierungen");
        expect(topicTrack(topic(), "de")).toBe("Grundlegend");
    });

    it("serves English when the UI is English", () => {
        expect(topicName(topic(), "en")).toBe("Binary trees");
        expect(topicDescription(topic(), "en")).toBe("Binary search tree, traversals");
        expect(topicTrack(topic(), "en")).toBe("Foundation");
    });

    it("matches the language, not the region", () => {
        // i18next-browser-languagedetector reports what the browser reports.
        expect(prefersEnglish("en-US")).toBe(true);
        expect(prefersEnglish("EN")).toBe(true);
        expect(prefersEnglish("de-AT")).toBe(false);
        expect(prefersEnglish(undefined)).toBe(false);
        expect(topicName(topic(), "en-GB")).toBe("Binary trees");
    });

    it("falls back to German when a translation is missing", () => {
        // A topic added to the curriculum without a translation: show German
        // rather than an empty row.
        const untranslated = topic({ nameEn: null, descriptionEn: null, trackEn: null });
        expect(topicName(untranslated, "en")).toBe("Binärbäume");
        expect(topicDescription(untranslated, "en")).toBe("Binärer Suchbaum, Traversierungen");
        expect(topicTrack(untranslated, "en")).toBe("Grundlegend");

        // An empty string counts as missing too, not as a deliberate blank.
        expect(topicName(topic({ nameEn: "   " }), "en")).toBe("Binärbäume");
    });
});

/**
 * The seed and the translation migration are two files that have to stay in
 * step: a topic added to V2__curriculum_seed.sql without a row in
 * V11__curriculum_i18n.sql would silently fall back to German everywhere.
 * This catches that at test time instead of in the UI.
 */
describe("curriculum translation coverage", () => {
    const dbMigration = resolve(
        dirname(fileURLToPath(import.meta.url)),
        "../../../backend/src/main/resources/db/migration",
    );
    const seed = readFileSync(resolve(dbMigration, "V2__curriculum_seed.sql"), "utf-8");
    const migration = readFileSync(resolve(dbMigration, "V11__curriculum_i18n.sql"), "utf-8");

    const UUID = /'([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})'/g;
    const idsIn = (sql: string, section: string) => {
        const start = sql.indexOf(section);
        expect(start, `section not found: ${section}`).toBeGreaterThan(-1);
        const body = sql.slice(start, sql.indexOf(";", start));
        return new Set(Array.from(body.matchAll(UUID), m => m[1]));
    };

    it("translates every topic the seed inserts", () => {
        const seeded = new Set([
            ...idsIn(seed, "-- 1. TOP-LEVEL CURRICULUM TOPICS"),
            ...idsIn(seed, "-- 2. SUB-TOPICS"),
        ]);
        const translated = idsIn(migration, "-- 1. TOPIC NAMES AND DESCRIPTIONS");

        expect(seeded.size).toBeGreaterThan(0);
        const missing = [...seeded].filter(id => !translated.has(id));
        const extra = [...translated].filter(id => !seeded.has(id));
        expect(missing, "seeded topics with no English translation").toEqual([]);
        expect(extra, "translations for topics that are not seeded").toEqual([]);
    });

    it("keeps the migration's own German free of the seed transliteration", () => {
        // Section 2 is excluded on purpose: it maps the seed's old track
        // spellings ('Spaet beginnend' → 'Spät beginnend'), so it has to name them.
        const section = migration.slice(
            migration.indexOf("-- 1. TOPIC NAMES"),
            migration.indexOf("-- 2. TRACK LABELS"),
        );
        expect(section).not.toMatch(/Binaer|Verschluesselung|Kuenstlich|Zaehler|Rueckgabe|Fakultaet|Tuerme/);
        expect(section).toContain("Binärbäume");
    });

    it("sets the client encoding before writing umlauts", () => {
        // Without this, psql on a Windows console (CP850) stores mojibake —
        // see db/maintenance/fix_utf8_cp850_mojibake.py.
        expect(migration).toMatch(/SET client_encoding TO 'UTF8';/);
    });
});
