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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HintLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"en", "EN", "en-US", "en-GB"})
    @DisplayName("any English tag selects English")
    void englishTags(String tag) {
        assertEquals(HintLocale.EN, HintLocale.from(tag));
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "DE", "de-DE", "de-AT"})
    @DisplayName("any German tag selects German")
    void germanTags(String tag) {
        assertEquals(HintLocale.DE, HintLocale.from(tag));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "fr", "nonsense"})
    @DisplayName("anything unrecognised falls back to German")
    void fallsBackToGerman(String tag) {
        // Hints were German-only before the locale was threaded through, so a
        // client that sends nothing has to keep getting German rather than
        // silently switching language.
        assertEquals(HintLocale.DE, HintLocale.from(tag));
    }

    @Test
    @DisplayName("prompt name is the language's own name")
    void promptNames() {
        assertEquals("English", HintLocale.EN.promptName());
        assertEquals("Deutsch", HintLocale.DE.promptName());
    }
}
