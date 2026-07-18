import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";

/**
 * Shown when a teacher has set a temporary password for a student.
 * The student must choose a new password before continuing.
 * Does NOT require the old password.
 */
export default function ChangePasswordPage() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const { isLoggedIn, mustChangePassword, clearMustChangePassword } = useAuth();

    const [newPassword, setNewPassword] = useState("");
    const [confirm, setConfirm] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    // If user is not logged in or doesn't need to change password, redirect away
    if (!isLoggedIn) {
        navigate("/login", { replace: true });
        return null;
    }
    if (!mustChangePassword) {
        navigate("/", { replace: true });
        return null;
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");

        if (newPassword.length < 8) {
            setError(t("changePassword.tooShort"));
            return;
        }
        if (newPassword !== confirm) {
            setError(t("changePassword.mismatch"));
            return;
        }

        try {
            setLoading(true);
            await api.post("/auth/change-temp-password", { newPassword });
            clearMustChangePassword();
            navigate("/", { replace: true });
        } catch (e: any) {
            setError(e.response?.data?.error || t("changePassword.error"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="container py-5" style={{ maxWidth: 480 }}>
            <div className="card shadow-sm border-0">
                <div className="card-body p-4">
                    <div className="text-center mb-4">
                        <i className="bi bi-key-fill text-warning" style={{ fontSize: "2.5rem" }}></i>
                        <h4 className="mt-2 fw-bold">{t("changePassword.title")}</h4>
                        <p className="text-muted small">{t("changePassword.subtitle")}</p>
                    </div>

                    {error && (
                        <div className="alert alert-danger py-2">{error}</div>
                    )}

                    <form onSubmit={handleSubmit}>
                        <div className="mb-3">
                            <label className="form-label fw-semibold">
                                {t("changePassword.newPassword")}
                            </label>
                            <input
                                type="password"
                                className="form-control"
                                value={newPassword}
                                onChange={e => setNewPassword(e.target.value)}
                                minLength={8}
                                required
                                autoFocus
                            />
                            <div className="form-text">{t("changePassword.minLength")}</div>
                        </div>

                        <div className="mb-4">
                            <label className="form-label fw-semibold">
                                {t("changePassword.confirm")}
                            </label>
                            <input
                                type="password"
                                className="form-control"
                                value={confirm}
                                onChange={e => setConfirm(e.target.value)}
                                required
                            />
                        </div>

                        <button
                            type="submit"
                            className="btn btn-primary w-100"
                            disabled={loading}
                        >
                            {loading ? (
                                <span className="spinner-border spinner-border-sm me-2" />
                            ) : (
                                <i className="bi bi-check-circle me-2"></i>
                            )}
                            {t("changePassword.submit")}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}
