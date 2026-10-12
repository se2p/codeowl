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

import java.sql.ResultSet;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class CurriculumTopicRowMapper implements RowMapper<CurriculumTopicDTO> {
    @Override
    public CurriculumTopicDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        CurriculumTopicDTO dto = new CurriculumTopicDTO();
        dto.setId(UUID.fromString(rs.getString("id")));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        // AP19: extended fields
        int grade = rs.getInt("grade_level");
        dto.setGradeLevel(rs.wasNull() ? null : grade);
        dto.setTrack(rs.getString("track"));
        dto.setLearningArea(rs.getString("learning_area"));
        String parentStr = rs.getString("parent_id");
        dto.setParentId(parentStr != null ? UUID.fromString(parentStr) : null);
        dto.setSortOrder(rs.getInt("sort_order"));
        // English translations — null for a topic that has none; the columns
        // themselves come from 11_migration_curriculum_i18n.sql, which is required.
        dto.setNameEn(rs.getString("name_en"));
        dto.setDescriptionEn(rs.getString("description_en"));
        dto.setTrackEn(rs.getString("track_en"));
        dto.setEducationLevel(rs.getString("education_level"));
        return dto;
    }
}