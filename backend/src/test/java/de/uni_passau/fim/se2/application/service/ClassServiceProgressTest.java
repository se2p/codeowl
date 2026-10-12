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
import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The class progress table, and which of its rows lead to a student's detail page.
 *
 * feedback_logs.user_id has no foreign key, so a student's clicks stay in the
 * class after the student is deleted or removed from it. Every row the table
 * returns with a userId is a link, and the detail page only answers for current
 * members — so a former student must come back without one.
 */
class ClassServiceProgressTest {

    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();

    private FeedbackLogService feedbackLogService;
    private ClassService service;

    @BeforeEach
    void setUp() {
        ClassRepository classRepository = mock(ClassRepository.class);
        OwnershipService ownershipService = mock(OwnershipService.class);
        feedbackLogService = mock(FeedbackLogService.class);
        service = new ClassService(
                classRepository,
                mock(UserRepository.class),
                ownershipService,
                mock(TokenService.class),
                feedbackLogService,
                mock(TempPasswordGenerator.class),
                mock(PasswordHasherService.class),
                mock(WorksheetService.class),
                mock(TaskPersistenceService.class),
                mock(TaskSubmissionService.class));

        when(classRepository.findByIdWithDetails(CLASS_ID)).thenReturn(Optional.of(ClassDTO.builder()
                .id(CLASS_ID)
                .ownerId(OWNER)
                .students(List.of(ClassDTO.StudentInfo.builder()
                        .userId(MEMBER).firstName("Anna").lastName("Müller").build()))
                .worksheets(List.of())
                .build()));
        when(ownershipService.getCurrentUserId()).thenReturn(OWNER);
        when(ownershipService.isOwnerOrAdmin(OWNER)).thenReturn(true);

        UUID task = UUID.randomUUID();
        when(feedbackLogService.getFeedbackLogsForClass(CLASS_ID)).thenReturn(List.of(
                click(MEMBER, task, true),
                click(FORMER, task, false),
                click(FORMER, task, true),
                click(null, task, false)));
    }

    private static FeedbackLogDTO click(UUID userId, UUID taskId, boolean correct) {
        return FeedbackLogDTO.builder()
                .id(UUID.randomUUID()).userId(userId).taskId(taskId).classId(CLASS_ID).correct(correct).build();
    }

    private Map<String, Object> rowNamed(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("studentName"))).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a current member's row carries their id, so it links to their details")
    void memberRowIsLinkable() {
        Map<String, Object> row = rowNamed(service.getClassProgress(CLASS_ID), "Anna Müller");

        assertEquals(MEMBER.toString(), row.get("userId"));
    }

    @Test
    @DisplayName("a former student's clicks still count, but their row has no id to link to")
    void formerStudentRowIsNotLinkable() {
        List<Map<String, Object>> rows = service.getClassProgress(CLASS_ID);
        Map<String, Object> row = rowNamed(rows, "Ehemaliger Schüler");

        assertAll(
                () -> assertNull(row.get("userId")),
                () -> assertEquals(2, row.get("totalAttempts")),
                () -> assertEquals(1L, row.get("correctAttempts")),
                () -> assertEquals(3, rows.size()));
    }

    @Test
    @DisplayName("anonymous clicks keep their own row, also without an id")
    void anonymousRowIsNotLinkable() {
        Map<String, Object> row = rowNamed(service.getClassProgress(CLASS_ID), "Unangemeldet");

        assertNull(row.get("userId"));
    }

    @Test
    @DisplayName("the detail view still refuses anyone who is not a member")
    void detailRefusesFormerStudent() {
        assertThrows(DomainValidationException.class, () -> service.getStudentProgressDetail(CLASS_ID, FORMER));
    }
}
