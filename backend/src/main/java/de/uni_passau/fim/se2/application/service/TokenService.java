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
import de.uni_passau.fim.se2.domain.exceptions.AuthenticationException;
import de.uni_passau.fim.se2.domain.interfaces.AuthTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final AuthTokenRepository tokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /**
     * Creates a short student invite token (e.g. "ABC-123-XYZ").
     */
    public AuthTokenDTO createStudentInviteToken(UUID userId) {
        String tokenValue = generateShortToken();
        return createToken(userId, tokenValue, AuthTokenDTO.TokenType.STUDENT_INVITE, 30 * 24); // 30 days
    }

    /**
     * Creates an email verification token.
     */
    public AuthTokenDTO createEmailVerificationToken(UUID userId) {
        // Delete old verification tokens for this user
        tokenRepository.deleteByUserIdAndType(userId, AuthTokenDTO.TokenType.EMAIL_VERIFICATION);
        String tokenValue = generateSecureToken();
        return createToken(userId, tokenValue, AuthTokenDTO.TokenType.EMAIL_VERIFICATION, 24); // 24 hours
    }

    /**
     * Creates a password reset token.
     */
    public AuthTokenDTO createPasswordResetToken(UUID userId) {
        // Delete old reset tokens for this user
        tokenRepository.deleteByUserIdAndType(userId, AuthTokenDTO.TokenType.PASSWORD_RESET);
        String tokenValue = generateSecureToken();
        return createToken(userId, tokenValue, AuthTokenDTO.TokenType.PASSWORD_RESET, 1); // 1 hour
    }

    /**
     * Validates and consumes a token.
     */
    public AuthTokenDTO validateAndConsume(String tokenValue, AuthTokenDTO.TokenType expectedType) {
        AuthTokenDTO token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new AuthenticationException("Ungültiger Token."));

        if (token.isUsed()) {
            throw new AuthenticationException("Token wurde bereits verwendet.");
        }
        if (token.isExpired()) {
            throw new AuthenticationException("Token ist abgelaufen.");
        }
        if (token.getTokenType() != expectedType) {
            throw new AuthenticationException("Ungültiger Token-Typ.");
        }

        // Atomic: WHERE used = FALSE prevents double-consumption
        if (!tokenRepository.markUsed(token.getTokenId())) {
            throw new AuthenticationException("Token wurde bereits verwendet.");
        }
        return token;
    }

    /**
     * Validates a token without consuming it (for multi-step flows like student onboarding).
     */
    public AuthTokenDTO validateWithoutConsuming(String tokenValue, AuthTokenDTO.TokenType expectedType) {
        AuthTokenDTO token = tokenRepository.findByTokenValue(tokenValue)
                .orElseThrow(() -> new AuthenticationException("Ungültiger Token."));

        if (token.isUsed()) {
            throw new AuthenticationException("Token wurde bereits verwendet.");
        }
        if (token.isExpired()) {
            throw new AuthenticationException("Token ist abgelaufen.");
        }
        if (token.getTokenType() != expectedType) {
            throw new AuthenticationException("Ungültiger Token-Typ.");
        }
        return token;
    }

    private AuthTokenDTO createToken(UUID userId, String tokenValue, AuthTokenDTO.TokenType type, int hoursValid) {
        AuthTokenDTO token = AuthTokenDTO.builder()
                .userId(userId)
                .tokenValue(tokenValue)
                .tokenType(type)
                .expiresAt(LocalDateTime.now().plusHours(hoursValid))
                .used(false)
                .build();
        return tokenRepository.save(token);
    }

    /**
     * Short human-readable token for student invites (e.g. "ABc-3Km-Xp7").
     */
    private String generateShortToken() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i == 3 || i == 6) sb.append('-');
            sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Long secure token for email verification / password reset.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
