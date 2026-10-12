import { describe, expect, it } from "vitest";
import {
    canOfferVisibilityControl,
    effectiveVisibility,
    LIBRARY_INBOX_SENTINEL,
    visibilityBadgeClass,
    visibilityHintKey,
    visibilityIcon,
    visibilityLabelKey,
    VISIBILITY_OPTIONS,
} from "../../src/lib/worksheetVisibility";
import type { WorksheetVisibility } from "../../src/types/models";

describe("effectiveVisibility", () => {
    it("takes the worksheet's own value when it has one", () => {
        expect(effectiveVisibility({ visibility: "PUBLIC" })).toBe("PUBLIC");
        expect(effectiveVisibility({ visibility: "PRIVATE" })).toBe("PRIVATE");
    });

    it("falls back to how worksheets always behaved when the server said nothing", () => {
        // Rows written before the column existed come back without a visibility. Treating that
        // as PRIVATE would make a teacher's existing worksheets look locked overnight.
        expect(effectiveVisibility({ visibility: null as unknown as WorksheetVisibility })).toBe("UNLISTED");
        expect(effectiveVisibility(undefined)).toBe("UNLISTED");
        expect(effectiveVisibility(null)).toBe("UNLISTED");
    });

    it("ignores a value it does not recognise", () => {
        expect(effectiveVisibility({ visibility: "EVERYONE" as WorksheetVisibility })).toBe("UNLISTED");
    });
});

describe("canOfferVisibilityControl", () => {
    const ordinary = { description: "Week 3, arrays" };

    it("is offered to owners and admins", () => {
        expect(canOfferVisibilityControl(ordinary, true)).toBe(true);
    });

    it("is withheld from everyone else", () => {
        expect(canOfferVisibilityControl(ordinary, false)).toBe(false);
    });

    it("is withheld for the Library Inbox, which the backend refuses to publish anyway", () => {
        expect(canOfferVisibilityControl(
            { description: `Quick-copied sequences. ${LIBRARY_INBOX_SENTINEL}` }, true)).toBe(false);
    });

    it("still recognises a renamed Library Inbox", () => {
        // The sentinel sits in the description precisely so a rename cannot hide it.
        expect(canOfferVisibilityControl(
            { description: `Meine Sammlung ${LIBRARY_INBOX_SENTINEL} nicht loeschen` }, true)).toBe(false);
    });

    it("copes with a missing description and a missing worksheet", () => {
        expect(canOfferVisibilityControl({ description: null as unknown as string }, true)).toBe(true);
        expect(canOfferVisibilityControl(null, true)).toBe(false);
    });
});

describe("the option list", () => {
    it("runs from least to most reach", () => {
        expect(VISIBILITY_OPTIONS).toEqual(["PRIVATE", "UNLISTED", "PUBLIC"]);
    });

    it("gives every option an icon, a badge class and both i18n keys", () => {
        for (const option of VISIBILITY_OPTIONS) {
            expect(visibilityIcon(option)).toMatch(/^bi-/);
            expect(visibilityBadgeClass(option)).toBeTruthy();
            expect(visibilityLabelKey(option)).toBe(`worksheet.visibility.${option}.label`);
            expect(visibilityHintKey(option)).toBe(`worksheet.visibility.${option}.hint`);
        }
    });

    it("marks only PUBLIC as the one worth noticing", () => {
        expect(visibilityBadgeClass("PUBLIC")).toContain("success");
        expect(visibilityBadgeClass("PRIVATE")).not.toContain("success");
        expect(visibilityBadgeClass("UNLISTED")).not.toContain("success");
    });
});
