import { useState, type FormEvent } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import { loginLandingPath } from "../lib/homePath.ts";

// Login with a FIM-Account (University of Passau). The backend checks the
// credentials against the FIM LDAP server.
export default function FimLoginPage() {
    const { t } = useTranslation();
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { loginWithFim } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            const result = await loginWithFim(username, password);
            navigate(loginLandingPath(result.role));
        } catch (err) {
            const data = (err as { response?: { data?: { error?: string } } }).response?.data;
            setError(data?.error || t("fimLogin.failed"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("fimLogin.title")}</h2>

            {error && <div className="alert alert-danger">{error}</div>}

            <form onSubmit={handleSubmit}>
                <div className="mb-3">
                    <label className="form-label">{t("fimLogin.username")}</label>
                    <input
                        type="text"
                        className="form-control"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        autoComplete="username"
                        autoCapitalize="none"
                        required
                    />
                </div>
                <div className="mb-3">
                    <label className="form-label">{t("fimLogin.password")}</label>
                    <input
                        type="password"
                        className="form-control"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        autoComplete="current-password"
                        required
                    />
                </div>
                <button type="submit" className="btn btn-primary w-100" disabled={loading}>
                    {loading ? t("fimLogin.submitting") : t("fimLogin.submit")}
                </button>
            </form>

            <div className="text-center mt-3">
                <Link to="/login">{t("fimLogin.backToLogin")}</Link>
            </div>
        </div>
    );
}
