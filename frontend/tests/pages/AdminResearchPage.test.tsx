import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";

const apiGet = vi.fn();
const downloadFile = vi.fn();

vi.mock("../../src/lib/api.ts", () => ({
    api: { get: (...args: unknown[]) => apiGet(...args) },
    apiErrorMessage: (_e: unknown, fallback: string) => fallback,
}));

vi.mock("../../src/lib/download.ts", () => ({
    downloadFile: (...args: unknown[]) => downloadFile(...args),
    downloadTimestamp: () => "2026-09-17T12-00-00",
}));

vi.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key: string, options?: Record<string, unknown>) =>
            options && "page" in options ? `${key}:${options.page}/${options.total}` : key,
    }),
}));

const { default: AdminResearchPage } = await import("../../src/pages/AdminResearchPage.tsx");

const CLASS_A = "20000000-0000-0000-0000-00000000000a";

function eventRow(overrides: Record<string, unknown> = {}) {
    return {
        id: "60000000-0000-0000-0000-000000000001",
        created_at: "2026-09-17T09:14:02",
        class_id: CLASS_A,
        user_id: "10000000-0000-0000-0000-000000000002",
        task_id: "50000000-0000-0000-0000-000000000001",
        sequence_id: null,
        tier_level: 1,
        event_type: "arrangement_changed",
        event_data: '{"moves":[1,2]}',
        ...overrides,
    };
}

const EVENT_COLUMNS = [
    "id",
    "created_at",
    "class_id",
    "user_id",
    "task_id",
    "sequence_id",
    "tier_level",
    "event_type",
    "event_data",
];

function pageResponse(rows: Record<string, unknown>[], overrides: Record<string, unknown> = {}) {
    return {
        columns: EVENT_COLUMNS,
        content: rows,
        totalElements: rows.length,
        totalPages: 1,
        page: 0,
        size: 50,
        ...overrides,
    };
}

function summaryResponse(overrides: Record<string, unknown> = {}) {
    return {
        totalRows: 1,
        identifiedUsers: 1,
        anonymousRows: 0,
        eventCounts: { arrangement_changed: 1, task_opened: 3 },
        ...overrides,
    };
}

/** Routes each GET to a response based on its URL, since the page fires several at once. */
function respond({
    rows = [eventRow()],
    page = {},
    summary = {},
}: {
    rows?: Record<string, unknown>[];
    page?: Record<string, unknown>;
    summary?: Record<string, unknown>;
} = {}) {
    apiGet.mockImplementation((url: string) => {
        if (url === "/classes") {
            return Promise.resolve({
                data: [
                    { id: CLASS_A, name: "7a", trackingEnabled: true },
                    { id: "20000000-0000-0000-0000-00000000000b", name: "7b", trackingEnabled: false },
                ],
            });
        }
        if (url.endsWith("/summary")) {
            return Promise.resolve({ data: summaryResponse(summary) });
        }
        return Promise.resolve({ data: pageResponse(rows, page) });
    });
}

/** The params of the most recent request for a dataset page (not the summary or class list). */
const lastPageRequest = () => {
    const call = apiGet.mock.calls
        .filter(([url]) => typeof url === "string" && url.startsWith("/admin/research/"))
        .filter(([url]) => !String(url).endsWith("/summary"))
        .at(-1);
    return { url: call?.[0] as string, params: call?.[1]?.params as Record<string, unknown> };
};

function renderPage() {
    render(
        <MemoryRouter initialEntries={["/admin/research"]}>
            <Routes>
                <Route path="/admin/research" element={<AdminResearchPage />} />
            </Routes>
        </MemoryRouter>,
    );
}

describe("AdminResearchPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        respond();
    });

    describe("loading the table", () => {
        it("asks for the events dataset first and renders its columns", async () => {
            renderPage();
            await waitFor(() =>
                expect(lastPageRequest().url).toBe("/admin/research/events"),
            );
            expect(screen.getByText("event_type")).toBeInTheDocument();
            expect(screen.getByText("arrangement_changed")).toBeInTheDocument();
        });

        it("reports a failed request instead of showing an empty table", async () => {
            apiGet.mockRejectedValue(new Error("boom"));
            renderPage();
            await waitFor(() =>
                expect(screen.getByText("adminResearch.loadError")).toBeInTheDocument(),
            );
            // The "nothing recorded" message would be a lie here — nothing was loaded at all.
            expect(screen.queryByText("adminResearch.empty")).not.toBeInTheDocument();
        });

        it("says so when the filter genuinely matches nothing", async () => {
            respond({ rows: [], summary: { totalRows: 0, identifiedUsers: 0 } });
            renderPage();
            await waitFor(() =>
                expect(screen.getByText("adminResearch.empty")).toBeInTheDocument(),
            );
        });
    });

    describe("anonymised rows", () => {
        it("marks a row with no student rather than leaving the cell blank", async () => {
            respond({ rows: [eventRow({ user_id: null })] });
            renderPage();
            await waitFor(() =>
                expect(screen.getByText("adminResearch.anonymous")).toBeInTheDocument(),
            );
        });

        it("marks a row with no class too", async () => {
            respond({ rows: [eventRow({ class_id: null })] });
            renderPage();
            await waitFor(() =>
                expect(screen.getByText("adminResearch.noClass")).toBeInTheDocument(),
            );
        });
    });

    describe("filtering", () => {
        it("re-queries with the class id when a class is picked", async () => {
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.change(screen.getByLabelText("adminResearch.filterClass"), { target: { value: CLASS_A } });

            await waitFor(() => expect(lastPageRequest().params.classId).toBe(CLASS_A));
        });

        it("offers the event types the summary actually reports", async () => {
            renderPage();
            await waitFor(() =>
                expect(screen.getByLabelText("adminResearch.filterType")).toBeInTheDocument(),
            );
            const options = screen.getByLabelText("adminResearch.filterType");
            expect(options).toHaveTextContent("arrangement_changed");
            expect(options).toHaveTextContent("task_opened");
        });

        it("drops the event type when switching to a dataset that has none", async () => {
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.change(screen.getByLabelText("adminResearch.filterType"), { target: { value: "task_opened" } });
            await waitFor(() => expect(lastPageRequest().params.eventType).toBe("task_opened"));

            fireEvent.click(screen.getByText("adminResearch.dataset.hints"));

            // Sending eventType to /hints is a 400, so it has to be dropped, not just hidden.
            await waitFor(() => {
                const req = lastPageRequest();
                expect(req.url).toBe("/admin/research/hints");
                expect(req.params.eventType).toBeUndefined();
            });
        });

        it("returns to the first page when a filter changes", async () => {
            respond({ page: { totalPages: 5, page: 1 } });
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.click(screen.getByRole("button", { name: "adminResearch.nextPage" }));

            fireEvent.change(screen.getByLabelText("adminResearch.filterClass"), { target: { value: CLASS_A } });

            // Page 3 of the old result is not page 3 of the new one.
            await waitFor(() => expect(lastPageRequest().params.page).toBe(0));
        });
    });

    describe("downloads", () => {
        it("exports the CSV with the filters currently on screen", async () => {
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.change(screen.getByLabelText("adminResearch.filterClass"), { target: { value: CLASS_A } });
            await waitFor(() => expect(lastPageRequest().params.classId).toBe(CLASS_A));

            fireEvent.click(screen.getByText("adminResearch.downloadCsv"));

            await waitFor(() => expect(downloadFile).toHaveBeenCalled());
            const [method, url, , params] = downloadFile.mock.calls.at(-1)!;
            expect(method).toBe("get");
            expect(url).toBe("/admin/research/events/export.csv");
            expect(params).toMatchObject({ classId: CLASS_A });
            // Paging is the table's business; the export is the whole filter.
            expect(params).not.toHaveProperty("page");
        });

        it("exports the class ZIP from one path regardless of the open tab", async () => {
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.click(screen.getByText("adminResearch.dataset.hints"));
            fireEvent.click(screen.getByText("adminResearch.downloadZip"));

            await waitFor(() => expect(downloadFile).toHaveBeenCalled());
            expect(downloadFile.mock.calls.at(-1)![1]).toBe("/admin/research/export.zip");
        });

        it("reports a failed download without wiping the table", async () => {
            downloadFile.mockRejectedValueOnce(new Error("nope"));
            renderPage();
            await waitFor(() => expect(lastPageRequest().url).toBeTruthy());

            fireEvent.click(screen.getByText("adminResearch.downloadCsv"));

            await waitFor(() =>
                expect(screen.getByText("adminResearch.downloadError")).toBeInTheDocument(),
            );
            expect(screen.getByText("arrangement_changed")).toBeInTheDocument();
        });
    });
});
