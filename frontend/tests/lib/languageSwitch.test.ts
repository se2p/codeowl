import { describe, it, expect, beforeEach } from "vitest";
import i18n from "i18next";

/**
 * The js-parsons widget is built once per task, in an effect keyed on the
 * task's solution. Anything it captures at that moment keeps answering in the
 * language that was active then, which is why the grader's messages stayed
 * English after switching to German until the page was reloaded.
 *
 * The fix is to resolve inside the callback via the i18n instance rather than
 * to bake in a `t` bound to one language. These tests pin that difference.
 */
beforeEach(async () => {
    if (!i18n.isInitialized) {
        await i18n.init({
            lng: "de",
            fallbackLng: "de",
            resources: {
                de: { translation: { solve: { grader_order: "Falsche Reihenfolge." } } },
                en: { translation: { solve: { grader_order: "Wrong order." } } },
            },
            interpolation: { escapeValue: false },
        });
    }
    await i18n.changeLanguage("de");
});

describe("grader message language", () => {
    it("follows a language switch when resolved through the instance", async () => {
        // How the widget overrides are wired now.
        const order = () => i18n.t("solve.grader_order");

        expect(order()).toBe("Falsche Reihenfolge.");
        await i18n.changeLanguage("en");
        expect(order()).toBe("Wrong order.");
    });

    it("gets stuck when a fixed t is captured instead", async () => {
        // The old shape: `t` from useTranslation, captured once by the effect.
        const capturedT = i18n.getFixedT("de");
        const order = () => capturedT("solve.grader_order");

        expect(order()).toBe("Falsche Reihenfolge.");
        await i18n.changeLanguage("en");
        // Still German — this is the bug the user hit.
        expect(order()).toBe("Falsche Reihenfolge.");
    });

    it("keeps interpolation working across the switch", async () => {
        i18n.addResource("de", "translation", "solve.no_matching", "Block {{line}} falsch eingerückt.");
        i18n.addResource("en", "translation", "solve.no_matching", "Block {{line}} is not indented correctly.");

        const noMatching = (line: number) => i18n.t("solve.no_matching", { line });

        expect(noMatching(3)).toBe("Block 3 falsch eingerückt.");
        await i18n.changeLanguage("en");
        expect(noMatching(3)).toBe("Block 3 is not indented correctly.");
    });
});
