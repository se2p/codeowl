import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api.ts";
import type { StudentProgressDetail } from "../types/models.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import StudentTaskStateModal from "../components/StudentTaskStateModal.tsx";

/** Statements are the whole problem text; a table row only needs enough to recognise the task. */
const STATEMENT_PREVIEW_LENGTH = 100;

function previewStatement(statement: string): string {
    if (statement.length <= STATEMENT_PREVIEW_LENGTH) return statement;
    return statement.slice(0, STATEMENT_PREVIEW_LENGTH).trimEnd() + "…";
}

export default function StudentProgressPage() {
    const { t } = useTranslation();
    usePageTitle(t("studentProgress.pageTitle"));
    const { id: classId, studentId } = useParams<{ id: string; studentId: string }>();

    const [detail, setDetail] = useState<StudentProgressDetail | null>(null);
    /** The task whose saved arrangement the teacher asked to see, or null for no dialog. */
    const [stateTaskId, setStateTaskId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                setLoading(true);
                const res = await api.get<StudentProgressDetail>(
                    `/classes/${classId}/students/${studentId}/progress`
                );
                if (!cancelled) setDetail(res.data);
            } catch (e) {
                if (!cancelled) setError(apiErrorMessage(e, t("studentProgress.errorLoading")));
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => { cancelled = true; };
    }, [classId, studentId]);

    if (loading) {
        return (
            <div className="container py-5 text-center">
                <div className="spinner-border" role="status">
                    <span className="visually-hidden">{t("classDetail.loading")}</span>
                </div>
            </div>
        );
    }

    if (error || !detail) {
        return (
            <div className="container py-5">
                <div className="alert alert-danger">{error || t("studentProgress.errorLoading")}</div>
                <Link to={`/classes/${classId}`} className="btn btn-outline-secondary">
                    {t("classDetail.back")}
                </Link>
            </div>
        );
    }

    return (
        <div className="container py-4" style={{ maxWidth: 900 }}>
            <Link to={`/classes/${classId}`} className="text-decoration-none mb-3 d-inline-block">
                <i className="bi bi-arrow-left me-1"></i> {t("studentProgress.backToClass")}
            </Link>

            <h2 className="mb-4">
                <i className="bi bi-person-lines-fill me-2"></i>
                {t("studentProgress.title", { name: detail.studentName })}
            </h2>

            {detail.worksheets.length === 0 ? (
                <p className="text-muted">{t("studentProgress.noWorksheets")}</p>
            ) : (
                detail.worksheets.map((ws) => (
                    <div className="card mb-4" key={ws.worksheetId}>
                        <div className="card-header">
                            <i className="bi bi-file-earmark-text me-2"></i>
                            <strong>{ws.name}</strong>
                        </div>
                        <div className="card-body">
                            {ws.sequences.length === 0 ? (
                                <p className="text-muted mb-0">{t("studentProgress.noSequences")}</p>
                            ) : (
                                ws.sequences.map((seq) => (
                                    <div key={seq.sequenceId} className="mb-4">
                                        <h6 className="mb-1">
                                            {t("studentProgress.sequenceHeading", { index: seq.index })}
                                            {seq.concepts && seq.concepts.length > 0 && (
                                                <span className="text-muted fw-normal ms-2 small">
                                                    {seq.concepts.join(", ")}
                                                </span>
                                            )}
                                        </h6>
                                        <div className="table-responsive">
                                            <table className="table table-sm table-hover mb-0">
                                                <thead>
                                                    <tr>
                                                        <th style={{ width: 70 }}>
                                                            {t("studentProgress.tierHeader")}
                                                        </th>
                                                        <th>{t("studentProgress.statementHeader")}</th>
                                                        <th style={{ width: 100 }}>
                                                            {t("studentProgress.attemptsHeader")}
                                                        </th>
                                                        <th style={{ width: 110 }}>
                                                            {t("studentProgress.statusHeader")}
                                                        </th>
                                                        <th style={{ width: 90 }}>
                                                            {t("studentProgress.stateHeader")}
                                                        </th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    {seq.tasks.map((task) => (
                                                        <tr key={task.taskId}>
                                                            <td>{task.tierLevel}</td>
                                                            <td title={task.statement}>
                                                                {previewStatement(task.statement)}
                                                            </td>
                                                            <td>{task.attempts}</td>
                                                            <td>
                                                                {task.solved ? (
                                                                    <span className="badge bg-success">
                                                                        {t("studentProgress.solved")}
                                                                    </span>
                                                                ) : task.attempts > 0 ? (
                                                                    <span className="badge bg-warning text-dark">
                                                                        {t("studentProgress.attempted")}
                                                                    </span>
                                                                ) : (
                                                                    <span className="badge bg-secondary">
                                                                        {t("studentProgress.notAttempted")}
                                                                    </span>
                                                                )}
                                                            </td>
                                                            <td>
                                                                {/*
                                                                  * Never gated on attempts. Those count
                                                                  * Feedback clicks, while the arrangement is
                                                                  * autosaved independently, so a student with
                                                                  * no attempts can still have left plenty
                                                                  * behind - the case this is most useful for.
                                                                  */}
                                                                <button
                                                                    type="button"
                                                                    className="btn btn-sm btn-outline-secondary"
                                                                    title={t("studentProgress.stateButtonTitle")}
                                                                    onClick={() => setStateTaskId(task.taskId)}
                                                                >
                                                                    {t("studentProgress.stateButton")}
                                                                </button>
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                ))
            )}

            {stateTaskId && classId && studentId && (
                <StudentTaskStateModal
                    classId={classId}
                    studentId={studentId}
                    taskId={stateTaskId}
                    studentName={detail?.studentName ?? ""}
                    onClose={() => setStateTaskId(null)}
                />
            )}
        </div>
    );
}
