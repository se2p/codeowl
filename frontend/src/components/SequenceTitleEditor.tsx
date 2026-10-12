import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api";
import { MAX_SEQUENCE_TITLE_LENGTH } from "../lib/sequenceName";

interface Props {
    sequenceId: string;
    title: string | null | undefined;
    /** Owner of the sequence or admin. Everyone else just sees the heading. */
    canEdit: boolean;
    onRenamed: (title: string | null) => void;
}

/**
 * The sequence page's heading, renamable in place. Saving an empty name clears the custom
 * one, so the page falls back to the generic "Tiered Task Sequence" heading.
 */
export default function SequenceTitleEditor({ sequenceId, title, canEdit, onRenamed }: Props) {
    const { t } = useTranslation();
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    function startEditing() {
        setDraft(title ?? "");
        setError("");
        setEditing(true);
    }

    async function save() {
        setSaving(true);
        setError("");
        try {
            const { data } = await api.patch(`/sequence/${sequenceId}/title`, { title: draft });
            onRenamed(data?.title ?? null);
            setEditing(false);
        } catch (e) {
            setError(apiErrorMessage(e, t("sequence.renameSequenceError")));
        } finally {
            setSaving(false);
        }
    }

    if (canEdit && editing) {
        return (
            <form
                className="flex-grow-1 me-2"
                style={{ maxWidth: 640 }}
                onSubmit={(e) => { e.preventDefault(); save(); }}
            >
                <div className="input-group">
                    <input
                        className="form-control"
                        autoFocus
                        value={draft}
                        maxLength={MAX_SEQUENCE_TITLE_LENGTH}
                        placeholder={t("sequence.title")}
                        aria-label={t("sequence.renameSequence")}
                        onChange={(e) => setDraft(e.target.value)}
                        onKeyDown={(e) => { if (e.key === "Escape") setEditing(false); }}
                        disabled={saving}
                    />
                    <button type="submit" className="btn btn-primary" disabled={saving}>
                        {saving
                            ? <span className="spinner-border spinner-border-sm" role="status" />
                            : t("common.save")}
                    </button>
                    <button type="button" className="btn btn-outline-secondary" onClick={() => setEditing(false)} disabled={saving}>
                        {t("common.cancel")}
                    </button>
                </div>
                <div className="form-text">{t("sequence.renameSequenceHint")}</div>
                {error && <div className="text-danger small mt-1">{error}</div>}
            </form>
        );
    }

    return (
        <div className="d-flex align-items-baseline gap-2">
            <h1 className="m-0">{title?.trim() || t("sequence.title")}</h1>
            {canEdit && (
                <button
                    className="btn btn-sm btn-link text-secondary p-0"
                    title={t("sequence.renameSequence")}
                    aria-label={t("sequence.renameSequence")}
                    onClick={startEditing}
                >
                    <i className="bi bi-pencil"></i>
                </button>
            )}
        </div>
    );
}
