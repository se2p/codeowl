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

import de.uni_passau.fim.se2.domain.entities.AuthTokenDTO;
import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Importing a class list at once.
 *
 * Each row is a real account that no single action undoes, so what this accepts
 * and what it refuses is worth pinning: a file with one bad line must create
 * nothing at all, and a file that is simply the wrong file must not create
 * hundreds of students before anyone reads the screen.
 */
class ClassServiceBulkStudentsTest {

    private ClassRepository classRepository;
    private UserRepository userRepository;
    private OwnershipService ownershipService;
    private TokenService tokenService;
    private ClassService service;

    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        classRepository = mock(ClassRepository.class);
        userRepository = mock(UserRepository.class);
        ownershipService = mock(OwnershipService.class);
        tokenService = mock(TokenService.class);
        service = new ClassService(
                classRepository,
                userRepository,
                ownershipService,
                tokenService,
                mock(FeedbackLogService.class),
                mock(TempPasswordGenerator.class),
                mock(PasswordHasherService.class),
                mock(WorksheetService.class),
                mock(TaskPersistenceService.class),
                mock(TaskSubmissionService.class));

        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(ClassDTO.builder()
                .id(CLASS_ID)
                .name("7a")
                .description("")
                .ownerId(OWNER)
                .build()));

        // Saving is what hands the new user its id, the way the DAO does.
        when(userRepository.save(any(UserDTO.class))).thenAnswer(invocation -> {
            UserDTO user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            return user;
        });

        when(tokenService.createStudentInviteToken(any(UUID.class))).thenAnswer(invocation ->
                AuthTokenDTO.builder()
                        .userId(invocation.getArgument(0))
                        .tokenValue("invite-" + invocation.getArgument(0))
                        .build());
    }

    private static List<ClassService.NewStudent> roster(String... names) {
        List<ClassService.NewStudent> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i += 2) {
            rows.add(new ClassService.NewStudent(names[i], names[i + 1]));
        }
        return rows;
    }

    @Test
    @DisplayName("creates one student per row, each with an invite token")
    void createsEveryRow() {
        List<ClassDTO.StudentInfo> created = service.createStudents(CLASS_ID,
                roster("Anna", "Müller", "Ben", "Weber"));

        assertAll(
                () -> assertEquals(2, created.size()),
                () -> assertEquals("Anna", created.get(0).getFirstName()),
                () -> assertEquals("Müller", created.get(0).getLastName()),
                () -> assertEquals("Weber", created.get(1).getLastName()),
                // The token is the whole point: without it the teacher has an
                // account nobody can claim.
                () -> assertTrue(created.stream().allMatch(s -> s.getInviteToken() != null)),
                () -> assertTrue(created.stream().allMatch(s -> s.getEmail() == null)));

        verify(classRepository, org.mockito.Mockito.times(2)).addStudent(eq(CLASS_ID), any(UUID.class));
    }

    @Test
    @DisplayName("trims the whitespace a spreadsheet leaves around a name")
    void trimsNames() {
        List<ClassDTO.StudentInfo> created = service.createStudents(CLASS_ID, roster("  Anna ", " Müller"));
        assertAll(
                () -> assertEquals("Anna", created.get(0).getFirstName()),
                () -> assertEquals("Müller", created.get(0).getLastName()));
    }

    @Test
    @DisplayName("keeps two students who happen to share a name")
    void allowsDuplicateNames() {
        // Real classes contain them, and refusing the second would be wrong
        // more often than it would be right.
        assertEquals(2, service.createStudents(CLASS_ID, roster("Anna", "Müller", "Anna", "Müller")).size());
    }

    @Test
    @DisplayName("rejects the whole import when one row has no name, naming the row")
    void rejectsBlankName() {
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> service.createStudents(CLASS_ID, roster("Anna", "Müller", "Ben", "  ")));

        assertAll(
                () -> assertTrue(e.getMessage().contains("Zeile 2"), e.getMessage()),
                // Nothing at all: a half-imported class is harder to repair
                // than one that never arrived.
                () -> verify(userRepository, never()).save(any(UserDTO.class)));
    }

    @Test
    @DisplayName("rejects a name longer than the column can hold")
    void rejectsOverlongName() {
        // varchar(100) — letting it through surfaces as a 500 from Postgres
        // after some of the class has already been created.
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> service.createStudents(CLASS_ID, roster("Anna", "M".repeat(101))));

        assertAll(
                () -> assertTrue(e.getMessage().contains("Zeile 1"), e.getMessage()),
                () -> verify(userRepository, never()).save(any(UserDTO.class)));
    }

    @Test
    @DisplayName("accepts a name of exactly the maximum length")
    void acceptsNameAtLimit() {
        assertEquals(1, service.createStudents(CLASS_ID, roster("Anna", "M".repeat(100))).size());
    }

    @Test
    @DisplayName("refuses a file too large to be a class")
    void refusesOversizedImport() {
        List<ClassService.NewStudent> tooMany = IntStream.range(0, 201)
                .mapToObj(i -> new ClassService.NewStudent("Anna" + i, "Müller"))
                .toList();

        assertThrows(DomainValidationException.class, () -> service.createStudents(CLASS_ID, tooMany));
        verify(userRepository, never()).save(any(UserDTO.class));
    }

    @Test
    @DisplayName("accepts an import of exactly the maximum size")
    void acceptsImportAtLimit() {
        List<ClassService.NewStudent> full = IntStream.range(0, 200)
                .mapToObj(i -> new ClassService.NewStudent("Anna" + i, "Müller"))
                .toList();

        assertEquals(200, service.createStudents(CLASS_ID, full).size());
    }

    @Test
    @DisplayName("rejects an empty list rather than reporting a successful import of nothing")
    void rejectsEmptyList() {
        assertAll(
                () -> assertThrows(DomainValidationException.class,
                        () -> service.createStudents(CLASS_ID, List.of())),
                () -> assertThrows(DomainValidationException.class,
                        () -> service.createStudents(CLASS_ID, null)));
    }

    @Test
    @DisplayName("checks who is asking before creating anything")
    void checksOwnership() {
        // The bulk path is a new way into the same class, so it has to be
        // guarded like the single one; admins are let through by the same check.
        doThrow(new AccessDeniedException("not yours"))
                .when(ownershipService).checkOwnership(OWNER, "class");

        assertThrows(AccessDeniedException.class,
                () -> service.createStudents(CLASS_ID, roster("Anna", "Müller")));
        verify(userRepository, never()).save(any(UserDTO.class));
    }

    @Test
    @DisplayName("refuses a class that does not exist")
    void refusesUnknownClass() {
        UUID unknown = UUID.randomUUID();
        when(classRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(DomainValidationException.class,
                () -> service.createStudents(unknown, roster("Anna", "Müller")));
    }

    @Test
    @DisplayName("the single-student path still validates the same way")
    void singleStudentSharesValidation() {
        // Both paths run through one checker, so neither can quietly accept
        // what the other refuses.
        assertAll(
                () -> assertThrows(DomainValidationException.class,
                        () -> service.createStudent(CLASS_ID, "Anna", "  ")),
                () -> assertThrows(DomainValidationException.class,
                        () -> service.createStudent(CLASS_ID, null, "Müller")),
                () -> assertThrows(DomainValidationException.class,
                        () -> service.createStudent(CLASS_ID, "Anna", "M".repeat(101))),
                () -> assertEquals("Anna", service.createStudent(CLASS_ID, " Anna ", "Müller").getFirstName()));
    }
}
