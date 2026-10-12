import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import WorksheetCard from "../../src/components/WorksheetCard.tsx";
import type { MarketplaceItem } from "../../src/types/models";

vi.mock("react-i18next", () => ({
    useTranslation: () => ({
        // Echo the key, with the count appended where the real bundle interpolates one, so the
        // assertions do not depend on German copy a translator may reword.
        t: (key: string, options?: Record<string, unknown>) =>
            options && "count" in options ? `${key}:${options.count}` : key,
    }),
}));

const base: MarketplaceItem = {
    worksheetId: "11111111-1111-1111-1111-111111111111",
    name: "Rekursion",
    description: "Drei Sequenzen zur Rekursion",
    ownerId: "22222222-2222-2222-2222-222222222222",
    createdAt: "2026-09-01T10:00:00Z",
    sequenceCount: 3,
    languages: ["java", "python"],
    copyCount: 0,
    alreadyCopied: false,
    highlight: null,
};

function renderCard(item: Partial<MarketplaceItem> = {}, props: Partial<{
    canCopy: boolean;
    onCopy: (item: MarketplaceItem) => void;
    copying: boolean;
}> = {}) {
    const onCopy = props.onCopy ?? vi.fn();
    render(
        <MemoryRouter>
            <WorksheetCard
                item={{ ...base, ...item }}
                canCopy={props.canCopy ?? true}
                onCopy={onCopy}
                copying={props.copying ?? false}
            />
        </MemoryRouter>,
    );
    return onCopy;
}

describe("WorksheetCard", () => {
    it("leads with what a teacher judges a worksheet by", () => {
        // The name is often "Blatt 3" and says nothing; the contents are the useful part.
        renderCard();

        expect(screen.getByText("Rekursion")).toBeInTheDocument();
        expect(screen.getByText("java")).toBeInTheDocument();
        expect(screen.getByText("python")).toBeInTheDocument();
        expect(screen.getByText("marketplace.sequenceCount:3")).toBeInTheDocument();
    });

    it("links to the worksheet itself", () => {
        renderCard();
        expect(screen.getByRole("link", { name: /marketplace\.open/ }))
            .toHaveAttribute("href", `/worksheets/${base.worksheetId}`);
    });

    it("falls back to a placeholder when a worksheet has no name", () => {
        renderCard({ name: "" });
        expect(screen.getByText("marketplace.untitled")).toBeInTheDocument();
    });

    describe("the copy count", () => {
        it("is hidden when nobody has copied it", () => {
            renderCard({ copyCount: 0 });
            expect(screen.queryByTitle("marketplace.copyCountTooltip")).not.toBeInTheDocument();
        });

        it("is shown once somebody has", () => {
            renderCard({ copyCount: 4 });
            expect(screen.getByTitle("marketplace.copyCountTooltip")).toHaveTextContent("4");
        });
    });

    describe("already copied", () => {
        it("is marked, so nobody makes a second copy by accident", () => {
            renderCard({ alreadyCopied: true });
            expect(screen.getByText("marketplace.alreadyCopied")).toBeInTheDocument();
        });

        it("still allows a deliberate second copy, and says so", () => {
            renderCard({ alreadyCopied: true });
            expect(screen.getByRole("button")).toHaveAttribute(
                "title", "marketplace.copyAgainTooltip");
        });

        it("is absent otherwise", () => {
            renderCard({ alreadyCopied: false });
            expect(screen.queryByText("marketplace.alreadyCopied")).not.toBeInTheDocument();
        });
    });

    describe("the copy button", () => {
        it("reports the item it belongs to", () => {
            const onCopy = renderCard();
            fireEvent.click(screen.getByRole("button"));
            expect(onCopy).toHaveBeenCalledWith(expect.objectContaining({ worksheetId: base.worksheetId }));
        });

        it("is absent for viewers who may not copy", () => {
            // Students and anonymous visitors can read a published worksheet but not take one.
            renderCard({}, { canCopy: false });
            expect(screen.queryByRole("button")).not.toBeInTheDocument();
            expect(screen.getByRole("link", { name: /marketplace\.open/ })).toBeInTheDocument();
        });

        it("is disabled while its own copy is in flight", () => {
            renderCard({}, { copying: true });
            expect(screen.getByRole("button")).toBeDisabled();
            expect(screen.getByText("marketplace.copying")).toBeInTheDocument();
        });
    });

    describe("search highlighting", () => {
        it("renders the marked-up snippet when there was a query", () => {
            renderCard({ highlight: "Drei Sequenzen zur Rekursion" });
            expect(screen.getByText("Rekursion", { selector: "mark" })).toBeInTheDocument();
        });

        it("escapes markup in the snippet rather than rendering it", () => {
            // The snippet is built from text a teacher typed, so it must never reach the DOM
            // as markup.
            renderCard({ highlight: "<img src=x onerror=alert(1)> Rekursion" });
            expect(document.querySelector("img")).toBeNull();
            expect(screen.getByText(/<img src=x/)).toBeInTheDocument();
        });

        it("shows the plain description when there was no query", () => {
            renderCard({ highlight: null });
            expect(screen.getByText("Drei Sequenzen zur Rekursion")).toBeInTheDocument();
            expect(document.querySelector("mark")).toBeNull();
        });

        it("shows nothing rather than an empty paragraph when there is neither", () => {
            renderCard({ highlight: null, description: null });
            expect(screen.queryByText("Drei Sequenzen zur Rekursion")).not.toBeInTheDocument();
        });
    });
});
