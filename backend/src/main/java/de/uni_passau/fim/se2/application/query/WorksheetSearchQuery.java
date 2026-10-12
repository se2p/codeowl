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

import java.util.Locale;

/**
 * What a teacher asked the marketplace for, after the request has been made safe.
 *
 * <p>A record rather than a mutable bean, and normalised once at the boundary by
 * {@link #of}: everything downstream can then assume the sort is one of three known values, the
 * page is not negative and the size is within what the server will serve. A query object that
 * might still be holding {@code sort=DROP TABLE} is a query object every later line has to be
 * suspicious of.
 *
 * <p>Lives in its own package with no {@code api} or {@code infrastructure} imports, so the
 * search service can be exercised without a web layer or a database. That is a deliberate
 * departure from {@code SequenceSearchService}, which reaches into {@code api.dto} for its
 * result types — convenient, but it points the dependency the wrong way and leaves the query
 * builder impossible to test on its own.
 *
 * @param q          free-text query, or {@code null} for "everything"
 * @param lang       programming language filter, matched against the worksheet's sequences
 * @param onlyMine   restrict to worksheets the caller published themselves
 * @param sort       {@link #SORT_RECENT}, {@link #SORT_RELEVANCE} or {@link #SORT_POPULARITY}
 * @param page       zero-based page index
 * @param size       page size
 */
public record WorksheetSearchQuery(
        String q,
        String lang,
        boolean onlyMine,
        String sort,
        int page,
        int size
) {

    public static final String SORT_RECENT = "recent";
    public static final String SORT_RELEVANCE = "relevance";
    public static final String SORT_POPULARITY = "popularity";

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Normalises a raw request into something safe to build SQL from.
     *
     * <p>Blank text becomes {@code null} rather than an empty string, so the rest of the code
     * has one way to ask "did they search for anything" instead of two. An unrecognised sort
     * falls back to {@link #SORT_RECENT} rather than being rejected: sort order is a
     * presentation detail, and failing a whole search because a client sent a stale value would
     * be a worse answer than showing the newest first.
     *
     * <p>Ordering by relevance means nothing without a query, so it quietly becomes
     * {@code recent} in that case — the alternative is a page that claims to be sorted by
     * relevance to nothing.
     */
    public static WorksheetSearchQuery of(String q, String lang, boolean onlyMine,
                                          String sort, int page, int size) {
        String text = blankToNull(q);
        String normalisedSort = normaliseSort(sort);
        if (SORT_RELEVANCE.equals(normalisedSort) && text == null) {
            normalisedSort = SORT_RECENT;
        }
        return new WorksheetSearchQuery(
                text,
                blankToNull(lang),
                onlyMine,
                normalisedSort,
                Math.max(0, page),
                clampSize(size));
    }

    /** Whether a free-text query was given; the SQL branches on this in several places. */
    public boolean hasText() {
        return q != null;
    }

    /** Rows to skip, for the {@code OFFSET} clause. */
    public int offset() {
        return page * size;
    }

    private static String normaliseSort(String sort) {
        if (sort == null) return SORT_RECENT;
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case SORT_RELEVANCE -> SORT_RELEVANCE;
            case SORT_POPULARITY -> SORT_POPULARITY;
            default -> SORT_RECENT;
        };
    }

    private static int clampSize(int size) {
        int requested = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
