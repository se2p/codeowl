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

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.StudentTaskStateDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.when;

/**
 * Letting a teacher look at one student's saved arrangement, and the three things that have to
 * hold first.
 *
 * <p>Two of them the progress page already enforced and this reuses. The third is new and is the
 * one worth the test: the arrangement is stored against a student and a task with no mention of
 * a class, so without checking that the task belongs to a worksheet <em>this</em> class was
 * assigned, any class owner could read any student's work anywhere in the system by passing its
 * task id.
 */
class StudentTaskStateTest {

    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();

    private static final UUID WORKSHEET = UUID.randomUUID();
    private static final UUID SEQUENCE = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();
    private static final UUID FOREIGN_TASK = UUID.randomUUID();

    private OwnershipService ownershipService;
    private TaskSubmissionService taskSubmissionService;
    private ClassService service;

    @BeforeEach
    void setUp() {
        ClassRepository classRepository = mock(ClassRepository.class);
        ownershipService = mock(OwnershipService.class);
        taskSubmissionService = mock(TaskSubmissionService.class);
        WorksheetService worksheetService = mock(WorksheetService.class);
        TaskPersistenceService taskPersistenceService = mock(TaskPersistenceService.class);

        service = new ClassService(
                classRepository,
                mock(UserRepository.class),
                ownershipService,
                mock(TokenService.class),
                mock(FeedbackLogService.class),
                mock(TempPasswordGenerator.class),
                mock(PasswordHasherService.class),
                worksheetService,
                taskPersistenceService,
                taskSubmissionService);

        when(classRepository.findByIdWithDetails(CLASS_ID)).thenReturn(Optional.of(ClassDTO.builder()
                .id(CLASS_ID)
                .ownerId(OWNER)
                .students(List.of(ClassDTO.StudentInfo.builder()
                        .userId(MEMBER).firstName("Anna").lastName("Müller").build()))
                .worksheets(List.of(ClassDTO.WorksheetInfo.builder()
                        .worksheetId(WORKSHEET).name("Blatt 1").build()))
                .build()));

        when(ownershipService.getCurrentUserId()).thenReturn(OWNER);
        when(ownershipService.isOwnerOrAdmin(OWNER)).thenReturn(true);

        TieredTaskSequenceDTO stub = new TieredTaskSequenceDTO();
        stub.setId(SEQUENCE);
        WorksheetDTO worksheet = new WorksheetDTO();
        worksheet.setId(WORKSHEET);
        worksheet.setSequenceDTOS(List.of(stub));
        when(worksheetService.loadWorksheetById(WORKSHEET)).thenReturn(worksheet);

        TaskDTO task = new TaskDTO();
        task.setId(TASK);
        task.setStatement("Berechne die Fläche");
        task.setSolution("base = 8 #swap:init\nreturn base");
        task.setTierLevel(1);

        TieredTaskSequenceDTO sequence = new TieredTaskSequenceDTO();
        sequence.setId(SEQUENCE);
        sequence.setProgrammingLanguage("python");
        sequence.setTasks(List.of(task));
        when(taskPersistenceService.loadSequenceById(SEQUENCE)).thenReturn(sequence);
    }

    @Nested
    @DisplayName("who may look")
    class Access {

        @Test
        @DisplayName("someone who does not own the class is refused")
        void nonOwnerIsRefused() {
            when(ownershipService.getCurrentUserId()).thenReturn(OUTSIDER);
            when(ownershipService.isOwnerOrAdmin(OWNER)).thenReturn(false);

            assertThrows(AccessDeniedException.class,
                    () -> service.getStudentTaskState(CLASS_ID, MEMBER, TASK));
            verify(taskSubmissionService, never()).find(any(), any());
        }

        @Test
        @DisplayName("a signed-out caller is refused")
        void anonymousIsRefused() {
            when(ownershipService.getCurrentUserId()).thenReturn(null);

            assertThrows(AccessDeniedException.class,
                    () -> service.getStudentTaskState(CLASS_ID, MEMBER, TASK));
        }

        @Test
        @DisplayName("a student who has left the class is refused, as on the progress page")
        void formerStudentIsRefused() {
            assertThrows(DomainValidationException.class,
                    () -> service.getStudentTaskState(CLASS_ID, FORMER, TASK));
            verify(taskSubmissionService, never()).find(any(), any());
        }

        @Test
        @DisplayName("a task from outside this class's worksheets is refused")
        void foreignTaskIsRefused() {
            // The check this feature adds. The arrangement is keyed by student and task alone,
            // so without it owning any class would be a licence to read any task's work.
            assertThrows(AccessDeniedException.class,
                    () -> service.getStudentTaskState(CLASS_ID, MEMBER, FOREIGN_TASK));
            verify(taskSubmissionService, never()).find(any(), any());
        }
    }

    @Nested
    @DisplayName("what comes back")
    class Payload {

        @Test
        @DisplayName("the student's arrangement, with the solution beside it")
        void returnsTheArrangement() {
            when(taskSubmissionService.find(MEMBER, TASK)).thenReturn(Optional.of(
                    TaskSubmissionDTO.builder()
                            .userId(MEMBER)
                            .taskId(TASK)
                            .blockOrder(List.of("base = 8", "return base"))
                            .indentLevels(List.of(0, 1))
                            .solved(true)
                            .build()));

            StudentTaskStateDTO state = service.getStudentTaskState(CLASS_ID, MEMBER, TASK);

            assertAll(
                    () -> assertTrue(state.hasState()),
                    () -> assertEquals(List.of("base = 8", "return base"), state.blockOrder()),
                    () -> assertEquals(List.of(0, 1), state.indentLevels()),
                    () -> assertTrue(state.solved()),
                    () -> assertEquals("python", state.programmingLanguage()),
                    () -> assertEquals("base = 8 #swap:init\nreturn base", state.solution()),
                    () -> assertEquals("Berechne die Fläche", state.statement()));
        }

        @Test
        @DisplayName("a task the student never touched still returns the solution, with hasState false")
        void returnsSolutionWhenNothingStored() {
            // Not a 404: the teacher asked about a task, and "they have not started" is an
            // answer worth rendering rather than an error worth showing.
            when(taskSubmissionService.find(MEMBER, TASK)).thenReturn(Optional.empty());

            StudentTaskStateDTO state = service.getStudentTaskState(CLASS_ID, MEMBER, TASK);

            assertAll(
                    () -> assertFalse(state.hasState()),
                    () -> assertEquals(List.of(), state.blockOrder()),
                    () -> assertEquals(List.of(), state.indentLevels()),
                    () -> assertFalse(state.solved()),
                    () -> assertEquals("base = 8 #swap:init\nreturn base", state.solution()));
        }
    }
}
