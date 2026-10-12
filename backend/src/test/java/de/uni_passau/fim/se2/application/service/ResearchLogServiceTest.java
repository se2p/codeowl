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

import de.uni_passau.fim.se2.domain.entities.ResearchDataset;
import de.uni_passau.fim.se2.domain.entities.ResearchLog;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ResearchLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the admin research page is allowed to ask for, and what comes back.
 *
 * <p>The filter is the one thing the table, the stats strip and both downloads share. If it
 * normalises differently for any of them, a CSV quietly stops matching the rows that were on
 * screen when it was clicked — which is the kind of wrong nobody notices until the numbers are
 * already in a paper.
 */
@DisplayName("research log service")
class ResearchLogServiceTest {

    private ResearchLogRepository repository;
    private ResearchLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(ResearchLogRepository.class);
        service = new ResearchLogService(repository);
    }

    private ResearchLog.Filter filter(ResearchDataset dataset, String eventType,
                                      LocalDate from, LocalDate to, Integer page, Integer size) {
        return service.buildFilter(dataset, null, eventType, from, to, page, size);
    }

    @Nested
    @DisplayName("filter")
    class Filters {

        @Test
        @DisplayName("a date range covers whole days, so from == to is not an empty range")
        void singleDayIsAWholeDay() {
            LocalDate day = LocalDate.of(2026, 9, 17);
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, day, day, 0, 50);

            assertAll(
                    () -> assertEquals(day.atStartOfDay(), f.from()),
                    // exclusive upper bound at the next midnight
                    () -> assertEquals(day.plusDays(1).atStartOfDay(), f.to())
            );
        }

        @Test
        @DisplayName("an oversized page is clamped rather than refused")
        void sizeIsClamped() {
            assertEquals(ResearchLogService.MAX_PAGE_SIZE,
                    filter(ResearchDataset.EVENTS, null, null, null, 0, 100_000).size());
        }

        @Test
        @DisplayName("nonsense paging falls back to the defaults instead of reaching SQL")
        void pagingIsNormalized() {
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, null, null, -3, 0);
            assertAll(
                    () -> assertEquals(0, f.page()),
                    () -> assertEquals(ResearchLogService.DEFAULT_PAGE_SIZE, f.size())
            );
        }

        @Test
        @DisplayName("a blank event type means no filter, not a filter on empty string")
        void blankEventTypeIsNoFilter() {
            assertNull(filter(ResearchDataset.EVENTS, "   ", null, null, 0, 50).eventType());
        }

        @Test
        @DisplayName("a mistyped event type is refused, so it cannot look like missing data")
        void unknownEventTypeIsRejected() {
            assertThrows(DomainValidationException.class,
                    () -> filter(ResearchDataset.EVENTS, "arrangement_nudged", null, null, 0, 50));
        }

        @Test
        @DisplayName("an event type on a dataset that has none is refused")
        void eventTypeOnWrongDatasetIsRejected() {
            assertThrows(DomainValidationException.class,
                    () -> filter(ResearchDataset.HINTS, "task_opened", null, null, 0, 50));
        }

        @Test
        @DisplayName("a backwards date range is refused")
        void backwardsRangeIsRejected() {
            assertThrows(DomainValidationException.class,
                    () -> filter(ResearchDataset.EVENTS, null,
                            LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 10), 0, 50));
        }

        @Test
        @DisplayName("no class means everything, including the rows that belong to no class")
        void noClassIsNoRestriction() {
            assertNull(service.buildFilter(
                    ResearchDataset.EVENTS, null, null, null, null, 0, 50).classId());
        }
    }

    @Nested
    @DisplayName("paging")
    class Paging {

        @Test
        @DisplayName("the page reports the total row count, not the size of the slice")
        void totalsComeFromTheCount() {
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, null, null, 0, 50);
            when(repository.countMatching(ResearchDataset.EVENTS, f)).thenReturn(123L);
            when(repository.findPage(ResearchDataset.EVENTS, f)).thenReturn(List.of(Map.of()));

            ResearchLog.Page page = service.page(ResearchDataset.EVENTS, f);

            assertAll(
                    () -> assertEquals(123L, page.totalElements()),
                    () -> assertEquals(3, page.totalPages()),
                    () -> assertEquals(1, page.content().size()),
                    // columns come from the dataset, so an empty result still has a header
                    () -> assertEquals(ResearchDataset.EVENTS.columns(), page.columns())
            );
        }

        @Test
        @DisplayName("an empty result is zero pages and still carries its columns")
        void emptyResultKeepsColumns() {
            ResearchLog.Filter f = filter(ResearchDataset.SUBMISSIONS, null, null, null, 0, 50);
            when(repository.countMatching(ResearchDataset.SUBMISSIONS, f)).thenReturn(0L);
            when(repository.findPage(ResearchDataset.SUBMISSIONS, f)).thenReturn(List.of());

            ResearchLog.Page page = service.page(ResearchDataset.SUBMISSIONS, f);

            assertAll(
                    () -> assertEquals(0, page.totalPages()),
                    () -> assertTrue(page.content().isEmpty()),
                    () -> assertTrue(page.columns().contains("block_order"))
            );
        }
    }

    @Nested
    @DisplayName("csv export")
    class Csv {

        @SuppressWarnings("unchecked")
        private void respondWith(List<Map<String, Object>> rows) {
            doAnswer(invocation -> {
                Consumer<Map<String, Object>> consumer = invocation.getArgument(2);
                rows.forEach(consumer);
                return null;
            }).when(repository).forEachMatching(any(), any(), any(Consumer.class));
        }

        private Map<String, Object> eventRow(String type, Object user) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
            row.put("created_at", "2026-09-17T09:14:02");
            row.put("class_id", null);
            row.put("user_id", user);
            row.put("task_id", null);
            row.put("sequence_id", null);
            row.put("tier_level", 1);
            row.put("event_type", type);
            row.put("event_data", "{\"moves\":[1,2]}");
            return row;
        }

        private String csvOf(List<Map<String, Object>> rows) {
            respondWith(rows);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, null, null, 0, 50);
            service.streamCsv(ResearchDataset.EVENTS, f, out);
            return out.toString(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("the header is written even when nothing matches")
        void headerAlwaysWritten() {
            String csv = csvOf(List.of());
            assertTrue(csv.startsWith("id,created_at,class_id,user_id"), csv);
        }

        @Test
        @DisplayName("a row with an embedded comma is quoted, so the columns stay aligned")
        void jsonPayloadIsQuoted() {
            String csv = csvOf(List.of(eventRow("arrangement_changed", null)));
            assertTrue(csv.contains("\"{\"\"moves\"\":[1,2]}\""), csv);
        }

        @Test
        @DisplayName("an anonymised row exports as an empty user, not the word null")
        void anonymousRowIsEmptyField() {
            String csv = csvOf(List.of(eventRow("task_opened", null)));
            String dataLine = csv.split("\r\n")[1];
            assertTrue(dataLine.contains(",,"), dataLine);
        }

        @Test
        @DisplayName("the row count returned is what gets audited")
        void rowCountIsReported() {
            respondWith(List.of(eventRow("task_opened", null), eventRow("task_correct", null)));
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, null, null, 0, 50);
            assertEquals(2L, service.streamCsv(
                    ResearchDataset.EVENTS, f, new ByteArrayOutputStream()));
        }

        @Test
        @DisplayName("the export ignores paging, so it is never capped at one page")
        void exportIgnoresPaging() {
            respondWith(List.of());
            ResearchLog.Filter f = filter(ResearchDataset.EVENTS, null, null, null, 0, 50);
            service.streamCsv(ResearchDataset.EVENTS, f, new ByteArrayOutputStream());

            verify(repository).forEachMatching(eq(ResearchDataset.EVENTS), eq(f), any());
            verify(repository, never()).findPage(any(), any());
        }
    }
}
