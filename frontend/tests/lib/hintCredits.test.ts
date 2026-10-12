import { describe, it, expect } from "vitest";

/**
 * The affordability rules SolvePage uses to enable or disable the two hint
 * buttons. Mirrors HintService.creditCost on the backend: level 2 asks the LLM
 * for an explanation on top of the deterministic finding, so it costs double.
 *
 * The interesting case is not zero — it is one credit left, where the cheap
 * hint is still affordable and the expensive one is not.
 */
const hintCost = (level: 1 | 2) => (level === 2 ? 2 : 1);
const canAfford = (remaining: number | null, level: 1 | 2) =>
    remaining === null || remaining >= hintCost(level);

describe("hint credit affordability", () => {
    it("charges level 2 twice as much as level 1", () => {
        expect(hintCost(1)).toBe(1);
        expect(hintCost(2)).toBe(2);
    });

    it("allows everything when the worksheet grants unlimited hints", () => {
        expect(canAfford(null, 1)).toBe(true);
        expect(canAfford(null, 2)).toBe(true);
    });

    it("blocks both buttons at zero", () => {
        expect(canAfford(0, 1)).toBe(false);
        expect(canAfford(0, 2)).toBe(false);
    });

    it("allows the cheap hint but not the explanation at exactly one credit", () => {
        expect(canAfford(1, 1)).toBe(true);
        expect(canAfford(1, 2)).toBe(false);
    });

    it("allows both from two credits upwards", () => {
        expect(canAfford(2, 1)).toBe(true);
        expect(canAfford(2, 2)).toBe(true);
        expect(canAfford(5, 2)).toBe(true);
    });

    it("never allows spending from a negative balance", () => {
        // Should not occur — the backend clamps remaining at 0 — but a stale
        // client must not be talked into enabling a button by bad data.
        expect(canAfford(-1, 1)).toBe(false);
        expect(canAfford(-3, 2)).toBe(false);
    });
});
