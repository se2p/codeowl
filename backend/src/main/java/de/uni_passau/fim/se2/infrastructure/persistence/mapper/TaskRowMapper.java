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
package de.uni_passau.fim.se2.infrastructure.persistence.mapper;

import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TaskRowMapper implements RowMapper<TaskDTO> {

    private static int intOrZero(ResultSet rs, String col) throws SQLException {
        Integer v = rs.getObject(col, Integer.class); // erlaubt NULL
        return v == null ? 0 : v;
    }

    @Override
    public TaskDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TaskDTO dto = new TaskDTO();

        // Pflichtfelder
        dto.setId(rs.getObject("id", UUID.class));
        dto.setStatement(rs.getString("statement"));
        dto.setSolution(rs.getString("solution"));
        dto.setTierLevel(rs.getInt("tier_level"));
        dto.setCyclomaticComplexity(intOrZero(rs, "cyclomatic_complexity"));
        dto.setLinesOfCode(intOrZero(rs, "lines_of_code"));
        dto.setDistinctIdentifierCount(intOrZero(rs, "distinct_identifier_count"));
        dto.setMaximalNestingDepth(intOrZero(rs, "maximal_nesting_depth"));
        dto.setMethodCallCount(intOrZero(rs, "method_call_count"));
        dto.setSequenceId(rs.getObject("sequence_id", UUID.class));
        dto.setDistractors(rs.getString("distractors"));
        dto.setDistractorPlan(rs.getString("distractor_plan"));
        dto.setDistractorsEnabled(rs.getBoolean("distractors_enabled"));
        dto.setContextBefore(rs.getString("context_before"));
        dto.setContextAfter(rs.getString("context_after"));
        dto.setSplitPlan(rs.getString("split_plan")); // AP31

        return dto;
    }
}