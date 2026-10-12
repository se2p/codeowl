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

import de.uni_passau.fim.se2.application.service.BulkConceptReclassifyJobStatus;
import de.uni_passau.fim.se2.application.service.BulkConceptReclassifyService;
import de.uni_passau.fim.se2.application.service.BulkConceptReclassifyService.BulkConceptReclassifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoint for the bulk concept re-classifier — the concept-facet mirror of {@link
 * AdminTagController}'s bulk retagger, in its own file for the same reason that one is split from
 * {@link AdminController}: so the two concerns don't bleed into one file.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/admin/sequences/reclassify-concepts}         &mdash; queue a bulk
 *       re-classify job (202 Accepted)</li>
 *   <li>{@code GET  /api/admin/sequences/reclassify-concepts/{jobId}} &mdash; poll progress of a
 *       specific job</li>
 *   <li>{@code GET  /api/admin/sequences/reclassify-concepts}         &mdash; list recent jobs
 *       (UI dashboard)</li>
 * </ul>
 *
 * <p>Security: mounted under {@code /api/admin} — relies on the existing admin-role filter
 * configured in {@code SecurityConfig} ({@code /api/admin/** } → {@code hasRole("ADMIN")}).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminConceptController {

    private final BulkConceptReclassifyService bulkConceptReclassifyService;

    /**
     * Queues a bulk concept re-classify job. Returns {@code 202 Accepted} with the job ID and a
     * {@code pollUrl} the SPA can hit to track progress. The actual LLM work happens on a
     * background virtual thread.
     */
    @PostMapping("/sequences/reclassify-concepts")
    public ResponseEntity<Map<String, Object>> startBulkConceptReclassify(
            @RequestBody(required = false) BulkConceptReclassifyRequest body) {
        BulkConceptReclassifyJobStatus snapshot = bulkConceptReclassifyService.submit(body);

        Map<String, Object> response = Map.of(
                "jobId",   snapshot.jobId().toString(),
                "status",  snapshot.status(),
                "filter",  snapshot.filter(),
                "total",   snapshot.total(),
                "pollUrl", "/api/admin/sequences/reclassify-concepts/" + snapshot.jobId()
        );

        log.info("Bulk concept reclassify requested: jobId={}, filter={}, total={}",
                snapshot.jobId(), snapshot.filter(), snapshot.total());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Returns the live progress of a single job. {@code 404} if the job ID is unknown
     * (never existed, or already evicted from the in-memory ring).
     */
    @GetMapping("/sequences/reclassify-concepts/{jobId}")
    public ResponseEntity<?> getBulkConceptReclassifyStatus(@PathVariable UUID jobId) {
        BulkConceptReclassifyJobStatus snapshot = bulkConceptReclassifyService.status(jobId);
        if (snapshot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Unknown or evicted jobId: " + jobId));
        }
        return ResponseEntity.ok(snapshot);
    }

    /** Lists all concept-reclassify jobs currently in memory, newest first. */
    @GetMapping("/sequences/reclassify-concepts")
    public ResponseEntity<?> listBulkConceptReclassifyJobs() {
        return ResponseEntity.ok(bulkConceptReclassifyService.listRecent());
    }
}
