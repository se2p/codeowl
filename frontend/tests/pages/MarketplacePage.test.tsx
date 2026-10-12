import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { MarketplaceItem, MarketplaceSearchResponse } from "../../src/types/models";

const apiGet = vi.fn();
const apiPost = vi.fn();
const navigate = vi.fn();

vi.mock("../../src/lib/api.ts", () => ({
    api: { get: (...args: unknown[]) => apiGet(...args), post: (...args: unknown[]) => apiPost(...args) },
    // The fallback is stubbed as "always used", which is a simplification the real helper does
    // not make -- see tests/lib/api.test.ts. isNotFound is the real one, because whether a 404
    // is recognised is exactly what these tests are about.
    apiErrorMessage: (_e: unknown, fallback: string) => fallback,
    isNotFound: (err: unknown) =>
        (err as { response?: { status?: number } } | undefined)?.response?.status === 404,
}));

vi.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key: string, options?: Record<string, unknown>) =>
            options && "count" in options ? `${key}:${options.count}` : key,
    }),
}));

vi.mock("../../src/hooks/usePageTitle.ts", () => ({ usePageTitle: () => undefined }));

type Auth = {
    isLoggedIn: boolean; isTeacher: boolean; isAdmin: boolean;
    user: { userId: string } | null;
};
let auth: Auth = { isLoggedIn: true, isTeacher: true, isAdmin: false, user: null };
vi.mock("../../src/context/AuthContext.tsx", () => ({ useAuth: () => auth }));

vi.mock("react-router-dom", async () => {
    const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
    return { ...actual, useNavigate: () => navigate };
});

const { default: MarketplacePage } = await import("../../src/pages/MarketplacePage.tsx");

function item(overrides: Partial<MarketplaceItem> = {}): MarketplaceItem {
    return {
        worksheetId: "11111111-1111-1111-1111-111111111111",
        name: "Rekursion",
        description: "",
        ownerId: "22222222-2222-2222-2222-222222222222",
        createdAt: "2026-09-01T10:00:00Z",
        sequenceCount: 2,
        languages: ["java"],
        copyCount: 0,
        alreadyCopied: false,
        highlight: null,
        ...overrides,
    };
}

function response(overrides: Partial<MarketplaceSearchResponse> = {}): MarketplaceSearchResponse {
    return {
        items: [item()],
        page: 0,
        size: 20,
        total: 1,
        facets: { languages: [{ name: "java", count: 1 }], mine: 0, others: 1 },
        ...overrides,
    };
}

function renderPage(url = "/library/worksheets") {
    render(
        <MemoryRouter initialEntries={[url]}>
            <Routes>
                <Route path="/library/worksheets" element={<MarketplacePage />} />
            </Routes>
        </MemoryRouter>,
    );
}

/** The parameters of the most recent search request. */
const lastRequest = () => apiGet.mock.calls.at(-1)?.[1]?.params as Record<string, unknown>;

describe("MarketplacePage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        auth = { isLoggedIn: true, isTeacher: true, isAdmin: false, user: null };
        apiGet.mockResolvedValue({ data: response() });
    });

    describe("loading and failure", () => {
        it("says it is loading before anything arrives", () => {
            apiGet.mockReturnValue(new Promise(() => {}));
            renderPage();
            expect(screen.getByText("marketplace.loading")).toBeInTheDocument();
        });

        it("reports a failed request instead of showing an empty marketplace", async () => {
            // An empty grid would read as "nobody has published anything", which is a very
            // different message from "the request failed".
            apiGet.mockRejectedValue(new Error("network"));
            renderPage();

            await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("marketplace.errorLoading"));
            expect(screen.queryByText("marketplace.emptyTitle")).not.toBeInTheDocument();
        });
    });

    describe("the empty state", () => {
        it("invites the first teacher to publish when nothing is filtered", async () => {
            apiGet.mockResolvedValue({ data: response({ items: [], total: 0 }) });
            renderPage();

            await waitFor(() => expect(screen.getByText("marketplace.emptyTitle")).toBeInTheDocument());
            expect(screen.getByText("marketplace.emptyNothingPublished")).toBeInTheDocument();
        });

        it("suggests loosening the filters when some are set", async () => {
            apiGet.mockResolvedValue({ data: response({ items: [], total: 0 }) });
            renderPage("/library/worksheets?q=nichts");

            await waitFor(() => expect(screen.getByText("marketplace.emptyFiltered")).toBeInTheDocument());
        });
    });

    describe("results", () => {
        it("renders a card per worksheet and says how many there are", async () => {
            apiGet.mockResolvedValue({
                data: response({
                    items: [item({ name: "A" }), item({ worksheetId: "b", name: "B" })],
                    total: 2,
                }),
            });
            renderPage();

            await waitFor(() => expect(screen.getByText("A")).toBeInTheDocument());
            expect(screen.getByText("B")).toBeInTheDocument();
            expect(screen.getByText("marketplace.resultCount:2")).toBeInTheDocument();
        });

        it("offers the languages the server reported, with their counts", async () => {
            apiGet.mockResolvedValue({
                data: response({ facets: { languages: [{ name: "java", count: 3 }], mine: 0, others: 3 } }),
            });
            renderPage();

            await waitFor(() => expect(screen.getByRole("option", { name: "java (3)" })).toBeInTheDocument());
        });
    });

    describe("the request it sends", () => {
        it("asks for the state the URL describes", async () => {
            renderPage("/library/worksheets?q=arrays&lang=java&owner=me&sort=popularity&page=2");

            await waitFor(() => expect(apiGet).toHaveBeenCalled());
            expect(apiGet.mock.calls[0][0]).toBe("/library/worksheets");
            expect(lastRequest()).toEqual({
                q: "arrays", lang: "java", owner: "me", sort: "popularity", page: 2, size: 20,
            });
        });

        it("re-requests when a filter changes", async () => {
            renderPage();
            await waitFor(() => expect(apiGet).toHaveBeenCalledTimes(1));

            fireEvent.change(screen.getByLabelText("marketplace.langLabel"), { target: { value: "java" } });

            await waitFor(() => expect(apiGet).toHaveBeenCalledTimes(2));
            expect(lastRequest().lang).toBe("java");
        });

        it("does not fire a request per keystroke", async () => {
            renderPage();
            await waitFor(() => expect(apiGet).toHaveBeenCalledTimes(1));

            const box = screen.getByLabelText("marketplace.searchLabel");
            fireEvent.change(box, { target: { value: "r" } });
            fireEvent.change(box, { target: { value: "re" } });
            fireEvent.change(box, { target: { value: "rek" } });

            // The debounce is what keeps a live search box from pushing one history entry and
            // one request per letter.
            expect(apiGet).toHaveBeenCalledTimes(1);
        });
    });

    describe("copying", () => {
        it("takes the teacher straight to their new copy", async () => {
            apiPost.mockResolvedValue({ data: { id: "copy-id" } });
            renderPage();
            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());

            fireEvent.click(screen.getByRole("button", { name: /marketplace\.copy/ }));

            await waitFor(() => expect(apiPost).toHaveBeenCalledWith(
                `/worksheets/${item().worksheetId}/copy`));
            expect(navigate).toHaveBeenCalledWith("/worksheets/copy-id",
                { state: { justCopied: true, from: "marketplace", search: "" } });
        });

        it("lets the teacher get back to the list they were browsing", async () => {
            // Without the provenance the copy is a dead end: its back button would offer home,
            // and a teacher copying three worksheets in a row would return to the marketplace
            // by hand three times.
            apiPost.mockResolvedValue({ data: { id: "copy-id" } });
            renderPage();
            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());

            fireEvent.click(screen.getByRole("button", { name: /marketplace\.copy/ }));

            await waitFor(() => expect(navigate).toHaveBeenCalled());
            const state = navigate.mock.calls[0][1].state;
            expect(state.from).toBe("marketplace");
        });

        it("says a worksheet is gone rather than showing a status code", async () => {
            // 404 from these endpoints has no body, so the fallback message never gets a look
            // in — the user would otherwise read "Request failed with status code 404".
            const alerts: string[] = [];
            vi.spyOn(window, "alert").mockImplementation((m) => { alerts.push(String(m)); });
            apiPost.mockRejectedValue({ response: { status: 404, data: "" },
                                        message: "Request failed with status code 404" });
            renderPage();
            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());

            fireEvent.click(screen.getByRole("button", { name: /marketplace\.copy/ }));

            await waitFor(() => expect(alerts).toEqual(["marketplace.errorGone"]));
        });

        it("still reports other failures with the generic message", async () => {
            const alerts: string[] = [];
            vi.spyOn(window, "alert").mockImplementation((m) => { alerts.push(String(m)); });
            apiPost.mockRejectedValue({ response: { status: 500, data: "" } });
            renderPage();
            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());

            fireEvent.click(screen.getByRole("button", { name: /marketplace\.copy/ }));

            await waitFor(() => expect(alerts).toEqual(["marketplace.errorCopying"]));
        });

        it("is not offered to viewers who may not copy", async () => {
            auth = { isLoggedIn: false, isTeacher: false, isAdmin: false, user: null };
            renderPage();

            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());
            expect(screen.queryByRole("button", { name: /marketplace\.copy/ })).not.toBeInTheDocument();
        });

        it("is not offered on your own worksheet, which leaves only the way in to open it", async () => {
            // Same id as the item's ownerId: this row is the viewer's own worksheet. Copying it
            // would duplicate something they already own and can edit where it stands.
            auth = { ...auth, user: { userId: "22222222-2222-2222-2222-222222222222" } };
            renderPage();

            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());
            expect(screen.queryByRole("button", { name: /marketplace\.copy/ })).not.toBeInTheDocument();
            expect(screen.getByRole("link", { name: /marketplace\.open/ })).toBeInTheDocument();
        });

        it("is still offered on a worksheet somebody else owns", async () => {
            auth = { ...auth, user: { userId: "99999999-9999-9999-9999-999999999999" } };
            renderPage();

            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());
            expect(screen.getByRole("button", { name: /marketplace\.copy/ })).toBeInTheDocument();
        });

        it("is offered on seed material, which has no owner and so is nobody's own", async () => {
            auth = { ...auth, user: { userId: "22222222-2222-2222-2222-222222222222" } };
            apiGet.mockResolvedValue({ data: response({ items: [item({ ownerId: null })] }) });
            renderPage();

            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());
            expect(screen.getByRole("button", { name: /marketplace\.copy/ })).toBeInTheDocument();
        });
    });

    describe("paging", () => {
        it("is hidden when everything fits on one page", async () => {
            renderPage();
            await waitFor(() => expect(screen.getByText("Rekursion")).toBeInTheDocument());
            expect(screen.queryByText("marketplace.next")).not.toBeInTheDocument();
        });

        it("appears once there is more than one page, with the first page's back button disabled", async () => {
            apiGet.mockResolvedValue({ data: response({ total: 45 }) });
            renderPage();

            await waitFor(() => expect(screen.getByText("marketplace.next")).toBeInTheDocument());
            expect(screen.getByText("marketplace.previous")).toBeDisabled();
            expect(screen.getByText("marketplace.next")).not.toBeDisabled();
        });

        it("stops at the last page", async () => {
            apiGet.mockResolvedValue({ data: response({ total: 45, page: 2 }) });
            renderPage("/library/worksheets?page=2");

            await waitFor(() => expect(screen.getByText("marketplace.next")).toBeInTheDocument());
            expect(screen.getByText("marketplace.next")).toBeDisabled();
        });
    });

    describe("clearing filters", () => {
        it("is offered only when there is something to clear", async () => {
            renderPage();
            await waitFor(() => expect(apiGet).toHaveBeenCalled());
            expect(screen.queryByText("marketplace.clearFilters")).not.toBeInTheDocument();
        });

        it("resets everything back to the default view", async () => {
            renderPage("/library/worksheets?q=arrays&lang=java");
            await waitFor(() => expect(screen.getByText("marketplace.clearFilters")).toBeInTheDocument());

            fireEvent.click(screen.getByText("marketplace.clearFilters"));

            await waitFor(() => expect(lastRequest()).toEqual({ page: 0, size: 20, sort: "recent" }));
        });
    });
});
