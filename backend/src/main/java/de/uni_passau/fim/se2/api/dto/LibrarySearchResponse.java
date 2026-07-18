/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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
package de.uni_passau.fim.se2.api.dto;

import java.util.List;

/**
 * Wrapper for {@code GET /api/library/sequences}.
 *
 * <p>{@code facets} are cheap to compute alongside the main query (same WHERE clause with a
 * GROUP BY) and let the Library sidebar show tag / language / ownership counts without a
 * second round-trip. {@code suggestions} is only populated when the FTS query returned zero
 * results — the server computes trigram-similar tag names as "Did you mean…" alternatives.
 */
public record LibrarySearchResponse(
        List<LibraryItemDTO> items,
        int page,
        int size,
        long total,
        Facets facets,
        List<String> suggestions
) {
    /** Facet counts for the filter sidebar. All lists are sorted by descending count. */
    public record Facets(
            List<FacetBucket> languages,
            List<FacetBucket> topTags,
            Ownership ownership
    ) {}

    /** A single facet entry: value → count. */
    public record FacetBucket(String name, long count) {}

    /** Split of sequences by "mine vs. everyone else" — helps the Library sidebar "My sequences" toggle. */
    public record Ownership(long mine, long others) {}
}
