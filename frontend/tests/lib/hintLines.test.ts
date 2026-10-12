import { describe, it, expect } from "vitest";
import { splitHintClauses } from "../../src/lib/hintLines.ts";
import en from "../../src/i18n/en.json";
import de from "../../src/i18n/de.json";

describe("splitHintClauses", () => {
    it("breaks at the semicolon and keeps it on the line it ends", () => {
        expect(splitHintClauses("Empty = available at once; no time = 00:00"))
            .toEqual(["Empty = available at once;", "no time = 00:00"]);
    });

    it("leaves a hint without a semicolon as one line", () => {
        expect(splitHintClauses("Empty or 0 = sharp deadline"))
            .toEqual(["Empty or 0 = sharp deadline"]);
    });

    it("drops the empty line a trailing semicolon would leave", () => {
        expect(splitHintClauses("One thing;")).toEqual(["One thing;"]);
    });

    it("handles an empty hint", () => {
        expect(splitHintClauses("")).toEqual([]);
    });

    it("splits the release and deadline hints that actually ship", () => {
        // The point of the change: these are the two that were running on.
        for (const locale of [en, de]) {
            const s = locale.schedule as Record<string, string>;
            expect(splitHintClauses(s.releaseHint)).toHaveLength(2);
            expect(splitHintClauses(s.deadlineHint)).toHaveLength(2);
            // The grace hint is a single rule and stays on one line.
            expect(splitHintClauses(s.graceHint)).toHaveLength(1);
        }
    });

    it("keeps the German umlauts and wording intact", () => {
        expect(splitHintClauses(de.schedule.releaseHint))
            .toEqual(["Leer = sofort verfügbar;", "Uhrzeit leer = 0:00 Uhr"]);
    });
});
