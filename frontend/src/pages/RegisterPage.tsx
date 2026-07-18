import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useTranslation } from "react-i18next";

export default function RegisterPage() {
    const { t } = useTranslation();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [success, setSuccess] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");

        if (password !== confirmPassword) {
            setError(t("register.passwordMismatch"));
            return;
        }
        if (password.length < 8) {
            setError(t("register.passwordTooShort"));
            return;
        }

        setLoading(true);
        try {
            await api.post("/auth/register", { email, password, firstName, lastName });
            setSuccess(true);
        } catch (err: any) {
            setError(err.response?.data?.error || t("register.failed"));
        } finally {
            setLoading(false);
        }
    };

    if (success) {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <div className="alert alert-success">
                    <h5>{t("register.successTitle")}</h5>
                    <p>{t("register.successMsg")}</p>
                </div>
                <Link to="/login" className="btn btn-primary w-100">{t("register.toLogin")}</Link>
            </div>
        );
    }

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("register.title")}</h2>

            {error && <div className="alert alert-danger">{error}</div>}

            <form onSubmit={handleSubmit}>
                <div className="row mb-3">
                    <div className="col">
                        <label className="form-label">{t("register.firstName")}</label>
                        <input type="text" className="form-control" value={firstName}
                               onChange={(e) => setFirstName(e.target.value)} required />
                    </div>
                    <div className="col">
                        <label className="form-label">{t("register.lastName")}</label>
                        <input type="text" className="form-control" value={lastName}
                               onChange={(e) => setLastName(e.target.value)} required />
                    </div>
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("register.email")}</label>
                    <input type="email" className="form-control" value={email}
                           onChange={(e) => setEmail(e.target.value)} required />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("register.password")}</label>
                    <input type="password" className="form-control" value={password}
                           onChange={(e) => setPassword(e.target.value)} required minLength={8} />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("register.confirmPassword")}</label>
                    <input type="password" className="form-control" value={confirmPassword}
                           onChange={(e) => setConfirmPassword(e.target.value)} required />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("register.submitting") : t("register.submit")}
                </button>
            </form>

            <div className="text-center mt-3">
                <span className="text-muted">{t("register.alreadyRegistered")}</span>
                <Link to="/login">{t("register.login")}</Link>
            </div>
        </div>
    );
}
