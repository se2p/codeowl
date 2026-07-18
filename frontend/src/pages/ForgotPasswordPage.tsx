import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useTranslation } from "react-i18next";

export default function ForgotPasswordPage() {
    const { t } = useTranslation();
    const [email, setEmail] = useState("");
    const [sent, setSent] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setLoading(true);
        try {
            await api.post("/auth/forgot-password", { email });
        } catch {
            // Always show success to not reveal email existence
        }
        setSent(true);
        setLoading(false);
    };

    if (sent) {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <div className="alert alert-success">
                    {t("forgotPassword.success")}
                </div>
                <Link to="/login" className="btn btn-primary w-100">{t("forgotPassword.backToLogin")}</Link>
            </div>
        );
    }

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("forgotPassword.title")}</h2>

            <form onSubmit={handleSubmit}>
                <div className="mb-3">
                    <label className="form-label">{t("forgotPassword.email")}</label>
                    <input type="email" className="form-control" value={email}
                           onChange={(e) => setEmail(e.target.value)} required />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("forgotPassword.submitting") : t("forgotPassword.submit")}
                </button>
            </form>

            <div className="text-center mt-3">
                <Link to="/login">{t("forgotPassword.backToLogin")}</Link>
            </div>
        </div>
    );
}
