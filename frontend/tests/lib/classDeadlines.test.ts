import { describe, it, expect } from "vitest";
import { nextDeadline, formatDeadline, SOON_MS } from "../../src/lib/classDeadlines.ts";
import type { ClassWorksheetInfo } from "../../src/types/models.ts";

const NOW = Date.parse("2026-09-03T12:00:00Z");

function ws(over: Partial<ClassWorksheetInfo> & { worksheetId: string }): ClassWorksheetInfo {
    return {
        name: over.worksheetId,
        description: "",
        published: true,
        releaseAt: null,
        deadlineAt: null,
        graceMinutes: null,
        availability: "OPEN",
        ...over,
    };
}

const at = (h: number) => new Date(NOW + h * 3600_000).toISOString();

describe("nextDeadline", () => {
    it("returns nothing when there are no worksheets", () => {
        expect(nextDeadline([], NOW)).toBeNull();
        expect(nextDeadline(null, NOW)).toBeNull();
        expect(nextDeadline(undefined, NOW)).toBeNull();
    });

    it("ignores worksheets that have no deadline", () => {
        // Open indefinitely: nothing to plan around.
        expect(nextDeadline([ws({ worksheetId: "a" })], NOW)).toBeNull();
    });

    it("picks the earliest deadline still ahead", () => {
        const result = nextDeadline([
            ws({ worksheetId: "later", name: "Later", deadlineAt: at(100) }),
            ws({ worksheetId: "soonest", name: "Soonest", deadlineAt: at(5) }),
            ws({ worksheetId: "middle", name: "Middle", deadlineAt: at(50) }),
        ], NOW);

        expect(result?.worksheetId).toBe("soonest");
        expect(result?.name).toBe("Soonest");
        expect(result?.msRemaining).toBe(5 * 3600_000);
    });

    it("skips worksheets whose deadline has already passed", () => {
        // A closed one is shown on the worksheet itself, not as what is next.
        const result = nextDeadline([
            ws({ worksheetId: "gone", deadlineAt: at(-10), availability: "CLOSED" }),
            ws({ worksheetId: "next", deadlineAt: at(30) }),
        ], NOW);

        expect(result?.worksheetId).toBe("next");
    });

    it("returns nothing when every deadline has passed", () => {
        expect(nextDeadline([
            ws({ worksheetId: "gone", deadlineAt: at(-10), availability: "CLOSED" }),
        ], NOW)).toBeNull();
    });

    it("ignores anything not released yet, whatever its deadline", () => {
        // The server filters these out for students already; belt and braces,
        // because leaking one here would announce an unreleased worksheet by name.
        expect(nextDeadline([
            ws({ worksheetId: "secret", name: "Secret", deadlineAt: at(1), availability: "SCHEDULED" }),
        ], NOW)).toBeNull();
    });

    it("still counts a worksheet inside its grace period", () => {
        // Past the deadline but the server says OPEN, so the grace is running
        // and it can still be handed in.
        const result = nextDeadline([
            ws({ worksheetId: "grace", deadlineAt: at(-1), availability: "OPEN" }),
        ], NOW);

        expect(result?.worksheetId).toBe("grace");
        expect(result?.msRemaining).toBeLessThan(0);
    });

    it("flags a deadline within a day as soon", () => {
        expect(nextDeadline([ws({ worksheetId: "a", deadlineAt: at(5) })], NOW)?.soon).toBe(true);
        expect(nextDeadline([ws({ worksheetId: "a", deadlineAt: at(48) })], NOW)?.soon).toBe(false);
    });

    it("treats exactly one day out as soon", () => {
        const result = nextDeadline([ws({ worksheetId: "a", deadlineAt: at(24) })], NOW);
        expect(result?.msRemaining).toBe(SOON_MS);
        expect(result?.soon).toBe(true);
    });

    it("breaks ties on the name so the card does not flicker", () => {
        const result = nextDeadline([
            ws({ worksheetId: "b", name: "Beta", deadlineAt: at(5) }),
            ws({ worksheetId: "a", name: "Alpha", deadlineAt: at(5) }),
        ], NOW);

        expect(result?.name).toBe("Alpha");
    });

    it("drops a deadline that cannot be parsed", () => {
        // Otherwise it sorts first as NaN and shows as "Invalid Date".
        const result = nextDeadline([
            ws({ worksheetId: "bad", deadlineAt: "not a date" }),
            ws({ worksheetId: "good", deadlineAt: at(9) }),
        ], NOW);

        expect(result?.worksheetId).toBe("good");
    });
});

describe("formatDeadline", () => {
    it("shows the date and the time", () => {
        const text = formatDeadline("2026-09-03T22:00:00Z", "en-GB");
        expect(text).toMatch(/2026/);
        // The time matters: a bare date means midnight, not "any time that day".
        expect(text).toMatch(/\d{1,2}:\d{2}/);
    });

    it("follows the locale it is given", () => {
        const iso = "2026-09-03T22:00:00Z";
        expect(formatDeadline(iso, "de-DE")).not.toBe(formatDeadline(iso, "en-GB"));
    });

    it("returns nothing for an unparseable instant", () => {
        expect(formatDeadline("nonsense", "en-GB")).toBe("");
    });
});
