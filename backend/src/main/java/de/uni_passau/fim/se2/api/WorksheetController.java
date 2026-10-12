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

import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.SequenceForkService;
import de.uni_passau.fim.se2.application.service.TaskPersistenceService;
import de.uni_passau.fim.se2.application.service.WorksheetService;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import de.uni_passau.fim.se2.domain.model.SequenceCuratorPolicy;
import de.uni_passau.fim.se2.domain.model.WorksheetAccessPolicy;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a worksheet is: creating one, editing it, filling it with sequences, deciding who
 * can see it, and copying someone else's.
 *
 * <p>Moved out of {@code TasksController}, which had accumulated these alongside sequence
 * generation, task editing, hints and submissions. They were only ever there because that is
 * where the first one was written; nothing about a worksheet needs to sit next to a task
 * generator, and the marketplace was about to add a third worksheet endpoint to a class that
 * already had nine.
 *
 * <p>A pure move: same paths, same authorisation, same responses. What was verified before is
 * still what happens.
 *
 * <p>Note the deliberate asymmetry in what a failure looks like. Editing something you do not
 * own raises {@code AccessDeniedException} through {@link OwnershipService}, because you had to
 * know the worksheet existed to try. Reading or copying one you are not allowed to see answers
 * 404, because saying "forbidden" would confirm that an id someone guessed is real.
 */
@CrossOrigin(origins = "http://localhost:5175")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorksheetController {

    private final WorksheetService worksheetService;
    private final OwnershipService ownershipService;
    private final SequenceForkService sequenceForkService;
    private final TaskPersistenceService taskPersistenceService;
    private final WorksheetRepository worksheetRepository;

    /**
     * Get all worksheets owned by the current user.
     */
    @GetMapping("/worksheets/my")
    public ResponseEntity<List<WorksheetDTO>> getMyWorksheets() {
        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(worksheetService.findByOwnerId(userId));
    }

    /**
     * Get a worksheet by ID.
     *
     * <p>Still reachable without logging in — that is how a student opens a worksheet from a
     * link their teacher sent — but no longer unconditionally. A PRIVATE worksheet answers 404
     * rather than 403, so the endpoint does not confirm that an id someone guessed exists.
     */
    @GetMapping("/worksheets/{sheetId}")
    public ResponseEntity<WorksheetDTO> getWorksheetById(@PathVariable UUID sheetId) {
        // Both branches answer 404 deliberately. Distinguishing "no such worksheet" from "not
        // yours to see" would hand an unauthorised caller a way to test which ids exist, which
        // is the one thing PRIVATE is meant to prevent.
        return worksheetService.findWorksheetById(sheetId)
                .filter(w -> WorksheetAccessPolicy.canView(w.getVisibility(), w.getOwnerId(),
                        ownershipService.getCurrentUserId(), ownershipService.getCurrentUserRole()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Publish a worksheet to the marketplace, or take it back out.
     * Allowed: Owner, Admin — except for the Library Inbox, which is a scratch pile.
     */
    @PutMapping("/worksheets/{sheetId}/visibility")
    public ResponseEntity<WorksheetDTO> setWorksheetVisibility(
            @PathVariable UUID sheetId,
            @RequestBody VisibilityRequest request) {

        WorksheetVisibility visibility;
        try {
            visibility = WorksheetVisibility.parse(request == null ? null : request.visibility());
        } catch (IllegalArgumentException e) {
            // An unknown value is a client bug, not a server one, and must not be rounded down
            // to something permissive.
            return ResponseEntity.badRequest().build();
        }

        // Checked here so an unknown id answers 404 like every other worksheet endpoint, rather
        // than the 400 the service's load would raise.
        if (worksheetService.findWorksheetById(sheetId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorksheetDTO updated = worksheetService.setVisibility(
                sheetId, visibility,
                ownershipService.getCurrentUserId(), ownershipService.getCurrentUserRole());
        return ResponseEntity.ok(updated);
    }

    /** Body of {@code PUT /worksheets/{id}/visibility}: {@code {"visibility": "PUBLIC"}}. */
    public record VisibilityRequest(String visibility) {}

    /**
     * Create a new worksheet.
     * Allowed: Teacher (becomes owner), Admin
     * Denied: Anonymous, Students
     */
    @PostMapping("/worksheets")
    public ResponseEntity<WorksheetDTO> createWorksheet(@RequestBody WorksheetDTO worksheet) {
        ownershipService.denyStudents("create worksheets");

        UUID currentUserId = ownershipService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        worksheet.setOwnerId(currentUserId);
        worksheetService.createWorksheet(worksheet);
        var location = java.net.URI.create("/api/worksheets/" + worksheet.getId());
        return ResponseEntity.created(location).body(worksheet);
    }

    /**
     * Update a worksheet.
     * Allowed: Owner, Admin
     * Denied: Everyone else
     */
    @PutMapping("/worksheets/{sheetId}")
    public ResponseEntity<Void> updateWorksheet(@PathVariable UUID sheetId, @RequestBody WorksheetDTO worksheet) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheet.setId(sheetId);
        worksheet.setOwnerId(existing.getOwnerId()); // preserve owner
        worksheetService.updateWorksheet(worksheet);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a worksheet.
     * Allowed: Owner, Admin
     * Denied: Everyone else
     */
    @DeleteMapping("/worksheets/{sheetId}")
    public ResponseEntity<Void> deleteWorksheet(@PathVariable UUID sheetId) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheetService.deleteWorksheet(sheetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Add a single sequence to a worksheet.
     * Allowed: Owner of worksheet, Admin
     */
    @PostMapping("/worksheets/{sheetId}/sequences/{sequenceId}")
    public ResponseEntity<Void> addSequenceToWorksheet(@PathVariable UUID sheetId, @PathVariable UUID sequenceId) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheetService.addSequenceToWorksheet(sheetId, sequenceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Add multiple sequences to a worksheet.
     * Allowed: Owner of worksheet, Admin
     */
    @PostMapping("/worksheets/{sheetId}/sequences")
    public ResponseEntity<Void> addSequencesToWorksheet(@PathVariable UUID sheetId,
                                                        @RequestBody List<UUID> sequenceIds) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheetService.addSequencesToWorksheet(sheetId, sequenceIds);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder the sequences on a worksheet.
     *
     * <p>The body must list every sequence already on the worksheet, in the desired order — no
     * more, no fewer. The DAO checks that and rejects a mismatched set rather than silently
     * dropping or inventing a position, since a stale client is the likely cause and the
     * worksheet should not end up half reordered.
     *
     * Allowed: Owner of worksheet, Admin
     */
    @PutMapping("/worksheets/{sheetId}/sequences/order")
    public ResponseEntity<Void> reorderSequences(@PathVariable UUID sheetId,
                                                 @RequestBody List<UUID> orderedSequenceIds) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheetService.reorderSequences(sheetId, orderedSequenceIds);
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove a sequence from a worksheet.
     * Allowed: Owner of worksheet, Admin
     */
    @DeleteMapping("/worksheets/{sheetId}/sequences/{sequenceId}")
    public ResponseEntity<Void> removeSequenceFromWorksheet(@PathVariable UUID sheetId, @PathVariable UUID sequenceId) {
        WorksheetDTO existing = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        ownershipService.checkOwnership(existing.getOwnerId(), "worksheet");

        worksheetService.removeSequenceFromWorksheet(sheetId, sequenceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deep-copy a worksheet (with all sequences and tasks).
     * The copy belongs to the current authenticated user and starts out PRIVATE.
     * Allowed: TEACHER, ADMIN, on a worksheet they are allowed to see
     * Denied: Students, Anonymous
     */
    @PostMapping("/worksheets/{sheetId}/copy")
    public ResponseEntity<WorksheetDTO> copyWorksheet(@PathVariable UUID sheetId) {
        ownershipService.denyStudents("copy worksheets");

        UUID currentUserId = ownershipService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Until now this endpoint checked only that the caller was a teacher, never anything
        // about the source. That was harmless while every worksheet was readable by link; with
        // PRIVATE in existence it would have been a way to read one by copying it.
        // Same 404 for "no such worksheet" and "not yours to copy", for the same reason as the
        // read endpoint above.
        boolean allowed = worksheetService.findWorksheetById(sheetId)
                .map(source -> WorksheetAccessPolicy.canCopy(source.getVisibility(),
                        source.getOwnerId(), currentUserId, ownershipService.getCurrentUserRole()))
                .orElse(false);
        if (!allowed) {
            return ResponseEntity.notFound().build();
        }

        WorksheetDTO copy = worksheetService.copyWorksheet(sheetId, currentUserId);
        var location = java.net.URI.create("/api/worksheets/" + copy.getId());
        return ResponseEntity.created(location).body(copy);
    }

    // ── Curating someone else's sequence ──────────────────────────────────

    /**
     * What the current user may do with one sequence inside one of their worksheets.
     *
     * <p>Worksheet-scoped on purpose. The claim being answered is "I put this in my worksheet",
     * so the worksheet is part of the question rather than context the server has to guess.
     *
     * <p>Answers rather than refuses when the user may do nothing: the page uses this to decide
     * what to render, and a 403 would turn an ordinary "no teacher view for you" into an error
     * the client has to special-case.
     */
    @GetMapping("/worksheets/{sheetId}/sequences/{sequenceId}/access")
    public ResponseEntity<SequenceAccessResponse> sequenceAccess(
            @PathVariable UUID sheetId, @PathVariable UUID sequenceId) {

        UUID userId = ownershipService.getCurrentUserId();
        UserDTO.UserRole role = ownershipService.getCurrentUserRole();

        TieredTaskSequenceDTO sequence = taskPersistenceService.loadSequenceById(sequenceId);
        if (sequence == null) {
            return ResponseEntity.notFound().build();
        }

        // The curator relation is asked of the worksheet the caller named, not of all their
        // worksheets: someone who owns a different worksheet containing this sequence has no
        // business forking it into this one.
        boolean curator = worksheetService.findWorksheetById(sheetId)
                .map(sheet -> userId != null && userId.equals(sheet.getOwnerId())
                        && worksheetRepository.isCuratedBy(sequenceId, userId))
                .orElse(false);

        UUID ownerId = sequence.getOwnerId();
        return ResponseEntity.ok(new SequenceAccessResponse(
                SequenceCuratorPolicy.canInspect(ownerId, userId, role, curator),
                SequenceCuratorPolicy.canEditInPlace(ownerId, userId, role),
                SequenceCuratorPolicy.mustForkToEdit(ownerId, userId, role, curator)));
    }

    /**
     * Takes a private copy of a sequence and repoints this worksheet at it.
     *
     * <p>How a curator edits material that is not theirs. The original stays untouched and
     * stays in everyone else's worksheets; only this worksheet moves to the copy.
     *
     * @return the id of the copy, so the client can navigate to it
     */
    @PostMapping("/worksheets/{sheetId}/sequences/{sequenceId}/fork")
    public ResponseEntity<Map<String, UUID>> forkSequence(
            @PathVariable UUID sheetId, @PathVariable UUID sequenceId) {

        ownershipService.denyStudents("fork sequences");

        UUID userId = ownershipService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WorksheetDTO sheet = worksheetService.findWorksheetById(sheetId).orElse(null);
        if (sheet == null || !userId.equals(sheet.getOwnerId())) {
            // Forking writes to the worksheet, so this needs ownership of it and not merely
            // permission to read it. Same 404 for both, as everywhere else here.
            return ResponseEntity.notFound().build();
        }

        TieredTaskSequenceDTO sequence = taskPersistenceService.loadSequenceById(sequenceId);
        if (sequence == null) {
            return ResponseEntity.notFound().build();
        }

        if (!SequenceCuratorPolicy.mustForkToEdit(sequence.getOwnerId(), userId,
                ownershipService.getCurrentUserRole(), true)) {
            // Already editable in place. Forking would hand back a duplicate nobody asked for.
            return ResponseEntity.badRequest().build();
        }

        UUID copyId = sequenceForkService.forkForEditing(sheetId, sequenceId, userId);
        return ResponseEntity.ok(Map.of("sequenceId", copyId));
    }

    /** What the current user may do with a sequence in a worksheet of theirs. */
    public record SequenceAccessResponse(boolean canInspect, boolean canEditInPlace,
                                         boolean mustForkToEdit) {
    }
}
