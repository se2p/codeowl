import { blockLabel } from "./blockLabel.ts";

/**
 * Marks the blocks a hint is about, badging each with the same letter the hint
 * text uses for it.
 *
 * The label comes from the block's own position in the solution area. It used
 * to come from the block's position in the findings list, so a hint about the
 * fifth block badged it "A" while the text named it differently — the student
 * had to guess which block was meant.
 *
 * @param container            the <ul> holding the solution blocks
 * @param affectedBlockIndices zero-based positions, as returned by the backend
 * @returns how many blocks were marked
 */
export function applyHintLabels(
    container: Element | null | undefined,
    affectedBlockIndices: readonly number[] | null | undefined,
): number {
    if (!container || !affectedBlockIndices) return 0;

    const blocks = container.querySelectorAll("li");
    let marked = 0;

    for (const blockIdx of affectedBlockIndices) {
        if (!Number.isInteger(blockIdx) || blockIdx < 0 || blockIdx >= blocks.length) continue;
        const block = blocks[blockIdx];
        block.classList.add("block-hint-error");
        block.setAttribute("data-hint-label", blockLabel(blockIdx));
        marked++;
    }

    return marked;
}
