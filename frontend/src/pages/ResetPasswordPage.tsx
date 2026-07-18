import { useState, type FormEvent } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { api } from "../lib/api";
import { useTranslation } from "react-i18next";

export default function ResetPasswordPage() {
    const { t } = useTranslation();
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") || "";
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [success, setSuccess] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");

        if (password !== confirmPassword) {
            setError(t("resetPassword.passwordMismatch"));
            return;
        }
        if (password.length < 8) {
            setError(t("resetPassword.passwordTooShort"));
            return;
        }

        setLoading(true);
        try {
            await api.post("/auth/reset-password", { token, newPassword: password });
            setSuccess(true);
        } catch (err: any) {
            setError(err.response?.data?.error || t("resetPassword.failed"));
        } finally {
            setLoading(false);
        }
    };

    if (!token) {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <div className="alert alert-danger">{t("resetPassword.noToken")}</div>
                <Link to="/login" className="btn btn-primary w-100">{t("resetPassword.toLogin")}</Link>
            </div>
        );
    }

    if (success) {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <div className="alert alert-success">
                    {t("resetPassword.success")}
                </div>
                <Link to="/login" className="btn btn-primary w-100">{t("resetPassword.toLogin")}</Link>
            </div>
        );
    }

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("resetPassword.title")}</h2>

            {error && <div className="alert alert-danger">{error}</div>}

            <form onSubmit={handleSubmit}>
                <div className="mb-3">
                    <label className="form-label">{t("resetPassword.newPassword")}</label>
                    <input type="password" className="form-control" value={password}
                           onChange={(e) => setPassword(e.target.value)} required minLength={8} />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("resetPassword.confirmPassword")}</label>
                    <input type="password" className="form-control" value={confirmPassword}
                           onChange={(e) => setConfirmPassword(e.target.value)} required />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("resetPassword.submitting") : t("resetPassword.submit")}
                </button>
            </form>
        </div>
    );
}
