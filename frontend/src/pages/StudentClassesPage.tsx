import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import type { ClassInfo } from "../types/models";

export default function StudentClassesPage() {
    const { t } = useTranslation();
    const { isLoggedIn, isStudent } = useAuth();
    const navigate = useNavigate();

    const [classes, setClasses] = useState<ClassInfo[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!isLoggedIn) {
            navigate("/login");
            return;
        }
        if (!isStudent) {
            navigate("/");
            return;
        }
        loadClasses();
    }, [isLoggedIn, isStudent]);

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
        <div className="container py-4" style={{ maxWidth: 700 }}>
            <h2 className="mb-4">
                <i className="bi bi-people me-2"></i>
                {t("classes.titleStudent")}
            </h2>

            {error && (
                <div className="alert alert-danger alert-dismissible">
                    {error}
                    <button className="btn-close" onClick={() => setError("")}></button>
                </div>
            )}

            {classes.length === 0 ? (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-inbox" style={{ fontSize: "3rem" }}></i>
                    <p className="mt-3">{t("classes.emptyStudent")}</p>
                </div>
            ) : (
                <div className="row g-3">
                    {classes.map((c) => (
                        <div key={c.id} className="col-12 col-md-6">
                            <div
                                className="card h-100 hover-shadow"
                                style={{ cursor: "pointer" }}
                                onClick={() => navigate(`/classes/${c.id}`)}
                            >
                                <div className="card-body d-flex flex-column">
                                    <h5 className="card-title mb-1">
                                        <i className="bi bi-mortarboard me-2 text-primary"></i>
                                        {c.name}
                                    </h5>
                                    {c.description && (
                                        <p className="card-text text-muted small mb-0 mt-1">
                                            {c.description}
                                        </p>
                                    )}
                                    <div className="mt-auto pt-2 text-end">
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
