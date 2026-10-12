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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean mailConfigured;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${auth.app.base-url}")
    private String appBaseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.mailConfigured = mailHost != null && !mailHost.isBlank();
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String subject = "CodeOwl - Email-Adresse bestätigen";
        String postfix = "verify-email?token=" + token;
        if (!endsWithSlash(appBaseUrl)) {
            postfix = "/" + postfix;
        }
        String verifyUrl = appBaseUrl + postfix;
        String body = String.format(
                "Hallo,\n\n" +
                "bitte bestätige deine Email-Adresse, indem du auf folgenden Link klickst:\n\n" +
                "%s\n\n" +
                "Der Link ist 24 Stunden gültig.\n\n" +
                "Viele Grüße,\nDas CodeOwl-Team",
                verifyUrl
        );
        sendMail(toEmail, subject, body);
    }

    private boolean endsWithSlash(String appBaseUrl) {
        return appBaseUrl.endsWith("/");
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String subject = "CodeOwl - Passwort zurücksetzen";
        String postfix = "reset-password?token=" + token;
        if (!endsWithSlash(appBaseUrl)) {
            postfix = "/" + postfix;
        }
        String resetUrl = appBaseUrl + postfix;
        String body = String.format(
                "Hallo,\n\n" +
                "du hast ein neues Passwort angefordert. Klicke auf folgenden Link:\n\n" +
                "%s\n\n" +
                "Der Link ist 1 Stunde gültig. Falls du kein neues Passwort angefordert hast, " +
                "ignoriere diese E-Mail.\n\n" +
                "Viele Grüße,\nDas CodeOwl-Team",
                resetUrl
        );
        sendMail(toEmail, subject, body);
    }

    private void sendMail(String to, String subject, String body) {
        // Log email metadata (tokens are masked for security)
        log.info("=== EMAIL ===\nTo: {}\nSubject: {}\n=== /EMAIL ===", to, subject);
        log.debug("Email body for {}: {}", to, body);

        if (!mailConfigured) {
            log.warn("Mail not configured – email was only logged, not sent.");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email successfully sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}