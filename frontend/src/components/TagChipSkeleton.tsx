/**
 * AP19 Teil A (A13): Pulsing placeholder chip shown while the LLM semantic tagger is still
 * running on a freshly-generated sequence.
 *
 * <p>Visually matches {@link TagChip} at the same size preset so the layout doesn't shift when
 * the real tag arrives. Uses a CSS keyframe animation ({@code tag-skeleton-pulse}) defined in
 * {@code index.css} together with the {@code tag-chip-fade-in} transition that new LLM chips
 * use for their first render.
 */
export interface TagChipSkeletonProps {
    /** Size preset — must match neighbouring TagChips. */
    size?: "sm" | "md";
    /** Optional placeholder width in ch units (defaults to 6 for "…"-like feel). */
    widthCh?: number;
    /** Accessible label so screen readers don't just announce empty chips. */
    ariaLabel?: string;
}

export default function TagChipSkeleton({
    size = "sm",
    widthCh = 6,
    ariaLabel = "Loading semantic tag",
}: TagChipSkeletonProps) {
    return (
        <span
            className="badge border-0 d-inline-flex align-items-center gap-1 tag-chip-skeleton"
            role="status"
            aria-label={ariaLabel}
            style={{
                fontSize: size === "md" ? "0.9rem" : "0.78rem",
                fontWeight: 500,
                minWidth: `${widthCh}ch`,
                // Height matches the real chip (badge + padding) so the line doesn't jump.
                padding: "0.35em 0.65em",
            }}
        >
            <i className="bi bi-stars" aria-hidden="true" style={{ fontSize: "0.7em", opacity: 0.6 }}></i>
            <span className="tag-chip-skeleton-bar" aria-hidden="true">&nbsp;</span>
        </span>
    );
}
