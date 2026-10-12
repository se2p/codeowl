import { describe, it, expect } from "vitest";
import {
    educationLevelOf,
    matchesEducationLevel,
    filterByEducationLevel,
    hasMixedEducationLevels,
} from "../../src/lib/curriculumFilter";
import type { CurriculumTopic } from "../../src/types/models";

const topic = (over: Partial<CurriculumTopic> = {}): CurriculumTopic => ({
    id: "a0000000-0000-0000-0000-000000000009",
    name: "Jgst. 9 — NTG",
    description: "Einstieg in die Programmierung",
    gradeLevel: 9,
    track: "NTG",
    learningArea: null,
    parentId: null,
    sortOrder: 9,
    nameEn: "Grade 9 — NTG",
    descriptionEn: "Getting started with programming",
    trackEn: "NTG",
    educationLevel: "SCHOOL",
    sequenceCount: 0,
    ...over,
});

const module_ = (name: string) => topic({
    name,
    nameEn: name,
    gradeLevel: null,
    track: null,
    trackEn: null,
    educationLevel: "UNIVERSITY",
});

describe("education level of a topic", () => {
    it("reads the column", () => {
        expect(educationLevelOf(topic())).toBe("SCHOOL");
        expect(educationLevelOf(module_("Programmieren 1"))).toBe("UNIVERSITY");
        expect(educationLevelOf(topic({ educationLevel: "university" }))).toBe("UNIVERSITY");
    });

    it("treats a missing or unknown value as school curriculum", () => {
        // Rows written before 12_migration_curriculum_education_level.sql, or
        // by a client that omitted the field: the whole seed is LehrplanPLUS,
        // and defaulting to SCHOOL keeps such a topic visible under a filter
        // rather than under neither.
        expect(educationLevelOf(topic({ educationLevel: null }))).toBe("SCHOOL");
        expect(educationLevelOf(topic({ educationLevel: "" }))).toBe("SCHOOL");
        expect(educationLevelOf(topic({ educationLevel: "COLLEGE" }))).toBe("SCHOOL");
    });
});

describe("filtering the overview", () => {
    const topics = [topic(), module_("Programmieren 1"), module_("Programmieren 2")];

    it("shows everything when the filter is off", () => {
        expect(filterByEducationLevel(topics, "ALL")).toHaveLength(3);
        expect(matchesEducationLevel(module_("Programmieren 1"), "ALL")).toBe(true);
    });

    it("narrows to one level", () => {
        expect(filterByEducationLevel(topics, "SCHOOL").map(t => t.name)).toEqual(["Jgst. 9 — NTG"]);
        expect(filterByEducationLevel(topics, "UNIVERSITY").map(t => t.name))
            .toEqual(["Programmieren 1", "Programmieren 2"]);
    });

    it("never mutates or reorders the input", () => {
        const before = [...topics];
        filterByEducationLevel(topics, "UNIVERSITY");
        expect(topics).toEqual(before);
    });
});

describe("whether the filter is worth showing", () => {
    it("stays hidden while the curriculum is school-only", () => {
        // Every installation until someone adds a module: a control whose
        // options all show the same list is just noise.
        expect(hasMixedEducationLevels([topic(), topic({ id: "b", name: "Jgst. 10" })])).toBe(false);
        expect(hasMixedEducationLevels([])).toBe(false);
    });

    it("stays hidden while the curriculum is university-only", () => {
        expect(hasMixedEducationLevels([module_("Programmieren 1")])).toBe(false);
    });

    it("appears once both exist", () => {
        expect(hasMixedEducationLevels([topic(), module_("Programmieren 1")])).toBe(true);
    });
});
