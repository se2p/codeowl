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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.FeedbackLogRepository;
import lombok.extern.slf4j.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL-based implementation of the {@link FeedbackLogRepository}.
 * <p>
 * Handles persistence of feedback click events from learners interacting with
 * Parsons problems in the solver interface. Each record represents a single
 * click on the “Feedback” button and whether the task was correct at that time.
 */
@Slf4j
@Repository
public class PostgreFeedbackLogDao implements FeedbackLogRepository {

    private final JdbcTemplate jdbc;

    public PostgreFeedbackLogDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persists a new feedback log entry.
     *
     * @param feedbackLogDTO the {@link FeedbackLogDTO} containing task reference and correctness flag
     */
    @Override
    public void save(FeedbackLogDTO feedbackLogDTO) {
        validateCreate(feedbackLogDTO);

        String sql = """
            INSERT INTO feedback_logs (task_id, correct, user_id, class_id)
            VALUES (?, ?, ?, ?)
            """;

        try {
            jdbc.update(sql,
                        feedbackLogDTO.getTaskId(),
                        feedbackLogDTO.isCorrect(),
                        feedbackLogDTO.getUserId(),
                        feedbackLogDTO.getClassId());

            log.info("Saved feedback log for task_id={}, correct={}", feedbackLogDTO.getTaskId(), feedbackLogDTO.isCorrect());
        } catch (DataAccessException e) {
            log.error("Error saving feedback log for task_id={}", feedbackLogDTO.getTaskId(), e);
            throw new DomainValidationException("Failed to save feedback log: " + e.getMessage());
        }
    }

    /**
     * Retrieves all feedback logs for the specified task.
     *
     * @param taskId the unique identifier of the task
     * @return list of {@link FeedbackLogDTO} entries sorted by timestamp ascending
     */
    @Override
    public List<FeedbackLogDTO> findByTaskId(UUID taskId) {
        validateTaskId(taskId);

        String sql = """
            SELECT id, task_id, correct, user_id, class_id, clicked_at
            FROM feedback_logs
            WHERE task_id = ?
            ORDER BY clicked_at ASC
            """;

        try {
            return jdbc.query(sql,
                              (ResultSet rs, int rowNum) -> FeedbackLogDTO.builder()
                                      .id(rs.getObject("id", UUID.class))
                                      .taskId(rs.getObject("task_id", UUID.class))
                                      .correct(rs.getBoolean("correct"))
                                      .userId(rs.getObject("user_id", UUID.class))
                                      .classId(rs.getObject("class_id", UUID.class))
                                      .clickedAt(rs.getTimestamp("clicked_at") != null
                                              ? rs.getTimestamp("clicked_at").toLocalDateTime() : null)
                                      .build(),
                              taskId);
        } catch (DataAccessException e) {
            log.error("Error loading feedback logs for task_id={}", taskId, e);
            throw new DomainValidationException("Failed to retrieve feedback logs: " + e.getMessage());
        }
    }

    /**
     * Deletes all feedback logs for a given task.
     *
     * @param taskId the unique identifier of the task
     */
    @Override
    public void deleteByTaskId(UUID taskId) {
        validateTaskId(taskId);

        try {
            int rows = jdbc.update("DELETE FROM feedback_logs WHERE task_id = ?", taskId);
            log.info("Deleted {} feedback logs for task_id={}", rows, taskId);
        } catch (DataAccessException e) {
            log.error("Error deleting feedback logs for task_id={}", taskId, e);
            throw new DomainValidationException("Failed to delete feedback logs: " + e.getMessage());
        }
    }

    /**
     * Retrieves all feedback logs for the specified class.
     */
    @Override
    public List<FeedbackLogDTO> findByClassId(UUID classId) {
        if (classId == null) {
            throw new DomainValidationException("Class ID cannot be null");
        }

        String sql = """
            SELECT id, task_id, correct, user_id, class_id, clicked_at
            FROM feedback_logs
            WHERE class_id = ?
            ORDER BY clicked_at ASC
            """;

        try {
            return jdbc.query(sql,
                              (ResultSet rs, int rowNum) -> FeedbackLogDTO.builder()
                                      .id(rs.getObject("id", UUID.class))
                                      .taskId(rs.getObject("task_id", UUID.class))
                                      .correct(rs.getBoolean("correct"))
                                      .userId(rs.getObject("user_id", UUID.class))
                                      .classId(rs.getObject("class_id", UUID.class))
                                      .clickedAt(rs.getTimestamp("clicked_at") != null
                                              ? rs.getTimestamp("clicked_at").toLocalDateTime() : null)
                                      .build(),
                              classId);
        } catch (DataAccessException e) {
            log.error("Error loading feedback logs for class_id={}", classId, e);
            throw new DomainValidationException("Failed to retrieve feedback logs: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Validation helpers
    // ────────────────────────────────────────────────────────────────

    private void validateCreate(FeedbackLogDTO feedbackLogDTO) {
        if (feedbackLogDTO == null) {
            throw new DomainValidationException("Feedback log cannot be null");
        }
        if (feedbackLogDTO.getTaskId() == null) {
            throw new DomainValidationException("Task ID cannot be null when saving feedback log");
        }
    }

    private void validateTaskId(UUID taskId) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
    }
}