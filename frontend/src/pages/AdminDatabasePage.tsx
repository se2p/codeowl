import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { useAuth } from "../context/AuthContext";

interface AuditEntry {
    id: number;
    adminId: string;
    action: string;
    ipAddress: string | null;
    bytesSent: number | null;
    rowsSent: number | null;
    success: boolean;
    errorMsg: string | null;
    createdAt: string;
}

function formatBytes(bytes: number | null): string {
    if (bytes == null) return "—";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function AdminDatabasePage() {
    const { t } = useTranslation();
    const { isAdmin } = useAuth();

    const [downloading, setDownloading] = useState<"backup" | "research" | null>(null);
    const [error, setError] = useState("");
    const [auditEntries, setAuditEntries] = useState<AuditEntry[]>([]);
    const [loadingAudit, setLoadingAudit] = useState(false);

    if (!isAdmin) return <Navigate to="/" replace />;

    // eslint-disable-next-line react-hooks/rules-of-hooks
    useEffect(() => {
        loadAudit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadAudit = async () => {
        setLoadingAudit(true);
        try {
            const res = await api.get<AuditEntry[]>("/admin/audit-log?limit=100");
            setAuditEntries(res.data);
        } catch {
            // silently ignore — audit table may be empty on fresh installs
        } finally {
            setLoadingAudit(false);
        }
    };

    const triggerDownload = async (
        kind: "backup" | "research",
        method: "post" | "get",
        url: string,
        filenameFallback: string
    ) => {
        setDownloading(kind);
        setError("");
        try {
            const res =
                method === "post"
                    ? await api.post(url, null, { responseType: "blob", timeout: 0 })
                    : await api.get(url, { responseType: "blob", timeout: 0 });

            // Try to read filename from Content-Disposition; fall back to generated name
            const cd = (res.headers["content-disposition"] as string) || "";
            const match = cd.match(/filename="?([^";]+)"?/i);
            const filename = match?.[1] || filenameFallback;

            const blobUrl = URL.createObjectURL(res.data as Blob);
            const a = document.createElement("a");
            a.href = blobUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(blobUrl);

            // Refresh audit log to show the new entry
            loadAudit();
        } catch (e: any) {
            if (e.response?.status === 503) {
                setError(t("adminDb.backupBusy"));
            } else {
                setError(e.response?.data?.error || t("adminDb.downloadError"));
            }
        } finally {
            setDownloading(null);
        }
    };

    const nowTs = () => new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);

    return (
        <div className="container py-4" style={{ maxWidth: 900 }}>
            <h2 className="mb-1">{t("adminDb.title")}</h2>
            <p className="text-muted mb-4">{t("adminDb.subtitle")}</p>

            {error && (
                <div className="alert alert-danger alert-dismissible" role="alert">
                    {error}
                    <button
                        type="button"
                        className="btn-close"
                        onClick={() => setError("")}
                        aria-label="Close"
                    />
                </div>
            )}

            <div className="row g-4 mb-5">
                {/* ── Card 1: Research Export ── */}
                <div className="col-md-6">
                    <div className="card h-100 border-primary">
                        <div className="card-body d-flex flex-column">
                            <h5 className="card-title">
                                <i className="bi bi-file-earmark-zip me-2 text-primary"></i>
                                {t("adminDb.researchTitle")}
                            </h5>
                            <p className="card-text text-muted flex-fill">
                                {t("adminDb.researchDesc")}
                            </p>
                            <button
                                className="btn btn-primary mt-3"
                                disabled={downloading !== null}
                                onClick={() =>
                                    triggerDownload(
                                        "research",
                                        "get",
                                        "/admin/research-export",
                                        `codeowl-research-${nowTs()}.zip`
                                    )
                                }
                            >
                                {downloading === "research" ? (
                                    <>
                                        <span className="spinner-border spinner-border-sm me-2" role="status" />
                                        {t("adminDb.downloading")}
                                    </>
                                ) : (
                                    <>
                                        <i className="bi bi-download me-2"></i>
                                        {t("adminDb.researchBtn")}
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>

                {/* ── Card 2: Full Backup ── */}
                <div className="col-md-6">
                    <div className="card h-100 border-danger">
                        <div className="card-body d-flex flex-column">
                            <h5 className="card-title">
                                <i className="bi bi-database-down me-2 text-danger"></i>
                                {t("adminDb.backupTitle")}
                            </h5>
                            <p className="card-text text-muted flex-fill">
                                {t("adminDb.backupDesc")}
                            </p>
                            <p className="card-text">
                                <small className="text-secondary fst-italic">
                                    {t("adminDb.restoreHint")}
                                </small>
                            </p>
                            <button
                                className="btn btn-danger mt-3"
                                disabled={downloading !== null}
                                onClick={() =>
                                    triggerDownload(
                                        "backup",
                                        "post",
                                        "/admin/backup",
                                        `codeowl-backup-${nowTs()}.dump`
                                    )
                                }
                            >
                                {downloading === "backup" ? (
                                    <>
                                        <span className="spinner-border spinner-border-sm me-2" role="status" />
                                        {t("adminDb.downloading")}
                                    </>
                                ) : (
                                    <>
                                        <i className="bi bi-download me-2"></i>
                                        {t("adminDb.backupBtn")}
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* ── Audit Log ── */}
            <div className="d-flex align-items-center justify-content-between mb-2">
                <h5 className="mb-0">{t("adminDb.auditTitle")}</h5>
                <button
                    className="btn btn-sm btn-outline-secondary"
                    onClick={loadAudit}
                    disabled={loadingAudit}
                >
                    {loadingAudit ? (
                        <span className="spinner-border spinner-border-sm" role="status" />
                    ) : (
                        <><i className="bi bi-arrow-clockwise me-1"></i>{t("adminDb.auditRefresh")}</>
                    )}
                </button>
            </div>

            {auditEntries.length === 0 && !loadingAudit ? (
                <p className="text-muted fst-italic">{t("adminDb.auditEmpty")}</p>
            ) : (
                <div className="table-responsive">
                    <table className="table table-sm table-hover align-middle">
                        <thead className="table-light">
                            <tr>
                                <th>{t("adminDb.auditDate")}</th>
                                <th>{t("adminDb.auditAdmin")}</th>
                                <th>{t("adminDb.auditAction")}</th>
                                <th>{t("adminDb.auditStatus")}</th>
                                <th>{t("adminDb.auditSize")}</th>
                                <th>{t("adminDb.auditRows")}</th>
                                <th>{t("adminDb.auditError")}</th>
                            </tr>
                        </thead>
                        <tbody>
                            {auditEntries.map((e) => (
                                <tr key={e.id}>
                                    <td>
                                        <small className="text-nowrap">
                                            {e.createdAt
                                                ? new Date(e.createdAt).toLocaleString()
                                                : "—"}
                                        </small>
                                    </td>
                                    <td>
                                        <code style={{ fontSize: "0.8em" }}>
                                            {e.adminId?.slice(0, 8)}…
                                        </code>
                                    </td>
                                    <td>
                                        <span className={`badge ${e.action === "FULL_BACKUP" ? "bg-danger" : "bg-primary"}`}>
                                            {e.action}
                                        </span>
                                    </td>
                                    <td>
                                        {e.success ? (
                                            <span className="text-success fw-semibold">
                                                <i className="bi bi-check-circle me-1"></i>
                                                {t("adminDb.success")}
                                            </span>
                                        ) : (
                                            <span className="text-danger fw-semibold">
                                                <i className="bi bi-x-circle me-1"></i>
                                                {t("adminDb.failed")}
                                            </span>
                                        )}
                                    </td>
                                    <td>{formatBytes(e.bytesSent)}</td>
                                    <td>{e.rowsSent ?? "—"}</td>
                                    <td>
                                        {e.errorMsg ? (
                                            <small className="text-danger">{e.errorMsg}</small>
                                        ) : (
                                            <span className="text-muted">—</span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
