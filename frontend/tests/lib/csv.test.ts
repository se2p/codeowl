import { describe, it, expect } from "vitest";
import { decodeCsvBytes } from "../../src/lib/csv.ts";

/**
 * Teachers upload whatever their spreadsheet saved. German Excel's default CSV is
 * Windows-1252, and reading that as UTF-8 turned every umlaut into "�".
 */
describe("decoding an uploaded CSV", () => {
    it("reads UTF-8", () => {
        const bytes = new TextEncoder().encode("Vorname;Nachname\nJürgen;Müßig\n");
        expect(decodeCsvBytes(bytes)).toBe("Vorname;Nachname\nJürgen;Müßig\n");
    });

    it("drops a UTF-8 byte order mark, as Excel's \"CSV UTF-8\" writes one", () => {
        const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode("Zoë;Öztürk")]);
        expect(decodeCsvBytes(bytes)).toBe("Zoë;Öztürk");
    });

    it("falls back to Windows-1252 for Excel's default CSV", () => {
        // "Jürgen;Müßig" in Windows-1252: ü = FC, ß = DF
        const bytes = new Uint8Array([
            0x4a, 0xfc, 0x72, 0x67, 0x65, 0x6e, 0x3b, 0x4d, 0xfc, 0xdf, 0x69, 0x67,
        ]);
        expect(decodeCsvBytes(bytes)).toBe("Jürgen;Müßig");
    });

    it("reads capital umlauts and the euro sign from Windows-1252", () => {
        // Ä = C4, Ö = D6, Ü = DC, € = 80 (where Windows-1252 differs from Latin-1)
        expect(decodeCsvBytes(new Uint8Array([0xc4, 0xd6, 0xdc, 0x80]))).toBe("ÄÖÜ€");
    });

    it("leaves plain ASCII alone", () => {
        expect(decodeCsvBytes(new TextEncoder().encode("Vorname;Nachname\nMax;Muster"))).toBe("Vorname;Nachname\nMax;Muster");
    });
});
