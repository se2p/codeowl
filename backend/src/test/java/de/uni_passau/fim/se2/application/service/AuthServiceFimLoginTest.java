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
import de.uni_passau.fim.se2.domain.interfaces.FimAccountDirectory;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import de.uni_passau.fim.se2.domain.model.FimAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Logging in with a FIM-Account.
 *
 * The LDAP server decides whether the password is right; what is worth pinning is
 * which CodeOwl account a valid FIM-Account ends up in, since a wrong link hands one
 * person's classes and worksheets to another.
 */
class AuthServiceFimLoginTest {

    private static final FimAccount ACCOUNT =
            new FimAccount("mustermann", "max.mustermann@uni-passau.de", "Max", "Mustermann");

    private UserRepository userRepository;
    private FimAccountDirectory directory;
    private JwtService jwtService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        directory = mock(FimAccountDirectory.class);
        jwtService = mock(JwtService.class);
        service = new AuthService(
                userRepository,
                mock(PasswordHasherService.class),
                jwtService,
                mock(TokenService.class),
                mock(EmailService.class),
                directory,
                ""
        );
        when(jwtService.generateToken(any(UserDTO.class))).thenReturn("jwt");
        when(userRepository.findByFimUid(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("wrong FIM credentials are rejected without touching any account")
    void rejectsWrongCredentials() {
        when(directory.authenticate("mustermann", "wrong")).thenReturn(Optional.empty());

        assertThrows(AuthenticationException.class, () -> service.loginWithFimAccount("mustermann", "wrong"));
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("first login creates a verified student account without a password")
    void firstLoginCreatesStudent() {
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));

        assertEquals("jwt", service.loginWithFimAccount("mustermann", "secret"));

        ArgumentCaptor<UserDTO> saved = ArgumentCaptor.forClass(UserDTO.class);
        verify(userRepository).save(saved.capture());
        UserDTO user = saved.getValue();
        assertAll(
                () -> assertEquals("mustermann", user.getFimUid()),
                () -> assertEquals("max.mustermann@uni-passau.de", user.getEmail()),
                () -> assertEquals("Max", user.getFirstName()),
                () -> assertEquals("Mustermann", user.getLastName()),
                () -> assertEquals(UserDTO.UserRole.STUDENT, user.getRole()),
                () -> assertTrue(user.isVerified()),
                () -> assertNull(user.getPasswordHash())
        );
    }

    @Test
    @DisplayName("a later login reuses the linked account, even if its email has changed")
    void reusesLinkedAccount() {
        UserDTO linked = user(true, "mustermann");
        linked.setEmail("changed@example.org");
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByFimUid("mustermann")).thenReturn(Optional.of(linked));

        service.loginWithFimAccount("mustermann", "secret");

        verify(jwtService).generateToken(linked);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("the first login of a student a teacher added up front fills in name and email")
    void completesAccountAddedByTeacher() {
        UserDTO placeholder = user(true, "mustermann");
        placeholder.setFirstName("mustermann");
        placeholder.setLastName("");
        placeholder.setEmail(null);
        placeholder.setPasswordHash(null);
        placeholder.setRole(UserDTO.UserRole.STUDENT);
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByFimUid("mustermann")).thenReturn(Optional.of(placeholder));

        service.loginWithFimAccount("mustermann", "secret");

        assertAll(
                () -> assertEquals("Max", placeholder.getFirstName()),
                () -> assertEquals("Mustermann", placeholder.getLastName()),
                () -> assertEquals("max.mustermann@uni-passau.de", placeholder.getEmail()),
                () -> assertEquals(UserDTO.UserRole.STUDENT, placeholder.getRole()));
        verify(userRepository).update(placeholder);
        verify(userRepository, never()).save(any());
        verify(jwtService).generateToken(placeholder);
    }

    @Test
    @DisplayName("a name someone has edited is not overwritten, and a taken email is not claimed")
    void keepsEditedNameAndTakenEmail() {
        UserDTO student = user(true, "mustermann");
        student.setFirstName("Maximilian");
        student.setLastName("");
        student.setEmail(null);
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByFimUid("mustermann")).thenReturn(Optional.of(student));
        when(userRepository.existsByEmail(ACCOUNT.email())).thenReturn(true);

        service.loginWithFimAccount("mustermann", "secret");

        assertAll(
                () -> assertEquals("Maximilian", student.getFirstName()),
                () -> assertNull(student.getEmail()));
        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("a verified account with the same email is linked instead of duplicated, keeping its role")
    void linksVerifiedAccountWithSameEmail() {
        UserDTO existing = user(true, null);
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByEmail(ACCOUNT.email())).thenReturn(Optional.of(existing));

        service.loginWithFimAccount("mustermann", "secret");

        assertEquals("mustermann", existing.getFimUid());
        assertEquals(UserDTO.UserRole.TEACHER, existing.getRole());
        verify(userRepository).update(existing);
        verify(userRepository, never()).save(any());
        verify(jwtService).generateToken(existing);
    }

    @Test
    @DisplayName("an unverified account with the same email is not linked: whoever registered it knows its password")
    void refusesUnverifiedAccountWithSameEmail() {
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByEmail(ACCOUNT.email())).thenReturn(Optional.of(user(false, null)));

        assertThrows(AuthenticationException.class, () -> service.loginWithFimAccount("mustermann", "secret"));
        verify(userRepository, never()).update(any());
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("an account already linked to another FIM-Account is not re-linked")
    void refusesAccountLinkedToOtherFimAccount() {
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByEmail(ACCOUNT.email())).thenReturn(Optional.of(user(true, "someoneelse")));

        assertThrows(AuthenticationException.class, () -> service.loginWithFimAccount("mustermann", "secret"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("a deactivated account cannot log in with its FIM-Account")
    void refusesDeactivatedAccount() {
        UserDTO linked = user(true, "mustermann");
        linked.setActive(false);
        when(directory.authenticate("mustermann", "secret")).thenReturn(Optional.of(ACCOUNT));
        when(userRepository.findByFimUid("mustermann")).thenReturn(Optional.of(linked));

        assertThrows(AuthenticationException.class, () -> service.loginWithFimAccount("mustermann", "secret"));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("missing name attributes fall back to the uid instead of violating NOT NULL")
    void fallsBackWhenDirectoryLacksNames() {
        when(directory.authenticate("mustermann", "secret"))
                .thenReturn(Optional.of(new FimAccount("mustermann", null, null, null)));

        service.loginWithFimAccount("mustermann", "secret");

        ArgumentCaptor<UserDTO> saved = ArgumentCaptor.forClass(UserDTO.class);
        verify(userRepository).save(saved.capture());
        assertAll(
                () -> assertEquals("mustermann", saved.getValue().getFirstName()),
                () -> assertEquals("", saved.getValue().getLastName()),
                () -> assertNull(saved.getValue().getEmail())
        );
    }

    private static UserDTO user(boolean verified, String fimUid) {
        return UserDTO.builder()
                .userId(UUID.randomUUID())
                .email(ACCOUNT.email())
                .firstName("Max")
                .lastName("Mustermann")
                .passwordHash("hash")
                .role(UserDTO.UserRole.TEACHER)
                .verified(verified)
                .active(true)
                .fimUid(fimUid)
                .build();
    }
}
