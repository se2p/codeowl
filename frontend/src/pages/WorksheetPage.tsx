import { useEffect, useState } from "react";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api.ts";
import type { Worksheet, ClassInfo } from "../types/models.ts";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { ListGroup } from "react-bootstrap";
import 'bootstrap/dist/css/bootstrap.min.css';
import { useAuth } from "../context/AuthContext.tsx";
import type { ExportableWorksheet, ExportableSequence } from "../lib/export.ts";
import {
    exportWorksheetJSON,
    exportWorksheetCSV,
    exportWorksheetPDF,
} from "../lib/export.ts";

export default function WorksheetPage() {
    usePageTitle("Worksheet View");
    const { t } = useTranslation();
    const { sheetId } = useParams<{ sheetId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const justCopied = (location.state as any)?.justCopied === true;
    const { user, isLoggedIn, isTeacher, isAdmin } = useAuth();
    const [worksheet, setWorksheet] = useState<Worksheet | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [showModal, setShowModal] = useState(false);
    const [sequenceId, setSequenceId] = useState("");
    const [adding, setAdding] = useState(false);
    const [modalError, setModalError] = useState<string | null>(null);
    const [batchMode, setBatchMode] = useState(false);
    const [batchInput, setBatchInput] = useState("");
    const [copied, setCopied] = useState(false);

    // Role-based teacher view: show toggle for owners and admins
    const isOwnerOrAdmin = isLoggedIn && worksheet && user && (worksheet.ownerId === user.userId || isAdmin);
    const canToggleTeacherView = !!isOwnerOrAdmin;
    const [teacherViewEnabled, setTeacherViewEnabled] = useState(false);

    // Auto-enable teacher view when worksheet loads and user is owner/admin
    useEffect(() => {
        if (isOwnerOrAdmin) {
            setTeacherViewEnabled(true);
        } else {
            setTeacherViewEnabled(false);
        }
    }, [isOwnerOrAdmin]);

    // Copy worksheet state
    const [copying, setCopying] = useState(false);

    // Assign to class state
    const [showAssignModal, setShowAssignModal] = useState(false);
    const [myClasses, setMyClasses] = useState<ClassInfo[]>([]);
    const [loadingClasses, setLoadingClasses] = useState(false);
    const [assigningClassId, setAssigningClassId] = useState<string | null>(null);
    const [assignError, setAssignError] = useState<string | null>(null);
    const [assignSuccess, setAssignSuccess] = useState<string | null>(null);

    // Determine if current user is the owner
    const isForeignWorksheet = isLoggedIn && worksheet && user && worksheet.ownerId !== user.userId;
    const canCopy = isLoggedIn && (isTeacher || isAdmin) && isForeignWorksheet;
    const canAssign = isLoggedIn && (isTeacher || isAdmin);
    const canEdit = teacherViewEnabled && isOwnerOrAdmin;

    // Edit worksheet state
    const [editing, setEditing] = useState(false);
    const [editName, setEditName] = useState("");
    const [editDescription, setEditDescription] = useState("");
    const [saving, setSaving] = useState(false);

    function startEditing() {
        if (!worksheet) return;
        setEditName(worksheet.name);
        setEditDescription(worksheet.description || "");
        setEditing(true);
    }

    async function handleSaveEdit() {
        if (!worksheet || !sheetId) return;
        setSaving(true);
        try {
            await api.put(`/worksheets/${sheetId}`, {
                id: sheetId,
                name: editName.trim(),
                description: editDescription.trim(),
            });
            setWorksheet({ ...worksheet, name: editName.trim(), description: editDescription.trim() });
            setEditing(false);
        } catch (err: any) {
            alert(err?.response?.data?.error || t("worksheet.errorSaving"));
        } finally {
            setSaving(false);
        }
    }

    async function handleRemoveSequence(sequenceId: string) {
        if (!window.confirm(t("worksheet.removeSequence"))) return;
        try {
            await api.delete(`/worksheets/${sheetId}/sequences/${sequenceId}`);
            const { data } = await api.get(`/worksheets/${sheetId}`);
            setWorksheet(data);
        } catch (err: any) {
            alert(err?.response?.data ?? "Error removing sequence.");
        }
    }


    async function handleCopyLink() {
        try {
            await navigator.clipboard.writeText(window.location.href);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        } catch {
            alert(t("solve.copyFailed"));
        }
    }

    async function handleCopyWorksheet() {
        if (!sheetId) return;
        setCopying(true);
        try {
            const { data } = await api.post<Worksheet>(`/worksheets/${sheetId}/copy`);
            navigate(`/worksheets/${data.id}`, { state: { justCopied: true } });
        } catch (err: any) {
            alert(err?.response?.data?.error || err?.response?.data || t("worksheet.errorSaving"));
        } finally {
            setCopying(false);
        }
    }

    async function handleOpenAssignModal() {
        setShowAssignModal(true);
        setAssignError(null);
        setAssignSuccess(null);
        setLoadingClasses(true);
        try {
            const { data } = await api.get<ClassInfo[]>("/classes");
            setMyClasses(data);
        } catch (err: any) {
            setAssignError(t("classes.errorLoading"));
        } finally {
            setLoadingClasses(false);
        }
    }

    async function handleAssignToClass(classId: string) {
        setAssigningClassId(classId);
        setAssignError(null);
        try {
            await api.post(`/classes/${classId}/worksheets/${sheetId}`);
            setAssignSuccess(t("worksheet.assignSuccess"));
            setTimeout(() => setAssignSuccess(null), 3000);
        } catch (err: any) {
            setAssignError(err?.response?.data?.error || err?.response?.data || t("classDetail.errorAssigning"));
        } finally {
            setAssigningClassId(null);
        }
    }

    // ── Export ──────────────────────────────────────────────
    const [exporting, setExporting] = useState(false);

    async function resolveWorksheetForExport(): Promise<ExportableWorksheet | null> {
        if (!worksheet) return null;
        setExporting(true);
        try {
            const seqPromises = (worksheet.sequenceDTOS ?? []).map((s) =>
                api.get(`/sequence/${s.id}`).then(({ data }): ExportableSequence => ({
                    id: String(data.id),
                    programmingLanguage: String(data.programmingLanguage ?? "unknown"),
                    concepts: Array.isArray(data.concepts) ? data.concepts.map(String) : [],
                    taskDescription: data.taskDescription ?? undefined,
                    tasks: Array.isArray(data.tasks)
                        ? data.tasks.map((t: any) => ({
                            id: String(t.id),
                            statement: String(t.statement ?? ""),
                            solution: String(t.solution ?? ""),
                            tierLevel: Number(t.tierLevel ?? 0),
                            cyclomaticComplexity: Number(t.cyclomaticComplexity ?? 0),
                            programmingLanguage: String(t.programmingLanguage ?? data.programmingLanguage ?? "unknown"),
                            distractors: t.distractors ?? null,
                            distractorPlan: t.distractorPlan ?? null,
                            distractorsEnabled: t.distractorsEnabled !== false,
                        }))
                        : [],
                }))
            );
            const sequences = await Promise.all(seqPromises);
            return {
                id: worksheet.id,
                name: worksheet.name,
                description: worksheet.description,
                sequences,
            };
        } catch (err: any) {
            alert(t("worksheet.errorSaving"));
            return null;
        } finally {
            setExporting(false);
        }
    }

    async function handleExport(format: "json" | "csv" | "pdf-freeform-teacher" | "pdf-freeform-student" | "pdf-puzzle") {
        const ws = await resolveWorksheetForExport();
        if (!ws) return;
        switch (format) {
            case "json": exportWorksheetJSON(ws); break;
            case "csv": exportWorksheetCSV(ws); break;
            case "pdf-freeform-teacher": exportWorksheetPDF(ws, "freeform", true); break;
            case "pdf-freeform-student": exportWorksheetPDF(ws, "freeform", false); break;
            case "pdf-puzzle": exportWorksheetPDF(ws, "puzzle"); break;
        }
    }

    async function handleAddSequence() {
        setModalError(null);

        if (batchMode) {
            // split CSV or newline/space separated values into UUIDs
            const ids = batchInput
                .split(/[\s,;]+/)
                .map((x) => x.trim())
                .filter(Boolean);

            if (ids.length === 0) {
                setModalError("Please enter at least one UUID.");
                return;
            }

            setAdding(true);
            try {
                await api.post(`/worksheets/${sheetId}/sequences`, ids);
                const { data } = await api.get(`/worksheets/${sheetId}`);
                setWorksheet(data);
                setShowModal(false);
                setBatchInput("");
            } catch (err: any) {
                setModalError(err?.response?.data ?? "Error adding sequences.");
            } finally {
                setAdding(false);
            }
        } else {
            if (!sequenceId.trim()) {
                setModalError("Please enter a sequence UUID.");
                return;
            }

            setAdding(true);
            try {
                await api.post(`/worksheets/${sheetId}/sequences/${sequenceId.trim()}`);
                const { data } = await api.get(`/worksheets/${sheetId}`);
                setWorksheet(data);
                setShowModal(false);
                setSequenceId("");
            } catch (err: any) {
                setModalError(err?.response?.data ?? "Error adding sequence.");
            } finally {
                setAdding(false);
            }
        }
    }

    useEffect(() => {
        api.get(`/worksheets/${sheetId}`)
            .then(({ data }) => setWorksheet(data))
            .catch((err) => setError(err?.response?.data ?? err.message))
            .finally(() => setLoading(false));
    }, [sheetId]);

    if (loading) return <div className="container py-4">{t("common.loading")}</div>;
    if (error) return <div className="container py-4 text-danger">{error}</div>;
    if (!worksheet) return <div className="container py-4">{t("common.error")}</div>;

    return (
        <div className="container py-4" style={{ maxWidth: 900 }}>
            <button className="btn btn-link mb-3" onClick={() => navigate("/")}>
                &larr; {t("common.home")}
            </button>
            {canToggleTeacherView && (
                <div className="d-flex justify-content-end mb-3">
                    <button
                        className={`btn btn-sm ${teacherViewEnabled ? "btn-warning" : "btn-outline-warning"}`}
                        onClick={() => setTeacherViewEnabled(!teacherViewEnabled)}
                    >
                        {teacherViewEnabled ? t("worksheet.hideTeacherMode") : t("worksheet.teacherMode")}
                    </button>
                </div>
            )}

            {teacherViewEnabled && (
                <>
                <div
                    className="alert alert-warning text-center fw-bold mb-4"
                    style={{
                        fontSize: "1.1rem",
                        background:
                            "linear-gradient(90deg, rgba(255,193,7,0.9) 0%, rgba(255,230,150,0.9) 100%)",
                        border: "none",
                        boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                    }}
                >
                    {t("worksheet.teacherBanner")}
                </div>
                <div className="card card-body mb-3 d-flex flex-row align-items-center gap-3 flex-wrap">
                    <div className="form-check form-switch mb-0">
                        <input
                            className="form-check-input"
                            type="checkbox"
                            id="hintsEnabledToggle"
                            checked={worksheet.hintsEnabled}
                            onChange={async () => {
                                const newVal = !worksheet.hintsEnabled;
                                try {
                                    await api.put(`/worksheets/${sheetId}`, {
                                        id: sheetId,
                                        name: worksheet.name,
                                        description: worksheet.description,
                                        hintsEnabled: newVal,
                                    });
                                    setWorksheet({ ...worksheet, hintsEnabled: newVal });
                                } catch {
                                    alert(t("worksheet.errorSavingHints"));
                                }
                            }}
                        />
                        <label className="form-check-label" htmlFor="hintsEnabledToggle">
                            {worksheet.hintsEnabled ? t("worksheet.hintsEnabled") : t("worksheet.hintsDisabled")}
                        </label>
                    </div>

                    <div className="dropdown ms-auto">
                        <button
                            className="btn btn-outline-secondary btn-sm dropdown-toggle d-flex align-items-center gap-1"
                            type="button"
                            data-bs-toggle="dropdown"
                            disabled={exporting || !worksheet.sequenceDTOS?.length}
                        >
                            {exporting ? (
                                <><span className="spinner-border spinner-border-sm" /> {t("worksheet.exporting")}</>
                            ) : (
                                <>{t("worksheet.export")}</>
                            )}
                        </button>
                        <ul className="dropdown-menu">
                            <li><button className="dropdown-item" onClick={() => handleExport("json")}>{t("sequence.exportJSON")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("csv")}>{t("sequence.exportCSV")}</button></li>
                            <li><hr className="dropdown-divider" /></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-freeform-teacher")}>{t("sequence.exportPdfTeacher")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-freeform-student")}>{t("sequence.exportPdfStudent")}</button></li>
                            <li><button className="dropdown-item" onClick={() => handleExport("pdf-puzzle")}>{t("sequence.exportPdfPuzzle")}</button></li>
                        </ul>
                    </div>
                </div>
                </>
            )}
            {editing ? (
                <div className="mb-3">
                    <input
                        type="text"
                        className="form-control form-control-lg mb-2"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        placeholder={t("home.worksheetName")}
                    />
                    <textarea
                        className="form-control mb-2"
                        value={editDescription}
                        onChange={(e) => setEditDescription(e.target.value)}
                        placeholder={t("home.worksheetDesc")}
                        rows={2}
                    />
                    <div className="d-flex gap-2">
                        <button className="btn btn-success btn-sm" onClick={handleSaveEdit} disabled={saving || !editName.trim()}>
                            {saving ? t("worksheet.saving") : t("worksheet.save")}
                        </button>
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => setEditing(false)} disabled={saving}>
                            {t("worksheet.cancel")}
                        </button>
                    </div>
                </div>
            ) : (
                <div className="d-flex align-items-start gap-2">
                    <div>
                        <h1>{worksheet.name}</h1>
                        <p className="text-muted">{worksheet.description}</p>
                    </div>
                    {canEdit && (
                        <button className="btn btn-outline-secondary btn-sm mt-2" onClick={startEditing} title={t("worksheet.editName")}>
                            ✏️ {t("common.edit")}
                        </button>
                    )}
                </div>
            )}

            {/* Copy / Assign buttons for foreign worksheets */}
            {justCopied && (
                <div className="alert alert-success">
                    {t("worksheet.worksheetCopied")}
                </div>
            )}

            <div className="d-flex flex-wrap gap-2 mb-4">
                {canCopy && (
                    <button
                        className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1"
                        onClick={handleCopyWorksheet}
                        disabled={copying}
                        title={t("worksheet.copy")}
                    >
                        {copying ? (
                            <>
                                <span className="spinner-border spinner-border-sm" />
                                {t("worksheet.copying")}
                            </>
                        ) : (
                            <>
                                <i className="bi bi-copy"></i>
                                {t("worksheet.copy")}
                            </>
                        )}
                    </button>
                )}

                {canAssign && (
                    <button
                        className="btn btn-outline-success btn-sm d-flex align-items-center gap-1"
                        onClick={handleOpenAssignModal}
                        title={t("worksheet.assign")}
                    >
                        <i className="bi bi-people"></i>
                        {t("worksheet.assign")}
                    </button>
                )}
            </div>

            <h3 className="mt-4">{t("worksheet.sequences")}</h3>
            <div className="d-flex justify-content-end align-items-center gap-2 mb-3">
                <button
                    className={`btn btn-sm d-flex align-items-center gap-1 ${
                        copied ? "btn-success" : "btn-outline-secondary"
                    }`}
                    onClick={handleCopyLink}
                    title="Copies this worksheet link"
                >
                    <i className="bi bi-link-45deg"></i>
                    {copied ? t("worksheet.copied") : t("worksheet.shareWorksheet")}
                </button>

                {teacherViewEnabled && (
                    <button
                        className="btn btn-outline-secondary btn-sm"
                        onClick={() => setShowModal(true)}
                    >
                        + {t("worksheet.addSequence")}
                    </button>
                )}
            </div>
            {worksheet.sequenceDTOS?.length ? (
                <ListGroup>
                    {worksheet.sequenceDTOS.map((s, idx) => (
                        <ListGroup.Item
                            key={s.id}
                            className="d-flex justify-content-between align-items-center"
                            title={teacherViewEnabled ? `Sequence ID: ${s.id}` : undefined}
                        >
                            <div
                                style={{ cursor: "pointer" }}
                                onClick={() =>
                                    navigate(`/sequence/${s.id}`, {
                                        state: { fromWorksheet: worksheet.id },
                                    })
                                }
                            >
                                Sequence {idx + 1}
                                {teacherViewEnabled && (
                                    <small className="text-muted ms-2">
                                        ({s.id ? s.id.slice(0, 8) : "?"}...)
                                    </small>
                                )}
                            </div>

                            {teacherViewEnabled && (
                                <button
                                    className="btn btn-sm btn-outline-danger"
                                    title={t("worksheet.removeSequence")}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleRemoveSequence(s.id);
                                    }}
                                >
                                    <i className="bi bi-trash"></i>
                                </button>
                            )}
                        </ListGroup.Item>
                    ))}
                </ListGroup>
            ) : (
                <div className="alert alert-info mt-3">
                    {t("worksheet.noSequences")}
                </div>
            )}
            {/* ADD SEQUENCE MODAL */}
            {showModal && (
                <>
                    <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                        <div className="modal-dialog modal-dialog-centered">
                            <div className="modal-content rounded-4 shadow">
                                <div className="modal-header">
                                    <h5 className="modal-title">{t("worksheet.addSequenceTitle")}</h5>
                                    <button type="button" className="btn-close" onClick={() => setShowModal(false)} />
                                </div>
                                <div className="modal-body">
                                    <div className="mb-3">
                                        <div className="d-flex justify-content-between align-items-center">
                                            <label className="form-label fw-semibold mb-0">
                                                {batchMode ? t("worksheet.sequenceUuids") : t("worksheet.sequenceUuid")}
                                            </label>
                                            <button
                                                type="button"
                                                className="btn btn-sm btn-outline-secondary"
                                                onClick={() => setBatchMode(!batchMode)}
                                                disabled={adding}
                                            >
                                                {batchMode ? t("worksheet.singleInsert") : t("worksheet.batchInsert")}
                                            </button>
                                        </div>

                                        {batchMode ? (
                                            <textarea
                                                className="form-control mt-2"
                                                rows={3}
                                                value={batchInput}
                                                onChange={(e) => setBatchInput(e.target.value)}
                                                placeholder={t("worksheet.pasteUuids")}
                                                disabled={adding}
                                            />
                                        ) : (
                                            <input
                                                type="text"
                                                className="form-control mt-2"
                                                value={sequenceId}
                                                onChange={(e) => setSequenceId(e.target.value)}
                                                placeholder={t("worksheet.enterUuid")}
                                                disabled={adding}
                                            />
                                        )}
                                    </div>
                                    {modalError && <div className="alert alert-danger py-2">{modalError}</div>}
                                </div>
                                <div className="modal-footer">
                                    <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)} disabled={adding}>
                                        {t("common.cancel")}
                                    </button>
                                    <button type="button" className="btn btn-primary" onClick={handleAddSequence} disabled={adding}>
                                        {adding ? (
                                            <>
                                                <span className="spinner-border spinner-border-sm me-2" />
                                                {t("worksheet.linking")}
                                            </>
                                        ) : (
                                            t("worksheet.linkSequence")
                                        )}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-backdrop fade show"></div>
                </>
            )}

            {/* ASSIGN TO CLASS MODAL */}
            {showAssignModal && (
                <>
                    <div className="modal fade show" style={{ display: "block" }} tabIndex={-1}>
                        <div className="modal-dialog modal-dialog-centered">
                            <div className="modal-content rounded-4 shadow">
                                <div className="modal-header">
                                    <h5 className="modal-title">{t("worksheet.assignTitle")}</h5>
                                    <button type="button" className="btn-close" onClick={() => setShowAssignModal(false)} />
                                </div>
                                <div className="modal-body">
                                    {assignError && <div className="alert alert-danger py-2">{assignError}</div>}
                                    {assignSuccess && <div className="alert alert-success py-2">{assignSuccess}</div>}

                                    {loadingClasses ? (
                                        <div className="text-center py-3">
                                            <span className="spinner-border spinner-border-sm me-2" />
                                            {t("worksheet.loadingClasses")}
                                        </div>
                                    ) : myClasses.length === 0 ? (
                                        <div className="alert alert-info mb-0">
                                            {t("worksheet.noClasses")}
                                            <br />
                                            <button
                                                className="btn btn-sm btn-outline-primary mt-2"
                                                onClick={() => {
                                                    setShowAssignModal(false);
                                                    navigate("/classes");
                                                }}
                                            >
                                                {t("worksheet.manageClasses")}
                                            </button>
                                        </div>
                                    ) : (
                                        <div className="list-group">
                                            {myClasses.map((cls) => (
                                                <div
                                                    key={cls.id}
                                                    className="list-group-item d-flex justify-content-between align-items-center"
                                                >
                                                    <div>
                                                        <strong>{cls.name}</strong>
                                                        {cls.description && (
                                                            <small className="text-muted d-block">{cls.description}</small>
                                                        )}
                                                    </div>
                                                    <button
                                                        className="btn btn-sm btn-outline-success"
                                                        onClick={() => handleAssignToClass(cls.id)}
                                                        disabled={assigningClassId === cls.id}
                                                    >
                                                        {assigningClassId === cls.id ? (
                                                            <span className="spinner-border spinner-border-sm" />
                                                        ) : (
                                                            t("worksheet.assign")
                                                        )}
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>
                                <div className="modal-footer">
                                    <button type="button" className="btn btn-secondary" onClick={() => setShowAssignModal(false)}>
                                        {t("worksheet.close")}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-backdrop fade show"></div>
                </>
            )}

            {teacherViewEnabled && (
                <div
                    style={{
                        position: "fixed",
                        right: "clamp(0.5rem, 2vw, 2rem)",
                        bottom: "clamp(0.5rem, 2vw, 2rem)",
                        backgroundColor: "#fffbea",
                        border: "1px solid #ffe08a",
                        borderRadius: "0.75rem",
                        boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                        padding: "1rem 1.25rem",
                        maxWidth: "min(400px, calc(100vw - 1rem))",
                        zIndex: 1000,
                    }}
                >
                    <h6 className="fw-bold mb-2">{t("worksheet.tips")}</h6>
                    <ul className="mb-0 small text-muted" style={{ lineHeight: 1.5 }}>
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip1") }} />
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip2") }} />
                        <li>{t("worksheet.tip3")}</li>
                        <li>{t("worksheet.tip4")}</li>
                        <li dangerouslySetInnerHTML={{ __html: t("worksheet.tip5") }} />
                    </ul>
                </div>
            )}
        </div>
    );
}
