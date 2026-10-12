import { useEffect, useRef, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api";
import { readCsvFile } from "../lib/csv.ts";
import { useAuth } from "../context/AuthContext";
import type { ClassInfo, StudentInfo, StudentProgress, Worksheet } from "../types/models";
import WorksheetSchedule from "../components/WorksheetSchedule.tsx";
import WorksheetDeadline from "../components/WorksheetDeadline.tsx";
import FimStudentAdder from "../components/FimStudentAdder.tsx";
import {
    parseStudentCsv,
    downloadInviteCsv,
    downloadTemplateCsv,
    csvDelimiterForLocale,
    studentInviteUrl,
    type ParsedStudent,
} from "../lib/studentImport.ts";

/** How many names of an import to show before summarising the rest. */
const IMPORT_PREVIEW_LIMIT = 10;

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

    // Students by FIM username
    const [showAddFim, setShowAddFim] = useState(false);

    // Assign worksheet
    const [showAssignWorksheet, setShowAssignWorksheet] = useState(false);
    const [worksheetSearch, setWorksheetSearch] = useState("");
    const [selectedWorksheet, setSelectedWorksheet] = useState<Worksheet | null>(null);
    const [worksheetOptions, setWorksheetOptions] = useState<Worksheet[]>([]);
    const [loadingWorksheetOptions, setLoadingWorksheetOptions] = useState(false);
    const [showWorksheetDropdown, setShowWorksheetDropdown] = useState(false);
    const [assigningWorksheet, setAssigningWorksheet] = useState(false);

    // Create worksheet
    const [showCreateWorksheet, setShowCreateWorksheet] = useState(false);
    const [newWorksheetName, setNewWorksheetName] = useState("");
    const [newWorksheetDesc, setNewWorksheetDesc] = useState("");
    const [creatingWorksheet, setCreatingWorksheet] = useState(false);

    // CSV import of a whole class list
    const [showImport, setShowImport] = useState(false);
    const [importRows, setImportRows] = useState<ParsedStudent[]>([]);
    const [importError, setImportError] = useState("");
    const [importing, setImporting] = useState(false);
    // Kept after the import so the invite links stay downloadable.
    const [importedStudents, setImportedStudents] = useState<StudentInfo[]>([]);
    const csvInputRef = useRef<HTMLInputElement>(null);

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

    const handleFimStudentsAdded = (added: StudentInfo[]) => {
        // Merged by id: a student who was already in the class comes back too.
        setClassData((prev) => {
            if (!prev) return prev;
            const addedById = new Map(added.map((s) => [s.userId, s]));
            const kept = prev.students.map((s) => addedById.get(s.userId) ?? s);
            const known = new Set(prev.students.map((s) => s.userId));
            return { ...prev, students: [...kept, ...added.filter((s) => !known.has(s.userId))] };
        });
        setSuccess(t("fimStudents.added", { count: added.length }));
        setTimeout(() => setSuccess(""), 5000);
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

    const loadWorksheetOptions = async () => {
        try {
            setLoadingWorksheetOptions(true);
            const res = await api.get<Worksheet[]>("/worksheets/my");
            setWorksheetOptions(res.data);
        } catch {
            // Ignore — manual UUID entry still works even if the list fails to load.
        } finally {
            setLoadingWorksheetOptions(false);
        }
    };

    // Load the teacher's worksheets lazily, the first time the assign form is opened.
    useEffect(() => {
        if (showAssignWorksheet && isOwnerOrAdmin && worksheetOptions.length === 0 && !loadingWorksheetOptions) {
            loadWorksheetOptions();
        }
    }, [showAssignWorksheet]);

    const handleAssignWorksheet = async (e: React.FormEvent) => {
        e.preventDefault();
        const targetId = selectedWorksheet ? selectedWorksheet.id : worksheetSearch.trim();
        if (!targetId) return;

        try {
            setAssigningWorksheet(true);
            await api.post(`/classes/${id}/worksheets/${targetId}`);
            await loadClass(); // reload to get worksheet name
            setWorksheetSearch("");
            setSelectedWorksheet(null);
            setShowAssignWorksheet(false);
            setSuccess(t("classDetail.worksheetAssigned"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorAssigning"));
        } finally {
            setAssigningWorksheet(false);
        }
    };

    const handleCreateWorksheet = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newWorksheetName.trim()) return;

        try {
            setCreatingWorksheet(true);
            const res = await api.post<Worksheet>("/worksheets", {
                name: newWorksheetName.trim(),
                description: newWorksheetDesc.trim(),
            });
            await api.post(`/classes/${id}/worksheets/${res.data.id}`);
            setWorksheetOptions((prev) => [res.data, ...prev]);
            await loadClass(); // reload to show the new worksheet in the list
            setNewWorksheetName("");
            setNewWorksheetDesc("");
            setShowCreateWorksheet(false);
            setSuccess(t("classDetail.worksheetCreated"));
            setTimeout(() => setSuccess(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classDetail.errorCreatingWorksheet"));
        } finally {
            setCreatingWorksheet(false);
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
        navigator.clipboard.writeText(studentInviteUrl(token));
        setCopiedToken(token);
        setTimeout(() => setCopiedToken(null), 2000);
    };

    /** Column titles for both CSVs, in the teacher's own language. */
    const csvHeaders = (): [string, string, string] => [
        t("csvImport.colFirstName"),
        t("csvImport.colLastName"),
        t("csvImport.colInviteLink"),
    ];

    const resetImport = () => {
        setImportRows([]);
        setImportError("");
        setImportedStudents([]);
        // The picker holds on to the last file otherwise, and choosing the
        // same one again would fire no change event.
        if (csvInputRef.current) csvInputRef.current.value = "";
    };

    const toggleImport = () => {
        resetImport();
        setShowImport((open) => !open);
    };

    const handleCsvChosen = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        setImportRows([]);
        setImportError("");
        setImportedStudents([]);
        if (!file) return;

        try {
            const result = parseStudentCsv(await readCsvFile(file));
            // The parser reports an i18n key so the message can be translated
            // where it is shown rather than where it is found.
            if (result.error) setImportError(t(result.error.key, result.error.params));
            else setImportRows(result.students);
        } catch {
            setImportError(t("csvImport.errorRead"));
        }
    };

    const handleImport = async () => {
        if (importRows.length === 0) return;
        try {
            setImporting(true);
            setImportError("");
            const res = await api.post<StudentInfo[]>(`/classes/${id}/students/bulk`, {
                students: importRows,
            });
            setClassData((prev) =>
                prev ? { ...prev, students: [...prev.students, ...res.data] } : prev
            );
            // The preview is spent; what replaces it is the link download.
            setImportedStudents(res.data);
            setImportRows([]);
            if (csvInputRef.current) csvInputRef.current.value = "";
            setSuccess(t("csvImport.created", { count: res.data.length }));
            setTimeout(() => setSuccess(""), 5000);
        } catch (err) {
            // apiErrorMessage rather than the older reach into `data.details`
            // in this file: the row the server names is the whole point.
            setImportError(apiErrorMessage(err, t("csvImport.errorCreating")));
        } finally {
            setImporting(false);
        }
    };

    const handleDownloadLinks = () => {
        if (!classData) return;
        downloadInviteCsv(importedStudents, classData.name, {
            headers: csvHeaders(),
            delimiter: csvDelimiterForLocale(i18n.language),
        });
    };

    const handleDownloadUnregistered = () => {
        if (!classData) return;
        // FIM students have no invite link: they log in with their FIM-Account.
        const unregistered = classData.students.filter((s) => !s.email && !s.fimUid);
        downloadInviteCsv(unregistered, classData.name, {
            headers: csvHeaders(),
            delimiter: csvDelimiterForLocale(i18n.language),
        });
    };

    const handleDownloadTemplate = () => {
        const [first, last] = csvHeaders();
        downloadTemplateCsv([first, last], csvDelimiterForLocale(i18n.language));
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

    const assignedWorksheetIds = new Set(classData.worksheets.map((w) => w.worksheetId));
    const worksheetSearchQuery = worksheetSearch.trim().toLowerCase();
    const filteredWorksheetOptions = worksheetOptions.filter(
        (w) =>
            !assignedWorksheetIds.has(w.id) &&
            (worksheetSearchQuery === "" || w.name.toLowerCase().includes(worksheetSearchQuery))
    );

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
                    <div className="d-flex gap-2">
                        <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => setShowAddStudent(!showAddStudent)}
                        >
                            <i className="bi bi-person-plus me-1"></i> {t("classDetail.addStudent")}
                        </button>
                        {/* A whole class list at once, for the start of a school year. */}
                        <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={toggleImport}
                            aria-expanded={showImport}
                        >
                            <i className="bi bi-filetype-csv me-1"></i> {t("csvImport.button")}
                        </button>
                        <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => setShowAddFim((open) => !open)}
                            aria-expanded={showAddFim}
                        >
                            <i className="bi bi-mortarboard me-1"></i> {t("fimStudents.button")}
                        </button>
                        {classData.students.some((s) => !s.email && !s.fimUid) && (
                            <button
                                className="btn btn-sm btn-outline-secondary"
                                onClick={handleDownloadUnregistered}
                                title={t("classDetail.downloadUnregisteredTitle")}
                            >
                                <i className="bi bi-download me-1"></i> {t("classDetail.downloadUnregistered")}
                            </button>
                        )}
                    </div>
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

                    {/* Students by FIM username, one or a CSV of them */}
                    {showAddFim && id && <FimStudentAdder classId={id} onAdded={handleFimStudentsAdded} />}

                    {/* CSV import */}
                    {showImport && (
                        <div className="mb-3 p-3 bg-light rounded">
                            <div className="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-1">
                                <h6 className="mb-0">{t("csvImport.title")}</h6>
                                {/*
                                  * An example beats the description: a teacher
                                  * who fills this in cannot get the shape wrong,
                                  * and finds out here whether their spreadsheet
                                  * mangles umlauts.
                                  */}
                                <button className="btn btn-sm btn-link p-0" onClick={handleDownloadTemplate}>
                                    <i className="bi bi-download me-1"></i>
                                    {t("csvImport.template")}
                                </button>
                            </div>
                            <p className="small text-muted">{t("csvImport.hint")}</p>
                            <input
                                ref={csvInputRef}
                                type="file"
                                accept=".csv,text/csv"
                                className="form-control form-control-sm"
                                aria-label={t("csvImport.fileLabel")}
                                disabled={importing}
                                onChange={handleCsvChosen}
                            />

                            {importError && (
                                <div className="alert alert-danger py-1 px-2 mt-2 mb-0 small">{importError}</div>
                            )}

                            {/*
                              * Nothing is created until the teacher has seen the
                              * names: an import cannot be undone in one step.
                              */}
                            {importRows.length > 0 && (
                                <div className="mt-2">
                                    <p className="small mb-1">
                                        {t("csvImport.preview", { count: importRows.length })}
                                    </p>
                                    <ul className="small text-muted mb-2">
                                        {importRows.slice(0, IMPORT_PREVIEW_LIMIT).map((s, i) => (
                                            <li key={i}>
                                                {s.firstName} {s.lastName}
                                            </li>
                                        ))}
                                        {importRows.length > IMPORT_PREVIEW_LIMIT && (
                                            <li>
                                                {t("csvImport.previewMore", {
                                                    count: importRows.length - IMPORT_PREVIEW_LIMIT,
                                                })}
                                            </li>
                                        )}
                                    </ul>
                                    <div className="d-flex gap-2">
                                        <button
                                            className="btn btn-success btn-sm"
                                            onClick={handleImport}
                                            disabled={importing}
                                        >
                                            {importing
                                                ? t("csvImport.creating")
                                                : t("csvImport.createBtn", { count: importRows.length })}
                                        </button>
                                        <button
                                            className="btn btn-outline-secondary btn-sm"
                                            onClick={resetImport}
                                            disabled={importing}
                                        >
                                            {t("csvImport.cancel")}
                                        </button>
                                    </div>
                                </div>
                            )}

                            {importedStudents.length > 0 && (
                                <div className="alert alert-success py-2 px-2 mt-2 mb-0">
                                    <p className="small mb-2">
                                        {t("csvImport.created", { count: importedStudents.length })}
                                    </p>
                                    <button
                                        className="btn btn-sm btn-outline-success"
                                        onClick={handleDownloadLinks}
                                    >
                                        <i className="bi bi-download me-1"></i>
                                        {t("csvImport.downloadLinks")}
                                    </button>
                                    <p className="small text-muted mb-0 mt-2">{t("csvImport.downloadHint")}</p>
                                </div>
                            )}
                        </div>
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
                                                ) : s.fimUid ? (
                                                    // Filled in by the student's first FIM login.
                                                    <span className="text-muted">–</span>
                                                ) : (
                                                    <span className="text-warning">
                                                        <i className="bi bi-clock me-1"></i>
                                                        {t("classDetail.notRegistered")}
                                                    </span>
                                                )}
                                            </td>
                                            <td>
                                                {s.fimUid ? (
                                                    <span className="badge bg-info text-dark" title={t("fimStudents.badgeTitle")}>
                                                        <i className="bi bi-mortarboard me-1"></i>
                                                        {s.fimUid}
                                                    </span>
                                                ) : s.email ? (
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
                                                    {s.email && !s.fimUid && (
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
                                            <tr
                                                key={p.userId || `anon-${idx}`}
                                                style={p.userId ? { cursor: "pointer" } : undefined}
                                                onClick={() => {
                                                    if (p.userId) navigate(`/classes/${id}/students/${p.userId}/progress`);
                                                }}
                                            >
                                                <td>
                                                    {p.userId ? (
                                                        <Link
                                                            to={`/classes/${id}/students/${p.userId}/progress`}
                                                            className="text-decoration-none"
                                                            title={t("studentProgress.viewDetails")}
                                                        >
                                                            {p.studentName}
                                                        </Link>
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
                        <div className="d-flex gap-2">
                            <button
                                className="btn btn-sm btn-outline-secondary"
                                onClick={() => {
                                    setShowCreateWorksheet(!showCreateWorksheet);
                                    setShowAssignWorksheet(false);
                                }}
                            >
                                <i className="bi bi-file-earmark-plus me-1"></i> {t("classDetail.createWorksheet")}
                            </button>
                            <button
                                className="btn btn-sm btn-outline-primary"
                                onClick={() => {
                                    setShowAssignWorksheet(!showAssignWorksheet);
                                    setShowCreateWorksheet(false);
                                }}
                            >
                                <i className="bi bi-plus-circle me-1"></i> {t("classDetail.assignWorksheet")}
                            </button>
                        </div>
                    )}
                </div>
                <div className="card-body">
                    {/* Create worksheet form */}
                    {showCreateWorksheet && (
                        <form onSubmit={handleCreateWorksheet} className="mb-3 p-3 bg-light rounded">
                            <div className="mb-2">
                                <input
                                    type="text"
                                    className="form-control"
                                    placeholder={t("classDetail.newWorksheetNamePlaceholder")}
                                    value={newWorksheetName}
                                    onChange={(e) => setNewWorksheetName(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="mb-2">
                                <textarea
                                    className="form-control"
                                    placeholder={t("classDetail.newWorksheetDescPlaceholder")}
                                    value={newWorksheetDesc}
                                    onChange={(e) => setNewWorksheetDesc(e.target.value)}
                                    rows={2}
                                />
                            </div>
                            <div className="d-flex gap-2">
                                <button
                                    type="submit"
                                    className="btn btn-success"
                                    disabled={creatingWorksheet || !newWorksheetName.trim()}
                                >
                                    {creatingWorksheet ? "..." : t("classDetail.createWorksheetBtn")}
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-outline-secondary"
                                    onClick={() => setShowCreateWorksheet(false)}
                                >
                                    {t("classDetail.cancel")}
                                </button>
                            </div>
                        </form>
                    )}

                    {/* Assign worksheet form */}
                    {showAssignWorksheet && (
                        <form onSubmit={handleAssignWorksheet} className="mb-3 p-3 bg-light rounded">
                            <div className="position-relative">
                                <div className="input-group">
                                    <input
                                        type="text"
                                        className="form-control"
                                        placeholder={t("classDetail.worksheetSearchPlaceholder")}
                                        value={worksheetSearch}
                                        onChange={(e) => {
                                            setWorksheetSearch(e.target.value);
                                            setSelectedWorksheet(null);
                                            setShowWorksheetDropdown(true);
                                        }}
                                        onFocus={() => setShowWorksheetDropdown(true)}
                                        onBlur={() => setShowWorksheetDropdown(false)}
                                        autoComplete="off"
                                        required
                                    />
                                    <button
                                        type="submit"
                                        className="btn btn-success"
                                        disabled={assigningWorksheet || !(selectedWorksheet || worksheetSearch.trim())}
                                    >
                                        {assigningWorksheet ? "..." : t("classDetail.assignBtn")}
                                    </button>
                                </div>
                                {showWorksheetDropdown && (
                                    <ul
                                        className="list-group position-absolute w-100 shadow-sm"
                                        style={{ zIndex: 1000, maxHeight: 260, overflowY: "auto" }}
                                    >
                                        {loadingWorksheetOptions ? (
                                            <li className="list-group-item text-muted small">
                                                {t("classDetail.loadingWorksheets")}
                                            </li>
                                        ) : filteredWorksheetOptions.length === 0 ? (
                                            <li className="list-group-item text-muted small">
                                                {t("classDetail.noMatchingWorksheets")}
                                            </li>
                                        ) : (
                                            filteredWorksheetOptions.slice(0, 8).map((w) => (
                                                <li
                                                    key={w.id}
                                                    className="list-group-item list-group-item-action"
                                                    style={{ cursor: "pointer" }}
                                                    onMouseDown={(e) => e.preventDefault()}
                                                    onClick={() => {
                                                        setSelectedWorksheet(w);
                                                        setWorksheetSearch(w.name);
                                                        setShowWorksheetDropdown(false);
                                                    }}
                                                >
                                                    <div className="fw-semibold">{w.name}</div>
                                                    {w.description && (
                                                        <small className="text-muted">{w.description}</small>
                                                    )}
                                                </li>
                                            ))
                                        )}
                                    </ul>
                                )}
                            </div>
                            <small className="text-muted d-block mt-1">
                                {t("classDetail.worksheetSearchHint")}
                            </small>
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
                                            state={{ fromClass: id }}
                                            className="fw-semibold text-decoration-none"
                                        >
                                            {w.name}
                                        </Link>
                                        {w.description && (
                                            <small className="text-muted d-block">
                                                {w.description}
                                            </small>
                                        )}
                                        {/*
                                          * Students get the deadline read-only;
                                          * the editable schedule is the
                                          * teacher's.
                                          */}
                                        {!isOwnerOrAdmin && <WorksheetDeadline worksheet={w} />}
                                        {isOwnerOrAdmin && (
                                            <WorksheetSchedule
                                                classId={id!}
                                                worksheet={w}
                                                onSaved={(patch) =>
                                                    setClassData((prev) =>
                                                        prev
                                                            ? {
                                                                  ...prev,
                                                                  worksheets: prev.worksheets.map((x) =>
                                                                      x.worksheetId === w.worksheetId
                                                                          ? { ...x, ...patch }
                                                                          : x,
                                                                  ),
                                                              }
                                                            : prev,
                                                    )
                                                }
                                            />
                                        )}
                                    </div>
                                    <div className="d-flex gap-2">
                                        <Link
                                            to={`/worksheets/${w.worksheetId}`}
                                            state={{ fromClass: id }}
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
