import { describe, it, expect, beforeEach } from "vitest";
import { applyHintLabels } from "../../src/lib/hintHighlight.ts";

/**
 * The badge on a highlighted block has to carry the same letter the hint text
 * uses for that block.
 *
 * It used to be labelled by the block's position in the *findings* list, so the
 * first affected block always read "A" no matter where it sat, while the hint
 * named its actual position. That is the regression the first test pins.
 */
function build(count: number) {
    document.body.innerHTML = `<ul id="sol"></ul>`;
    const ul = document.getElementById("sol")!;
    for (let i = 0; i < count; i++) {
        const li = document.createElement("li");
        li.id = "b" + i;
        ul.appendChild(li);
    }
    return ul;
}

const labelOf = (ul: Element, i: number) =>
    ul.children[i].getAttribute("data-hint-label");

beforeEach(() => {
    document.body.innerHTML = "";
});

describe("applyHintLabels", () => {
    it("labels by block position, not by findings order", () => {
        const ul = build(6);

        applyHintLabels(ul, [4]);

        // The fifth block is "E". Labelling by findings order gave "A" here.
        expect(labelOf(ul, 4)).toBe("E");
        expect(labelOf(ul, 0)).toBeNull();
    });

    it("keeps labels stable however the findings are ordered", () => {
        const ul = build(6);

        applyHintLabels(ul, [3, 1]);

        // Position decides the letter, so the order of the list cannot change it.
        expect(labelOf(ul, 1)).toBe("B");
        expect(labelOf(ul, 3)).toBe("D");
    });

    it("marks each affected block for the CSS to style", () => {
        const ul = build(4);

        const marked = applyHintLabels(ul, [0, 2]);

        expect(marked).toBe(2);
        expect(ul.children[0].classList.contains("block-hint-error")).toBe(true);
        expect(ul.children[2].classList.contains("block-hint-error")).toBe(true);
        expect(ul.children[1].classList.contains("block-hint-error")).toBe(false);
    });

    it("ignores indices outside the solution area", () => {
        // The student can move a block out between asking and the reply arriving.
        const ul = build(3);

        const marked = applyHintLabels(ul, [-1, 0, 99, 1.5]);

        expect(marked).toBe(1);
        expect(labelOf(ul, 0)).toBe("A");
    });

    it("does nothing without a container or indices", () => {
        expect(applyHintLabels(null, [0])).toBe(0);
        expect(applyHintLabels(build(2), null)).toBe(0);
        expect(applyHintLabels(build(2), [])).toBe(0);
    });
});
