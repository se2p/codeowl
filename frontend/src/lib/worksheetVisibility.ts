import type { Worksheet, WorksheetVisibility } from "../types/models";

/**
 * The visibility choices, in the order they are offered.
 *
 * <p>Ascending reach, deliberately: a teacher reading down the list goes from "only me" to
 * "everyone", so the consequence of moving further down is obvious without reading the
 * descriptions.
 */
export const VISIBILITY_OPTIONS: readonly WorksheetVisibility[] = [
    "PRIVATE",
    "UNLISTED",
    "PUBLIC",
] as const;

/** Bootstrap icon for each state, so the badge and the picker cannot drift apart. */
const ICONS: Record<WorksheetVisibility, string> = {
    PRIVATE: "bi-lock",
    UNLISTED: "bi-link-45deg",
    PUBLIC: "bi-globe2",
};

/**
 * Badge styling per state. Only PUBLIC is coloured: it is the one that puts the worksheet in
 * front of other people, so it is the one worth noticing at a glance.
 */
const BADGE_CLASSES: Record<WorksheetVisibility, string> = {
    PRIVATE: "text-bg-light text-muted",
    UNLISTED: "text-bg-light text-muted",
    PUBLIC: "text-bg-success-subtle text-success-emphasis",
};

export function visibilityIcon(visibility: WorksheetVisibility): string {
    return ICONS[visibility] ?? ICONS.UNLISTED;
}

export function visibilityBadgeClass(visibility: WorksheetVisibility): string {
    return BADGE_CLASSES[visibility] ?? BADGE_CLASSES.UNLISTED;
}

/** i18n key for a state's short label, e.g. `worksheet.visibility.PUBLIC.label`. */
export function visibilityLabelKey(visibility: WorksheetVisibility): string {
    return `worksheet.visibility.${visibility}.label`;
}

/** i18n key for the sentence explaining what the state means. */
export function visibilityHintKey(visibility: WorksheetVisibility): string {
    return `worksheet.visibility.${visibility}.hint`;
}

/**
 * What a worksheet's visibility is, given the server may not have said.
 *
 * Rows written before the column existed come back without one, and they behave the way they
 * always did rather than becoming invisible.
 */
export function effectiveVisibility(worksheet: Pick<Worksheet, "visibility"> | null | undefined): WorksheetVisibility {
    const value = worksheet?.visibility;
    return value && VISIBILITY_OPTIONS.includes(value) ? value : "UNLISTED";
}

/**
 * Whether the visibility control should be offered at all.
 *
 * The Library Inbox is excluded because publishing a pile of one-click clippings is not
 * something anyone means to do; the backend refuses it too, and this only keeps the UI from
 * offering a button that would fail. The sentinel is matched in the description rather than the
 * name, so renaming the inbox does not sneak it past.
 */
export const LIBRARY_INBOX_SENTINEL = "[codeowl:library-inbox]";

export function canOfferVisibilityControl(
    worksheet: Pick<Worksheet, "description"> | null | undefined,
    isOwnerOrAdmin: boolean,
): boolean {
    if (!worksheet || !isOwnerOrAdmin) return false;
    return !(worksheet.description ?? "").includes(LIBRARY_INBOX_SENTINEL);
}
