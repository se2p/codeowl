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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.uni_passau.fim.se2.api.dto.ManualTaskRequest;
import de.uni_passau.fim.se2.api.dto.TaskEditRequest;
import de.uni_passau.fim.se2.application.service.FeedbackLogService;
import de.uni_passau.fim.se2.application.service.ManualTaskService;
import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.TaskSubmissionService;
import de.uni_passau.fim.se2.application.service.WorksheetAccessService;
import de.uni_passau.fim.se2.application.service.TaskEditService;
import de.uni_passau.fim.se2.application.service.TaskEnricher;
import de.uni_passau.fim.se2.application.service.TaskGeneratorService;
import de.uni_passau.fim.se2.application.service.TaskPersistenceService;
import de.uni_passau.fim.se2.application.util.GapMarkers;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:5175")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TasksController {

    private final TaskGeneratorService taskGeneratorService;
    private final TaskPersistenceService taskPersistenceService;
    private final OwnershipService ownershipService;
    private final TaskEditService taskEditService;
    private final ManualTaskService manualTaskService;
    private final TaskOriginalRepository taskOriginalRepository;
    private final FeedbackLogService feedbackLogService;
    private final TaskEnricher taskEnricher;
    private final TaskSubmissionService taskSubmissionService;
    private final WorksheetAccessService worksheetAccessService;

    /**
     * Generate a new task sequence.
     * Allowed: Teacher, Admin
     * Denied: Anonymous and Students (enforced in SecurityConfig)
     */
    @PostMapping("/tasks/generate")
    public ResponseEntity<TieredTaskSequenceDTO> generateTaskSequence(
            @RequestBody TaskGenerationRequest request
    ) throws IOException, InterruptedException {
        // Students are not allowed to generate sequences
        ownershipService.denyStudents("generate task sequences");

        UUID currentUserId = ownershipService.getCurrentUserId();

        String targetLang = request.getTargetLanguage();
        if (targetLang == null || targetLang.isBlank()) targetLang = "de";

        boolean distractors = request.isDistractorsEnabled();
        boolean contextMode = request.isContextModeEnabled();
        boolean splitEnabled = request.isSplitEnabled(); // AP31

        // AP20: Build GenerationConstraints from request fields
        GenerationConstraints constraints = GenerationConstraints.builder()
                .blacklist(request.getBlacklist())
                .whitelist(request.getWhitelist())
                .difficultySlope(request.getDifficultySlope())
                .additionalContext(request.getAdditionalContext())
                .build();

        // AP21: Validate and default tierCount
        int tierCount = request.getTierCount();
        if (tierCount != 3 && tierCount != 5) tierCount = 3;

        var seq = taskGeneratorService.generate(
                request.getProblemStatement(),
                request.getConcepts(),
                request.getLanguage(),
                request.getCodeSnippet(),
                currentUserId,  // owner_id (null for anonymous)
                targetLang,
                distractors,
                contextMode,
                constraints.hasAny() ? constraints : null,
                tierCount,
                splitEnabled // AP31
        );
        var location = java.net.URI.create("/api/sequence/" + seq.getId());
        return ResponseEntity.created(location).body(seq);
    }

    /**
     * Get a sequence by ID. Public endpoint (anyone can view via link).
     */
    @GetMapping("/sequence/{id}")
    public ResponseEntity<TieredTaskSequenceDTO> getTaskSequence(@PathVariable UUID id) {
        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(id);
        if (seq == null) return ResponseEntity.notFound().build();

        if (seq.getTasks() != null) {
            seq.setTasks(seq.getTasks().stream()
                                 .sorted(java.util.Comparator.comparingInt(TaskDTO::getTierLevel))
                                 .toList());
        }
        // AP15: Strip disabled distractors for non-owners
        stripDisabledDistractors(seq);
        return ResponseEntity.ok(seq);
    }

    /**
     * Replace a sequence's concepts. The teacher view on the sequence page
     * edits them as a set, so this is a PUT of the whole list rather than
     * add/remove endpoints.
     * Allowed: the sequence's owner, and admins.
     */
    @PutMapping("/sequence/{id}/concepts")
    public ResponseEntity<List<String>> updateSequenceConcepts(
            @PathVariable UUID id,
            @RequestBody UpdateConceptsRequest request) {

        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(id);
        if (seq == null) {
            return ResponseEntity.notFound().build();
        }
        ownershipService.checkOwnership(seq.getOwnerId(), "sequence concepts");

        return ResponseEntity.ok(
                taskPersistenceService.updateSequenceConcepts(id, request == null ? null : request.getConcepts()));
    }

    /**
     * Get a single task by ID. Public endpoint.
     * AP15: If distractorsEnabled=false and requester is not the owner/admin,
     * distractors are stripped from the response (kept in DB, not delivered).
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskDTO> getTaskById(@PathVariable UUID taskId) {
        TaskDTO task = taskPersistenceService.loadTaskById(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        stripDisabledDistractors(task);
        return ResponseEntity.ok(task);
    }

    /**
     * Edit a task's problem statement and code (AP9).
     * On the first edit the original version is archived and an adjustment rating is required.
     * Allowed: Owner of the parent sequence, Admin
     */
    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<TaskDTO> editTask(@PathVariable UUID taskId,
                                            @RequestBody TaskEditRequest request) {
        TaskDTO updated = taskEditService.editTask(taskId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Create a task sequence manually — no LLM involved (AP23).
     * The teacher provides code + problem statements directly.
     * Allowed: Teacher, Admin
     * Denied: Students, Anonymous
     */
    @PostMapping("/tasks/manual")
    public ResponseEntity<TieredTaskSequenceDTO> createManualSequence(
            @RequestBody ManualTaskRequest request
    ) {
        ownershipService.denyStudents("create manual tasks");

        UUID currentUserId = ownershipService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        TieredTaskSequenceDTO seq = manualTaskService.createManualSequence(request, currentUserId);
        var location = java.net.URI.create("/api/sequence/" + seq.getId());
        return ResponseEntity.created(location).body(seq);
    }


    /**
     * Whether the current user may still work on this task, or only read it.
     *
     * Drives the solve page's read-only mode. Not the enforcement point: the
     * services that record solving re-check it, so a client that ignores this
     * still cannot write.
     */
    @GetMapping("/tasks/{taskId}/access")
    public ResponseEntity<Map<String, Object>> getTaskAccess(@PathVariable UUID taskId) {
        return ResponseEntity.ok(Map.of("canSolve", worksheetAccessService.canSolve(taskId)));
    }

    /**
     * The current student's solved arrangement of a task, so reopening it shows
     * their work instead of a reshuffled puzzle.
     *
     * 204 when there is nothing stored — including for anonymous visitors, who
     * have nobody to store work against.
     */
    @GetMapping("/tasks/{taskId}/submission")
    public ResponseEntity<TaskSubmissionDTO> getMySubmission(@PathVariable UUID taskId) {
        UUID userId = ownershipService.getCurrentUserId();
        return taskSubmissionService.find(userId, taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Records the arrangement a student had when they solved a task.
     *
     * Replaces any earlier one for the same student and task. Anonymous solves
     * are accepted and quietly ignored, so a shared-link visitor is not shown an
     * error for something that is not their problem.
     */
    @PutMapping("/tasks/{taskId}/submission")
    public ResponseEntity<Void> saveMySubmission(@PathVariable UUID taskId,
                                                 @RequestBody TaskSubmissionRequest request) {
        UUID userId = ownershipService.getCurrentUserId();
        taskSubmissionService.saveSolved(userId, taskId, request.blockOrder(), request.indentLevels());
        return ResponseEntity.noContent().build();
    }

    /**
     * Autosaves the arrangement a student currently has, without claiming they solved it.
     *
     * Called repeatedly while a student works, so it is deliberately cheap and forgiving: an
     * empty arrangement is valid, and anonymous callers are ignored rather than rejected.
     */
    @PutMapping("/tasks/{taskId}/submission/progress")
    public ResponseEntity<Void> saveMyProgress(@PathVariable UUID taskId,
                                               @RequestBody TaskSubmissionRequest request) {
        UUID userId = ownershipService.getCurrentUserId();
        taskSubmissionService.saveProgress(userId, taskId, request.blockOrder(), request.indentLevels());
        return ResponseEntity.noContent().build();
    }

    /**
     * Throws away the current student's stored arrangement for a task.
     *
     * What the "start over" control calls, so the next visit deals a fresh puzzle instead of
     * restoring work the student has chosen to abandon.
     */
    @DeleteMapping("/tasks/{taskId}/submission")
    public ResponseEntity<Void> discardMySubmission(@PathVariable UUID taskId) {
        UUID userId = ownershipService.getCurrentUserId();
        taskSubmissionService.discard(userId, taskId);
        return ResponseEntity.noContent().build();
    }

    /** The solution area as the student left it. */
    public record TaskSubmissionRequest(List<String> blockOrder, List<Integer> indentLevels) {}

    /**
     * Get all sequences owned by the current user.
     */
    @GetMapping("/sequences/my")
    public ResponseEntity<List<TieredTaskSequenceDTO>> getMySequences() {
        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(taskPersistenceService.findSequencesByOwnerId(userId));
    }

    /**
     * Delete one of your own sequences. It leaves the library and your list, but stays in any
     * worksheet that already contains it, so work in progress is not disrupted.
     * Allowed: Owner, Admin.
     */
    @DeleteMapping("/sequence/{seqId}")
    public ResponseEntity<Void> deleteSequence(@PathVariable UUID seqId) {
        requireSequenceOwnerOrAdmin(seqId);
        taskPersistenceService.deleteSequence(seqId);
        return ResponseEntity.noContent().build();
    }









    /**
     * AP15b: Generate distractors on-demand for a single existing task.
     * Allowed: Owner of parent sequence, Admin.
     */
    @PostMapping("/tasks/{taskId}/generate-distractors")
    public ResponseEntity<TaskDTO> generateDistractorsForTask(@PathVariable UUID taskId) {
        TaskDTO task = taskPersistenceService.loadTaskById(taskId);
        if (task == null) return ResponseEntity.notFound().build();

        // Ownership check via sequence
        requireSequenceOwnerOrAdmin(task.getSequenceId());

        if (task.getSolution() == null || task.getSolution().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Determine language from sequence
        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(task.getSequenceId());
        String lang = seq != null ? seq.getProgrammingLanguage() : "python";
        // AP30: resolve $$blank:…$$ gap markers to real code before generating distractors
        String solutionCode = GapMarkers.strip(task.getSolution());
        int loc = (int) solutionCode.lines().count();

        String distractors = taskEnricher.generateDistractorsForTask(
                solutionCode, lang, task.getStatement(),
                task.getTierLevel(), task.getDistractorPlan(), loc);

        if (distractors != null) {
            taskPersistenceService.updateDistractors(taskId, distractors);
            task.setDistractors(distractors);
        }

        return ResponseEntity.ok(task);
    }

    /**
     * AP15b: Batch-generate distractors for all tasks in a sequence that don't have them yet.
     * Allowed: Owner of parent sequence, Admin.
     */
    @PostMapping("/sequence/{seqId}/generate-distractors")
    public ResponseEntity<TieredTaskSequenceDTO> generateDistractorsForSequence(@PathVariable UUID seqId) {
        requireSequenceOwnerOrAdmin(seqId);

        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(seqId);
        if (seq == null) return ResponseEntity.notFound().build();

        String lang = seq.getProgrammingLanguage() != null ? seq.getProgrammingLanguage() : "python";
        int generated = 0;

        if (seq.getTasks() != null) {
            for (TaskDTO task : seq.getTasks()) {
                if (task.getDistractors() != null && !task.getDistractors().isBlank()) continue;
                if (task.getSolution() == null || task.getSolution().isBlank()) continue;

                // AP30: resolve gap markers before generating distractors
                String solutionCode = GapMarkers.strip(task.getSolution());
                int loc = (int) solutionCode.lines().count();
                String distractors = taskEnricher.generateDistractorsForTask(
                        solutionCode, lang, task.getStatement(),
                        task.getTierLevel(), task.getDistractorPlan(), loc);

                if (distractors != null) {
                    taskPersistenceService.updateDistractors(task.getId(), distractors);
                    task.setDistractors(distractors);
                    generated++;
                }
            }
        }

        // Reload to get updated data
        TieredTaskSequenceDTO updated = taskPersistenceService.loadSequenceById(seqId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Replace a sequence's task description. A blank one clears it.
     * Allowed: the sequence's owner, and admins.
     */
    @PutMapping("/sequence/{id}/task-description")
    public ResponseEntity<Map<String, String>> updateSequenceTaskDescription(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(id);
        if (seq == null) {
            return ResponseEntity.notFound().build();
        }
        ownershipService.checkOwnership(seq.getOwnerId(), "sequence task description");

        String stored = taskPersistenceService.updateTaskDescription(id, body.get("taskDescription"));
        // Map.of rejects null values, and a cleared description is null.
        Map<String, String> response = new java.util.HashMap<>();
        response.put("taskDescription", stored);
        return ResponseEntity.ok(response);
    }

    /**
     * Rename a sequence. Body: {@code {"title": "..."}}; a blank or missing title restores the
     * default heading.
     * Allowed: the sequence's owner, and admins.
     */
    @PatchMapping("/sequence/{id}/title")
    public ResponseEntity<Map<String, String>> renameSequence(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(id);
        if (seq == null) {
            return ResponseEntity.notFound().build();
        }
        ownershipService.checkOwnership(seq.getOwnerId(), "sequence title");

        String stored = taskPersistenceService.updateSequenceTitle(id, body.get("title"));
        // Map.of rejects null values, and a cleared name is null.
        Map<String, String> response = new java.util.HashMap<>();
        response.put("title", stored);
        return ResponseEntity.ok(response);
    }

    /** Body of PUT /api/sequence/{id}/concepts — the full new set of names. */
    @Setter
    @Getter
    public static class UpdateConceptsRequest {
        private List<String> concepts;
    }

    @Setter
    @Getter
    public static class TaskGenerationRequest {
        @JsonProperty("problemStatement")
        @JsonAlias({"taskDescription"})
        private String problemStatement;
        private List<String> concepts;
        private String language;
        private String codeSnippet;
        /** Human language for problem statements: "de" or "en". Default: "de". */
        private String targetLanguage;
        /** AP15: Whether to generate distractors for Parsons problems. Default: false. */
        private boolean distractorsEnabled;
        /** AP22: Whether to enable context mode (Beta) for longer code with scaffolding. Default: false. */
        private boolean contextModeEnabled;
        /** AP31: Whether to generate inline-split plans for complex lines. Default: false. */
        private boolean splitEnabled;

        // ── AP20: Generation Constraints ─────────────────────
        /** Constructs that MUST NOT appear in generated code. */
        private List<String> blacklist;
        /** If non-empty, ONLY these constructs may appear. */
        private List<String> whitelist;
        /** Controls difficulty slope: "gentle", "moderate", "steep". */
        private String difficultySlope;
        /** Free-text teacher instructions injected into prompt. */
        private String additionalContext;
        /** AP21: Number of tiers (3 or 5). Default: 3. */
        private int tierCount = 3;
    }

    /**
     * Check if an original version exists for a task (AP9).
     * Used by the frontend to determine if isFirstEdit should be true.
     */
    @GetMapping("/tasks/{taskId}/has-original")
    public ResponseEntity<Map<String, Boolean>> hasOriginal(@PathVariable UUID taskId) {
        boolean hasOriginal = taskOriginalRepository.existsByTaskId(taskId);
        return ResponseEntity.ok(Map.of("hasOriginal", hasOriginal));
    }

    @PostMapping("/tasks/{taskId}/feedback-log")
    public ResponseEntity<Void> logFeedbackClick(@PathVariable UUID taskId, @RequestBody FeedbackLogRequest req) {
        UUID userId = ownershipService.getCurrentUserId();
        feedbackLogService.logFeedbackClickWithTracking(taskId, req.correct(), userId);
        return ResponseEntity.ok().build();
    }

    public record FeedbackLogRequest(boolean correct) {}

    /**
     * AP15: Toggle distractors enabled/disabled for a task.
     * Distractors are kept in DB but not delivered to students when disabled.
     * Allowed: Owner of parent sequence, Admin.
     */
    @PatchMapping("/tasks/{taskId}/distractors-enabled")
    public ResponseEntity<TaskDTO> toggleDistractorsEnabled(
            @PathVariable UUID taskId,
            @RequestBody Map<String, Boolean> body) {
        TaskDTO task = taskPersistenceService.loadTaskById(taskId);
        if (task == null) return ResponseEntity.notFound().build();

        requireSequenceOwnerOrAdmin(task.getSequenceId());

        boolean enabled = body.getOrDefault("enabled", true);
        taskPersistenceService.updateDistractorsEnabled(taskId, enabled);
        task.setDistractorsEnabled(enabled);
        return ResponseEntity.ok(task);
    }

    /**
     * Rename a task. Body: {@code {"title": "..."}}; a blank or missing title restores the
     * numbered default.
     * Allowed: Owner of parent sequence, Admin.
     */
    @PatchMapping("/tasks/{taskId}/title")
    public ResponseEntity<TaskDTO> renameTask(
            @PathVariable UUID taskId,
            @RequestBody Map<String, String> body) {
        TaskDTO task = taskPersistenceService.loadTaskById(taskId);
        if (task == null) return ResponseEntity.notFound().build();

        requireSequenceOwnerOrAdmin(task.getSequenceId());

        task.setTitle(taskPersistenceService.updateTitle(taskId, body.get("title")));
        return ResponseEntity.ok(task);
    }

    /**
     * Checks ownership of a sequence by ID. Throws AccessDeniedException if denied.
     */
    private void requireSequenceOwnerOrAdmin(UUID sequenceId) {
        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(sequenceId);
        if (seq == null) {
            throw new org.springframework.security.access.AccessDeniedException("Sequence not found");
        }
        ownershipService.checkOwnership(seq.getOwnerId(), "sequence");
    }

    /**
     * AP15: For non-owner/admin requests, strip distractors when they are disabled.
     * Teachers always see distractors (for management), students don't when disabled.
     *
     * Single-task variant: loads the parent sequence once to check ownership.
     */
    private void stripDisabledDistractors(TaskDTO task) {
        if (task == null || task.getDistractors() == null) return;
        if (task.isDistractorsEnabled()) return;

        // Check if current user is owner or admin — if so, keep distractors visible
        try {
            TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(task.getSequenceId());
            if (seq != null && ownershipService.isOwnerOrAdmin(seq.getOwnerId())) {
                return; // Teacher/admin sees everything
            }
        } catch (Exception ignored) {}

        // Non-owner: strip distractors
        task.setDistractors(null);
        task.setDistractorPlan(null);
    }

    /**
     * Sequence variant: checks ownership ONCE for the whole sequence, then applies to all tasks.
     * Avoids an N+1 pattern where the single-task overload would reload the sequence per task.
     */
    private void stripDisabledDistractors(TieredTaskSequenceDTO seq) {
        if (seq == null || seq.getTasks() == null) return;

        // Resolve owner check once — avoids N DB calls for a sequence with N tasks
        boolean isOwnerOrAdmin = ownershipService.isOwnerOrAdmin(seq.getOwnerId());

        for (TaskDTO task : seq.getTasks()) {
            if (task.getDistractors() == null) continue;
            if (task.isDistractorsEnabled()) continue;
            if (isOwnerOrAdmin) continue; // owner/admin always sees distractors
            task.setDistractors(null);
            task.setDistractorPlan(null);
        }
    }
}
