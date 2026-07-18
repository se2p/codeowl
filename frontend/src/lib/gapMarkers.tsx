import { type ReactNode } from "react";

// AP30: inline gap marker $$blank:<expected>[|<mode>]$$
// Kept in one place so every editor (ManualTaskCreator, TaskEditModal) and the
// preview stay in sync with the grammar parsed by public/jsparsons/parsons.js.
export const GAP_RE_SRC = "\\$\\$blank(?::((?:(?!\\$\\$).)*))?\\$\\$";

export type GapMode = "trim" | "exact" | "ci";

/** Build a $$blank:…$$ marker wrapping a selected substring. */
export function makeGapMarker(selected: string, mode: GapMode): string {
    return "$$blank:" + selected + (mode === "trim" ? "" : "|" + mode) + "$$";
}

/** True if the text contains at least one gap marker. */
export function hasGapMarker(text: string): boolean {
    return new RegExp(GAP_RE_SRC).test(text);
}

/** Renders a line, showing `$$blank:…$$` markers as gap chips. */
export function renderGapLine(line: string): ReactNode {
    const re = new RegExp(GAP_RE_SRC, "g");
    const parts: ReactNode[] = [];
    let last = 0;
    let m: RegExpExecArray | null;
    let k = 0;
    while ((m = re.exec(line)) !== null) {
        if (m.index > last) parts.push(line.substring(last, m.index));
        const inner = m[1] ?? "";
        let exp = inner;
        const sep = inner.lastIndexOf("|");
        if (sep >= 0 && ["exact", "trim", "ci"].includes(inner.substring(sep + 1))) {
            exp = inner.substring(0, sep);
        }
        parts.push(
            <span key={`g${k++}`} className="badge text-bg-warning fw-normal" style={{ fontFamily: "monospace" }}>
                {exp || "▁▁▁"}
            </span>
        );
        last = m.index + m[0].length;
    }
    if (last < line.length) parts.push(line.substring(last));
    return parts.length ? <>{parts}</> : line;
}
