import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { WorksheetVisibility } from "../types/models";
import {
    VISIBILITY_OPTIONS,
    visibilityHintKey,
    visibilityIcon,
    visibilityLabelKey,
} from "../lib/worksheetVisibility";

interface Props {
    value: WorksheetVisibility;
    /** Resolves once the change is persisted; rejects to leave the previous value in place. */
    onChange: (next: WorksheetVisibility) => Promise<void>;
    disabled?: boolean;
}

/**
 * Picks how far a worksheet reaches: owner only, by link, or listed in the marketplace.
 *
 * <p>Three radio buttons rather than a "publish" switch, because the states are not two. Every
 * worksheet has always been readable by link, so a switch would have had to either hide that or
 * misrepresent it — and a teacher who has already sent a colleague a link deserves to see that
 * this is a real state their worksheet is in, not an off position.
 *
 * <p>The selection is optimistic and reverts if the request fails, so a lost connection cannot
 * leave the control claiming a worksheet is published when it is not.
 */
export default function VisibilityToggle({ value, onChange, disabled = false }: Props) {
    const { t } = useTranslation();
    const [pending, setPending] = useState<WorksheetVisibility | null>(null);
    const [error, setError] = useState(false);

    const shown = pending ?? value;

    async function select(next: WorksheetVisibility) {
        // The fieldset already stops a real browser from getting here, but not every caller of
        // this component is a browser: a programmatic change event would otherwise slip past
        // and publish a worksheet while the control is meant to be inert.
        if (disabled || next === value || pending) return;
        setPending(next);
        setError(false);
        try {
            await onChange(next);
        } catch {
            setError(true);
        } finally {
            setPending(null);
        }
    }

    return (
        <fieldset className="border rounded p-3 mb-3" disabled={disabled || pending !== null}>
            <legend className="fs-6 fw-semibold px-2 mb-2" style={{ width: "auto" }}>
                <i className="bi bi-eye me-1" aria-hidden="true"></i>
                {t("worksheet.visibility.legend")}
            </legend>

            {VISIBILITY_OPTIONS.map((option) => (
                <div className="form-check mb-2" key={option}>
                    <input
                        className="form-check-input"
                        type="radio"
                        name="worksheet-visibility"
                        id={`visibility-${option}`}
                        checked={shown === option}
                        onChange={() => void select(option)}
                    />
                    <label className="form-check-label" htmlFor={`visibility-${option}`}>
                        <span className="fw-medium">
                            <i className={`bi ${visibilityIcon(option)} me-1`} aria-hidden="true"></i>
                            {t(visibilityLabelKey(option))}
                        </span>
                        <span className="d-block small text-muted">{t(visibilityHintKey(option))}</span>
                    </label>
                </div>
            ))}

            {pending && (
                <div className="small text-muted" role="status">
                    <span className="spinner-border spinner-border-sm me-1" aria-hidden="true" />
                    {t("worksheet.visibility.saving")}
                </div>
            )}
            {error && (
                <div className="small text-danger" role="alert">
                    {t("worksheet.visibility.error")}
                </div>
            )}
        </fieldset>
    );
}
