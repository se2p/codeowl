import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import WorksheetPage from "../../src/pages/WorksheetPage.tsx";
import { api } from "../../src/lib/api.ts";

/**
 * What a visitor is told when a worksheet will not open.
 *
 * <p>Narrow on purpose. The page is large and mostly about editing; this covers only the load
 * failure, because that is the path a stranger following a link takes and the one that broke.
 *
 * <p>The bug: {@code ResponseEntity.notFound().build()} sends an empty body, so
 * {@link apiErrorMessage} found nothing to report and fell through to axios's own English
 * "Request failed with status code 404". Someone opening a link to a worksheet whose owner had
 * just made it private read a stack-trace fragment in a German UI. Both helpers are therefore
 * the real implementations here -- mocking them would test the mock, and the defect lived
 * exactly in how the two behave together.
 */
vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("../../src/lib/api.ts", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../src/lib/api.ts")>();
    return {
        ...actual,
        api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
    };
});

/**
 * Who is looking. Mutable because the page behaves quite differently for a signed-out visitor
 * following a link and for the teacher who owns the worksheet, and both are worth covering.
 */
let currentUser: { userId: string } | null = null;

vi.mock("../../src/context/AuthContext.tsx", () => ({
    useAuth: () => ({
        user: currentUser,
        isLoggedIn: currentUser !== null,
        isTeacher: currentUser !== null,
        isAdmin: false,
        isStudent: false,
    }),
}));

const get = api.get as unknown as ReturnType<typeof vi.fn>;

/** An axios rejection as the interceptor hands it on: a status, and for 404 no body at all. */
function httpError(status: number, data?: unknown) {
    return {
        message: `Request failed with status code ${status}`,
        response: { status, data },
    };
}

const WORKSHEET = {
    id: "w-1",
    name: "Rekursion Klasse 10",
    description: "Einstieg",
    sequenceDTOS: [],
    ownerId: "someone-else",
    hintsEnabled: false,
    hintCreditsPerTask: null,
    visibility: "PUBLIC",
    createdAt: null,
    copiedFrom: null,
    copiedAt: null,
};

/** api.get is called for more than the worksheet once it loads; only that one needs a body. */
function serveWorksheet() {
    get.mockImplementation((url: string) =>
        url.includes("/worksheets/")
            ? Promise.resolve({ data: WORKSHEET })
            : Promise.resolve({ data: [] }));
}

function renderPage(state?: unknown) {
    return render(
        <MemoryRouter initialEntries={[{ pathname: "/worksheets/w-1", state }]}>
            <Routes>
                <Route path="/worksheets/:sheetId" element={<WorksheetPage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe("WorksheetPage, when the worksheet does not load", () => {
    beforeEach(() => {
        currentUser = null;
        get.mockReset();
    });

    it("says the worksheet is not available rather than quoting axios", async () => {
        get.mockRejectedValue(httpError(404));
        renderPage();

        await waitFor(() => expect(screen.getByText("worksheet.notAvailable")).toBeInTheDocument());
    });

    it("never shows the raw transport message on a 404", async () => {
        // The regression itself, asserted directly: the empty body is what let axios's English
        // sentence reach the page, and that is what must not come back.
        get.mockRejectedValue(httpError(404));
        renderPage();

        await waitFor(() => expect(screen.getByText("worksheet.notAvailable")).toBeInTheDocument());
        expect(screen.queryByText(/Request failed with status code/)).not.toBeInTheDocument();
    });

    it("still reports what the server said for errors that are not 404", async () => {
        // The 404 branch must stay a special case. If it swallowed every failure, a broken
        // server would look to the visitor exactly like a worksheet that was taken down.
        get.mockRejectedValue(httpError(500, { message: "Datenbank nicht erreichbar" }));
        renderPage();

        await waitFor(() =>
            expect(screen.getByText("Datenbank nicht erreichbar")).toBeInTheDocument());
        expect(screen.queryByText("worksheet.notAvailable")).not.toBeInTheDocument();
    });

    it("shows the loading state until the request settles", async () => {
        get.mockReturnValue(new Promise(() => {})); // never resolves
        renderPage();

        expect(screen.getByText("common.loading")).toBeInTheDocument();
    });
});

describe("WorksheetPage, the way back", () => {
    beforeEach(() => {
        currentUser = null;
        get.mockReset();
        serveWorksheet();
    });

    it("offers the marketplace alongside home to a visitor who came from it", async () => {
        renderPage({ from: "marketplace", search: "?q=rekursion" });

        await waitFor(() =>
            expect(screen.getByText(/marketplace\.backToMarketplace/)).toBeInTheDocument());
        // Home stays. A worksheet copied from the marketplace is now yours, and wanting to go
        // to your own list rather than back to the shop is the ordinary case.
        expect(screen.getByText(/common\.home/)).toBeInTheDocument();
    });

    it("offers home alone when there is nowhere to go back to", async () => {
        // A pasted link or a bookmark carries no provenance, so there is no history to claim.
        renderPage();

        await waitFor(() => expect(screen.getByText(/common\.home/)).toBeInTheDocument());
        expect(screen.queryByText(/marketplace\.backToMarketplace/)).not.toBeInTheDocument();
    });

    it("leaves the class button to the class page's own marker", async () => {
        // Two independent entry points share this row. Neither may claim the other's visitor.
        renderPage({ fromClass: "c-1" });

        await waitFor(() => expect(screen.getByText(/worksheet\.backToClass/)).toBeInTheDocument());
        expect(screen.queryByText(/marketplace\.backToMarketplace/)).not.toBeInTheDocument();
    });
});

describe("WorksheetPage, whose material is in here", () => {
    const MINE = "me";
    const sheetWith = (sequences: { id: string; ownerId: string | null }[]) => ({
        ...WORKSHEET, ownerId: MINE, sequenceDTOS: sequences,
    });

    beforeEach(() => {
        currentUser = { userId: MINE };
        get.mockReset();
    });

    it("marks the sequences that belong to somebody else", async () => {
        // The complaint this answers: after forking a sequence the list looked exactly as it
        // had before, so the fork appeared to have done nothing at all.
        get.mockImplementation((url: string) =>
            url.includes("/worksheets/")
                ? Promise.resolve({ data: sheetWith([
                    { id: "s-1", ownerId: "someone-else" },
                    { id: "s-2", ownerId: null },
                    { id: "s-3", ownerId: MINE }]) })
                : Promise.resolve({ data: [] }));

        renderPage();

        // Two foreign: one owned by another teacher, one seed material owned by nobody.
        await waitFor(() =>
            expect(screen.getAllByText("worksheet.foreignSequence")).toHaveLength(2));
    });

    it("marks nothing once everything in the worksheet is the teacher's own", async () => {
        get.mockImplementation((url: string) =>
            url.includes("/worksheets/")
                ? Promise.resolve({ data: sheetWith([{ id: "s-1", ownerId: MINE }]) })
                : Promise.resolve({ data: [] }));

        renderPage();

        await waitFor(() => expect(screen.getByText(/worksheet\.sequenceLabel/)).toBeInTheDocument());
        expect(screen.queryByText("worksheet.foreignSequence")).not.toBeInTheDocument();
    });
});
