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

import de.uni_passau.fim.se2.application.service.LearningEventService;
import de.uni_passau.fim.se2.domain.entities.LearningEventDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class LearningEventController {

    private final LearningEventService learningEventService;
    private final ObjectMapper objectMapper;

    /**
     * Request data transfer object for logging events.
     */
    public record LogEventRequest(
            String taskId,
            String sequenceId,
            Integer tierLevel,
            String eventType,
            Map<String, Object> eventData
    ) {
    }

    /**
     * POST /api/events — public, anonymous allowed.
     * Extracts userId from JWT if present.
     *
     * @param request the event logging request payload
     * @return a ResponseEntity indicating success or failure
     */
    @PostMapping
    public ResponseEntity<?> logEvent(@RequestBody LogEventRequest request) {
        UUID userId = extractUserId();

        UUID taskId;
        try {
            taskId = UUID.fromString(request.taskId());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid taskId"));
        }

        UUID sequenceId = null;
        if (request.sequenceId() != null && !request.sequenceId().isBlank()) {
            try {
                sequenceId = UUID.fromString(request.sequenceId());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid sequenceId"));
            }
        }

        String eventDataJson = "{}";
        if (request.eventData() != null) {
            try {
                eventDataJson = objectMapper.writeValueAsString(request.eventData());
            } catch (JsonProcessingException e) {
                eventDataJson = "{}";
            }
        }

        learningEventService.logEvent(
                userId,
                taskId,
                sequenceId,
                request.tierLevel(),
                request.eventType(),
                eventDataJson
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Event logged"));
    }

    /**
     * GET /api/events/task/{taskId} — authenticated.
     *
     * @param taskId the task ID to filter events by
     * @return list of events associated with the specified task
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<LearningEventDTO>> getEventsByTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(learningEventService.getEventsByTask(taskId));
    }

    /**
     * GET /api/events/sequence/{sequenceId} — authenticated.
     *
     * @param sequenceId the sequence ID to filter events by
     * @return list of events associated with the specified sequence
     */
    @GetMapping("/sequence/{sequenceId}")
    public ResponseEntity<List<LearningEventDTO>> getEventsBySequence(@PathVariable UUID sequenceId) {
        return ResponseEntity.ok(learningEventService.getEventsBySequence(sequenceId));
    }

    /**
     * GET /api/events/export — admin only.
     *
     * @return list of all events
     */
    @GetMapping("/export")
    public ResponseEntity<List<LearningEventDTO>> exportAllEvents() {
        return ResponseEntity.ok(learningEventService.getAllEvents());
    }

    /**
     * GET /api/events/summary/{sequenceId} — authenticated.
     *
     * @param sequenceId the sequence ID to retrieve the summary for
     * @return summary of the specified sequence
     */
    @GetMapping("/summary/{sequenceId}")
    public ResponseEntity<Map<String, Object>> getSequenceSummary(@PathVariable UUID sequenceId) {
        return ResponseEntity.ok(learningEventService.getSequenceSummary(sequenceId));
    }

    /**
     * Extracts the user ID from the authentication context.
     *
     * @return the extracted user ID, or null if not found
     */
    private UUID extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return (UUID) auth.getPrincipal();
        } catch (ClassCastException e) {
            return null;
        }
    }
}