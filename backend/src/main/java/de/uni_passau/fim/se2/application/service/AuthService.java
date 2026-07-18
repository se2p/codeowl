/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasherService passwordHasher;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final String rootAdminEmail;

    public AuthService(UserRepository userRepository,
                       PasswordHasherService passwordHasher,
                       JwtService jwtService,
                       TokenService tokenService,
                       EmailService emailService,
                       @Value("${auth.root-admin-email:}") String rootAdminEmail) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.rootAdminEmail = rootAdminEmail;
    }

    // ── Teacher Registration ──────────────────────────────────

    /**
     * Registers a new teacher. Sends email verification.
     * Root admin email automatically gets ADMIN role.
     */
    @Transactional
    public void registerTeacher(String email, String password, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Ein Account mit dieser Email existiert bereits.");
        }

        boolean isRootAdmin = rootAdminEmail != null && !rootAdminEmail.isBlank()
                && rootAdminEmail.equalsIgnoreCase(email.trim());

        UserDTO user = UserDTO.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .passwordHash(passwordHasher.hashPassword(password))
                .role(isRootAdmin ? UserDTO.UserRole.ADMIN : UserDTO.UserRole.TEACHER)
                .verified(isRootAdmin) // Root admin is auto-verified
                .active(true)
                .build();

        userRepository.save(user);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(user.getUserId());
        emailService.sendVerificationEmail(email, verifyToken.getTokenValue());

        log.info("Teacher registered: {} ({})", email, user.getUserId());
    }

    // ── Login ────────────────────────────────────────────────

    /**
     * Login with email + password. Returns JWT.
     */
    public String login(String email, String password) {
        UserDTO user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Ungültige Anmeldedaten."));

        if (!user.isActive()) {
            throw new AuthenticationException("Account ist deaktiviert.");
        }
        if (user.getPasswordHash() == null || !passwordHasher.verifyPassword(password, user.getPasswordHash())) {
            throw new AuthenticationException("Ungültige Anmeldedaten.");
        }
        if (!user.isVerified()) {
            throw new AuthenticationException("Bitte bestätige zuerst deine Email-Adresse.");
        }

        // Ensure root admin always has ADMIN role
        if (rootAdminEmail != null && !rootAdminEmail.isBlank()
                && rootAdminEmail.equalsIgnoreCase(email.trim()) && user.getRole() != UserDTO.UserRole.ADMIN) {
            user.setRole(UserDTO.UserRole.ADMIN);
            userRepository.update(user);
            log.info("Root admin role restored for: {}", email);
        }

        log.info("Login successful: {} (role={})", email, user.getRole());
        return jwtService.generateToken(user);
    }

    // ── Email Verification ───────────────────────────────────

    /**
     * Verifies email using token.
     */
    public void verifyEmail(String tokenValue) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.EMAIL_VERIFICATION);

        UserDTO user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        user.setVerified(true);
        userRepository.update(user);

        log.info("Email verified for user: {}", user.getUserId());
    }

    // ── Password Reset ───────────────────────────────────────

    /**
     * Initiates password reset. Sends email with reset token.
     */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            AuthTokenDTO resetToken = tokenService.createPasswordResetToken(user.getUserId());
            emailService.sendPasswordResetEmail(email, resetToken.getTokenValue());
            log.info("Password reset requested for: {}", email);
        });
        // Always succeed to not reveal whether email exists
    }

    /**
     * Resets password using token.
     */
    public void resetPassword(String tokenValue, String newPassword) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.PASSWORD_RESET);

        UserDTO user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        userRepository.update(user);

        log.info("Password reset completed for user: {}", user.getUserId());
    }

    // ── Student Invite Token Flow ────────────────────────────

    /**
     * Validates a student invite token (without consuming).
     * Returns the student user info so the frontend can show the onboarding form.
     */
    public UserDTO validateStudentToken(String tokenValue) {
        AuthTokenDTO token = tokenService.validateWithoutConsuming(tokenValue, AuthTokenDTO.TokenType.STUDENT_INVITE);
        return userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));
    }

    /**
     * Completes student onboarding: sets email + password, sends verification email, invalidates invite token.
     */
    @Transactional
    public void completeStudentOnboarding(String tokenValue, String email, String password) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.STUDENT_INVITE);

        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Diese Email-Adresse wird bereits verwendet.");
        }

        UserDTO student = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        student.setEmail(email);
        student.setPasswordHash(passwordHasher.hashPassword(password));
        student.setVerified(false);
        userRepository.update(student);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(student.getUserId());
        emailService.sendVerificationEmail(email, verifyToken.getTokenValue());

        log.info("Student onboarding started: {} ({})", email, student.getUserId());
    }

    // ── Profile Editing ──────────────────────────────────────

    /**
     * Updates the user's first and last name.
     */
    public UserDTO updateProfile(UUID userId, String firstName, String lastName) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        userRepository.update(user);
        log.info("Profile updated for user: {}", userId);
        return user;
    }

    /**
     * Updates the user's email. Marks as unverified and sends verification email.
     */
    @Transactional
    public void updateEmail(UUID userId, String newEmail) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        // Check if new email is already taken by another user
        userRepository.findByEmail(newEmail).ifPresent(existing -> {
            if (!existing.getUserId().equals(userId)) {
                throw new AuthenticationException("Diese Email-Adresse wird bereits verwendet.");
            }
        });

        user.setEmail(newEmail);
        user.setVerified(false);
        userRepository.update(user);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(userId);
        emailService.sendVerificationEmail(newEmail, verifyToken.getTokenValue());

        log.info("Email updated for user: {} -> {}", userId, newEmail);
    }

    /**
     * Changes the user's password. Requires current password for verification.
     */
    public void updatePassword(UUID userId, String currentPassword, String newPassword) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        if (user.getPasswordHash() == null || !passwordHasher.verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Aktuelles Passwort ist falsch.");
        }

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        user.setMustChangePassword(false);
        userRepository.update(user);

        log.info("Password changed for user: {}", userId);
    }

    /**
     * Changes a temporary password without requiring the old password.
     * Only valid when the user has mustChangePassword = true.
     */
    public void changeTempPassword(UUID userId, String newPassword) {
        UserDTO user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));

        if (!user.isMustChangePassword()) {
            throw new AuthenticationException("Kein temporäres Passwort gesetzt.");
        }

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        user.setMustChangePassword(false);
        userRepository.update(user);

        log.info("Temporary password changed for user: {}", userId);
    }

    // ── Helper ───────────────────────────────────────────────

    /**
     * Get current user from JWT token.
     */
    public UserDTO getUserFromToken(String token) {
        UUID userId = jwtService.extractUserId(token);
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));
    }

    /**
     * Get user by ID.
     */
    public UserDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("Benutzer nicht gefunden."));
    }
}
