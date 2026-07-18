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

import de.uni_passau.fim.se2.domain.entities.HintUsageDTO;
import de.uni_passau.fim.se2.domain.interfaces.HintUsageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

@Repository
public class PostgreHintUsageDao implements HintUsageRepository {

    private final JdbcTemplate jdbc;

    public PostgreHintUsageDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<HintUsageDTO> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        HintUsageDTO dto = new HintUsageDTO();
        dto.setId(rs.getObject("id", UUID.class));
        dto.setUserId(rs.getObject("user_id", UUID.class));
        dto.setTaskId(rs.getObject("task_id", UUID.class));
        dto.setHintLevel(rs.getInt("hint_level"));
        dto.setBlockOrderAtRequest(rs.getString("block_order_at_request"));
        dto.setFindings(rs.getString("findings"));
        dto.setHintText(rs.getString("hint_text"));
        dto.setLlmExplanation(rs.getString("llm_explanation"));
        dto.setAffectedBlockIds(rs.getString("affected_block_ids"));
        dto.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return dto;
    };

    @Override
    public void save(HintUsageDTO usage) {
        UUID id = usage.getId() != null ? usage.getId() : UUID.randomUUID();
        usage.setId(id);

        String sql = """
            INSERT INTO hint_usage (id, user_id, task_id, hint_level,
                                    block_order_at_request, findings, hint_text, llm_explanation, affected_block_ids)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
        """;

        jdbc.update(sql, id, usage.getUserId(), usage.getTaskId(), usage.getHintLevel(),
                    usage.getBlockOrderAtRequest(), usage.getFindings(),
                    usage.getHintText(), usage.getLlmExplanation(), usage.getAffectedBlockIds());
    }

    @Override
    public List<HintUsageDTO> findByTaskId(UUID taskId) {
        return jdbc.query("SELECT * FROM hint_usage WHERE task_id = ? ORDER BY created_at", ROW_MAPPER, taskId);
    }
}
