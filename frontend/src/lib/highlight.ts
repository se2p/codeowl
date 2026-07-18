/**
 * AP19 Teil A (A14): Safe renderer for PostgreSQL {@code ts_headline} snippets.
 *
 * <p>The backend emits highlights using control-character markers ({@code \u0001} /
 * {@code \u0002}) around matched words rather than literal {@code <mark>} tags. This lets the
 * frontend HTML-escape the entire string first — protecting against XSS from anything the
 * teacher, the LLM summary, or an attacker ever wrote into a sequence — and then swap only the
 * markers for {@code <mark>}. Any real HTML in the source text stays visibly escaped.
 *
 * <p>Keep the two marker characters in sync with
 * {@code SequenceSearchService.HIGHLIGHT_START_MARKER / HIGHLIGHT_STOP_MARKER}.
 */

const HIGHLIGHT_START_MARKER = "\u0001";
const HIGHLIGHT_STOP_MARKER = "\u0002";

const HTML_ESCAPES: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
};

function escapeHtml(raw: string): string {
    return raw.replace(/[&<>"']/g, (c) => HTML_ESCAPES[c] ?? c);
}

/**
 * Convert a ts_headline snippet (with control-char markers) into an HTML string safe for
 * {@code dangerouslySetInnerHTML}. Returns an empty string for nullish / empty input so callers
 * can render a React fallback with a simple truthiness check.
 */
export function renderHighlight(raw: string | null | undefined): string {
    if (raw == null || raw === "") return "";
    // Strip any stray literal <mark> the DB might have emitted (defensive — not expected
    // given the new markers, but old cached rows could still contain them).
    const withoutLegacy = raw.replace(/<\/?mark>/gi, "");
    const escaped = escapeHtml(withoutLegacy);
    return escaped
        .split(HIGHLIGHT_START_MARKER)
        .join("<mark>")
        .split(HIGHLIGHT_STOP_MARKER)
        .join("</mark>");
}
