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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.ClassDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassRepository {

    ClassDTO save(ClassDTO classDTO);

    Optional<ClassDTO> findById(UUID id);

    /** Load class with students and worksheets */
    Optional<ClassDTO> findByIdWithDetails(UUID id);

    List<ClassDTO> findByOwnerId(UUID ownerId);

    /** Admin: find all classes */
    List<ClassDTO> findAll();

    void update(ClassDTO classDTO);

    void deleteById(UUID id);

    void addStudent(UUID classId, UUID studentId);

    void removeStudent(UUID classId, UUID studentId);

    void addWorksheet(UUID classId, UUID worksheetId);

    void removeWorksheet(UUID classId, UUID worksheetId);

    /** Find classes a student belongs to */
    List<ClassDTO> findByStudentId(UUID studentId);

    /** Update tracking_enabled flag */
    void updateTrackingEnabled(UUID classId, boolean enabled);

    /** Check if tracking is enabled for a class */
    boolean isTrackingEnabled(UUID classId);

    /** Find the class ID for a student (first class found) */
    UUID findClassIdForStudent(UUID studentId);
}
