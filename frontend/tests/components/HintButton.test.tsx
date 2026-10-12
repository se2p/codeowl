import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import HintButton from "../../src/components/HintButton.tsx";

/**
 * The tooltip has to work precisely when the button is disabled — that is when
 * it carries the explanation ("no credits left", "click Feedback first").
 *
 * Two browser behaviours make that awkward, and both were live bugs here: a
 * disabled control fires no pointer events, and it swallows the hover instead
 * of letting the wrapper see it.
 */
const base = {
    level: 1 as const,
    className: "btn btn-outline-info",
    icon: "💡",
    label: "Hint",
    onRequest: () => {},
};

describe("HintButton", () => {
    it("shows its tooltip on hover while disabled", async () => {
        render(<HintButton {...base} tooltip="All hint credits used up." disabled loading={false} />);

        // Hovering the wrapper is what a user hovering the greyed-out button does,
        // since the button itself is pointer-events: none.
        fireEvent.mouseOver(screen.getByRole("button", { name: /Hint/ }).parentElement!);

        await waitFor(() => {
            expect(screen.getByRole("tooltip")).toHaveTextContent("All hint credits used up.");
        });
    });

    it("lets hit-testing fall through to the wrapper when disabled", () => {
        render(<HintButton {...base} tooltip="nope" disabled loading={false} />);
        const button = screen.getByRole("button", { name: /Hint/ });

        expect(button).toBeDisabled();
        // Without this the wrapper never receives the hover and no tooltip appears.
        expect(button).toHaveStyle({ pointerEvents: "none" });
    });

    it("does not block pointer events while enabled", () => {
        render(<HintButton {...base} tooltip="Shows problematic blocks" disabled={false} loading={false} />);
        const button = screen.getByRole("button", { name: /Hint/ });

        expect(button).toBeEnabled();
        expect(button).not.toHaveStyle({ pointerEvents: "none" });
    });

    it("shows its tooltip on hover while enabled too", async () => {
        render(<HintButton {...base} tooltip="Shows problematic blocks" disabled={false} loading={false} />);

        fireEvent.mouseOver(screen.getByRole("button", { name: /Hint/ }));

        await waitFor(() => {
            expect(screen.getByRole("tooltip")).toHaveTextContent("Shows problematic blocks");
        });
    });

    it("requests the hint for its own level when clicked", () => {
        const onRequest = vi.fn();
        render(
            <HintButton {...base} level={2} label="Explain" tooltip="t"
                        disabled={false} loading={false} onRequest={onRequest} />,
        );

        fireEvent.click(screen.getByRole("button", { name: /Explain/ }));

        expect(onRequest).toHaveBeenCalledWith(2);
    });

    it("does not fire a request while disabled", () => {
        const onRequest = vi.fn();
        render(
            <HintButton {...base} tooltip="t" disabled loading={false} onRequest={onRequest} />,
        );

        fireEvent.click(screen.getByRole("button", { name: /Hint/ }));

        expect(onRequest).not.toHaveBeenCalled();
    });

    it("swaps the icon for a spinner while loading", () => {
        const { container } = render(
            <HintButton {...base} tooltip="t" disabled loading />,
        );
        expect(container.querySelector(".spinner-border")).toBeTruthy();
    });
});
