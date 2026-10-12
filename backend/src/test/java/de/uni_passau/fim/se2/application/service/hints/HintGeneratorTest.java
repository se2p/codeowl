/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.application.service.hints;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level-1 hint text.
 *
 * Two things went wrong here and both are worth holding onto: the text was
 * German whatever the UI language, and the German was written with ASCII
 * transliterations (Bloecke, Loesung) rather than umlauts.
 */
class HintGeneratorTest {

    private final HintGenerator generator = new HintGenerator();

    /** A static-analysis finding pinned to one block of the solution. */
    private static BlockMapper.BlockFinding findingOn(int blockIndex) {
        return new BlockMapper.BlockFinding(blockIndex,
                new AnalysisFinding("F821", "Undefined name 'x'", "error",
                        blockIndex + 1, blockIndex + 1, "ruff", "undefined_variable"));
    }

    /** No findings, no distractors, nothing missing — just the requested locale. */
    private String hint(HintLocale locale) {
        return generator.generate(List.of(), "python", List.of(), false, 0, 0, locale);
    }

    @Nested
    @DisplayName("language selection")
    class LanguageSelection {

        @Test
        @DisplayName("the same situation reads differently in each language")
        void differsByLocale() {
            assertNotEquals(hint(HintLocale.DE), hint(HintLocale.EN));
        }

        @Test
        @DisplayName("English is used when English is asked for")
        void english() {
            String text = generator.generate(List.of(), "python", List.of(), true, 2, 5, HintLocale.EN);
            assertAll(
                    () -> assertTrue(text.contains("Only 2 of 5"), text),
                    () -> assertFalse(text.contains("Es wurden nur"), text));
        }

        @Test
        @DisplayName("the overloads without a locale stay German")
        void legacyOverloadsDefaultToGerman() {
            // Older callers must not have their language changed underneath them.
            assertEquals(hint(HintLocale.DE), generator.generate(List.of(), "python"));
            assertEquals(hint(HintLocale.DE), generator.generate(List.of(), "python", List.of()));
        }
    }

    @Nested
    @DisplayName("German spelling")
    class GermanSpelling {

        @Test
        @DisplayName("too-few-blocks uses umlauts")
        void tooFewBlocks() {
            String text = generator.generate(List.of(), "python", List.of(), true, 2, 5, HintLocale.DE);
            assertAll(
                    () -> assertTrue(text.contains("benötigten"), text),
                    () -> assertTrue(text.contains("Blöcken"), text),
                    () -> assertTrue(text.contains("Lösungsbereich"), text),
                    () -> assertFalse(text.contains("benoetigten"), text),
                    () -> assertFalse(text.contains("Bloecken"), text),
                    () -> assertFalse(text.contains("Loesungsbereich"), text));
        }

        @Test
        @DisplayName("distractor wording uses umlauts, singular and plural")
        void distractors() {
            String one = generator.generate(List.of(), "python", List.of(2), false, 0, 0, HintLocale.DE);
            String many = generator.generate(List.of(), "python", List.of(1, 3), false, 0, 0, HintLocale.DE);
            assertAll(
                    () -> assertTrue(one.contains("gehört nicht zur Lösung"), one),
                    () -> assertTrue(one.contains("zurück"), one),
                    () -> assertTrue(many.contains("Blöcke"), many),
                    () -> assertTrue(many.contains("gehören"), many),
                    () -> assertFalse(many.contains("gehoeren"), many));
        }

        @Test
        @DisplayName("the no-findings fallback uses umlauts")
        void fallback() {
            String text = hint(HintLocale.DE);
            assertAll(
                    () -> assertTrue(text.contains("Prüfe"), text),
                    () -> assertTrue(text.contains("Blöcke"), text),
                    () -> assertFalse(text.contains("Pruefe"), text));
        }

        @Test
        @DisplayName("no German output contains an ASCII transliteration")
        void noTransliterationsAnywhere() {
            String all = String.join(" ",
                    generator.generate(List.of(), "python", List.of(), true, 2, 5, HintLocale.DE),
                    generator.generate(List.of(), "python", List.of(2), false, 0, 0, HintLocale.DE),
                    generator.generate(List.of(), "python", List.of(1, 3), false, 0, 0, HintLocale.DE),
                    hint(HintLocale.DE));
            for (String bad : List.of("Bloeck", "bloeck", "Loesung", "loesung",
                                      "benoetig", "zurueck", "gehoer", "Pruefe")) {
                assertFalse(all.contains(bad), "transliteration '" + bad + "' in: " + all);
            }
        }
    }

    @Nested
    @DisplayName("content")
    class Content {

        @Test
        @DisplayName("blocks are named by the same letter the UI badges them with")
        void namesBlocksByLetter() {
            // Index 2 is the third block, badged "C" in the solution area. Saying
            // "Block 3" here left the student matching a number against a letter.
            assertTrue(generator.generate(List.of(), "python", List.of(2), false, 0, 0, HintLocale.EN)
                    .contains("Block C"));
            assertTrue(generator.generate(List.of(), "python", List.of(1, 3), false, 0, 0, HintLocale.EN)
                    .contains("Blocks B, D"));
        }

        @Test
        @DisplayName("no hint refers to a block by number")
        void neverUsesNumbers() {
            String text = generator.generate(List.of(), "python", List.of(0, 1, 2), false, 0, 0, HintLocale.EN);
            for (int n = 1; n <= 5; n++) {
                assertFalse(text.contains("Block " + n), "numeric block reference in: " + text);
            }
        }

        @Test
        @DisplayName("a finding names its block by letter too")
        void findingsUseLetters() {
            // The per-finding line is a separate branch from the distractor one
            // and was the half that kept saying "Block 5".
            String text = generator.generate(List.of(findingOn(4)), "python", List.of(),
                    false, 0, 0, HintLocale.EN);
            assertAll(
                    () -> assertTrue(text.contains("Block E"), text),
                    () -> assertFalse(text.contains("Block 5"), text),
                    () -> assertTrue(text.contains("F821"), text));
        }

        @Test
        @DisplayName("the fallback appears only when there is nothing else to say")
        void fallbackOnlyWhenEmpty() {
            String withIssue = generator.generate(List.of(), "python", List.of(2), false, 0, 0, HintLocale.EN);
            assertFalse(withIssue.contains("No static errors found"), withIssue);
            assertTrue(hint(HintLocale.EN).contains("No static errors found"));
        }
    }
}
