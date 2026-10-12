import { describe, it, expect } from "vitest";
import {
    addConcept,
    canonicalConcept,
    isNewConcept,
    removeConcept,
    suggestConcepts,
} from "../../src/lib/conceptSuggest";

// A slice of the real catalogue, including the mixed casing it actually has.
const CATALOGUE = [
    "2d-array",
    "Arrays",
    "array",
    "boolean-logic",
    "for-loop",
    "enhanced-for-loop",
    "recursion",
    "Rekursion",
];

describe("suggesting from the catalogue", () => {
    it("lists the catalogue before anything is typed, so it can be browsed", () => {
        expect(suggestConcepts(CATALOGUE, "", [], 3)).toEqual(["2d-array", "Arrays", "array"]);
    });

    it("ranks names that start with the query above ones that merely contain it", () => {
        // "arr" should reach `array` before `2d-array`.
        const hits = suggestConcepts(CATALOGUE, "arr", []);
        expect(hits.indexOf("array")).toBeLessThan(hits.indexOf("2d-array"));
        expect(hits).toContain("Arrays");
    });

    it("matches case-insensitively", () => {
        expect(suggestConcepts(CATALOGUE, "FOR-LOOP", [])).toEqual(["for-loop", "enhanced-for-loop"]);
    });

    it("drops what is already selected", () => {
        expect(suggestConcepts(CATALOGUE, "rec", ["recursion"])).toEqual([]);
        // Selection is compared case-insensitively too.
        expect(suggestConcepts(CATALOGUE, "arr", ["ARRAY"])).not.toContain("array");
    });

    it("honours the limit", () => {
        expect(suggestConcepts(CATALOGUE, "", [], 2)).toHaveLength(2);
    });

    it("returns nothing for a query the catalogue does not know", () => {
        expect(suggestConcepts(CATALOGUE, "kellerautomat", [])).toEqual([]);
    });
});

describe("adding a concept", () => {
    it("keeps the catalogue's spelling rather than what was typed", () => {
        // Otherwise "FOR-LOOP" would create a second concept next to `for-loop`,
        // which is exactly what the backend's case-insensitive match prevents.
        expect(canonicalConcept(CATALOGUE, "FOR-LOOP")).toBe("for-loop");
        expect(addConcept([], CATALOGUE, "  Recursion ")).toEqual(["recursion"]);
    });

    it("lowercases a name outside the catalogue, which is how it will be stored", () => {
        // PostgreConceptDAO normalises on insert and a CHECK constraint enforces
        // it, so showing the typed casing would promise a spelling the database
        // is not going to keep.
        expect(addConcept([], CATALOGUE, "  Kellerautomaten ")).toEqual(["kellerautomaten"]);
        expect(canonicalConcept(CATALOGUE, "NEA/DEA")).toBe("nea/dea");
    });

    it("is a no-op for a blank name or one already selected", () => {
        const selected = ["array"];
        expect(addConcept(selected, CATALOGUE, "   ")).toBe(selected);
        expect(addConcept(selected, CATALOGUE, "ARRAY")).toBe(selected);
    });

    it("appends rather than reordering", () => {
        expect(addConcept(["array"], CATALOGUE, "for-loop")).toEqual(["array", "for-loop"]);
    });
});

describe("marking a name as new", () => {
    it("is true only for a non-blank name the catalogue lacks", () => {
        expect(isNewConcept(CATALOGUE, "Kellerautomaten")).toBe(true);
        expect(isNewConcept(CATALOGUE, "array")).toBe(false);
        expect(isNewConcept(CATALOGUE, "ARRAY")).toBe(false);
        expect(isNewConcept(CATALOGUE, "   ")).toBe(false);
        expect(isNewConcept(CATALOGUE, "")).toBe(false);
    });
});

describe("removing a concept", () => {
    it("removes case-insensitively and leaves the rest in order", () => {
        expect(removeConcept(["array", "for-loop", "recursion"], "FOR-LOOP"))
            .toEqual(["array", "recursion"]);
    });
});
