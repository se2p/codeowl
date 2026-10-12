import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import { loginLandingPath } from "../lib/homePath.ts";

export default function LoginPage() {
    const { t } = useTranslation();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            const result = await login(email, password);

            // Teacher set a temporary password — must change it first
            if (result.mustChangePassword) {
                navigate("/change-password", { replace: true });
                return;
            }

            // Everyone starts on their classes page: it is where the normal
            // workflow begins for both roles. Students used to be dropped
            // straight into a class when they had exactly one, which made the
            // landing page depend on how many classes they happened to be in.
            navigate(loginLandingPath(result.role));
        } catch (err: any) {
            setError(err.response?.data?.error || t("login.failed"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("login.title")}</h2>

            {error && <div className="alert alert-danger">{error}</div>}

            <form onSubmit={handleSubmit}>
                <div className="mb-3">
                    <label className="form-label">{t("login.email")}</label>
                    <input
                        type="email"
                        className="form-control"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("login.password")}</label>
                    <input
                        type="password"
                        className="form-control"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("login.submitting") : t("login.submit")}
                </button>
            </form>

            <Link to="/login/fim" className="btn btn-outline-primary w-100 mt-2">
                <i className="bi bi-mortarboard me-1"></i>
                {t("login.fimAccount")}
            </Link>

            <div className="text-center mt-3">
                <Link to="/forgot-password">{t("login.forgotPassword")}</Link>
            </div>
            <div className="text-center mt-2">
                <span className="text-muted">{t("login.noAccount")}</span>
                <Link to="/register">{t("login.register")}</Link>
            </div>
            <hr />
            <div className="text-center">
                <Link to="/student-onboarding" className="btn btn-outline-secondary btn-sm">
                    {t("login.studentToken")}
                </Link>
            </div>
        </div>
    );
}
