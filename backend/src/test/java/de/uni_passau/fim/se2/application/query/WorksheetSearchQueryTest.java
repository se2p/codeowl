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
package de.uni_passau.fim.se2.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Making a request safe before any SQL is built from it.
 *
 * <p>Everything downstream assumes the sort is one of three known strings and the paging numbers
 * are sane. This is where that becomes true, so it is where it is worth checking — including the
 * inputs nobody sends deliberately: a negative page, a size of ten thousand, a sort value left
 * over from an older client.
 */
class WorksheetSearchQueryTest {

    @Nested
    @DisplayName("free text")
    class FreeText {

        @Test
        @DisplayName("blank input is the same as no input")
        void blankBecomesNull() {
            assertAll(
                    () -> assertNull(WorksheetSearchQuery.of(null, null, false, null, 0, 0).q()),
                    () -> assertNull(WorksheetSearchQuery.of("", null, false, null, 0, 0).q()),
                    () -> assertNull(WorksheetSearchQuery.of("   ", null, false, null, 0, 0).q()));
        }

        @Test
        @DisplayName("surrounding whitespace is dropped, inner text is left alone")
        void trimsWithoutMangling() {
            assertEquals("binäre suche",
                    WorksheetSearchQuery.of("  binäre suche  ", null, false, null, 0, 0).q());
        }

        @Test
        @DisplayName("hasText answers the question the SQL branches on")
        void hasTextMatchesTheField() {
            assertAll(
                    () -> assertTrue(WorksheetSearchQuery.of("x", null, false, null, 0, 0).hasText()),
                    () -> assertFalse(WorksheetSearchQuery.of("  ", null, false, null, 0, 0).hasText()));
        }

        @Test
        @DisplayName("a language filter is normalised the same way")
        void languageIsNormalised() {
            assertAll(
                    () -> assertEquals("java",
                            WorksheetSearchQuery.of(null, " java ", false, null, 0, 0).lang()),
                    () -> assertNull(WorksheetSearchQuery.of(null, "  ", false, null, 0, 0).lang()));
        }
    }

    @Nested
    @DisplayName("sort")
    class Sort {

        @Test
        @DisplayName("the three known values survive, in any casing")
        void knownValuesSurvive() {
            assertAll(
                    () -> assertEquals(WorksheetSearchQuery.SORT_POPULARITY,
                            WorksheetSearchQuery.of(null, null, false, "popularity", 0, 0).sort()),
                    () -> assertEquals(WorksheetSearchQuery.SORT_POPULARITY,
                            WorksheetSearchQuery.of(null, null, false, " POPULARITY ", 0, 0).sort()),
                    () -> assertEquals(WorksheetSearchQuery.SORT_RELEVANCE,
                            WorksheetSearchQuery.of("q", null, false, "Relevance", 0, 0).sort()));
        }

        @Test
        @DisplayName("anything else falls back to newest first rather than failing the search")
        void unknownFallsBack() {
            assertAll(
                    () -> assertEquals(WorksheetSearchQuery.SORT_RECENT,
                            WorksheetSearchQuery.of(null, null, false, "created_at; DROP TABLE", 0, 0).sort()),
                    () -> assertEquals(WorksheetSearchQuery.SORT_RECENT,
                            WorksheetSearchQuery.of(null, null, false, null, 0, 0).sort()),
                    () -> assertEquals(WorksheetSearchQuery.SORT_RECENT,
                            WorksheetSearchQuery.of(null, null, false, "", 0, 0).sort()));
        }

        @Test
        @DisplayName("relevance without a query means nothing, so it becomes newest first")
        void relevanceNeedsAQuery() {
            assertEquals(WorksheetSearchQuery.SORT_RECENT,
                    WorksheetSearchQuery.of(null, null, false, "relevance", 0, 0).sort(),
                    "a page cannot be sorted by relevance to nothing");
        }
    }

    @Nested
    @DisplayName("paging")
    class Paging {

        @Test
        @DisplayName("a missing size becomes the default, an excessive one is capped")
        void sizeIsClamped() {
            assertAll(
                    () -> assertEquals(WorksheetSearchQuery.DEFAULT_PAGE_SIZE,
                            WorksheetSearchQuery.of(null, null, false, null, 0, 0).size()),
                    () -> assertEquals(WorksheetSearchQuery.DEFAULT_PAGE_SIZE,
                            WorksheetSearchQuery.of(null, null, false, null, 0, -5).size()),
                    () -> assertEquals(WorksheetSearchQuery.MAX_PAGE_SIZE,
                            WorksheetSearchQuery.of(null, null, false, null, 0, 10_000).size()),
                    () -> assertEquals(7,
                            WorksheetSearchQuery.of(null, null, false, null, 0, 7).size()));
        }

        @Test
        @DisplayName("a negative page is the first page, not a negative offset")
        void pageIsNeverNegative() {
            // OFFSET -20 is a database error, so this is the difference between a clamped
            // request and a 500.
            assertAll(
                    () -> assertEquals(0, WorksheetSearchQuery.of(null, null, false, null, -3, 0).page()),
                    () -> assertEquals(0, WorksheetSearchQuery.of(null, null, false, null, -3, 0).offset()));
        }

        @Test
        @DisplayName("the offset follows from page and size")
        void offsetIsDerived() {
            assertEquals(60, WorksheetSearchQuery.of(null, null, false, null, 3, 20).offset());
        }
    }
}
