import React, { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import { usePageTitle } from "../hooks/usePageTitle";
import { renderGapLine } from "../lib/gapMarkers";
import CodeSolutionEditor from "../components/CodeSolutionEditor";
import type { SplitEntry } from "../types/models";
import "bootstrap/dist/css/bootstrap.min.css";

/* ────────────────────────────────────────────────────────────── */
/*  Types                                                        */
/* ────────────────────────────────────────────────────────────── */

interface TierEntry {
    tierLevel: number;
    statement: string;
    solution: string;
    /** AP31: inline-split entries for this tier (one per split line). */
    splits: SplitEntry[];
}

const EMPTY_TIER = (level: number): TierEntry => ({
    tierLevel: level,
    statement: "",
    solution: "",
    splits: [],
});

/* ────────────────────────────────────────────────────────────── */
/*  Component                                                    */
/* ────────────────────────────────────────────────────────────── */

export default function ManualTaskCreator() {
    const { t } = useTranslation();
    usePageTitle("CodeOwl - Manual Task Creator");
    const navigate = useNavigate();

    // ── Form state ──────────────────────────────────────────
    const [language, setLanguage] = useState("");
    const [concepts, setConcepts] = useState("");
    const [tiers, setTiers] = useState<TierEntry[]>([
        EMPTY_TIER(1),
        EMPTY_TIER(2),
        EMPTY_TIER(3),
    ]);

    // ── UI state ────────────────────────────────────────────
    const [activeTier, setActiveTier] = useState(0); // index into tiers[]
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [previewLines, setPreviewLines] = useState<string[]>([]);

    // ── Derived ─────────────────────────────────────────────
    const conceptTokens = concepts
        .split(",")
        .map((c) => c.trim())
        .filter(Boolean);

    // ── Tier helpers ────────────────────────────────────────
    const updateTier = useCallback(
        (index: number, field: keyof TierEntry, value: string | number) => {
            setTiers((prev) =>
                prev.map((tier, i) => (i === index ? { ...tier, [field]: value } : tier))
            );
        },
        []
    );

    // AP31: replace the split entries of a tier
    const setTierSplits = useCallback((index: number, splits: SplitEntry[]) => {
        setTiers((prev) => prev.map((tier, i) => (i === index ? { ...tier, splits } : tier)));
    }, []);

    const addTier = () => {
        if (tiers.length >= 3) return;
        const usedLevels = new Set(tiers.map((tier) => tier.tierLevel));
        const next = [1, 2, 3].find((l) => !usedLevels.has(l)) ?? tiers.length + 1;
        setTiers((prev) => [...prev, EMPTY_TIER(next)]);
        setActiveTier(tiers.length);
    };

    const removeTier = (index: number) => {
        if (tiers.length <= 1) return;
        setTiers((prev) => prev.filter((_, i) => i !== index));
        setActiveTier((prev) => Math.min(prev, tiers.length - 2));
    };

    // ── Live Preview ────────────────────────────────────────
    const refreshPreview = useCallback(
        (code: string) => {
            const lines = code
                .split("\n")
                .filter((l) => l.trim().length > 0);
            setPreviewLines(lines);
        },
        []
    );

    // ── Submit ──────────────────────────────────────────────
    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        // Client-side validation
        if (!language.trim()) {
            setError(t("manual.languageRequired"));
            return;
        }
        if (conceptTokens.length === 0) {
            setError(t("manual.conceptRequired"));
            return;
        }
        for (const tier of tiers) {
            if (!tier.statement.trim()) {
                setError(t("manual.statementRequired", { level: tier.tierLevel }));
                return;
            }
            if (!tier.solution.trim()) {
                setError(t("manual.solutionRequired", { level: tier.tierLevel }));
                return;
            }
        }

        setSubmitting(true);
        try {
            const payload = {
                language: language.trim(),
                concepts: conceptTokens,
                tiers: tiers.map((t) => ({
                    tierLevel: t.tierLevel,
                    statement: t.statement.trim(),
                    solution: t.solution.trim(),
                    // AP31: only send splits that still reconstruct their (trimmed) solution line
                    splits: (t.splits ?? []).filter((s) => {
                        const lines = t.solution.trim().split("\n");
                        return s.lineIndex >= 0 && s.lineIndex < lines.length;
                    }),
                })),
            };

            const { data } = await api.post("/tasks/manual", payload);
            navigate(`/sequence/${data.id}`);
        } catch (err: any) {
            const msg =
                err?.response?.data?.message ||
                err?.response?.data ||
                err?.message ||
                t("manual.errorCreating");
            setError(typeof msg === "string" ? msg : JSON.stringify(msg));
        } finally {
            setSubmitting(false);
        }
    };

    const currentTier = tiers[activeTier] ?? tiers[0];

    // ── Render ──────────────────────────────────────────────
    return (
        <div className="container py-4" style={{ maxWidth: 1200 }}>
            {/* Hero */}
            <div
                className="rounded-4 p-4 p-md-5 mb-4 text-white"
                style={{
                    background:
                        "radial-gradient(1200px 400px at -10% -50%, #198754 0%, transparent 60%), radial-gradient(1000px 400px at 110% -50%, #0d6efd 0%, transparent 60%), linear-gradient(135deg, #0d6efd 0%, #198754 100%)",
                    boxShadow: "0 10px 30px rgba(25,135,84,.25)",
                }}
            >
                <div className="d-flex align-items-center gap-3">
                    <span style={{ fontSize: 40, lineHeight: 1 }}>
                        <i className="bi bi-pencil-square"></i>
                    </span>
                    <div>
                        <h1 className="h2 fw-bold mb-1">{t("manual.title")}</h1>
                        <p className="mb-0 opacity-75">
                            {t("manual.subtitle")}
                        </p>
                    </div>
                </div>
            </div>

            <form onSubmit={handleSubmit}>
                <div className="row gy-4">
                    {/* ── LEFT COLUMN: Meta + Tiers ─────────────── */}
                    <div className="col-12 col-lg-7">
                        {/* Meta card */}
                        <div className="card shadow-sm border-0 rounded-4 mb-4">
                            <div className="card-body">
                                <h5 className="card-title mb-3">
                                    <i className="bi bi-gear me-2"></i>{t("manual.sequenceSettings")}
                                </h5>

                                {/* Language */}
                                <div className="input-group mb-3">
                                    <span className="input-group-text">
                                        <i className="bi bi-code-slash"></i>
                                    </span>
                                    <div className="form-floating">
                                        <input
                                            id="manualLang"
                                            type="text"
                                            className="form-control"
                                            value={language}
                                            onChange={(e) => setLanguage(e.target.value)}
                                            placeholder={t("manual.languagePlaceholder")}
                                            required
                                        />
                                        <label htmlFor="manualLang">
                                            {t("manual.programmingLanguage")}
                                        </label>
                                    </div>
                                </div>

                                {/* Concepts */}
                                <div className="input-group">
                                    <span className="input-group-text">
                                        <i className="bi bi-tags"></i>
                                    </span>
                                    <input
                                        type="text"
                                        className="form-control"
                                        value={concepts}
                                        onChange={(e) => setConcepts(e.target.value)}
                                        placeholder={t("manual.conceptsLabel")}
                                        required
                                    />
                                </div>
                                {conceptTokens.length > 0 && (
                                    <div className="mt-2">
                                        {conceptTokens.map((c, i) => (
                                            <span
                                                key={`${c}-${i}`}
                                                className="badge rounded-pill text-bg-light me-2 mb-1 border"
                                            >
                                                {c}
                                            </span>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Tier tabs + editor */}
                        <div className="card shadow-sm border-0 rounded-4">
                            <div className="card-header bg-white rounded-top-4 border-bottom-0 pt-3 px-3">
                                <div className="d-flex align-items-center gap-2 flex-wrap">
                                    {tiers.map((tier, i) => (
                                        <button
                                            key={i}
                                            type="button"
                                            className={`btn btn-sm ${
                                                activeTier === i
                                                    ? "btn-primary"
                                                    : "btn-outline-primary"
                                            }`}
                                            onClick={() => {
                                                setActiveTier(i);
                                                refreshPreview(tiers[i].solution);
                                            }}
                                        >
                                            {t("manual.tier", { level: tier.tierLevel })}
                                            {tiers.length > 1 && (
                                                <span
                                                    className="ms-2 badge bg-danger rounded-circle"
                                                    style={{
                                                        cursor: "pointer",
                                                        fontSize: "0.65rem",
                                                        lineHeight: 1,
                                                        padding: "3px 5px",
                                                    }}
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        removeTier(i);
                                                    }}
                                                    title={t("manual.removeTier")}
                                                >
                                                    <i className="bi bi-x"></i>
                                                </span>
                                            )}
                                        </button>
                                    ))}
                                    {tiers.length < 3 && (
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-outline-success"
                                            onClick={addTier}
                                            title={t("manual.addTier")}
                                        >
                                            <i className="bi bi-plus-lg"></i>
                                        </button>
                                    )}
                                </div>
                            </div>

                            <div className="card-body">
                                {/* Tier Level selector */}
                                <div className="mb-3">
                                    <label className="form-label fw-semibold">
                                        {t("manual.tierLevel")}
                                    </label>
                                    <select
                                        className="form-select"
                                        value={currentTier.tierLevel}
                                        onChange={(e) =>
                                            updateTier(
                                                activeTier,
                                                "tierLevel",
                                                parseInt(e.target.value)
                                            )
                                        }
                                    >
                                        <option value={1}>{t("manual.tierEasy")}</option>
                                        <option value={2}>{t("manual.tierMedium")}</option>
                                        <option value={3}>{t("manual.tierHard")}</option>
                                    </select>
                                </div>

                                {/* Problem statement */}
                                <div className="mb-3">
                                    <label className="form-label fw-semibold">
                                        {t("manual.problemStatement")}
                                    </label>
                                    <textarea
                                        className="form-control"
                                        rows={3}
                                        value={currentTier.statement}
                                        onChange={(e) =>
                                            updateTier(activeTier, "statement", e.target.value)
                                        }
                                        placeholder={t("manual.problemPlaceholder")}
                                        required
                                    />
                                    <div className="form-text">
                                        {t("manual.problemHelper")}
                                    </div>
                                </div>

                                {/* Code solution */}
                                <div className="mb-3">
                                    <label className="form-label fw-semibold">
                                        {t("manual.codeSolution")}
                                    </label>
                                    <CodeSolutionEditor
                                        key={activeTier}
                                        code={currentTier.solution}
                                        onCodeChange={(val) => {
                                            updateTier(activeTier, "solution", val);
                                            refreshPreview(val);
                                        }}
                                        splits={currentTier.splits}
                                        onSplitsChange={(s) => setTierSplits(activeTier, s)}
                                        onError={setError}
                                        placeholder={t("manual.codePlaceholder")}
                                    />
                                </div>
                            </div>
                        </div>

                        {/* Actions */}
                        <div className="d-flex flex-wrap gap-2 mt-3">
                            <button
                                type="submit"
                                className="btn btn-success px-4"
                                disabled={submitting}
                            >
                                {submitting ? (
                                    <>
                                        <span
                                            className="spinner-border spinner-border-sm me-2"
                                            role="status"
                                        />
                                        {t("manual.creating")}
                                    </>
                                ) : (
                                    <>
                                        <i className="bi bi-check-lg me-1"></i>
                                        {t("manual.createSequence")}
                                    </>
                                )}
                            </button>
                            <button
                                type="button"
                                className="btn btn-outline-secondary"
                                onClick={() => navigate("/")}
                                disabled={submitting}
                            >
                                {t("manual.cancel")}
                            </button>
                        </div>

                        {error && (
                            <div className="alert alert-danger d-flex align-items-start gap-2 mt-3">
                                <i className="bi bi-exclamation-triangle"></i>
                                <div>
                                    <strong>{t("common.error")}:</strong> {error}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* ── RIGHT COLUMN: Live Preview ────────────── */}
                    <div className="col-12 col-lg-5">
                        <div className="card shadow-sm border-0 rounded-4 sticky-top" style={{ top: "1rem" }}>
                            <div className="card-body">
                                <h5 className="card-title mb-3">
                                    <i className="bi bi-eye me-2"></i>
                                    {t("manual.blockPreview", { level: currentTier.tierLevel })}
                                </h5>

                                {previewLines.length === 0 && !currentTier.solution.trim() ? (
                                    <div className="text-muted">
                                        <p>{t("manual.previewEmpty")}</p>
                                        <hr />
                                        <h6>{t("manual.tips")}</h6>
                                        <ul className="small mb-0">
                                            <li>{t("manual.tipLines")}</li>
                                            <li>{t("manual.tipIndent")}</li>
                                            <li>{t("manual.tipLength")}</li>
                                            <li>{t("manual.tipDifficulty")}</li>
                                            <li>{t("manual.tipVariation")}</li>
                                        </ul>
                                    </div>
                                ) : (
                                    <div>
                                        {/* Simulated Parsons blocks */}
                                        <div className="mb-3">
                                            <small className="text-muted d-block mb-2">
                                                {t("manual.blocksCount", { count: previewLines.length })}
                                            </small>
                                            {currentTier.solution
                                                .split("\n")
                                                .filter((l) => l.trim().length > 0)
                                                .map((line, idx) => {
                                                    const indent = line.search(/\S/);
                                                    const indentLevel = Math.floor(
                                                        Math.max(0, indent) / 4
                                                    );
                                                    return (
                                                        <div
                                                            key={idx}
                                                            className="border rounded px-3 py-2 mb-1 bg-light font-monospace"
                                                            style={{
                                                                marginLeft: `${indentLevel * 24}px`,
                                                                fontSize: "0.8rem",
                                                                cursor: "grab",
                                                                borderLeft: "3px solid #0d6efd",
                                                                whiteSpace: "pre",
                                                                overflowX: "auto",
                                                            }}
                                                        >
                                                            {renderGapLine(line.trim())}
                                                        </div>
                                                    );
                                                })}
                                        </div>

                                        {/* Statement preview */}
                                        {currentTier.statement.trim() && (
                                            <div className="mt-3 p-3 bg-light rounded border">
                                                <small className="text-muted d-block mb-1">
                                                    {t("manual.problemStatement")}
                                                </small>
                                                <div style={{ fontSize: "0.9rem" }}>
                                                    {currentTier.statement}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* All tiers summary */}
                                <hr />
                                <h6 className="text-muted">{t("manual.summary")}</h6>
                                <table className="table table-sm table-borderless mb-0" style={{ fontSize: "0.85rem" }}>
                                    <tbody>
                                        <tr>
                                            <td className="text-muted">{t("manual.language")}</td>
                                            <td>{language || "—"}</td>
                                        </tr>
                                        <tr>
                                            <td className="text-muted">{t("manual.conceptsHeader")}</td>
                                            <td>{conceptTokens.length > 0 ? conceptTokens.join(", ") : "—"}</td>
                                        </tr>
                                        <tr>
                                            <td className="text-muted">{t("manual.tierLevel")}</td>
                                            <td>
                                                {tiers.map((tier) => (
                                                    <span
                                                        key={tier.tierLevel}
                                                        className={`badge me-1 ${
                                                            tier.statement.trim() && tier.solution.trim()
                                                                ? "bg-success"
                                                                : "bg-secondary"
                                                        }`}
                                                    >
                                                        T{tier.tierLevel}
                                                        {tier.statement.trim() && tier.solution.trim()
                                                            ? " \u2713"
                                                            : ""}
                                                    </span>
                                                ))}
                                            </td>
                                        </tr>
                                        {tiers.map((tier) => (
                                            <tr key={`lines-${tier.tierLevel}`}>
                                                <td className="text-muted">
                                                    T{tier.tierLevel} Lines
                                                </td>
                                                <td>
                                                    {tier.solution
                                                        .split("\n")
                                                        .filter((l) => l.trim()).length || 0}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </form>
        </div>
    );
}
