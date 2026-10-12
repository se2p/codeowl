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

import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class AdminAuditLogRowMapper implements RowMapper<AdminAuditLogDTO> {
    @Override
    public AdminAuditLogDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");

        // Read nullable BIGINT columns before checking wasNull()
        long bytesSentRaw = rs.getLong("bytes_sent");
        boolean bytesSentNull = rs.wasNull();
        long rowsSentRaw = rs.getLong("rows_sent");
        boolean rowsSentNull = rs.wasNull();

        return AdminAuditLogDTO.builder()
                .id(rs.getLong("id"))
                .adminId(UUID.fromString(rs.getString("admin_id")))
                .action(rs.getString("action"))
                .ipAddress(rs.getString("ip_address"))
                .userAgent(rs.getString("user_agent"))
                .bytesSent(bytesSentNull ? null : bytesSentRaw)
                .rowsSent(rowsSentNull ? null : rowsSentRaw)
                .success(rs.getBoolean("success"))
                .errorMsg(rs.getString("error_msg"))
                .createdAt(created != null ? created.toLocalDateTime() : null)
                .build();
    }
}
