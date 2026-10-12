import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api.ts";
import { renderGapLine } from "../lib/gapMarkers.tsx";
import { toDisplayLines } from "../lib/solutionLines.ts";
import type { StudentTaskState } from "../types/models.ts";

/** Pixels of margin per indent level — the widget's x_indent default, see lib/readArrangement.ts. */
const INDENT_PX = 50;

interface StudentTaskStateModalProps {
    classId: string;
    studentId: string;
    taskId: string;
    /** Shown in the title, so the teacher knows whose work they are looking at. */
    studentName: string;
    onClose: () => void;
}

/**
 * Shows what one student last built for one task, beside the solution they were building towards.
 *
 * Self-contained in the same way AddToWorksheetModal is: given the three ids it fetches its own
 * data and owns its loading and error state, so the progress page only has to remember which row
 * was clicked.
 *
 * The arrangement is rendered directly rather than through restoreSubmission — that one relocates
 * `<li>` nodes a live js-parsons widget dealt, which a read-only view has none of. Here
 * `blockOrder[i]` is already the visible text and `indentLevels[i]` the level.
 */
export default function StudentTaskStateModal({
    classId,
    studentId,
    taskId,
    studentName,
    onClose,
}: StudentTaskStateModalProps) {
    const { t, i18n } = useTranslation();

    const [state, setState] = useState<StudentTaskState | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        api.get<StudentTaskState>(`/classes/${classId}/students/${studentId}/tasks/${taskId}/state`)
            .then(({ data }) => { if (!cancelled) setState(data); })
            .catch((err) => { if (!cancelled) setError(apiErrorMessage(err, t("studentProgress.stateError"))); })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [classId, studentId, taskId]);

    // Escape closes, as it would for a native dialog.
    useEffect(() => {
        const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [onClose]);

    const savedAt = state?.updatedAt
        ? new Date(state.updatedAt).toLocaleString(i18n.language)
        : null;

    const codeStyle: React.CSSProperties = {
        fontFamily: "monospace",
        fontSize: "0.85rem",
        whiteSpace: "pre-wrap",
    };

    return (
        <>
            <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                <div className="modal-dialog modal-dialog-centered modal-xl modal-dialog-scrollable">
                    <div className="modal-content rounded-4 shadow">
                        <div className="modal-header">
                            <h5 className="modal-title">
                                {t("studentProgress.stateTitle", { name: studentName })}
                            </h5>
                            <button
                                type="button"
                                className="btn-close"
                                aria-label={t("common.close")}
                                onClick={onClose}
                            />
                        </div>

                        <div className="modal-body">
                            {loading && <p className="text-muted mb-0">{t("studentProgress.stateLoading")}</p>}
                            {error && <div className="alert alert-danger mb-0">{error}</div>}

                            {!loading && !error && state && (
                                <>
                                    <p className="text-muted small">{state.statement}</p>

                                    <div className="row g-3">
                                        <div className="col-md-6">
                                            <h6 className="fw-semibold">
                                                {t("studentProgress.stateStudentColumn")}
                                            </h6>

                                            {state.hasState ? (
                                                <>
                                                    <div className="bg-light p-2 rounded" style={codeStyle}>
                                                        {state.blockOrder.length === 0 ? (
                                                            <span className="text-muted fst-italic">
                                                                {t("studentProgress.stateEmptySolution")}
                                                            </span>
                                                        ) : (
                                                            state.blockOrder.map((text, i) => (
                                                                <div
                                                                    key={i}
                                                                    style={{
                                                                        marginLeft: (state.indentLevels[i] ?? 0) * INDENT_PX,
                                                                    }}
                                                                >
                                                                    {text}
                                                                </div>
                                                            ))
                                                        )}
                                                    </div>
                                                    <p className="small text-muted mt-2 mb-0">
                                                        {state.solved
                                                            ? t("studentProgress.stateSolved")
                                                            : t("studentProgress.stateInProgress")}
                                                        {savedAt && ` · ${t("studentProgress.stateSavedAt", { when: savedAt })}`}
                                                    </p>
                                                </>
                                            ) : (
                                                <p className="text-muted fst-italic mb-0">
                                                    {t("studentProgress.stateNothingSaved")}
                                                </p>
                                            )}
                                        </div>

                                        <div className="col-md-6">
                                            <h6 className="fw-semibold">
                                                {t("studentProgress.stateSolutionColumn")}
                                            </h6>
                                            <div className="bg-light p-2 rounded" style={codeStyle}>
                                                {toDisplayLines(state.solution).map((line, i) => (
                                                    <div key={i}>{renderGapLine(line)}</div>
                                                ))}
                                            </div>
                                        </div>
                                    </div>

                                    {/*
                                      * An arrangement is saved as the visible text of each block, and a
                                      * gap input or a split chip contributes none. Saying so beats letting
                                      * a teacher read a missing answer as a blank the student left.
                                      */}
                                    {state.hasState && state.solution?.includes("$$blank") && (
                                        <p className="small text-muted mt-3 mb-0">
                                            {t("studentProgress.stateGapNote")}
                                        </p>
                                    )}
                                </>
                            )}
                        </div>

                        <div className="modal-footer">
                            <button type="button" className="btn btn-secondary" onClick={onClose}>
                                {t("common.close")}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            <div className="modal-backdrop fade show"></div>
        </>
    );
}
