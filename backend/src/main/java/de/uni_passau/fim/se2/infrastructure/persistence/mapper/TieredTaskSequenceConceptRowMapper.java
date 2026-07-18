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

import java.sql.ResultSet;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceConceptDTO;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class TieredTaskSequenceConceptRowMapper implements RowMapper<TieredTaskSequenceConceptDTO> {
    @Override
    public TieredTaskSequenceConceptDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TieredTaskSequenceConceptDTO dto = new TieredTaskSequenceConceptDTO();
        dto.setSequenceId(UUID.fromString(rs.getString("sequence_id")));
        dto.setConceptId(UUID.fromString(rs.getString("concept_id")));
        return dto;
    }
}