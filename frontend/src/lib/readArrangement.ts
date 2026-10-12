/**
 * Reads the student's current arrangement out of the solution area.
 *
 * The mirror of {@link restoreSubmission}: that one puts a saved arrangement back into the
 * DOM, this one takes the live arrangement out of it. Both speak the same two lists — the
 * visible text of each block top to bottom, and the indent level of each — so what is read
 * here can be handed straight back to restore later.
 *
 * Indentation comes from `margin-left` rather than the widget's own `line.indent`, because
 * that is what every existing caller already did and it does not require the widget object.
 * `x_indent` is the pixels-per-level the widget was configured with (50 by default).
 *
 * Note the solution area only. The source area's ordering is not part of a saved arrangement:
 * restoring leaves whatever was not placed sitting in the source, and the initial deal is
 * unseeded anyway, so there would be nothing stable to compare a saved source order against.
 */
export interface Arrangement {
    /** Visible text of each block in the solution area, top to bottom. */
    blockOrder: string[];
    /** Indent level of each block, positionally aligned with {@link blockOrder}. */
    indentLevels: number[];
}

export function readArrangement(
    targetUl: Element | null | undefined,
    xIndent = 50,
): Arrangement {
    if (!targetUl) return { blockOrder: [], indentLevels: [] };

    // A zero or negative x_indent would make every level infinite or negative; fall back
    // rather than emit NaN into something a researcher will later try to read.
    const perLevel = xIndent > 0 ? xIndent : 50;

    const blocks = Array.from(targetUl.querySelectorAll("li"));
    return {
        blockOrder: blocks.map((li) => li.textContent?.trim() ?? ""),
        indentLevels: blocks.map((li) => {
            const ml = parseFloat((li as HTMLElement).style.marginLeft) || 0;
            return Math.max(0, Math.round(ml / perLevel));
        }),
    };
}
