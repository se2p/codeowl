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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A page of marketplace results.
 *
 * @param items  the worksheets on this page
 * @param page   zero-based page index that was served
 * @param size   page size that was served
 * @param total  how many worksheets match, across all pages
 * @param facets counts for the filter sidebar
 */
public record WorksheetSearchResult(
        List<Item> items,
        int page,
        int size,
        long total,
        Facets facets
) {

    /**
     * One worksheet as the marketplace shows it.
     *
     * <p>Everything a card needs is here, so rendering a page of results costs one query rather
     * than one query and then a follow-up per card. {@code sequenceCount} and {@code languages}
     * in particular are what a teacher actually scans for — a worksheet's name says what it is
     * called, not what is in it.
     *
     * @param worksheetId   identifier
     * @param name          the teacher's title for it
     * @param description   their description, possibly empty
     * @param ownerId       who published it
     * @param createdAt     when it was created; the default sort
     * @param sequenceCount how many sequences it holds
     * @param languages     the distinct programming languages of those sequences, sorted
     * @param copyCount     how many times it has been copied — the popularity sort
     * @param alreadyCopied whether the caller already has a copy, so the card can say so
     *                      instead of letting them make a second one by accident
     * @param highlight     a {@code ts_headline} snippet with the match marked, or {@code null}
     *                      when there was no query
     */
    public record Item(
            UUID worksheetId,
            String name,
            String description,
            UUID ownerId,
            Instant createdAt,
            int sequenceCount,
            List<String> languages,
            long copyCount,
            boolean alreadyCopied,
            String highlight
    ) {}

    /**
     * Counts for the sidebar.
     *
     * @param languages  how many worksheets contain each programming language
     * @param mine       how many of the matches the caller published
     * @param others     how many came from everyone else
     */
    public record Facets(List<Bucket> languages, long mine, long others) {}

    /** A facet entry: a value and how many matches carry it. */
    public record Bucket(String name, long count) {}
}
