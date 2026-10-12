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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.application.query.WorksheetSearchQuery;
import de.uni_passau.fim.se2.application.service.WorksheetSearchService.WhereClause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The filter the marketplace applies, checked without a database.
 *
 * <p>Whether the SQL matches the right rows is {@code WorksheetSearchIT}'s job — that needs a
 * real Postgres and cannot be faked. What this class covers is the part a database would not
 * tell you anyway: that the clause is <em>built</em> correctly. In particular that the
 * visibility filter is unconditional, that no caller-supplied text ever reaches the SQL string,
 * and that "only mine" without a user narrows rather than widens.
 */
class WorksheetSearchServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private static WhereClause where(String q, String lang, boolean onlyMine, UUID user) {
        return WorksheetSearchService.buildWhere(
                WorksheetSearchQuery.of(q, lang, onlyMine, null, 0, 0), user);
    }

    @Nested
    @DisplayName("what is always true")
    class Invariants {

        @Test
        @DisplayName("only PUBLIC worksheets are ever listed, whatever the filters")
        void visibilityFilterIsUnconditional() {
            // The endpoint is safe to serve anonymously only because of this clause, so it must
            // survive every combination of the optional ones.
            assertAll(
                    () -> assertTrue(where(null, null, false, null).sql().contains("w.visibility = ?")),
                    () -> assertTrue(where("rekursion", "java", true, USER).sql().contains("w.visibility = ?")),
                    () -> assertTrue(where(null, null, false, null).params().contains("PUBLIC")));
        }

        @Test
        @DisplayName("the Library Inbox is excluded from every query")
        void inboxIsAlwaysExcluded() {
            WhereClause clause = where(null, null, false, USER);
            assertAll(
                    () -> assertTrue(clause.sql().contains("NOT LIKE ?")),
                    () -> assertTrue(clause.params().stream().anyMatch(
                            p -> String.valueOf(p).contains(WorksheetService.LIBRARY_INBOX_SENTINEL))));
        }
    }

    @Nested
    @DisplayName("injection safety")
    class InjectionSafety {

        @Test
        @DisplayName("nothing the caller typed appears in the SQL string")
        void userInputIsAlwaysBound() {
            String nasty = "'; DROP TABLE worksheets; --";
            WhereClause clause = where(nasty, nasty, true, USER);

            assertAll(
                    () -> assertFalse(clause.sql().contains("DROP TABLE"),
                            "the query text must reach the database as a parameter, never as SQL"),
                    () -> assertFalse(clause.sql().contains(nasty)),
                    () -> assertTrue(clause.params().contains(nasty),
                            "and it must actually be bound, or the filter silently does nothing"));
        }

        @Test
        @DisplayName("every placeholder has exactly one parameter")
        void placeholdersAndParametersAgree() {
            // A mismatch here is the classic way a dynamic query breaks: the SQL is valid, the
            // parameters are valid, and they line up one position out.
            for (WhereClause clause : new WhereClause[]{
                    where(null, null, false, null),
                    where("prüfung", null, false, USER),
                    where(null, "java", false, USER),
                    where("prüfung", "java", true, USER)}) {
                long placeholders = clause.sql().chars().filter(c -> c == '?').count();
                assertEquals(placeholders, clause.params().size(),
                        "placeholder/parameter mismatch for: " + clause.sql());
            }
        }

        @Test
        @DisplayName("the generated clause has balanced parentheses")
        void parenthesesBalance() {
            // Written after the free-text branch shipped one closing paren short. Postgres said
            // "syntax error at end of input", which the database tests caught and every mock
            // test in the world would have missed — the clause is assembled from four nested
            // fragments, and counting them by eye is exactly the kind of thing to automate.
            for (WhereClause clause : new WhereClause[]{
                    where(null, null, false, null),
                    where("prüfung", null, false, USER),
                    where(null, "java", false, USER),
                    where("prüfung", "java", true, USER)}) {
                int depth = 0;
                for (char c : clause.sql().toCharArray()) {
                    if (c == '(') depth++;
                    if (c == ')') depth--;
                    assertTrue(depth >= 0, "closed a parenthesis that was never opened: " + clause.sql());
                }
                assertEquals(0, depth, "unbalanced parentheses in: " + clause.sql());
            }
        }

        @Test
        @DisplayName("a free-text query binds the prefix variant the shared helper needs")
        void freeTextBindsBothVariants() {
            WhereClause clause = where("prüfung", null, false, USER);

            // Three tsquery expressions (worksheet, sequence, task), two parameters each —
            // umlaut folding now happens in SQL via search_normalize, applied identically to
            // both the query and the generated search_vector columns, so the query itself is
            // bound as typed rather than pre-folded in Java.
            assertEquals(6, clause.params().stream().filter("prüfung"::equals).count());
        }
    }

    @Nested
    @DisplayName("optional filters")
    class OptionalFilters {

        @Test
        @DisplayName("no filters means no extra clauses")
        void emptyQueryStaysMinimal() {
            WhereClause clause = where(null, null, false, USER);
            assertAll(
                    () -> assertFalse(clause.sql().contains("programming_language")),
                    () -> assertFalse(clause.sql().contains("search_vector")),
                    () -> assertFalse(clause.sql().contains("owner_id")));
        }

        @Test
        @DisplayName("a language filter reaches the worksheet's sequences")
        void languageFilterJoinsSequences() {
            WhereClause clause = where(null, "java", false, USER);
            assertAll(
                    () -> assertTrue(clause.sql().contains("programming_language = ?")),
                    () -> assertTrue(clause.params().contains("java")));
        }

        @Test
        @DisplayName("the language facet query drops the language filter, so the sidebar shows alternatives")
        void facetQueryIgnoresItsOwnFilter() {
            WhereClause facet = WorksheetSearchService.buildWhere(
                    WorksheetSearchQuery.of(null, "java", false, null, 0, 0), USER, false);

            assertFalse(facet.sql().contains("programming_language = ?"),
                    "counting only the language already picked would make the sidebar useless");
        }

        @Test
        @DisplayName("free text searches the worksheet and everything inside it")
        void freeTextReachesSequencesAndTasks() {
            String sql = where("rekursion", null, false, USER).sql();
            assertAll(
                    () -> assertTrue(sql.contains("w.search_vector"), "the worksheet's own words"),
                    () -> assertTrue(sql.contains("s.search_vector"), "its sequences"),
                    () -> assertTrue(sql.contains("t.search_vector"), "and their tasks"));
        }
    }

    @Nested
    @DisplayName("only mine")
    class OnlyMine {

        @Test
        @DisplayName("narrows to the caller when there is one")
        void narrowsToTheCaller() {
            WhereClause clause = where(null, null, true, USER);
            assertAll(
                    () -> assertTrue(clause.sql().contains("w.owner_id = ?")),
                    () -> assertTrue(clause.params().contains(USER)));
        }

        @Test
        @DisplayName("returns nothing rather than everything when nobody is logged in")
        void anonymousGetsNothing() {
            WhereClause clause = where(null, null, true, null);
            assertAll(
                    () -> assertTrue(clause.sql().contains("1=0"),
                            "\"only mine\" with no known user must not quietly mean \"everyone\""),
                    () -> assertFalse(clause.sql().contains("w.owner_id = ?")));
        }
    }
}
