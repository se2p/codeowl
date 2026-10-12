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

import java.util.List;

/**
 * The shape of a free-text query, shared by everything in the product that has a search box.
 *
 * <p>Lifted out of {@link SequenceSearchService}, unchanged, when the worksheet marketplace
 * gained a second one. The two searches look at different tables but must answer the same way:
 * a teacher who types "Prüfung" in one box and "prue" in the other has no reason to expect
 * different rules about umlauts or half-typed words, and two copies of this logic would have
 * drifted apart the first time somebody fixed one of them.
 *
 * <p>{@link SequenceSearchService} keeps thin delegates for the members its own test pins
 * down, so that test still describes the behaviour where it was written.
 */
final class FullTextSearch {

    private FullTextSearch() {
    }

    /**
     * SQL fragment for a free-text tsquery that ORs together two ways a search term can match,
     * combined with the tsquery {@code ||} (OR) operator. Every occurrence takes two bind
     * parameters, filled in by {@link #addTsqueryParams}, in this order:
     *
     * <ol>
     *   <li>{@code websearch_to_tsquery(search_normalize(query))} — the query as typed, with
     *       real operators ("exact phrase", {@code -exclude}, {@code or}), matched after the
     *       same normalization {@code search_vector} was generated with (see
     *       {@code db/migration/V19__unaccent_search.sql}). {@code search_normalize} folds
     *       German umlauts to their digraph spelling and strips every other language's accents
     *       via the {@code unaccent} extension, so "fakultaet" and "Fakultät" — whichever one is
     *       stored and whichever one is typed — normalize to the same lexeme.</li>
     *   <li>a prefix match on every word of {@code search_normalize(query)} (see
     *       {@link #prefixTsquerySubexpr()}) — the search boxes are live and debounced, so most
     *       keystrokes submit a word that is still being typed. {@code websearch_to_tsquery}
     *       matches whole words only, so without this branch search-as-you-type shows nothing
     *       until the last word is finished. It loses websearch's operators, which is why it is
     *       additive rather than a replacement.</li>
     * </ol>
     */
    static final String TSQUERY_EXPR = """
            (websearch_to_tsquery('simple', public.search_normalize(?)) || %1$s)\
            """.formatted(prefixTsquerySubexpr());

    /**
     * Builds an all-words-as-prefixes tsquery from arbitrary raw text — safe against tsquery
     * syntax errors (and SQL injection, since the text only ever flows through as a bind
     * parameter) because {@code to_tsvector} does the tokenizing: its output lexemes can never
     * contain tsquery metacharacters ({@code & | ! ( ) :}), so joining them back together with
     * {@code ':*'} suffixes and re-parsing with {@code to_tsquery} is always well-formed. Falls
     * back to an empty (never-matching) tsquery for blank input rather than erroring.
     */
    private static String prefixTsquerySubexpr() {
        return """
                COALESCE(
                    (SELECT to_tsquery('simple', string_agg(lexeme || ':*', ' & '))
                       FROM unnest(to_tsvector('simple', public.search_normalize(?))) AS u(lexeme, positions, weights)),
                    ''::tsquery
                )""";
    }

    /**
     * Adds the two bind parameters one {@link #TSQUERY_EXPR} occurrence needs, in order.
     *
     * <p>The SQL placeholders are position-based, so a reorder here silently breaks every query
     * using the expression, with no compiler error to catch it. That is what
     * {@code SequenceSearchServiceTest} pins down.
     */
    static void addTsqueryParams(List<Object> params, String query) {
        params.add(query);
        params.add(query);
    }

    /**
     * SQL fragment for the tsquery {@code ts_headline} highlights against — deliberately
     * <em>not</em> {@link #TSQUERY_EXPR}, and for a reason specific to how {@code ts_headline}
     * works: it re-tokenizes the raw, un-normalized column text every time it runs, rather than
     * reading the already-normalized {@code search_vector}. A query normalized through {@code
     * search_normalize} (folding "Fakultät" and "fakultaet" to the same lexeme) therefore stops
     * matching the raw text's own lexeme entirely — not "the wrong direction" but neither
     * direction, including the query spelled exactly like the stored text. {@code TSQUERY_EXPR}
     * decides <em>which rows</em> match, correctly, via the generated column; this decides which
     * word in a row that already matched gets wrapped in {@code <mark>}, which needs its own
     * tsquery built from the raw, un-normalized text on both sides instead.
     *
     * <p>ORs together three spellings of the query, each with its own prefix-match sibling (see
     * {@link #prefixTsquerySubexpr()}), six bind parameters total, filled in by {@link
     * #addHighlightTsqueryParams} in this order:
     *
     * <ol>
     *   <li>the query as typed — matches a row spelled exactly the same way.</li>
     *   <li>the query folded ä/ö/ü→ae/oe/ue (see {@link #foldToDigraphs}) — matches a row
     *       written before the semantic-tagger prompt was fixed, which spells umlauts as
     *       digraphs.</li>
     *   <li>the query expanded ae/oe/ue→ä/ö/ü (see {@link #expandDigraphsToUmlauts}) — matches
     *       a row spelled with real umlauts when the query was typed without them.</li>
     * </ol>
     *
     * <p>The fold and the expansion are both ambiguous in the general case ("Aeon", "Klasse")
     * the way {@code search_normalize} is not, which is exactly why they are confined to
     * highlighting: which rows come back is decided by {@code TSQUERY_EXPR} alone, so the worst
     * an ambiguous branch here can do is mark a coincidental word inside a row that already
     * qualified on other grounds — never change the result set itself.
     */
    static final String HIGHLIGHT_TSQUERY_EXPR = """
            (websearch_to_tsquery('simple', ?) || %1$s \
            || websearch_to_tsquery('simple', ?) || %1$s \
            || websearch_to_tsquery('simple', ?) || %1$s)\
            """.formatted(prefixTsquerySubexprRaw());

    /** Like {@link #prefixTsquerySubexpr()} but without the {@code search_normalize} wrapper — see {@link #HIGHLIGHT_TSQUERY_EXPR}. */
    private static String prefixTsquerySubexprRaw() {
        return """
                COALESCE(
                    (SELECT to_tsquery('simple', string_agg(lexeme || ':*', ' & '))
                       FROM unnest(to_tsvector('simple', ?)) AS u(lexeme, positions, weights)),
                    ''::tsquery
                )""";
    }

    /** @see #HIGHLIGHT_TSQUERY_EXPR */
    static void addHighlightTsqueryParams(List<Object> params, String query) {
        String folded = foldToDigraphs(query);
        String expanded = expandDigraphsToUmlauts(query);
        params.add(query);
        params.add(query);
        params.add(folded);
        params.add(folded);
        params.add(expanded);
        params.add(expanded);
    }

    /** ä/ö/ü → ae/oe/ue (both cases); every other character is left untouched. */
    private static String foldToDigraphs(String s) {
        return s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue");
    }

    /** ae/oe/ue → ä/ö/ü (both cases); every other character is left untouched. */
    private static String expandDigraphsToUmlauts(String s) {
        return s.replace("ae", "ä").replace("oe", "ö").replace("ue", "ü")
                .replace("Ae", "Ä").replace("Oe", "Ö").replace("Ue", "Ü");
    }

    /** Clamps a requested page size into what the server is willing to serve. */
    static int clampPageSize(int requested, int fallback, int max) {
        int size = requested <= 0 ? fallback : requested;
        return Math.min(size, max);
    }
}
