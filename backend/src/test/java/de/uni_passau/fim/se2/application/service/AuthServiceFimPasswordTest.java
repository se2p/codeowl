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
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.AuthenticationException;
import de.uni_passau.fim.se2.domain.interfaces.FimAccountDirectory;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A FIM-Account's password lives in the FIM directory. Changing or resetting the
 * local hash would not change the one that logs the user in, so every way of
 * setting a password in CodeOwl is closed to such an account.
 */
class AuthServiceFimPasswordTest {

    private static final String EMAIL = "max.mustermann@uni-passau.de";

    private UserRepository userRepository;
    private PasswordHasherService hasher;
    private TokenService tokenService;
    private EmailService emailService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        hasher = mock(PasswordHasherService.class);
        tokenService = mock(TokenService.class);
        emailService = mock(EmailService.class);
        service = new AuthService(
                userRepository,
                hasher,
                mock(JwtService.class),
                tokenService,
                emailService,
                mock(FimAccountDirectory.class),
                ""
        );
        when(hasher.verifyPassword(anyString(), anyString())).thenReturn(true);
        when(hasher.hashPassword(anyString())).thenReturn("new-hash");
    }

    @Test
    @DisplayName("a FIM account cannot change its password, even with the right current one")
    void refusesPasswordChange() {
        UserDTO fim = user("mustermann");
        when(userRepository.findById(fim.getUserId())).thenReturn(Optional.of(fim));

        assertThrows(AuthenticationException.class,
                () -> service.updatePassword(fim.getUserId(), "hash", "another-password"));

        assertEquals("hash", fim.getPasswordHash());
        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("an ordinary account can still change its password")
    void allowsPasswordChangeForOrdinaryAccount() {
        UserDTO ordinary = user(null);
        when(userRepository.findById(ordinary.getUserId())).thenReturn(Optional.of(ordinary));

        service.updatePassword(ordinary.getUserId(), "hash", "another-password");

        assertEquals("new-hash", ordinary.getPasswordHash());
        verify(userRepository).update(ordinary);
    }

    @Test
    @DisplayName("forgot-password sends nothing to a FIM account")
    void sendsNoResetMailToFimAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user("mustermann")));

        service.requestPasswordReset(EMAIL);

        verify(tokenService, never()).createPasswordResetToken(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("forgot-password still sends the mail to an ordinary account")
    void sendsResetMailToOrdinaryAccount() {
        UserDTO ordinary = user(null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ordinary));
        when(tokenService.createPasswordResetToken(ordinary.getUserId()))
                .thenReturn(resetToken(ordinary.getUserId()));

        service.requestPasswordReset(EMAIL);

        verify(emailService).sendPasswordResetEmail(EMAIL, "reset-token");
    }

    @Test
    @DisplayName("a reset token issued before the account was linked cannot set a local password")
    void refusesResetWithOldToken() {
        UserDTO fim = user("mustermann");
        when(tokenService.validateAndConsume("reset-token", AuthTokenDTO.TokenType.PASSWORD_RESET))
                .thenReturn(resetToken(fim.getUserId()));
        when(userRepository.findById(fim.getUserId())).thenReturn(Optional.of(fim));

        assertThrows(AuthenticationException.class,
                () -> service.resetPassword("reset-token", "another-password"));

        assertEquals("hash", fim.getPasswordHash());
        verify(userRepository, never()).update(any());
    }

    @Test
    @DisplayName("a reset token still works for an ordinary account")
    void allowsResetForOrdinaryAccount() {
        UserDTO ordinary = user(null);
        when(tokenService.validateAndConsume("reset-token", AuthTokenDTO.TokenType.PASSWORD_RESET))
                .thenReturn(resetToken(ordinary.getUserId()));
        when(userRepository.findById(ordinary.getUserId())).thenReturn(Optional.of(ordinary));

        service.resetPassword("reset-token", "another-password");

        assertEquals("new-hash", ordinary.getPasswordHash());
        verify(userRepository).update(ordinary);
    }

    @Test
    @DisplayName("a FIM account cannot set a password through the temporary-password route either")
    void refusesTempPasswordChange() {
        UserDTO fim = user("mustermann");
        fim.setMustChangePassword(true);
        when(userRepository.findById(fim.getUserId())).thenReturn(Optional.of(fim));

        assertThrows(AuthenticationException.class,
                () -> service.changeTempPassword(fim.getUserId(), "another-password"));

        assertEquals("hash", fim.getPasswordHash());
        verify(userRepository, never()).update(any());
    }

    private static AuthTokenDTO resetToken(UUID userId) {
        return AuthTokenDTO.builder()
                .tokenId(UUID.randomUUID())
                .userId(userId)
                .tokenValue("reset-token")
                .tokenType(AuthTokenDTO.TokenType.PASSWORD_RESET)
                .build();
    }

    private static UserDTO user(String fimUid) {
        return UserDTO.builder()
                .userId(UUID.randomUUID())
                .email(EMAIL)
                .firstName("Max")
                .lastName("Mustermann")
                .passwordHash("hash")
                .role(UserDTO.UserRole.TEACHER)
                .verified(true)
                .active(true)
                .fimUid(fimUid)
                .build();
    }
}
