import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useTranslation } from "react-i18next";

export default function StudentOnboardingPage() {
    const { t } = useTranslation();
    const [step, setStep] = useState<"token" | "form" | "done">("token");
    const [tokenInput, setTokenInput] = useState("");
    const [studentName, setStudentName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleTokenSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            const res = await api.post("/auth/validate-token", { token: tokenInput });
            setStudentName(`${res.data.firstName} ${res.data.lastName}`);
            setStep("form");
        } catch (err: any) {
            setError(err.response?.data?.error || t("studentOnboarding.invalidToken"));
        } finally {
            setLoading(false);
        }
    };

    const handleOnboardingSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");

        if (password !== confirmPassword) {
            setError(t("studentOnboarding.passwordMismatch"));
            return;
        }
        if (password.length < 8) {
            setError(t("studentOnboarding.passwordTooShort"));
            return;
        }

        setLoading(true);
        try {
            await api.post("/auth/student-onboarding", {
                token: tokenInput,
                email,
                password,
            });
            setStep("done");
        } catch (err: any) {
            setError(err.response?.data?.error || t("studentOnboarding.setupFailed"));
        } finally {
            setLoading(false);
        }
    };

    if (step === "done") {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <div className="alert alert-success">
                    <h5>{t("studentOnboarding.setupSuccess")}</h5>
                    <p>{t("studentOnboarding.setupSuccessMsg")}</p>
                </div>
                <Link to="/login" className="btn btn-primary w-100">{t("resetPassword.toLogin")}</Link>
            </div>
        );
    }

    if (step === "token") {
        return (
            <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
                <h2 className="text-center mb-4">{t("studentOnboarding.title")}</h2>
                <p className="text-muted text-center">
                    {t("studentOnboarding.helper")}
                </p>

                {error && <div className="alert alert-danger">{error}</div>}

                <form onSubmit={handleTokenSubmit}>
                    <div className="mb-3">
                        <label className="form-label">{t("studentOnboarding.token")}</label>
                        <input
                            type="text"
                            className="form-control text-center"
                            style={{ fontSize: "1.3rem", letterSpacing: "0.1em" }}
                            placeholder={t("studentOnboarding.tokenPlaceholder")}
                            value={tokenInput}
                            onChange={(e) => setTokenInput(e.target.value.trim())}
                            required
                        />
                    </div>
                    <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                        {loading ? t("studentOnboarding.checking") : t("studentOnboarding.submit")}
                    </button>
                </form>

                <div className="text-center mt-3">
                    <Link to="/login">{t("studentOnboarding.backToLogin")}</Link>
                </div>
            </div>
        );
    }

    // step === "form"
    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("studentOnboarding.setupTitle")}</h2>
            <p className="text-center">
                {t("studentOnboarding.welcome", { name: studentName })}
            </p>

            {error && <div className="alert alert-danger">{error}</div>}

            <form onSubmit={handleOnboardingSubmit}>
                <div className="mb-3">
                    <label className="form-label">{t("studentOnboarding.email")}</label>
                    <input type="email" className="form-control" value={email}
                           onChange={(e) => setEmail(e.target.value)} required />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("studentOnboarding.password")}</label>
                    <input type="password" className="form-control" value={password}
                           onChange={(e) => setPassword(e.target.value)} required minLength={8} />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("studentOnboarding.confirmPassword")}</label>
                    <input type="password" className="form-control" value={confirmPassword}
                           onChange={(e) => setConfirmPassword(e.target.value)} required />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("studentOnboarding.settingUp") : t("studentOnboarding.setupBtn")}
                </button>
            </form>
        </div>
    );
}
