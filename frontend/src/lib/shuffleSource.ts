/**
 * Shuffles the blocks still sitting in the source area, leaving the solution
 * area exactly as the student built it.
 *
 * js-parsons' own shuffleLines() cannot be used for this: it calls
 * createHTMLFromLists([], allIds) (parsons.js:1765), which empties the solution
 * and returns every block to the source — fine for the initial deal, but as a
 * mid-solve button it throws away the student's work.
 *
 * Rebuilding just the source list would be wrong too. createHTMLFromLists
 * regenerates each <li> from the raw code via codeLineToHTML, which does not
 * re-emit the gap inputs (AP30) or the inline-split chips (AP31), and those are
 * wired up once when the task loads. So this moves the existing nodes instead:
 * appendChild relocates a node rather than copying it, and everything attached
 * to it — values typed into gaps, placed fragments, hint highlighting, event
 * handlers — comes along untouched.
 *
 * Reordering the DOM is enough to reorder the model, because the widget reads
 * block order back out of the DOM via sortable('toArray') (parsons.js:1645).
 *
 * @param container the <ul> holding the source blocks
 * @param rng       injectable for tests; defaults to Math.random
 * @returns how many blocks were reordered
 */
export function shuffleElementChildren(
    container: Element | null | undefined,
    rng: () => number = Math.random,
): number {
    if (!container) return 0;

    const items = Array.from(container.children).filter((el) => el.tagName === "LI");
    if (items.length < 2) return items.length;

    // Fisher-Yates over a copy, so the DOM is touched once at the end.
    for (let i = items.length - 1; i > 0; i--) {
        const j = Math.floor(rng() * (i + 1));
        [items[i], items[j]] = [items[j], items[i]];
    }

    const fragment = container.ownerDocument.createDocumentFragment();
    for (const item of items) fragment.appendChild(item);
    container.appendChild(fragment);

    return items.length;
}
