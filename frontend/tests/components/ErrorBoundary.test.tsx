import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ErrorBoundary } from "../../src/components/ErrorBoundary.tsx";

/**
 * The object that originally blanked the app: an ApiErrorResponse body handed
 * to JSX as a child. Rendering it throws "Objects are not valid as a React
 * child", and without a boundary React unmounts the entire tree.
 */
const apiErrorBody = {
    error: "Ungültige Eingabe",
    message: "Worksheet description must not be empty.",
};

function Boom({ shouldThrow }: { shouldThrow: boolean }) {
    // Rendering a plain object as a child is what throws; casting keeps the
    // deliberate mistake compiling.
    return <div>{shouldThrow ? (apiErrorBody as unknown as string) : "recovered"}</div>;
}

const fallback = (error: Error, reset: () => void) => (
    <div>
        <span>caught: {error.message}</span>
        <button onClick={reset}>retry</button>
    </div>
);

describe("ErrorBoundary", () => {
    beforeEach(() => {
        // React logs caught render errors; silence the expected noise so a
        // genuine failure stays visible in the test output.
        vi.spyOn(console, "error").mockImplementation(() => {});
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("renders the fallback instead of unmounting the tree", () => {
        render(
            <ErrorBoundary fallback={fallback}>
                <Boom shouldThrow />
            </ErrorBoundary>,
        );

        expect(screen.getByText(/caught:/)).toBeInTheDocument();
    });

    it("hands the real Error to the fallback", () => {
        render(
            <ErrorBoundary fallback={fallback}>
                <Boom shouldThrow />
            </ErrorBoundary>,
        );

        expect(screen.getByText(/Objects are not valid as a React child/)).toBeInTheDocument();
    });

    it("renders healthy children untouched", () => {
        render(
            <ErrorBoundary fallback={fallback}>
                <p>healthy</p>
            </ErrorBoundary>,
        );

        expect(screen.getByText("healthy")).toBeInTheDocument();
        expect(screen.queryByText(/caught:/)).not.toBeInTheDocument();
    });

    it("clears the error when resetKey changes, so navigation recovers", () => {
        // App passes the pathname as resetKey. Without this a boundary stays
        // latched after catching and keeps showing the fallback on the next
        // route, which reads as a permanently stuck app.
        const { rerender } = render(
            <ErrorBoundary fallback={fallback} resetKey="/broken">
                <Boom shouldThrow />
            </ErrorBoundary>,
        );
        expect(screen.getByText(/caught:/)).toBeInTheDocument();

        rerender(
            <ErrorBoundary fallback={fallback} resetKey="/other">
                <Boom shouldThrow={false} />
            </ErrorBoundary>,
        );

        expect(screen.getByText("recovered")).toBeInTheDocument();
        expect(screen.queryByText(/caught:/)).not.toBeInTheDocument();
    });

    it("stays in the fallback while resetKey is unchanged", () => {
        const { rerender } = render(
            <ErrorBoundary fallback={fallback} resetKey="/same">
                <Boom shouldThrow />
            </ErrorBoundary>,
        );

        rerender(
            <ErrorBoundary fallback={fallback} resetKey="/same">
                <Boom shouldThrow={false} />
            </ErrorBoundary>,
        );

        expect(screen.getByText(/caught:/)).toBeInTheDocument();
    });

    it("retries the subtree when the reset callback fires", () => {
        let shouldThrow = true;
        const Retryable = () => <div>{shouldThrow ? (apiErrorBody as unknown as string) : "recovered"}</div>;

        render(
            <ErrorBoundary fallback={fallback} resetKey="/x">
                <Retryable />
            </ErrorBoundary>,
        );
        expect(screen.getByText(/caught:/)).toBeInTheDocument();

        shouldThrow = false;
        // fireEvent wraps the dispatch in act() so the reset re-render flushes;
        // calling .click() on the node directly would leave it pending.
        fireEvent.click(screen.getByRole("button", { name: "retry" }));

        expect(screen.getByText("recovered")).toBeInTheDocument();
    });

    it("reports the error through onError", () => {
        const onError = vi.fn();

        render(
            <ErrorBoundary fallback={fallback} onError={onError}>
                <Boom shouldThrow />
            </ErrorBoundary>,
        );

        expect(onError).toHaveBeenCalledOnce();
        const [error, info] = onError.mock.calls[0];
        expect(error).toBeInstanceOf(Error);
        expect(info).toHaveProperty("componentStack");
    });
});
