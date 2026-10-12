import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import WorksheetSchedule from "../../src/components/WorksheetSchedule.tsx";
import type { ClassWorksheetInfo } from "../../src/types/models.ts";

// Keys stand in for their translations, so the assertions read as the keys.
vi.mock("react-i18next", () => ({
    useTranslation: () => ({ t: (key: string) => key }),
}));

const post = vi.fn();
vi.mock("../../src/lib/api.ts", () => ({
    api: { post: (...args: unknown[]) => post(...args), put: vi.fn() },
    apiErrorMessage: (_e: unknown, fallback: string) => fallback,
}));

const hidden: ClassWorksheetInfo = {
    worksheetId: "w1",
    name: "Loops",
    description: "",
    published: false,
    releaseAt: null,
    deadlineAt: null,
    graceMinutes: null,
    availability: "SCHEDULED",
};

function setup(worksheet: ClassWorksheetInfo = hidden) {
    const onSaved = vi.fn();
    const view = render(<WorksheetSchedule classId="c1" worksheet={worksheet} onSaved={onSaved} />);
    return { onSaved, ...view };
}

describe("WorksheetSchedule publish button", () => {
    beforeEach(() => {
        post.mockReset();
        post.mockResolvedValue({ data: {} });
    });

    it("writes the moment of publication into the release fields", async () => {
        // Publishing stamps release_at server-side the way ending stamps the
        // deadline; leaving the field blank would hide when it went live.
        const { container } = setup();
        fireEvent.click(screen.getByText("schedule.edit"));
        const date = container.querySelector<HTMLInputElement>("#rel-w1")!;
        expect(date.value).toBe("");

        fireEvent.click(screen.getByText("schedule.publishNow"));

        await waitFor(() => expect(date.value).not.toBe(""));
        const [d, t] = [new Date().toISOString().slice(0, 10), date.value];
        expect(t).toBe(d);
        // A time comes with it, not just the date.
        const time = container.querySelectorAll<HTMLInputElement>('input[type="time"]')[0];
        expect(time.value).toMatch(/^\d{2}:\d{2}$/);
    });

    it("reports the release instant back to the parent", async () => {
        const { onSaved } = setup();
        fireEvent.click(screen.getByText("schedule.publishNow"));

        await waitFor(() => expect(onSaved).toHaveBeenCalled());
        const patch = onSaved.mock.calls[0][0];
        expect(patch.published).toBe(true);
        expect(patch.availability).toBe("OPEN");
        // Without this the row keeps showing an empty release date until reload.
        expect(patch.releaseAt).toBeTruthy();
        expect(Math.abs(Date.parse(patch.releaseAt) - Date.now())).toBeLessThan(5000);
    });

    it("calls the publish endpoint", async () => {
        setup();
        fireEvent.click(screen.getByText("schedule.publishNow"));
        await waitFor(() => expect(post).toHaveBeenCalledWith("/classes/c1/worksheets/w1/publish"));
    });

    it("clears the release fields again when unpublishing", async () => {
        // The server clears release_at too, so both routes to visibility close.
        const open: ClassWorksheetInfo = { ...hidden, published: true, availability: "OPEN",
            releaseAt: "2026-01-05T09:00:00Z" };
        const { container, onSaved } = setup(open);
        fireEvent.click(screen.getByText("schedule.edit"));
        const date = container.querySelector<HTMLInputElement>("#rel-w1")!;
        expect(date.value).toBe("2026-01-05");

        fireEvent.click(screen.getByText("schedule.unpublish"));

        await waitFor(() => expect(date.value).toBe(""));
        expect(onSaved).toHaveBeenCalledWith(
            expect.objectContaining({ published: false, releaseAt: null, availability: "SCHEDULED" }));
    });
});

describe("WorksheetSchedule hints", () => {
    beforeEach(() => {
        post.mockReset();
        post.mockResolvedValue({ data: {} });
    });

    it("renders every hint clause as its own block", () => {
        // t() is mocked to return keys, so the wording is checked in
        // hintLines.test.ts; what matters here is that the component routes
        // hints through the splitter and gives each clause a block of its own.
        const { container } = setup();
        fireEvent.click(screen.getByText("schedule.edit"));

        const hints = container.querySelectorAll("small.text-muted");
        expect(hints).toHaveLength(3);
        hints.forEach((hint) => {
            expect(hint.querySelectorAll("span.d-block").length).toBeGreaterThan(0);
        });
    });

    it("top-aligns the three columns so the inputs line up", () => {
        // Bottom alignment sank the grace column, whose hint is one line
        // shorter than the other two.
        const { container } = setup();
        fireEvent.click(screen.getByText("schedule.edit"));

        const row = container.querySelector(".row.g-2")!;
        expect(row.className).toContain("align-items-start");
        expect(row.className).not.toContain("align-items-end");
    });
});
