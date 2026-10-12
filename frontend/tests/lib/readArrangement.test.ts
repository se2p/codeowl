import { describe, it, expect, beforeEach } from "vitest";
import { readArrangement } from "../../src/lib/readArrangement.ts";
import { restoreSubmission } from "../../src/lib/restoreSubmission.ts";

/**
 * The read half of resuming a task.
 *
 * Autosave, the solved save and the hint request all used to inline their own copy of this,
 * which is how the three drifted apart in the first place. What matters here is that what it
 * reads out is exactly what restoreSubmission can put back: the two are each other's inverse,
 * and the round-trip test at the bottom is the one that would actually catch a drift.
 */
function target(blocks: Array<[string, number]>, xIndent = 50) {
    document.body.innerHTML = `<ul id="tgt"></ul>`;
    const ul = document.getElementById("tgt")!;
    blocks.forEach(([text, indent], i) => {
        const li = document.createElement("li");
        li.id = "t" + i;
        li.textContent = text;
        li.style.marginLeft = `${indent * xIndent}px`;
        ul.appendChild(li);
    });
    return ul;
}

beforeEach(() => {
    document.body.innerHTML = "";
});

describe("readArrangement", () => {
    it("reads the blocks top to bottom with their indent levels", () => {
        const ul = target([["def f():", 0], ["x = 1", 1], ["return x", 1]]);

        expect(readArrangement(ul)).toEqual({
            blockOrder: ["def f():", "x = 1", "return x"],
            indentLevels: [0, 1, 1],
        });
    });

    it("is empty for an empty solution area, rather than throwing", () => {
        expect(readArrangement(target([]))).toEqual({ blockOrder: [], indentLevels: [] });
    });

    it("is empty when the list is missing entirely", () => {
        // The widget may not have built its lists yet when an autosave timer fires.
        expect(readArrangement(null)).toEqual({ blockOrder: [], indentLevels: [] });
        expect(readArrangement(undefined)).toEqual({ blockOrder: [], indentLevels: [] });
    });

    it("honours a non-default x_indent", () => {
        const ul = target([["a", 0], ["b", 2]], 25);

        expect(readArrangement(ul, 25).indentLevels).toEqual([0, 2]);
    });

    it("treats a block with no margin as unindented", () => {
        document.body.innerHTML = `<ul id="tgt"><li>a = 1</li></ul>`;

        expect(readArrangement(document.getElementById("tgt"))).toEqual({
            blockOrder: ["a = 1"],
            indentLevels: [0],
        });
    });

    it("never reports a negative indent", () => {
        const ul = target([["a", 0]]);
        (ul.firstElementChild as HTMLElement).style.marginLeft = "-50px";

        expect(readArrangement(ul).indentLevels).toEqual([0]);
    });

    it("falls back to the default rather than emitting NaN for a zero x_indent", () => {
        const ul = target([["a", 1]]);

        expect(readArrangement(ul, 0).indentLevels).toEqual([1]);
    });

    it("round-trips through restoreSubmission", () => {
        // The property that matters: anything read out can be put back unchanged.
        const original = [["def f():", 0], ["x = 1", 1], ["return x", 1]] as Array<[string, number]>;
        const read = readArrangement(target(original));

        document.body.innerHTML = `<ul id="src"></ul><ul id="tgt2"></ul>`;
        const source = document.getElementById("src")!;
        const restoreTarget = document.getElementById("tgt2")!;
        // Deal the same blocks out shuffled, as a fresh mount would.
        ["return x", "def f():", "x = 1"].forEach((text, i) => {
            const li = document.createElement("li");
            li.id = "s" + i;
            li.textContent = text;
            source.appendChild(li);
        });

        restoreSubmission(source, restoreTarget, read.blockOrder, read.indentLevels);

        expect(readArrangement(restoreTarget)).toEqual(read);
    });
});
