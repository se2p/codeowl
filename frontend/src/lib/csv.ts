/**
 * The bits of CSV handling that are the same wherever we write one.
 *
 * Kept apart from `export.ts` so that modules needing only a comma and a
 * download do not pull in the PDF machinery along with it. That module keeps
 * its own copies on purpose: its escaping is looser than the one below, and
 * tightening it would silently change the bytes of exports people already have.
 */

/**
 * Quotes a field if it carries anything that would otherwise break the row.
 *
 * Semicolons count: a file written with commas is still routinely opened by a
 * spreadsheet configured to split on semicolons, and quoting keeps such a field
 * whole when that happens.
 */
export function escapeCsvField(value: string | number | null | undefined): string {
    if (value == null) return "";
    const s = String(value);
    if (/[",;\t\r\n]/.test(s)) {
        return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
}

/** Hands the browser a generated file to save. */
export function downloadTextFile(content: string, filename: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

/**
 * Turns the bytes of an uploaded CSV into text, whichever encoding the
 * teacher's spreadsheet saved it in.
 *
 * Excel on a German Windows saves "CSV (Trennzeichen-getrennt)" as
 * Windows-1252, not UTF-8, and `File.text()` always assumes UTF-8 — every
 * umlaut came out as "�". UTF-8 is tried strictly first; a Windows-1252 file
 * with any umlaut in it is almost never valid UTF-8, so a failure there is a
 * reliable sign to fall back. A file of plain ASCII decodes the same either way.
 */
export function decodeCsvBytes(bytes: ArrayBuffer | Uint8Array): string {
    try {
        return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch {
        return new TextDecoder("windows-1252").decode(bytes);
    }
}

/** Reads an uploaded CSV file as text. See {@link decodeCsvBytes}. */
export async function readCsvFile(file: File): Promise<string> {
    return decodeCsvBytes(await file.arrayBuffer());
}
