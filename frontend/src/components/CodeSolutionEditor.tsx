import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { makeGapMarker, renderGapLine, hasGapMarker, type GapMode } from "../lib/gapMarkers";
import type { SplitEntry } from "../types/models";

/* ────────────────────────────────────────────────────────────── */
/*  AP31 split helpers                                            */
/* ────────────────────────────────────────────────────────────── */

/** Leading whitespace of a line (kept as the fixed, non-draggable prefix). */
function leadingWhitespace(line: string): string {
    const m = line.match(/^\s*/);
    return m ? m[0] : "";
}

/** Split a line body into fragments at the given (sorted, deduped) character positions. */
function fragmentsFromPoints(body: string, points: number[]): string[] {
    const pts = [...new Set(points)].filter((p) => p > 0 && p < body.length).sort((a, b) => a - b);
    const frags: string[] = [];
    let start = 0;
    for (const p of pts) {
        frags.push(body.slice(start, p));
        start = p;
    }
    frags.push(body.slice(start));
    return frags;
}

/** True if a split still reconstructs its line in the current code (prefix + join + suffix). */
function splitReconstructs(s: SplitEntry, lines: string[]): boolean {
    if (s.lineIndex < 0 || s.lineIndex >= lines.length) return false;
    const prefix = s.prefix ?? "";
    const suffix = s.suffix ?? "";
    return prefix + s.fragments.join("") + suffix === lines[s.lineIndex];
}

/* ────────────────────────────────────────────────────────────── */
/*  Props                                                         */
/* ────────────────────────────────────────────────────────────── */

interface CodeSolutionEditorProps {
    /** The raw code solution (may contain inline `$$blank:…$$` gap markers). */
    code: string;
    onCodeChange: (code: string) => void;
    /** AP31 inline-split entries whose fragments reconstruct the matching `code` lines. */
    splits: SplitEntry[];
    onSplitsChange: (splits: SplitEntry[]) => void;
    /** Rows for the code textarea. Default 12. */
    rows?: number;
    /** Show the AP31 inline-split editor. Default true. */
    showSplitEditor?: boolean;
    /** Show an inline gap-marker preview under the textarea (for contexts without a live preview). */
    showGapPreview?: boolean;
    /** Show the non-empty-lines / character counters. Default true. */
    showCounters?: boolean;
    /** Report validation errors (e.g. an invalid gap selection) to the parent. */
    onError?: (msg: string | null) => void;
    /** Placeholder for the textarea. */
    placeholder?: string;
}

/* ────────────────────────────────────────────────────────────── */
/*  Component                                                     */
/* ────────────────────────────────────────────────────────────── */

/**
 * Shared code-solution editor used by both the manual task creator and the task
 * edit modal. Bundles the code textarea, the AP30 fill-in-the-gaps toolbar and the
 * AP31 inline-split editor so every one of those features is defined exactly once.
 *
 * Controlled component: the parent owns `code` + `splits` and receives updates via
 * `onCodeChange` / `onSplitsChange`.
 */
export default function CodeSolutionEditor({
    code,
    onCodeChange,
    splits,
    onSplitsChange,
    rows = 12,
    showSplitEditor = true,
    showGapPreview = false,
    showCounters = true,
    onError,
    placeholder,
}: CodeSolutionEditorProps) {
    const { t } = useTranslation();

    const codeTaRef = useRef<HTMLTextAreaElement>(null);
    const [gapMode, setGapMode] = useState<GapMode>("trim");

    // AP31: which solution line is being split + its cut points.
    const [splitEdit, setSplitEdit] = useState<{ lineIndex: number; points: number[]; distractor: string } | null>(null);

    // AP31: keep `splits` consistent with `code`. When a line is edited such that a saved
    // split no longer reconstructs it, drop that split so the editor never shows (or the
    // backend never rejects) a stale entry.
    useEffect(() => {
        const lines = code.split("\n");
        const pruned = splits.filter((s) => splitReconstructs(s, lines));
        if (pruned.length !== splits.length) {
            onSplitsChange(pruned);
        }
        // Intentionally only re-run when the code text changes.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [code]);

    // ── AP30: wrap the current textarea selection in a $$blank:…$$ marker ──
    const markSelectionAsGap = () => {
        const ta = codeTaRef.current;
        if (!ta) return;
        const start = ta.selectionStart;
        const end = ta.selectionEnd;
        if (start == null || end == null || start === end) {
            onError?.(t("taskEdit.gapSelectHint", "Bitte zuerst Text innerhalb einer Code-Zeile markieren, um eine Lücke zu setzen."));
            return;
        }
        const selected = code.substring(start, end);
        if (selected.includes("\n")) {
            onError?.(t("taskEdit.gapSingleLine", "Eine Lücke darf nur innerhalb einer einzelnen Zeile liegen."));
            return;
        }
        const marker = makeGapMarker(selected, gapMode);
        const newVal = code.substring(0, start) + marker + code.substring(end);
        onCodeChange(newVal);
        onError?.(null);
        requestAnimationFrame(() => {
            ta.focus();
            const pos = start + marker.length;
            ta.selectionStart = ta.selectionEnd = pos;
        });
    };

    // ── Tab inserts 4 spaces instead of leaving the field ──
    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key !== "Tab") return;
        e.preventDefault();
        const ta = e.currentTarget;
        const start = ta.selectionStart;
        const end = ta.selectionEnd;
        const spaces = "    "; // 4 spaces
        const newVal = ta.value.substring(0, start) + spaces + ta.value.substring(end);
        onCodeChange(newVal);
        requestAnimationFrame(() => {
            ta.selectionStart = ta.selectionEnd = start + spaces.length;
        });
    };

    const nonEmptyLines = code.split("\n").filter((l) => l.trim()).length;

    return (
        <>
            {/* AP30: Fill-in-the-Gaps toolbar */}
            <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
                <button
                    type="button"
                    className="btn btn-sm btn-outline-warning"
                    onClick={markSelectionAsGap}
                    title={t("taskEdit.gapButtonTitle", "Markierten Text in der Code-Zeile als Freitext-Lücke setzen")}
                >
                    <i className="bi bi-input-cursor-text me-1"></i>
                    {t("taskEdit.gapButton", "Auswahl als Lücke")}
                </button>
                <select
                    className="form-select form-select-sm"
                    style={{ width: "auto" }}
                    value={gapMode}
                    onChange={(e) => setGapMode(e.target.value as GapMode)}
                    title={t("taskEdit.gapModeTitle", "Vergleichsmodus für neue Lücken")}
                >
                    <option value="trim">trim (Standard)</option>
                    <option value="exact">exact</option>
                    <option value="ci">case-insensitive</option>
                </select>
                <small className="text-muted">
                    {t("taskEdit.gapHint", "Text markieren → „Auswahl als Lücke“. Mehrere Lücken pro Zeile möglich.")}
                </small>
            </div>

            {/* Code textarea */}
            <textarea
                ref={codeTaRef}
                className="form-control font-monospace"
                rows={rows}
                value={code}
                onChange={(e) => onCodeChange(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                style={{ fontSize: "0.875rem", tabSize: 4, lineHeight: 1.5 }}
                spellCheck={false}
            />

            {showCounters && (
                <div className="form-text d-flex justify-content-between">
                    <span>{t("manual.nonEmptyLines", { count: nonEmptyLines })}</span>
                    <span>{t("manual.characters", { count: code.length })}</span>
                </div>
            )}

            {/* AP30: inline gap preview — lines containing $$blank$$ markers, rendered as chips */}
            {showGapPreview && hasGapMarker(code) && (
                <div className="mt-2 p-2 rounded border bg-light">
                    <div className="small text-muted mb-1">
                        <i className="bi bi-eye me-1"></i>
                        {t("taskEdit.gapPreview", "Lücken-Vorschau")}
                    </div>
                    <div className="font-monospace small">
                        {code.split("\n").filter((l) => hasGapMarker(l)).map((l, i) => (
                            <div key={i} style={{ whiteSpace: "pre-wrap" }}>{renderGapLine(l)}</div>
                        ))}
                    </div>
                </div>
            )}

            {/* AP31: Inline-Split editor */}
            {showSplitEditor && (
                <div className="mt-3 mb-1">
                    <label className="form-label fw-semibold">
                        <i className="bi bi-scissors me-1"></i>{t("splitEditor.title", "Inline-Split (AP31)")}
                    </label>
                    <div className="form-text mb-2">
                        {t("splitEditor.help", "Zeile wählen → Trennpunkte per Klick zwischen den Zeichen setzen (2–4 Fragmente). Optionaler Fragment-Distraktor.")}
                    </div>
                    <div className="border rounded p-2" style={{ maxHeight: 280, overflowY: "auto" }}>
                        {code.split("\n").map((rawLine, li) => {
                            if (!rawLine.trim()) return null;
                            const existing = splits?.find((s) => s.lineIndex === li);
                            const editing = splitEdit?.lineIndex === li;
                            const prefix = leadingWhitespace(rawLine);
                            const body = rawLine.slice(prefix.length);
                            const frags = editing ? fragmentsFromPoints(body, splitEdit!.points) : [];
                            const valid = frags.length >= 2 && frags.length <= 4 && frags.every((f) => f.length > 0);
                            return (
                                <div key={li} className="mb-2 pb-2 border-bottom">
                                    <div className="d-flex align-items-center gap-2">
                                        <code className="small flex-grow-1 text-truncate">{rawLine}</code>
                                        {existing && !editing && (
                                            <span className="badge text-bg-info">{t("splitEditor.fragments", { count: existing.fragments.length })}</span>
                                        )}
                                        {!editing && (
                                            <button type="button" className="btn btn-sm btn-outline-primary"
                                                onClick={() => setSplitEdit({ lineIndex: li, points: [], distractor: "" })}>
                                                {existing ? t("splitEditor.edit", "Bearbeiten") : t("splitEditor.split", "Split")}
                                            </button>
                                        )}
                                        {existing && !editing && (
                                            <button type="button" className="btn btn-sm btn-outline-danger"
                                                onClick={() => onSplitsChange((splits ?? []).filter((s) => s.lineIndex !== li))}>
                                                <i className="bi bi-x"></i>
                                            </button>
                                        )}
                                    </div>
                                    {/* AP31: read-only view of a generated/saved split so teachers can
                                        actually see its fragments + distractor (the inline editor below
                                        can't represent generated prefix/suffix splits). */}
                                    {existing && !editing && (
                                        <div className="mt-1 d-flex flex-wrap gap-1 align-items-center">
                                            {existing.prefix && <span className="text-muted small font-monospace">{existing.prefix}</span>}
                                            {existing.fragments.map((f, fi) => (
                                                <span key={fi} className="badge text-bg-warning font-monospace">{f === "" ? "∅" : f}</span>
                                            ))}
                                            {existing.suffix && <span className="text-muted small font-monospace">{existing.suffix}</span>}
                                            {(existing.distractors ?? []).length > 0 && (
                                                <span className="text-muted small ms-1">{t("splitEditor.distractor", "Distraktor")}:</span>
                                            )}
                                            {(existing.distractors ?? []).map((d, di) => (
                                                <span key={"d" + di} className="badge text-bg-danger font-monospace" title={d.type ?? "distractor"}>
                                                    {d.text}
                                                </span>
                                            ))}
                                            {(existing.distractors ?? []).length > 0 && (
                                                <button type="button" className="btn btn-sm btn-outline-danger py-0 px-1"
                                                    title={t("splitEditor.removeDistractor", "Distraktor entfernen")}
                                                    onClick={() => onSplitsChange((splits ?? []).map((s) =>
                                                        s.lineIndex === li ? { ...s, distractors: [] } : s))}>
                                                    <i className="bi bi-x-circle me-1"></i>{t("splitEditor.removeDistractor", "Distraktor entfernen")}
                                                </button>
                                            )}
                                        </div>
                                    )}
                                    {editing && (
                                        <div className="mt-2 p-2 bg-light rounded">
                                            <div className="mb-2" style={{ fontFamily: "monospace", fontSize: "0.95rem", lineHeight: 2.2 }}>
                                                {prefix && <span className="text-muted">{prefix.replace(/ /g, "·")}</span>}
                                                {[...body].map((ch, ci) => (
                                                    <span key={ci}>
                                                        {ci > 0 && (
                                                            <span role="button" title={t("splitEditor.toggleCut", "Trennpunkt umschalten")}
                                                                onClick={() => setSplitEdit((se) => {
                                                                    if (!se) return se;
                                                                    const has = se.points.includes(ci);
                                                                    return { ...se, points: has ? se.points.filter((p) => p !== ci) : [...se.points, ci] };
                                                                })}
                                                                style={{
                                                                    display: "inline-block", width: splitEdit!.points.includes(ci) ? 12 : 7,
                                                                    textAlign: "center", cursor: "pointer",
                                                                    color: splitEdit!.points.includes(ci) ? "#dc3545" : "#adb5bd",
                                                                    fontWeight: splitEdit!.points.includes(ci) ? 700 : 400,
                                                                }}>
                                                                {splitEdit!.points.includes(ci) ? "│" : "·"}
                                                            </span>
                                                        )}
                                                        <span>{ch === " " ? " " : ch}</span>
                                                    </span>
                                                ))}
                                            </div>
                                            <div className="d-flex flex-wrap gap-1 mb-2">
                                                {frags.map((f, fi) => (
                                                    <span key={fi} className="badge text-bg-warning" style={{ fontFamily: "monospace" }}>
                                                        {f === "" ? "∅" : f}
                                                    </span>
                                                ))}
                                            </div>
                                            <div className="input-group input-group-sm mb-2">
                                                <span className="input-group-text">{t("splitEditor.distractor", "Distraktor")}</span>
                                                <input className="form-control" value={splitEdit!.distractor}
                                                    onChange={(e) => setSplitEdit((se) => (se ? { ...se, distractor: e.target.value } : se))}
                                                    placeholder={t("splitEditor.distractorPlaceholder", "optional, z. B. i <= arr.length;")} />
                                            </div>
                                            {!valid && <div className="text-danger small mb-2">{t("splitEditor.invalid", "2–4 nicht-leere Fragmente nötig.")}</div>}
                                            <div className="d-flex gap-2">
                                                <button type="button" className="btn btn-sm btn-success" disabled={!valid}
                                                    onClick={() => {
                                                        const entry: SplitEntry = {
                                                            lineIndex: li,
                                                            fragments: frags,
                                                            prefix,
                                                            suffix: "",
                                                            distractors: splitEdit!.distractor.trim()
                                                                ? [{ text: splitEdit!.distractor.trim(), type: "OFF_BY_ONE" }]
                                                                : [],
                                                        };
                                                        const rest = (splits ?? []).filter((s) => s.lineIndex !== li);
                                                        onSplitsChange([...rest, entry]);
                                                        setSplitEdit(null);
                                                    }}>
                                                    {t("splitEditor.save", "Speichern")}
                                                </button>
                                                <button type="button" className="btn btn-sm btn-outline-secondary"
                                                    onClick={() => setSplitEdit(null)}>
                                                    {t("splitEditor.cancel", "Abbrechen")}
                                                </button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                        {!code.trim() && (
                            <div className="text-muted small">{t("splitEditor.enterCodeFirst", "Erst Code eingeben.")}</div>
                        )}
                    </div>
                </div>
            )}
        </>
    );
}
