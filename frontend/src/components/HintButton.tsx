import { OverlayTrigger, Tooltip } from "react-bootstrap";

interface Props {
    level: 1 | 2;
    className: string;
    icon: string;
    label: string;
    tooltip: string;
    disabled: boolean;
    loading: boolean;
    onRequest: (level: 1 | 2) => void | Promise<void>;
}

/**
 * A hint button whose tooltip still works while the button is disabled.
 *
 * Two browser behaviours have to be worked around, and both only bite in the
 * state where the explanation matters most — no credits left, or feedback not
 * clicked yet:
 *
 *  1. A disabled control receives no pointer events, so a `title` (or any
 *     trigger) placed on the button itself never fires. The trigger therefore
 *     sits on a wrapping span.
 *  2. A disabled child still occupies the hit-test area and swallows the hover
 *     without propagating it, so the wrapper would not see it either. Giving the
 *     disabled button `pointer-events: none` lets hit-testing fall through to
 *     the span. This is the fix Bootstrap's own documentation prescribes.
 *
 * Using react-bootstrap's overlay rather than the native `title` also removes
 * the browser's ~1s hover delay, which is far too slow for a hint that is
 * explaining why a student cannot proceed.
 */
export default function HintButton({
    level, className, icon, label, tooltip, disabled, loading, onRequest,
}: Props) {
    return (
        <OverlayTrigger
            placement="top"
            delay={{ show: 100, hide: 50 }}
            overlay={<Tooltip id={`hint-tooltip-${level}`}>{tooltip}</Tooltip>}
        >
            <span className="d-inline-block">
                <button
                    className={className}
                    disabled={disabled}
                    style={disabled ? { pointerEvents: "none" } : undefined}
                    onClick={async (e) => {
                        e.preventDefault();
                        await onRequest(level);
                    }}
                >
                    {loading ? (
                        <span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true" />
                    ) : (
                        <span className="me-1">{icon}</span>
                    )}
                    {label}
                </button>
            </span>
        </OverlayTrigger>
    );
}
