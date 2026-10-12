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
package de.uni_passau.fim.se2.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The guard that "Pyhton" was able to walk past.
 *
 * <p>The behaviour worth pinning is not that "python" resolves -- it is that a near miss does
 * not. Anything that tries to be helpful about a misspelling reopens the hole this type was
 * added to close, because a wrong guess is indistinguishable from correct data once it reaches
 * the column.
 */
@DisplayName("supported programming languages")
class SupportedLanguageTest {

    @Nested
    @DisplayName("resolving a value")
    class Resolving {

        @ParameterizedTest
        @ValueSource(strings = {"python", "Python", "PYTHON", "  python  "})
        @DisplayName("case and surrounding whitespace do not matter")
        void acceptsCaseAndWhitespaceVariants(String raw) {
            assertEquals(Optional.of(SupportedLanguage.PYTHON), SupportedLanguage.fromString(raw));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Pyhton", "pyhton", "PYHTON", "jvaa", "pyton", "python3"})
        @DisplayName("a near miss resolves to nothing rather than being guessed at")
        void rejectsNearMisses(String raw) {
            assertEquals(Optional.empty(), SupportedLanguage.fromString(raw),
                    "'" + raw + "' was guessed at; that is how the typo reached the database");
        }

        @ParameterizedTest
        @ValueSource(strings = {"c++", "ruby", "javascript", "other", ""})
        @DisplayName("an unsupported language resolves to nothing")
        void rejectsUnsupportedLanguages(String raw) {
            assertEquals(Optional.empty(), SupportedLanguage.fromString(raw));
        }

        @Test
        @DisplayName("null is not a language")
        void rejectsNull() {
            assertEquals(Optional.empty(), SupportedLanguage.fromString(null));
        }

        @Test
        @DisplayName("matches() agrees with fromString()")
        void matchesAgreesWithFromString() {
            assertAll(
                    () -> assertTrue(SupportedLanguage.JAVA.matches("Java")),
                    () -> assertFalse(SupportedLanguage.JAVA.matches("Pyhton")),
                    () -> assertFalse(SupportedLanguage.PYTHON.matches("Pyhton")),
                    () -> assertFalse(SupportedLanguage.PYTHON.matches(null)));
        }
    }

    @Nested
    @DisplayName("the detection schema")
    class DetectionSchema {

        @Test
        @DisplayName("offers every supported language plus a way to say neither")
        void offersEverySupportedLanguageAndAnEscape() {
            assertEquals(List.of("python", "java", "other"), SupportedLanguage.detectionChoices());
        }

        @Test
        @DisplayName("canonical names are what the column stores")
        void canonicalNamesAreLowercase() {
            assertEquals(List.of("python", "java"), SupportedLanguage.canonicalNames());
        }

        @Test
        @DisplayName("'other' is not itself a supported language")
        void unsupportedSentinelIsNotALanguage() {
            assertEquals(Optional.empty(),
                    SupportedLanguage.fromString(SupportedLanguage.UNSUPPORTED));
        }
    }
}
