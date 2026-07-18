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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.api.dto.CreateClassRequest;
import de.uni_passau.fim.se2.api.dto.CreateStudentRequest;
import de.uni_passau.fim.se2.application.service.ClassService;
import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * Creates a new class.
     *
     * @param request the request body containing the class name and description
     * @return the created class
     */
    @PostMapping
    public ResponseEntity<ClassDTO> createClass(@Valid @RequestBody CreateClassRequest request) {
        ClassDTO created = classService.createClass(request.getName(), request.getDescription());
        return ResponseEntity.ok(created);
    }

    /**
     * Retrieves the current user's classes.
     *
     * @return a list of classes
     */
    @GetMapping
    public ResponseEntity<List<ClassDTO>> getMyClasses() {
        return ResponseEntity.ok(classService.getMyClasses());
    }

    /**
     * Retrieves a class by its unique identifier.
     *
     * @param id the UUID of the class
     * @return the class with the specified ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ClassDTO> getClassById(@PathVariable UUID id) {
        return ResponseEntity.ok(classService.getClassById(id));
    }

    /**
     * Updates a class by its unique identifier.
     *
     * @param id      the UUID of the class
     * @param request the request body containing updated class name and description
     * @return the updated class
     */
    @PutMapping("/{id}")
    public ResponseEntity<ClassDTO> updateClass(
            @PathVariable UUID id,
            @Valid @RequestBody CreateClassRequest request) {
        ClassDTO updated = classService.updateClass(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a class by its unique identifier.
     *
     * @param id the UUID of the class
     * @return a message indicating successful deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable UUID id) {
        classService.deleteClass(id);
        return ResponseEntity.ok(Map.of("message", "Klasse gelöscht."));
    }

    /**
     * Adds a new student to a specified class.
     *
     * @param id      the UUID of the class
     * @param request the request body containing the student's first and last name
     * @return the created student information
     */
    @PostMapping("/{id}/students")
    public ResponseEntity<ClassDTO.StudentInfo> createStudent(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStudentRequest request) {
        ClassDTO.StudentInfo student = classService.createStudent(
                id, request.getFirstName(), request.getLastName());
        return ResponseEntity.ok(student);
    }

    /**
     * Resets a student's password to a teacher-readable temporary password.
     * The student must change it on next login.
     *
     * @param id        the UUID of the class
     * @param studentId the UUID of the student
     * @param body      optional { "locale": "de" } — defaults to "en"
     * @return { "tempPassword": "Happy-Dog-42" }
     */
    @PostMapping("/{id}/students/{studentId}/reset-temp-password")
    public ResponseEntity<?> resetTempPassword(
            @PathVariable UUID id,
            @PathVariable UUID studentId,
            @RequestBody(required = false) Map<String, String> body) {
        String locale = (body != null) ? body.getOrDefault("locale", "en") : "en";
        String tempPassword = classService.resetTempPassword(id, studentId, locale);
        return ResponseEntity.ok(Map.of("tempPassword", tempPassword));
    }

    /**
     * Removes a student from a specified class.
     *
     * @param id        the UUID of the class
     * @param studentId the UUID of the student
     * @return a message indicating successful removal
     */
    @DeleteMapping("/{id}/students/{studentId}")
    public ResponseEntity<?> removeStudent(
            @PathVariable UUID id,
            @PathVariable UUID studentId) {
        classService.removeStudent(id, studentId);
        return ResponseEntity.ok(Map.of("message", "Schüler entfernt."));
    }

    /**
     * Assigns a worksheet to a specified class.
     *
     * @param id          the UUID of the class
     * @param worksheetId the UUID of the worksheet
     * @return a message indicating successful assignment
     */
    @PostMapping("/{id}/worksheets/{worksheetId}")
    public ResponseEntity<?> assignWorksheet(
            @PathVariable UUID id,
            @PathVariable UUID worksheetId) {
        classService.assignWorksheet(id, worksheetId);
        return ResponseEntity.ok(Map.of("message", "Worksheet zugewiesen."));
    }

    /**
     * Removes a worksheet assignment from a specified class.
     *
     * @param id          the UUID of the class
     * @param worksheetId the UUID of the worksheet
     * @return a message indicating successful removal
     */
    @DeleteMapping("/{id}/worksheets/{worksheetId}")
    public ResponseEntity<?> removeWorksheet(
            @PathVariable UUID id,
            @PathVariable UUID worksheetId) {
        classService.removeWorksheet(id, worksheetId);
        return ResponseEntity.ok(Map.of("message", "Worksheet-Zuweisung entfernt."));
    }

    /**
     * Toggles tracking for a specified class.
     *
     * @param id   the UUID of the class
     * @param body the request body containing the tracking enabled status
     * @return a message indicating the tracking status
     */
    @PutMapping("/{id}/tracking")
    public ResponseEntity<?> toggleTracking(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("enabled", false);
        classService.setTrackingEnabled(id, enabled);
        return ResponseEntity.ok(Map.of(
                "message", enabled ? "Tracking aktiviert." : "Tracking deaktiviert.",
                "trackingEnabled", enabled
        ));
    }

    /**
     * Retrieves the progress of a specified class.
     *
     * @param classId the UUID of the class
     * @return a list of progress data maps
     */
    @GetMapping("/{classId}/progress")
    public ResponseEntity<List<Map<String, Object>>> getClassProgress(@PathVariable UUID classId) {
        List<Map<String, Object>> progress = classService.getClassProgress(classId);
        return ResponseEntity.ok(progress);
    }
}