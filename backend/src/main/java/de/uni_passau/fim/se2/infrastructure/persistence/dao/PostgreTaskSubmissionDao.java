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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class PostgreTaskSubmissionDao implements TaskSubmissionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgreTaskSubmissionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<TaskSubmissionDTO> rowMapper = (rs, rowNum) -> TaskSubmissionDTO.builder()
            .userId(rs.getObject("user_id", UUID.class))
            .taskId(rs.getObject("task_id", UUID.class))
            .blockOrder(readList(rs.getString("block_order"), new TypeReference<List<String>>() {}))
            .indentLevels(readList(rs.getString("indent_levels"), new TypeReference<List<Integer>>() {}))
            .solved(rs.getBoolean("solved"))
            .solvedAt(rs.getTimestamp("solved_at") != null ? rs.getTimestamp("solved_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    @Override
    public void save(TaskSubmissionDTO submission) {
        if (submission == null || submission.getUserId() == null || submission.getTaskId() == null) {
            throw new DomainValidationException("Task submission needs a user and a task.");
        }

        // One row per student and task: a later arrangement replaces the earlier one rather
        // than adding to a history. The audit trail is learning_events.
        //
        // `solved` only ever goes false -> true. An autosave of work in progress must not
        // take away having solved the task, and solved_at keeps the moment it was first
        // solved rather than being pushed forward by every later edit.
        //
        // block_order/indent_levels/updated_at follow the same stickiness once the row is
        // solved: an autosave (EXCLUDED.solved = false) against an already-solved row is a
        // redo in progress that was abandoned before the student re-solved, so it must not
        // clobber the arrangement that was actually graded correct. Only a fresh solve
        // (EXCLUDED.solved = true) is allowed to replace it. Against a not-yet-solved row,
        // every autosave still overwrites, exactly as before.
        String sql = """
                INSERT INTO task_submissions (user_id, task_id, block_order, indent_levels,
                                              solved, solved_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP END, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, task_id) DO UPDATE
                SET block_order   = CASE WHEN task_submissions.solved AND NOT EXCLUDED.solved
                                         THEN task_submissions.block_order ELSE EXCLUDED.block_order END,
                    indent_levels = CASE WHEN task_submissions.solved AND NOT EXCLUDED.solved
                                         THEN task_submissions.indent_levels ELSE EXCLUDED.indent_levels END,
                    solved        = task_submissions.solved OR EXCLUDED.solved,
                    solved_at     = COALESCE(task_submissions.solved_at, EXCLUDED.solved_at),
                    updated_at    = CASE WHEN task_submissions.solved AND NOT EXCLUDED.solved
                                         THEN task_submissions.updated_at ELSE CURRENT_TIMESTAMP END
                """;

        jdbc.update(sql,
                submission.getUserId(),
                submission.getTaskId(),
                toJsonb(submission.getBlockOrder()),
                toJsonb(submission.getIndentLevels()),
                submission.isSolved(),
                submission.isSolved());

        log.debug("Stored {} arrangement for user={} task={}",
                submission.isSolved() ? "solved" : "in-progress",
                submission.getUserId(), submission.getTaskId());
    }

    @Override
    public Optional<TaskSubmissionDTO> find(UUID userId, UUID taskId) {
        if (userId == null || taskId == null) return Optional.empty();

        String sql = """
                SELECT user_id, task_id, block_order, indent_levels, solved, solved_at, updated_at
                FROM task_submissions
                WHERE user_id = ? AND task_id = ?
                """;

        return jdbc.query(sql, rowMapper, userId, taskId).stream().findFirst();
    }

    @Override
    public void delete(UUID userId, UUID taskId) {
        if (userId == null || taskId == null) return;

        int removed = jdbc.update(
                "DELETE FROM task_submissions WHERE user_id = ? AND task_id = ?", userId, taskId);

        log.debug("Discarded {} stored arrangement(s) for user={} task={}",
                removed, userId, taskId);
    }

    /** Wraps a list as a jsonb parameter; a null list is stored as an empty array. */
    private PGobject toJsonb(Object value) {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(objectMapper.writeValueAsString(value != null ? value : List.of()));
            return json;
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new DomainValidationException("Could not serialise task submission: " + e.getMessage());
        }
    }

    /** A row written by an older or broken client must not break loading the page. */
    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable task submission payload, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }
}
