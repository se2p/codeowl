import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, apiErrorMessage } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import type { Worksheet } from "../types/models";
import { usePageTitle } from "../hooks/usePageTitle";

export default function WorksheetsPage() {
    const { t } = useTranslation();
    usePageTitle(t("worksheets.pageTitle"));
    const { isLoggedIn, isStudent } = useAuth();
    const navigate = useNavigate();

    const [worksheets, setWorksheets] = useState<Worksheet[]>([]);
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
        loadWorksheets();
    }, [isLoggedIn]);

    const loadWorksheets = async () => {
        try {
            setLoading(true);
            const res = await api.get<Worksheet[]>("/worksheets/my");
            setWorksheets(res.data);
        } catch (e) {
            setError(apiErrorMessage(e, t("worksheets.errorLoading")));
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newName.trim()) return;

        try {
            setCreating(true);
            const res = await api.post<Worksheet>("/worksheets", {
                name: newName.trim(),
                description: newDesc.trim(),
            });
            setWorksheets([res.data, ...worksheets]);
            setNewName("");
            setNewDesc("");
            setShowCreate(false);
        } catch (e) {
            setError(apiErrorMessage(e, t("worksheets.errorCreating")));
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (worksheetId: string) => {
        if (!confirm(t("worksheets.deleteConfirm"))) return;

        try {
            await api.delete(`/worksheets/${worksheetId}`);
            setWorksheets(worksheets.filter((w) => w.id !== worksheetId));
        } catch (e) {
            setError(apiErrorMessage(e, t("worksheets.errorDeleting")));
        }
    };

    if (loading) {
        return (
            <div className="container py-5 text-center">
                <div className="spinner-border" role="status">
                    <span className="visually-hidden">{t("worksheets.loading")}</span>
                </div>
            </div>
        );
    }

    return (
        <div className="container py-4" style={{ maxWidth: 800 }}>
            <div className="d-flex justify-content-between align-items-center mb-4">
                <h2 className="mb-0">
                    <i className="bi bi-journal-text me-2"></i>
                    {t("worksheets.title")}
                </h2>
                {!isStudent && (
                    <button
                        className="btn btn-primary"
                        onClick={() => setShowCreate(!showCreate)}
                    >
                        <i className="bi bi-plus-circle me-1"></i>
                        {t("worksheets.newWorksheet")}
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
                        <h5 className="card-title">{t("worksheets.createTitle")}</h5>
                        <form onSubmit={handleCreate}>
                            <div className="mb-3">
                                <label className="form-label">{t("worksheets.name")}</label>
                                <input
                                    type="text"
                                    className="form-control"
                                    value={newName}
                                    onChange={(e) => setNewName(e.target.value)}
                                    placeholder={t("worksheets.namePlaceholder")}
                                    required
                                />
                            </div>
                            <div className="mb-3">
                                <label className="form-label">{t("worksheets.description")}</label>
                                <textarea
                                    className="form-control"
                                    value={newDesc}
                                    onChange={(e) => setNewDesc(e.target.value)}
                                    placeholder={t("worksheets.descPlaceholder")}
                                    rows={2}
                                />
                            </div>
                            <div className="d-flex gap-2">
                                <button type="submit" className="btn btn-success" disabled={creating}>
                                    {creating ? t("worksheets.creating") : t("worksheets.create")}
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

            {/* Worksheet List */}
            {worksheets.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox" style={{ fontSize: "3rem" }}></i>
                    <p className="mt-3">{t("worksheets.empty")}</p>
                </div>
            ) : (
                <div className="row g-3">
                    {worksheets.map((w) => (
                        <div key={w.id} className="col-12">
                            <div
                                className="card hover-shadow"
                                style={{ cursor: "pointer" }}
                                onClick={() => navigate(`/worksheets/${w.id}`)}
                            >
                                <div className="card-body d-flex justify-content-between align-items-center">
                                    <div>
                                        <h5 className="card-title mb-1">{w.name}</h5>
                                        {w.description && (
                                            <p className="card-text text-muted small mb-0">
                                                {w.description}
                                            </p>
                                        )}
                                        <p className="card-text text-muted small mb-0">
                                            {t("worksheets.sequenceCount", { count: w.sequenceDTOS?.length ?? 0 })}
                                        </p>
                                    </div>
                                    <div className="d-flex align-items-center gap-3">
                                        <button
                                            className="btn btn-sm btn-outline-danger"
                                            title={t("worksheets.deleteWorksheet")}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                handleDelete(w.id);
                                            }}
                                        >
                                            <i className="bi bi-trash"></i>
                                        </button>
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
