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

import de.uni_passau.fim.se2.domain.entities.AuthTokenDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class AuthTokenRowMapper implements RowMapper<AuthTokenDTO> {

    @Override
    public AuthTokenDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AuthTokenDTO.builder()
                .tokenId(UUID.fromString(rs.getString("token_id")))
                .userId(UUID.fromString(rs.getString("user_id")))
                .tokenValue(rs.getString("token_value"))
                .tokenType(AuthTokenDTO.TokenType.valueOf(rs.getString("token_type")))
                .expiresAt(toLocalDateTime(rs.getTimestamp("expires_at")))
                .used(rs.getBoolean("used"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
