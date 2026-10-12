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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.api.dto.AddFimStudentsRequest;
import de.uni_passau.fim.se2.api.dto.BulkCreateStudentsRequest;
import de.uni_passau.fim.se2.api.dto.CreateClassRequest;
import de.uni_passau.fim.se2.api.dto.CreateStudentRequest;
import de.uni_passau.fim.se2.application.service.ClassService;
import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.StudentProgressDetailDTO;
import de.uni_passau.fim.se2.domain.entities.StudentTaskStateDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Instant;
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
     * Adds a whole class list at once, as read from a teacher's CSV.
     *
     * All or nothing: a single unusable row rejects the request naming that row,
     * rather than leaving the teacher with a half-imported class.
     *
     * @param id      the UUID of the class
     * @param request the request body containing one first/last name pair per student
     * @return the created students, each with the invite token to send them
     */
    @PostMapping("/{id}/students/bulk")
    public ResponseEntity<List<ClassDTO.StudentInfo>> createStudents(
            @PathVariable UUID id,
            @RequestBody BulkCreateStudentsRequest request) {
        // No @Valid: the service does the checking, so a bad row comes back as
        // a 400 naming it rather than as the catch-all's generic 500.
        List<ClassService.NewStudent> rows = request.getStudents() == null ? List.of()
                : request.getStudents().stream()
                        .map(s -> new ClassService.NewStudent(s.getFirstName(), s.getLastName()))
                        .toList();
        return ResponseEntity.ok(classService.createStudents(id, rows));
    }

    /**
     * Adds students by FIM username, whether or not they have logged in to CodeOwl yet.
     *
     * @param id      the UUID of the class
     * @param request the FIM usernames, one typed in or many from a CSV
     * @return the added students
     */
    @PostMapping("/{id}/students/fim")
    public ResponseEntity<List<ClassDTO.StudentInfo>> addFimStudents(
            @PathVariable UUID id,
            @RequestBody AddFimStudentsRequest request) {
        // No @Valid, as for the CSV import: the service names the offending username.
        return ResponseEntity.ok(classService.addFimStudents(id, request.getUsernames()));
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
     * Sets when a worksheet opens and closes for this class.
     *
     * Any field may be omitted or null to lift that constraint: no release means
     * available at once, no deadline means open indefinitely, no grace period
     * means the deadline is sharp.
     */
    @PutMapping("/{id}/worksheets/{worksheetId}/schedule")
    public ResponseEntity<?> setWorksheetSchedule(
            @PathVariable UUID id,
            @PathVariable UUID worksheetId,
            @RequestBody WorksheetScheduleRequest request) {
        classService.setWorksheetSchedule(id, worksheetId,
                request.releaseAt(), request.deadlineAt(), request.graceMinutes());
        return ResponseEntity.noContent().build();
    }

    /**
     * Publishes an assignment immediately, so the class can see it.
     *
     * A newly assigned worksheet is hidden until this is called or its release
     * date arrives, so students never meet one that is still being prepared.
     */
    @PostMapping("/{id}/worksheets/{worksheetId}/publish")
    public ResponseEntity<?> publishWorksheet(@PathVariable UUID id, @PathVariable UUID worksheetId) {
        classService.setWorksheetPublished(id, worksheetId, true);
        return ResponseEntity.noContent().build();
    }

    /** Takes an assignment back out of sight, e.g. one published by mistake. */
    @PostMapping("/{id}/worksheets/{worksheetId}/unpublish")
    public ResponseEntity<?> unpublishWorksheet(@PathVariable UUID id, @PathVariable UUID worksheetId) {
        classService.setWorksheetPublished(id, worksheetId, false);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends an assignment now: the deadline becomes this moment, so students may
     * still read the tasks but no longer work on them.
     */
    @PostMapping("/{id}/worksheets/{worksheetId}/close")
    public ResponseEntity<?> closeWorksheet(@PathVariable UUID id, @PathVariable UUID worksheetId) {
        classService.closeWorksheetNow(id, worksheetId);
        return ResponseEntity.noContent().build();
    }

    /** All fields optional; null clears that part of the schedule. */
    public record WorksheetScheduleRequest(Instant releaseAt, Instant deadlineAt, Integer graceMinutes) {}

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

    /**
     * One student's work across this class's assigned worksheets: every task,
     * how many times they attempted it, and whether it was solved.
     *
     * @param classId   the UUID of the class
     * @param studentId the UUID of the student
     */
    @GetMapping("/{classId}/students/{studentId}/progress")
    public ResponseEntity<StudentProgressDetailDTO> getStudentProgressDetail(
            @PathVariable UUID classId,
            @PathVariable UUID studentId) {
        return ResponseEntity.ok(classService.getStudentProgressDetail(classId, studentId));
    }

    /**
     * What one student last built for one task, beside the solution it was meant to become.
     *
     * <p>Class-scoped on purpose. The arrangement is keyed by student and task alone, so reading
     * it needs a reason, and the reason is that this class was assigned the worksheet the task
     * sits in. That also keeps the authorization identical to the progress page this is opened
     * from, rather than inventing a second story for the same data.
     *
     * @param classId   the UUID of the class
     * @param studentId the UUID of the student
     * @param taskId    the UUID of the task, which must belong to this class's worksheets
     */
    @GetMapping("/{classId}/students/{studentId}/tasks/{taskId}/state")
    public ResponseEntity<StudentTaskStateDTO> getStudentTaskState(
            @PathVariable UUID classId,
            @PathVariable UUID studentId,
            @PathVariable UUID taskId) {
        return ResponseEntity.ok(classService.getStudentTaskState(classId, studentId, taskId));
    }
}