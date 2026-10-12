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

import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import de.uni_passau.fim.se2.domain.interfaces.AdminAuditLogRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.AdminAuditLogRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgreAdminAuditLogDao implements AdminAuditLogRepository {

    private final JdbcTemplate jdbc;
    private final AdminAuditLogRowMapper rowMapper;

    @Override
    public AdminAuditLogDTO save(AdminAuditLogDTO entry) {
        if (entry.getCreatedAt() == null) entry.setCreatedAt(LocalDateTime.now());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO admin_audit_log
                      (admin_id, action, ip_address, user_agent, bytes_sent, rows_sent, success, error_msg, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, entry.getAdminId());
            ps.setString(2, entry.getAction());
            ps.setString(3, entry.getIpAddress());
            ps.setString(4, entry.getUserAgent());
            if (entry.getBytesSent() != null) ps.setLong(5, entry.getBytesSent());
            else ps.setNull(5, Types.BIGINT);
            if (entry.getRowsSent() != null) ps.setLong(6, entry.getRowsSent());
            else ps.setNull(6, Types.BIGINT);
            ps.setBoolean(7, entry.isSuccess());
            ps.setString(8, entry.getErrorMsg());
            ps.setTimestamp(9, Timestamp.valueOf(entry.getCreatedAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) entry.setId(key.longValue());
        return entry;
    }

    @Override
    public List<AdminAuditLogDTO> findRecent(int limit) {
        return jdbc.query(
                "SELECT * FROM admin_audit_log ORDER BY created_at DESC LIMIT ?",
                rowMapper, limit
        );
    }

    @Override
    public List<AdminAuditLogDTO> findByAdminId(UUID adminId, int limit) {
        return jdbc.query(
                "SELECT * FROM admin_audit_log WHERE admin_id = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper, adminId, limit
        );
    }
}
