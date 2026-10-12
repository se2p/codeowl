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

import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository.RetagCandidate;
import de.uni_passau.fim.se2.infrastructure.ai.tagging.LlmSemanticTagger;
import de.uni_passau.fim.se2.infrastructure.ai.tagging.LlmSemanticTagger.SemanticTagResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AP19 Teil A: admin-triggered bulk retagger.
 *
 * <p>Two entry points:
 * <ol>
 *   <li>{@link #submit(BulkRetagRequest)} &mdash; returns a job ID immediately and kicks off the work
 *       on the {@code taggingExecutor} virtual-thread pool.</li>
 *   <li>{@link #status(UUID)} &mdash; returns a live snapshot for polling.</li>
 * </ol>
 *
 * <p>Processing is <b>sequential within a job</b>: the LLM is called once per candidate, and the
 * job's {@code processed} counter ticks up after each iteration. Multiple jobs can run in parallel
 * (each on its own virtual thread) &mdash; admins who want higher throughput can chunk their IDs
 * across several calls.
 *
 * <p>Jobs are kept in an in-memory map with a small retention policy (newest {@link #MAX_JOBS} kept).
 * The SPA can show "recent jobs" by calling {@link #listRecent()}. Server restart drops history; this
 * is acceptable because the work itself is idempotent.
 */
@Service
public class BulkRetagService {

    private static final Logger logger = LoggerFactory.getLogger(BulkRetagService.class);

    /** Hard cap on candidates per job — protects LLM budget on an accidental "all sequences" call. */
    private static final int MAX_CANDIDATES_PER_JOB = 5_000;

    /** How many finished jobs we remember for the UI. */
    private static final int MAX_JOBS = 64;

    private final TieredTaskSequenceRepository sequenceRepo;
    private final LlmSemanticTagger tagger;
    private final SequenceTagService tagService;
    /** Same virtual-thread pool that semantic tagging uses. Injected directly instead of via
     *  {@code @Async} because self-invocation skips the Spring AOP proxy. */
    private final Executor taggingExecutor;

    /** jobId → live JobState. Reads are wait-free; writes use atomic counters. */
    private final ConcurrentHashMap<UUID, JobState> jobs = new ConcurrentHashMap<>();

    public BulkRetagService(TieredTaskSequenceRepository sequenceRepo,
                            LlmSemanticTagger tagger,
                            SequenceTagService tagService,
                            @Qualifier("taggingExecutor") Executor taggingExecutor) {
        this.sequenceRepo = sequenceRepo;
        this.tagger = tagger;
        this.tagService = tagService;
        this.taggingExecutor = taggingExecutor;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Queue a new retag job. Returns a snapshot with {@code status = QUEUED/RUNNING} and the
     * generated {@code jobId}; the work itself happens on a background virtual thread.
     */
    public BulkRetagJobStatus submit(BulkRetagRequest request) {
        BulkRetagRequest req = request == null ? new BulkRetagRequest() : request;

        // Load candidates up-front so the caller immediately knows the total count.
        List<RetagCandidate> candidates = loadCandidates(req);

        UUID jobId = UUID.randomUUID();
        JobState state = new JobState(jobId, candidates.size(), req.effectiveFilterLabel());
        jobs.put(jobId, state);
        pruneJobs();

        logger.info("Bulk retag job {} queued: filter='{}', total={}",
                jobId, state.filter, state.total);

        if (candidates.isEmpty()) {
            state.markDone();
            return state.snapshot();
        }

        // Fire the worker on the virtual-thread pool. We deliberately do NOT go through @Async
        // so self-invocation of service methods still works through the Spring proxy.
        taggingExecutor.execute(() -> runJob(state, candidates));

        return state.snapshot();
    }

    /** Live snapshot of a single job, or {@code null} if unknown / evicted. */
    public BulkRetagJobStatus status(UUID jobId) {
        JobState s = jobs.get(jobId);
        return s == null ? null : s.snapshot();
    }

    /** All jobs currently in memory, most recently started first. */
    public List<BulkRetagJobStatus> listRecent() {
        List<BulkRetagJobStatus> out = new ArrayList<>();
        for (JobState s : jobs.values()) out.add(s.snapshot());
        out.sort(Comparator.comparing(BulkRetagJobStatus::startedAt).reversed());
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    private List<RetagCandidate> loadCandidates(BulkRetagRequest req) {
        try {
            List<UUID> ids = req.sequenceIds();
            boolean missingOnly = req.isMissingLlmTags();
            return sequenceRepo.findRetagCandidates(missingOnly, ids, MAX_CANDIDATES_PER_JOB);
        } catch (Exception e) {
            logger.warn("Failed to load retag candidates for filter '{}': {}",
                    req.effectiveFilterLabel(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Worker body. Runs on a virtual thread via {@link #taggingExecutor}. Iterates candidates
     * sequentially — one LLM call at a time so we don't pound the provider.
     */
    private void runJob(JobState state, List<RetagCandidate> candidates) {
        MDC.put("bulkRetagJobId", state.jobId.toString());
        state.markRunning();
        logger.info("Bulk retag job {} started, processing {} sequences", state.jobId, candidates.size());

        try {
            for (RetagCandidate c : candidates) {
                processOne(c, state);
            }
            state.markDone();
            logger.info("Bulk retag job {} finished: succeeded={}, failed={}, total={}",
                    state.jobId, state.succeeded.get(), state.failed.get(), state.total);
        } catch (Exception e) {
            state.markFailed(e.getMessage());
            logger.error("Bulk retag job {} aborted: {}", state.jobId, e.getMessage(), e);
        } finally {
            MDC.remove("bulkRetagJobId");
        }
    }

    private void processOne(RetagCandidate c, JobState state) {
        try {
            if (c.combinedCode() == null || c.combinedCode().isBlank()) {
                // Nothing to tag — treat as processed-but-skipped so the counter advances.
                state.processed.incrementAndGet();
                return;
            }

            SemanticTagResult result = tagger.tag(
                    c.programmingLanguage(), c.combinedStatement(), c.combinedCode());

            if (result == null) {
                state.failed.incrementAndGet();
                state.processed.incrementAndGet();
                return;
            }

            tagService.writeLlmTags(c.sequenceId(), result.primaryTags(), result.aliases());

            if (result.shortDescription() != null && !result.shortDescription().isBlank()) {
                try {
                    sequenceRepo.updateSemanticSummary(c.sequenceId(), result.shortDescription());
                } catch (Exception e) {
                    logger.warn("Summary update failed for sequence {} in job {}: {}",
                            c.sequenceId(), state.jobId, e.getMessage());
                }
            }

            state.succeeded.incrementAndGet();
        } catch (Exception e) {
            state.failed.incrementAndGet();
            logger.warn("Retag failed for sequence {} in job {}: {}",
                    c.sequenceId(), state.jobId, e.getMessage());
        } finally {
            state.processed.incrementAndGet();
        }
    }

    /** Evict oldest finished jobs so the in-memory map doesn't grow unbounded. */
    private void pruneJobs() {
        if (jobs.size() <= MAX_JOBS) return;
        List<JobState> finished = jobs.values().stream()
                .filter(s -> BulkRetagJobStatus.DONE.equals(s.status) || BulkRetagJobStatus.FAILED.equals(s.status))
                .sorted(Comparator.comparing((JobState s) -> s.startedAt))
                .toList();
        int toRemove = jobs.size() - MAX_JOBS;
        for (int i = 0; i < toRemove && i < finished.size(); i++) {
            jobs.remove(finished.get(i).jobId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Body of {@code POST /api/admin/sequences/retag}.
     *
     * <p>Three filter flavors:
     * <ul>
     *   <li>{@code filter = "missing-llm-tags"} &mdash; only sequences that currently have no
     *       {@code source='llm'} tag row (<b>default</b>).</li>
     *   <li>{@code filter = "all"} &mdash; every sequence in the system (dangerous — bounded by
     *       {@link #MAX_CANDIDATES_PER_JOB}).</li>
     *   <li>{@code sequenceIds = [...]} &mdash; explicit list; overrides {@code filter}.</li>
     * </ul>
     */
    public static final class BulkRetagRequest {
        private String filter;
        private List<UUID> sequenceIds;

        public BulkRetagRequest() {}

        public BulkRetagRequest(String filter, List<UUID> sequenceIds) {
            this.filter = filter;
            this.sequenceIds = sequenceIds;
        }

        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }

        public List<UUID> getSequenceIds() { return sequenceIds; }
        public void setSequenceIds(List<UUID> sequenceIds) { this.sequenceIds = sequenceIds; }

        // Convenience accessors used by the service layer.
        List<UUID> sequenceIds() { return sequenceIds; }

        boolean isMissingLlmTags() {
            return filter == null || filter.isBlank() || "missing-llm-tags".equalsIgnoreCase(filter);
        }

        String effectiveFilterLabel() {
            if (sequenceIds != null && !sequenceIds.isEmpty()) return "specific(" + sequenceIds.size() + ")";
            if (isMissingLlmTags()) return "missing-llm-tags";
            if ("all".equalsIgnoreCase(filter)) return "all";
            return filter == null ? "missing-llm-tags" : filter;
        }
    }

    /** Mutable, thread-safe live state of a running job. Hidden behind {@link BulkRetagJobStatus} snapshots. */
    static final class JobState {
        final UUID jobId;
        final int total;
        final String filter;
        final AtomicInteger processed = new AtomicInteger(0);
        final AtomicInteger succeeded = new AtomicInteger(0);
        final AtomicInteger failed    = new AtomicInteger(0);
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile String status = BulkRetagJobStatus.QUEUED;
        volatile String errorMessage;

        JobState(UUID jobId, int total, String filter) {
            this.jobId = jobId;
            this.total = total;
            this.filter = filter;
            this.startedAt = Instant.now();
        }

        void markRunning() { this.status = BulkRetagJobStatus.RUNNING; }

        void markDone() {
            this.status = BulkRetagJobStatus.DONE;
            this.finishedAt = Instant.now();
        }

        void markFailed(String msg) {
            this.status = BulkRetagJobStatus.FAILED;
            this.errorMessage = msg;
            this.finishedAt = Instant.now();
        }

        BulkRetagJobStatus snapshot() {
            return new BulkRetagJobStatus(jobId, status, filter, total,
                    processed.get(), succeeded.get(), failed.get(),
                    startedAt, finishedAt, errorMessage);
        }
    }
}
