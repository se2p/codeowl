import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { useMarketplaceFilters } from "../../src/hooks/useMarketplaceFilters";

/**
 * A harness that shows the hook's state and the URL side by side, so a test can assert that
 * they agree. The point of putting filters in the URL is that the two never diverge — a page
 * showing Java results under a URL that says Python is the bug this guards against.
 */
function Harness() {
    const filters = useMarketplaceFilters();
    const location = useLocation();
    return (
        <div>
            <span data-testid="state">
                {JSON.stringify({
                    q: filters.q,
                    lang: filters.lang,
                    owner: filters.owner,
                    sort: filters.sort,
                    page: filters.page,
                })}
            </span>
            <span data-testid="search">{location.search}</span>
            <button onClick={() => filters.setLang("java")}>lang</button>
            <button onClick={() => filters.setQ("rekursion")}>query</button>
            <button onClick={() => filters.setPage(3)}>page</button>
            <button onClick={() => filters.setOwner("me")}>mine</button>
            <button onClick={() => filters.reset()}>reset</button>
        </div>
    );
}

function renderAt(url: string) {
    render(
        <MemoryRouter initialEntries={[url]}>
            <Routes>
                <Route path="/library/worksheets" element={<Harness />} />
            </Routes>
        </MemoryRouter>,
    );
}

const state = () => JSON.parse(screen.getByTestId("state").textContent ?? "{}");
const search = () => screen.getByTestId("search").textContent ?? "";

describe("useMarketplaceFilters", () => {
    it("takes its initial state from the URL, so a shared link opens the view it describes", () => {
        renderAt("/library/worksheets?q=arrays&lang=python&owner=me&sort=popularity&page=2");

        expect(state()).toEqual({
            q: "arrays",
            lang: "python",
            owner: "me",
            sort: "popularity",
            page: 2,
        });
    });

    it("writes a change back into the URL", () => {
        renderAt("/library/worksheets");
        fireEvent.click(screen.getByText("lang"));

        expect(state().lang).toBe("java");
        expect(search()).toContain("lang=java");
    });

    it("returns to the first page when a filter changes", () => {
        // Staying on page four of a result set that has just been replaced shows an empty
        // screen and reads as a broken search.
        renderAt("/library/worksheets?page=4");
        expect(state().page).toBe(4);

        fireEvent.click(screen.getByText("query"));

        expect(state().page).toBe(0);
        expect(search()).not.toContain("page=");
    });

    it("keeps the page when only the page changes", () => {
        renderAt("/library/worksheets?lang=java");
        fireEvent.click(screen.getByText("page"));

        expect(state()).toMatchObject({ page: 3, lang: "java" });
        expect(search()).toContain("page=3");
    });

    it("keeps the URL free of defaults", () => {
        renderAt("/library/worksheets");
        fireEvent.click(screen.getByText("mine"));

        expect(search()).toBe("?owner=me");
    });

    it("reset clears the query string entirely", () => {
        renderAt("/library/worksheets?q=arrays&lang=java&owner=me&page=2");
        fireEvent.click(screen.getByText("reset"));

        expect(search()).toBe("");
        expect(state()).toEqual({ q: "", lang: "", owner: "any", sort: "recent", page: 0 });
    });
});
