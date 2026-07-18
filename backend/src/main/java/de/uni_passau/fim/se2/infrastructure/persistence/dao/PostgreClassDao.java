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

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgreClassDao implements ClassRepository {

    private final JdbcTemplate jdbc;

    @Override
    public ClassDTO save(ClassDTO classDTO) {
        if (classDTO.getId() == null) {
            classDTO.setId(UUID.randomUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        classDTO.setCreatedAt(now);
        classDTO.setUpdatedAt(now);

        jdbc.update("""
            INSERT INTO classes (id, name, description, owner_id, tracking_enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                classDTO.getId(),
                classDTO.getName(),
                classDTO.getDescription(),
                classDTO.getOwnerId(),
                classDTO.isTrackingEnabled(),
                Timestamp.valueOf(classDTO.getCreatedAt()),
                Timestamp.valueOf(classDTO.getUpdatedAt())
        );

        log.info("Created class: id={}, name='{}'", classDTO.getId(), classDTO.getName());
        return classDTO;
    }

    @Override
    public Optional<ClassDTO> findById(UUID id) {
        List<ClassDTO> results = jdbc.query(
                "SELECT * FROM classes WHERE id = ?",
                (rs, rowNum) -> ClassDTO.builder()
                        .id(rs.getObject("id", UUID.class))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .ownerId(rs.getObject("owner_id", UUID.class))
                        .trackingEnabled(rs.getBoolean("tracking_enabled"))
                        .createdAt(rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .updatedAt(rs.getTimestamp("updated_at") != null
                                ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                        .build(),
                id
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<ClassDTO> findByIdWithDetails(UUID id) {
        Optional<ClassDTO> classOpt = findById(id);
        if (classOpt.isEmpty()) return Optional.empty();

        ClassDTO classDTO = classOpt.get();

        // Load students
        List<ClassDTO.StudentInfo> students = jdbc.query("""
            SELECT u.user_id, u.first_name, u.last_name, u.email,
                   at.token_value as invite_token
            FROM class_students cs
            JOIN users u ON u.user_id = cs.student_id
            LEFT JOIN auth_tokens at ON at.user_id = u.user_id
                AND at.token_type = 'STUDENT_INVITE'
                AND at.used = FALSE
                AND at.expires_at > NOW()
            WHERE cs.class_id = ?
            ORDER BY u.last_name, u.first_name
            """,
                (rs, rowNum) -> ClassDTO.StudentInfo.builder()
                        .userId(rs.getObject("user_id", UUID.class))
                        .firstName(rs.getString("first_name"))
                        .lastName(rs.getString("last_name"))
                        .email(rs.getString("email"))
                        .inviteToken(rs.getString("invite_token"))
                        .build(),
                id
        );
        classDTO.setStudents(students);

        // Load worksheets
        List<ClassDTO.WorksheetInfo> worksheets = jdbc.query("""
            SELECT w.id as worksheet_id, w.name, w.description
            FROM class_worksheets cw
            JOIN worksheets w ON w.id = cw.worksheet_id
            WHERE cw.class_id = ?
            ORDER BY w.name
            """,
                (rs, rowNum) -> ClassDTO.WorksheetInfo.builder()
                        .worksheetId(rs.getObject("worksheet_id", UUID.class))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .build(),
                id
        );
        classDTO.setWorksheets(worksheets);

        return Optional.of(classDTO);
    }

    @Override
    public List<ClassDTO> findByOwnerId(UUID ownerId) {
        return jdbc.query("""
            SELECT c.*, COUNT(DISTINCT cs.student_id) as student_count,
                   COUNT(DISTINCT cw.worksheet_id) as worksheet_count
            FROM classes c
            LEFT JOIN class_students cs ON cs.class_id = c.id
            LEFT JOIN class_worksheets cw ON cw.class_id = c.id
            WHERE c.owner_id = ?
            GROUP BY c.id
            ORDER BY c.created_at DESC
            """,
                (rs, rowNum) -> ClassDTO.builder()
                        .id(rs.getObject("id", UUID.class))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .ownerId(rs.getObject("owner_id", UUID.class))
                        .trackingEnabled(rs.getBoolean("tracking_enabled"))
                        .createdAt(rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .updatedAt(rs.getTimestamp("updated_at") != null
                                ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                        .build(),
                ownerId
        );
    }

    @Override
    public List<ClassDTO> findAll() {
        return jdbc.query("""
            SELECT * FROM classes ORDER BY created_at DESC
            """,
                (rs, rowNum) -> ClassDTO.builder()
                        .id(rs.getObject("id", UUID.class))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .ownerId(rs.getObject("owner_id", UUID.class))
                        .trackingEnabled(rs.getBoolean("tracking_enabled"))
                        .createdAt(rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .updatedAt(rs.getTimestamp("updated_at") != null
                                ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                        .build()
        );
    }

    @Override
    public void update(ClassDTO classDTO) {
        classDTO.setUpdatedAt(LocalDateTime.now());
        int rows = jdbc.update("""
            UPDATE classes SET name = ?, description = ?, updated_at = ?
            WHERE id = ?
            """,
                classDTO.getName(),
                classDTO.getDescription(),
                Timestamp.valueOf(classDTO.getUpdatedAt()),
                classDTO.getId()
        );
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + classDTO.getId());
        }
        log.info("Updated class: id={}, name='{}'", classDTO.getId(), classDTO.getName());
    }

    @Override
    public void deleteById(UUID id) {
        // Cascading deletes handle class_students and class_worksheets
        int rows = jdbc.update("DELETE FROM classes WHERE id = ?", id);
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + id);
        }
        log.info("Deleted class: id={}", id);
    }

    @Override
    public void addStudent(UUID classId, UUID studentId) {
        try {
            jdbc.update("""
                INSERT INTO class_students (class_id, student_id)
                VALUES (?, ?)
                ON CONFLICT (class_id, student_id) DO NOTHING
                """, classId, studentId);
            log.info("Added student {} to class {}", studentId, classId);
        } catch (DataAccessException e) {
            throw new DomainValidationException("Fehler beim Hinzufügen des Schülers: " + e.getMessage());
        }
    }

    @Override
    public void removeStudent(UUID classId, UUID studentId) {
        jdbc.update("DELETE FROM class_students WHERE class_id = ? AND student_id = ?",
                classId, studentId);
        log.info("Removed student {} from class {}", studentId, classId);
    }

    @Override
    public void addWorksheet(UUID classId, UUID worksheetId) {
        try {
            jdbc.update("""
                INSERT INTO class_worksheets (class_id, worksheet_id)
                VALUES (?, ?)
                ON CONFLICT (class_id, worksheet_id) DO NOTHING
                """, classId, worksheetId);
            log.info("Assigned worksheet {} to class {}", worksheetId, classId);
        } catch (DataAccessException e) {
            throw new DomainValidationException("Fehler beim Zuweisen des Worksheets: " + e.getMessage());
        }
    }

    @Override
    public void removeWorksheet(UUID classId, UUID worksheetId) {
        jdbc.update("DELETE FROM class_worksheets WHERE class_id = ? AND worksheet_id = ?",
                classId, worksheetId);
        log.info("Removed worksheet {} from class {}", worksheetId, classId);
    }

    @Override
    public List<ClassDTO> findByStudentId(UUID studentId) {
        return jdbc.query("""
            SELECT c.* FROM classes c
            JOIN class_students cs ON cs.class_id = c.id
            WHERE cs.student_id = ?
            ORDER BY c.name
            """,
                (rs, rowNum) -> ClassDTO.builder()
                        .id(rs.getObject("id", UUID.class))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .ownerId(rs.getObject("owner_id", UUID.class))
                        .trackingEnabled(rs.getBoolean("tracking_enabled"))
                        .createdAt(rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .updatedAt(rs.getTimestamp("updated_at") != null
                                ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                        .build(),
                studentId
        );
    }

    // ── Tracking (AP5) ──────────────────────────────────────

    public void updateTrackingEnabled(UUID classId, boolean enabled) {
        int rows = jdbc.update("""
            UPDATE classes SET tracking_enabled = ?, updated_at = ?
            WHERE id = ?
            """,
                enabled,
                Timestamp.valueOf(LocalDateTime.now()),
                classId
        );
        if (rows == 0) {
            throw new DomainValidationException("Klasse nicht gefunden: id=" + classId);
        }
        log.info("Updated tracking_enabled={} for class id={}", enabled, classId);
    }

    public boolean isTrackingEnabled(UUID classId) {
        List<Boolean> results = jdbc.query(
                "SELECT tracking_enabled FROM classes WHERE id = ?",
                (rs, rowNum) -> rs.getBoolean("tracking_enabled"),
                classId
        );
        return results.stream().findFirst().orElse(false);
    }

    /**
     * Returns the class_id for a student (first class found).
     * Used to determine tracking context when logging feedback.
     */
    public UUID findClassIdForStudent(UUID studentId) {
        List<UUID> results = jdbc.query(
                "SELECT class_id FROM class_students WHERE student_id = ? LIMIT 1",
                (rs, rowNum) -> rs.getObject("class_id", UUID.class),
                studentId
        );
        return results.stream().findFirst().orElse(null);
    }
}
