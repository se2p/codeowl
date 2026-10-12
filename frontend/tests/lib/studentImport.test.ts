import { describe, it, expect } from "vitest";
import {
    parseStudentCsv,
    detectDelimiter,
    buildInviteCsv,
    buildTemplateCsv,
    csvDelimiterForLocale,
    inviteCsvFilename,
    studentInviteUrl,
    MAX_IMPORT_ROWS,
    MAX_NAME_LENGTH,
} from "../../src/lib/studentImport.ts";

const ORIGIN = "https://codeowl.example";
const BASE = "/codeowl/";

/**
 * Every row of an accepted file becomes a real student account that cannot be
 * un-created in one step, so what the parser lets through matters more than
 * most parsing does.
 */
describe("parsing a roster", () => {
    it("reads two columns of names", () => {
        const result = parseStudentCsv("Anna,Müller\nBen,Weber\n");
        expect(result.error).toBeUndefined();
        expect(result.students).toEqual([
            { firstName: "Anna", lastName: "Müller" },
            { firstName: "Ben", lastName: "Weber" },
        ]);
    });

    it("accepts the semicolons a German spreadsheet writes", () => {
        // Excel with a German locale exports these, and it does not say so.
        const result = parseStudentCsv("Anna;Müller\nBen;Weber");
        expect(result.students).toHaveLength(2);
        expect(result.students[0]).toEqual({ firstName: "Anna", lastName: "Müller" });
    });

    it("accepts tab-separated files too", () => {
        const result = parseStudentCsv("Anna\tMüller\nBen\tWeber");
        expect(result.students).toHaveLength(2);
    });

    it("survives Excel's byte order mark", () => {
        // Left in, it becomes part of the first student's given name and the
        // account is created under a name nobody typed.
        const result = parseStudentCsv("\uFEFFAnna,Müller\n");
        expect(result.students).toEqual([{ firstName: "Anna", lastName: "Müller" }]);
    });

    it("handles CRLF line endings", () => {
        const result = parseStudentCsv("Anna,Müller\r\nBen,Weber\r\n");
        expect(result.students).toHaveLength(2);
        expect(result.students[1].lastName).toBe("Weber");
    });

    it("skips a heading row when both cells name columns", () => {
        expect(parseStudentCsv("firstname,lastname\nAnna,Müller").students).toEqual([
            { firstName: "Anna", lastName: "Müller" },
        ]);
        expect(parseStudentCsv("Vorname;Nachname\nAnna;Müller").students).toEqual([
            { firstName: "Anna", lastName: "Müller" },
        ]);
    });

    it("keeps a row where only one cell looks like a heading", () => {
        // "Vorname" is a plausible enough given name, and a student really can
        // be surnamed "First"; dropping either row would silently lose a
        // student, which is worse than importing a stray heading the teacher
        // can see in the preview.
        const oneSided = parseStudentCsv("Vorname,Müller\nBen,Weber");
        expect(oneSided.students).toHaveLength(2);
        expect(oneSided.students[0]).toEqual({ firstName: "Vorname", lastName: "Müller" });

        const swapped = parseStudentCsv("Anna,First\nBen,Weber");
        expect(swapped.students).toHaveLength(2);
        expect(swapped.students[0]).toEqual({ firstName: "Anna", lastName: "First" });
    });

    it("ignores blank lines and trailing delimiters", () => {
        const result = parseStudentCsv("Anna,Müller,\n\nBen,Weber\n\n");
        expect(result.students).toEqual([
            { firstName: "Anna", lastName: "Müller" },
            { firstName: "Ben", lastName: "Weber" },
        ]);
    });

    it("trims the spaces a spreadsheet leaves behind", () => {
        const result = parseStudentCsv(" Anna , Müller ");
        expect(result.students).toEqual([{ firstName: "Anna", lastName: "Müller" }]);
    });

    it("reads quoted fields, delimiters and all", () => {
        const result = parseStudentCsv('"Müller, Anna","von der Weber"\n');
        expect(result.students).toEqual([{ firstName: "Müller, Anna", lastName: "von der Weber" }]);
    });

    it("unescapes doubled quotes inside a quoted field", () => {
        const result = parseStudentCsv('"Anna ""Ani""",Müller');
        expect(result.students).toEqual([{ firstName: 'Anna "Ani"', lastName: "Müller" }]);
    });

    it("rejects a row with the wrong number of columns, naming the line", () => {
        const result = parseStudentCsv("Anna,Müller\nBen,Weber,7a\n");
        expect(result.students).toEqual([]);
        expect(result.error?.key).toBe("csvImport.errorColumns");
        expect(result.error?.params).toEqual({ row: 2, count: 3 });
    });

    it("counts blank and heading lines when numbering a bad row", () => {
        // The number is only useful if it matches what the teacher's editor shows.
        const result = parseStudentCsv("firstname,lastname\nAnna,Müller\n\nBen\n");
        expect(result.error?.params?.row).toBe(4);
    });

    it("rejects a row missing one of the two names", () => {
        const result = parseStudentCsv("Anna,Müller\n,Weber");
        expect(result.error?.key).toBe("csvImport.errorMissingName");
        expect(result.error?.params).toEqual({ row: 2 });
    });

    it("rejects a name longer than the column can hold", () => {
        // varchar(100) — letting it through would surface as a 500 from Postgres.
        const result = parseStudentCsv(`Anna,${"M".repeat(MAX_NAME_LENGTH + 1)}`);
        expect(result.error?.key).toBe("csvImport.errorNameTooLong");
        expect(result.error?.params).toEqual({ row: 1, max: MAX_NAME_LENGTH });
    });

    it("accepts a name of exactly the maximum length", () => {
        const result = parseStudentCsv(`Anna,${"M".repeat(MAX_NAME_LENGTH)}`);
        expect(result.error).toBeUndefined();
    });

    it("rejects an empty file", () => {
        expect(parseStudentCsv("").error?.key).toBe("csvImport.errorEmpty");
        expect(parseStudentCsv("\n\n  \n").error?.key).toBe("csvImport.errorEmpty");
        expect(parseStudentCsv("firstname,lastname\n").error?.key).toBe("csvImport.errorEmpty");
    });

    it("refuses a file too large to be a class", () => {
        // The wrong file — a whole-school export — would otherwise create
        // hundreds of accounts before anyone noticed.
        const rows = Array.from({ length: MAX_IMPORT_ROWS + 1 }, (_, i) => `Anna${i},Müller`).join("\n");
        const result = parseStudentCsv(rows);
        expect(result.students).toEqual([]);
        expect(result.error?.key).toBe("csvImport.errorTooMany");
        expect(result.error?.params).toEqual({ count: MAX_IMPORT_ROWS + 1, max: MAX_IMPORT_ROWS });
    });

    it("accepts a file of exactly the maximum size", () => {
        const rows = Array.from({ length: MAX_IMPORT_ROWS }, (_, i) => `Anna${i},Müller`).join("\n");
        expect(parseStudentCsv(rows).error).toBeUndefined();
    });
});

describe("delimiter detection", () => {
    it("picks whichever separator actually appears", () => {
        expect(detectDelimiter("a,b\nc,d")).toBe(",");
        expect(detectDelimiter("a;b\nc;d")).toBe(";");
        expect(detectDelimiter("a\tb\nc\td")).toBe("\t");
    });

    it("is not fooled by a separator inside a quoted name", () => {
        // Two real semicolon columns beat the one comma inside the quotes.
        expect(detectDelimiter('"Müller, Anna";Weber\n"Schmidt, Ben";Klein')).toBe(";");
    });

    it("falls back to a comma when there is nothing to go on", () => {
        expect(detectDelimiter("Anna")).toBe(",");
    });
});

describe("invite links", () => {
    it("builds the onboarding link the teacher copies", () => {
        expect(studentInviteUrl("abc123", ORIGIN, BASE)).toBe(
            "https://codeowl.example/codeowl/student-onboarding?token=abc123",
        );
    });

    it("writes a header and one row per student", () => {
        const csv = buildInviteCsv(
            [{ firstName: "Anna", lastName: "Müller", inviteToken: "tok1" }],
            { headers: ["First name", "Last name", "Invite link"], origin: ORIGIN, basePath: BASE },
        );
        const lines = csv.replace(/^\uFEFF/, "").trimEnd().split("\r\n");
        expect(lines[0]).toBe("First name,Last name,Invite link");
        expect(lines[1]).toBe("Anna,Müller,https://codeowl.example/codeowl/student-onboarding?token=tok1");
    });

    it("carries a byte order mark and CRLF endings for Excel", () => {
        // Without them a Windows spreadsheet renders every umlaut as rubble.
        const csv = buildInviteCsv([{ firstName: "Anna", lastName: "Müller", inviteToken: "t" }], {
            headers: ["a", "b", "c"],
            origin: ORIGIN,
            basePath: BASE,
        });
        expect(csv.startsWith("\uFEFF")).toBe(true);
        expect(csv).toContain("\r\n");
    });

    it("uses the delimiter it is given", () => {
        const csv = buildInviteCsv([{ firstName: "Anna", lastName: "Müller", inviteToken: "t" }], {
            headers: ["a", "b", "c"],
            delimiter: ";",
            origin: ORIGIN,
            basePath: BASE,
        });
        expect(csv).toContain("Anna;Müller;");
    });

    it("quotes a name that would otherwise split the row", () => {
        const csv = buildInviteCsv([{ firstName: "Anna, geb. Klein", lastName: "Müller", inviteToken: "t" }], {
            headers: ["a", "b", "c"],
            origin: ORIGIN,
            basePath: BASE,
        });
        expect(csv).toContain('"Anna, geb. Klein",Müller,');
    });

    it("leaves the link empty for a student without a token", () => {
        // Better an obviously blank cell than a link to nowhere.
        const csv = buildInviteCsv([{ firstName: "Anna", lastName: "Müller", inviteToken: null }], {
            headers: ["a", "b", "c"],
            origin: ORIGIN,
            basePath: BASE,
        });
        expect(csv.trimEnd().endsWith("Anna,Müller,")).toBe(true);
    });

    it("separates with semicolons for German and commas otherwise", () => {
        expect(csvDelimiterForLocale("de")).toBe(";");
        expect(csvDelimiterForLocale("de-DE")).toBe(";");
        expect(csvDelimiterForLocale("en")).toBe(",");
        expect(csvDelimiterForLocale(undefined)).toBe(",");
    });

    it("names the file after the class", () => {
        expect(inviteCsvFilename("Klasse 7a")).toBe("invite-links-Klasse-7a.csv");
        expect(inviteCsvFilename("  ")).toBe("invite-links.csv");
        expect(inviteCsvFilename("7a / Info")).toBe("invite-links-7a-Info.csv");
    });
});

describe("the roster template", () => {
    it("round-trips through the parser it is an example for", () => {
        // A template the importer would reject is worse than none at all.
        const template = buildTemplateCsv(["Vorname", "Nachname"], ";");
        const result = parseStudentCsv(template);
        expect(result.error).toBeUndefined();
        expect(result.students).toEqual([
            { firstName: "Anna", lastName: "Müller" },
            { firstName: "Ben", lastName: "Weber" },
        ]);
    });

    it("round-trips with English headings and commas too", () => {
        const result = parseStudentCsv(buildTemplateCsv(["firstname", "lastname"], ","));
        expect(result.students).toHaveLength(2);
    });
});
