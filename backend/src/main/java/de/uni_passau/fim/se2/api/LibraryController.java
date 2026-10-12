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

import de.uni_passau.fim.se2.api.dto.*;
import de.uni_passau.fim.se2.application.service.*;
import de.uni_passau.fim.se2.application.query.WorksheetSearchQuery;
import de.uni_passau.fim.se2.application.query.WorksheetSearchResult;
import de.uni_passau.fim.se2.application.service.WorksheetSearchService;
import de.uni_passau.fim.se2.api.dto.LibraryItemDTO;
import de.uni_passau.fim.se2.api.dto.LibrarySearchResponse;
import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.SequenceSearchService;
import de.uni_passau.fim.se2.application.service.SequenceSearchService.*;
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.application.service.SequenceTagService;
import de.uni_passau.fim.se2.application.service.TaskPersistenceService;
import de.uni_passau.fim.se2.application.service.WorksheetService;
import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AP19 Teil A: read-side Library endpoints.
 *
 * <p>The Library is a "grid of all sequences I can use" view for teachers. Filters are
 * passed as query parameters so URLs are shareable / bookmarkable:
 * <pre>
 *   /api/library/sequences?q=rekursion&amp;tags=fibonacci,klassiker&amp;lang=java&amp;owner=me&amp;sort=recent&amp;page=0
 * </pre>
 *
 * <p>Autocomplete is split off to its own endpoint so the tag-input component can throttle it
 * independently of the main search query.
 */
@Slf4j
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final SequenceSearchService searchService;
    private final SequenceTagService tagService;
    private final OwnershipService ownershipService;
    private final TaskPersistenceService taskPersistenceService;
    private final WorksheetService worksheetService;
    private final WorksheetSearchService worksheetSearchService;
    /** Minimal request body for adding a tag — keeps the URL shape clean. */
    public record AddTagRequest(String tag) {}


    /**
     * Endpoint for searching sequences in the library with various filters.
     *
     * @param q             Query string for search, optional.
     * @param tagsCsv       Comma-separated tags, optional.
     * @param tagMode       Tag matching mode, defaults to "any".
     * @param conceptsCsv   Comma-separated concepts, optional.
     * @param conceptMode   Concept matching mode, defaults to "any".
     * @param lang          Language filter, optional.
     * @param owner         Owner filter, defaults to "any".
     * @param tierCount     Tier count filter, optional.
     * @param sort          Sort order, defaults to "recent".
     * @param page          Page number, defaults to 0.
     * @param size          Page size, defaults to 20.
     * @param tagsLimit     How many top tags the facet sidebar gets back, defaults to 50. The
     *                      sidebar's "Show more" re-requests with this raised by the same amount
     *                      each time, rather than the server ever sending the whole tag table.
     * @param conceptsLimit The concept-facet mirror of {@code tagsLimit}.
     * @return LibrarySearchResponse containing search results.
     */
    @GetMapping("/sequences")
    public ResponseEntity<LibrarySearchResponse> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tags", required = false) String tagsCsv,
            @RequestParam(name = "tagMode", required = false, defaultValue = "any") String tagMode,
            @RequestParam(name = "concepts", required = false) String conceptsCsv,
            @RequestParam(name = "conceptMode", required = false, defaultValue = "any") String conceptMode,
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "owner", required = false, defaultValue = "any") String owner,
            @RequestParam(name = "tierCount", required = false) Integer tierCount,
            @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            @RequestParam(name = "tagsLimit", required = false) Integer tagsLimit,
            @RequestParam(name = "conceptsLimit", required = false) Integer conceptsLimit) {

        UUID currentUserId = ownershipService.getCurrentUserId();

        SearchQuery query = new SearchQuery()
                .q(q)
                .tags(parseCsvList(tagsCsv))
                .tagMode(tagMode)
                .concepts(parseCsvList(conceptsCsv))
                .conceptMode(conceptMode)
                .lang(lang)
                .owner(owner)
                .tierCount(tierCount)
                .sort(sort)
                .page(page)
                .size(size)
                .tagsLimit(tagsLimit)
                .conceptsLimit(conceptsLimit);

        LibrarySearchResponse response = searchService.search(query, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Search published worksheets — the marketplace.
     *
     * <p>Lists only worksheets whose owner set them to PUBLIC, which is enforced in the SQL
     * rather than here: the filter is the reason this endpoint is safe to serve, so it belongs
     * where it cannot be forgotten. Readable without logging in, like the sequence library,
     * although "only mine" then has nothing to mean and returns nothing.
     *
     * @param q        free-text query over the worksheets and everything inside them
     * @param lang     restrict to worksheets containing sequences in this language
     * @param owner    {@code "me"} to see only what the caller published
     * @param sort     {@code recent} (default), {@code relevance} or {@code popularity}
     */
    @GetMapping("/worksheets")
    public ResponseEntity<WorksheetSearchResult> searchWorksheets(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "owner", required = false, defaultValue = "any") String owner,
            @RequestParam(name = "sort", required = false, defaultValue = "recent") String sort,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {

        WorksheetSearchQuery query = WorksheetSearchQuery.of(
                q, lang, "me".equalsIgnoreCase(owner), sort, page, size);

        return ResponseEntity.ok(
                worksheetSearchService.search(query, ownershipService.getCurrentUserId()));
    }

    /**
     * Autocomplete endpoint for tag-name suggestions based on a prefix.
     *
     * @param prefix Prefix string to filter tags, defaults to empty string.
     * @param limit  Limit for the number of suggestions, defaults to 10.
     * @return List of tag suggestions.
     */
    @GetMapping("/tags/autocomplete")
    public ResponseEntity<List<String>> autocomplete(
            @RequestParam(name = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        return ResponseEntity.ok(tagService.autocomplete(prefix, limit));
    }

    /**
     * Retrieves a list of sequences similar to the provided sequence ID.
     *
     * @param sequenceId UUID of the reference sequence.
     * @param limit      Maximum number of similar sequences to fetch, defaults to 3.
     * @return List of similar LibraryItemDTO.
     */
    @GetMapping("/sequences/{sequenceId}/similar")
    public ResponseEntity<List<LibraryItemDTO>> similar(
            @PathVariable UUID sequenceId,
            @RequestParam(name = "limit", required = false, defaultValue = "3") int limit) {
        UUID me = ownershipService.getCurrentUserId();
        return ResponseEntity.ok(searchService.findSimilar(sequenceId, limit, me));
    }

    /**
     * Retrieves current tags for a specific sequence.
     *
     * @param sequenceId UUID of the sequence.
     * @return List of SequenceTagDTO associated with the sequence.
     */
    @GetMapping("/sequences/{sequenceId}/tags")
    public ResponseEntity<List<SequenceTagDTO>> getTags(@PathVariable UUID sequenceId) {
        return ResponseEntity.ok(tagService.getTagsFor(sequenceId));
    }

    /**
     * Adds a manual tag to a specific sequence. Requires ownership or admin privileges.
     *
     * @param sequenceId UUID of the sequence.
     * @param body       Request body containing the tag to add.
     * @return Updated list of SequenceTagDTO after the addition.
     */
    @PostMapping("/sequences/{sequenceId}/tags")
    public ResponseEntity<List<SequenceTagDTO>> addTag(
            @PathVariable UUID sequenceId,
            @RequestBody AddTagRequest body) {
        requireSequenceOwnerOrAdmin(sequenceId);
        if (body == null || body.tag() == null || body.tag().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        tagService.addManualTag(sequenceId, body.tag());
        log.info("User added manual tag '{}' to sequence {}", body.tag(), sequenceId);
        return ResponseEntity.ok(tagService.getTagsFor(sequenceId));
    }

    /**
     * Removes a tag from a specific sequence. Requires ownership or admin privileges.
     *
     * @param sequenceId UUID of the sequence.
     * @param tagName    Name of the tag to remove.
     * @return Updated list of SequenceTagDTO after removal.
     */
    @DeleteMapping("/sequences/{sequenceId}/tags/{tagName}")
    public ResponseEntity<List<SequenceTagDTO>> removeTag(
            @PathVariable UUID sequenceId,
            @PathVariable String tagName) {
        requireSequenceOwnerOrAdmin(sequenceId);
        tagService.removeTag(sequenceId, tagName);
        log.info("User removed tag '{}' from sequence {}", tagName, sequenceId);
        return ResponseEntity.ok(tagService.getTagsFor(sequenceId));
    }

    /**
     * Parses a comma-separated list (tags or concepts) into a trimmed list of strings.
     *
     * @param csv Comma-separated values as a single string.
     * @return List of trimmed strings; null if input is empty or blank.
     */
    private static List<String> parseCsvList(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        List<String> out = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return out.isEmpty() ? null : out;
    }

    /**
     * Ensures the caller owns the sequence or has admin rights.
     *
     * @param sequenceId UUID of the sequence.
     * @throws AccessDeniedException if the caller lacks the necessary privileges.
     */
    private void requireSequenceOwnerOrAdmin(UUID sequenceId) {
        TieredTaskSequenceDTO seq = taskPersistenceService.loadSequenceById(sequenceId);
        if (seq == null) {
            throw new AccessDeniedException("Sequence not found");
        }
        ownershipService.checkOwnership(seq.getOwnerId(), "sequence tags");
    }

    /**
     * One-click copy of a Library sequence into the teacher's "Library Inbox" worksheet (auto-created
     * on first use). The endpoint is idempotent — re-clicking Copy on an already-present sequence
     * is a no-op at the DB level ({@code ON CONFLICT DO NOTHING}), but the response flags
     * {@code alreadyPresent=true} so the UI can differentiate toast copy from "already there".
     *
     * <p>An optional {@code worksheetId} query parameter lets the UI override the default — e.g. a
     * "Save to specific worksheet" dropdown in the future. Ownership of that sheet is enforced.
     *
     * <p>Undo is handled by the existing {@code DELETE /tasks/worksheets/{sheetId}/sequences/{sequenceId}}
     * endpoint — the frontend just remembers the target sheet ID from this response.
     */
    @PostMapping("/sequences/{sequenceId}/copy-to-default")
    public ResponseEntity<CopyToWorksheetResponse> copyToDefault(
            @PathVariable UUID sequenceId,
            @RequestParam(name = "worksheetId", required = false) UUID targetWorksheetId) {

        UUID ownerId = ownershipService.getCurrentUserId();
        if (ownerId == null) {
            throw new AccessDeniedException("Login required to copy a sequence.");
        }
        ownershipService.denyStudents("copy sequences");

        // ── Resolve target worksheet ──
        WorksheetDTO target;
        if (targetWorksheetId != null) {
            target = worksheetService.loadWorksheetById(targetWorksheetId);
            if (target == null || target.getId() == null) {
                throw new AccessDeniedException("Worksheet not found.");
            }
            ownershipService.checkOwnership(target.getOwnerId(), "worksheet");
        } else {
            target = worksheetService.getOrCreateDefaultWorksheet(ownerId);
        }

        // ── Pre-flight presence check (so the UI can distinguish "added" vs "already there") ──
        WorksheetDTO fresh = worksheetService.loadWorksheetById(target.getId());
        boolean alreadyPresent = fresh.getSequenceDTOS() != null
                && fresh.getSequenceDTOS().stream()
                .anyMatch(s -> sequenceId.equals(s.getId()));

        // ── Link (idempotent via ON CONFLICT DO NOTHING) ──
        worksheetService.addSequenceToWorksheet(target.getId(), sequenceId);
        log.info("Copied sequence {} into worksheet {} (alreadyPresent={})",
                 sequenceId, target.getId(), alreadyPresent);

        return ResponseEntity.ok(new CopyToWorksheetResponse(
                target.getId(),
                target.getName(),
                alreadyPresent,
                targetWorksheetId == null));
    }

    /**
     * Response payload for the one-click copy. Returns enough info for the toast to say
     * "Added to <name> — [Undo] [Go]" and for Undo to hit the right DELETE endpoint.
     *
     * @param worksheetId        the worksheet the sequence was added to (or already in)
     * @param worksheetName      display name for the toast
     * @param alreadyPresent     true if the sequence was already linked before this call
     * @param wasDefaultInbox    true if the target was auto-resolved (no explicit {@code worksheetId})
     */
    public record CopyToWorksheetResponse(
            UUID worksheetId,
            String worksheetName,
            boolean alreadyPresent,
            boolean wasDefaultInbox) {}

    /**
     * Bulk variant of {@link #copyToDefault}: copies many sequences into a single target worksheet
     * in one shot. Used by Library "select mode" — the teacher ticks N cards and hits
     * "Copy to worksheet" in the sticky footer. Idempotent: sequences already present in the target
     * are silently skipped (via {@code ON CONFLICT DO NOTHING}) and counted separately so the toast
     * can read "Added 4, skipped 2 duplicates".
     *
     * <p>Target resolution mirrors the single-copy endpoint: explicit {@code worksheetId} in the body,
     * otherwise the caller's Library Inbox (auto-created on first use). Students are denied; worksheet
     * ownership is enforced if an explicit ID is given.
     *
     * <p>No per-sequence Undo is offered — undoing a batch of N selectively is a fiddly UX problem.
     * The caller can always navigate to the worksheet and remove items individually.
     */
    @PostMapping("/sequences/bulk-copy")
    public ResponseEntity<BulkCopyResponse> bulkCopy(@RequestBody BulkCopyRequest body) {
        if (body == null || body.sequenceIds() == null || body.sequenceIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID ownerId = ownershipService.getCurrentUserId();
        if (ownerId == null) {
            throw new AccessDeniedException("Login required to copy sequences.");
        }
        ownershipService.denyStudents("bulk-copy sequences");

        // ── Deduplicate + normalise the request list (client might double-send) ──
        List<UUID> requested = body.sequenceIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requested.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // ── Resolve target worksheet ──
        WorksheetDTO target;
        if (body.worksheetId() != null) {
            target = worksheetService.loadWorksheetById(body.worksheetId());
            if (target == null || target.getId() == null) {
                throw new AccessDeniedException("Worksheet not found.");
            }
            ownershipService.checkOwnership(target.getOwnerId(), "worksheet");
        } else {
            target = worksheetService.getOrCreateDefaultWorksheet(ownerId);
        }

        // ── Pre-flight: which of the requested sequences are already linked? ──
        WorksheetDTO fresh = worksheetService.loadWorksheetById(target.getId());
        Set<UUID> alreadyPresent = new HashSet<>();
        if (fresh.getSequenceDTOS() != null) {
            for (var s : fresh.getSequenceDTOS()) {
                if (s.getId() != null && requested.contains(s.getId())) {
                    alreadyPresent.add(s.getId());
                }
            }
        }

        // ── Bulk-link (ON CONFLICT DO NOTHING handles any race with concurrent copies) ──
        worksheetService.addSequencesToWorksheet(target.getId(), requested);
        int addedCount = requested.size() - alreadyPresent.size();
        log.info("Bulk-copied {} sequences into worksheet {} ({} newly added, {} already present)",
                 requested.size(), target.getId(), addedCount, alreadyPresent.size());

        return ResponseEntity.ok(new BulkCopyResponse(
                target.getId(),
                target.getName(),
                requested.size(),
                addedCount,
                alreadyPresent.size(),
                body.worksheetId() == null));
    }

    /** Request body for bulk-copy. Sequence IDs are required; worksheetId is optional (falls back to Library Inbox). */
    public record BulkCopyRequest(List<UUID> sequenceIds, UUID worksheetId) {}

    /**
     * Response payload for bulk-copy. Lets the toast show "Added {addedCount} to {worksheetName}"
     * (or the nuanced "Added X, skipped Y duplicates" variant when alreadyPresentCount &gt; 0).
     *
     * @param worksheetId          target worksheet
     * @param worksheetName        display name for the toast
     * @param totalRequested       number of unique sequence IDs received
     * @param addedCount           newly linked (totalRequested − alreadyPresentCount)
     * @param alreadyPresentCount  duplicates skipped
     * @param wasDefaultInbox      true if we auto-resolved to Library Inbox
     */
    public record BulkCopyResponse(
            UUID worksheetId,
            String worksheetName,
            int totalRequested,
            int addedCount,
            int alreadyPresentCount,
            boolean wasDefaultInbox) {}
}