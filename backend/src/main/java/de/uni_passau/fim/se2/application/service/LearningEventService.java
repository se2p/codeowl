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

import de.uni_passau.fim.se2.domain.entities.LearningEventDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.LearningEventRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreLearningEventDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningEventService {

    private final LearningEventRepository learningEventRepository;

    /**
     * The event types that may be written — and, through {@link #allowedEventTypes()}, the
     * ones the admin research page will accept as a filter, so a mistyped filter is a 400
     * rather than an empty table that looks like missing data.
     */
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            // AP10/AP11: Core events
            "task_opened",
            "task_closed",
            "feedback_requested",
            "task_correct",
            "task_abandoned",
            // Historical: emitted while the shuffle button still cleared the
            // solution area. Kept accepted so replayed or queued events from an
            // older client are not rejected; nothing produces it now.
            "task_reset",
            "source_shuffled",
            "task_reopened",
            "tier_opened",
            "tier_closed",
            "sequence_rating",
            "sequence_completed",
            // AP13: Hint events
            "hint_requested",
            "hint_explanation_requested",
            "hint_viewed",
            "post_hint_action",
            // AP14: Advanced analytics
            "repair_strategy",
            "stuckness_detected",
            "self_report",
            "teacher_edit_started",
            "teacher_edit_saved",
            "teacher_edit_cancelled",
            // AP15: Distractor analytics
            "distractor_placed",
            "distractor_removed",
            // Redo of an already-solved task
            "task_redo_started",
            // AP31: Inline-split fragment analytics
            "fragment_placed",
            "split_distractor_placed",
            // AP30/AP31: what the student actually typed into a gap, and how they ordered an
            // inline-split line, each with the expected value alongside it. SolvePage has
            // emitted all four since gaps and splits were added, but they were never on this
            // list, so every one was rejected with a 400 that useEventTracker swallows on
            // purpose -- the client reported success and nothing was stored. They are also the
            // one part of a student's work that task_submissions cannot hold: it records the
            // visible text of each block, and neither a gap value nor a chip placement
            // contributes any textContent. Until now they were recorded nowhere at all.
            "gap_correct",
            "gap_incorrect",
            "split_line_correct",
            "split_line_incorrect",
            // Every reorder and indent the student makes, batched. Until this existed the
            // only record of an arrangement was whatever they had when they clicked Feedback,
            // so a student who worked for ten minutes without clicking it left nothing behind
            // -- and no event of any kind carried indentation.
            "arrangement_changed"
    );

    /** The allowlist above, for callers that validate against it rather than write through it. */
    public static Set<String> allowedEventTypes() {
        return ALLOWED_EVENT_TYPES;
    }

    /**
     * Logs a learning event. If tracking is not enabled for the student's class,
     * the userId is set to null (anonymized).
     */
    public void logEvent(UUID userId, UUID taskId, UUID sequenceId,
                         Integer tierLevel, String eventType, String eventData) {

        if (taskId == null) {
            throw new DomainValidationException("taskId is required");
        }

        if (eventType == null || !ALLOWED_EVENT_TYPES.contains(eventType)) {
            throw new DomainValidationException(
                    "Invalid eventType: " + eventType + ". Allowed: " + ALLOWED_EVENT_TYPES);
        }

        // Resolve the class BEFORE anonymizing, because anonymizing is what destroys the only
        // link back to it. Identity is dropped for an untracked class; class context is kept, so
        // a per-class view still sees the work — the same trade feedback_logs already makes.
        UUID classId = null;
        if (userId != null && learningEventRepository instanceof PostgreLearningEventDao dao) {
            classId = dao.resolveClassId(userId, taskId);

            // Anonymize if tracking is not enabled for this student's class
            if (!dao.isTrackingEnabledForStudent(userId)) {
                log.debug("Tracking not enabled for user {}, anonymizing event", userId);
                userId = null;
            }
        }

        LearningEventDTO event = LearningEventDTO.builder()
                .userId(userId)
                .classId(classId)
                .taskId(taskId)
                .sequenceId(sequenceId)
                .tierLevel(tierLevel)
                .eventType(eventType)
                .eventData(eventData != null ? eventData : "{}")
                .build();

        learningEventRepository.save(event);
    }

    public List<LearningEventDTO> getEventsByTask(UUID taskId) {
        return learningEventRepository.findByTaskId(taskId);
    }

    public List<LearningEventDTO> getEventsBySequence(UUID sequenceId) {
        return learningEventRepository.findBySequenceId(sequenceId);
    }

    public List<LearningEventDTO> getAllEvents() {
        return learningEventRepository.findAll();
    }

    /**
     * Returns aggregated summary stats for a sequence, including
     * completionRate, averageAttempts, and averageTimeMs (AP11).
     */
    public Map<String, Object> getSequenceSummary(UUID sequenceId) {
        List<LearningEventDTO> events = learningEventRepository.findBySequenceId(sequenceId);

        Map<String, Long> eventCounts = events.stream()
                .collect(Collectors.groupingBy(LearningEventDTO::getEventType, Collectors.counting()));

        // Count identified users
        long identifiedUsers = events.stream()
                .map(LearningEventDTO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // Count anonymous events (userId == null) as separate anonymous users
        long anonymousEvents = events.stream()
                .filter(e -> e.getUserId() == null)
                .count();

        long totalEvents = events.size();

        // Completion rate: task_correct / task_opened
        long taskOpened = eventCounts.getOrDefault("task_opened", 0L);
        long taskCorrect = eventCounts.getOrDefault("task_correct", 0L);
        double completionRate = taskOpened > 0 ? (double) taskCorrect / taskOpened : 0.0;

        // Average attempts and time from task_correct event data
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        List<LearningEventDTO> correctEvents = events.stream()
                .filter(e -> "task_correct".equals(e.getEventType()))
                .toList();

        double averageAttempts = 0.0;
        double averageTimeMs = 0.0;
        if (!correctEvents.isEmpty()) {
            long totalAttempts = 0;
            long totalTimeMs = 0;
            int attemptCount = 0;
            int timeCount = 0;
            for (LearningEventDTO ce : correctEvents) {
                try {
                    Map<String, Object> data = om.readValue(
                            ce.getEventData() != null ? ce.getEventData() : "{}",
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    if (data.containsKey("totalAttempts")) {
                        totalAttempts += ((Number) data.get("totalAttempts")).longValue();
                        attemptCount++;
                    }
                    if (data.containsKey("totalTimeMs")) {
                        totalTimeMs += ((Number) data.get("totalTimeMs")).longValue();
                        timeCount++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse eventData for summary: {}", e.getMessage());
                }
            }
            averageAttempts = attemptCount > 0 ? (double) totalAttempts / attemptCount : 0.0;
            averageTimeMs = timeCount > 0 ? (double) totalTimeMs / timeCount : 0.0;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sequenceId", sequenceId.toString());
        summary.put("totalEvents", totalEvents);
        summary.put("uniqueIdentifiedUsers", identifiedUsers);
        summary.put("anonymousEventCount", anonymousEvents);
        summary.put("completionRate", Math.round(completionRate * 10000.0) / 10000.0);
        summary.put("averageAttempts", Math.round(averageAttempts * 100.0) / 100.0);
        summary.put("averageTimeMs", Math.round(averageTimeMs));
        summary.put("eventCounts", eventCounts);

        return summary;
    }
}
