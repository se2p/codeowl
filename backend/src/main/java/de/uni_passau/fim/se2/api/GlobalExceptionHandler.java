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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.api.dto.ApiErrorResponse;
import de.uni_passau.fim.se2.application.service.BackupService;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.exceptions.GenerationException;
import de.uni_passau.fim.se2.domain.interfaces.DatabaseDumpExecutor;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Problem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle domain validation errors with a 400 response.
     * If the message indicates an unsupported language, the title is specialized.
     */
    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainValidationException(DomainValidationException ex) {
        final String title = (ex.getMessage() != null && ex.getMessage().contains("Unsupported language"))
                ? "Ungültige Sprache"
                : "Ungültige Eingabe";

        log.warn("Domain validation failed: {}", ex.getMessage(), ex);

        ApiErrorResponse error = new ApiErrorResponse(title, safeMsg(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    /**
     * Turn JavaParser parse problems into a concise 400 response.
     * Collects up to 5 problem messages (with location if available).
     */
    @ExceptionHandler(ParseProblemException.class)
    public ResponseEntity<ApiErrorResponse> handleParseProblem(ParseProblemException ex) {
        String details = extractParseProblems(ex);
        log.warn("Parse error: {}", details, ex);

        ApiErrorResponse error = new ApiErrorResponse("Syntaxfehler im Code", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    /**
     * Handle task/code generation failures from upstream services/models.
     * Defaults to 502 (Bad Gateway); uses 504 for timeouts and 503 for transient I/O-like issues.
     */
    @ExceptionHandler(GenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleGenerationException(GenerationException ex) {
        HttpStatus status = resolveGenerationHttpStatus(ex);
        log.error("Generation failed: {}", ex.getMessage(), ex);

        ApiErrorResponse error = new ApiErrorResponse(
                "Fehler bei der Aufgabengenerierung",
                safeMsg(ex.getMessage())
        );
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handle access denied (ownership / role violations) -> 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                "Zugriff verweigert",
                safeMsg(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error); // 403
    }

    /**
     * Another backup is already running — 503 Service Unavailable.
     */
    @ExceptionHandler(BackupService.BackupBusyException.class)
    public ResponseEntity<Map<String, String>> handleBackupBusy(BackupService.BackupBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * pg_dump failure — 500 Internal Server Error.
     */
    @ExceptionHandler(DatabaseDumpExecutor.DumpException.class)
    public ResponseEntity<Map<String, String>> handleDumpException(DatabaseDumpExecutor.DumpException e) {
        log.error("Database dump failed: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Database backup failed: " + e.getMessage()));
    }

    /**
     * Catch-all for unexpected errors -> 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiErrorResponse error = new ApiErrorResponse(
                "Interner Serverfehler",
                "Es ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es erneut."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }

    // ---------- helpers ----------

    private static String extractParseProblems(ParseProblemException ex) {
        List<Problem> problems = ex.getProblems();
        if (problems == null || problems.isEmpty()) {
            return safeMsg(ex.getMessage());
        }
        return problems.stream()
                .limit(5)
                .map(p -> {
                    String msg = p.getMessage();
                    var loc = p.getLocation().orElse(null);
                    return (loc == null) ? msg : (msg + " @" + loc);
                })
                .collect(Collectors.joining("; "));
    }

    private static HttpStatus resolveGenerationHttpStatus(GenerationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT; // 504
        }
        return HttpStatus.BAD_GATEWAY; // 502
    }

    private static String safeMsg(String msg) {
        return (msg == null || msg.isBlank()) ? "No details available." : msg;
    }
}