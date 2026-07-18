import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";
import type { SequenceTag } from "../types/models";
import TagChip from "./TagChip";

/**
 * AP19 Teil A (A11): Inline editor for a single sequence's tags.
 *
 * <p>Shown on teacher-owned sequences (the caller is expected to gate visibility). Consists of:
 * <ul>
 *   <li>a flex row of {@link TagChip}s, each with an X button to remove</li>
 *   <li>an autocomplete input below for adding new tags</li>
 * </ul>
 *
 * <p>Autocomplete is debounced at 150ms (typing is usually fast) and hits
 * {@code GET /api/library/tags/autocomplete?prefix=&amp;limit=8}. Keyboard nav (↑/↓/Enter/Esc) is
 * handled locally so the component works without a popper/listbox library.
 *
 * <p>Mutations use POST / DELETE against the per-sequence tag endpoints. The response is the
 * full, up-to-date tag list, which we pass back through {@code onTagsChanged} so parents can
 * re-render without an extra round-trip.
 */
export interface TagEditorProps {
    sequenceId: string;
    tags: SequenceTag[];
    /** Fires whenever the server confirms a mutation, with the new full tag list. */
    onTagsChanged: (next: SequenceTag[]) => void;
    /** Soft-disable when the user lacks permission — chips render but inputs/remove buttons disappear. */
    readOnly?: boolean;
    /** Optional max number of tags (soft limit — gates the add input only). */
    maxTags?: number;
}

const AUTOCOMPLETE_DEBOUNCE_MS = 150;
const AUTOCOMPLETE_LIMIT = 8;

export default function TagEditor({
    sequenceId,
    tags,
    onTagsChanged,
    readOnly = false,
    maxTags = 20,
}: TagEditorProps) {
    const { t } = useTranslation();

    const [input, setInput] = useState("");
    const [suggestions, setSuggestions] = useState<string[]>([]);
    const [showSuggestions, setShowSuggestions] = useState(false);
    const [highlighted, setHighlighted] = useState(0);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");

    const inputRef = useRef<HTMLInputElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Close the suggestion dropdown when clicking outside.
    useEffect(() => {
        function onDocClick(e: MouseEvent) {
            if (
                containerRef.current &&
                !containerRef.current.contains(e.target as Node)
            ) {
                setShowSuggestions(false);
            }
        }
        document.addEventListener("mousedown", onDocClick);
        return () => document.removeEventListener("mousedown", onDocClick);
    }, []);

    // Debounced autocomplete fetch.
    useEffect(() => {
        if (readOnly) return;
        const prefix = input.trim().toLowerCase();
        if (prefix.length === 0) {
            setSuggestions([]);
            return;
        }
        const h = window.setTimeout(async () => {
            try {
                const res = await api.get<string[]>("/library/tags/autocomplete", {
                    params: { prefix, limit: AUTOCOMPLETE_LIMIT },
                });
                // Filter out tags already on this sequence — no point suggesting them.
                const existing = new Set(tags.map((tt) => tt.tagName));
                setSuggestions(res.data.filter((s) => !existing.has(s)));
                setHighlighted(0);
            } catch {
                // Autocomplete failures should never block typing.
                setSuggestions([]);
            }
        }, AUTOCOMPLETE_DEBOUNCE_MS);
        return () => window.clearTimeout(h);
    }, [input, tags, readOnly]);

    // ── API helpers ──

    const addTag = useCallback(
        async (raw: string) => {
            const tag = raw.trim().toLowerCase();
            if (!tag) return;
            if (tags.some((tt) => tt.tagName === tag)) {
                setInput("");
                return;
            }
            if (tags.length >= maxTags) {
                setError(t("library.tagEditor.maxReached", { max: maxTags }));
                return;
            }
            setBusy(true);
            setError("");
            try {
                const res = await api.post<SequenceTag[]>(
                    `/library/sequences/${sequenceId}/tags`,
                    { tag },
                );
                onTagsChanged(res.data);
                setInput("");
                setSuggestions([]);
                inputRef.current?.focus();
            } catch (e) {
                console.error("addTag failed", e);
                setError(t("library.tagEditor.errorAdd"));
            } finally {
                setBusy(false);
            }
        },
        [sequenceId, tags, maxTags, onTagsChanged, t],
    );

    const removeTag = useCallback(
        async (tag: SequenceTag) => {
            setBusy(true);
            setError("");
            try {
                const res = await api.delete<SequenceTag[]>(
                    `/library/sequences/${sequenceId}/tags/${encodeURIComponent(tag.tagName)}`,
                );
                onTagsChanged(res.data);
            } catch (e) {
                console.error("removeTag failed", e);
                setError(t("library.tagEditor.errorRemove"));
            } finally {
                setBusy(false);
            }
        },
        [sequenceId, onTagsChanged, t],
    );

    // ── Keyboard handling ──
    function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
        if (!showSuggestions || suggestions.length === 0) {
            if (e.key === "Enter" && input.trim().length > 0) {
                e.preventDefault();
                addTag(input);
            }
            return;
        }

        switch (e.key) {
            case "ArrowDown":
                e.preventDefault();
                setHighlighted((h) => Math.min(h + 1, suggestions.length - 1));
                break;
            case "ArrowUp":
                e.preventDefault();
                setHighlighted((h) => Math.max(h - 1, 0));
                break;
            case "Enter":
                e.preventDefault();
                if (suggestions[highlighted]) addTag(suggestions[highlighted]);
                else if (input.trim().length > 0) addTag(input);
                break;
            case "Escape":
                setShowSuggestions(false);
                break;
            case "Tab":
                if (suggestions[highlighted]) {
                    e.preventDefault();
                    setInput(suggestions[highlighted]);
                    setShowSuggestions(false);
                }
                break;
        }
    }

    const atMax = tags.length >= maxTags;

    return (
        <div className="tag-editor" ref={containerRef}>
            {/* Existing tags */}
            <div className="d-flex flex-wrap gap-1 align-items-center">
                {tags.length === 0 && (
                    <span className="small text-muted fst-italic">
                        {t("library.tagEditor.noTags")}
                    </span>
                )}
                {tags.map((tag) => (
                    <TagChip
                        key={`${tag.tagName}-${tag.source}`}
                        tag={tag}
                        onRemove={readOnly || busy ? undefined : () => removeTag(tag)}
                    />
                ))}
            </div>

            {/* Add-tag input (hidden when read-only) */}
            {!readOnly && (
                <div className="position-relative mt-2">
                    <div className="input-group input-group-sm">
                        <span className="input-group-text">
                            <i className="bi bi-plus-lg"></i>
                        </span>
                        <input
                            ref={inputRef}
                            type="text"
                            className="form-control"
                            placeholder={
                                atMax
                                    ? (t("library.tagEditor.maxReached", { max: maxTags }) as string)
                                    : (t("library.tagEditor.inputPlaceholder") as string)
                            }
                            value={input}
                            onChange={(e) => {
                                setInput(e.target.value);
                                setShowSuggestions(true);
                            }}
                            onFocus={() => setShowSuggestions(true)}
                            onKeyDown={onKeyDown}
                            disabled={busy || atMax}
                            autoComplete="off"
                            aria-autocomplete="list"
                            aria-controls="tag-suggestions"
                            aria-expanded={showSuggestions && suggestions.length > 0}
                        />
                        <button
                            className="btn btn-outline-secondary"
                            disabled={busy || atMax || !input.trim()}
                            onClick={() => addTag(input)}
                            title={t("library.tagEditor.addTag") as string}
                        >
                            <i className="bi bi-check-lg"></i>
                        </button>
                    </div>

                    {/* Suggestion dropdown */}
                    {showSuggestions && suggestions.length > 0 && (
                        <ul
                            id="tag-suggestions"
                            role="listbox"
                            className="list-group position-absolute w-100 shadow-sm"
                            style={{ zIndex: 10, maxHeight: "12rem", overflowY: "auto" }}
                        >
                            {suggestions.map((s, i) => (
                                <li
                                    key={s}
                                    role="option"
                                    aria-selected={i === highlighted}
                                    className={`list-group-item list-group-item-action py-1 px-2 ${
                                        i === highlighted ? "active" : ""
                                    }`}
                                    style={{ cursor: "pointer", fontSize: "0.85rem" }}
                                    onMouseEnter={() => setHighlighted(i)}
                                    onMouseDown={(e) => {
                                        // mousedown (not click) so we fire before the input's blur
                                        e.preventDefault();
                                        addTag(s);
                                    }}
                                >
                                    <i className="bi bi-tag me-1 text-muted"></i>
                                    {s}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            )}

            {error && <div className="text-danger small mt-1">{error}</div>}
        </div>
    );
}
