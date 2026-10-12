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
package de.uni_passau.fim.se2.infrastructure.persistence.mapper;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.UUID;

public class TieredTaskSequenceRowMapper implements RowMapper<TieredTaskSequenceDTO> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public TieredTaskSequenceDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TieredTaskSequenceDTO dto = new TieredTaskSequenceDTO();

        dto.setId(rs.getObject("id", UUID.class));
        dto.setProgrammingLanguage(rs.getString("programming_language"));
        dto.setCodeSnippet(rs.getString("code_snippet"));
        var ts = rs.getTimestamp("created_at");
        if (ts != null) {
            dto.setCreatedAt(ts.toLocalDateTime());
        }

        dto.setTaskDescription(rs.getString("task_description"));
        if (hasColumn(rs, "title")) {
            dto.setTitle(rs.getString("title"));
        }
        dto.setVersionId(rs.getObject("version_id", UUID.class));
        String strategyId = rs.getObject("strategyname", String.class);
        dto.setStrategyName(strategyId);

        dto.setOwnerId(rs.getObject("owner_id", UUID.class));
        dto.setTargetLanguage(rs.getString("target_language"));

        // AP20: Parse constraint_config JSONB
        String constraintJson = rs.getString("constraint_config");
        if (constraintJson != null && !constraintJson.isBlank()) {
            try {
                dto.setConstraints(JSON.readValue(constraintJson, GenerationConstraints.class));
            } catch (JsonProcessingException e) {
                // Log but don't fail — treat as unconstrained
                dto.setConstraints(null);
            }
        }

        // AP21: Parse tier_count (default 3 for legacy rows)
        int tierCount = rs.getInt("tier_count");
        dto.setTierCount(tierCount > 0 ? tierCount : 3);

        // AP19: Parse curriculum_topic_id
        dto.setCurriculumTopicId(rs.getObject("curriculum_topic_id", UUID.class));
        // Provenance; absent from queries that predate the column, hence the guard.
        if (hasColumn(rs, "copied_from")) {
            dto.setCopiedFrom(rs.getObject("copied_from", UUID.class));
        }
        // Whether that copy has since been edited. Same guard, same reason.
        if (hasColumn(rs, "diverged_at")) {
            var diverged = rs.getTimestamp("diverged_at");
            dto.setDivergedAt(diverged == null ? null : diverged.toInstant());
        }

        return dto;
    }

    /**
     * Whether the result set carries a column.
     *
     * <p>This mapper is shared by several queries, and not all of them select every column —
     * {@code loadAll} and {@code findByOwnerId} have their own column lists. Asking first is
     * cheaper than keeping every one of them in step, and far cheaper than the alternative,
     * which is a mapper that throws on the query that forgot a column.
     */
    private static boolean hasColumn(ResultSet rs, String column) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }
}