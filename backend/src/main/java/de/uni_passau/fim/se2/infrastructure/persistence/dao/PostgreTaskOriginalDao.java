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

import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreTaskOriginalDao implements TaskOriginalRepository {

    private final JdbcTemplate jdbc;

    public PostgreTaskOriginalDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TaskOriginalDTO> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        TaskOriginalDTO dto = new TaskOriginalDTO();
        dto.setId(rs.getObject("id", UUID.class));
        dto.setTaskId(rs.getObject("task_id", UUID.class));
        dto.setOriginalProblemStatement(rs.getString("original_problem_statement"));
        dto.setOriginalCode(rs.getString("original_code"));
        int rating = rs.getInt("adjustment_rating");
        dto.setAdjustmentRating(rs.wasNull() ? null : rating);
        dto.setSavedAt(rs.getTimestamp("saved_at") != null
                ? rs.getTimestamp("saved_at").toLocalDateTime() : null);
        return dto;
    };

    @Override
    public void save(TaskOriginalDTO original) {
        UUID id = original.getId() != null ? original.getId() : UUID.randomUUID();
        original.setId(id);

        String sql = """
            INSERT INTO task_originals (id, task_id, original_problem_statement, original_code, adjustment_rating)
            VALUES (?, ?, ?, ?, ?)
        """;
        jdbc.update(sql,
                id,
                original.getTaskId(),
                original.getOriginalProblemStatement(),
                original.getOriginalCode(),
                original.getAdjustmentRating()
        );
    }

    @Override
    public Optional<TaskOriginalDTO> findByTaskId(UUID taskId) {
        String sql = "SELECT * FROM task_originals WHERE task_id = ?";
        try {
            TaskOriginalDTO result = jdbc.queryForObject(sql, ROW_MAPPER, taskId);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskOriginalDTO> findAll() {
        return jdbc.query("SELECT * FROM task_originals ORDER BY saved_at DESC", ROW_MAPPER);
    }

    @Override
    public boolean existsByTaskId(UUID taskId) {
        String sql = "SELECT COUNT(*) FROM task_originals WHERE task_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, taskId);
        return count != null && count > 0;
    }
}
