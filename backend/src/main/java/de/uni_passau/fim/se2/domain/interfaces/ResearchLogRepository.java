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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.ResearchDataset;
import de.uni_passau.fim.se2.domain.entities.ResearchLog;

import java.util.List;
import java.util.Map;

/**
 * Filtered, paginated reads over the research tables for the admin research page.
 *
 * <p>Separate from {@link ResearchDataReader}, which exists to dump whole tables into the
 * research ZIP. This one never returns an unbounded result: the page shows a slice and the
 * CSV download streams, so nothing here loads a table into memory the way
 * {@code LearningEventService.getAllEvents()} does.
 */
public interface ResearchLogRepository {

    /** One page of rows, ordered newest first. */
    List<Map<String, Object>> findPage(ResearchDataset dataset, ResearchLog.Filter filter);

    /** How many rows the filter matches in total, for the pager. */
    long countMatching(ResearchDataset dataset, ResearchLog.Filter filter);

    /** Aggregates for the stats strip, computed in SQL rather than over a loaded list. */
    ResearchLog.Summary summarize(ResearchDataset dataset, ResearchLog.Filter filter);

    /**
     * Streams every matching row to the consumer, ignoring {@code page}/{@code size}.
     *
     * <p>For the CSV and ZIP downloads, which must not be capped at a page and must not
     * materialise the whole result either.
     */
    void forEachMatching(ResearchDataset dataset, ResearchLog.Filter filter,
                         java.util.function.Consumer<Map<String, Object>> consumer);
}
