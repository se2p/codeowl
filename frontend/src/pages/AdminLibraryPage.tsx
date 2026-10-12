import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";

/**
 * AP19 Teil A A16 + A7: Admin "library curation" dashboard.
 *
 * <p>Four widgets:
 * <ol>
 *   <li><b>New topics</b> — tags whose first appearance in the system fell inside a configurable
 *       time window.</li>
 *   <li><b>Merge tags</b> — free-form tool to rename a tag across every sequence.</li>
 *   <li><b>Bulk-Retagger</b> — fire-and-forget job that re-runs LLM tagging on all sequences
 *       that are missing semantic tags, with live progress polling.</li>
 *   <li><b>Bulk concept re-classifier</b> — the concept-facet mirror of the retagger: re-runs
 *       LLM concept detection so material generated before a concept existed (a teacher's
 *       free-typed one, or a newly-mapped curriculum concept) can still pick it up.</li>
 * </ol>
 */

/** One row of the new-topics report. Matches {@code SequenceTagRepository.NewTopic}. */
interface NewTopic {
    tagName: string;
    firstSeen: string; // ISO-8601
    usageCount: number;
}

/** Response of {@code POST /api/admin/tags/merge}. */
interface MergeResponse {
    from: string;
    to: string;
    affected: number;
}

/** Snapshot of a bulk-retag job. Matches {@code BulkRetagJobStatus}. */
interface BulkRetagJob {
    jobId: string;
    status: "QUEUED" | "RUNNING" | "DONE" | "FAILED";
    filter: string;
    total: number;
    processed: number;
    succeeded: number;
    failed: number;
    startedAt: string | null;
    finishedAt: string | null;
    errorMessage: string | null;
}

/** Snapshot of a bulk concept-reclassify job. Matches {@code BulkConceptReclassifyJobStatus} —
 *  same shape as {@link BulkRetagJob}, kept as its own type so a job's fields read unambiguously
 *  as belonging to one feature or the other. */
type BulkConceptJob = BulkRetagJob;

// ── status → Bootstrap colour ──────────────────────────────────────────
const STATUS_BADGE: Record<BulkRetagJob["status"], string> = {
    QUEUED:  "text-bg-secondary",
    RUNNING: "text-bg-primary",
    DONE:    "text-bg-success",
    FAILED:  "text-bg-danger",
};

export default function AdminLibraryPage() {
    const { t, i18n } = useTranslation();

    // ── new-topics state ──────────────────────────────────────────────
    const [days, setDays] = useState<number>(7);
    const [topics, setTopics] = useState<NewTopic[]>([]);
    const [topicsLoading, setTopicsLoading] = useState(true);
    const [topicsError, setTopicsError] = useState("");

    // ── merge state ───────────────────────────────────────────────────
    const [mergeFrom, setMergeFrom] = useState("");
    const [mergeTo, setMergeTo] = useState("");
    const [suggestions, setSuggestions] = useState<string[]>([]);
    const [mergeBusy, setMergeBusy] = useState(false);
    const [mergeResult, setMergeResult] = useState<MergeResponse | null>(null);
    const [mergeError, setMergeError] = useState("");

    // ── bulk-retag state ──────────────────────────────────────────────
    const [retagFilter, setRetagFilter] = useState<"missing-llm-tags" | "all">("missing-llm-tags");
    const [retagStarting, setRetagStarting] = useState(false);
    const [retagError, setRetagError] = useState("");
    const [activeJob, setActiveJob] = useState<BulkRetagJob | null>(null);
    const [recentJobs, setRecentJobs] = useState<BulkRetagJob[]>([]);
    // Store the polling interval ID in a ref so the cleanup in useEffect always sees the
    // current value without needing it in the dependency array.
    const pollRef = useRef<number | null>(null);

    // ── bulk concept-reclassify state — mirrors bulk-retag state above exactly ─────────
    const [conceptFilter, setConceptFilter] = useState<"missing-concepts" | "all">("missing-concepts");
    const [conceptStarting, setConceptStarting] = useState(false);
    const [conceptError, setConceptError] = useState("");
    const [activeConceptJob, setActiveConceptJob] = useState<BulkConceptJob | null>(null);
    const [recentConceptJobs, setRecentConceptJobs] = useState<BulkConceptJob[]>([]);
    const conceptPollRef = useRef<number | null>(null);

    // ─────────────────────────────────────────────────────────────────
    // New-topics
    // ─────────────────────────────────────────────────────────────────

    const loadTopics = useCallback(async () => {
        setTopicsLoading(true);
        setTopicsError("");
        try {
            const res = await api.get<NewTopic[]>("/admin/tags/new-topics", {
                params: { days, limit: 100 },
            });
            setTopics(res.data);
        } catch (e) {
            console.error("loadTopics failed", e);
            setTopicsError(t("admin.library.newTopics.errorLoad"));
        } finally {
            setTopicsLoading(false);
        }
    }, [days, t]);

    useEffect(() => { loadTopics(); }, [loadTopics]);

    // ─────────────────────────────────────────────────────────────────
    // Merge autocomplete
    // ─────────────────────────────────────────────────────────────────

    useEffect(() => {
        const q = mergeTo.trim();
        if (q.length < 2) { setSuggestions([]); return; }
        const h = window.setTimeout(async () => {
            try {
                const res = await api.get<string[]>("/library/tags/autocomplete", {
                    params: { prefix: q, limit: 8 },
                });
                setSuggestions(res.data.filter((s) => s !== mergeFrom.trim().toLowerCase()));
            } catch {
                setSuggestions([]);
            }
        }, 200);
        return () => window.clearTimeout(h);
    }, [mergeTo, mergeFrom]);

    const openMergeFor = (sourceTag: string) => {
        setMergeFrom(sourceTag);
        setMergeTo("");
        setSuggestions([]);
        setMergeResult(null);
        setMergeError("");
    };

    const clearMerge = () => {
        setMergeFrom(""); setMergeTo(""); setSuggestions([]);
        setMergeResult(null); setMergeError("");
    };

    const handleMerge = async () => {
        const from = mergeFrom.trim().toLowerCase();
        const to   = mergeTo.trim().toLowerCase();
        if (!from || !to) { setMergeError(t("admin.library.merge.errorBothRequired")); return; }
        if (from === to)  { setMergeError(t("admin.library.merge.errorSame")); return; }
        setMergeBusy(true); setMergeError(""); setMergeResult(null);
        try {
            const res = await api.post<MergeResponse>("/admin/tags/merge", { from, to });
            setMergeResult(res.data);
            loadTopics();
        } catch (e) {
            console.error("merge failed", e);
            setMergeError(t("admin.library.merge.errorServer"));
        } finally {
            setMergeBusy(false);
        }
    };

    // ─────────────────────────────────────────────────────────────────
    // Bulk-retag
    // ─────────────────────────────────────────────────────────────────

    const loadRecentJobs = useCallback(async () => {
        try {
            const res = await api.get<BulkRetagJob[]>("/admin/sequences/retag");
            setRecentJobs(res.data);
        } catch {
            // Non-fatal — the recent-jobs table is a nice-to-have.
        }
    }, []);

    useEffect(() => { loadRecentJobs(); }, [loadRecentJobs]);

    // Start polling as soon as we have an active job in a non-terminal state.
    useEffect(() => {
        if (!activeJob || activeJob.status === "DONE" || activeJob.status === "FAILED") return;

        pollRef.current = window.setInterval(async () => {
            try {
                const res = await api.get<BulkRetagJob>(`/admin/sequences/retag/${activeJob.jobId}`);
                setActiveJob(res.data);
                if (res.data.status === "DONE" || res.data.status === "FAILED") {
                    window.clearInterval(pollRef.current!);
                    pollRef.current = null;
                    loadRecentJobs();
                }
            } catch {
                // Transient network hiccup — keep polling.
            }
        }, 2000);

        return () => {
            if (pollRef.current != null) {
                window.clearInterval(pollRef.current);
                pollRef.current = null;
            }
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeJob?.jobId]);   // re-run only when a *different* job becomes active

    const handleStartRetag = async () => {
        setRetagStarting(true);
        setRetagError("");
        try {
            const res = await api.post<BulkRetagJob>("/admin/sequences/retag", {
                filter: retagFilter,
            });
            setActiveJob(res.data);
        } catch (e) {
            console.error("retag start failed", e);
            setRetagError(t("admin.library.retag.errorStart"));
        } finally {
            setRetagStarting(false);
        }
    };

    // ─────────────────────────────────────────────────────────────────
    // Bulk concept-reclassify — mirrors bulk-retag above exactly, against
    // /admin/sequences/reclassify-concepts instead of /admin/sequences/retag.
    // ─────────────────────────────────────────────────────────────────

    const loadRecentConceptJobs = useCallback(async () => {
        try {
            const res = await api.get<BulkConceptJob[]>("/admin/sequences/reclassify-concepts");
            setRecentConceptJobs(res.data);
        } catch {
            // Non-fatal — the recent-jobs table is a nice-to-have.
        }
    }, []);

    useEffect(() => { loadRecentConceptJobs(); }, [loadRecentConceptJobs]);

    useEffect(() => {
        if (!activeConceptJob || activeConceptJob.status === "DONE" || activeConceptJob.status === "FAILED") return;

        conceptPollRef.current = window.setInterval(async () => {
            try {
                const res = await api.get<BulkConceptJob>(
                    `/admin/sequences/reclassify-concepts/${activeConceptJob.jobId}`,
                );
                setActiveConceptJob(res.data);
                if (res.data.status === "DONE" || res.data.status === "FAILED") {
                    window.clearInterval(conceptPollRef.current!);
                    conceptPollRef.current = null;
                    loadRecentConceptJobs();
                }
            } catch {
                // Transient network hiccup — keep polling.
            }
        }, 2000);

        return () => {
            if (conceptPollRef.current != null) {
                window.clearInterval(conceptPollRef.current);
                conceptPollRef.current = null;
            }
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeConceptJob?.jobId]);

    const handleStartConceptReclassify = async () => {
        setConceptStarting(true);
        setConceptError("");
        try {
            const res = await api.post<BulkConceptJob>("/admin/sequences/reclassify-concepts", {
                filter: conceptFilter,
            });
            setActiveConceptJob(res.data);
        } catch (e) {
            console.error("concept reclassify start failed", e);
            setConceptError(t("admin.library.reclassifyConcepts.errorStart"));
        } finally {
            setConceptStarting(false);
        }
    };

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    const fmtDate = useCallback(
        (iso: string) => {
            try {
                return new Date(iso).toLocaleDateString(i18n.language, {
                    year: "numeric", month: "short", day: "numeric",
                });
            } catch { return iso; }
        },
        [i18n.language],
    );

    const fmtTime = useCallback(
        (iso: string | null) => {
            if (!iso) return "—";
            try {
                return new Date(iso).toLocaleTimeString(i18n.language, {
                    hour: "2-digit", minute: "2-digit", second: "2-digit",
                });
            } catch { return iso; }
        },
        [i18n.language],
    );

    const isActiveJobRunning =
        activeJob && (activeJob.status === "QUEUED" || activeJob.status === "RUNNING");

    const retagPct = activeJob && activeJob.total > 0
        ? Math.round((activeJob.processed / activeJob.total) * 100)
        : 0;

    const isActiveConceptJobRunning =
        activeConceptJob && (activeConceptJob.status === "QUEUED" || activeConceptJob.status === "RUNNING");

    const conceptPct = activeConceptJob && activeConceptJob.total > 0
        ? Math.round((activeConceptJob.processed / activeConceptJob.total) * 100)
        : 0;

    // ─────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────

    return (
        <div className="container py-4" style={{ maxWidth: "960px" }}>
            <h1 className="h3 mb-4">
                <i className="bi bi-shield-lock me-2"></i>
                {t("admin.library.title")}
            </h1>

            {/* ─── Section 1: new-topics report ─── */}
            <section className="card mb-4">
                <div className="card-header d-flex align-items-center justify-content-between flex-wrap gap-2">
                    <h2 className="h5 mb-0">
                        <i className="bi bi-stars me-2"></i>
                        {t("admin.library.newTopics.title")}
                    </h2>
                    <div className="d-flex align-items-center gap-2">
                        <label className="small text-muted mb-0">
                            {t("admin.library.newTopics.daysLabel")}
                        </label>
                        <select
                            className="form-select form-select-sm"
                            style={{ width: "6rem" }}
                            value={days}
                            onChange={(e) => setDays(parseInt(e.target.value, 10))}
                        >
                            <option value={1}>1</option>
                            <option value={7}>7</option>
                            <option value={14}>14</option>
                            <option value={30}>30</option>
                            <option value={90}>90</option>
                        </select>
                        <button
                            className="btn btn-sm btn-outline-secondary"
                            onClick={loadTopics}
                            disabled={topicsLoading}
                            title={t("admin.library.newTopics.refresh") as string}
                        >
                            <i className="bi bi-arrow-clockwise"></i>
                        </button>
                    </div>
                </div>

                <div className="card-body">
                    <p className="small text-muted mb-3">
                        {t("admin.library.newTopics.subtitle", { days })}
                    </p>

                    {topicsLoading && (
                        <div className="text-center py-4">
                            <div className="spinner-border text-secondary" role="status" aria-hidden="true"></div>
                        </div>
                    )}
                    {!topicsLoading && topicsError && (
                        <div className="alert alert-danger mb-0">{topicsError}</div>
                    )}
                    {!topicsLoading && !topicsError && topics.length === 0 && (
                        <div className="text-muted text-center py-3">
                            {t("admin.library.newTopics.empty")}
                        </div>
                    )}
                    {!topicsLoading && !topicsError && topics.length > 0 && (
                        <div className="table-responsive">
                            <table className="table table-sm table-hover mb-0">
                                <thead>
                                    <tr>
                                        <th>{t("admin.library.newTopics.colTag")}</th>
                                        <th>{t("admin.library.newTopics.colFirstSeen")}</th>
                                        <th className="text-end">{t("admin.library.newTopics.colUsage")}</th>
                                        <th className="text-end" style={{ width: "9rem" }}></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {topics.map((row) => (
                                        <tr key={row.tagName}>
                                            <td><code className="text-body">{row.tagName}</code></td>
                                            <td className="text-muted small">{fmtDate(row.firstSeen)}</td>
                                            <td className="text-end">
                                                <span className="badge text-bg-light text-dark">{row.usageCount}</span>
                                            </td>
                                            <td className="text-end">
                                                <button
                                                    className="btn btn-sm btn-outline-primary"
                                                    onClick={() => openMergeFor(row.tagName)}
                                                >
                                                    <i className="bi bi-arrow-left-right me-1"></i>
                                                    {t("admin.library.newTopics.mergeAction")}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </section>

            {/* ─── Section 2: merge tool ─── */}
            <section className="card mb-4">
                <div className="card-header">
                    <h2 className="h5 mb-0">
                        <i className="bi bi-arrow-left-right me-2"></i>
                        {t("admin.library.merge.title")}
                    </h2>
                </div>
                <div className="card-body">
                    <p className="small text-muted">{t("admin.library.merge.subtitle")}</p>

                    <div className="row g-2 align-items-end">
                        <div className="col-md-5">
                            <label className="form-label small text-muted mb-1">
                                {t("admin.library.merge.fromLabel")}
                            </label>
                            <input
                                type="text"
                                className="form-control"
                                placeholder={t("admin.library.merge.fromPlaceholder") as string}
                                value={mergeFrom}
                                onChange={(e) => setMergeFrom(e.target.value)}
                            />
                        </div>
                        <div className="col-md-auto text-center pb-1">
                            <i className="bi bi-arrow-right text-muted"></i>
                        </div>
                        <div className="col-md-5 position-relative">
                            <label className="form-label small text-muted mb-1">
                                {t("admin.library.merge.toLabel")}
                            </label>
                            <input
                                type="text"
                                className="form-control"
                                placeholder={t("admin.library.merge.toPlaceholder") as string}
                                value={mergeTo}
                                onChange={(e) => setMergeTo(e.target.value)}
                                autoComplete="off"
                            />
                            {suggestions.length > 0 && (
                                <ul
                                    className="list-group position-absolute shadow-sm"
                                    style={{ zIndex: 10, top: "100%", left: 0, right: 0 }}
                                >
                                    {suggestions.map((s) => (
                                        <li
                                            key={s}
                                            className="list-group-item list-group-item-action py-1 px-2 small"
                                            role="button"
                                            onClick={() => { setMergeTo(s); setSuggestions([]); }}
                                        >
                                            {s}
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                    </div>

                    <div className="mt-3 d-flex gap-2">
                        <button
                            className="btn btn-primary"
                            onClick={handleMerge}
                            disabled={mergeBusy || !mergeFrom.trim() || !mergeTo.trim()}
                        >
                            {mergeBusy
                                ? <span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
                                : <i className="bi bi-check2-circle me-1"></i>}
                            {t("admin.library.merge.confirm")}
                        </button>
                        {(mergeFrom || mergeTo) && (
                            <button className="btn btn-outline-secondary" onClick={clearMerge} disabled={mergeBusy}>
                                {t("admin.library.merge.clear")}
                            </button>
                        )}
                    </div>

                    {mergeError && (
                        <div className="alert alert-danger mt-3 mb-0 py-2">{mergeError}</div>
                    )}
                    {mergeResult && (
                        <div className="alert alert-success mt-3 mb-0 py-2">
                            <i className="bi bi-check-circle-fill me-2"></i>
                            {t("admin.library.merge.success", {
                                from: mergeResult.from,
                                to: mergeResult.to,
                                count: mergeResult.affected,
                            })}
                        </div>
                    )}
                </div>
            </section>

            {/* ─── Section 3: bulk-retagger ─── */}
            <section className="card">
                <div className="card-header">
                    <h2 className="h5 mb-0">
                        <i className="bi bi-arrow-repeat me-2"></i>
                        {t("admin.library.retag.title")}
                    </h2>
                </div>
                <div className="card-body">
                    <p className="small text-muted">{t("admin.library.retag.subtitle")}</p>

                    {/* Filter + start button */}
                    <div className="d-flex align-items-center gap-3 flex-wrap">
                        <div className="flex-grow-1" style={{ minWidth: "200px" }}>
                            <label className="form-label small text-muted mb-1">
                                {t("admin.library.retag.filterLabel")}
                            </label>
                            <select
                                className="form-select w-100"
                                value={retagFilter}
                                onChange={(e) => setRetagFilter(e.target.value as typeof retagFilter)}
                                disabled={!!isActiveJobRunning}
                            >
                                <option value="missing-llm-tags">
                                    {t("admin.library.retag.filterMissing")}
                                </option>
                                <option value="all">
                                    {t("admin.library.retag.filterAll")}
                                </option>
                            </select>
                        </div>
                        <div className="pt-3">
                            <button
                                className="btn btn-warning"
                                onClick={handleStartRetag}
                                disabled={retagStarting || !!isActiveJobRunning}
                            >
                                {retagStarting
                                    ? <><span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>{t("admin.library.retag.starting")}</>
                                    : <><i className="bi bi-play-fill me-2"></i>{t("admin.library.retag.start")}</>
                                }
                            </button>
                        </div>
                    </div>

                    {/* Warning when "all" is selected */}
                    {retagFilter === "all" && !isActiveJobRunning && (
                        <div className="alert alert-warning d-flex align-items-start gap-2 mt-3 mb-0 py-2">
                            <i className="bi bi-exclamation-triangle-fill flex-shrink-0 mt-1"></i>
                            <span className="small">{t("admin.library.retag.warningAll")}</span>
                        </div>
                    )}

                    {retagError && (
                        <div className="alert alert-danger mt-3 mb-0 py-2">{retagError}</div>
                    )}

                    {/* Active job progress */}
                    {activeJob && (
                        <div className="mt-4 p-3 border rounded bg-light">
                            <div className="d-flex align-items-center justify-content-between mb-2">
                                <span className="fw-semibold small">
                                    {t("admin.library.retag.activeJob")}
                                    <code className="ms-2 text-muted" style={{ fontSize: "0.75em" }}>
                                        {activeJob.jobId.split("-")[0]}…
                                    </code>
                                </span>
                                <span className={`badge ${STATUS_BADGE[activeJob.status]}`}>
                                    {activeJob.status}
                                </span>
                            </div>

                            {/* Progress bar */}
                            <div className="progress mb-2" style={{ height: "1.1rem" }}>
                                <div
                                    className={`progress-bar progress-bar-striped ${isActiveJobRunning ? "progress-bar-animated" : ""} ${activeJob.status === "FAILED" ? "bg-danger" : ""}`}
                                    role="progressbar"
                                    style={{ width: `${retagPct}%` }}
                                    aria-valuenow={retagPct}
                                    aria-valuemin={0}
                                    aria-valuemax={100}
                                >
                                    {retagPct > 10 && `${retagPct}%`}
                                </div>
                            </div>

                            <div className="d-flex justify-content-between small text-muted">
                                <span>
                                    {t("admin.library.retag.progress", {
                                        processed: activeJob.processed,
                                        total: activeJob.total,
                                    })}
                                    {activeJob.succeeded > 0 && (
                                        <span className="text-success ms-2">
                                            <i className="bi bi-check2 me-1"></i>{activeJob.succeeded}
                                        </span>
                                    )}
                                    {activeJob.failed > 0 && (
                                        <span className="text-danger ms-2">
                                            <i className="bi bi-x me-1"></i>{activeJob.failed}
                                        </span>
                                    )}
                                </span>
                                <span>{activeJob.filter}</span>
                            </div>

                            {activeJob.errorMessage && (
                                <div className="alert alert-danger mt-2 mb-0 py-1 small">
                                    {activeJob.errorMessage}
                                </div>
                            )}
                        </div>
                    )}

                    {/* Recent jobs table */}
                    <div className="mt-4">
                        <div className="d-flex align-items-center justify-content-between mb-2">
                            <span className="small fw-semibold text-muted">
                                {t("admin.library.retag.recentJobs")}
                            </span>
                            <button
                                className="btn btn-sm btn-outline-secondary py-0"
                                onClick={loadRecentJobs}
                                title={t("admin.library.newTopics.refresh") as string}
                            >
                                <i className="bi bi-arrow-clockwise"></i>
                            </button>
                        </div>

                        {recentJobs.length === 0 ? (
                            <p className="text-muted small mb-0">{t("admin.library.retag.noRecentJobs")}</p>
                        ) : (
                            <div className="table-responsive">
                                <table className="table table-sm table-hover mb-0">
                                    <thead>
                                        <tr>
                                            <th>{t("admin.library.retag.colFilter")}</th>
                                            <th>{t("admin.library.retag.colStatus")}</th>
                                            <th className="text-end">{t("admin.library.retag.colProgress")}</th>
                                            <th className="text-end">{t("admin.library.retag.colStarted")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {recentJobs.map((job) => (
                                            <tr
                                                key={job.jobId}
                                                className={activeJob?.jobId === job.jobId ? "table-active" : ""}
                                            >
                                                <td>
                                                    <code className="text-body small">{job.filter}</code>
                                                </td>
                                                <td>
                                                    <span className={`badge ${STATUS_BADGE[job.status]}`}>
                                                        {job.status}
                                                    </span>
                                                </td>
                                                <td className="text-end small text-muted">
                                                    {job.processed} / {job.total}
                                                    {job.failed > 0 && (
                                                        <span className="text-danger ms-1">
                                                            ({job.failed} err)
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="text-end small text-muted">
                                                    {fmtTime(job.startedAt)}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            </section>

            {/* ─── Section 4: bulk concept re-classifier ─── */}
            <section className="card mt-4">
                <div className="card-header">
                    <h2 className="h5 mb-0">
                        <i className="bi bi-diagram-3 me-2"></i>
                        {t("admin.library.reclassifyConcepts.title")}
                    </h2>
                </div>
                <div className="card-body">
                    <p className="small text-muted">{t("admin.library.reclassifyConcepts.subtitle")}</p>

                    {/* Filter + start button */}
                    <div className="d-flex align-items-center gap-3 flex-wrap">
                        <div className="flex-grow-1" style={{ minWidth: "200px" }}>
                            <label className="form-label small text-muted mb-1">
                                {t("admin.library.reclassifyConcepts.filterLabel")}
                            </label>
                            <select
                                className="form-select w-100"
                                value={conceptFilter}
                                onChange={(e) => setConceptFilter(e.target.value as typeof conceptFilter)}
                                disabled={!!isActiveConceptJobRunning}
                            >
                                <option value="missing-concepts">
                                    {t("admin.library.reclassifyConcepts.filterMissing")}
                                </option>
                                <option value="all">
                                    {t("admin.library.reclassifyConcepts.filterAll")}
                                </option>
                            </select>
                        </div>
                        <div className="pt-3">
                            <button
                                className="btn btn-warning"
                                onClick={handleStartConceptReclassify}
                                disabled={conceptStarting || !!isActiveConceptJobRunning}
                            >
                                {conceptStarting
                                    ? <><span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>{t("admin.library.reclassifyConcepts.starting")}</>
                                    : <><i className="bi bi-play-fill me-2"></i>{t("admin.library.reclassifyConcepts.start")}</>
                                }
                            </button>
                        </div>
                    </div>

                    {/* Warning when "all" is selected */}
                    {conceptFilter === "all" && !isActiveConceptJobRunning && (
                        <div className="alert alert-warning d-flex align-items-start gap-2 mt-3 mb-0 py-2">
                            <i className="bi bi-exclamation-triangle-fill flex-shrink-0 mt-1"></i>
                            <span className="small">{t("admin.library.reclassifyConcepts.warningAll")}</span>
                        </div>
                    )}

                    {conceptError && (
                        <div className="alert alert-danger mt-3 mb-0 py-2">{conceptError}</div>
                    )}

                    {/* Active job progress */}
                    {activeConceptJob && (
                        <div className="mt-4 p-3 border rounded bg-light">
                            <div className="d-flex align-items-center justify-content-between mb-2">
                                <span className="fw-semibold small">
                                    {t("admin.library.reclassifyConcepts.activeJob")}
                                    <code className="ms-2 text-muted" style={{ fontSize: "0.75em" }}>
                                        {activeConceptJob.jobId.split("-")[0]}…
                                    </code>
                                </span>
                                <span className={`badge ${STATUS_BADGE[activeConceptJob.status]}`}>
                                    {activeConceptJob.status}
                                </span>
                            </div>

                            {/* Progress bar */}
                            <div className="progress mb-2" style={{ height: "1.1rem" }}>
                                <div
                                    className={`progress-bar progress-bar-striped ${isActiveConceptJobRunning ? "progress-bar-animated" : ""} ${activeConceptJob.status === "FAILED" ? "bg-danger" : ""}`}
                                    role="progressbar"
                                    style={{ width: `${conceptPct}%` }}
                                    aria-valuenow={conceptPct}
                                    aria-valuemin={0}
                                    aria-valuemax={100}
                                >
                                    {conceptPct > 10 && `${conceptPct}%`}
                                </div>
                            </div>

                            <div className="d-flex justify-content-between small text-muted">
                                <span>
                                    {t("admin.library.reclassifyConcepts.progress", {
                                        processed: activeConceptJob.processed,
                                        total: activeConceptJob.total,
                                    })}
                                    {activeConceptJob.succeeded > 0 && (
                                        <span className="text-success ms-2">
                                            <i className="bi bi-check2 me-1"></i>{activeConceptJob.succeeded}
                                        </span>
                                    )}
                                    {activeConceptJob.failed > 0 && (
                                        <span className="text-danger ms-2">
                                            <i className="bi bi-x me-1"></i>{activeConceptJob.failed}
                                        </span>
                                    )}
                                </span>
                                <span>{activeConceptJob.filter}</span>
                            </div>

                            {activeConceptJob.errorMessage && (
                                <div className="alert alert-danger mt-2 mb-0 py-1 small">
                                    {activeConceptJob.errorMessage}
                                </div>
                            )}
                        </div>
                    )}

                    {/* Recent jobs table */}
                    <div className="mt-4">
                        <div className="d-flex align-items-center justify-content-between mb-2">
                            <span className="small fw-semibold text-muted">
                                {t("admin.library.reclassifyConcepts.recentJobs")}
                            </span>
                            <button
                                className="btn btn-sm btn-outline-secondary py-0"
                                onClick={loadRecentConceptJobs}
                                title={t("admin.library.newTopics.refresh") as string}
                            >
                                <i className="bi bi-arrow-clockwise"></i>
                            </button>
                        </div>

                        {recentConceptJobs.length === 0 ? (
                            <p className="text-muted small mb-0">{t("admin.library.reclassifyConcepts.noRecentJobs")}</p>
                        ) : (
                            <div className="table-responsive">
                                <table className="table table-sm table-hover mb-0">
                                    <thead>
                                        <tr>
                                            <th>{t("admin.library.reclassifyConcepts.colFilter")}</th>
                                            <th>{t("admin.library.reclassifyConcepts.colStatus")}</th>
                                            <th className="text-end">{t("admin.library.reclassifyConcepts.colProgress")}</th>
                                            <th className="text-end">{t("admin.library.reclassifyConcepts.colStarted")}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {recentConceptJobs.map((job) => (
                                            <tr
                                                key={job.jobId}
                                                className={activeConceptJob?.jobId === job.jobId ? "table-active" : ""}
                                            >
                                                <td>
                                                    <code className="text-body small">{job.filter}</code>
                                                </td>
                                                <td>
                                                    <span className={`badge ${STATUS_BADGE[job.status]}`}>
                                                        {job.status}
                                                    </span>
                                                </td>
                                                <td className="text-end small text-muted">
                                                    {job.processed} / {job.total}
                                                    {job.failed > 0 && (
                                                        <span className="text-danger ms-1">
                                                            ({job.failed} err)
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="text-end small text-muted">
                                                    {fmtTime(job.startedAt)}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </div>
                </div>
            </section>
        </div>
    );
}
