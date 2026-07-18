import type { SequenceTag, SequenceTagSource } from "../types/models";

/**
 * AP19 Teil A (A11): A reusable tag chip with source-aware styling.
 *
 * <p>Sources are color-coded so teachers can tell at a glance where a tag came from:
 * <ul>
 *   <li><b>manual</b> (green) — "this teacher set it deliberately"</li>
 *   <li><b>llm</b> (blue) — "inferred by the LLM; may be noisy"</li>
 *   <li><b>auto</b> (grey) — "derived structurally (e.g. for-loop, recursion)"</li>
 *   <li><b>active</b> override (primary) — used when the chip represents an active filter</li>
 * </ul>
 *
 * <p>The component is click-safe for three independent actions:
 * <ul>
 *   <li>{@code onClick} — whole chip is clickable (e.g. toggle filter)</li>
 *   <li>{@code onRemove} — shows an X button that fires its own handler, {@code stopPropagation} so it
 *       never triggers {@code onClick}</li>
 *   <li>default — just a display badge with a tooltip</li>
 * </ul>
 */
export interface TagChipProps {
    tag: SequenceTag;
    /** When true, the chip is rendered in the primary "active filter" color. */
    active?: boolean;
    /** Size preset — sm matches Bootstrap's .badge default; md is slightly larger. */
    size?: "sm" | "md";
    /** If provided, the whole chip becomes a button that fires this handler. */
    onClick?: () => void;
    /** If provided, shows an X button at the end that fires this handler (stops propagation). */
    onRemove?: () => void;
    /** Optional extra class names for one-off styling. */
    className?: string;
    /**
     * AP19 Teil A (A13): Apply the {@code tag-chip-fade-in} transition on mount. Intended for
     * LLM tags that arrive asynchronously via {@link useProgressiveTags}, so they visibly slide
     * in instead of popping. Respects {@code prefers-reduced-motion}.
     */
    fadeIn?: boolean;
}

export default function TagChip({
    tag,
    active = false,
    size = "sm",
    onClick,
    onRemove,
    className = "",
    fadeIn = false,
}: TagChipProps) {
    const colorClass = active ? "text-bg-primary" : sourceColor(tag.source);
    const isInteractive = !!onClick;

    // Low-confidence LLM tags get a dashed border as a gentle "uncertain" hint.
    const uncertain =
        tag.source === "llm" && tag.confidence != null && tag.confidence < 0.7;

    const title = tooltipText(tag);

    // The chip is always a <button> when onClick is set — otherwise a <span>
    // so assistive tech reads it as static text.
    const commonProps = {
        className: [
            "badge",
            colorClass,
            "border-0",
            "d-inline-flex",
            "align-items-center",
            "gap-1",
            size === "md" ? "fs-6" : "",
            uncertain ? "tag-chip-uncertain" : "",
            fadeIn ? "tag-chip-fade-in" : "",
            className,
        ]
            .filter(Boolean)
            .join(" "),
        title,
        style: {
            cursor: isInteractive ? "pointer" : "default",
            fontWeight: 500 as const,
            fontSize: size === "md" ? "0.9rem" : "0.78rem",
            ...(uncertain
                ? { borderStyle: "dashed", borderWidth: "1px", borderColor: "currentColor" }
                : {}),
        },
    };

    const content = (
        <>
            {/* Source icon — only shown when it helps (llm + manual). auto is grey & self-evident. */}
            {tag.source === "llm" && (
                <i className="bi bi-stars" aria-hidden="true" style={{ fontSize: "0.7em" }}></i>
            )}
            {tag.source === "manual" && (
                <i
                    className="bi bi-person-fill"
                    aria-hidden="true"
                    style={{ fontSize: "0.7em" }}
                ></i>
            )}
            <span>{tag.tagName}</span>
            {tag.source === "llm" && tag.confidence != null && (
                <span
                    className="opacity-75"
                    style={{ fontSize: "0.7em" }}
                    aria-hidden="true"
                >
                    {Math.round(tag.confidence * 100)}%
                </span>
            )}
            {onRemove && (
                <button
                    type="button"
                    className="btn-close btn-close-white"
                    onClick={(e) => {
                        e.stopPropagation();
                        onRemove();
                    }}
                    aria-label={`Remove ${tag.tagName}`}
                    style={{
                        fontSize: "0.55em",
                        padding: 0,
                        marginLeft: "2px",
                        filter: active ? "none" : "invert(0.2)",
                    }}
                ></button>
            )}
        </>
    );

    if (isInteractive) {
        return (
            <button type="button" {...commonProps} onClick={onClick}>
                {content}
            </button>
        );
    }
    return <span {...commonProps}>{content}</span>;
}

/** Bootstrap contextual class for a given tag source. */
function sourceColor(source: SequenceTagSource): string {
    switch (source) {
        case "manual":
            return "text-bg-success";
        case "llm":
            return "text-bg-info";
        case "auto":
        default:
            return "text-bg-secondary";
    }
}

/** Rich tooltip — shows source + confidence percentage where applicable. */
function tooltipText(tag: SequenceTag): string {
    const parts: string[] = [];
    parts.push(`source=${tag.source}`);
    if (tag.source === "llm" && tag.confidence != null) {
        parts.push(`confidence=${(tag.confidence * 100).toFixed(0)}%`);
    }
    return parts.join(", ");
}
