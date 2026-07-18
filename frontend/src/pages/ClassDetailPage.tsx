import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import type { ClassInfo, StudentInfo, StudentProgress } from "../types/models";

export default function ClassDetailPage() {
    const { t, i18n } = useTranslation();
    const { id } = useParams<{ id: string }>();
    const { isLoggedIn, user, isStudent } = useAuth();
    const navigate = useNavigate();

    const [classData, setClassData] = useState<ClassInfo | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    // Edit mode
    const [editing, setEditing] = useState(false);
    const [editName, setEditName] = useState("");
    const [editDesc, setEditDesc] = useState("");

    // Add student form
    const [showAddStudent, setShowAddStudent] = useState(false);
    const [studentFirstName, setStudentFirstName] = useState("");
    const [studentLastName, setStudentLastName] = useState("");
    const [addingStudent, setAddingStudent] = useState(false);

    // Assign worksheet
    const [showAssignWorksheet, setShowAssignWorksheet] = useState(false);
    const [worksheetId, setWorksheetId] = useState("");
    const [assigningWorksheet, setAssigningWorksheet] = useState(false);

    // Copied token feedback
    const [copiedToken, setCopiedToken] = useState<string | null>(null);

    // Temp-password reset
    const [resetingStudent, setResetingStudent] = useState<StudentInfo | null>(null);
    const [tempPassword, setTempPassword] = useState<string | null>(null);
    const [resetting, setResetting] = useState(false);
    const [copiedTempPw, setCopiedTempPw] = useState(false);

    // Tracking (AP5)
    const [togglingTracking, setTogglingTracking] = useState(false);
    const [progress, setProgress] = useState<StudentProgress[]>([]);
    const [loadingProgress, setLoadingProgress] = useState(false);

    const isOwnerOrAdmin =
        classData && user && (classData.ownerId === user.userId || user.role === "ADMIN");

    useEffect(() => {
        if (!isLoggedIn) {
            navigate("/login");
            return;
        }
        loadClass();
    }, [isLoggedIn, id]);

    const loadClass = async () => {
        try {
            setLoading(true);
            const res = await api.get<ClassInfo>(`/classes/${id}`);
            setClassData(res.data);
            setEditName(res.data.name);
            setEditDesc(res.data.description);
        } catch (e: any) {
            setError(e.response?.data?.details || e.response?.data?.error || t("classDetail.notFound"));
        } finally {
            setLoading(false);
        }
    };

    const handleUpdate = async () => {
        if (!editName.trim()) return;
        try {
            const res = await api.put<ClassInfo>(`/classes/${id}`, {
                name: editName.trim(),
                description: editDesc.trim(),
            });
            setClassData((prev) => prev ? { ...prev, name: res.data.name, description: res.data.description } : prev);
            setEditing(false);
            setSuccess(t("classDetail.updated"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorUpdating"));
        }
    };

    const handleAddStudent = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!studentFirstName.trim() || !studentLastName.trim()) return;

        try {
            setAddingStudent(true);
            const res = await api.post<StudentInfo>(`/classes/${id}/students`, {
                firstName: studentFirstName.trim(),
                lastName: studentLastName.trim(),
            });
            setClassData((prev) =>
                prev ? { ...prev, students: [...prev.students, res.data] } : prev
            );
            setStudentFirstName("");
            setStudentLastName("");
            setShowAddStudent(false);
            setSuccess(t("classDetail.studentCreated", { name: `${res.data.firstName} ${res.data.lastName}` }));
            setTimeout(() => setSuccess(""), 5000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorCreatingStudent"));
        } finally {
            setAddingStudent(false);
        }
    };

    const handleRemoveStudent = async (studentId: string, name: string) => {
        if (!confirm(t("classDetail.removeConfirm", { name }))) return;
        try {
            await api.delete(`/classes/${id}/students/${studentId}`);
            setClassData((prev) =>
                prev
                    ? { ...prev, students: prev.students.filter((s) => s.userId !== studentId) }
                    : prev
            );
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorRemoving"));
        }
    };

    const handleAssignWorksheet = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!worksheetId.trim()) return;

        try {
            setAssigningWorksheet(true);
            await api.post(`/classes/${id}/worksheets/${worksheetId.trim()}`);
            await loadClass(); // reload to get worksheet name
            setWorksheetId("");
            setShowAssignWorksheet(false);
            setSuccess(t("classDetail.worksheetAssigned"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorAssigning"));
        } finally {
            setAssigningWorksheet(false);
        }
    };

    const handleRemoveWorksheet = async (wsId: string, name: string) => {
        if (!confirm(t("classDetail.removeWorksheetConfirm", { name }))) return;
        try {
            await api.delete(`/classes/${id}/worksheets/${wsId}`);
            setClassData((prev) =>
                prev
                    ? { ...prev, worksheets: prev.worksheets.filter((w) => w.worksheetId !== wsId) }
                    : prev
            );
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorRemoving"));
        }
    };

    const handleResetTempPassword = async () => {
        if (!resetingStudent) return;
        try {
            setResetting(true);
            const locale = i18n.language?.startsWith("de") ? "de" : "en";
            const res = await api.post<{ tempPassword: string }>(
                `/classes/${id}/students/${resetingStudent.userId}/reset-temp-password`,
                { locale }
            );
            setTempPassword(res.data.tempPassword);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorResetPassword"));
            setResetingStudent(null);
        } finally {
            setResetting(false);
        }
    };

    const closeTempPwModal = () => {
        setResetingStudent(null);
        setTempPassword(null);
        setCopiedTempPw(false);
    };

    const copyToken = (token: string) => {
        const url = `${window.location.origin}${import.meta.env.BASE_URL}student-onboarding?token=${token}`;
        navigator.clipboard.writeText(url);
        setCopiedToken(token);
        setTimeout(() => setCopiedToken(null), 2000);
    };

    const handleToggleTracking = async () => {
        if (!classData) return;
        try {
            setTogglingTracking(true);
            const newValue = !classData.trackingEnabled;
            await api.put(`/classes/${id}/tracking`, { enabled: newValue });
            setClassData((prev) => prev ? { ...prev, trackingEnabled: newValue } : prev);
            if (newValue) {
                loadProgress();
            } else {
                setProgress([]);
            }
            setSuccess(newValue ? t("classDetail.trackingOn") : t("classDetail.trackingOff"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorToggleTracking"));
        } finally {
            setTogglingTracking(false);
        }
    };

    const loadProgress = async () => {
        try {
            setLoadingProgress(true);
            const res = await api.get<StudentProgress[]>(`/classes/${id}/progress`);
            setProgress(res.data);
        } catch (e: any) {
            console.error("Error loading progress", e);
        } finally {
            setLoadingProgress(false);
        }
    };

    // Load progress when class data loads and tracking is enabled
    useEffect(() => {
        if (classData?.trackingEnabled && isOwnerOrAdmin) {
            loadProgress();
        }
    }, [classData?.trackingEnabled]);

    if (loading) {
        return (
            <div className="container py-5 text-center">
                <div className="spinner-border" role="status">
                    <span className="visually-hidden">{t("classDetail.loading")}</span>
                </div>
            </div>
        );
    }

    if (!classData) {
        return (
            <div className="container py-5">
                <div className="alert alert-danger">{error || t("classDetail.notFound")}</div>
                <Link to="/classes" className="btn btn-outline-secondary">{t("classDetail.back")}</Link>
            </div>
        );
    }

    return (
        <>
        <div className="container py-4" style={{ maxWidth: 900 }}>
            {/* Back link */}
            <Link to={isStudent ? "/my-classes" : "/classes"} className="text-decoration-none mb-3 d-inline-block">
                <i className="bi bi-arrow-left me-1"></i> {t("classDetail.backToOverview")}
            </Link>

            {/* Alerts */}
            {error && (
                <div className="alert alert-danger alert-dismissible">
                    {error}
                    <button className="btn-close" onClick={() => setError("")}></button>
                </div>
            )}
            {success && (
                <div className="alert alert-success alert-dismissible">
                    {success}
                    <button className="btn-close" onClick={() => setSuccess("")}></button>
                </div>
            )}

            {/* Header */}
            <div className="d-flex justify-content-between align-items-start mb-4">
                {editing ? (
                    <div className="flex-grow-1 me-3">
                        <input
                            type="text"
                            className="form-control form-control-lg mb-2"
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                        />
                        <textarea
                            className="form-control mb-2"
                            value={editDesc}
                            onChange={(e) => setEditDesc(e.target.value)}
                            rows={2}
                        />
                        <div className="d-flex gap-2">
                            <button className="btn btn-success btn-sm" onClick={handleUpdate}>
                                {t("classDetail.save")}
                            </button>
                            <button
                                className="btn btn-outline-secondary btn-sm"
                                onClick={() => setEditing(false)}
                            >
                                {t("classDetail.cancel")}
                            </button>
                        </div>
                    </div>
                ) : (
                    <div>
                        <h2 className="mb-1">{classData.name}</h2>
                        {classData.description && (
                            <p className="text-muted">{classData.description}</p>
                        )}
                    </div>
                )}
                {isOwnerOrAdmin && !editing && (
                    <button
                        className="btn btn-outline-secondary btn-sm"
                        onClick={() => setEditing(true)}
                    >
                        <i className="bi bi-pencil me-1"></i> {t("common.edit")}
                    </button>
                )}
            </div>

            {/* ── Students Section (only for owner/admin) ───────────────────────── */}
            {isOwnerOrAdmin && (
            <div className="card mb-4">
                <div className="card-header d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">
                        <i className="bi bi-person me-2"></i>
                        {t("classDetail.students", { count: classData.students.length })}
                    </h5>
                    <button
                        className="btn btn-sm btn-outline-primary"
                        onClick={() => setShowAddStudent(!showAddStudent)}
                    >
                        <i className="bi bi-person-plus me-1"></i> {t("classDetail.addStudent")}
                    </button>
                </div>
                <div className="card-body">
                    {/* Add student form */}
                    {showAddStudent && (
                        <form onSubmit={handleAddStudent} className="mb-3 p-3 bg-light rounded">
                            <div className="row g-2">
                                <div className="col">
                                    <input
                                        type="text"
                                        className="form-control"
                                        placeholder={t("classDetail.firstName")}
                                        value={studentFirstName}
                                        onChange={(e) => setStudentFirstName(e.target.value)}
                                        required
                                    />
                                </div>
                                <div className="col">
                                    <input
                                        type="text"
                                        className="form-control"
                                        placeholder={t("classDetail.lastName")}
                                        value={studentLastName}
                                        onChange={(e) => setStudentLastName(e.target.value)}
                                        required
                                    />
                                </div>
                                <div className="col-auto">
                                    <button
                                        type="submit"
                                        className="btn btn-success"
                                        disabled={addingStudent}
                                    >
                                        {addingStudent ? "..." : t("classDetail.create")}
                                    </button>
                                </div>
                            </div>
                        </form>
                    )}

                    {/* Student list */}
                    {classData.students.length === 0 ? (
                        <p className="text-muted mb-0">{t("classDetail.noStudents")}</p>
                    ) : (
                        <div className="table-responsive">
                            <table className="table table-hover mb-0">
                                <thead>
                                    <tr>
                                        <th>{t("classDetail.nameHeader")}</th>
                                        <th>{t("classDetail.emailHeader")}</th>
                                        <th>{t("classDetail.statusHeader")}</th>
                                        <th style={{ width: 120 }}>{t("classDetail.actionsHeader")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {classData.students.map((s) => (
                                        <tr key={s.userId}>
                                            <td>
                                                {s.firstName} {s.lastName}
                                            </td>
                                            <td>
                                                {s.email ? (
                                                    <span className="text-muted">{s.email}</span>
                                                ) : (
                                                    <span className="text-warning">
                                                        <i className="bi bi-clock me-1"></i>
                                                        {t("classDetail.notRegistered")}
                                                    </span>
                                                )}
                                            </td>
                                            <td>
                                                {s.email ? (
                                                    <span className="badge bg-success">{t("classDetail.active")}</span>
                                                ) : s.inviteToken ? (
                                                    <button
                                                        className="btn btn-sm btn-outline-primary"
                                                        onClick={() => copyToken(s.inviteToken!)}
                                                        title={t("classDetail.copyLink")}
                                                    >
                                                        {copiedToken === s.inviteToken ? (
                                                            <>
                                                                <i className="bi bi-check me-1"></i>
                                                                {t("classDetail.linkCopied")}
                                                            </>
                                                        ) : (
                                                            <>
                                                                <i className="bi bi-link-45deg me-1"></i>
                                                                {t("classDetail.copyLink")}
                                                            </>
                                                        )}
                                                    </button>
                                                ) : (
                                                    <span className="badge bg-secondary">
                                                        {t("classDetail.tokenExpired")}
                                                    </span>
                                                )}
                                            </td>
                                            <td>
                                                <div className="d-flex gap-1">
                                                    {s.email && (
                                                        <button
                                                            className="btn btn-sm btn-outline-warning"
                                                            onClick={() => setResetingStudent(s)}
                                                            title={t("classDetail.resetPassword")}
                                                        >
                                                            <i className="bi bi-key"></i>
                                                        </button>
                                                    )}
                                                    <button
                                                        className="btn btn-sm btn-outline-danger"
                                                        onClick={() =>
                                                            handleRemoveStudent(
                                                                s.userId,
                                                                `${s.firstName} ${s.lastName}`
                                                            )
                                                        }
                                                        title={t("classDetail.removeStudent")}
                                                    >
                                                        <i className="bi bi-trash"></i>
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>
            )}

            {/* ── Tracking Section (AP5) ────────────────── */}
            {isOwnerOrAdmin && (
                <div className="card mb-4">
                    <div className="card-header d-flex justify-content-between align-items-center">
                        <h5 className="mb-0">
                            <i className="bi bi-bar-chart me-2"></i>
                            {t("classDetail.tracking")}
                        </h5>
                        <div className="form-check form-switch">
                            <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="trackingSwitch"
                                checked={classData.trackingEnabled}
                                onChange={handleToggleTracking}
                                disabled={togglingTracking}
                            />
                            <label className="form-check-label" htmlFor="trackingSwitch">
                                {classData.trackingEnabled ? t("classDetail.trackingEnabled") : t("classDetail.trackingDisabled")}
                            </label>
                        </div>
                    </div>
                    <div className="card-body">
                        {!classData.trackingEnabled ? (
                            <p className="text-muted mb-0">
                                {t("classDetail.trackingInfo")}
                            </p>
                        ) : loadingProgress ? (
                            <div className="text-center py-3">
                                <div className="spinner-border spinner-border-sm" role="status">
                                    <span className="visually-hidden">{t("classDetail.loading")}</span>
                                </div>
                            </div>
                        ) : progress.length === 0 ? (
                            <p className="text-muted mb-0">
                                {t("classDetail.noProgress")}
                            </p>
                        ) : (
                            <div className="table-responsive">
                                <table className="table table-hover mb-0">
                                    <thead>
                                        <tr>
                                            <th>{t("classDetail.studentHeader")}</th>
                                            <th>{t("classDetail.solvedTasks")}</th>
                                            <th>{t("classDetail.totalAttempts")}</th>
                                            <th>{t("classDetail.correctAttempts")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {progress.map((p, idx) => (
                                            <tr key={p.userId || `anon-${idx}`}>
                                                <td>
                                                    {p.userId ? (
                                                        <span>{p.studentName}</span>
                                                    ) : (
                                                        <span className="text-muted fst-italic">
                                                            {p.studentName}
                                                        </span>
                                                    )}
                                                </td>
                                                <td>
                                                    <span className="badge bg-success">
                                                        {p.solvedTaskIds.length}
                                                    </span>
                                                </td>
                                                <td>{p.totalAttempts}</td>
                                                <td>{p.correctAttempts}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* ── Worksheets Section ─────────────────────── */}
            <div className="card">
                <div className="card-header d-flex justify-content-between align-items-center">
                    <h5 className="mb-0">
                        <i className="bi bi-file-earmark-text me-2"></i>
                        {t("classDetail.worksheets", { count: classData.worksheets.length })}
                    </h5>
                    {isOwnerOrAdmin && (
                        <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => setShowAssignWorksheet(!showAssignWorksheet)}
                        >
                            <i className="bi bi-plus-circle me-1"></i> {t("classDetail.assignWorksheet")}
                        </button>
                    )}
                </div>
                <div className="card-body">
                    {/* Assign worksheet form */}
                    {showAssignWorksheet && (
                        <form onSubmit={handleAssignWorksheet} className="mb-3 p-3 bg-light rounded">
                            <div className="input-group">
                                <input
                                    type="text"
                                    className="form-control"
                                    placeholder={t("classDetail.worksheetUuidPlaceholder")}
                                    value={worksheetId}
                                    onChange={(e) => setWorksheetId(e.target.value)}
                                    required
                                />
                                <button
                                    type="submit"
                                    className="btn btn-success"
                                    disabled={assigningWorksheet}
                                >
                                    {assigningWorksheet ? "..." : t("classDetail.assignBtn")}
                                </button>
                            </div>
                        </form>
                    )}

                    {/* Worksheet list */}
                    {classData.worksheets.length === 0 ? (
                        <p className="text-muted mb-0">
                            {t("classDetail.noWorksheets")}
                        </p>
                    ) : (
                        <div className="list-group list-group-flush">
                            {classData.worksheets.map((w) => (
                                <div
                                    key={w.worksheetId}
                                    className="list-group-item d-flex justify-content-between align-items-center"
                                >
                                    <div>
                                        <Link
                                            to={`/worksheets/${w.worksheetId}`}
                                            className="fw-semibold text-decoration-none"
                                        >
                                            {w.name}
                                        </Link>
                                        {w.description && (
                                            <small className="text-muted d-block">
                                                {w.description}
                                            </small>
                                        )}
                                    </div>
                                    <div className="d-flex gap-2">
                                        <Link
                                            to={`/worksheets/${w.worksheetId}`}
                                            className="btn btn-sm btn-outline-secondary"
                                            title={t("classDetail.openWorksheet")}
                                        >
                                            <i className="bi bi-box-arrow-up-right"></i>
                                        </Link>
                                        {isOwnerOrAdmin && (
                                            <button
                                                className="btn btn-sm btn-outline-danger"
                                                onClick={() =>
                                                    handleRemoveWorksheet(w.worksheetId, w.name)
                                                }
                                                title={t("classDetail.removeAssignment")}
                                            >
                                                <i className="bi bi-x-lg"></i>
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>

        {/* ── Temp Password Modal ─────────────────────────────── */}
        {resetingStudent && (
            <div
                className="modal d-block"
                style={{ backgroundColor: "rgba(0,0,0,0.5)" }}
                onClick={e => { if (e.target === e.currentTarget) closeTempPwModal(); }}
            >
                <div className="modal-dialog modal-dialog-centered">
                    <div className="modal-content">
                        <div className="modal-header">
                            <h5 className="modal-title">
                                <i className="bi bi-key me-2 text-warning"></i>
                                {t("classDetail.resetPasswordTitle")}
                            </h5>
                            <button className="btn-close" onClick={closeTempPwModal} />
                        </div>
                        <div className="modal-body">
                            {!tempPassword ? (
                                <>
                                    <p>
                                        {t("classDetail.resetPasswordConfirm", {
                                            name: `${resetingStudent.firstName} ${resetingStudent.lastName}`
                                        })}
                                    </p>
                                    <p className="text-muted small">{t("classDetail.resetPasswordHint")}</p>
                                </>
                            ) : (
                                <>
                                    <p className="text-success fw-semibold">
                                        <i className="bi bi-check-circle me-1"></i>
                                        {t("classDetail.resetPasswordSuccess", {
                                            name: `${resetingStudent.firstName} ${resetingStudent.lastName}`
                                        })}
                                    </p>
                                    <div className="input-group mb-2">
                                        <input
                                            type="text"
                                            className="form-control form-control-lg fw-bold text-center"
                                            value={tempPassword}
                                            readOnly
                                        />
                                        <button
                                            className="btn btn-outline-secondary"
                                            onClick={() => {
                                                navigator.clipboard.writeText(tempPassword);
                                                setCopiedTempPw(true);
                                                setTimeout(() => setCopiedTempPw(false), 2000);
                                            }}
                                        >
                                            {copiedTempPw
                                                ? <i className="bi bi-check text-success"></i>
                                                : <i className="bi bi-clipboard"></i>}
                                        </button>
                                    </div>
                                    <p className="text-muted small">
                                        <i className="bi bi-exclamation-triangle me-1 text-warning"></i>
                                        {t("classDetail.resetPasswordOnce")}
                                    </p>
                                </>
                            )}
                        </div>
                        <div className="modal-footer">
                            {!tempPassword ? (
                                <>
                                    <button className="btn btn-outline-secondary" onClick={closeTempPwModal}>
                                        {t("classDetail.cancel")}
                                    </button>
                                    <button
                                        className="btn btn-warning"
                                        onClick={handleResetTempPassword}
                                        disabled={resetting}
                                    >
                                        {resetting
                                            ? <span className="spinner-border spinner-border-sm me-2" />
                                            : <i className="bi bi-key me-2"></i>}
                                        {t("classDetail.resetPasswordBtn")}
                                    </button>
                                </>
                            ) : (
                                <button className="btn btn-success" onClick={closeTempPwModal}>
                                    {t("common.done")}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        )}
        </>
    );
}
