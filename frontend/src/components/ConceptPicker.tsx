import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
    addConcept,
    isNewConcept,
    removeConcept,
    suggestConcepts,
} from "../lib/conceptSuggest";

/**
 * Picks the concepts mapped to a curriculum learning area.
 *
 * <p>Follows {@link TagEditor}'s shape — chips above, an autocomplete input
 * below, ↑/↓/Enter/Esc handled locally so no popper library is needed — but is
 * a plain controlled field: it owns no server state and saves nothing. The
 * parent form holds the value and writes it with the rest of the topic.
 *
 * <p>The catalogue is a few dozen names the parent has already fetched, so
 * matching runs locally with no debounce or autocomplete endpoint. Suggestions
 * appear on focus, before anything is typed, because the point is to reach the
 * concepts that already exist rather than to invent near-duplicates of them.
 * A name outside the catalogue is still allowed — the backend creates it — and
 * is marked as new while typing.
 */
export interface ConceptPickerProps {
    /** Currently selected concept names, in the catalogue's spelling. */
    value: string[];
    onChange: (next: string[]) => void;
    /** Every known concept name; may be empty while it is still loading. */
    catalogue: string[];
    inputId?: string;
}

/**
 * Every match is listed and the box scrolls, rather than the list being cut
 * short: a cap silently hides concepts that do exist, which is the opposite of
 * what the picker is for — an alphabetical cut at 8 stopped around
 * "boolean-logic" and made the rest of the catalogue look absent. The number
 * is only a guard against a pathologically large catalogue.
 */
const SUGGESTION_LIMIT = 500;

/** Roughly eight rows; the rest is reached by scrolling. */
const SUGGESTION_MAX_HEIGHT = 260;

export default function ConceptPicker({ value, onChange, catalogue, inputId }: ConceptPickerProps) {
    const { t } = useTranslation();

    const [query, setQuery] = useState("");
    const [open, setOpen] = useState(false);
    const [highlighted, setHighlighted] = useState(0);

    const containerRef = useRef<HTMLDivElement>(null);
    const listRef = useRef<HTMLDivElement>(null);

    const suggestions = suggestConcepts(catalogue, query, value, SUGGESTION_LIMIT);

    // Close the dropdown when the click lands outside the picker.
    useEffect(() => {
        function onDocClick(e: MouseEvent) {
            if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        }
        document.addEventListener("mousedown", onDocClick);
        return () => document.removeEventListener("mousedown", onDocClick);
    }, []);

    // A shrinking suggestion list must not leave the highlight past its end.
    useEffect(() => {
        setHighlighted(h => Math.min(h, Math.max(suggestions.length - 1, 0)));
    }, [suggestions.length]);

    // The list scrolls, so ↑/↓ has to bring the highlighted row along with it —
    // otherwise the selection walks out of sight and the box looks frozen.
    // (scrollIntoView is absent in jsdom, hence the optional call.)
    useEffect(() => {
        if (!open) return;
        const row = listRef.current?.children[highlighted] as HTMLElement | undefined;
        row?.scrollIntoView?.({ block: "nearest" });
    }, [highlighted, open]);

    const commit = (raw: string) => {
        const next = addConcept(value, catalogue, raw);
        if (next !== value) {
            onChange(next);
        }
        setQuery("");
        setHighlighted(0);
    };

    const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "ArrowDown" && suggestions.length > 0) {
            e.preventDefault();
            setOpen(true);
            setHighlighted(h => Math.min(h + 1, suggestions.length - 1));
            return;
        }
        if (e.key === "ArrowUp" && suggestions.length > 0) {
            e.preventDefault();
            setHighlighted(h => Math.max(h - 1, 0));
            return;
        }
        if (e.key === "Enter" || e.key === ",") {
            // Enter on a highlighted suggestion takes it; otherwise the typed
            // text stands on its own, so a new concept needs no extra gesture.
            e.preventDefault();
            if (open && suggestions[highlighted]) {
                commit(suggestions[highlighted]);
            } else if (query.trim()) {
                commit(query);
            }
            return;
        }
        if (e.key === "Escape") {
            setOpen(false);
            return;
        }
        if (e.key === "Backspace" && query === "" && value.length > 0) {
            // The usual chip-input shorthand: backspace on an empty field eats
            // the last chip rather than doing nothing.
            onChange(value.slice(0, -1));
        }
    };

    return (
        <div ref={containerRef} className="position-relative">
            {value.length > 0 && (
                <div className="d-flex flex-wrap gap-1 mb-2">
                    {value.map(concept => (
                        <span key={concept} className="badge bg-secondary-subtle text-secondary-emphasis d-inline-flex align-items-center gap-1">
                            {concept}
                            <button
                                type="button"
                                className="btn-close btn-close-sm"
                                style={{ fontSize: "0.5rem" }}
                                aria-label={t("curriculum.conceptRemove", { concept })}
                                onClick={() => onChange(removeConcept(value, concept))}
                            />
                        </span>
                    ))}
                </div>
            )}

            <input
                id={inputId}
                className="form-control form-control-sm"
                value={query}
                placeholder={t("curriculum.conceptsPlaceholder")}
                autoComplete="off"
                role="combobox"
                aria-expanded={open && suggestions.length > 0}
                aria-controls="conceptPickerSuggestions"
                onChange={e => { setQuery(e.target.value); setOpen(true); }}
                onFocus={() => setOpen(true)}
                onKeyDown={onKeyDown}
            />

            {open && suggestions.length > 0 && (
                <div
                    ref={listRef}
                    id="conceptPickerSuggestions"
                    className="list-group position-absolute w-100 shadow-sm"
                    role="listbox"
                    style={{ zIndex: 1056, maxHeight: SUGGESTION_MAX_HEIGHT, overflowY: "auto" }}
                >
                    {/* The option is the button itself, not a wrapper around it:
                        the thing that carries the role has to be the thing that
                        takes the click. */}
                    {suggestions.map((concept, i) => (
                        <button
                            key={concept}
                            type="button"
                            role="option"
                            aria-selected={i === highlighted}
                            className={`list-group-item list-group-item-action py-1 small text-start ${i === highlighted ? "active" : ""}`}
                            // mousedown, not click: the input's blur would
                            // otherwise close the list before the click lands.
                            onMouseDown={e => { e.preventDefault(); commit(concept); }}
                            onMouseEnter={() => setHighlighted(i)}
                        >
                            {concept}
                        </button>
                    ))}
                </div>
            )}

            {isNewConcept(catalogue, query) && (
                <div className="form-text">{t("curriculum.conceptNew", { concept: query.trim() })}</div>
            )}
        </div>
    );
}
