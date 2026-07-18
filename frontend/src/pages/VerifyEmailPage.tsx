import { useEffect, useState } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { api } from "../lib/api";
import { useTranslation } from "react-i18next";

export default function VerifyEmailPage() {
    const { t } = useTranslation();
    const [searchParams] = useSearchParams();
    const [status, setStatus] = useState<"loading" | "success" | "error">("loading");
    const [message, setMessage] = useState("");

    useEffect(() => {
        const token = searchParams.get("token");
        if (!token) {
            setStatus("error");
            setMessage(t("verify.noToken"));
            return;
        }

        api.post("/auth/verify-email", { token })
            .then(() => {
                setStatus("success");
                setMessage(t("verify.success"));
            })
            .catch((err) => {
                setStatus("error");
                setMessage(err.response?.data?.error || t("verify.failed"));
            });
    }, [searchParams]);

    return (
        <div className="container" style={{ maxWidth: 440, marginTop: "5rem" }}>
            <h2 className="text-center mb-4">{t("verify.title")}</h2>

            {status === "loading" && (
                <div className="text-center">
                    <div className="spinner-border text-primary" role="status" />
                    <p className="mt-2">{t("verify.verifying")}</p>
                </div>
            )}

            {status === "success" && (
                <div className="alert alert-success">{message}</div>
            )}

            {status === "error" && (
                <div className="alert alert-danger">{message}</div>
            )}

            <Link to="/login" className="btn btn-primary w-100 mt-3">{t("verify.toLogin")}</Link>
        </div>
    );
}
