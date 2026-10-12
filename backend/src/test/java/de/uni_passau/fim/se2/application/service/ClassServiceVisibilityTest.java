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
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a student is allowed to learn about a class from the overview.
 *
 * The overview now carries deadlines, which means it carries the assignments
 * they belong to — so the same filter the detail view applies has to apply
 * here. Without it the list would name worksheets a teacher has not released.
 */
class ClassServiceVisibilityTest {

    private ClassRepository classRepository;
    private OwnershipService ownershipService;
    private ClassService service;

    private static final UUID STUDENT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        classRepository = mock(ClassRepository.class);
        ownershipService = mock(OwnershipService.class);
        service = new ClassService(
                classRepository,
                mock(UserRepository.class),
                ownershipService,
                mock(TokenService.class),
                mock(FeedbackLogService.class),
                mock(TempPasswordGenerator.class),
                mock(PasswordHasherService.class),
                mock(WorksheetService.class),
                mock(TaskPersistenceService.class),
                mock(TaskSubmissionService.class));
    }

    private static ClassDTO.WorksheetInfo worksheet(String name, WorksheetAvailability availability) {
        return ClassDTO.WorksheetInfo.builder()
                .worksheetId(UUID.randomUUID())
                .name(name)
                .description("")
                .availability(availability)
                .build();
    }

    private static ClassDTO classWith(ClassDTO.WorksheetInfo... worksheets) {
        return ClassDTO.builder()
                .id(UUID.randomUUID())
                .name("5b")
                .description("")
                .ownerId(UUID.randomUUID())
                .worksheets(new ArrayList<>(List.of(worksheets)))
                .build();
    }

    private void asStudent() {
        when(ownershipService.getCurrentUserId()).thenReturn(STUDENT);
        when(ownershipService.isStudent()).thenReturn(true);
        when(ownershipService.isAdmin()).thenReturn(false);
    }

    private void asTeacher() {
        when(ownershipService.getCurrentUserId()).thenReturn(STUDENT);
        when(ownershipService.isStudent()).thenReturn(false);
        when(ownershipService.isAdmin()).thenReturn(false);
    }

    @Test
    @DisplayName("the overview hides a worksheet that has not been released")
    void overviewHidesUnreleased() {
        asStudent();
        when(classRepository.findByStudentId(STUDENT)).thenReturn(new ArrayList<>(List.of(
                classWith(worksheet("Ready", WorksheetAvailability.OPEN),
                          worksheet("Still being written", WorksheetAvailability.SCHEDULED)))));

        List<ClassDTO> classes = service.getMyClasses();

        List<String> names = classes.get(0).getWorksheets().stream()
                .map(ClassDTO.WorksheetInfo::getName).toList();
        assertAll(
                () -> assertEquals(List.of("Ready"), names),
                // Not even the name may leak: it would announce what is coming.
                () -> assertTrue(names.stream().noneMatch(n -> n.contains("Still"))));
    }

    @Test
    @DisplayName("the overview keeps a worksheet whose deadline has passed")
    void overviewKeepsClosed() {
        // Closed is still readable, and seeing that the deadline has gone is
        // exactly what the student needs from the overview.
        asStudent();
        when(classRepository.findByStudentId(STUDENT)).thenReturn(new ArrayList<>(List.of(
                classWith(worksheet("Handed in last week", WorksheetAvailability.CLOSED)))));

        List<ClassDTO> classes = service.getMyClasses();

        assertEquals(1, classes.get(0).getWorksheets().size());
    }

    @Test
    @DisplayName("a teacher's own overview keeps every assignment")
    void teacherSeesEverything() {
        asTeacher();
        when(classRepository.findByOwnerId(any())).thenReturn(new ArrayList<>(List.of(
                classWith(worksheet("Ready", WorksheetAvailability.OPEN),
                          worksheet("Still being written", WorksheetAvailability.SCHEDULED)))));

        List<ClassDTO> classes = service.getMyClasses();

        // The teacher has to see what they have not published yet, or they
        // could not publish it.
        assertEquals(2, classes.get(0).getWorksheets().size());
    }

    @Test
    @DisplayName("a class whose assignments were never loaded is left alone")
    void toleratesMissingWorksheets() {
        // findByStudentId fills these in, but a null list must not throw and
        // take the whole overview down with it.
        asStudent();
        ClassDTO withoutWorksheets = ClassDTO.builder()
                .id(UUID.randomUUID()).name("5b").description("").ownerId(UUID.randomUUID())
                .build();
        when(classRepository.findByStudentId(STUDENT))
                .thenReturn(new ArrayList<>(List.of(withoutWorksheets)));

        assertEquals(1, service.getMyClasses().size());
    }
}
