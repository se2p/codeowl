import type { LibraryItem } from "../types/models.ts";

/**
 * The worksheet page's add-sequence search dropdown: same Library search results, filtered and
 * reordered for that one picker rather than via a Library-wide sort mode.
 *
 * Kept apart from WorksheetPage.tsx so the filtering/ordering/title rules can be tested directly,
 * without mounting the whole page (auth context, routing, API calls and all).
 */

/**
 * Filters out sequences already on the worksheet, then sorts the current user's own sequences
 * first — stable otherwise, so ties keep the order the search returned them in (whatever the
 * caller's sort/relevance choice was).
 *
 * A teacher adding a sequence to their own worksheet is almost always looking for one of their
 * own; this is a picker-local reorder rather than a Library sort mode because that ranking only
 * makes sense here, not on the Library page's general search.
 */
export function prepareSequenceOptions(
    items: LibraryItem[],
    existingSequenceIds: Iterable<string>,
    currentUserId: string | null | undefined
): LibraryItem[] {
    const existing = new Set(existingSequenceIds);
    const candidates = items.filter((it) => !existing.has(it.sequenceId));

    const isMine = (it: LibraryItem) => currentUserId != null && it.ownerId === currentUserId;
    const mine = candidates.filter(isMine);
    const others = candidates.filter((it) => !isMine(it));
    return [...mine, ...others];
}

/**
 * Same title priority as the Library card (see LibraryPage.tsx): the name the owner gave the
 * sequence, then a teacher-written task description, the LLM-generated one-liner only as a
 * fallback for sequences that have neither, then the caller's own fallback text.
 */
export function sequenceOptionTitle(
    item: Pick<LibraryItem, "title" | "taskDescription" | "semanticSummary">,
    fallback: string
): string {
    return item.title?.trim() || item.taskDescription?.trim() || item.semanticSummary?.trim() || fallback;
}
