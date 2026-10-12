import { detectDelimiter, splitRows, MAX_IMPORT_ROWS, type CsvError } from "./studentImport.ts";

/**
 * Reading FIM usernames, one typed in or a column of them from a CSV.
 *
 * Mirrors FimAccount.normalizeUid / isValidUid in the backend, which checks the
 * same things again; checking here as well lets a bad file be refused with the
 * line it is on, before anything is sent.
 */

/** Same shape the backend accepts: lowercase letters, digits, dot, dash, underscore. */
const VALID_USERNAME = /^[a-z0-9][a-z0-9._-]{0,63}$/;

/** A first row reading like one of these is a column title, not a student. */
const USERNAME_HEADINGS = [
    "username",
    "user name",
    "fim username",
    "fim-username",
    "benutzername",
    "fim-benutzername",
    "fim benutzername",
    "kennung",
    "fim-kennung",
    "uid",
    "login",
];

/** The one spelling that is stored: LDAP compares uids case-insensitively. */
export function normalizeFimUsername(username: string): string {
    return username.trim().toLowerCase();
}

export function isValidFimUsername(normalized: string): boolean {
    return VALID_USERNAME.test(normalized);
}

export interface FimCsvParseResult {
    /** Normalized, in file order, each only once. */
    usernames: string[];
    /** Set when nothing should be imported; `usernames` is then empty. */
    error?: CsvError;
}

/**
 * Reads a CSV with one FIM username per row.
 *
 * Strictly one column: a roster exported with names beside the usernames would
 * otherwise have its first names read as usernames, and "anna" is a perfectly
 * valid one. Row numbers in errors are lines as the teacher sees the file.
 */
export function parseFimUsernameCsv(text: string): FimCsvParseResult {
    const rows = splitRows(text, detectDelimiter(text));
    const usernames: string[] = [];
    const seen = new Set<string>();

    for (let i = 0; i < rows.length; i++) {
        const lineNumber = i + 1;
        const fields = rows[i].map((f) => f.trim());
        // Trailing delimiters leave empty cells that are not columns.
        while (fields.length > 0 && fields[fields.length - 1] === "") {
            fields.pop();
        }
        if (fields.length === 0) continue;

        if (fields.length !== 1) {
            return {
                usernames: [],
                error: { key: "fimStudents.errorColumns", params: { row: lineNumber, count: fields.length } },
            };
        }

        // trim() above also removes the byte order mark Excel writes.
        const username = normalizeFimUsername(fields[0]);
        if (usernames.length === 0 && USERNAME_HEADINGS.includes(username)) {
            continue;
        }
        if (!isValidFimUsername(username)) {
            return {
                usernames: [],
                error: { key: "fimStudents.errorInvalidRow", params: { row: lineNumber, value: fields[0] } },
            };
        }
        if (!seen.has(username)) {
            seen.add(username);
            usernames.push(username);
        }
    }

    if (usernames.length === 0) {
        return { usernames: [], error: { key: "fimStudents.errorEmpty" } };
    }
    if (usernames.length > MAX_IMPORT_ROWS) {
        return {
            usernames: [],
            error: { key: "fimStudents.errorTooMany", params: { count: usernames.length, max: MAX_IMPORT_ROWS } },
        };
    }
    return { usernames };
}
