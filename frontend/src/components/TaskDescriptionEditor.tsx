import { useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api";

/** Mirrors TaskPersistenceService.MAX_TASK_DESCRIPTION_LENGTH on the backend. */
const MAX_TASK_DESCRIPTION_LENGTH = 5000;

interface Props {
    sequenceId: string;
    taskDescription: string | undefined;
    /** Owner of the sequence or admin. Everyone else just reads the description. */
    canEdit: boolean;
    onSaved: (taskDescription: string | undefined) => void;
}

/** The sequence's task description in the teacher view, editable in place. */
export default function TaskDescriptionEditor({ sequenceId, taskDescription, canEdit, onSaved }: Props) {
    const { t } = useTranslation();
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    function startEditing() {
        setDraft(taskDescription ?? "");
        setError("");
        setEditing(true);
    }

    async function save() {
        setSaving(true);
        setError("");
        try {
            const { data } = await api.put(`/sequence/${sequenceId}/task-description`, { taskDescription: draft });
            onSaved(data?.taskDescription ?? undefined);
            setEditing(false);
        } catch (e) {
            setError(apiErrorMessage(e, t("sequence.taskDescriptionSaveError")));
        } finally {
            setSaving(false);
        }
    }

    return (
        <div>
            <div className="d-flex align-items-center gap-2">
                <strong>{t("sequence.taskDescription")}: </strong>
                {canEdit && !editing && (
                    <button
                        className="btn btn-sm btn-link text-secondary p-0"
                        title={t("sequence.taskDescriptionEdit")}
                        aria-label={t("sequence.taskDescriptionEdit")}
                        onClick={startEditing}
                    >
                        <i className="bi bi-pencil"></i>
                    </button>
                )}
            </div>

            {canEdit && editing ? (
                <form className="mt-2 mb-3" onSubmit={(e) => { e.preventDefault(); save(); }}>
                    <textarea
                        className="form-control"
                        rows={4}
                        autoFocus
                        value={draft}
                        maxLength={MAX_TASK_DESCRIPTION_LENGTH}
                        aria-label={t("sequence.taskDescription")}
                        onChange={(e) => setDraft(e.target.value)}
                        onKeyDown={(e) => { if (e.key === "Escape") setEditing(false); }}
                        disabled={saving}
                    />
                    <div className="d-flex gap-2 mt-2">
                        <button type="submit" className="btn btn-sm btn-primary" disabled={saving}>
                            {saving
                                ? <span className="spinner-border spinner-border-sm" role="status" />
                                : t("common.save")}
                        </button>
                        <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setEditing(false)} disabled={saving}>
                            {t("common.cancel")}
                        </button>
                    </div>
                    {error && <div className="text-danger small mt-1">{error}</div>}
                </form>
            ) : (
                taskDescription && <p style={{ whiteSpace: "pre-wrap" }}>{taskDescription}</p>
            )}
        </div>
    );
}
