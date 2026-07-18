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

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SequenceTagRowMapper implements RowMapper<SequenceTagDTO> {
    @Override
    public SequenceTagDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        SequenceTagDTO dto = new SequenceTagDTO();
        dto.setSequenceId(rs.getObject("sequence_id", UUID.class));
        dto.setTagName(rs.getString("tag_name"));
        dto.setSource(rs.getString("source"));
        float conf = rs.getFloat("confidence");
        if (!rs.wasNull()) dto.setConfidence(conf);
        var ts = rs.getTimestamp("created_at");
        if (ts != null) dto.setCreatedAt(ts.toLocalDateTime());
        return dto;
    }
}
