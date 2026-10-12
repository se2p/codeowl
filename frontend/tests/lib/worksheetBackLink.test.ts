import { describe, expect, it } from "vitest";
import { marketplaceBackLink, MARKETPLACE_PATH } from "../../src/lib/worksheetBackLink.ts";

/**
 * The marketplace's way back from a worksheet.
 *
 * <p>Written after a teacher reported that the nav row read as "back" — it carried a left
 * arrow — but went home. The row now holds Home plus one button per route actually taken; this
 * covers the marketplace entry, and returning null is as much of a result as returning a link.
 */
describe("marketplaceBackLink", () => {
    it("offers the marketplace to a visitor who came from it", () => {
        const link = marketplaceBackLink({ from: "marketplace", search: "" });

        expect(link).toEqual({ to: MARKETPLACE_PATH, labelKey: "marketplace.backToMarketplace" });
    });

    it("carries the filters back, so the list is the one the teacher was reading", () => {
        // The marketplace keeps its state in the URL. Dropping the query string here would
        // silently reset a search and three filters every time somebody opened a worksheet.
        const link = marketplaceBackLink({ from: "marketplace", search: "?q=rekursion&lang=java&page=2" });

        expect(link?.to).toBe(`${MARKETPLACE_PATH}?q=rekursion&lang=java&page=2`);
    });

    it("offers nothing when there is nowhere to go back to", () => {
        // A pasted link or a bookmark. Claiming "back" here would be a lie about history, and
        // the page then shows Home alone.
        expect(marketplaceBackLink(undefined)).toBeNull();
        expect(marketplaceBackLink(null)).toBeNull();
        expect(marketplaceBackLink({})).toBeNull();
    });

    it("ignores navigation state from somewhere else", () => {
        // The class page sets its own marker and renders its own button; this must not claim
        // that visitor as the marketplace's.
        expect(marketplaceBackLink({ fromClass: "c-1" })).toBeNull();
        expect(marketplaceBackLink({ from: "library" })).toBeNull();
        expect(marketplaceBackLink({ justCopied: true })).toBeNull();
    });

    it("survives a search value that is not a string", () => {
        // State crosses a router boundary and is not validated on the way in.
        expect(marketplaceBackLink({ from: "marketplace", search: 42 })?.to).toBe(MARKETPLACE_PATH);
    });
});
