import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import type { LibraryItem } from "../types/models";
import TagChip from "./TagChip";

/**
 * AP19 Teil A (A15): "You might also like…" panel shown beneath the tasks on a sequence
 * detail page (teacher view only — the Library is teacher-facing). Renders up to N compact
 * cards ranked by weighted tag overlap via {@code GET /library/sequences/{id}/similar}.
 *
 * <p>Design choices:
 * <ul>
 *   <li>Shown only when results are non-empty — an empty panel is worse than no panel.</li>
 *   <li>Compact cards (language + title + ≤3 tag chips) — this is discovery UX, not a
 *       full Library grid. The teacher can still click through to the full sequence page.</li>
 *   <li>Fetched once per sequenceId; errors are silent (this is a non-essential enhancement).</li>
 * </ul>
 */
export interface SimilarSequencesPanelProps {
    /** Source sequence — we exclude it from the results server-side. */
    sequenceId: string;
    /** Max number of neighbours to show. Server clamps to 1..10. */
    limit?: number;
}

export default function SimilarSequencesPanel({
    sequenceId,
    limit = 3,
}: SimilarSequencesPanelProps) {
    const { t } = useTranslation();
    const [items, setItems] = useState<LibraryItem[] | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        api.get<LibraryItem[]>(`/library/sequences/${sequenceId}/similar`, {
            params: { limit },
        })
            .then((resp) => {
                if (!cancelled) setItems(resp.data ?? []);
            })
            .catch(() => {
                if (!cancelled) setItems([]);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [sequenceId, limit]);

    if (loading) {
        // Match the height the real panel will take so there's no big layout jump.
        return (
            <div className="mt-4" aria-busy="true">
                <h5 className="mb-2">
                    <i className="bi bi-stars me-2"></i>
                    {t("sequence.similarTitle", "You might also like")}
                </h5>
                <div className="d-flex gap-2 py-3">
                    {Array.from({ length: limit }).map((_, i) => (
                        <div
                            key={i}
                            className="flex-fill tag-chip-skeleton"
                            style={{ height: "5.5rem", borderRadius: "0.5rem" }}
                        ></div>
                    ))}
                </div>
            </div>
        );
    }

    if (!items || items.length === 0) return null;

    return (
        <div className="mt-4">
            <h5 className="mb-2">
                <i className="bi bi-stars me-2"></i>
                {t("sequence.similarTitle", "You might also like")}
            </h5>
            <div className="row g-2">
                {items.map((item) => (
                    <div className="col-md-4" key={item.sequenceId}>
                        <SimilarCard item={item} />
                    </div>
                ))}
            </div>
        </div>
    );
}

/** Lightweight card — enough to evaluate relevance, not to fully preview. */
function SimilarCard({ item }: { item: LibraryItem }) {
    const { t } = useTranslation();
    const title =
        item.taskDescription?.trim() ||
        item.semanticSummary?.trim() ||
        t("library.untitled");

    return (
        <Link
            to={`/sequence/${item.sequenceId}`}
            className="card h-100 shadow-sm text-decoration-none text-body"
            style={{ transition: "transform 0.15s ease" }}
            onMouseEnter={(e) => (e.currentTarget.style.transform = "translateY(-2px)")}
            onMouseLeave={(e) => (e.currentTarget.style.transform = "translateY(0)")}
        >
            <div className="card-body p-3">
                <div className="d-flex align-items-center gap-2 mb-2">
                    <span className="badge text-bg-primary text-uppercase small">
                        {item.programmingLanguage || "—"}
                    </span>
                    <span className="badge text-bg-light text-muted small">
                        {t("library.tierBadge", { count: item.tierCount })}
                    </span>
                    {item.alreadyInMyWorksheet && (
                        <i
                            className="bi bi-check-circle-fill text-success ms-auto"
                            title={t("library.alreadyInLibraryTooltip") as string}
                            aria-label={t("library.alreadyInLibrary") as string}
                        ></i>
                    )}
                </div>
                <h6 className="card-title text-truncate" title={title} style={{ fontSize: "0.95rem" }}>
                    {title}
                </h6>
                {item.tags.length > 0 && (
                    <div className="d-flex flex-wrap gap-1 mt-2">
                        {item.tags.slice(0, 3).map((tag) => (
                            <TagChip key={`${tag.tagName}-${tag.source}`} tag={tag} />
                        ))}
                        {item.tags.length > 3 && (
                            <span className="badge text-bg-light text-muted small">
                                +{item.tags.length - 3}
                            </span>
                        )}
                    </div>
                )}
            </div>
        </Link>
    );
}
