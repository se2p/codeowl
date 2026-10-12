import { describe, it, expect } from "vitest";
import { blockLabel } from "../../src/lib/blockLabel.ts";

/**
 * Must match BlockLabels.of on the backend: the badge on a highlighted block
 * and the hint text naming that block come from opposite sides of the wire, and
 * a disagreement sends the student to the wrong block.
 *
 * The badge used to be labelled by position in the findings list, so a lone
 * affected block always read "A" while the hint named its real position.
 */
describe("blockLabel", () => {
    it("labels the first blocks A, B, C", () => {
        expect(blockLabel(0)).toBe("A");
        expect(blockLabel(1)).toBe("B");
        expect(blockLabel(2)).toBe("C");
    });

    it("continues like spreadsheet columns past Z", () => {
        expect(blockLabel(25)).toBe("Z");
        expect(blockLabel(26)).toBe("AA");
        expect(blockLabel(27)).toBe("AB");
        expect(blockLabel(51)).toBe("AZ");
        expect(blockLabel(52)).toBe("BA");
        expect(blockLabel(701)).toBe("ZZ");
        expect(blockLabel(702)).toBe("AAA");
    });

    it("agrees with the backend on every index it will realistically meet", () => {
        // Same sequence the Java side asserts, so a change on one side without
        // the other shows up here.
        const expected: Record<number, string> = {
            0: "A", 1: "B", 25: "Z", 26: "AA", 27: "AB",
            51: "AZ", 52: "BA", 701: "ZZ", 702: "AAA",
        };
        for (const [index, label] of Object.entries(expected)) {
            expect(blockLabel(Number(index))).toBe(label);
        }
    });

    it("never gives two blocks the same label", () => {
        const seen = new Set<string>();
        for (let i = 0; i < 1000; i++) {
            const label = blockLabel(i);
            expect(seen.has(label)).toBe(false);
            seen.add(label);
        }
    });

    it("returns a placeholder for nonsense rather than throwing", () => {
        // A hint is worth more than an exception in the highlighting code.
        expect(blockLabel(-1)).toBe("?");
        expect(blockLabel(1.5)).toBe("?");
        expect(blockLabel(NaN)).toBe("?");
    });
});
