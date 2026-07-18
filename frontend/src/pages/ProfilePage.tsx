import { useState, useEffect } from "react";
import { useAuth } from "../context/AuthContext";
import { useNavigate, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";

interface WorksheetSummary {
    id: string;
    name: string;
    description: string;
}

interface SequenceSummary {
    id: string;
    programmingLanguage: string;
    taskDescription: string;
}

export default function ProfilePage() {
    const { t } = useTranslation();
    const { user, logout, isTeacher, isAdmin } = useAuth();
    const navigate = useNavigate();

    // My resources (Teacher/Admin only)
    const [myWorksheets, setMyWorksheets] = useState<WorksheetSummary[]>([]);
    const [mySequences, setMySequences] = useState<SequenceSummary[]>([]);
    const [loadingResources, setLoadingResources] = useState(false);

    // Name editing
    const [editingName, setEditingName] = useState(false);
    const [firstName, setFirstName] = useState(user?.firstName ?? "");
    const [lastName, setLastName] = useState(user?.lastName ?? "");
    const [nameMsg, setNameMsg] = useState("");
    const [nameErr, setNameErr] = useState("");
    const [nameSaving, setNameSaving] = useState(false);

    // Email editing
    const [editingEmail, setEditingEmail] = useState(false);
    const [newEmail, setNewEmail] = useState("");
    const [emailMsg, setEmailMsg] = useState("");
    const [emailErr, setEmailErr] = useState("");
    const [emailSaving, setEmailSaving] = useState(false);

    // Password changing
    const [editingPassword, setEditingPassword] = useState(false);
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [pwMsg, setPwMsg] = useState("");
    const [pwErr, setPwErr] = useState("");
    const [pwSaving, setPwSaving] = useState(false);

    useEffect(() => {
        if (user && (isTeacher || isAdmin)) {
            setLoadingResources(true);
            Promise.all([
                api.get<WorksheetSummary[]>("/worksheets/my").catch(() => ({ data: [] })),
                api.get<SequenceSummary[]>("/sequences/my").catch(() => ({ data: [] })),
            ]).then(([wsRes, seqRes]) => {
                setMyWorksheets(wsRes.data);
                setMySequences(seqRes.data);
            }).finally(() => setLoadingResources(false));
        }
    }, [user, isTeacher, isAdmin]);

    if (!user) {
        navigate("/login");
        return null;
    }

    const roleLabel = {
        ADMIN: t("profile.roleAdmin"),
        TEACHER: t("profile.roleTeacher"),
        STUDENT: t("profile.roleStudent"),
    }[user.role];

    const handleNameSave = async () => {
        if (!firstName.trim() || !lastName.trim()) {
            setNameErr(t("profile.nameRequired"));
            return;
        }
        setNameSaving(true);
        setNameErr("");
        setNameMsg("");
        try {
            const res = await api.put("/auth/profile", { firstName: firstName.trim(), lastName: lastName.trim() });
            setNameMsg(res.data.message || t("profile.profileUpdated"));
            // Update localStorage
            const stored = localStorage.getItem("authUser");
            if (stored) {
                const parsed = JSON.parse(stored);
                parsed.firstName = res.data.firstName;
                parsed.lastName = res.data.lastName;
                localStorage.setItem("authUser", JSON.stringify(parsed));
            }
            setEditingName(false);
            // Force reload to update AuthContext
            window.location.reload();
        } catch (e: any) {
            setNameErr(e.response?.data?.error || e.response?.data?.message || t("profile.errorSaving"));
        } finally {
            setNameSaving(false);
        }
    };

    const handleEmailSave = async () => {
        if (!newEmail.trim()) {
            setEmailErr(t("profile.emailRequired"));
            return;
        }
        setEmailSaving(true);
        setEmailErr("");
        setEmailMsg("");
        try {
            const res = await api.put("/auth/profile/email", { newEmail: newEmail.trim() });
            setEmailMsg(res.data.message || t("profile.emailChanged"));
            setEditingEmail(false);
            setNewEmail("");
        } catch (e: any) {
            setEmailErr(e.response?.data?.error || e.response?.data?.message || t("profile.errorSaving"));
        } finally {
            setEmailSaving(false);
        }
    };

    const handlePasswordSave = async () => {
        if (newPassword !== confirmPassword) {
            setPwErr(t("profile.passwordMismatch"));
            return;
        }
        if (newPassword.length < 8) {
            setPwErr(t("profile.passwordTooShort"));
            return;
        }
        setPwSaving(true);
        setPwErr("");
        setPwMsg("");
        try {
            const res = await api.put("/auth/profile/password", { currentPassword, newPassword });
            setPwMsg(res.data.message || t("profile.passwordChanged"));
            setEditingPassword(false);
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
        } catch (e: any) {
            setPwErr(e.response?.data?.error || e.response?.data?.message || t("profile.errorSaving"));
        } finally {
            setPwSaving(false);
        }
    };

    return (
        <div className="container" style={{ maxWidth: 600, marginTop: "2rem" }}>
            <h2 className="mb-4">
                <i className="bi bi-person-circle me-2"></i>
                {t("profile.title")}
            </h2>

            {/* ── Profile Info Card ─────────────────── */}
            <div className="card mb-3">
                <div className="card-header d-flex justify-content-between align-items-center">
                    <strong>{t("profile.personalData")}</strong>
                    {!editingName && (
                        <button className="btn btn-sm btn-outline-primary" onClick={() => { setEditingName(true); setFirstName(user.firstName); setLastName(user.lastName); setNameMsg(""); setNameErr(""); }}>
                            <i className="bi bi-pencil me-1"></i>{t("profile.edit")}
                        </button>
                    )}
                </div>
                <div className="card-body">
                    {nameMsg && <div className="alert alert-success py-2">{nameMsg}</div>}
                    {nameErr && <div className="alert alert-danger py-2">{nameErr}</div>}

                    {editingName ? (
                        <>
                            <div className="mb-2">
                                <label className="form-label">{t("profile.firstName")}</label>
                                <input className="form-control" value={firstName} onChange={e => setFirstName(e.target.value)} />
                            </div>
                            <div className="mb-3">
                                <label className="form-label">{t("profile.lastName")}</label>
                                <input className="form-control" value={lastName} onChange={e => setLastName(e.target.value)} />
                            </div>
                            <div className="d-flex gap-2">
                                <button className="btn btn-primary" onClick={handleNameSave} disabled={nameSaving}>
                                    {nameSaving ? t("profile.saving") : t("profile.save")}
                                </button>
                                <button className="btn btn-outline-secondary" onClick={() => setEditingName(false)}>{t("profile.cancel")}</button>
                            </div>
                        </>
                    ) : (
                        <table className="table table-borderless mb-0">
                            <tbody>
                                <tr>
                                    <td className="text-muted" style={{ width: "40%" }}>{t("profile.name")}</td>
                                    <td><strong>{user.firstName} {user.lastName}</strong></td>
                                </tr>
                                <tr>
                                    <td className="text-muted">{t("profile.role")}</td>
                                    <td>
                                        <span className={`badge bg-${user.role === "ADMIN" ? "danger" : user.role === "TEACHER" ? "primary" : "success"}`}>
                                            {roleLabel}
                                        </span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            {/* ── Email Card ──────────────────────── */}
            <div className="card mb-3">
                <div className="card-header d-flex justify-content-between align-items-center">
                    <strong>{t("profile.emailSection")}</strong>
                    {!editingEmail && (
                        <button className="btn btn-sm btn-outline-primary" onClick={() => { setEditingEmail(true); setNewEmail(user.email); setEmailMsg(""); setEmailErr(""); }}>
                            <i className="bi bi-pencil me-1"></i>{t("profile.change")}
                        </button>
                    )}
                </div>
                <div className="card-body">
                    {emailMsg && <div className="alert alert-success py-2">{emailMsg}</div>}
                    {emailErr && <div className="alert alert-danger py-2">{emailErr}</div>}

                    {editingEmail ? (
                        <>
                            <div className="mb-3">
                                <label className="form-label">{t("profile.newEmail")}</label>
                                <input className="form-control" type="email" value={newEmail} onChange={e => setNewEmail(e.target.value)} />
                                <div className="form-text">{t("profile.emailHelper")}</div>
                            </div>
                            <div className="d-flex gap-2">
                                <button className="btn btn-primary" onClick={handleEmailSave} disabled={emailSaving}>
                                    {emailSaving ? t("profile.saving") : t("profile.changeEmail")}
                                </button>
                                <button className="btn btn-outline-secondary" onClick={() => setEditingEmail(false)}>{t("profile.cancel")}</button>
                            </div>
                        </>
                    ) : (
                        <p className="mb-0">{user.email}</p>
                    )}
                </div>
            </div>

            {/* ── Password Card ───────────────────── */}
            <div className="card mb-3">
                <div className="card-header d-flex justify-content-between align-items-center">
                    <strong>{t("profile.passwordSection")}</strong>
                    {!editingPassword && (
                        <button className="btn btn-sm btn-outline-primary" onClick={() => { setEditingPassword(true); setPwMsg(""); setPwErr(""); }}>
                            <i className="bi bi-key me-1"></i>{t("profile.change")}
                        </button>
                    )}
                </div>
                <div className="card-body">
                    {pwMsg && <div className="alert alert-success py-2">{pwMsg}</div>}
                    {pwErr && <div className="alert alert-danger py-2">{pwErr}</div>}

                    {editingPassword ? (
                        <>
                            <div className="mb-2">
                                <label className="form-label">{t("profile.currentPassword")}</label>
                                <input className="form-control" type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} />
                            </div>
                            <div className="mb-2">
                                <label className="form-label">{t("profile.newPassword")}</label>
                                <input className="form-control" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} />
                            </div>
                            <div className="mb-3">
                                <label className="form-label">{t("profile.confirmPassword")}</label>
                                <input className="form-control" type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} />
                            </div>
                            <div className="d-flex gap-2">
                                <button className="btn btn-primary" onClick={handlePasswordSave} disabled={pwSaving}>
                                    {pwSaving ? t("profile.saving") : t("profile.changePassword")}
                                </button>
                                <button className="btn btn-outline-secondary" onClick={() => { setEditingPassword(false); setCurrentPassword(""); setNewPassword(""); setConfirmPassword(""); }}>{t("profile.cancel")}</button>
                            </div>
                        </>
                    ) : (
                        <p className="mb-0 text-muted">********</p>
                    )}
                </div>
            </div>

            {/* ── My Resources (Teacher/Admin) ──── */}
            {(isTeacher || isAdmin) && (
                <>
                    <div className="card mb-3">
                        <div className="card-header"><strong>{t("profile.myWorksheets")}</strong></div>
                        <div className="card-body">
                            {loadingResources ? (
                                <p className="text-muted">{t("profile.loading")}</p>
                            ) : myWorksheets.length === 0 ? (
                                <p className="text-muted mb-0">{t("profile.noWorksheets")}</p>
                            ) : (
                                <div className="list-group list-group-flush">
                                    {myWorksheets.map(ws => (
                                        <Link key={ws.id} to={`/worksheets/${ws.id}`} className="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
                                            <div>
                                                <strong>{ws.name}</strong>
                                                {ws.description && <small className="text-muted d-block">{ws.description}</small>}
                                            </div>
                                            <i className="bi bi-chevron-right"></i>
                                        </Link>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="card mb-3">
                        <div className="card-header"><strong>{t("profile.mySequences")}</strong></div>
                        <div className="card-body">
                            {loadingResources ? (
                                <p className="text-muted">{t("profile.loading")}</p>
                            ) : mySequences.length === 0 ? (
                                <p className="text-muted mb-0">{t("profile.noSequences")}</p>
                            ) : (
                                <div className="list-group list-group-flush">
                                    {mySequences.map(seq => (
                                        <Link key={seq.id} to={`/sequence/${seq.id}`} className="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
                                            <div>
                                                <strong>{seq.programmingLanguage}</strong>
                                                <small className="text-muted d-block">{seq.taskDescription?.substring(0, 80)}{seq.taskDescription && seq.taskDescription.length > 80 ? "..." : ""}</small>
                                            </div>
                                            <i className="bi bi-chevron-right"></i>
                                        </Link>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </>
            )}

            {/* ── Logout ──────────────────────────── */}
            <button className="btn btn-outline-danger w-100 mt-2 mb-4" onClick={() => { logout(); navigate("/"); }}>
                <i className="bi bi-box-arrow-right me-2"></i>{t("profile.logout")}
            </button>
        </div>
    );
}
