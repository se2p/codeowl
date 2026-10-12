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

import de.uni_passau.fim.se2.application.service.BulkRetagJobStatus;
import de.uni_passau.fim.se2.application.service.BulkRetagService;
import de.uni_passau.fim.se2.application.service.BulkRetagService.BulkRetagRequest;
import de.uni_passau.fim.se2.application.service.SequenceTagService;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AP19 Teil A admin endpoints for tag management.
 *
 * <p>Separate from {@link AdminController} (user management) so the two concerns don't bleed into
 * one 400-line file. All routes live under {@code /api/admin/sequences} / {@code /api/admin/tags}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/admin/sequences/retag}            &mdash; queue a bulk retag job (202 Accepted)</li>
 *   <li>{@code GET  /api/admin/sequences/retag/{jobId}}    &mdash; poll progress of a specific job</li>
 *   <li>{@code GET  /api/admin/sequences/retag}            &mdash; list recent jobs (UI dashboard)</li>
 *   <li>{@code GET  /api/admin/tags}                       &mdash; tag cloud / usage counts</li>
 * </ul>
 *
 * <p>Security: mounted under {@code /api/admin} — rely on the existing admin-role filter configured
 * for {@link AdminController}. If a finer-grained permission is added later, decorate individual
 * routes with {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTagController {

    private final BulkRetagService bulkRetagService;
    private final SequenceTagService sequenceTagService;

    /**
     * Queues a bulk retag job. Returns {@code 202 Accepted} with the job ID and a {@code pollUrl}
     * the SPA can hit to track progress. The actual LLM work happens on a background virtual thread.
     */
    @PostMapping("/sequences/retag")
    public ResponseEntity<Map<String, Object>> startBulkRetag(@RequestBody(required = false) BulkRetagRequest body) {
        BulkRetagJobStatus snapshot = bulkRetagService.submit(body);

        Map<String, Object> response = Map.of(
                "jobId",   snapshot.jobId().toString(),
                "status",  snapshot.status(),
                "filter",  snapshot.filter(),
                "total",   snapshot.total(),
                "pollUrl", "/api/admin/sequences/retag/" + snapshot.jobId()
        );

        log.info("Bulk retag requested: jobId={}, filter={}, total={}",
                snapshot.jobId(), snapshot.filter(), snapshot.total());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Returns the live progress of a single job. {@code 404} if the job ID is unknown
     * (never existed, or already evicted from the in-memory ring).
     */
    @GetMapping("/sequences/retag/{jobId}")
    public ResponseEntity<?> getBulkRetagStatus(@PathVariable UUID jobId) {
        BulkRetagJobStatus snapshot = bulkRetagService.status(jobId);
        if (snapshot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Unknown or evicted jobId: " + jobId));
        }
        return ResponseEntity.ok(snapshot);
    }

    /** Lists all retag jobs currently in memory, newest first. Used by the admin dashboard. */
    @GetMapping("/sequences/retag")
    public ResponseEntity<?> listBulkRetagJobs() {
        return ResponseEntity.ok(bulkRetagService.listRecent());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tag metadata
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the full tag cloud: every distinct tag_name with its usage count, sorted by popularity.
     * This is the source of truth for the admin "merge tags" UI (AP19 A16) and the Library filter
     * sidebar facet counts (AP19 A8).
     */
    @GetMapping("/tags")
    public ResponseEntity<?> listAllTags() {
        java.util.List<SequenceTagRepository.TagCount> counts = sequenceTagService.getTagCloud();
        return ResponseEntity.ok(counts);
    }

    // ─────────────────────────────────────────────────────────────────────
    // AP19 A16: new-topics report + tag merge
    // ─────────────────────────────────────────────────────────────────────

    /**
     * "New topics" weekly report. Returns tags whose first appearance in the system falls within
     * the past {@code days} days (default 7), ordered by usage count DESC. Feeds the admin
     * dashboard's weekly-review widget — each row comes with an action to merge it into an existing
     * canonical tag via {@link #mergeTags}.
     *
     * @param days  window size in days (1..90, default 7)
     * @param limit max rows (1..200, default 50)
     */
    @GetMapping("/tags/new-topics")
    public ResponseEntity<java.util.List<SequenceTagRepository.NewTopic>> listNewTopics(
            @RequestParam(name = "days",  required = false, defaultValue = "7")  int days,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok(sequenceTagService.getNewTopics(days, limit));
    }

    /**
     * Merges one tag name into another across every sequence in the system. Body:
     * <pre>{ "from": "rekursion", "to": "recursion" }</pre>
     *
     * <p>Idempotent and safe:
     * <ul>
     *   <li>Merging a tag into itself → no-op, 0 affected.</li>
     *   <li>Merging a missing tag → 0 affected.</li>
     *   <li>Sequences that already carry both tags lose the "from" row (preserves the unique key).</li>
     * </ul>
     *
     * <p>Response returns the affected-row count so the UI can show "Merged — N rows updated".
     */
    @PostMapping("/tags/merge")
    public ResponseEntity<Map<String, Object>> mergeTags(@RequestBody MergeTagsRequest body) {
        if (body == null || body.from() == null || body.to() == null
                || body.from().isBlank() || body.to().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Both 'from' and 'to' tag names are required."));
        }
        int affected = sequenceTagService.mergeTags(body.from(), body.to());
        log.info("Admin merged tag '{}' → '{}' (rows affected: {})", body.from(), body.to(), affected);
        return ResponseEntity.ok(Map.of(
                "from",     body.from(),
                "to",       body.to(),
                "affected", affected));
    }

    /** Request body for the tag-merge endpoint. */
    public record MergeTagsRequest(String from, String to) {}
}