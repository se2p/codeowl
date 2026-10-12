import { describe, it, expect, beforeEach } from "vitest";
import { shuffleElementChildren } from "../../src/lib/shuffleSource.ts";

/**
 * The shuffle button used to call js-parsons' shuffleLines(), which empties the
 * solution area and deals every block back to the source — destroying whatever
 * the student had already built.
 *
 * The two properties that matter here are that the solution list is not touched
 * at all, and that source blocks keep their identity: they are moved, not
 * recreated, so typed-in gap values and placed split fragments survive.
 */
function build(sourceIds: string[], solutionIds: string[]) {
    document.body.innerHTML = `
        <div id="jsparsons-source"><ul id="ul-jsparsons-source"></ul></div>
        <div id="jsparsons-target"><ul id="ul-jsparsons-target"></ul></div>
    `;
    const source = document.getElementById("ul-jsparsons-source")!;
    const solution = document.getElementById("ul-jsparsons-target")!;
    for (const id of sourceIds) {
        const li = document.createElement("li");
        li.id = id;
        source.appendChild(li);
    }
    for (const id of solutionIds) {
        const li = document.createElement("li");
        li.id = id;
        solution.appendChild(li);
    }
    return { source, solution };
}

const idsOf = (ul: Element) => Array.from(ul.children).map((c) => c.id);

/** Deterministic rng cycling through fixed values, so order is reproducible. */
const seeded = (values: number[]) => {
    let i = 0;
    return () => values[i++ % values.length];
};

beforeEach(() => {
    document.body.innerHTML = "";
});

describe("shuffleElementChildren", () => {
    it("leaves the solution area completely untouched", () => {
        const { source, solution } = build(["s1", "s2", "s3"], ["a", "b", "c"]);
        const before = Array.from(solution.children);

        shuffleElementChildren(source, seeded([0, 0.9, 0.4]));

        expect(idsOf(solution)).toEqual(["a", "b", "c"]);
        // Same nodes, not replacements.
        Array.from(solution.children).forEach((node, i) => {
            expect(node).toBe(before[i]);
        });
    });

    it("moves source nodes rather than recreating them", () => {
        // This is what keeps gap inputs and split chips alive: a rebuild would
        // regenerate the <li> from raw code and lose them.
        const { source } = build(["s1", "s2", "s3"], []);
        const input = document.createElement("input");
        input.value = "typed by the student";
        source.querySelector("#s2")!.appendChild(input);
        const originals = new Map(Array.from(source.children).map((c) => [c.id, c]));

        shuffleElementChildren(source, seeded([0, 0.9, 0.4]));

        Array.from(source.children).forEach((node) => {
            expect(node).toBe(originals.get(node.id));
        });
        expect(source.querySelector<HTMLInputElement>("#s2 input")!.value)
            .toBe("typed by the student");
    });

    it("keeps exactly the same blocks, only in another order", () => {
        const { source } = build(["s1", "s2", "s3", "s4"], []);

        shuffleElementChildren(source, seeded([0.99, 0.1, 0.7]));

        expect(idsOf(source).sort()).toEqual(["s1", "s2", "s3", "s4"]);
        expect(source.children.length).toBe(4);
    });

    it("does reorder for at least one seed", () => {
        const { source } = build(["s1", "s2", "s3", "s4", "s5"], []);
        shuffleElementChildren(source, seeded([0, 0, 0, 0]));
        expect(idsOf(source)).not.toEqual(["s1", "s2", "s3", "s4", "s5"]);
    });

    it("reports how many blocks it handled", () => {
        const { source } = build(["s1", "s2", "s3"], []);
        expect(shuffleElementChildren(source, seeded([0.5]))).toBe(3);
    });

    it("does nothing when there is nothing to shuffle", () => {
        const { source } = build([], []);
        expect(shuffleElementChildren(source)).toBe(0);

        const one = build(["only"], []).source;
        expect(shuffleElementChildren(one)).toBe(1);
        expect(idsOf(one)).toEqual(["only"]);
    });

    it("survives a missing container", () => {
        // The button can be clicked before the widget has rendered.
        expect(shuffleElementChildren(null)).toBe(0);
        expect(shuffleElementChildren(undefined)).toBe(0);
    });

    it("ignores non-block children such as a label paragraph", () => {
        const { source } = build(["s1", "s2"], []);
        const p = document.createElement("p");
        p.textContent = "label";
        source.insertBefore(p, source.firstChild);

        expect(shuffleElementChildren(source, seeded([0.5]))).toBe(2);
        // The <p> is left where it was; only the <li>s moved to the end.
        expect(source.firstElementChild!.tagName).toBe("P");
    });
});
