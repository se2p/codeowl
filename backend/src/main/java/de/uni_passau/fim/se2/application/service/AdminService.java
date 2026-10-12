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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    /**
     * Returns a paginated, searchable, sortable list of users.
     */
    public PaginatedUsersResponse getUsers(String search, int page, int size, String sort, String dir) {
        // Map frontend sort field names to DB column names
        String sortColumn = switch (sort) {
            case "firstName" -> "first_name";
            case "lastName" -> "last_name";
            case "role" -> "role";
            case "verified" -> "is_verified";
            case "createdAt" -> "created_at";
            default -> "email";
        };

        int offset = page * size;
        List<UserDTO> users = userRepository.findAllPaginated(search, sortColumn, dir, offset, size);
        long total = userRepository.countAll(search);

        return new PaginatedUsersResponse(
                users.stream().map(AdminUserResponse::from).toList(),
                total,
                (int) Math.ceil((double) total / size),
                page,
                size
        );
    }

    public record PaginatedUsersResponse(
            List<AdminUserResponse> content,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {}

    public record AdminUserResponse(
            String userId, String email, String firstName, String lastName,
            String role, boolean verified, boolean active, String createdAt, boolean fimAccount
    ) {
        static AdminUserResponse from(UserDTO user) {
            return new AdminUserResponse(
                    user.getUserId().toString(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole().name(),
                    user.isVerified(),
                    user.isActive(),
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                    user.getFimUid() != null
            );
        }
    }

    /**
     * Updates a user's profile info (admin).
     */
    public UserDTO updateUser(UUID userId, String firstName, String lastName, String email, String role) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        // Users without an email (students before onboarding, FIM-Accounts whose
        // directory entry has none) arrive here with a blank one; store that as null
        // rather than "", which would collide with the next such user.
        String newEmail = email == null || email.isBlank() ? null : email.trim();

        // Check email uniqueness if changed
        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())) {
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getUserId().equals(userId)) {
                    throw new AuthenticationException("Diese Email-Adresse wird bereits verwendet.");
                }
            });
        }
        user.setEmail(newEmail);

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(UserDTO.UserRole.valueOf(role));
        userRepository.update(user);

        log.info("Admin updated user: {}", userId);
        return user;
    }

    /**
     * Deletes a user.
     */
    public void deleteUser(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));
        userRepository.deleteById(userId);
        log.info("Admin deleted user: {}", userId);
    }

    /**
     * Toggles admin status for a user.
     */
    public UserDTO toggleAdmin(UUID userId) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        if (user.getRole() == UserDTO.UserRole.ADMIN) {
            user.setRole(UserDTO.UserRole.TEACHER);
        } else {
            user.setRole(UserDTO.UserRole.ADMIN);
        }
        userRepository.update(user);
        log.info("Admin toggled role for user {} to {}", userId, user.getRole());
        return user;
    }

    /**
     * Triggers a password reset email for the given user.
     */
    @Transactional
    public void resetPassword(UUID userId) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        if (user.getFimUid() != null) {
            throw new AuthenticationException(
                    "Das Passwort eines FIM-Accounts kann nicht in CodeOwl zurückgesetzt werden.");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new AuthenticationException("Benutzer hat keine Email-Adresse.");
        }

        AuthTokenDTO resetToken = tokenService.createPasswordResetToken(userId);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getTokenValue());
        log.info("Admin triggered password reset for user: {}", userId);
    }

}
