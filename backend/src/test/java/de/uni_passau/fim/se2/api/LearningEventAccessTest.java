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
package de.uni_passau.fim.se2.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.application.service.LearningEventService;
import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.TaskPersistenceService;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may read the learning events of a task or sequence.
 *
 * <p>These endpoints were {@code authenticated} and nothing more, so any logged-in student
 * could read every event for any task — including the arrangements other students had built.
 * Recording every move rather than a snapshot at each Feedback click makes that considerably
 * worse, which is why the guard landed with the feature.
 *
 * <p>Plain JUnit and Mockito, like the rest of this suite: the project has no Spring test
 * context, so what is checked is the guard the controller applies, not the {@code
 * SecurityConfig} matcher in front of it.
 */
class LearningEventAccessTest {

    private LearningEventService events;
    private OwnershipService ownership;
    private TaskPersistenceService tasks;
    private LearningEventController controller;

    private static final UUID SEQUENCE = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        events = mock(LearningEventService.class);
        ownership = mock(OwnershipService.class);
        tasks = mock(TaskPersistenceService.class);
        controller = new LearningEventController(events, new ObjectMapper(), ownership, tasks);

        TieredTaskSequenceDTO sequence = new TieredTaskSequenceDTO();
        sequence.setId(SEQUENCE);
        sequence.setOwnerId(OWNER);
        when(tasks.loadSequenceById(SEQUENCE)).thenReturn(sequence);

        TaskDTO task = new TaskDTO();
        task.setId(TASK);
        task.setSequenceId(SEQUENCE);
        when(tasks.loadTaskById(TASK)).thenReturn(task);
    }

    /** Makes the caller someone other than the sequence's owner. */
    private void callerIsNotTheOwner() {
        doThrow(new AccessDeniedException("not yours"))
                .when(ownership).checkOwnership(any(), any());
    }

    @Test
    @DisplayName("a non-owner cannot read a task's events, and the query never runs")
    void taskEventsAreRefused() {
        callerIsNotTheOwner();

        assertThrows(AccessDeniedException.class, () -> controller.getEventsByTask(TASK));
        verify(events, never()).getEventsByTask(any());
    }

    @Test
    @DisplayName("a non-owner cannot read a sequence's events")
    void sequenceEventsAreRefused() {
        callerIsNotTheOwner();

        assertThrows(AccessDeniedException.class, () -> controller.getEventsBySequence(SEQUENCE));
        verify(events, never()).getEventsBySequence(any());
    }

    @Test
    @DisplayName("a non-owner cannot read a sequence's summary")
    void summaryIsRefused() {
        callerIsNotTheOwner();

        assertThrows(AccessDeniedException.class, () -> controller.getSequenceSummary(SEQUENCE));
        verify(events, never()).getSequenceSummary(any());
    }

    @Test
    @DisplayName("the owner reads all three")
    void ownerIsAllowed() {
        when(events.getEventsByTask(TASK)).thenReturn(List.of());
        when(events.getEventsBySequence(SEQUENCE)).thenReturn(List.of());

        assertDoesNotThrow(() -> {
            controller.getEventsByTask(TASK);
            controller.getEventsBySequence(SEQUENCE);
            controller.getSequenceSummary(SEQUENCE);
        });

        verify(events).getEventsByTask(TASK);
        verify(events).getEventsBySequence(SEQUENCE);
        verify(events).getSequenceSummary(SEQUENCE);
    }

    @Test
    @DisplayName("an unknown task is refused rather than checked against a null sequence")
    void unknownTaskIsRefused() {
        when(tasks.loadTaskById(TASK)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> controller.getEventsByTask(TASK));
        verify(events, never()).getEventsByTask(any());
    }

    @Test
    @DisplayName("an unknown sequence is refused rather than read")
    void unknownSequenceIsRefused() {
        when(tasks.loadSequenceById(SEQUENCE)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> controller.getEventsBySequence(SEQUENCE));
        verify(events, never()).getEventsBySequence(any());
    }
}
