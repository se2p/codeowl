import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, apiErrorMessage } from "../lib/api";
import { downloadFile, downloadTimestamp } from "../lib/download";
import type { ClassInfo } from "../types/models";

/** The four tables, in the order they appear as tabs. */
const DATASETS = ["events", "feedback", "hints", "submissions"] as const;
type Dataset = (typeof DATASETS)[number];

interface ResearchPage {
    columns: string[];
    content: Record<string, unknown>[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
}

interface ResearchSummary {
    totalRows: number;
    identifiedUsers: number;
    anonymousRows: number;
    eventCounts: Record<string, number>;
}

const PAGE_SIZE = 50;

/** Columns holding a JSON blob — shown folded until clicked. */
const JSON_COLUMNS = new Set([
    "event_data",
    "findings",
    "block_order_at_request",
    "affected_block_ids",
    "block_order",
    "indent_levels",
]);

/** Columns holding an id, shown as a short pseudonym rather than 36 characters of UUID. */
const ID_COLUMNS = new Set(["id", "user_id", "class_id", "task_id", "sequence_id"]);

export default function AdminResearchPage() {
    const { t } = useTranslation();

    const [dataset, setDataset] = useState<Dataset>("events");
    const [classId, setClassId] = useState("");
    const [eventType, setEventType] = useState("");
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [page, setPage] = useState(0);

    const [classes, setClasses] = useState<ClassInfo[]>([]);
    const [data, setData] = useState<ResearchPage | null>(null);
    const [summary, setSummary] = useState<ResearchSummary | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [downloading, setDownloading] = useState<"csv" | "zip" | null>(null);
    const [expanded, setExpanded] = useState<string | null>(null);

    // An admin gets every class back from this endpoint, so it needs no admin-only variant.
    useEffect(() => {
        api.get<ClassInfo[]>("/classes")
            .then((res) => setClasses(res.data))
            .catch(() => setClasses([]));
    }, []);

    const params = useMemo(() => {
        const p: Record<string, string | number> = {};
        if (classId) p.classId = classId;
        // Only the events dataset has an event_type; sending it elsewhere is a 400.
        if (eventType && dataset === "events") p.eventType = eventType;
        if (from) p.from = from;
        if (to) p.to = to;
        return p;
    }, [classId, eventType, from, to, dataset]);

    const load = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            const [rows, stats] = await Promise.all([
                api.get<ResearchPage>(`/admin/research/${dataset}`, {
                    params: { ...params, page, size: PAGE_SIZE },
                }),
                api.get<ResearchSummary>(`/admin/research/${dataset}/summary`, { params }),
            ]);
            setData(rows.data);
            setSummary(stats.data);
        } catch (e) {
            // Leave the previous rows on screen rather than blanking the table: an empty table
            // and a failed request look identical, and only one of them means "no data".
            setError(apiErrorMessage(e, t("adminResearch.loadError")));
        } finally {
            setLoading(false);
        }
    }, [dataset, params, page, t]);

    useEffect(() => {
        load();
    }, [load]);

    // Any filter change invalidates the page number — page 7 of the old result is not page 7
    // of the new one, and asking for it shows an empty table that looks like no data.
    const changeFilter = (apply: () => void) => {
        apply();
        setPage(0);
    };

    const switchDataset = (next: Dataset) => {
        changeFilter(() => {
            setDataset(next);
            if (next !== "events") setEventType("");
        });
        setExpanded(null);
    };

    const download = async (kind: "csv" | "zip") => {
        setDownloading(kind);
        setError("");
        try {
            const url =
                kind === "csv"
                    ? `/admin/research/${dataset}/export.csv`
                    : "/admin/research/export.zip";
            const fallback =
                kind === "csv"
                    ? `codeowl-${dataset}-${downloadTimestamp()}.csv`
                    : `codeowl-research-${downloadTimestamp()}.zip`;
            await downloadFile("get", url, fallback, params);
        } catch (e) {
            setError(apiErrorMessage(e, t("adminResearch.downloadError")));
        } finally {
            setDownloading(null);
        }
    };

    const rowKey = (row: Record<string, unknown>, index: number) =>
        String(row.id ?? `${row.user_id}-${row.task_id}`) + `-${index}`;

    const renderCell = (column: string, value: unknown, key: string) => {
        if (value === null || value === undefined) {
            // An absent user is the anonymised case, and saying so beats an empty cell that
            // reads as a rendering bug.
            const blank =
                column === "user_id"
                    ? t("adminResearch.anonymous")
                    : column === "class_id"
                      ? t("adminResearch.noClass")
                      : "—";
            return <span className="text-muted fst-italic">{blank}</span>;
        }
        if (typeof value === "boolean") {
            return value ? (
                <i className="bi bi-check-circle text-success" aria-label="true"></i>
            ) : (
                <i className="bi bi-x-circle text-secondary" aria-label="false"></i>
            );
        }
        if (JSON_COLUMNS.has(column)) {
            const text = String(value);
            const isOpen = expanded === key;
            return (
                <button
                    type="button"
                    className="btn btn-link btn-sm p-0 text-start font-monospace"
                    style={{ fontSize: "0.75em", maxWidth: isOpen ? undefined : 220 }}
                    onClick={() => setExpanded(isOpen ? null : key)}
                    title={t("adminResearch.expandHint")}
                >
                    {isOpen ? (
                        <pre className="mb-0" style={{ whiteSpace: "pre-wrap" }}>
                            {prettyJson(text)}
                        </pre>
                    ) : (
                        <span className="d-inline-block text-truncate" style={{ maxWidth: 220 }}>
                            {text}
                        </span>
                    )}
                </button>
            );
        }
        if (ID_COLUMNS.has(column)) {
            return (
                <code style={{ fontSize: "0.8em" }} title={String(value)}>
                    {String(value).slice(0, 8)}…
                </code>
            );
        }
        if (column.endsWith("_at")) {
            return <small className="text-nowrap">{new Date(String(value)).toLocaleString()}</small>;
        }
        return <span>{String(value)}</span>;
    };

    const eventTypes = Object.keys(summary?.eventCounts ?? {});

    return (
        <div className="container-fluid py-4" style={{ maxWidth: 1400 }}>
            <h2 className="mb-1">{t("adminResearch.title")}</h2>
            <p className="text-muted mb-4">{t("adminResearch.subtitle")}</p>

            {error && (
                <div className="alert alert-danger alert-dismissible" role="alert">
                    {error}
                    <button
                        type="button"
                        className="btn-close"
                        onClick={() => setError("")}
                        aria-label={t("common.close")}
                    />
                </div>
            )}

            {/* ── Dataset tabs ── */}
            <ul className="nav nav-tabs mb-3">
                {DATASETS.map((ds) => (
                    <li className="nav-item" key={ds}>
                        <button
                            type="button"
                            className={`nav-link ${dataset === ds ? "active" : ""}`}
                            onClick={() => switchDataset(ds)}
                        >
                            {t(`adminResearch.dataset.${ds}`)}
                        </button>
                    </li>
                ))}
            </ul>

            {/* ── Filters ── */}
            <div className="row g-2 align-items-end mb-3">
                <div className="col-md-3">
                    <label className="form-label small mb-1" htmlFor="research-class">
                        {t("adminResearch.filterClass")}
                    </label>
                    <select
                        id="research-class"
                        className="form-select form-select-sm"
                        value={classId}
                        onChange={(e) => changeFilter(() => setClassId(e.target.value))}
                    >
                        <option value="">{t("adminResearch.allClasses")}</option>
                        {classes.map((c) => (
                            <option key={c.id} value={c.id}>
                                {c.name}
                                {c.trackingEnabled ? "" : ` ${t("adminResearch.trackingOffTag")}`}
                            </option>
                        ))}
                    </select>
                </div>

                {dataset === "events" && (
                    <div className="col-md-3">
                        <label className="form-label small mb-1" htmlFor="research-type">
                            {t("adminResearch.filterType")}
                        </label>
                        <select
                            id="research-type"
                            className="form-select form-select-sm"
                            value={eventType}
                            onChange={(e) => changeFilter(() => setEventType(e.target.value))}
                        >
                            <option value="">{t("adminResearch.allTypes")}</option>
                            {eventTypes.map((type) => (
                                <option key={type} value={type}>
                                    {type} ({summary?.eventCounts[type]})
                                </option>
                            ))}
                        </select>
                    </div>
                )}

                <div className="col-md-2">
                    <label className="form-label small mb-1" htmlFor="research-from">
                        {t("adminResearch.filterFrom")}
                    </label>
                    <input
                        id="research-from"
                        type="date"
                        className="form-control form-control-sm"
                        value={from}
                        onChange={(e) => changeFilter(() => setFrom(e.target.value))}
                    />
                </div>
                <div className="col-md-2">
                    <label className="form-label small mb-1" htmlFor="research-to">
                        {t("adminResearch.filterTo")}
                    </label>
                    <input
                        id="research-to"
                        type="date"
                        className="form-control form-control-sm"
                        value={to}
                        onChange={(e) => changeFilter(() => setTo(e.target.value))}
                    />
                </div>

                <div className="col-md-2 d-flex gap-2">
                    <button
                        className="btn btn-sm btn-outline-secondary"
                        onClick={load}
                        disabled={loading}
                        title={t("adminResearch.refresh")}
                    >
                        {loading ? (
                            <span className="spinner-border spinner-border-sm" role="status" />
                        ) : (
                            <i className="bi bi-arrow-clockwise"></i>
                        )}
                    </button>
                    <button
                        className="btn btn-sm btn-outline-secondary"
                        onClick={() =>
                            changeFilter(() => {
                                setClassId("");
                                setEventType("");
                                setFrom("");
                                setTo("");
                            })
                        }
                    >
                        {t("adminResearch.clearFilters")}
                    </button>
                </div>
            </div>

            {/* ── Stats strip ── */}
            <div className="card bg-light border-0 mb-3">
                <div className="card-body py-2 d-flex flex-wrap gap-4 align-items-center">
                    <span>
                        <strong>{summary?.totalRows ?? 0}</strong>{" "}
                        <span className="text-muted">{t("adminResearch.statRows")}</span>
                    </span>
                    <span>
                        <strong>{summary?.identifiedUsers ?? 0}</strong>{" "}
                        <span className="text-muted">{t("adminResearch.statStudents")}</span>
                    </span>
                    <span>
                        <strong>{summary?.anonymousRows ?? 0}</strong>{" "}
                        <span className="text-muted">{t("adminResearch.statAnonymous")}</span>
                    </span>
                    <span className="ms-auto d-flex gap-2">
                        <button
                            className="btn btn-sm btn-primary"
                            onClick={() => download("csv")}
                            disabled={downloading !== null}
                        >
                            {downloading === "csv" ? (
                                <span className="spinner-border spinner-border-sm me-2" role="status" />
                            ) : (
                                <i className="bi bi-filetype-csv me-1"></i>
                            )}
                            {t("adminResearch.downloadCsv")}
                        </button>
                        <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => download("zip")}
                            disabled={downloading !== null}
                            title={t("adminResearch.downloadZipHint")}
                        >
                            {downloading === "zip" ? (
                                <span className="spinner-border spinner-border-sm me-2" role="status" />
                            ) : (
                                <i className="bi bi-file-earmark-zip me-1"></i>
                            )}
                            {t("adminResearch.downloadZip")}
                        </button>
                    </span>
                </div>
            </div>

            {/* ── Table ── */}
            <div className="table-responsive">
                <table className="table table-sm table-hover align-middle">
                    <thead className="table-light">
                        <tr>
                            {(data?.columns ?? []).map((column) => (
                                <th key={column} className="text-nowrap">
                                    <small>{column}</small>
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {(data?.content ?? []).map((row, index) => {
                            const key = rowKey(row, index);
                            return (
                                <tr key={key}>
                                    {(data?.columns ?? []).map((column) => (
                                        <td key={column}>
                                            {renderCell(column, row[column], `${key}-${column}`)}
                                        </td>
                                    ))}
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {!loading && data && data.content.length === 0 && (
                <p className="text-muted fst-italic">{t("adminResearch.empty")}</p>
            )}

            {/* ── Pager ── */}
            {data && data.totalPages > 1 && (
                <div className="d-flex align-items-center justify-content-between mt-2">
                    <small className="text-muted">
                        {t("adminResearch.pageOf", {
                            page: data.page + 1,
                            total: data.totalPages,
                        })}
                    </small>
                    <div className="btn-group btn-group-sm">
                        <button
                            className="btn btn-outline-secondary"
                            aria-label={t("adminResearch.prevPage")}
                            disabled={data.page === 0 || loading}
                            onClick={() => setPage((p) => Math.max(0, p - 1))}
                        >
                            <i className="bi bi-chevron-left"></i>
                        </button>
                        <button
                            className="btn btn-outline-secondary"
                            aria-label={t("adminResearch.nextPage")}
                            disabled={data.page + 1 >= data.totalPages || loading}
                            onClick={() => setPage((p) => p + 1)}
                        >
                            <i className="bi bi-chevron-right"></i>
                        </button>
                    </div>
                </div>
            )}

            <p className="text-muted small mt-4 mb-0">
                <i className="bi bi-info-circle me-1"></i>
                {t("adminResearch.privacyNote")}
            </p>
        </div>
    );
}

/** Pretty-prints a JSON column, leaving it alone if it is not actually JSON. */
function prettyJson(text: string): string {
    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
        return text;
    }
}
