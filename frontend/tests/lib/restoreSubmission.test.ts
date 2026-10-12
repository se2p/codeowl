import { describe, it, expect, beforeEach } from "vitest";
import { restoreSubmission } from "../../src/lib/restoreSubmission.ts";

/**
 * Reopening a solved task used to hand the student a reshuffled puzzle, losing
 * the work they had finished. Restoring moves the saved blocks back into the
 * solution area, in the saved order, with their indents.
 *
 * Nodes are moved rather than recreated, so gap inputs and split chips survive —
 * the same reason the shuffle button moves nodes instead of rebuilding them.
 */
function build(sourceTexts: string[], targetTexts: string[] = []) {
    document.body.innerHTML = `<ul id="src"></ul><ul id="tgt"></ul>`;
    const source = document.getElementById("src")!;
    const target = document.getElementById("tgt")!;
    sourceTexts.forEach((text, i) => {
        const li = document.createElement("li");
        li.id = "s" + i;
        li.textContent = text;
        source.appendChild(li);
    });
    targetTexts.forEach((text, i) => {
        const li = document.createElement("li");
        li.id = "t" + i;
        li.textContent = text;
        target.appendChild(li);
    });
    return { source, target };
}

const textsOf = (ul: Element) =>
    Array.from(ul.children).map((c) => (c.textContent ?? "").trim());

beforeEach(() => {
    document.body.innerHTML = "";
});

describe("restoreSubmission", () => {
    it("puts the saved blocks back in the saved order", () => {
        const { source, target } = build(["c = 3", "a = 1", "b = 2"]);

        const result = restoreSubmission(source, target, ["a = 1", "b = 2", "c = 3"], [0, 0, 0]);

        expect(result).toEqual({
            restored: 3,
            missing: 0,
            placed: [{ id: "s1", indent: 0 }, { id: "s2", indent: 0 }, { id: "s0", indent: 0 }],
        });
        expect(textsOf(target)).toEqual(["a = 1", "b = 2", "c = 3"]);
        expect(textsOf(source)).toEqual([]);
    });

    it("reapplies indentation", () => {
        const { source, target } = build(["if x:", "    y = 1"]);

        restoreSubmission(source, target, ["if x:", "    y = 1"], [0, 1], 50);

        expect((target.children[0] as HTMLElement).style.marginLeft).toBe("0px");
        expect((target.children[1] as HTMLElement).style.marginLeft).toBe("50px");
    });

    it("moves the existing nodes instead of recreating them", () => {
        // What keeps a typed-in gap answer or a placed split fragment alive.
        const { source, target } = build(["a = 1"]);
        const original = source.children[0];
        const input = document.createElement("input");
        input.value = "typed";
        original.appendChild(input);

        restoreSubmission(source, target, ["a = 1"], [0]);

        expect(target.children[0]).toBe(original);
        expect(target.querySelector("input")!.value).toBe("typed");
    });

    it("fills repeated lines left to right", () => {
        // Two identical lines are indistinguishable by text, so matches are
        // consumed in order rather than both resolving to the same block.
        const { source, target } = build(["x += 1", "x += 1", "y = 0"]);

        const result = restoreSubmission(source, target, ["x += 1", "y = 0", "x += 1"], [0, 0, 0]);

        expect(result.restored).toBe(3);
        expect(textsOf(target)).toEqual(["x += 1", "y = 0", "x += 1"]);
    });

    it("restores what it can when the task has changed since", () => {
        // A teacher may have edited the task. Half the work back beats none.
        const { source, target } = build(["a = 1", "b = 2"]);

        const result = restoreSubmission(source, target, ["a = 1", "gone", "b = 2"], [0, 0, 0]);

        expect(result).toEqual({
            restored: 2,
            missing: 1,
            placed: [{ id: "s0", indent: 0 }, { id: "s1", indent: 0 }],
        });
        expect(textsOf(target)).toEqual(["a = 1", "b = 2"]);
    });

    it("does nothing when there is nothing stored", () => {
        const { source, target } = build(["a = 1"]);

        expect(restoreSubmission(source, target, [], [])).toEqual({ restored: 0, missing: 0, placed: [] });
        expect(restoreSubmission(source, target, null, null)).toEqual({ restored: 0, missing: 0, placed: [] });
        expect(restoreSubmission(null, target, ["a = 1"], [0])).toEqual({ restored: 0, missing: 0, placed: [] });
        expect(textsOf(source)).toEqual(["a = 1"]);
    });

    it("tolerates a missing indent list", () => {
        const { source, target } = build(["a = 1"]);

        restoreSubmission(source, target, ["a = 1"], undefined);

        expect((target.children[0] as HTMLElement).style.marginLeft).toBe("0px");
    });

    /**
     * margin-left is only how indentation is drawn. The widget grades from line.indent on its
     * own model, which init left at 0, so a restored solution used to be graded as fully
     * unindented until the student dragged something -- a correct answer marked wrong. The
     * helper cannot reach the widget, so it reports what the caller must copy across.
     */
    it("reports each placed block with its indent, so the caller can sync the widget model", () => {
        const { source, target } = build(["if x:", "    y = 1", "z = 2"]);

        const result = restoreSubmission(source, target, ["if x:", "    y = 1"], [0, 1], 50);

        expect(result.placed).toEqual([
            { id: "s0", indent: 0 },
            { id: "s1", indent: 1 },
        ]);
        // Only what was actually placed -- the block left in the source area is not reported.
        expect(result.placed).toHaveLength(2);
    });

    it("reports indent 0 for placed blocks when no indents were saved", () => {
        const { source, target } = build(["a = 1"]);

        expect(restoreSubmission(source, target, ["a = 1"], undefined).placed)
            .toEqual([{ id: "s0", indent: 0 }]);
    });
});
