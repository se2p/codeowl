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

import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.UserRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgreUserDao implements UserRepository {

    private final JdbcTemplate jdbc;
    private final UserRowMapper rowMapper;

    @Override
    public UserDTO save(UserDTO user) {
        if (user.getUserId() == null) {
            user.setUserId(UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        jdbc.update("""
            INSERT INTO users (user_id, email, first_name, last_name, password_hash, role, is_verified, is_active, must_change_password, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::user_role, ?, ?, ?, ?, ?)
            """,
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPasswordHash(),
                user.getRole().name(),
                user.isVerified(),
                user.isActive(),
                user.isMustChangePassword(),
                Timestamp.valueOf(user.getCreatedAt()),
                Timestamp.valueOf(user.getUpdatedAt())
        );
        return user;
    }

    @Override
    public Optional<UserDTO> findById(UUID userId) {
        List<UserDTO> results = jdbc.query(
                "SELECT * FROM users WHERE user_id = ?",
                rowMapper, userId
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<UserDTO> findByEmail(String email) {
        List<UserDTO> results = jdbc.query(
                "SELECT * FROM users WHERE LOWER(email) = LOWER(?)",
                rowMapper, email
        );
        return results.stream().findFirst();
    }

    @Override
    public List<UserDTO> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY created_at DESC", rowMapper);
    }

    @Override
    public void update(UserDTO user) {
        user.setUpdatedAt(LocalDateTime.now());
        jdbc.update("""
            UPDATE users SET email = ?, first_name = ?, last_name = ?, password_hash = ?,
                role = ?::user_role, is_verified = ?, is_active = ?, must_change_password = ?, updated_at = ?
            WHERE user_id = ?
            """,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPasswordHash(),
                user.getRole().name(),
                user.isVerified(),
                user.isActive(),
                user.isMustChangePassword(),
                Timestamp.valueOf(user.getUpdatedAt()),
                user.getUserId()
        );
    }

    @Override
    public void deleteById(UUID userId) {
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)",
                Integer.class, email
        );
        return count != null && count > 0;
    }

    @Override
    public List<UserDTO> findAllPaginated(String search, String sortColumn, String sortDir, int offset, int limit) {
        // Whitelist sort columns to prevent SQL injection
        String safeSort = switch (sortColumn) {
            case "first_name" -> "first_name";
            case "last_name" -> "last_name";
            case "role" -> "role";
            case "is_verified" -> "is_verified";
            case "created_at" -> "created_at";
            default -> "email";
        };
        String safeDir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            return jdbc.query(
                    "SELECT * FROM users WHERE LOWER(email) LIKE ? OR LOWER(first_name) LIKE ? OR LOWER(last_name) LIKE ? ORDER BY " + safeSort + " " + safeDir + " LIMIT ? OFFSET ?",
                    rowMapper, pattern, pattern, pattern, limit, offset
            );
        }
        return jdbc.query(
                "SELECT * FROM users ORDER BY " + safeSort + " " + safeDir + " LIMIT ? OFFSET ?",
                rowMapper, limit, offset
        );
    }

    @Override
    public void setMustChangePassword(UUID userId, boolean mustChangePassword) {
        jdbc.update(
                "UPDATE users SET must_change_password = ?, updated_at = ? WHERE user_id = ?",
                mustChangePassword, Timestamp.valueOf(LocalDateTime.now()), userId
        );
    }

    @Override
    public long countAll(String search) {
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(email) LIKE ? OR LOWER(first_name) LIKE ? OR LOWER(last_name) LIKE ?",
                    Long.class, pattern, pattern, pattern
            );
            return count != null ? count : 0;
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return count != null ? count : 0;
    }
}
