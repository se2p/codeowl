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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;
import de.uni_passau.fim.se2.domain.entities.StudentProgressDetailDTO;
import de.uni_passau.fim.se2.domain.entities.StudentTaskStateDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import de.uni_passau.fim.se2.domain.model.FimAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreClassDao;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClassService {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final OwnershipService ownershipService;
    private final TokenService tokenService;
    private final FeedbackLogService feedbackLogService;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final PasswordHasherService passwordHasher;
    private final WorksheetService worksheetService;
    private final TaskPersistenceService taskPersistenceService;
    private final TaskSubmissionService taskSubmissionService;

    // ── CRUD ──────────────────────────────────────────────────

    public ClassDTO createClass(String name, String description) {
        ownershipService.denyStudents("create classes");
        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentifizierung erforderlich.");
        }

        if (name == null || name.isBlank()) {
            throw new DomainValidationException("Klassenname darf nicht leer sein.");
        }

        ClassDTO classDTO = ClassDTO.builder()
                .name(name.trim())
                .description(description != null ? description.trim() : "")
                .ownerId(userId)
                .build();

        return classRepository.save(classDTO);
    }

    public List<ClassDTO> getMyClasses() {
        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentifizierung erforderlich.");
        }

        if (ownershipService.isAdmin()) {
            return classRepository.findAll();
        }

        if (ownershipService.isStudent()) {
            List<ClassDTO> classes = classRepository.findByStudentId(userId);
            // Same filter as the detail view: the overview must not leak a
            // worksheet that has not been released yet, deadline and all.
            classes.forEach(ClassService::hideUnreleased);
            return classes;
        }

        return classRepository.findByOwnerId(userId);
    }

    public ClassDTO getClassById(UUID classId) {
        ClassDTO classDTO = classRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentifizierung erforderlich.");
        }

        // Access: owner, admin, or student in the class
        if (!ownershipService.isOwnerOrAdmin(classDTO.getOwnerId())) {
            boolean isStudentInClass = classDTO.getStudents().stream()
                    .anyMatch(s -> s.getUserId().equals(userId));
            if (!isStudentInClass) {
                throw new AccessDeniedException("Kein Zugriff auf diese Klasse.");
            }
        }

        if (ownershipService.isStudent()) {
            hideUnreleased(classDTO);
        }

        return classDTO;
    }

    /**
     * Drops the assignments a student is not meant to know about yet.
     *
     * A worksheet that has not been released does not exist as far as a student
     * is concerned. Done here rather than in the query, so the teacher's own
     * view keeps every assignment whatever its state.
     *
     * A closed worksheet stays: students may still read it, and seeing that its
     * deadline has passed is the point.
     */
    private static void hideUnreleased(ClassDTO classDTO) {
        if (classDTO.getWorksheets() == null) return;
        classDTO.setWorksheets(classDTO.getWorksheets().stream()
                .filter(w -> w.getAvailability() == null || w.getAvailability().visibleToStudents())
                .toList());
    }

    public ClassDTO updateClass(UUID classId, String name, String description) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");

        if (name == null || name.isBlank()) {
            throw new DomainValidationException("Klassenname darf nicht leer sein.");
        }

        classDTO.setName(name.trim());
        classDTO.setDescription(description != null ? description.trim() : "");
        classRepository.update(classDTO);

        return classDTO;
    }

    public void deleteClass(UUID classId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
        classRepository.deleteById(classId);
    }

    // ── Student Management ────────────────────────────────────

    /** One row of a class list, before it has been checked. */
    public record NewStudent(String firstName, String lastName) {}

    /**
     * users.first_name and last_name are varchar(100); refusing here turns what
     * would be a 500 from Postgres into a message naming the offending row.
     */
    private static final int MAX_NAME_LENGTH = 100;

    /**
     * Every row becomes an account, and there is no one-click way back. A file
     * chosen by mistake should be refused rather than acted on; the CSV importer
     * in the frontend applies the same bound before anything is sent.
     */
    private static final int MAX_BULK_STUDENTS = 200;

    /**
     * Creates a new student user and adds them to the class.
     * Returns the student with their invite token.
     */
    @Transactional
    public ClassDTO.StudentInfo createStudent(UUID classId, String firstName, String lastName) {
        requireOwnedClass(classId);
        return createStudent(classId, checked(new NewStudent(firstName, lastName)));
    }

    /**
     * Creates a whole class list at once, as read from a teacher's CSV.
     *
     * All or nothing: every row is checked before any account exists, and the
     * transaction rolls the rest back if a later one fails. A half-imported
     * class, with no way to tell which half took, is worse than none.
     *
     * Names are not treated as unique. Two students really can share one, and
     * refusing the second would be wrong more often than it would be right.
     *
     * @return the created students, each with the invite token to send them
     */
    @Transactional
    public List<ClassDTO.StudentInfo> createStudents(UUID classId, List<NewStudent> students) {
        requireOwnedClass(classId);

        if (students == null || students.isEmpty()) {
            throw new DomainValidationException("Es wurden keine Schüler übergeben.");
        }
        if (students.size() > MAX_BULK_STUDENTS) {
            throw new DomainValidationException(
                    "Höchstens " + MAX_BULK_STUDENTS + " Schüler pro Import (" + students.size() + " übergeben).");
        }

        List<NewStudent> rows = new ArrayList<>(students.size());
        for (int i = 0; i < students.size(); i++) {
            try {
                rows.add(checked(students.get(i)));
            } catch (DomainValidationException e) {
                // The row number is what makes the message actionable.
                throw new DomainValidationException("Zeile " + (i + 1) + ": " + e.getMessage());
            }
        }

        List<ClassDTO.StudentInfo> created = new ArrayList<>(rows.size());
        for (NewStudent row : rows) {
            created.add(createStudent(classId, row));
        }

        log.info("Created {} students in class {}", created.size(), classId);
        return created;
    }

    /**
     * Adds students by FIM username, one typed in or many from a CSV.
     *
     * A student who has never logged in to CodeOwl gets an account up front,
     * linked to the FIM-Account but otherwise empty: the name is the username
     * until the student's first FIM login fills in the real one (see
     * {@code AuthService.loginWithFimAccount}). So the teacher can set up the
     * whole class before anyone has logged in, and whoever logs in first lands
     * in the class.
     *
     * The usernames are not checked against the FIM directory — reading it needs
     * a login — so a mistyped one becomes an account nobody logs in to, which the
     * teacher can remove from the class like any other student.
     *
     * All or nothing, like {@link #createStudents}. A student already in the class
     * is no error; the list a teacher uploads twice should not fail the second time.
     *
     * @return the students as they now are in the class, in the order given
     */
    @Transactional
    public List<ClassDTO.StudentInfo> addFimStudents(UUID classId, List<String> usernames) {
        requireOwnedClass(classId);

        if (usernames == null || usernames.isEmpty()) {
            throw new DomainValidationException("Es wurden keine FIM-Benutzernamen übergeben.");
        }
        if (usernames.size() > MAX_BULK_STUDENTS) {
            throw new DomainValidationException(
                    "Höchstens " + MAX_BULK_STUDENTS + " Schüler pro Import (" + usernames.size() + " übergeben).");
        }

        // Check every row, and resolve the accounts that exist, before changing anything.
        // Messages name the username rather than a row: the frontend drops headings
        // and blank lines, so a position here is not a line in the teacher's file.
        Map<String, Optional<UserDTO>> accounts = new LinkedHashMap<>();
        for (String username : usernames) {
            String uid = FimAccount.normalizeUid(username);
            if (!FimAccount.isValidUid(uid)) {
                throw new DomainValidationException("\"" + (username == null ? "" : username.trim())
                        + "\" ist kein gültiger FIM-Benutzername.");
            }
            if (accounts.containsKey(uid)) {
                continue;
            }
            Optional<UserDTO> existing = userRepository.findByFimUid(uid);
            if (existing.isPresent() && existing.get().getRole() != UserDTO.UserRole.STUDENT) {
                throw new DomainValidationException("Der FIM-Account \"" + uid
                        + "\" gehört zu einem Lehrer- oder Admin-Konto und kann nicht als Schüler hinzugefügt werden.");
            }
            accounts.put(uid, existing);
        }

        List<ClassDTO.StudentInfo> added = new ArrayList<>(accounts.size());
        for (Map.Entry<String, Optional<UserDTO>> entry : accounts.entrySet()) {
            UserDTO student = entry.getValue().orElseGet(() -> createFimStudent(entry.getKey()));
            classRepository.addStudent(classId, student.getUserId());
            added.add(ClassDTO.StudentInfo.builder()
                    .userId(student.getUserId())
                    .firstName(student.getFirstName())
                    .lastName(student.getLastName())
                    .email(student.getEmail())
                    .fimUid(student.getFimUid())
                    .build());
        }

        log.info("Added {} FIM students to class {}", added.size(), classId);
        return added;
    }

    /** Creates the placeholder account for a FIM student who has not logged in yet. */
    private UserDTO createFimStudent(String uid) {
        UserDTO student = UserDTO.builder()
                .firstName(uid)
                .lastName("")
                .fimUid(uid)
                .role(UserDTO.UserRole.STUDENT)
                .verified(true) // logging in goes through the FIM directory, not an email
                .active(true)
                .build();
        userRepository.save(student);
        log.info("Created FIM student {} ({})", uid, student.getUserId());
        return student;
    }

    /** Loads the class and refuses anyone who is neither its owner nor an admin. */
    private ClassDTO requireOwnedClass(UUID classId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));
        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
        return classDTO;
    }

    /** Trims a row and rejects it if it could not become an account. */
    private static NewStudent checked(NewStudent student) {
        String firstName = student.firstName() != null ? student.firstName().trim() : "";
        String lastName = student.lastName() != null ? student.lastName().trim() : "";

        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new DomainValidationException("Vor- und Nachname des Schülers sind erforderlich.");
        }
        if (firstName.length() > MAX_NAME_LENGTH || lastName.length() > MAX_NAME_LENGTH) {
            throw new DomainValidationException(
                    "Vor- und Nachname dürfen höchstens " + MAX_NAME_LENGTH + " Zeichen lang sein.");
        }
        return new NewStudent(firstName, lastName);
    }

    /** Creates one already-checked student in an already-authorised class. */
    private ClassDTO.StudentInfo createStudent(UUID classId, NewStudent row) {
        // Create student user (no email/password yet — Anton-Prinzip)
        UserDTO student = UserDTO.builder()
                .firstName(row.firstName())
                .lastName(row.lastName())
                .role(UserDTO.UserRole.STUDENT)
                .verified(false)
                .active(true)
                .build();
        userRepository.save(student);

        // Create invite token for student onboarding
        var inviteToken = tokenService.createStudentInviteToken(student.getUserId());

        // Add to class
        classRepository.addStudent(classId, student.getUserId());

        log.info("Created student {} {} in class {}", row.firstName(), row.lastName(), classId);

        return ClassDTO.StudentInfo.builder()
                .userId(student.getUserId())
                .firstName(student.getFirstName())
                .lastName(student.getLastName())
                .email(null)
                .inviteToken(inviteToken.getTokenValue())
                .build();
    }

    /**
     * Teacher resets a student's password to a readable temporary password.
     * The student must change it on next login (mustChangePassword flag).
     *
     * @param classId   class the teacher owns
     * @param studentId student to reset
     * @param locale    teacher's UI locale ("de" or "en") for word list selection
     * @return the plaintext temporary password (shown once to the teacher)
     */
    @Transactional
    public String resetTempPassword(UUID classId, UUID studentId, String locale) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");

        // Verify student is actually in this class
        boolean studentInClass = classDTO.getStudents() != null && classDTO.getStudents().stream()
                .anyMatch(s -> s.getUserId().equals(studentId));
        if (!studentInClass) {
            // Reload with details to be safe
            ClassDTO full = classRepository.findByIdWithDetails(classId)
                    .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));
            studentInClass = full.getStudents().stream()
                    .anyMatch(s -> s.getUserId().equals(studentId));
            if (!studentInClass) {
                throw new DomainValidationException("Schüler ist nicht Mitglied dieser Klasse.");
            }
        }

        UserDTO student = userRepository.findById(studentId)
                .orElseThrow(() -> new DomainValidationException("Schüler nicht gefunden."));

        if (student.getRole() != UserDTO.UserRole.STUDENT) {
            throw new DomainValidationException("Benutzer ist kein Schüler.");
        }
        // A temporary password would give the account a second way in, one the
        // teacher knows, beside the FIM password the student already has.
        if (student.getFimUid() != null) {
            throw new DomainValidationException(
                    "Dieser Schüler meldet sich mit seinem FIM-Account an; sein Passwort wird bei der FIM zurückgesetzt.");
        }

        String tempPassword = tempPasswordGenerator.generate(locale);
        student.setPasswordHash(passwordHasher.hashPassword(tempPassword));
        student.setMustChangePassword(true);
        // Students with temp passwords are considered verified so they can log in
        student.setVerified(true);
        userRepository.update(student);

        log.info("Teacher reset temp password for student {} in class {}", studentId, classId);
        return tempPassword;
    }

    public void removeStudent(UUID classId, UUID studentId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
        classRepository.removeStudent(classId, studentId);
    }

    // ── Worksheet Assignment ──────────────────────────────────

    public void assignWorksheet(UUID classId, UUID worksheetId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
        classRepository.addWorksheet(classId, worksheetId);
    }

    /**
     * Sets when a worksheet opens and closes for one class.
     *
     * Any field may be null to lift that constraint: no release means available
     * at once, no deadline means open indefinitely, no grace means the deadline
     * is sharp.
     */
    public void setWorksheetSchedule(UUID classId, UUID worksheetId,
                                     Instant releaseAt, Instant deadlineAt, Integer graceMinutes) {
        requireClassOwner(classId);

        if (graceMinutes != null && graceMinutes < 0) {
            throw new DomainValidationException("Die Nachfrist darf nicht negativ sein.");
        }
        if (releaseAt != null && deadlineAt != null && !deadlineAt.isAfter(releaseAt)) {
            // Otherwise the worksheet would close before anyone could open it.
            throw new DomainValidationException("Die Abgabefrist muss nach der Freigabe liegen.");
        }

        dao().updateWorksheetSchedule(classId, worksheetId, releaseAt, deadlineAt, graceMinutes);
        log.info("Schedule set for worksheet {} in class {}", worksheetId, classId);
    }

    /**
     * Publishes an assignment now, or takes it back out of sight.
     *
     * Independent of the release date: a teacher can publish by hand without
     * setting one, and a release date still opens the assignment by itself when
     * the moment comes.
     */
    public void setWorksheetPublished(UUID classId, UUID worksheetId, boolean published) {
        requireClassOwner(classId);
        dao().setWorksheetPublished(classId, worksheetId, published);
        log.info("Worksheet {} in class {} {}", worksheetId, classId,
                published ? "published" : "unpublished");
    }

    /**
     * Ends an assignment immediately: students keep access to read the tasks but
     * can no longer work on them.
     */
    public void closeWorksheetNow(UUID classId, UUID worksheetId) {
        requireClassOwner(classId);
        dao().closeWorksheetNow(classId, worksheetId, Instant.now());
        log.info("Worksheet {} in class {} closed manually", worksheetId, classId);
    }

    private void requireClassOwner(UUID classId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));
        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
    }

    private PostgreClassDao dao() {
        if (classRepository instanceof PostgreClassDao dao) return dao;
        throw new DomainValidationException("Scheduling is not supported by this repository.");
    }

    public void removeWorksheet(UUID classId, UUID worksheetId) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");
        classRepository.removeWorksheet(classId, worksheetId);
    }

    // ── Tracking (AP5) ───────────────────────────────────────

    public void setTrackingEnabled(UUID classId, boolean enabled) {
        ClassDTO classDTO = classRepository.findById(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        ownershipService.checkOwnership(classDTO.getOwnerId(), "class");

        classRepository.updateTrackingEnabled(classId, enabled);
    }

    public boolean isTrackingEnabled(UUID classId) {
        return classRepository.isTrackingEnabled(classId);
    }

    /**
     * Returns the class_id for a student (first class found).
     */
    public UUID findClassIdForStudent(UUID studentId) {
        return classRepository.findClassIdForStudent(studentId);
    }

    /**
     * Returns progress data: for each student in the class, which tasks they solved correctly.
     * Only available when tracking is enabled.
     */
    public List<Map<String, Object>> getClassProgress(UUID classId) {
        ClassDTO classDTO = classRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) {
            throw new AccessDeniedException("Authentifizierung erforderlich.");
        }
        if (!ownershipService.isOwnerOrAdmin(classDTO.getOwnerId())) {
            throw new AccessDeniedException("Kein Zugriff auf Fortschrittsdaten.");
        }

        List<FeedbackLogDTO> logs = feedbackLogService.getFeedbackLogsForClass(classId);

        // Group logs by user_id (null = anonymous)
        Map<UUID, List<FeedbackLogDTO>> logsByUser = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getUserId() != null ? l.getUserId() : new UUID(0, 0),
                        Collectors.toList()
                ));

        // Build student name lookup
        Map<UUID, String> studentNames = classDTO.getStudents().stream()
                .collect(Collectors.toMap(
                        ClassDTO.StudentInfo::getUserId,
                        s -> s.getFirstName() + " " + s.getLastName()
                ));

        List<Map<String, Object>> result = new ArrayList<>();

        for (var entry : logsByUser.entrySet()) {
            UUID sId = entry.getKey();
            List<FeedbackLogDTO> studentLogs = entry.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            boolean isAnonymous = sId.equals(new UUID(0, 0));
            // feedback_logs.user_id has no foreign key, so clicks outlive the student:
            // deleted, or removed from the class. Their counts still belong to the
            // class, but without a userId — the frontend links every row that has
            // one, and the detail view only answers for current members.
            boolean isMember = studentNames.containsKey(sId);
            row.put("userId", isMember ? sId.toString() : null);
            row.put("studentName", isAnonymous ? "Unangemeldet"
                    : isMember ? studentNames.get(sId) : "Ehemaliger Schüler");

            // Tasks solved correctly (distinct task IDs where correct=true)
            Set<String> solvedTasks = studentLogs.stream()
                    .filter(FeedbackLogDTO::isCorrect)
                    .map(l -> l.getTaskId().toString())
                    .collect(Collectors.toSet());

            row.put("solvedTaskIds", solvedTasks);
            row.put("totalAttempts", studentLogs.size());
            row.put("correctAttempts", studentLogs.stream().filter(FeedbackLogDTO::isCorrect).count());
            result.add(row);
        }

        return result;
    }

    /**
     * One student's work across every worksheet assigned to this class: every
     * task in it, how many feedback clicks they made, and whether one of
     * those was correct.
     *
     * Scoped to this class's own assignments — the same worksheets the
     * teacher already sees on the class page — rather than everything the
     * student has ever touched, so a click from the class progress table
     * lands on a page whose authorization matches the class the teacher owns.
     */
    public StudentProgressDetailDTO getStudentProgressDetail(UUID classId, UUID studentId) {
        ClassDTO classDTO = loadClassForProgress(classId);
        ClassDTO.StudentInfo student = requireCurrentMember(classDTO, studentId);

        Map<UUID, List<FeedbackLogDTO>> logsByTask = feedbackLogService.getFeedbackLogsForClass(classId).stream()
                .filter(l -> studentId.equals(l.getUserId()))
                .collect(Collectors.groupingBy(FeedbackLogDTO::getTaskId));

        List<StudentProgressDetailDTO.WorksheetProgress> worksheetProgresses = new ArrayList<>();
        for (ClassDTO.WorksheetInfo wsInfo : classDTO.getWorksheets()) {
            WorksheetDTO worksheet = worksheetService.loadWorksheetById(wsInfo.getWorksheetId());

            List<StudentProgressDetailDTO.SequenceProgress> sequenceProgresses = new ArrayList<>();
            int index = 0;
            for (TieredTaskSequenceDTO seqStub : worksheet.getSequenceDTOS()) {
                index++;
                TieredTaskSequenceDTO sequence = taskPersistenceService.loadSequenceById(seqStub.getId());

                List<StudentProgressDetailDTO.TaskProgress> taskProgresses = new ArrayList<>();
                for (TaskDTO task : sequence.getOrderedTasks()) {
                    List<FeedbackLogDTO> taskLogs = logsByTask.getOrDefault(task.getId(), List.of());
                    long correctAttempts = taskLogs.stream().filter(FeedbackLogDTO::isCorrect).count();
                    taskProgresses.add(new StudentProgressDetailDTO.TaskProgress(
                            task.getId(),
                            task.getTierLevel(),
                            task.getStatement(),
                            taskLogs.size(),
                            (int) correctAttempts,
                            correctAttempts > 0));
                }

                sequenceProgresses.add(new StudentProgressDetailDTO.SequenceProgress(
                        sequence.getId(),
                        index,
                        sequence.getProgrammingLanguage(),
                        sequence.getConcepts(),
                        sequence.getTaskDescription(),
                        taskProgresses));
            }

            worksheetProgresses.add(new StudentProgressDetailDTO.WorksheetProgress(
                    wsInfo.getWorksheetId(), wsInfo.getName(), sequenceProgresses));
        }

        return new StudentProgressDetailDTO(
                studentId,
                student.getFirstName() + " " + student.getLastName(),
                worksheetProgresses);
    }

    /**
     * The class, once the caller has proved they may read progress data for it.
     *
     * <p>Owner or admin, not merely a teacher of the class: that is the rule the progress page
     * has always applied, and loosening it here would quietly loosen it there too.
     */
    private ClassDTO loadClassForProgress(UUID classId) {
        ClassDTO classDTO = classRepository.findByIdWithDetails(classId)
                .orElseThrow(() -> new DomainValidationException("Klasse nicht gefunden."));

        if (ownershipService.getCurrentUserId() == null) {
            throw new AccessDeniedException("Authentifizierung erforderlich.");
        }
        if (!ownershipService.isOwnerOrAdmin(classDTO.getOwnerId())) {
            throw new AccessDeniedException("Kein Zugriff auf Fortschrittsdaten.");
        }
        return classDTO;
    }

    /**
     * The student, if they are a member of the class now.
     *
     * <p>A former student is refused rather than served: feedback_logs has no foreign key on
     * user_id, so their rows survive them leaving, and serving those would attribute work to
     * somebody the teacher can no longer account for.
     */
    private ClassDTO.StudentInfo requireCurrentMember(ClassDTO classDTO, UUID studentId) {
        return classDTO.getStudents().stream()
                .filter(s -> s.getUserId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new DomainValidationException(
                        "Schüler ist nicht Mitglied dieser Klasse."));
    }

    /**
     * What one student last built for one task, beside the solution it was meant to become.
     *
     * <p>Three things have to hold, and only the first two are shared with the progress page:
     * the caller owns the class, the student is in it, and -- checked here for the first time --
     * the task belongs to a worksheet this class was actually assigned. Without that last one a
     * class owner could read any student's arrangement for any task in the system by passing its
     * id, because the arrangement itself is keyed by student and task alone.
     *
     * @param classId   the class whose assignment justifies the read
     * @param studentId the student whose work is being looked at
     * @param taskId    the task, which must belong to this class's worksheets
     * @return the arrangement and the expected solution; never null, even when nothing is stored
     */
    public StudentTaskStateDTO getStudentTaskState(UUID classId, UUID studentId, UUID taskId) {
        ClassDTO classDTO = loadClassForProgress(classId);
        requireCurrentMember(classDTO, studentId);

        TaskInClass found = findTaskInClass(classDTO, taskId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Aufgabe gehört zu keinem Arbeitsblatt dieser Klasse."));

        TaskDTO task = found.task();
        return taskSubmissionService.find(studentId, taskId)
                .map(stored -> new StudentTaskStateDTO(
                        taskId,
                        task.getStatement(),
                        found.programmingLanguage(),
                        task.getSolution(),
                        true,
                        stored.getBlockOrder(),
                        stored.getIndentLevels(),
                        stored.isSolved(),
                        stored.getSolvedAt(),
                        stored.getUpdatedAt()))
                .orElseGet(() -> new StudentTaskStateDTO(
                        taskId,
                        task.getStatement(),
                        found.programmingLanguage(),
                        task.getSolution(),
                        false,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        null));
    }

    /** A task reached through this class's worksheets, with the language of the sequence holding it. */
    private record TaskInClass(TaskDTO task, String programmingLanguage) {}

    /**
     * Walks the same worksheet -> sequence -> task structure the progress page is built from, and
     * stops at the requested task. Empty means the task is not this class's to show.
     */
    private Optional<TaskInClass> findTaskInClass(ClassDTO classDTO, UUID taskId) {
        for (ClassDTO.WorksheetInfo wsInfo : classDTO.getWorksheets()) {
            WorksheetDTO worksheet = worksheetService.loadWorksheetById(wsInfo.getWorksheetId());
            for (TieredTaskSequenceDTO seqStub : worksheet.getSequenceDTOS()) {
                TieredTaskSequenceDTO sequence = taskPersistenceService.loadSequenceById(seqStub.getId());
                for (TaskDTO task : sequence.getOrderedTasks()) {
                    if (taskId.equals(task.getId())) {
                        return Optional.of(new TaskInClass(task, sequence.getProgrammingLanguage()));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
