import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import type { MarketplaceItem } from "../types/models";
import { renderHighlight } from "../lib/highlight";

interface Props {
    item: MarketplaceItem;
    /** Whether the viewer may copy at all — students and anonymous visitors may not. */
    canCopy: boolean;
    /** Resolves once the copy exists; the page navigates to it. */
    onCopy: (item: MarketplaceItem) => void;
    copying: boolean;
}

/**
 * One worksheet in the marketplace grid.
 *
 * <p>Presentational: every decision it renders is made by the page and handed down as a prop, so
 * it can be tested by rendering it rather than by standing up a page around it. That is the one
 * thing to keep from going the way of `LibraryPage`, whose card is 260 lines inside a
 * 1100-line file and has no tests at all.
 *
 * <p>The card leads with what a teacher actually judges a worksheet by — how many sequences and
 * in which languages — because the name alone ("Blatt 3") usually says nothing.
 */
export default function WorksheetCard({ item, canCopy, onCopy, copying }: Props) {
    const { t } = useTranslation();

    return (
        <div className="card h-100 shadow-sm">
            <div className="card-body d-flex flex-column">
                <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
                    {item.languages.map((language) => (
                        <span key={language} className="badge text-bg-primary text-uppercase">
                            {language}
                        </span>
                    ))}
                    <span className="badge text-bg-light text-muted">
                        {t("marketplace.sequenceCount", { count: item.sequenceCount })}
                    </span>
                    {item.copyCount > 0 && (
                        <span
                            className="badge text-bg-light text-muted"
                            title={t("marketplace.copyCountTooltip") as string}
                        >
                            <i className="bi bi-files me-1" aria-hidden="true"></i>
                            {item.copyCount}
                        </span>
                    )}
                    {item.alreadyCopied && (
                        <span className="badge text-bg-success-subtle text-success-emphasis">
                            <i className="bi bi-check-circle-fill me-1" aria-hidden="true"></i>
                            {t("marketplace.alreadyCopied")}
                        </span>
                    )}
                </div>

                <h5 className="card-title mb-1" style={{ fontSize: "1rem" }}>
                    {item.name || t("marketplace.untitled")}
                </h5>

                {item.highlight ? (
                    // renderHighlight escapes the payload before turning the markers into <mark>,
                    // so text a teacher wrote cannot break out into markup.
                    <p
                        className="card-text small text-muted mb-2"
                        dangerouslySetInnerHTML={{ __html: renderHighlight(item.highlight) }}
                    />
                ) : item.description ? (
                    <p className="card-text small text-muted mb-2">{item.description}</p>
                ) : null}

                <div className="mt-auto d-flex gap-2 pt-2">
                    <Link
                        to={`/worksheets/${item.worksheetId}`}
                        // Tells the worksheet where the visitor came from, so it can offer a
                        // way back to this list with its filters intact.
                        state={{ from: "marketplace", search: window.location.search }}
                        className="btn btn-sm btn-outline-primary flex-fill"
                    >
                        <i className="bi bi-eye me-1" aria-hidden="true"></i>
                        {t("marketplace.open")}
                    </Link>
                    {canCopy && (
                        <button
                            className="btn btn-sm btn-outline-success"
                            onClick={() => onCopy(item)}
                            disabled={copying}
                            title={
                                item.alreadyCopied
                                    ? (t("marketplace.copyAgainTooltip") as string)
                                    : (t("marketplace.copyTooltip") as string)
                            }
                        >
                            {copying ? (
                                <>
                                    <span className="spinner-border spinner-border-sm me-1" aria-hidden="true" />
                                    {t("marketplace.copying")}
                                </>
                            ) : (
                                <>
                                    <i className="bi bi-clipboard-plus me-1" aria-hidden="true"></i>
                                    {t("marketplace.copy")}
                                </>
                            )}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
