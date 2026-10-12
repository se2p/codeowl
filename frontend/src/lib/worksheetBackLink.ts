/**
 * The marketplace's way back from a worksheet.
 *
 * <p>Worksheets have several ways in — a teacher's own list, a class page, and now the
 * marketplace — so the nav row carries Home plus one "back to …" button per route the visitor
 * actually took. This supplies the marketplace one; the class page decides its own the same
 * way, and a worksheet opened from a bookmark offers neither.
 *
 * <p>A pure function rather than logic inside the page, so the rule can be tested without
 * mounting a router. The page renders what this returns and decides nothing.
 */
export interface BackLink {
    /** Router path to navigate to. */
    to: string;
    /** i18n key for the label. */
    labelKey: string;
}

/**
 * Navigation state a page sets when it links to a worksheet, so the worksheet knows where the
 * visitor came from. Anything else — a pasted link, a bookmark, a page that does not set it —
 * yields no back link, which is the honest answer: there is nowhere to go back to.
 */
export interface WorksheetNavState {
    from?: string;
    /** The marketplace's query string, so returning restores the filters rather than resetting them. */
    search?: string;
}

export const MARKETPLACE_PATH = "/library/worksheets";

/** The marketplace back link, or null when the visitor did not come from there. */
export function marketplaceBackLink(state: unknown): BackLink | null {
    const nav = (state ?? {}) as WorksheetNavState;

    if (nav.from !== "marketplace") {
        return null;
    }

    // The marketplace keeps its filters in the URL, so carrying the query string back returns
    // the teacher to the list they were reading, not to an unfiltered one.
    const search = typeof nav.search === "string" ? nav.search : "";
    return { to: MARKETPLACE_PATH + search, labelKey: "marketplace.backToMarketplace" };
}
