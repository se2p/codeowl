import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import type { ClassInfo } from "../types/models";

export default function ClassesPage() {
    const { t } = useTranslation();
    const { isLoggedIn, isStudent, isAdmin } = useAuth();
    const navigate = useNavigate();

    const [classes, setClasses] = useState<ClassInfo[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    // Create form
    const [showCreate, setShowCreate] = useState(false);
    const [newName, setNewName] = useState("");
    const [newDesc, setNewDesc] = useState("");
    const [creating, setCreating] = useState(false);

    useEffect(() => {
        if (!isLoggedIn) {
            navigate("/login");
            return;
        }
        loadClasses();
    }, [isLoggedIn]);

    const loadClasses = async () => {
        try {
            setLoading(true);
            const res = await api.get<ClassInfo[]>("/classes");
            setClasses(res.data);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classes.errorLoading"));
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newName.trim()) return;

        try {
            setCreating(true);
            const res = await api.post<ClassInfo>("/classes", {
                name: newName.trim(),
                description: newDesc.trim(),
            });
            setClasses([res.data, ...classes]);
            setNewName("");
            setNewDesc("");
            setShowCreate(false);
        } catch (e: any) {
            setError(e.response?.data?.error || t("classes.errorCreating"));
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (classId: string) => {
        if (!confirm(t("classes.deleteConfirm"))) return;

        try {
            await api.delete(`/classes/${classId}`);
            setClasses(classes.filter((c) => c.id !== classId));
        } catch (e: any) {
            setError(e.response?.data?.error || t("classes.errorDeleting"));
        }
    };

    if (loading) {
        return (
            <div className="container py-5 text-center">
                <div className="spinner-border" role="status">
                    <span className="visually-hidden">{t("classes.loading")}</span>
                </div>
            </div>
        );
    }

    return (
        <div className="container py-4" style={{ maxWidth: 800 }}>
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2 className="mb-0">
                    <i className="bi bi-people me-2"></i>
                    {isStudent ? t("classes.titleStudent") : t("classes.titleTeacher")}
                </h2>
                {!isStudent && (
                    <button
                        className="btn btn-primary"
                        onClick={() => setShowCreate(!showCreate)}
                    >
                        <i className="bi bi-plus-circle me-1"></i>
                        {t("classes.newClass")}
                    </button>
                )}
            </div>

            {error && (
                <div className="alert alert-danger alert-dismissible">
                    {error}
                    <button className="btn-close" onClick={() => setError("")}></button>
                </div>
            )}

            {/* Create Form */}
            {showCreate && (
                <div className="card mb-4">
                    <div className="card-body">
                        <h5 className="card-title">{t("classes.createTitle")}</h5>
                        <form onSubmit={handleCreate}>
                            <div className="mb-3">
                                <label className="form-label">{t("classes.name")}</label>
                                <input
                                    type="text"
                                    className="form-control"
                                    value={newName}
                                    onChange={(e) => setNewName(e.target.value)}
                                    placeholder={t("classes.namePlaceholder")}
                                    required
                                />
                            </div>
                            <div className="mb-3">
                                <label className="form-label">{t("classes.description")}</label>
                                <textarea
                                    className="form-control"
                                    value={newDesc}
                                    onChange={(e) => setNewDesc(e.target.value)}
                                    placeholder={t("classes.descPlaceholder")}
                                    rows={2}
                                />
                            </div>
                            <div className="d-flex gap-2">
                                <button type="submit" className="btn btn-success" disabled={creating}>
                                    {creating ? t("classes.creating") : t("classes.create")}
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-outline-secondary"
                                    onClick={() => setShowCreate(false)}
                                >
                                    {t("common.cancel")}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Class List */}
            {classes.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox" style={{ fontSize: "3rem" }}></i>
                    <p className="mt-3">
                        {isStudent
                            ? t("classes.emptyStudent")
                            : t("classes.emptyTeacher")}
                    </p>
                </div>
            ) : (
                <div className="row g-3">
                    {classes.map((c) => (
                        <div key={c.id} className="col-12">
                            <div
                                className="card hover-shadow"
                                style={{ cursor: "pointer" }}
                                onClick={() => navigate(`/classes/${c.id}`)}
                            >
                                <div className="card-body d-flex justify-content-between align-items-center">
                                    <div>
                                        <h5 className="card-title mb-1">{c.name}</h5>
                                        {c.description && (
                                            <p className="card-text text-muted small mb-0">
                                                {c.description}
                                            </p>
                                        )}
                                        {isAdmin && (
                                            <p className="card-text text-muted small mb-0">
                                                <i className="bi bi-person me-1"></i>
                                                {t("classes.createdBy", {
                                                    name: c.ownerName || c.ownerEmail || t("classes.unknownOwner"),
                                                })}
                                                {c.ownerName && c.ownerEmail && (
                                                    <span className="ms-1">({c.ownerEmail})</span>
                                                )}
                                            </p>
                                        )}
                                    </div>
                                    <div className="d-flex align-items-center gap-3">
                                        {!isStudent && (
                                            <button
                                                className="btn btn-sm btn-outline-danger"
                                                title={t("classes.deleteClass")}
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleDelete(c.id);
                                                }}
                                            >
                                                <i className="bi bi-trash"></i>
                                            </button>
                                        )}
                                        <i className="bi bi-chevron-right text-muted"></i>
                                    </div>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
