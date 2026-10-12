import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { Task } from "../types/models";
import { api, apiErrorMessage } from "../lib/api";
import { MAX_TASK_TITLE_LENGTH, taskName } from "../lib/taskName";

interface Props {
    task: Task;
    /** Owner of the sequence or admin. Everyone else just sees the name. */
    canEdit: boolean;
    onRenamed: (title: string | null) => void;
}

/**
 * A task's heading on the sequence page, renamable in place. Saving an empty name clears the
 * custom one, so the task falls back to its numbered default.
 */
export default function TaskTitleEditor({ task, canEdit, onRenamed }: Props) {
    const { t } = useTranslation();
    const [editing, setEditing] = useState(false);
    const [draft, setDraft] = useState("");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    function startEditing() {
        setDraft(task.title ?? "");
        setError("");
        setEditing(true);
    }

    async function save() {
        setSaving(true);
        setError("");
        try {
            const { data } = await api.patch(`/tasks/${task.id}/title`, { title: draft });
            onRenamed(data?.title ?? null);
            setEditing(false);
        } catch (e) {
            setError(apiErrorMessage(e, t("sequence.renameTaskError")));
        } finally {
            setSaving(false);
        }
    }

    if (canEdit && editing) {
        return (
            <form
                className="flex-grow-1 me-2"
                style={{ maxWidth: 520 }}
                onSubmit={(e) => { e.preventDefault(); save(); }}
            >
                <div className="input-group input-group-sm">
                    <input
                        className="form-control"
                        autoFocus
                        value={draft}
                        maxLength={MAX_TASK_TITLE_LENGTH}
                        placeholder={t("sequence.tier", { level: task.tierLevel })}
                        aria-label={t("sequence.renameTask")}
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
                <div className="form-text">{t("sequence.renameTaskHint")}</div>
                {error && <div className="text-danger small mt-1">{error}</div>}
            </form>
        );
    }

    return (
        <div className="d-flex align-items-baseline gap-2">
            <h3 className="m-0">{taskName(task, t)}</h3>
            {task.title?.trim() && (
                <span className="text-muted small">{t("sequence.tier", { level: task.tierLevel })}</span>
            )}
            {canEdit && (
                <button
                    className="btn btn-sm btn-link text-secondary p-0"
                    title={t("sequence.renameTask")}
                    aria-label={t("sequence.renameTask")}
                    onClick={startEditing}
                >
                    <i className="bi bi-pencil"></i>
                </button>
            )}
        </div>
    );
}
