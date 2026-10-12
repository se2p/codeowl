import { describe, it, expect } from "vitest";

/**
 * Which explanation a hint button shows when it is disabled.
 *
 * Mirrors hintTooltip in SolvePage. The ordering matters: running out of
 * credits is permanent for this task, whereas "click Feedback first" is a step
 * the student can still take. Reporting the transient blocker to someone with
 * no credits sends them off to do something that cannot help.
 */
const hintCost = (level: 1 | 2) => (level === 2 ? 2 : 1);

function hintTooltip(
    level: 1 | 2,
    { hintsUnlocked, creditsRemaining }: { hintsUnlocked: boolean; creditsRemaining: number | null },
): string {
    const canAfford = creditsRemaining === null || creditsRemaining >= hintCost(level);
    if (!canAfford) {
        return creditsRemaining === 0 ? "exhausted" : `insufficient:${hintCost(level)}/${creditsRemaining}`;
    }
    if (!hintsUnlocked) return "feedbackFirst";
    return "enabled";
}

describe("hint button tooltip", () => {
    it("explains the credit budget rather than the feedback step when empty", () => {
        // The case that matters: a fresh task open, so feedback has not been
        // clicked yet, and the budget is already spent.
        expect(hintTooltip(1, { hintsUnlocked: false, creditsRemaining: 0 })).toBe("exhausted");
        expect(hintTooltip(2, { hintsUnlocked: false, creditsRemaining: 0 })).toBe("exhausted");
    });

    it("asks for feedback first while credits remain", () => {
        expect(hintTooltip(1, { hintsUnlocked: false, creditsRemaining: 5 })).toBe("feedbackFirst");
        expect(hintTooltip(1, { hintsUnlocked: false, creditsRemaining: null })).toBe("feedbackFirst");
    });

    it("names the shortfall when some credits remain but not enough", () => {
        // One credit left: the cheap hint is affordable, the explanation is not.
        expect(hintTooltip(1, { hintsUnlocked: true, creditsRemaining: 1 })).toBe("enabled");
        expect(hintTooltip(2, { hintsUnlocked: true, creditsRemaining: 1 })).toBe("insufficient:2/1");
    });

    it("shows the normal description when the button is usable", () => {
        expect(hintTooltip(1, { hintsUnlocked: true, creditsRemaining: 5 })).toBe("enabled");
        expect(hintTooltip(2, { hintsUnlocked: true, creditsRemaining: null })).toBe("enabled");
    });
});
