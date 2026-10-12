import { describe, it, expect } from "vitest";
import {
    parseFimUsernameCsv,
    normalizeFimUsername,
    isValidFimUsername,
} from "../../src/lib/fimStudentImport.ts";
import { MAX_IMPORT_ROWS } from "../../src/lib/studentImport.ts";

/**
 * Every accepted username becomes an account in the class, created up front and
 * not checked against the FIM directory, so what the parser lets through is what
 * the teacher gets.
 */
describe("parsing a CSV of FIM usernames", () => {
    it("reads one username per row", () => {
        const result = parseFimUsernameCsv("mustermann\nmusterfrau\n");
        expect(result.error).toBeUndefined();
        expect(result.usernames).toEqual(["mustermann", "musterfrau"]);
    });

    it("lowercases and trims, the way the backend stores uids", () => {
        expect(parseFimUsernameCsv("  MusterMann \r\nMUSTERFRAU").usernames).toEqual(["mustermann", "musterfrau"]);
    });

    it("skips a heading row", () => {
        expect(parseFimUsernameCsv("FIM-Benutzername\nmustermann").usernames).toEqual(["mustermann"]);
        expect(parseFimUsernameCsv("Username;\nmustermann;").usernames).toEqual(["mustermann"]);
    });

    it("only treats the first row as a heading", () => {
        // "login" is a heading word, but a valid username further down.
        expect(parseFimUsernameCsv("mustermann\nlogin").usernames).toEqual(["mustermann", "login"]);
    });

    it("ignores blank lines, trailing delimiters and Excel's byte order mark", () => {
        const result = parseFimUsernameCsv("﻿mustermann;\n\n;\nmusterfrau\n\n");
        expect(result.usernames).toEqual(["mustermann", "musterfrau"]);
    });

    it("lists a username given twice only once", () => {
        expect(parseFimUsernameCsv("mustermann\nMustermann\nmusterfrau").usernames).toEqual([
            "mustermann",
            "musterfrau",
        ]);
    });

    it("refuses a roster with names beside the usernames rather than guess the column", () => {
        const result = parseFimUsernameCsv("Anna;Müller;amueller\nBen;Weber;bweber");
        expect(result.usernames).toEqual([]);
        expect(result.error).toEqual({ key: "fimStudents.errorColumns", params: { row: 1, count: 3 } });
    });

    it("names the line of an email address or a name in the file", () => {
        const result = parseFimUsernameCsv("mustermann\n\nmax.mustermann@uni-passau.de");
        expect(result.usernames).toEqual([]);
        expect(result.error).toEqual({
            key: "fimStudents.errorInvalidRow",
            params: { row: 3, value: "max.mustermann@uni-passau.de" },
        });
        expect(parseFimUsernameCsv("Max Mustermann").error?.key).toBe("fimStudents.errorInvalidRow");
    });

    it("refuses an empty file and one with only a heading", () => {
        expect(parseFimUsernameCsv("").error?.key).toBe("fimStudents.errorEmpty");
        expect(parseFimUsernameCsv("username\n").error?.key).toBe("fimStudents.errorEmpty");
    });

    it("refuses more usernames than the backend accepts at once", () => {
        const text = Array.from({ length: MAX_IMPORT_ROWS + 1 }, (_, i) => `student${i}`).join("\n");
        expect(parseFimUsernameCsv(text).error).toEqual({
            key: "fimStudents.errorTooMany",
            params: { count: MAX_IMPORT_ROWS + 1, max: MAX_IMPORT_ROWS },
        });
    });
});

describe("a single FIM username", () => {
    it("accepts what FIM usernames look like", () => {
        for (const u of ["mustermann", "muster01", "m.mustermann", "muster-mann", "muster_mann"]) {
            expect(isValidFimUsername(normalizeFimUsername(u))).toBe(true);
        }
    });

    it("refuses what is not one", () => {
        for (const u of ["", "  ", "Max Mustermann", "max@uni-passau.de", "-muster", "a".repeat(65)]) {
            expect(isValidFimUsername(normalizeFimUsername(u))).toBe(false);
        }
    });
});
