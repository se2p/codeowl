import { describe, it, expect } from "vitest";
import { toDisplayLines } from "../../src/lib/solutionLines.ts";

/**
 * The solution as a teacher should see it, next to what the student actually built.
 *
 * The point of this helper is the stripping. A stored solution keeps the annotations js-parsons
 * reads and then hides, while a student's saved arrangement holds the text the widget put on
 * screen — so without this, `base = 8 #swap:init` would sit beside `base = 8` in a side-by-side
 * comparison and two identical lines would look like a difference.
 */
describe("toDisplayLines", () => {
    it("splits a solution into its lines", () => {
        expect(toDisplayLines("def f():\n    return 1")).toEqual(["def f():", "    return 1"]);
    });

    it("strips the #swap marker the widget hides", () => {
        expect(toDisplayLines("base = 8 #swap:init")).toEqual(["base = 8"]);
    });

    it("strips the #group marker", () => {
        expect(toDisplayLines("total = 0 #group:setup")).toEqual(["total = 0"]);
    });

    it("strips #distractor", () => {
        expect(toDisplayLines("x = 1 #distractor")).toEqual(["x = 1"]);
    });

    it("mirrors the widget when markers are stacked, rather than out-cleverring it", () => {
        // parsons.js anchors each of these at end-of-line too (parsons.js:1001-1017), so a
        // #swap that is not last survives there and shows up in the block the student sees.
        // Stripping it here would *create* a mismatch against their saved text. The
        // combination should not occur anyway -- TaskEnricher:326 keeps #distractor lines
        // free of #swap and #group.
        expect(toDisplayLines("x = 1 #swap:a #distractor")).toEqual(["x = 1 #swap:a"]);
    });

    it("keeps leading indentation, which is part of the line", () => {
        expect(toDisplayLines("    height = 5 #swap:init")).toEqual(["    height = 5"]);
    });

    it("drops blank lines, which are never dealt as blocks", () => {
        expect(toDisplayLines("a = 1\n\n   \nb = 2")).toEqual(["a = 1", "b = 2"]);
    });

    it("leaves gap markers alone for renderGapLine to handle", () => {
        expect(toDisplayLines("return $$blank:x + y$$")).toEqual(["return $$blank:x + y$$"]);
    });

    it("does not strip a # that is not one of the markers", () => {
        expect(toDisplayLines("x = 1  # a real comment")).toEqual(["x = 1  # a real comment"]);
    });

    it("is empty for nothing", () => {
        expect(toDisplayLines("")).toEqual([]);
        expect(toDisplayLines(null)).toEqual([]);
        expect(toDisplayLines(undefined)).toEqual([]);
    });
});
