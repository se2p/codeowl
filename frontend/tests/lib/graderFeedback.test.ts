import { describe, it, expect } from "vitest";
import { formatGraderFeedback } from "../../src/lib/graderFeedback.ts";

/**
 * js-parsons' getFeedback() returns an array of messages for LineBasedGrader
 * (parsons.js:1727), never a bare string. SolvePage rendered it only when
 * `typeof fb === "string"`, so every grader message was silently discarded and
 * students saw nothing but the red/green colouring.
 *
 * The first test is the regression: an array must produce output.
 */
describe("formatGraderFeedback", () => {
    it("renders the array shape LineBasedGrader actually returns", () => {
        const result = formatGraderFeedback(["Dein Programm hat zu wenige Blöcke."]);
        expect(result).toBe("Dein Programm hat zu wenige Blöcke.");
        expect(result).not.toBe("");
    });

    it("lists several messages instead of running them together", () => {
        const result = formatGraderFeedback([
            "Manche Blöcke sind in der falschen Reihenfolge.",
            "Dein Programm hat zu viele Blöcke.",
        ]);
        expect(result).toContain("<ul");
        expect(result).toContain("<li>Manche Blöcke sind in der falschen Reihenfolge.</li>");
        expect(result).toContain("<li>Dein Programm hat zu viele Blöcke.</li>");
    });

    it("returns nothing for a correct solution", () => {
        // errors is empty when the puzzle is solved; the completion banner takes over.
        expect(formatGraderFeedback([])).toBe("");
    });

    it("ignores blank and non-string entries rather than emitting empty bullets", () => {
        expect(formatGraderFeedback(["   ", "", null, undefined, 42])).toBe("");
        expect(formatGraderFeedback(["real message", "  ", null])).toBe("real message");
    });

    it("handles the html shape from the unittest graders", () => {
        expect(formatGraderFeedback({ feedback: "<span>failed</span>", success: false }))
            .toBe("<span>failed</span>");
    });

    it("still accepts a plain string", () => {
        expect(formatGraderFeedback("some message")).toBe("some message");
    });

    it("returns an empty string for nothing at all", () => {
        // getFeedback is optional-chained in SolvePage, so undefined is reachable.
        expect(formatGraderFeedback(undefined)).toBe("");
        expect(formatGraderFeedback(null)).toBe("");
        expect(formatGraderFeedback({})).toBe("");
        expect(formatGraderFeedback({ success: true })).toBe("");
    });
});
