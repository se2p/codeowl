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

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a {@link BulkRetagService} job.
 *
 * <p>AP19 Teil A: admins poll {@code GET /api/admin/sequences/retag/{jobId}} and see
 * {@code processed/total} progress. Unlike the live {@code BulkRetagService.JobState}
 * counters, this record is safe to serialize / log / embed in responses.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code QUEUED}   &mdash; job created, worker not yet started</li>
 *   <li>{@code RUNNING}  &mdash; worker iterating candidates</li>
 *   <li>{@code DONE}     &mdash; all candidates processed (some may still have failed individually)</li>
 *   <li>{@code FAILED}   &mdash; fatal error aborted the loop; {@code errorMessage} is set</li>
 * </ul>
 */
public record BulkRetagJobStatus(
        UUID jobId,
        String status,
        String filter,
        int total,
        int processed,
        int succeeded,
        int failed,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {
    public static final String QUEUED  = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE    = "DONE";
    public static final String FAILED  = "FAILED";
}
