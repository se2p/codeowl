import { escapeCsvField, downloadTextFile } from "./csv.ts";

/**
 * Reading a class roster out of a CSV, and writing the invite links back out.
 *
 * Both halves live here rather than in the page so they can be exercised
 * directly: a roster arrives as whatever a teacher's spreadsheet produced, and
 * guessing wrong about it costs real student accounts.
 */

/** users.first_name / last_name are varchar(100); refuse rather than let Postgres. */
export const MAX_NAME_LENGTH = 100;

/**
 * An import creates an account per row and cannot be undone in one go, so a
 * wrong file — an export of the whole school, say — must not go through
 * unnoticed. The backend enforces the same bound.
 */
export const MAX_IMPORT_ROWS = 200;

/** Delimiters a spreadsheet might have chosen, in preference order for ties. */
const DELIMITERS = [",", ";", "\t"];

/** Both cells have to read as a heading before we drop a row as one. */
const FIRST_NAME_HEADINGS = ["firstname", "first name", "first", "vorname", "given name", "givenname"];
const LAST_NAME_HEADINGS = ["lastname", "last name", "last", "nachname", "surname", "familyname", "family name"];

export interface ParsedStudent {
    firstName: string;
    lastName: string;
}

/** What went wrong, as an i18n key plus its placeholders. */
export interface CsvError {
    key: string;
    params?: Record<string, string | number>;
}

export interface CsvParseResult {
    students: ParsedStudent[];
    /** Set when nothing should be imported; `students` is then empty. */
    error?: CsvError;
}

/**
 * Splits CSV text into rows of raw fields.
 *
 * Quoted fields may contain the delimiter, newlines, and doubled quotes. A
 * quote only opens a field at its start, so a quote character in the middle of
 * a name is kept as written.
 */
export function splitRows(text: string, delimiter: string): string[][] {
    const rows: string[][] = [];
    let row: string[] = [];
    let field = "";
    let quoted = false;

    for (let i = 0; i < text.length; i++) {
        const ch = text[i];

        if (quoted) {
            if (ch === '"') {
                if (text[i + 1] === '"') {
                    field += '"';
                    i++;
                } else {
                    quoted = false;
                }
            } else {
                field += ch;
            }
            continue;
        }

        if (ch === '"' && field.trim() === "") {
            // Leading spaces before the quote are the spreadsheet's, not the name's.
            quoted = true;
            field = "";
        } else if (ch === delimiter) {
            row.push(field);
            field = "";
        } else if (ch === "\r") {
            // Swallowed, so CRLF files behave like LF ones.
        } else if (ch === "\n") {
            row.push(field);
            rows.push(row);
            row = [];
            field = "";
        } else {
            field += ch;
        }
    }

    if (field !== "" || row.length > 0) {
        row.push(field);
        rows.push(row);
    }
    return rows;
}

/** Counts a candidate delimiter, ignoring anything inside quotes. */
function countOutsideQuotes(text: string, delimiter: string): number {
    let count = 0;
    let quoted = false;
    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (ch === '"') {
            quoted = !quoted;
        } else if (!quoted && ch === delimiter) {
            count++;
        }
    }
    return count;
}

/**
 * Picks the delimiter the file was written with.
 *
 * German spreadsheets export semicolons, English ones commas, and neither
 * announces which; whichever appears most often is separating the columns.
 */
export function detectDelimiter(text: string): string {
    let best = DELIMITERS[0];
    let bestCount = 0;
    for (const candidate of DELIMITERS) {
        const count = countOutsideQuotes(text, candidate);
        if (count > bestCount) {
            best = candidate;
            bestCount = count;
        }
    }
    return best;
}

/** True when both cells name a column rather than a person. */
function looksLikeHeading(fields: string[]): boolean {
    if (fields.length < 2) return false;
    const first = fields[0].trim().toLowerCase();
    const last = fields[1].trim().toLowerCase();
    return FIRST_NAME_HEADINGS.includes(first) && LAST_NAME_HEADINGS.includes(last);
}

/**
 * Reads a roster of first and last names.
 *
 * Row numbers in errors count lines in the file as the teacher sees them,
 * blanks and heading included, so a reported row can actually be found.
 *
 * Any bad row rejects the whole file rather than importing around it: a
 * half-imported class is harder to repair than one that never arrived.
 */
export function parseStudentCsv(text: string): CsvParseResult {
    const delimiter = detectDelimiter(text);
    const rows = splitRows(text, delimiter);

    const students: ParsedStudent[] = [];
    let headingSkipped = false;

    for (let i = 0; i < rows.length; i++) {
        const lineNumber = i + 1;
        // A trailing delimiter leaves an empty cell that is not a column.
        const fields = [...rows[i]];
        while (fields.length > 0 && fields[fields.length - 1].trim() === "") {
            fields.pop();
        }

        if (fields.length === 0) continue;

        if (!headingSkipped && students.length === 0 && looksLikeHeading(fields)) {
            headingSkipped = true;
            continue;
        }

        if (fields.length !== 2) {
            return {
                students: [],
                error: { key: "csvImport.errorColumns", params: { row: lineNumber, count: fields.length } },
            };
        }

        // Trimming also disposes of the byte order mark Excel writes ahead of
        // the first field, which counts as whitespace; left in, it would become
        // part of the first student's given name.
        const firstName = fields[0].trim();
        const lastName = fields[1].trim();

        if (!firstName || !lastName) {
            return { students: [], error: { key: "csvImport.errorMissingName", params: { row: lineNumber } } };
        }
        if (firstName.length > MAX_NAME_LENGTH || lastName.length > MAX_NAME_LENGTH) {
            return {
                students: [],
                error: { key: "csvImport.errorNameTooLong", params: { row: lineNumber, max: MAX_NAME_LENGTH } },
            };
        }

        students.push({ firstName, lastName });
    }

    if (students.length === 0) {
        return { students: [], error: { key: "csvImport.errorEmpty" } };
    }
    if (students.length > MAX_IMPORT_ROWS) {
        return {
            students: [],
            error: { key: "csvImport.errorTooMany", params: { count: students.length, max: MAX_IMPORT_ROWS } },
        };
    }

    return { students };
}

// ── Invite links ─────────────────────────────────────────

export interface InviteRow {
    firstName: string;
    lastName: string;
    inviteToken: string | null;
}

/**
 * The onboarding link a student follows to claim their account.
 *
 * Origin and base path are arguments so this stays the one place that knows the
 * shape of the link, whether it is being copied to the clipboard or written
 * into a file.
 */
export function studentInviteUrl(
    token: string,
    origin: string = window.location.origin,
    basePath: string = import.meta.env.BASE_URL,
): string {
    return `${origin}${basePath}student-onboarding?token=${token}`;
}

export interface InviteCsvOptions {
    /** Column titles, in the teacher's language. */
    headers: [string, string, string];
    /**
     * Semicolon for German spreadsheets, comma elsewhere. The file is only
     * useful if it opens into columns in the teacher's own Excel.
     */
    delimiter?: string;
    origin?: string;
    basePath?: string;
}

/**
 * The finished file: one row per student, with the link to send them.
 *
 * Carries a byte order mark and CRLF endings because its destination is a
 * spreadsheet on Windows, where their absence turns every umlaut into rubble.
 */
export function buildInviteCsv(students: InviteRow[], options: InviteCsvOptions): string {
    const delimiter = options.delimiter ?? ",";
    const rows = [
        options.headers.slice(0, 3),
        ...students.map((s) => [
            s.firstName,
            s.lastName,
            s.inviteToken ? studentInviteUrl(s.inviteToken, options.origin, options.basePath) : "",
        ]),
    ];
    return "\uFEFF" + rows.map((r) => r.map(escapeCsvField).join(delimiter)).join("\r\n") + "\r\n";
}

/** Semicolons for German, commas for everything else; see {@link InviteCsvOptions}. */
export function csvDelimiterForLocale(locale: string | undefined): string {
    return (locale ?? "").toLowerCase().startsWith("de") ? ";" : ",";
}

/** Turns a class name into something safe to hand a filesystem. */
export function inviteCsvFilename(className: string): string {
    const slug = className
        .trim()
        .replace(/[^\p{L}\p{N}]+/gu, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 60);
    return slug ? `invite-links-${slug}.csv` : "invite-links.csv";
}

/** Writes the invite links out for the teacher to save. */
export function downloadInviteCsv(students: InviteRow[], className: string, options: InviteCsvOptions) {
    downloadTextFile(buildInviteCsv(students, options), inviteCsvFilename(className), "text/csv;charset=utf-8");
}

// ── Template ─────────────────────────────────────────────

/** Two rows that answer "what should the file look like?" without prose. */
const TEMPLATE_ROWS: ParsedStudent[] = [
    { firstName: "Anna", lastName: "Müller" },
    { firstName: "Ben", lastName: "Weber" },
];

/**
 * A fillable example of the roster format.
 *
 * The sample names carry an umlaut deliberately: a teacher who opens this,
 * types over it and saves finds out here whether their spreadsheet mangles
 * German names, rather than after importing a class of them.
 */
export function buildTemplateCsv(headers: [string, string], delimiter = ","): string {
    const rows = [headers.slice(0, 2), ...TEMPLATE_ROWS.map((s) => [s.firstName, s.lastName])];
    return "\uFEFF" + rows.map((r) => r.map(escapeCsvField).join(delimiter)).join("\r\n") + "\r\n";
}

/** Hands the teacher the example file to fill in. */
export function downloadTemplateCsv(headers: [string, string], delimiter = ",") {
    downloadTextFile(buildTemplateCsv(headers, delimiter), "student-import-template.csv", "text/csv;charset=utf-8");
}
