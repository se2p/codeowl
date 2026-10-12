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

import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Autosaving work in progress, and keeping it distinct from having solved the task.
 *
 * <p>The distinction is the whole point of the {@code solved} flag. An autosave must not claim
 * a student finished a task they are halfway through, and it must not take away a solve they
 * already earned by fiddling with the blocks afterwards. The stickiness itself is enforced in
 * the DAO's upsert; what is checked here is that the service asks for the right thing.
 */
class TaskSubmissionProgressTest {

    private TaskSubmissionRepository repository;
    private WorksheetAccessService access;
    private TaskSubmissionService service;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(TaskSubmissionRepository.class);
        access = mock(WorksheetAccessService.class);
        service = new TaskSubmissionService(repository, access);
    }

    private TaskSubmissionDTO saved() {
        ArgumentCaptor<TaskSubmissionDTO> captor = ArgumentCaptor.forClass(TaskSubmissionDTO.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("work in progress")
    class Progress {

        @Test
        @DisplayName("is stored without claiming the task was solved")
        void storedAsUnsolved() {
            service.saveProgress(USER, TASK, List.of("a = 1", "b = 2"), List.of(0, 1));

            TaskSubmissionDTO dto = saved();
            assertAll(
                    () -> assertFalse(dto.isSolved(), "an autosave is not a solve"),
                    () -> assertEquals(List.of("a = 1", "b = 2"), dto.getBlockOrder()),
                    () -> assertEquals(List.of(0, 1), dto.getIndentLevels()));
        }

        @Test
        @DisplayName("an empty solution area is a real state, not an error")
        void emptyArrangementIsAccepted() {
            // A student who has pulled every block back out is somewhere specific. Refusing to
            // record it would leave the previous arrangement to be restored over their work.
            service.saveProgress(USER, TASK, List.of(), List.of());

            assertEquals(List.of(), saved().getBlockOrder());
        }

        @Test
        @DisplayName("a null arrangement is stored as empty rather than rejected")
        void nullArrangementIsTolerated() {
            service.saveProgress(USER, TASK, null, null);

            TaskSubmissionDTO dto = saved();
            assertAll(
                    () -> assertEquals(List.of(), dto.getBlockOrder()),
                    () -> assertEquals(List.of(), dto.getIndentLevels()));
        }

        @Test
        @DisplayName("is refused on a worksheet that is no longer solvable")
        void closedWorksheetIsRefused() {
            doThrowOnAccessCheck();

            assertThrows(AccessDeniedLike.class, () -> service.saveProgress(USER, TASK, List.of("a"), List.of(0)));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("an anonymous solver has nowhere to store work, and is not an error")
        void anonymousIsIgnored() {
            service.saveProgress(null, TASK, List.of("a = 1"), List.of(0));

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("needs a task")
        void taskIsRequired() {
            assertThrows(DomainValidationException.class,
                    () -> service.saveProgress(USER, null, List.of("a = 1"), List.of(0)));
        }
    }

    @Nested
    @DisplayName("a solve")
    class Solve {

        @Test
        @DisplayName("is stored as solved")
        void storedAsSolved() {
            service.saveSolved(USER, TASK, List.of("a = 1"), List.of(0));

            assertTrue(saved().isSolved());
        }

        @Test
        @DisplayName("still refuses an empty arrangement, which would look solved-but-blank")
        void emptyArrangementIsRejected() {
            assertThrows(DomainValidationException.class,
                    () -> service.saveSolved(USER, TASK, List.of(), List.of()));
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("discarding")
    class Discard {

        @Test
        @DisplayName("removes the stored arrangement")
        void removesTheRow() {
            service.discard(USER, TASK);

            verify(repository).delete(USER, TASK);
        }

        @Test
        @DisplayName("is allowed on a closed worksheet, since the student is giving up work")
        void allowedWhenNotSolvable() {
            doThrowOnAccessCheck();

            service.discard(USER, TASK);

            verify(repository).delete(USER, TASK);
        }

        @Test
        @DisplayName("does nothing for an anonymous visitor")
        void anonymousIsIgnored() {
            service.discard(null, TASK);

            verify(repository, never()).delete(any(), any());
        }
    }

    /** Marker for the exception WorksheetAccessService raises; see doThrowOnAccessCheck. */
    private static class AccessDeniedLike extends RuntimeException {
        AccessDeniedLike() {
            super("not solvable");
        }
    }

    private void doThrowOnAccessCheck() {
        org.mockito.Mockito.doThrow(new AccessDeniedLike())
                .when(access).requireSolvable(TASK);
    }
}
