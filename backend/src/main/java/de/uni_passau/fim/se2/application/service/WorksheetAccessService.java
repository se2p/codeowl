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

import de.uni_passau.fim.se2.domain.model.WorksheetAvailability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Whether a student may still work on a task, given the deadlines their classes
 * set for the worksheet it belongs to.
 *
 * Grading happens in the browser, so there is no single request to refuse.
 * What this guards instead are the writes that make up solving — recording a
 * solved arrangement, logging feedback, asking for a hint — so a closed
 * worksheet cannot be worked on even by a client that ignores the UI.
 */
@Service
@Slf4j
public class WorksheetAccessService {

    private final JdbcTemplate jdbc;
    private final OwnershipService ownershipService;

    public WorksheetAccessService(JdbcTemplate jdbc, OwnershipService ownershipService) {
        this.jdbc = jdbc;
        this.ownershipService = ownershipService;
    }

    /**
     * Whether the current user may still solve the given task.
     *
     * Everyone who is not a student — teachers, admins, and visitors following a
     * shared link who have no class membership — is unaffected: schedules are a
     * property of assigning a worksheet to a class, so someone outside every
     * class has no schedule to be bound by.
     *
     * A worksheet can be assigned to several classes on different schedules. A
     * student in more than one gets the most permissive answer: if any of their
     * classes still has it open, they can work on it.
     */
    public boolean canSolve(UUID taskId) {
        if (taskId == null) return true;
        if (!ownershipService.isStudent()) return true;

        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) return true;

        List<Schedule> schedules = schedulesFor(userId, taskId);
        if (schedules.isEmpty()) {
            // The task reached the student some other way than an assignment —
            // a shared link, say. Nothing schedules it, so nothing blocks it.
            return true;
        }

        Instant now = Instant.now();
        return schedules.stream()
                .map(s -> WorksheetAvailability.of(s.published(), s.releaseAt(), s.deadlineAt(), s.graceMinutes(), now))
                .anyMatch(WorksheetAvailability::allowsSolving);
    }

    /** Throws rather than returning false, for use at the top of a write endpoint. */
    public void requireSolvable(UUID taskId) {
        if (!canSolve(taskId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This worksheet is closed; its tasks can be read but not worked on.");
        }
    }

    private List<Schedule> schedulesFor(UUID userId, UUID taskId) {
        // task -> sequence -> worksheet -> assignment, restricted to the classes
        // this student is actually in.
        String sql = """
                SELECT cw.published, cw.release_at, cw.deadline_at, cw.grace_minutes
                FROM tasks t
                JOIN worksheets_sequence ws ON ws.sequence_id = t.sequence_id
                JOIN class_worksheets cw    ON cw.worksheet_id = ws.worksheet_id
                JOIN class_students cs      ON cs.class_id = cw.class_id
                WHERE t.id = ? AND cs.student_id = ?
                """;
        try {
            return jdbc.query(sql, (rs, rowNum) -> new Schedule(
                    rs.getBoolean("published"),
                    rs.getTimestamp("release_at") != null ? rs.getTimestamp("release_at").toInstant() : null,
                    rs.getTimestamp("deadline_at") != null ? rs.getTimestamp("deadline_at").toInstant() : null,
                    (Integer) rs.getObject("grace_minutes")
            ), taskId, userId);
        } catch (Exception e) {
            // A lookup failure must not lock a student out of their work.
            log.warn("Could not resolve worksheet schedule for task {}: {}", taskId, e.getMessage());
            return List.of();
        }
    }

    private record Schedule(boolean published, Instant releaseAt, Instant deadlineAt, Integer graceMinutes) {}

    /** For the client: whether the task is still workable, without attempting a write. */
    public record TaskAccess(boolean canSolve) {}
}
