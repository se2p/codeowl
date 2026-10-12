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

import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Forking a sequence a curator does not own.
 *
 * <p>Whether the copy is <em>complete</em> is {@link WorksheetCloneService}'s question, covered
 * by {@code CloneCompletenessTest}; this class deliberately does not re-test it, because the
 * fork calls the same method a worksheet copy does and duplicating the assertions would only
 * mean two places to update.
 *
 * <p>What is left is the part unique to forking, and it is all about ordering and refusal: the
 * worksheet must end up pointing at the copy and not at the original, and it must never end up
 * pointing at neither.
 */
class SequenceForkServiceTest {

    private static final UUID WORKSHEET = UUID.randomUUID();
    private static final UUID SEQUENCE = UUID.randomUUID();
    private static final UUID CURATOR = UUID.randomUUID();
    private static final UUID COPY = UUID.randomUUID();

    private WorksheetRepository worksheetRepo;
    private WorksheetCloneService cloneService;
    private SequenceForkService service;

    @BeforeEach
    void setUp() {
        worksheetRepo = mock(WorksheetRepository.class);
        cloneService = mock(WorksheetCloneService.class);
        service = new SequenceForkService(worksheetRepo, cloneService);

        // getWorksheetById fills the DTO it is handed rather than returning one.
        doAnswer(invocation -> {
            WorksheetDTO sheet = invocation.getArgument(0);
            List<TieredTaskSequenceDTO> sequences = new ArrayList<>();
            sequences.add(TieredTaskSequenceDTO.builder().id(SEQUENCE).build());
            sheet.setSequenceDTOS(sequences);
            sheet.setOwnerId(CURATOR);
            return null;
        }).when(worksheetRepo).getWorksheetById(any());

        when(cloneService.copySequenceInto(any(), eq(SEQUENCE), eq(CURATOR))).thenReturn(COPY);
    }

    @Test
    @DisplayName("hands back the copy the caller should redirect to")
    void returnsTheCopy() {
        assertEquals(COPY, service.forkForEditing(WORKSHEET, SEQUENCE, CURATOR));
    }

    @Test
    @DisplayName("links the copy before unlinking the original")
    void copyIsLinkedBeforeOriginalIsRemoved() {
        // If the order were reversed and the copy then failed, the transaction would roll back
        // — but only if there is a transaction. Getting the order right means the worksheet is
        // never momentarily empty even when there is not.
        service.forkForEditing(WORKSHEET, SEQUENCE, CURATOR);

        InOrder order = inOrder(cloneService, worksheetRepo);
        order.verify(cloneService).copySequenceInto(any(), eq(SEQUENCE), eq(CURATOR));
        order.verify(worksheetRepo).removeSequenceFromWorksheet(any(), any());
    }

    @Test
    @DisplayName("unlinks the original, not the copy")
    void unlinksTheOriginal() {
        // The two ids are interchangeable at the call site and swapping them would leave the
        // worksheet holding the sequence it was supposed to stop holding.
        service.forkForEditing(WORKSHEET, SEQUENCE, CURATOR);

        verify(worksheetRepo).removeSequenceFromWorksheet(any(),
                org.mockito.ArgumentMatchers.argThat(seq -> SEQUENCE.equals(seq.getId())));
    }

    @Test
    @DisplayName("refuses a sequence the worksheet does not contain")
    void refusesForeignSequence() {
        UUID notInSheet = UUID.randomUUID();

        assertThrows(DomainValidationException.class,
                () -> service.forkForEditing(WORKSHEET, notInSheet, CURATOR));
        // Nothing copied and nothing unlinked: a fork of something the worksheet does not have
        // would quietly add material to it.
        verify(cloneService, never()).copySequenceInto(any(), any(), any());
        verify(worksheetRepo, never()).removeSequenceFromWorksheet(any(), any());
    }

    @Test
    @DisplayName("refuses null arguments rather than writing half a fork")
    void refusesNulls() {
        assertAll(
                () -> assertThrows(DomainValidationException.class,
                        () -> service.forkForEditing(null, SEQUENCE, CURATOR)),
                () -> assertThrows(DomainValidationException.class,
                        () -> service.forkForEditing(WORKSHEET, null, CURATOR)),
                () -> assertThrows(DomainValidationException.class,
                        () -> service.forkForEditing(WORKSHEET, SEQUENCE, null)));
        verify(cloneService, never()).copySequenceInto(any(), any(), any());
    }
}
