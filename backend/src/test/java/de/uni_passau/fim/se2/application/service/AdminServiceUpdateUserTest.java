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

import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.AuthenticationException;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An admin editing a user, in particular promoting a FIM-Account, which starts out
 * as a student and may have no email at all.
 */
class AdminServiceUpdateUserTest {

    private UserRepository userRepository;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AdminService(userRepository, mock(TokenService.class), mock(EmailService.class));
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a FIM student can be promoted to teacher and stays linked to the FIM-Account")
    void promotesFimStudentToTeacher() {
        UserDTO student = fimStudent("max.mustermann@uni-passau.de");
        when(userRepository.findById(student.getUserId())).thenReturn(Optional.of(student));

        service.updateUser(student.getUserId(), "Max", "Mustermann", "max.mustermann@uni-passau.de", "TEACHER");

        assertAll(
                () -> assertEquals(UserDTO.UserRole.TEACHER, student.getRole()),
                () -> assertEquals("mustermann", student.getFimUid()),
                () -> assertEquals("max.mustermann@uni-passau.de", student.getEmail())
        );
        verify(userRepository).update(student);
    }

    @Test
    @DisplayName("a user without an email can be edited; the blank email the form sends is stored as null")
    void editsUserWithoutEmail() {
        UserDTO student = fimStudent(null);
        when(userRepository.findById(student.getUserId())).thenReturn(Optional.of(student));

        service.updateUser(student.getUserId(), "Max", "Mustermann", "", "ADMIN");

        assertEquals(UserDTO.UserRole.ADMIN, student.getRole());
        assertNull(student.getEmail());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository).update(student);
    }

    @Test
    @DisplayName("an email already used by another account is still refused")
    void refusesEmailOfAnotherAccount() {
        UserDTO student = fimStudent(null);
        UserDTO other = fimStudent("taken@uni-passau.de");
        when(userRepository.findById(student.getUserId())).thenReturn(Optional.of(student));
        when(userRepository.findByEmail("taken@uni-passau.de")).thenReturn(Optional.of(other));

        assertThrows(AuthenticationException.class, () ->
                service.updateUser(student.getUserId(), "Max", "Mustermann", "taken@uni-passau.de", "TEACHER"));
        verify(userRepository, never()).update(any());
    }

    private static UserDTO fimStudent(String email) {
        return UserDTO.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .firstName("Max")
                .lastName("Mustermann")
                .role(UserDTO.UserRole.STUDENT)
                .verified(true)
                .active(true)
                .fimUid("mustermann")
                .build();
    }
}
