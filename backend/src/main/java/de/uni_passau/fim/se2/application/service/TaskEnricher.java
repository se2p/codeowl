/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.GenerationException;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.ComplexityResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDateTime;

/**
 * Enriches a TieredTaskSequence by adding code solutions and (optional)
 * complexity metrics. New Task instances are created with fresh IDs.
 *
 * <p>Enrichment is parallelized at two levels (BA-Optimierung OPT-1 + OPT-3):
 * <ul>
 *   <li><b>Inter-Task (OPT-1):</b> Alle Tasks einer Sequenz werden parallel angereichert,
 *       da die einzelnen Tasks nach dem strategy.generate(…) voneinander unabhängig sind.</li>
 *   <li><b>Intra-Task (OPT-3):</b> Context-Annotator und Distractor-Generator hängen beide
 *       nicht voneinander ab und laufen pro Task ebenfalls parallel.</li>
 * </ul>
 *
 * <p>Backing-Executor: virtual threads (JDK 21), damit die blockierenden HTTP-Calls
 * ohne Thread-Starvation horizontal skalieren.
 */
@Service
public final class TaskEnricher {

    private static final Logger logger = LoggerFactory.getLogger(TaskEnricher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper(); // AP31: split_plan (de)serialization

    private final AiModelClient client;
    private final InterchangeableAnnotator interchangeableAnnotator;
    private final ContextAnnotator contextAnnotator;
    private final CodeSanitizerService codeSanitizerService;
    private final CodeAnalyzer codeAnalyzer;

    /**
     * Dedicated virtual-thread executor for LLM-bound I/O.
     * Virtual threads are cheap, so we can allocate one per task without pool tuning.
     * Shared across requests — Spring bean lifecycle handles disposal implicitly
     * (JVM shutdown cleans up).
     */
    private final ExecutorService enrichmentExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    public TaskEnricher(AiModelClient client,
                        InterchangeableAnnotator interchangeableAnnotator,
                        ContextAnnotator contextAnnotator,
                        CodeSanitizerService codeSanitizerService,
                        CodeAnalyzer codeAnalyzer) {
        this.client = client;
        this.interchangeableAnnotator = interchangeableAnnotator;
        this.contextAnnotator = contextAnnotator;
        this.codeSanitizerService = codeSanitizerService;
        this.codeAnalyzer = codeAnalyzer;
    }

    /**
     * For each task in the given sequence, fetches the code solution from GPT
     * and returns a new TieredTaskSequence with enriched task objects.
     *
     * @param incompleteSequence the task sequence with only problem statements
     * @return a fully enriched sequence including solutions
     */
    public TieredTaskSequenceDTO enrichWithSolutions(final TieredTaskSequenceDTO incompleteSequence) {
        return enrichWithSolutions(incompleteSequence, false);
    }

    /**
     * Enriches tasks with solutions and optionally generates distractors (AP15).
     */
    public TieredTaskSequenceDTO enrichWithSolutions(final TieredTaskSequenceDTO incompleteSequence,
                                                     final boolean distractorsEnabled) {
        return enrichWithSolutions(incompleteSequence, distractorsEnabled, false);
    }

    /**
     * Enriches tasks with solutions, optional distractors (AP15), and optional context annotation (AP22).
     * <p>All tasks are enriched in parallel (OPT-1).
     */
    public TieredTaskSequenceDTO enrichWithSolutions(final TieredTaskSequenceDTO incompleteSequence,
                                                     final boolean distractorsEnabled,
                                                     final boolean contextModeEnabled) {
        return enrichWithSolutions(incompleteSequence, distractorsEnabled, contextModeEnabled, false);
    }

    /**
     * AP31: additionally generates inline-split plans (post-solve, validated against the
     * final puzzle; invalid or failed splits are dropped — fail-open).
     */
    public TieredTaskSequenceDTO enrichWithSolutions(final TieredTaskSequenceDTO incompleteSequence,
                                                     final boolean distractorsEnabled,
                                                     final boolean contextModeEnabled,
                                                     final boolean splitEnabled) {
        logger.trace("Enriching task sequence with solutions (distractors={}, context={}, split={}): {}",
                distractorsEnabled, contextModeEnabled, splitEnabled, incompleteSequence);

        final String lang = incompleteSequence.getProgrammingLanguage();
        final UUID sequenceId = incompleteSequence.getId();
        final long t0 = System.nanoTime();

        // OPT-1: Inter-Task-Parallelisierung via virtual threads.
        // Ordering of enrichedTasks preserves the original (Tier 1, 2, 3, …) order
        // because CompletableFuture.join() is called in list-order.
        List<CompletableFuture<TaskDTO>> futures = incompleteSequence.getTasks().stream()
                .map(task -> CompletableFuture.supplyAsync(
                        () -> enrichSingleTask(task, lang, sequenceId, contextModeEnabled, distractorsEnabled, splitEnabled),
                        enrichmentExecutor))
                .toList();

        List<TaskDTO> enrichedTasks;
        try {
            enrichedTasks = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        } catch (CompletionException ce) {
            // Unwrap to the original GenerationException for consistent error handling upstream.
            Throwable cause = ce.getCause();
            if (cause instanceof GenerationException ge) throw ge;
            if (cause instanceof RuntimeException re) throw re;
            throw new GenerationException("Parallel task enrichment failed: " + cause, cause);
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        logger.debug("Enriched {} tasks in {}ms (parallel, distractors={}, context={})",
                enrichedTasks.size(), elapsedMs, distractorsEnabled, contextModeEnabled);

        return TieredTaskSequenceDTO.builder()
                .id(UUID.randomUUID())
                .programmingLanguage(incompleteSequence.getProgrammingLanguage())
                .concepts(incompleteSequence.getConcepts())
                .tasks(enrichedTasks)
                .codeSnippet(incompleteSequence.getCodeSnippet())
                .taskDescription(incompleteSequence.getTaskDescription())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Enriches a single task. Designed to be invoked on a dedicated (virtual) thread.
     * <p>Internally parallelizes independent LLM calls (OPT-3): ContextAnnotator and
     * Distractor-Generation both only need {@code sanitizedCode} + {@code complexity},
     * so they can run concurrently.
     */
    private TaskDTO enrichSingleTask(TaskDTO task, String lang, UUID sequenceId,
                                     boolean contextModeEnabled, boolean distractorsEnabled,
                                     boolean splitEnabled) {
        // Add task identifier to MDC so parallel log lines remain traceable.
        String previousMdc = MDC.get("taskId");
        MDC.put("taskId", task.getId() != null ? task.getId().toString() : "new");
        if (sequenceId != null) MDC.put("sequenceId", sequenceId.toString());

        try {
            logger.debug("Contract: {}", task.getContract());

            // AP22: When context mode is enabled, modify the contract to request fuller code.
            SolveContract solveContract = task.getContract();
            if (contextModeEnabled) {
                solveContract = SolveContract.builder()
                        .language(solveContract.language())
                        .taskStatement(solveContract.taskStatement())
                        .functionSignature(solveContract.functionSignature())
                        .purposeBehavior(solveContract.purposeBehavior()
                                + "\n\nCONTEXT MODE: Generate a COMPLETE program, not just a single function. "
                                + "Include imports, class/module declarations, and a main method or test harness. "
                                + "The solution should be 25-40 lines total with realistic boilerplate.")
                        .prePostConditions(solveContract.prePostConditions())
                        .examples(solveContract.examples())
                        .build();
            }

            // Pass 0: GPT solves the problem (blocking LLM call).
            JsonNode codeNode = client.solveProblem(solveContract);
            String code = codeNode.asText();

            // Pass 1: Sanitize (remove comments) — fast CPU.
            String sanitizedCode = codeSanitizerService.removeComments(code, lang);

            // Pass 2: AP16 — Annotate interchangeable lines (#group/#swap markers) (LLM).
            String annotatedCode = interchangeableAnnotator.annotate(sanitizedCode, lang);

            // Compute complexity up-front: needed by distractor generation AND cheap.
            ComplexityResult complexity = codeAnalyzer.computeComplexity(sanitizedCode, lang);

            // OPT-3: ContextAnnotator and DistractorGenerator are independent — run in parallel.
            CompletableFuture<ContextAnnotator.ContextResult> ctxFut =
                    contextModeEnabled
                            ? CompletableFuture.supplyAsync(
                                    () -> contextAnnotator.annotate(annotatedCode, lang),
                                    enrichmentExecutor)
                            : CompletableFuture.completedFuture(null);

            final String distractorSource = sanitizedCode;
            final int loc = complexity.linesOfCode();
            CompletableFuture<String> distFut =
                    distractorsEnabled
                            ? CompletableFuture.supplyAsync(
                                    () -> generateDistractorsForTask(
                                            distractorSource, lang, task.getStatement(),
                                            task.getTierLevel(), task.getDistractorPlan(), loc),
                                    enrichmentExecutor)
                            : CompletableFuture.completedFuture(null);

            ContextAnnotator.ContextResult ctxResult = ctxFut.join();
            String distractors = distFut.join();

            String finalPuzzle = annotatedCode;
            String contextBefore = null;
            String contextAfter = null;
            if (ctxResult != null) {
                finalPuzzle = ctxResult.puzzleSolution();
                contextBefore = ctxResult.contextBefore();
                contextAfter = ctxResult.contextAfter();
                logger.debug("Context separation applied for tier {} task (before={}, after={})",
                        task.getTierLevel(),
                        contextBefore != null ? contextBefore.split("\n").length : 0,
                        contextAfter != null ? contextAfter.split("\n").length : 0);
            }

            // AP31: post-solve inline-split generation (opt-in), validated against the final
            // puzzle. Fail-open: any failure or reconstruction mismatch yields no splits.
            String splitPlanJson = splitEnabled
                    ? generateSplitPlanForTask(finalPuzzle, lang, task.getStatement(), task.getTierLevel(), distractorsEnabled)
                    : null;

            return TaskDTO.builder()
                    .id(UUID.randomUUID())
                    .statement(task.getStatement())
                    .solution(finalPuzzle)
                    .tierLevel(task.getTierLevel())
                    .cyclomaticComplexity(complexity.cyclomaticComplexity())
                    .maximalNestingDepth(complexity.maxNestingDepth())
                    .distinctIdentifierCount(complexity.distinctIdentifierCount())
                    .linesOfCode(complexity.linesOfCode())
                    .methodCallCount(complexity.methodCallCount())
                    .contract(task.getContract())
                    .distractorPlan(task.getDistractorPlan())
                    .distractors(distractors)
                    .contextBefore(contextBefore)
                    .contextAfter(contextAfter)
                    .splitPlan(splitPlanJson)
                    .build();
        } catch (GenerationException ge) {
            throw ge;
        } catch (Exception e) {
            throw new GenerationException("Failed to enrich task: " + task.getStatement(), e);
        } finally {
            if (previousMdc != null) MDC.put("taskId", previousMdc);
            else MDC.remove("taskId");
            MDC.remove("sequenceId");
        }
    }

    /**
     * Generates distractors for a single task via the Antagonist-Call (AP15).
     * Applies the Mengen-Heuristik (T1: 1-2, T2: 2-3, T3: 3-4) capped at 30% of LOC.
     *
     * @return newline-separated distractor lines (each ending with #distractor), or null on failure
     */
    public String generateDistractorsForTask(String solutionCode, String language,
                                               String taskStatement, int tierLevel,
                                               String distractorPlan, int linesOfCode) {
        try {
            // Mengen-Heuristik: tier-based count (AP21: extended for 5-tier)
            int baseCount = switch (tierLevel) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                case 4 -> 3;
                case 5 -> 4;
                default -> 2;
            };
            // Cap at 30% of solution LOC (minimum 1)
            int maxByLoc = Math.max(1, (int) Math.ceil(linesOfCode * 0.3));
            int count = Math.min(baseCount + 1, maxByLoc);

            logger.debug("Generating {} distractors for tier {} task (LOC={})", count, tierLevel, linesOfCode);

            List<String> distractorLines = client.generateDistractors(
                    solutionCode, language, taskStatement, tierLevel, distractorPlan, count);

            if (distractorLines == null || distractorLines.isEmpty()) {
                logger.warn("No distractors returned for task: {}", taskStatement);
                return null;
            }

            // Ensure every line ends with #distractor and does NOT contain #swap or #group
            List<String> validated = distractorLines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.contains("#swap") && !line.contains("#group"))
                    .map(line -> line.endsWith("#distractor") ? line : line + " #distractor")
                    .filter(line -> !solutionCode.contains(line.replace(" #distractor", "").trim())) // identity check
                    .toList();

            if (validated.isEmpty()) {
                logger.warn("All distractors filtered out for task: {}", taskStatement);
                return null;
            }

            String result = String.join("\n", validated);
            logger.debug("Generated {} validated distractors for tier {} task", validated.size(), tierLevel);
            return result;

        } catch (Exception e) {
            logger.warn("Distractor generation failed for task '{}', continuing without distractors: {}",
                         taskStatement, e.getMessage());
            return null;
        }
    }

    /**
     * AP31: Generates an inline-split plan for a single (already solved) task via the LLM,
     * then validates it against the solution. Returns the validated split_plan JSON, or
     * {@code null} on any failure / reconstruction mismatch (fail-open).
     */
    public String generateSplitPlanForTask(String solutionCode, String language,
                                           String taskStatement, int tierLevel,
                                           boolean distractorsEnabled) {
        try {
            String raw = client.generateSplitPlan(solutionCode, language, taskStatement, tierLevel);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            SplitPlan plan = MAPPER.readValue(raw, SplitPlan.class);
            if (plan.getSplits() == null || plan.getSplits().isEmpty()) {
                return null;
            }
            // AP31: LLMs often miscount the 0-based lineIndex on longer solutions (esp. Tier 3), which
            // makes an otherwise-perfect split fail the exact-reconstruction check and get dropped. If
            // the fragments reconstruct exactly ONE line, snap the index to that line before validating.
            repairSplitLineIndices(plan, solutionCode);
            if (!SequenceValidator.isValidSplitPlan(plan, solutionCode)) {
                logger.warn("Generated split plan failed reconstruction check (tier {}); dropping.", tierLevel);
                return null;
            }
            // AP31: split distractors are generated in a SECOND, split-aware pass so the wrong
            // fragment is a drop-in for one slot. Independent of the split itself; added only when
            // distractors are enabled AND the tier warrants a trap (Tier 1 stays distractor-free).
            boolean allowSplitDistractor = distractorsEnabled && tierLevel >= 2;
            for (SplitPlan.SplitEntry entry : plan.getSplits()) {
                entry.setDistractors(allowSplitDistractor
                        ? generateSplitDistractorFor(entry, language, taskStatement, tierLevel)
                        : null);
            }
            return MAPPER.writeValueAsString(plan);
        } catch (Exception e) {
            logger.warn("Split generation failed for task '{}', continuing without splits: {}",
                         taskStatement, e.getMessage());
            return null;
        }
    }

    /**
     * AP31: Second pass — asks the model for ONE fragment-level distractor that fits a single slot,
     * given the fragments already chosen for this split. Fail-open: returns {@code null} on any issue.
     */
    private java.util.List<SplitPlan.SplitDistractor> generateSplitDistractorFor(
            SplitPlan.SplitEntry entry, String language, String taskStatement, int tierLevel) {
        try {
            String raw = client.generateSplitDistractor(entry.getFragments(), entry.getPrefix(),
                    entry.getSuffix(), language, taskStatement, tierLevel);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            SplitPlan.SplitDistractor d = MAPPER.readValue(raw, SplitPlan.SplitDistractor.class);
            if (d == null || d.getText() == null || d.getText().isBlank()) {
                return null;
            }
            if (d.getType() == null || d.getType().isBlank()) {
                d.setType("OFF_BY_ONE");
            }
            return java.util.List.of(d);
        } catch (Exception e) {
            logger.warn("Split-distractor generation failed (tier {}): {}", tierLevel, e.getMessage());
            return null;
        }
    }

    /**
     * AP31: LLMs frequently miscount the 0-based {@code lineIndex} on longer solutions (Tier 3),
     * so an otherwise-correct split fails the exact-reconstruction check. When a split's
     * {@code prefix + fragments + suffix} reconstructs exactly ONE solution line, snap its
     * lineIndex to that line. Ambiguous or non-matching splits are left for validation to drop.
     */
    private static void repairSplitLineIndices(SplitPlan plan, String solution) {
        if (plan == null || plan.getSplits() == null) {
            return;
        }
        String[] lines = (solution == null ? "" : solution.replace("\r\n", "\n")).split("\n", -1);
        for (SplitPlan.SplitEntry s : plan.getSplits()) {
            List<String> frags = s.getFragments();
            if (frags == null || frags.isEmpty()) {
                continue;
            }
            String prefix = s.getPrefix() == null ? "" : s.getPrefix();
            String suffix = s.getSuffix() == null ? "" : s.getSuffix();
            String reconstructed = prefix + String.join("", frags) + suffix;
            int idx = s.getLineIndex();
            if (idx >= 0 && idx < lines.length && reconstructed.equals(lines[idx])) {
                continue; // already correct
            }
            int match = -1;
            for (int j = 0; j < lines.length; j++) {
                if (reconstructed.equals(lines[j])) {
                    if (match >= 0) { match = -1; break; } // ambiguous — don't guess
                    match = j;
                }
            }
            if (match >= 0) {
                s.setLineIndex(match);
            }
        }
    }
}
