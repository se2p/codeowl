/**
 * Splits a schedule hint into the clauses it should be shown on separate lines.
 *
 * These hints read as independent rules joined by a semicolon ("empty means X;
 * no time means Y"). Left to wrap on their own they broke mid-clause at
 * whatever width the column happened to have, which read as one run-on
 * sentence. The semicolon is kept on the line it ends.
 *
 * A hint with no semicolon comes back as a single line, so this is safe to
 * apply to every hint and to translations that phrase things differently.
 */
export function splitHintClauses(text: string): string[] {
    if (!text) return [];
    const parts = text.split(";");
    return parts
        .map((part, i) => part.trim() + (i < parts.length - 1 ? ";" : ""))
        // A trailing semicolon would otherwise leave an empty last line.
        .filter((line) => line !== "");
}
