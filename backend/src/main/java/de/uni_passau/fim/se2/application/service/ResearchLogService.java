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

import de.uni_passau.fim.se2.application.util.CsvWriter;
import de.uni_passau.fim.se2.domain.entities.ResearchDataset;
import de.uni_passau.fim.se2.domain.entities.ResearchLog;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ResearchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reads the research tables for the admin research page.
 *
 * <p>Deliberately separate from {@link LearningEventService}, which owns the write path. The
 * two aggregations that already existed there load whole result sets and group them in Java
 * ({@code getSequenceSummary} over one sequence, {@code getAllEvents} over the entire table);
 * everything here is filtered, counted and aggregated in SQL instead, because the page is
 * pointed at a whole class rather than a single sequence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchLogService {

    private final ResearchLogRepository researchLogRepository;

    /** A page big enough to scroll, small enough that one request cannot pull a table. */
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 500;

    /**
     * Normalises what arrived on the query string into a filter the DAO can trust.
     *
     * <p>Paging is clamped rather than rejected — a hand-edited {@code size=100000} is a
     * request for everything, and the answer to that is the CSV download, not an OOM.
     *
     * @param from inclusive; {@code to} is made exclusive by adding a day, so picking the same
     *             date for both still selects that whole day rather than nothing
     */
    public ResearchLog.Filter buildFilter(ResearchDataset dataset, UUID classId, String eventType,
                                          LocalDate from, LocalDate to, Integer page, Integer size) {

        String type = (eventType == null || eventType.isBlank()) ? null : eventType.trim();
        if (type != null) {
            if (!dataset.isEventTyped()) {
                throw new DomainValidationException(
                        "eventType only applies to the events dataset, not " + dataset.wireName());
            }
            if (!LearningEventService.allowedEventTypes().contains(type)) {
                throw new DomainValidationException("Unknown eventType: " + type);
            }
        }

        if (from != null && to != null && to.isBefore(from)) {
            throw new DomainValidationException("'to' is before 'from'");
        }

        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size < 1) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        return new ResearchLog.Filter(
                classId,
                type,
                from != null ? from.atStartOfDay() : null,
                to != null ? to.plusDays(1).atStartOfDay() : null,
                safePage,
                safeSize
        );
    }

    /** One page of rows plus the total, for the table and its pager. */
    public ResearchLog.Page page(ResearchDataset dataset, ResearchLog.Filter filter) {
        long total = researchLogRepository.countMatching(dataset, filter);
        List<Map<String, Object>> rows = researchLogRepository.findPage(dataset, filter);

        int totalPages = (int) Math.ceil((double) total / filter.size());
        return new ResearchLog.Page(
                dataset.columns(), rows, total, totalPages, filter.page(), filter.size());
    }

    /** The stats strip over the same filter the table is showing. */
    public ResearchLog.Summary summary(ResearchDataset dataset, ResearchLog.Filter filter) {
        return researchLogRepository.summarize(dataset, filter);
    }

    /**
     * Writes every matching row as CSV to {@code output}, ignoring the filter's paging.
     *
     * <p>Read-only transaction so the DAO's fetch size takes effect: without it the driver
     * buffers the whole result before the first row is written, which defeats the point of
     * streaming an export of a table this size.
     *
     * @return rows written, for the audit entry
     */
    @Transactional(readOnly = true)
    public long streamCsv(ResearchDataset dataset, ResearchLog.Filter filter, OutputStream output) {
        CsvWriter csv = new CsvWriter(output);
        long rows = writeRows(csv, dataset, filter);
        csv.flush();

        log.info("Research CSV export: dataset={}, classId={}, rows={}",
                dataset.wireName(), filter.classId(), rows);
        return rows;
    }

    /**
     * Header plus every matching row, onto a writer the caller owns.
     *
     * <p>Shared with the per-class ZIP, which writes four of these into one archive and must
     * not close the stream between them.
     *
     * @return rows written, not counting the header
     */
    long writeRows(CsvWriter csv, ResearchDataset dataset, ResearchLog.Filter filter) {
        csv.writeRow(dataset.columns());

        AtomicLong rows = new AtomicLong();
        researchLogRepository.forEachMatching(dataset, filter, row -> {
            List<Object> values = new ArrayList<>(dataset.columns().size());
            for (String column : dataset.columns()) {
                values.add(row.get(column));
            }
            csv.writeRow(values);
            rows.incrementAndGet();
        });
        return rows.get();
    }
}
