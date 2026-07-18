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

import de.uni_passau.fim.se2.domain.entities.LearningEventDTO;
import de.uni_passau.fim.se2.domain.interfaces.LearningEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgreLearningEventDao implements LearningEventRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<LearningEventDTO> ROW_MAPPER = (rs, rowNum) -> LearningEventDTO.builder()
            .id(rs.getObject("id", UUID.class))
            .userId(rs.getObject("user_id", UUID.class))
            .taskId(rs.getObject("task_id", UUID.class))
            .sequenceId(rs.getObject("sequence_id", UUID.class))
            .tierLevel(rs.getObject("tier_level") != null ? rs.getInt("tier_level") : null)
            .eventType(rs.getString("event_type"))
            .eventData(rs.getString("event_data"))
            .createdAt(rs.getTimestamp("created_at"))
            .build();

    @Override
    public void save(LearningEventDTO event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }

        PGobject jsonObj = new PGobject();
        jsonObj.setType("jsonb");
        try {
            jsonObj.setValue(event.getEventData() != null ? event.getEventData() : "{}");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set JSONB value", e);
        }

        jdbc.update("""
            INSERT INTO learning_events (id, user_id, task_id, sequence_id, tier_level, event_type, event_data)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                event.getId(),
                event.getUserId(),
                event.getTaskId(),
                event.getSequenceId(),
                event.getTierLevel(),
                event.getEventType(),
                jsonObj
        );

        log.debug("Saved learning event: type={}, taskId={}", event.getEventType(), event.getTaskId());
    }

    @Override
    public List<LearningEventDTO> findByTaskId(UUID taskId) {
        return jdbc.query(
                "SELECT * FROM learning_events WHERE task_id = ? ORDER BY created_at",
                ROW_MAPPER, taskId
        );
    }

    @Override
    public List<LearningEventDTO> findByUserId(UUID userId) {
        return jdbc.query(
                "SELECT * FROM learning_events WHERE user_id = ? ORDER BY created_at",
                ROW_MAPPER, userId
        );
    }

    @Override
    public List<LearningEventDTO> findBySequenceId(UUID sequenceId) {
        return jdbc.query(
                "SELECT * FROM learning_events WHERE sequence_id = ? ORDER BY created_at",
                ROW_MAPPER, sequenceId
        );
    }

    @Override
    public List<LearningEventDTO> findAll() {
        return jdbc.query(
                "SELECT * FROM learning_events ORDER BY created_at",
                ROW_MAPPER
        );
    }

    /**
     * Checks whether any class this student belongs to has tracking_enabled = true.
     */
    public boolean isTrackingEnabledForStudent(UUID userId) {
        List<Boolean> results = jdbc.query("""
            SELECT EXISTS(
                SELECT 1 FROM class_students cs
                JOIN classes c ON c.id = cs.class_id
                WHERE cs.student_id = ? AND c.tracking_enabled = true
            ) AS enabled
            """,
                (rs, rowNum) -> rs.getBoolean("enabled"),
                userId
        );
        return results.stream().findFirst().orElse(false);
    }
}
