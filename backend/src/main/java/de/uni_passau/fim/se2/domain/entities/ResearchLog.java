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
package de.uni_passau.fim.se2.domain.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the admin research page is currently looking at, and what came back.
 */
public final class ResearchLog {

    private ResearchLog() {}

    /**
     * The filter behind every read on the page — one shape for all four datasets, so the
     * table, the summary strip and the CSV download cannot drift apart and disagree about
     * what is on screen.
     *
     * <p>Every field is optional except paging. A null {@code classId} means all classes
     * <em>including</em> the events that belong to none: work done from a direct link, or an
     * anonymised row written before V22 whose class is not recoverable. Those rows exist and
     * hiding them by default would quietly understate the data.
     *
     * @param classId   restrict to one class, or null for everything
     * @param eventType only meaningful for {@link ResearchDataset#EVENTS}
     * @param from      inclusive lower bound on the dataset's time column
     * @param to        exclusive upper bound, so a whole day is [day, day+1)
     */
    public record Filter(
            UUID classId,
            String eventType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {}

    /**
     * One page of rows.
     *
     * <p>Rows are untyped column maps rather than a DTO per dataset, matching
     * {@code ResearchDataReader} and the CSV export that already reads these same tables.
     * Four hand-written DTOs would buy type safety the page never uses — it renders whatever
     * columns it is handed — at the cost of four mappers to keep in step with the schema.
     *
     * <p>{@code columns} is sent explicitly and not derived from the first row, so an empty
     * result still renders its table header instead of collapsing to nothing.
     */
    public record Page(
            List<String> columns,
            List<Map<String, Object>> content,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {}

    /**
     * The stats strip above the table, over whatever the filter currently selects.
     *
     * @param totalRows         rows matching the filter
     * @param identifiedUsers   distinct non-null user ids among them
     * @param anonymousRows     rows with no user id — anonymised, or never logged in
     * @param eventCounts       per event type, highest first; empty for non-event datasets
     */
    public record Summary(
            long totalRows,
            long identifiedUsers,
            long anonymousRows,
            Map<String, Long> eventCounts
    ) {}
}
