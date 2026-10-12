import { describe, it, expect } from "vitest";
import { toGraceParts, fromGraceParts, formatGrace } from "../../src/lib/gracePeriod.ts";

/**
 * The grace period is typed as hours and minutes but stored as a single number
 * of minutes. A mistake in either direction silently changes how long late work
 * is accepted for, so the round trip is worth pinning.
 */
describe("grace period conversion", () => {
    it("splits stored minutes into hours and minutes", () => {
        expect(toGraceParts(90)).toEqual({ hours: "1", minutes: "30" });
        expect(toGraceParts(60)).toEqual({ hours: "1", minutes: "0" });
        expect(toGraceParts(45)).toEqual({ hours: "0", minutes: "45" });
        expect(toGraceParts(1445)).toEqual({ hours: "24", minutes: "5" });
    });

    it("combines the two fields back into minutes", () => {
        expect(fromGraceParts({ hours: "1", minutes: "30" })).toBe(90);
        expect(fromGraceParts({ hours: "2", minutes: "" })).toBe(120);
        expect(fromGraceParts({ hours: "", minutes: "45" })).toBe(45);
    });

    it("round-trips every value it will realistically meet", () => {
        for (const minutes of [1, 5, 30, 59, 60, 61, 90, 120, 480, 1440, 4321]) {
            expect(fromGraceParts(toGraceParts(minutes))).toBe(minutes);
        }
    });

    it("treats no grace and an explicit zero alike", () => {
        // Both mean a sharp deadline, and null is how that is stored.
        expect(fromGraceParts({ hours: "", minutes: "" })).toBeNull();
        expect(fromGraceParts({ hours: "0", minutes: "0" })).toBeNull();
        expect(fromGraceParts({ hours: "0", minutes: "" })).toBeNull();
        expect(toGraceParts(null)).toEqual({ hours: "", minutes: "" });
        expect(toGraceParts(0)).toEqual({ hours: "", minutes: "" });
        expect(toGraceParts(undefined)).toEqual({ hours: "", minutes: "" });
    });

    it("accepts minutes above 59 rather than being pedantic", () => {
        // Typing 90 in the minutes box plainly means 90 minutes.
        expect(fromGraceParts({ hours: "", minutes: "90" })).toBe(90);
        expect(fromGraceParts({ hours: "1", minutes: "90" })).toBe(150);
    });

    it("reports nonsense as unusable rather than guessing", () => {
        // undefined is distinct from null: null is a deliberate "no grace",
        // undefined means the field cannot be read and the save must stop.
        expect(fromGraceParts({ hours: "abc", minutes: "" })).toBeUndefined();
        expect(fromGraceParts({ hours: "", minutes: "1.5" })).toBeUndefined();
        expect(fromGraceParts({ hours: "-1", minutes: "" })).toBeUndefined();
        expect(fromGraceParts({ hours: "", minutes: "-30" })).toBeUndefined();
    });

    it("never returns a negative total", () => {
        for (const parts of [{ hours: "-5", minutes: "10" }, { hours: "1", minutes: "-90" }]) {
            const result = fromGraceParts(parts);
            expect(result === undefined || result === null || result > 0).toBe(true);
        }
    });

    it("formats a readable summary", () => {
        expect(formatGrace(90)).toBe("1 h 30 min");
        expect(formatGrace(120)).toBe("2 h");
        expect(formatGrace(45)).toBe("45 min");
        expect(formatGrace(null)).toBe("");
    });
});
