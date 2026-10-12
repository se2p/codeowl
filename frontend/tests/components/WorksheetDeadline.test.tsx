import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import WorksheetDeadline from "../../src/components/WorksheetDeadline.tsx";
import type { ClassWorksheetInfo } from "../../src/types/models.ts";

// Keys stand in for translations; interpolation is spelled out so the
// assertions can check that the formatted instant reaches the string.
vi.mock("react-i18next", () => ({
    useTranslation: () => ({
        t: (key: string, vars?: Record<string, string>) =>
            vars ? `${key}:${Object.values(vars).join("|")}` : key,
        i18n: { language: "en-GB" },
    }),
}));

function ws(over: Partial<ClassWorksheetInfo>): ClassWorksheetInfo {
    return {
        worksheetId: "w1",
        name: "Loops",
        description: "",
        published: true,
        releaseAt: null,
        deadlineAt: null,
        graceMinutes: null,
        availability: "OPEN",
        ...over,
    };
}

const inHours = (h: number) => new Date(Date.now() + h * 3600_000).toISOString();

describe("WorksheetDeadline", () => {
    it("shows nothing when the worksheet has no deadline", () => {
        // "Open indefinitely" is not news; a line saying so would be noise.
        const { container } = render(<WorksheetDeadline worksheet={ws({})} />);
        expect(container).toBeEmptyDOMElement();
    });

    it("states when an open worksheet is due", () => {
        render(<WorksheetDeadline worksheet={ws({ deadlineAt: inHours(72) })} />);
        expect(screen.getByText(/^deadline\.due:/)).toBeInTheDocument();
    });

    it("plays a distant deadline down and an imminent one up", () => {
        const { container: far } = render(<WorksheetDeadline worksheet={ws({ deadlineAt: inHours(72) })} />);
        expect(far.querySelector(".text-muted")).toBeTruthy();
        expect(far.querySelector(".text-danger")).toBeNull();

        const { container: near } = render(<WorksheetDeadline worksheet={ws({ deadlineAt: inHours(3) })} />);
        expect(near.querySelector(".text-danger")).toBeTruthy();
    });

    it("says so plainly once the deadline has passed", () => {
        // Closed worksheets stay readable, so the student needs to be told why
        // they cannot work on it any more.
        render(<WorksheetDeadline worksheet={ws({ deadlineAt: inHours(-5), availability: "CLOSED" })} />);
        expect(screen.getByText("deadline.closed")).toBeInTheDocument();
        expect(screen.getByText(/^deadline\.was:/)).toBeInTheDocument();
    });

    it("still marks a closed worksheet that has no recorded deadline", () => {
        render(<WorksheetDeadline worksheet={ws({ availability: "CLOSED" })} />);
        expect(screen.getByText("deadline.closed")).toBeInTheDocument();
        expect(screen.queryByText(/^deadline\.was:/)).toBeNull();
    });
});
