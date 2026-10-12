import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";

interface Props {
    children: ReactNode;

    /**
     * Renders the fallback UI. Receives the caught error and a callback that
     * clears the error so the same subtree is retried.
     */
    fallback: (error: Error, reset: () => void) => ReactNode;

    /**
     * When this value changes, a caught error is cleared automatically.
     * Pass the current route so navigating away from a broken page recovers
     * without a reload — a boundary otherwise stays in its error state forever.
     */
    resetKey?: unknown;

    /** Optional hook for reporting; the error is logged to the console regardless. */
    onError?: (error: Error, info: ErrorInfo) => void;
}

interface State {
    error: Error | null;
}

/**
 * Catches render-time exceptions in the subtree below it.
 *
 * Without a boundary anywhere in the tree, React unmounts the entire
 * application on any render throw and the user is left staring at a blank
 * page. The classic trigger here is putting a non-primitive into JSX — an
 * API error body, for instance — which throws "Objects are not valid as a
 * React child".
 *
 * Note this only catches errors thrown while rendering, in lifecycle methods
 * and in constructors of the subtree. Errors inside event handlers, async
 * callbacks and promise rejections never reach it; those still have to be
 * handled where they happen.
 */
export class ErrorBoundary extends Component<Props, State> {
    state: State = { error: null };

    static getDerivedStateFromError(error: Error): State {
        return { error };
    }

    componentDidCatch(error: Error, info: ErrorInfo): void {
        console.error("Unhandled render error:", error, info.componentStack);
        this.props.onError?.(error, info);
    }

    componentDidUpdate(prevProps: Props): void {
        if (this.state.error !== null && prevProps.resetKey !== this.props.resetKey) {
            this.setState({ error: null });
        }
    }

    private readonly reset = (): void => this.setState({ error: null });

    render(): ReactNode {
        const { error } = this.state;
        if (error !== null) {
            return this.props.fallback(error, this.reset);
        }
        return this.props.children;
    }
}

export default ErrorBoundary;
