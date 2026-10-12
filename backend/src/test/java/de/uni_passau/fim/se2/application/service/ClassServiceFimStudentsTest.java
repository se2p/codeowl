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
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adding students to a class by FIM username.
 *
 * The teacher does this before the school year starts, when most students have
 * never logged in, so the accounts have to exist up front and be the very ones
 * the students' first FIM login later finds.
 */
class ClassServiceFimStudentsTest {

    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    private ClassRepository classRepository;
    private UserRepository userRepository;
    private OwnershipService ownershipService;
    private ClassService service;

    @BeforeEach
    void setUp() {
        classRepository = mock(ClassRepository.class);
        userRepository = mock(UserRepository.class);
        ownershipService = mock(OwnershipService.class);
        service = new ClassService(
                classRepository,
                userRepository,
                ownershipService,
                mock(TokenService.class),
                mock(FeedbackLogService.class),
                mock(TempPasswordGenerator.class),
                mock(PasswordHasherService.class),
                mock(WorksheetService.class),
                mock(TaskPersistenceService.class),
                mock(TaskSubmissionService.class));

        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(ClassDTO.builder()
                .id(CLASS_ID)
                .name("Programmierung I")
                .description("")
                .ownerId(OWNER)
                .build()));
        when(userRepository.findByFimUid(anyString())).thenReturn(Optional.empty());
        // Saving is what hands the new user its id, the way the DAO does.
        when(userRepository.save(any(UserDTO.class))).thenAnswer(invocation -> {
            UserDTO user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            return user;
        });
    }

    @Test
    @DisplayName("a student who never logged in gets a FIM-linked student account and joins the class")
    void createsAccountForUnknownUsername() {
        List<ClassDTO.StudentInfo> added = service.addFimStudents(CLASS_ID, List.of("mustermann"));

        ArgumentCaptor<UserDTO> saved = ArgumentCaptor.forClass(UserDTO.class);
        verify(userRepository).save(saved.capture());
        UserDTO user = saved.getValue();
        assertAll(
                () -> assertEquals("mustermann", user.getFimUid()),
                () -> assertEquals(UserDTO.UserRole.STUDENT, user.getRole()),
                // The name is a placeholder until the first login fills it in.
                () -> assertEquals("mustermann", user.getFirstName()),
                () -> assertEquals("", user.getLastName()),
                () -> assertNull(user.getEmail()),
                () -> assertNull(user.getPasswordHash()),
                () -> assertTrue(user.isActive()),
                () -> assertEquals(1, added.size()),
                () -> assertEquals("mustermann", added.get(0).getFimUid()),
                () -> assertNull(added.get(0).getInviteToken()));
        verify(classRepository).addStudent(CLASS_ID, user.getUserId());
    }

    @Test
    @DisplayName("a student who already logged in is added with their existing account")
    void reusesExistingFimStudent() {
        UserDTO existing = fimUser("mustermann", UserDTO.UserRole.STUDENT);
        when(userRepository.findByFimUid("mustermann")).thenReturn(Optional.of(existing));

        List<ClassDTO.StudentInfo> added = service.addFimStudents(CLASS_ID, List.of("mustermann"));

        verify(userRepository, never()).save(any());
        verify(classRepository).addStudent(CLASS_ID, existing.getUserId());
        assertAll(
                () -> assertEquals("Max", added.get(0).getFirstName()),
                () -> assertEquals(existing.getEmail(), added.get(0).getEmail()));
    }

    @Test
    @DisplayName("usernames are matched however the teacher capitalised or padded them")
    void normalisesUsernames() {
        service.addFimStudents(CLASS_ID, List.of("  MusterMann "));

        verify(userRepository).findByFimUid("mustermann");
        ArgumentCaptor<UserDTO> saved = ArgumentCaptor.forClass(UserDTO.class);
        verify(userRepository).save(saved.capture());
        assertEquals("mustermann", saved.getValue().getFimUid());
    }

    @Test
    @DisplayName("a username listed twice is added once")
    void collapsesDuplicates() {
        List<ClassDTO.StudentInfo> added = service.addFimStudents(CLASS_ID, List.of("mustermann", "Mustermann", "musterfrau"));

        assertEquals(2, added.size());
        verify(userRepository, times(2)).save(any());
        verify(classRepository, times(2)).addStudent(eq(CLASS_ID), any(UUID.class));
    }

    @Test
    @DisplayName("one invalid username rejects the whole list before any account exists")
    void rejectsInvalidUsernameUpFront() {
        DomainValidationException e = assertThrows(DomainValidationException.class,
                () -> service.addFimStudents(CLASS_ID, List.of("mustermann", "max.mustermann@uni-passau.de", "musterfrau")));

        assertTrue(e.getMessage().contains("max.mustermann@uni-passau.de"), e.getMessage());
        verify(userRepository, never()).save(any());
        verify(classRepository, never()).addStudent(any(), any());
    }

    @Test
    @DisplayName("names, blanks and other things that are not usernames are refused")
    void rejectsThingsThatAreNotUsernames() {
        for (String notAUsername : new String[] { "", "   ", "Max Mustermann", "-mustermann", "a".repeat(65) }) {
            assertThrows(DomainValidationException.class,
                    () -> service.addFimStudents(CLASS_ID, List.of(notAUsername)), notAUsername);
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a FIM-Account that belongs to a teacher is not added as a student")
    void rejectsTeacherAccount() {
        when(userRepository.findByFimUid("lehrer")).thenReturn(Optional.of(fimUser("lehrer", UserDTO.UserRole.TEACHER)));

        assertThrows(DomainValidationException.class,
                () -> service.addFimStudents(CLASS_ID, List.of("mustermann", "lehrer")));
        verify(userRepository, never()).save(any());
        verify(classRepository, never()).addStudent(any(), any());
    }

    @Test
    @DisplayName("an empty list and an oversized one are refused")
    void rejectsEmptyAndOversizedLists() {
        assertThrows(DomainValidationException.class, () -> service.addFimStudents(CLASS_ID, List.of()));
        assertThrows(DomainValidationException.class, () -> service.addFimStudents(CLASS_ID, null));
        assertThrows(DomainValidationException.class,
                () -> service.addFimStudents(CLASS_ID, Collections.nCopies(201, "mustermann")));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("only the owner or an admin may add students")
    void requiresOwnership() {
        doThrow(new AccessDeniedException("not yours")).when(ownershipService).checkOwnership(OWNER, "class");

        assertThrows(AccessDeniedException.class, () -> service.addFimStudents(CLASS_ID, List.of("mustermann")));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a FIM student gets no temporary password beside their FIM password")
    void refusesTempPasswordForFimStudent() {
        UserDTO student = fimUser("mustermann", UserDTO.UserRole.STUDENT);
        when(classRepository.findById(CLASS_ID)).thenReturn(Optional.of(ClassDTO.builder()
                .id(CLASS_ID)
                .ownerId(OWNER)
                .students(List.of(ClassDTO.StudentInfo.builder().userId(student.getUserId()).build()))
                .build()));
        when(userRepository.findById(student.getUserId())).thenReturn(Optional.of(student));

        assertThrows(DomainValidationException.class,
                () -> service.resetTempPassword(CLASS_ID, student.getUserId(), "de"));
        verify(userRepository, never()).update(any());
    }

    private static UserDTO fimUser(String uid, UserDTO.UserRole role) {
        return UserDTO.builder()
                .userId(UUID.randomUUID())
                .firstName("Max")
                .lastName("Mustermann")
                .email(uid + "@uni-passau.de")
                .fimUid(uid)
                .role(role)
                .verified(true)
                .active(true)
                .build();
    }
}
