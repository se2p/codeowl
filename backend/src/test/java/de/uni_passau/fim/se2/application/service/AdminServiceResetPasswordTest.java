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
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An admin triggering a password reset, which a FIM-Account does not have: its
 * password is reset at the FIM.
 */
class AdminServiceResetPasswordTest {

    private static final String EMAIL = "max.mustermann@uni-passau.de";

    private UserRepository userRepository;
    private TokenService tokenService;
    private EmailService emailService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenService = mock(TokenService.class);
        emailService = mock(EmailService.class);
        service = new AdminService(userRepository, tokenService, emailService);
    }

    @Test
    @DisplayName("no reset mail is sent for a FIM account")
    void refusesFimAccount() {
        UserDTO fim = user("mustermann");
        when(userRepository.findById(fim.getUserId())).thenReturn(Optional.of(fim));

        assertThrows(AuthenticationException.class, () -> service.resetPassword(fim.getUserId()));

        verify(tokenService, never()).createPasswordResetToken(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("an ordinary account still gets its reset mail")
    void sendsMailToOrdinaryAccount() {
        UserDTO ordinary = user(null);
        when(userRepository.findById(ordinary.getUserId())).thenReturn(Optional.of(ordinary));
        when(tokenService.createPasswordResetToken(ordinary.getUserId())).thenReturn(
                AuthTokenDTO.builder().userId(ordinary.getUserId()).tokenValue("reset-token").build());

        service.resetPassword(ordinary.getUserId());

        verify(emailService).sendPasswordResetEmail(EMAIL, "reset-token");
    }

    @Test
    @DisplayName("the user list tells the admin page which users are FIM accounts")
    void listFlagsFimAccounts() {
        UserDTO fim = user("mustermann");
        UserDTO ordinary = user(null);
        when(userRepository.findAllPaginated(any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(fim, ordinary));
        when(userRepository.countAll(any())).thenReturn(2L);

        var page = service.getUsers("", 0, 20, "email", "asc");

        assertTrue(page.content().get(0).fimAccount());
        assertFalse(page.content().get(1).fimAccount());
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
