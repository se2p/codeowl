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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-logic pieces of the prefix-matching free-text search: the fixed parameter order every
 * {@link SequenceSearchService#TSQUERY_EXPR} / {@link SequenceSearchService#HIGHLIGHT_TSQUERY_EXPR}
 * occurrence relies on.
 *
 * <p>Umlaut tolerance for <em>matching</em> is no longer Java-side logic to unit test here —
 * {@code search_normalize} (folding German umlauts to their digraph spelling, then stripping
 * every other language's accents via the {@code unaccent} extension) runs in Postgres, applied
 * identically to the generated {@code search_vector} columns and to the query text. See {@code
 * db/migration/V19__unaccent_search.sql} and {@code MarketplaceJourneyDatabaseTest} /
 * {@code WorksheetSearchDatabaseTest} for the behaviour that matters.
 *
 * <p><em>Highlighting</em> still has Java-side umlaut logic, because {@code ts_headline}
 * re-tokenizes raw column text rather than reading {@code search_vector} and so cannot use the
 * same normalized query — {@link SequenceSearchService#addHighlightTsqueryParams} folds and
 * expands the query for that reason alone; see its tests below and
 * {@code FullTextSearch#HIGHLIGHT_TSQUERY_EXPR}'s Javadoc for why.
 *
 * <p>What this class deliberately does NOT cover: whether the SQL itself actually matches rows in
 * Postgres. The project has no database-integration test setup (no Testcontainers, no live-DB
 * test profile — every existing test here is a Mockito unit test), so that part was verified by
 * hand against a running dev database rather than in this suite. A regression in the SQL text
 * itself (e.g. a broken tsquery expression) would not be caught by these tests.
 */
class SequenceSearchServiceTest {

    @Nested
    @DisplayName("addTsqueryParams")
    class AddTsqueryParams {

        @Test
        @DisplayName("adds exactly the two params TSQUERY_EXPR's two placeholders need, in order")
        void addsTwoParamsInOrder() {
            List<Object> params = new ArrayList<>();
            SequenceSearchService.addTsqueryParams(params, "Prüfung");

            // Order matters: it must line up 1:1 with TSQUERY_EXPR's two '?' placeholders
            // (the websearch branch, then the prefix-match branch — both normalized in SQL). A
            // reorder here silently binds the wrong value to the wrong branch with no compiler
            // error.
            assertEquals(List.of("Prüfung", "Prüfung"), params);
        }

        @Test
        @DisplayName("appends to whatever the params list already contains, rather than replacing it")
        void appendsRatherThanReplaces() {
            List<Object> params = new ArrayList<>();
            params.add("existing-param");
            SequenceSearchService.addTsqueryParams(params, "test");

            assertEquals(List.of("existing-param", "test", "test"), params);
        }
    }

    @Nested
    @DisplayName("TSQUERY_EXPR")
    class TsqueryExprShape {

        @Test
        @DisplayName("has exactly two '?' placeholders, matching addTsqueryParams' two params")
        void hasExactlyTwoPlaceholders() {
            // The single most likely way to silently break every FTS query at once: adding or
            // removing a branch in TSQUERY_EXPR without updating addTsqueryParams' two adds (or
            // vice versa) desyncs every bind-parameter position from here on in buildWhere,
            // fetchPage's ts_headline, and both ORDER BY relevance branches.
            long placeholderCount = SequenceSearchService.TSQUERY_EXPR.chars().filter(c -> c == '?').count();
            assertEquals(2, placeholderCount);
        }

        @Test
        @DisplayName("is a single self-contained, parenthesised OR expression")
        void isParenthesisedOrExpression() {
            String expr = SequenceSearchService.TSQUERY_EXPR.strip();
            assertEquals('(', expr.charAt(0));
            assertEquals(')', expr.charAt(expr.length() - 1));
            assertBalancedParens(expr);
        }

        @Test
        @DisplayName("normalizes the query through search_normalize on both branches")
        void normalizesBothBranches() {
            long normalizeCalls = SequenceSearchService.TSQUERY_EXPR
                    .split("search_normalize", -1).length - 1;
            assertEquals(2, normalizeCalls,
                    "both the websearch branch and the prefix branch must normalize the query, "
                            + "or matching stops being symmetric with how search_vector was built");
        }

        private void assertBalancedParens(String sql) {
            int depth = 0;
            for (char c : sql.toCharArray()) {
                if (c == '(') depth++;
                if (c == ')') depth--;
                assertTrue(depth >= 0, "unbalanced parentheses in TSQUERY_EXPR");
            }
            assertEquals(0, depth, "unbalanced parentheses in TSQUERY_EXPR");
        }
    }

    @Nested
    @DisplayName("addHighlightTsqueryParams")
    class AddHighlightTsqueryParams {

        @Test
        @DisplayName("adds the query as typed, folded to digraphs, and expanded to umlauts — six params")
        void addsSixParamsInOrder() {
            List<Object> params = new ArrayList<>();
            SequenceSearchService.addHighlightTsqueryParams(params, "Prüfung");

            // Order matters: it must line up 1:1 with HIGHLIGHT_TSQUERY_EXPR's six '?'
            // placeholders (as typed, folded, expanded — each with its own prefix-match
            // sibling). Unlike addTsqueryParams, this is NOT search_normalize'd in SQL: ts_headline
            // re-tokenizes the raw column text, so what it needs is the query in whichever raw
            // spellings could plausibly appear in that raw text.
            assertEquals(List.of("Prüfung", "Prüfung", "Pruefung", "Pruefung", "Prüfung", "Prüfung"), params);
        }

        @Test
        @DisplayName("expands a digraph query to the umlaut spelling too, not just folds an umlaut one")
        void expandsDigraphsAsWellAsFolding() {
            List<Object> params = new ArrayList<>();
            SequenceSearchService.addHighlightTsqueryParams(params, "fakultaet");

            // This is the direction the old, Java-side fold never covered: a query typed without
            // umlauts must still be able to highlight a row spelled with them.
            assertEquals(List.of("fakultaet", "fakultaet", "fakultaet", "fakultaet", "fakultät", "fakultät"),
                    params);
        }
    }

    @Nested
    @DisplayName("HIGHLIGHT_TSQUERY_EXPR")
    class HighlightTsqueryExprShape {

        @Test
        @DisplayName("has exactly six '?' placeholders, matching addHighlightTsqueryParams' six params")
        void hasExactlySixPlaceholders() {
            long placeholderCount =
                    SequenceSearchService.HIGHLIGHT_TSQUERY_EXPR.chars().filter(c -> c == '?').count();
            assertEquals(6, placeholderCount);
        }

        @Test
        @DisplayName("does not normalize the query, unlike TSQUERY_EXPR")
        void doesNotNormalize() {
            // ts_headline re-tokenizes the raw, un-normalized column text on every call rather
            // than reading search_vector, so a search_normalize'd query would never match it in
            // either direction. See FullTextSearch#HIGHLIGHT_TSQUERY_EXPR.
            assertFalse(SequenceSearchService.HIGHLIGHT_TSQUERY_EXPR.contains("search_normalize"));
        }
    }
}
