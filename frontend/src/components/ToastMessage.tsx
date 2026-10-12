import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

/**
 * AP19 Teil A (A12): Minimal auto-dismissing toast with an optional action button.
 *
 * <p>Rendered as a fixed-position element at bottom-right; one instance at a time is expected
 * (the caller manages the queue). Designed for the Library's one-click copy:
 * "Added to <worksheet> — [Undo] [Go]".
 *
 * <p>Bootstrap toast CSS is used for visual consistency, but the show/hide lifecycle is managed
 * by React state (no jQuery / Bootstrap JS dependency).
 */
export type ToastVariant = "info" | "success" | "warning" | "danger";

export interface ToastAction {
    label: string;
    /**
     * Called when the user clicks the action button. By default, the toast also dismisses
     * itself after this fires (set {@code keepOpen} if you want to stay open — e.g. while
     * an async undo is in flight).
     */
    onClick: () => void;
    /** When true, the toast stays open after the click; caller must dismiss via {@code onClose}. */
    keepOpen?: boolean;
    /** Optional icon class (e.g. "bi-arrow-counterclockwise"). */
    icon?: string;
}

export interface ToastMessageProps {
    message: string;
    /** Short description / context shown below the main message. */
    detail?: string;
    variant?: ToastVariant;
    /** First action (most prominent). */
    primaryAction?: ToastAction;
    /** Optional secondary action. */
    secondaryAction?: ToastAction;
    /** Called when the toast dismisses — either by timeout, close button, or action click. */
    onClose: () => void;
    /** Time in ms before the toast auto-dismisses. Default 6000; pass 0 to disable. */
    timeoutMs?: number;
}

export default function ToastMessage({
    message,
    detail,
    variant = "success",
    primaryAction,
    secondaryAction,
    onClose,
    timeoutMs = 6000,
}: ToastMessageProps) {
    const { t } = useTranslation();
    const timerRef = useRef<number | null>(null);

    // Auto-dismiss after timeout (unless disabled)
    useEffect(() => {
        if (timeoutMs <= 0) return;
        timerRef.current = window.setTimeout(() => onClose(), timeoutMs);
        return () => {
            if (timerRef.current) window.clearTimeout(timerRef.current);
        };
    }, [timeoutMs, onClose]);

    const bgClass = variantBg(variant);
    const textClass = variant === "warning" ? "text-dark" : "text-white";

    function handleAction(action: ToastAction) {
        action.onClick();
        if (!action.keepOpen) onClose();
    }

    return (
        <div
            className="toast-container position-fixed bottom-0 end-0 p-3"
            style={{ zIndex: 1100 }}
            aria-live="polite"
            aria-atomic="true"
        >
            <div
                className={`toast show ${bgClass} ${textClass} shadow-lg`}
                role="alert"
                style={{ minWidth: "22rem", maxWidth: "28rem" }}
            >
                <div className="toast-body d-flex align-items-start gap-2">
                    <i className={`bi ${variantIcon(variant)} fs-5 mt-1`} aria-hidden="true"></i>
                    <div className="flex-fill">
                        <div className="fw-semibold">{message}</div>
                        {detail && <div className="small opacity-75 mt-1">{detail}</div>}
                        {(primaryAction || secondaryAction) && (
                            <div className="mt-2 d-flex gap-2">
                                {primaryAction && (
                                    <button
                                        type="button"
                                        className={`btn btn-sm ${actionBtnClass(variant, "primary")}`}
                                        onClick={() => handleAction(primaryAction)}
                                    >
                                        {primaryAction.icon && (
                                            <i className={`bi ${primaryAction.icon} me-1`}></i>
                                        )}
                                        {primaryAction.label}
                                    </button>
                                )}
                                {secondaryAction && (
                                    <button
                                        type="button"
                                        className={`btn btn-sm ${actionBtnClass(variant, "secondary")}`}
                                        onClick={() => handleAction(secondaryAction)}
                                    >
                                        {secondaryAction.icon && (
                                            <i className={`bi ${secondaryAction.icon} me-1`}></i>
                                        )}
                                        {secondaryAction.label}
                                    </button>
                                )}
                            </div>
                        )}
                    </div>
                    <button
                        type="button"
                        className={`btn-close ${variant !== "warning" ? "btn-close-white" : ""}`}
                        aria-label={t("common.close")}
                        onClick={onClose}
                    ></button>
                </div>
            </div>
        </div>
    );
}

function variantBg(v: ToastVariant): string {
    switch (v) {
        case "success":
            return "bg-success";
        case "danger":
            return "bg-danger";
        case "warning":
            return "bg-warning";
        case "info":
        default:
            return "bg-primary";
    }
}

function variantIcon(v: ToastVariant): string {
    switch (v) {
        case "success":
            return "bi-check-circle-fill";
        case "danger":
            return "bi-exclamation-triangle-fill";
        case "warning":
            return "bi-exclamation-circle-fill";
        case "info":
        default:
            return "bi-info-circle-fill";
    }
}

function actionBtnClass(variant: ToastVariant, kind: "primary" | "secondary"): string {
    if (variant === "warning") {
        return kind === "primary" ? "btn-dark" : "btn-outline-dark";
    }
    // Light buttons on colored background pop better than outline-light.
    return kind === "primary" ? "btn-light" : "btn-outline-light";
}
