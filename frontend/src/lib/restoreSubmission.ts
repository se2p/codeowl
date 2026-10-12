/**
 * Puts a previously solved arrangement back into the solution area.
 *
 * The widget deals every block into the source area on init, so restoring means
 * moving the right blocks across, in the saved order, and reapplying their
 * indents. Nodes are moved rather than recreated — appendChild relocates — so
 * gap inputs and split chips survive, and the widget still reads block order
 * back out of the DOM (parsons.js sortable('toArray')).
 *
 * Blocks are matched by their visible text, because that is all the saved
 * arrangement records. Where a task repeats a line, matches are consumed in
 * order, so identical lines fill the saved positions left to right.
 */
export interface RestoredBlock {
    /** The widget's id for the block, so the caller can find its line object. */
    id: string;
    /** The indent level the block was restored to. */
    indent: number;
}

export interface RestoreResult {
    /** How many saved blocks were placed. */
    restored: number;
    /** Saved blocks whose text no longer exists among the available blocks. */
    missing: number;
    /**
     * The blocks that were placed, in solution order.
     *
     * Restoring sets `margin-left`, which is only how indentation is *drawn*. The widget grades
     * from `line.indent` on its own model, which init left at 0, so a caller that does not copy
     * these levels across has a student whose restored solution grades as fully unindented
     * until they drag something. This helper cannot do it itself without taking a dependency on
     * the widget, so it hands back what the caller needs to.
     */
    placed: RestoredBlock[];
}

export function restoreSubmission(
    sourceUl: Element | null | undefined,
    targetUl: Element | null | undefined,
    blockOrder: readonly string[] | null | undefined,
    indentLevels: readonly number[] | null | undefined,
    xIndent = 50,
): RestoreResult {
    if (!sourceUl || !targetUl || !blockOrder || blockOrder.length === 0) {
        return { restored: 0, missing: 0, placed: [] };
    }

    // Index the available blocks by text. Both areas are searched: a restore may
    // run when some blocks already sit in the solution.
    const available = new Map<string, HTMLElement[]>();
    for (const ul of [sourceUl, targetUl]) {
        for (const li of Array.from(ul.children)) {
            if (li.tagName !== "LI") continue;
            const key = (li.textContent ?? "").trim();
            const bucket = available.get(key);
            if (bucket) bucket.push(li as HTMLElement);
            else available.set(key, [li as HTMLElement]);
        }
    }

    let restored = 0;
    let missing = 0;
    const placed: RestoredBlock[] = [];

    blockOrder.forEach((text, position) => {
        const bucket = available.get(text.trim());
        const block = bucket?.shift();
        if (!block) {
            // The task was edited since, or the text no longer matches. Skipping
            // is better than aborting: a partial restore still saves the student
            // most of their work.
            missing++;
            return;
        }
        targetUl.appendChild(block);
        const indent = indentLevels?.[position] ?? 0;
        block.style.marginLeft = `${indent * xIndent}px`;
        if (block.id) placed.push({ id: block.id, indent });
        restored++;
    });

    return { restored, missing, placed };
}
