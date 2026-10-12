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

import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository.RetagCandidate;
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
 * Admin-triggered bulk concept re-classifier — the concept-facet mirror of {@link
 * BulkRetagService}, offered for the same reason: {@link de.uni_passau.fim.se2.domain.model
 * .ConceptCatalog} can now recognize concepts added after the static catalog file was written
 * (a teacher's free-typed concept, or a curriculum topic's mapping), but a sequence generated
 * before such a concept existed has no way to pick it up on its own. This is how an admin asks
 * the LLM to look again, against whatever the concept arsenal has grown into since.
 *
 * <p>Same two entry points, same job/polling shape, same virtual-thread worker, same in-memory
 * retention — see {@link BulkRetagService}'s class doc for the reasoning, which applies here
 * unchanged.
 *
 * <p>Where it differs: each candidate gets one call to {@link
 * AiModelClient#generateConcepts(String, String)} instead of the semantic tagger, and a
 * non-empty result replaces the sequence's concept links ({@link
 * TieredTaskSequenceRepository#replaceConcepts}) and its structural tags ({@link
 * SequenceTagService#replaceStructuralTags}) together, so the two never drift out of step. An
 * <em>empty</em> result is treated as a skip, not a replacement — the LLM returning nothing this
 * time is not good evidence that the sequence's existing concepts are wrong, and silently
 * clearing them on a fluke would be a worse outcome than leaving them alone.
 */
@Service
public class BulkConceptReclassifyService {

    private static final Logger logger = LoggerFactory.getLogger(BulkConceptReclassifyService.class);

    /** Hard cap on candidates per job — protects LLM budget on an accidental "all sequences" call. */
    private static final int MAX_CANDIDATES_PER_JOB = 5_000;

    /** How many finished jobs we remember for the UI. */
    private static final int MAX_JOBS = 64;

    private final TieredTaskSequenceRepository sequenceRepo;
    private final AiModelClient aiModelClient;
    private final ConceptRepository conceptRepo;
    private final SequenceTagService tagService;
    /** Same virtual-thread pool the bulk retagger and semantic tagging use. */
    private final Executor taggingExecutor;

    /** jobId → live JobState. Reads are wait-free; writes use atomic counters. */
    private final ConcurrentHashMap<UUID, JobState> jobs = new ConcurrentHashMap<>();

    public BulkConceptReclassifyService(TieredTaskSequenceRepository sequenceRepo,
                                        AiModelClient aiModelClient,
                                        ConceptRepository conceptRepo,
                                        SequenceTagService tagService,
                                        @Qualifier("taggingExecutor") Executor taggingExecutor) {
        this.sequenceRepo = sequenceRepo;
        this.aiModelClient = aiModelClient;
        this.conceptRepo = conceptRepo;
        this.tagService = tagService;
        this.taggingExecutor = taggingExecutor;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Queue a new re-classify job. Returns a snapshot with {@code status = QUEUED/RUNNING} and the
     * generated {@code jobId}; the work itself happens on a background virtual thread.
     */
    public BulkConceptReclassifyJobStatus submit(BulkConceptReclassifyRequest request) {
        BulkConceptReclassifyRequest req = request == null ? new BulkConceptReclassifyRequest() : request;

        // Load candidates up-front so the caller immediately knows the total count.
        List<RetagCandidate> candidates = loadCandidates(req);

        UUID jobId = UUID.randomUUID();
        JobState state = new JobState(jobId, candidates.size(), req.effectiveFilterLabel());
        jobs.put(jobId, state);
        pruneJobs();

        logger.info("Bulk concept reclassify job {} queued: filter='{}', total={}",
                jobId, state.filter, state.total);

        if (candidates.isEmpty()) {
            state.markDone();
            return state.snapshot();
        }

        // Fire the worker on the virtual-thread pool, not through @Async — see BulkRetagService.
        taggingExecutor.execute(() -> runJob(state, candidates));

        return state.snapshot();
    }

    /** Live snapshot of a single job, or {@code null} if unknown / evicted. */
    public BulkConceptReclassifyJobStatus status(UUID jobId) {
        JobState s = jobs.get(jobId);
        return s == null ? null : s.snapshot();
    }

    /** All jobs currently in memory, most recently started first. */
    public List<BulkConceptReclassifyJobStatus> listRecent() {
        List<BulkConceptReclassifyJobStatus> out = new ArrayList<>();
        for (JobState s : jobs.values()) out.add(s.snapshot());
        out.sort(Comparator.comparing(BulkConceptReclassifyJobStatus::startedAt).reversed());
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    private List<RetagCandidate> loadCandidates(BulkConceptReclassifyRequest req) {
        try {
            List<UUID> ids = req.sequenceIds();
            boolean missingOnly = req.isMissingConcepts();
            return sequenceRepo.findConceptReclassifyCandidates(missingOnly, ids, MAX_CANDIDATES_PER_JOB);
        } catch (Exception e) {
            logger.warn("Failed to load concept reclassify candidates for filter '{}': {}",
                    req.effectiveFilterLabel(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Worker body. Runs on a virtual thread via {@link #taggingExecutor}. Iterates candidates
     * sequentially — one LLM call at a time so we don't pound the provider.
     */
    private void runJob(JobState state, List<RetagCandidate> candidates) {
        MDC.put("bulkConceptReclassifyJobId", state.jobId.toString());
        state.markRunning();
        logger.info("Bulk concept reclassify job {} started, processing {} sequences",
                state.jobId, candidates.size());

        try {
            for (RetagCandidate c : candidates) {
                processOne(c, state);
            }
            state.markDone();
            logger.info("Bulk concept reclassify job {} finished: succeeded={}, failed={}, total={}",
                    state.jobId, state.succeeded.get(), state.failed.get(), state.total);
        } catch (Exception e) {
            state.markFailed(e.getMessage());
            logger.error("Bulk concept reclassify job {} aborted: {}", state.jobId, e.getMessage(), e);
        } finally {
            MDC.remove("bulkConceptReclassifyJobId");
        }
    }

    private void processOne(RetagCandidate c, JobState state) {
        try {
            if (c.combinedCode() == null || c.combinedCode().isBlank()) {
                // Nothing to classify — treat as processed-but-skipped so the counter advances.
                state.processed.incrementAndGet();
                return;
            }

            List<String> conceptSlugs = aiModelClient.generateConcepts(c.combinedCode(), c.programmingLanguage());

            if (conceptSlugs == null || conceptSlugs.isEmpty()) {
                // See the class doc: an empty result is a skip, not "this sequence has no
                // concepts" — it must not clear what generation already found.
                state.processed.incrementAndGet();
                return;
            }

            List<ConceptDTO> dtos = conceptSlugs.stream()
                    .map(name -> {
                        ConceptDTO dto = new ConceptDTO();
                        dto.setName(name);
                        return dto;
                    })
                    .toList();
            List<UUID> conceptIds = conceptRepo.saveAll(dtos);

            sequenceRepo.replaceConcepts(c.sequenceId(), conceptIds);
            tagService.replaceStructuralTags(c.sequenceId(), conceptSlugs);

            state.succeeded.incrementAndGet();
        } catch (Exception e) {
            state.failed.incrementAndGet();
            logger.warn("Concept reclassify failed for sequence {} in job {}: {}",
                    c.sequenceId(), state.jobId, e.getMessage());
        } finally {
            state.processed.incrementAndGet();
        }
    }

    /** Evict oldest finished jobs so the in-memory map doesn't grow unbounded. */
    private void pruneJobs() {
        if (jobs.size() <= MAX_JOBS) return;
        List<JobState> finished = jobs.values().stream()
                .filter(s -> BulkConceptReclassifyJobStatus.DONE.equals(s.status)
                        || BulkConceptReclassifyJobStatus.FAILED.equals(s.status))
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
     * Body of {@code POST /api/admin/sequences/reclassify-concepts}.
     *
     * <p>Three filter flavors:
     * <ul>
     *   <li>{@code filter = "missing-concepts"} &mdash; only sequences with no linked concept at
     *       all (<b>default</b>).</li>
     *   <li>{@code filter = "all"} &mdash; every sequence in the system, re-classified and
     *       overwritten if the LLM returns something (dangerous — bounded by
     *       {@link #MAX_CANDIDATES_PER_JOB}). This is the flavor that actually answers "does the
     *       extended concept arsenal apply to material generated before it existed" — most
     *       sequences already have concepts from generation time, so {@code missing-concepts}
     *       alone rarely finds anything to do.</li>
     *   <li>{@code sequenceIds = [...]} &mdash; explicit list; overrides {@code filter}.</li>
     * </ul>
     */
    public static final class BulkConceptReclassifyRequest {
        private String filter;
        private List<UUID> sequenceIds;

        public BulkConceptReclassifyRequest() {}

        public BulkConceptReclassifyRequest(String filter, List<UUID> sequenceIds) {
            this.filter = filter;
            this.sequenceIds = sequenceIds;
        }

        public String getFilter() { return filter; }
        public void setFilter(String filter) { this.filter = filter; }

        public List<UUID> getSequenceIds() { return sequenceIds; }
        public void setSequenceIds(List<UUID> sequenceIds) { this.sequenceIds = sequenceIds; }

        // Convenience accessors used by the service layer.
        List<UUID> sequenceIds() { return sequenceIds; }

        boolean isMissingConcepts() {
            return filter == null || filter.isBlank() || "missing-concepts".equalsIgnoreCase(filter);
        }

        String effectiveFilterLabel() {
            if (sequenceIds != null && !sequenceIds.isEmpty()) return "specific(" + sequenceIds.size() + ")";
            if (isMissingConcepts()) return "missing-concepts";
            if ("all".equalsIgnoreCase(filter)) return "all";
            return filter == null ? "missing-concepts" : filter;
        }
    }

    /** Mutable, thread-safe live state of a running job. Hidden behind {@link BulkConceptReclassifyJobStatus} snapshots. */
    static final class JobState {
        final UUID jobId;
        final int total;
        final String filter;
        final AtomicInteger processed = new AtomicInteger(0);
        final AtomicInteger succeeded = new AtomicInteger(0);
        final AtomicInteger failed    = new AtomicInteger(0);
        volatile Instant startedAt;
        volatile Instant finishedAt;
        volatile String status = BulkConceptReclassifyJobStatus.QUEUED;
        volatile String errorMessage;

        JobState(UUID jobId, int total, String filter) {
            this.jobId = jobId;
            this.total = total;
            this.filter = filter;
            this.startedAt = Instant.now();
        }

        void markRunning() { this.status = BulkConceptReclassifyJobStatus.RUNNING; }

        void markDone() {
            this.status = BulkConceptReclassifyJobStatus.DONE;
            this.finishedAt = Instant.now();
        }

        void markFailed(String msg) {
            this.status = BulkConceptReclassifyJobStatus.FAILED;
            this.errorMessage = msg;
            this.finishedAt = Instant.now();
        }

        BulkConceptReclassifyJobStatus snapshot() {
            return new BulkConceptReclassifyJobStatus(jobId, status, filter, total,
                    processed.get(), succeeded.get(), failed.get(),
                    startedAt, finishedAt, errorMessage);
        }
    }
}
