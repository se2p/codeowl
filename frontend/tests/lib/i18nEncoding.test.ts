import { describe, expect, it } from "vitest";
import de from "../../src/i18n/de.json";
import en from "../../src/i18n/en.json";

/**
 * The translation bundles must not contain double-encoded text.
 *
 * <p>Written after "Niemand sonst kann das Arbeitsblatt öffnen" shipped as "Arbeitsblatt
 * Ã¶ffnen". The cause was a tool that read the file as UTF-8 and wrote it back as Latin-1, so
 * every umlaut in the touched lines gained a second encoding pass. Nothing caught it: the JSON
 * stayed valid, TypeScript was happy, and every test that reads these keys mocks i18n and
 * asserts against key names rather than against the German.
 *
 * <p>The markers below are what mis-decoded UTF-8 always produces — the lead bytes of a
 * two-byte sequence rendered as Latin-1. No German or English string has a legitimate reason
 * to contain them, so their absence is a cheap and exact check.
 */
const MOJIBAKE = /[ÃÂâ]/;

function flatten(value: unknown, path = ""): [string, string][] {
    if (typeof value === "string") return [[path, value]];
    if (value && typeof value === "object") {
        return Object.entries(value as Record<string, unknown>)
            .flatMap(([key, child]) => flatten(child, path ? `${path}.${key}` : key));
    }
    return [];
}

describe.each([["de", de], ["en", en]])("%s.json", (_name, bundle) => {
    it("contains no double-encoded characters", () => {
        const broken = flatten(bundle)
            .filter(([, text]) => MOJIBAKE.test(text))
            .map(([key, text]) => `${key}: ${text}`);

        expect(broken).toEqual([]);
    });

    it("is not empty, so the check above cannot pass by reading nothing", () => {
        // A bundle that failed to import would make the assertion above trivially true.
        expect(flatten(bundle).length).toBeGreaterThan(100);
    });
});

describe("both bundles", () => {
    it("define the same keys, so a language is never missing a string", () => {
        // Not strictly an encoding matter, but the same edit that broke the encoding is the
        // kind that drops a key from one file — and a missing key renders as its own name.
        const keysOf = (b: unknown) => flatten(b).map(([k]) => k).sort();

        expect(keysOf(de)).toEqual(keysOf(en));
    });
});
