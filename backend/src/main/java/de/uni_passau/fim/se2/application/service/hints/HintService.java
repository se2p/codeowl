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
package de.uni_passau.fim.se2.application.service.hints;

import de.uni_passau.fim.se2.domain.entities.HintUsageDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.model.SupportedLanguage;
import de.uni_passau.fim.se2.domain.interfaces.HintUsageRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.WorksheetAccessService;
import de.uni_passau.fim.se2.application.util.GapMarkers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Arrays;

@Service
public class HintService {

    private static final Logger logger = LoggerFactory.getLogger(HintService.class);
    private final CodeAssembler codeAssembler;
    private final RuffAnalyzer ruffAnalyzer;
    private final JavacAnalyzer javacAnalyzer;
    private final SpotBugsAnalyzer spotBugsAnalyzer;
    private final BlockMapper blockMapper;
    private final HintGenerator hintGenerator;
    private final HintExplainer hintExplainer;
    private final SarifParser sarifParser;
    private final TaskRepository taskRepository;
    private final HintUsageRepository hintUsageRepository;
    private final OwnershipService ownershipService;
    private final WorksheetAccessService worksheetAccessService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HintService(CodeAssembler codeAssembler, RuffAnalyzer ruffAnalyzer,
                       JavacAnalyzer javacAnalyzer, SpotBugsAnalyzer spotBugsAnalyzer,
                       BlockMapper blockMapper, HintGenerator hintGenerator,
                       HintExplainer hintExplainer, SarifParser sarifParser,
                       TaskRepository taskRepository, HintUsageRepository hintUsageRepository,
                       OwnershipService ownershipService,
                       WorksheetAccessService worksheetAccessService, JdbcTemplate jdbc) {
        this.codeAssembler = codeAssembler;
        this.ruffAnalyzer = ruffAnalyzer;
        this.javacAnalyzer = javacAnalyzer;
        this.spotBugsAnalyzer = spotBugsAnalyzer;
        this.blockMapper = blockMapper;
        this.hintGenerator = hintGenerator;
        this.hintExplainer = hintExplainer;
        this.sarifParser = sarifParser;
        this.taskRepository = taskRepository;
        this.hintUsageRepository = hintUsageRepository;
        this.ownershipService = ownershipService;
        this.worksheetAccessService = worksheetAccessService;
        this.jdbc = jdbc;
    }

    /**
     * Resolves hint-configuration for a task by walking task → sequence → worksheet.
     * Returns true when no worksheet is linked (default) or when the worksheet has hints enabled.
     */
    public boolean isHintsEnabled(UUID taskId) {
        String sql = """
            SELECT COALESCE(w.hints_enabled, true) AS hints_enabled
            FROM tasks t
            JOIN worksheets_sequence ws ON ws.sequence_id = t.sequence_id
            JOIN worksheets w ON w.id = ws.worksheet_id
            WHERE t.id = ?
            LIMIT 1
            """;
        try {
            Boolean enabled = jdbc.queryForObject(sql, Boolean.class, taskId);
            return enabled == null || enabled;
        } catch (Exception e) {
            // Standalone sequence (no worksheet) → hints enabled by default
            return true;
        }
    }

    /**
     * What one hint costs. Level 2 asks the LLM for an explanation on top of the
     * deterministic finding, so it is charged double.
     */
    public static int creditCost(int hintLevel) {
        return hintLevel >= 2 ? 2 : 1;
    }

    /**
     * The credit budget configured for a task's worksheet, or {@code null} for
     * unlimited — which is also the answer for a task that is not in any
     * worksheet.
     */
    public Integer resolveCreditsPerTask(UUID taskId) {
        String sql = """
            SELECT w.hint_credits_per_task
            FROM tasks t
            JOIN worksheets_sequence ws ON ws.sequence_id = t.sequence_id
            JOIN worksheets w ON w.id = ws.worksheet_id
            WHERE t.id = ?
            LIMIT 1
            """;
        try {
            return jdbc.queryForObject(sql, Integer.class, taskId);
        } catch (Exception e) {
            // Standalone sequence (no worksheet) → unlimited
            return null;
        }
    }

    /**
     * Credits this student has already spent on this task.
     *
     * Derived from hint_usage rather than a counter column, so resetting the
     * puzzle does not refund anything: the usage rows outlive the reset.
     * Anonymous requests cannot be attributed to anyone and always report 0.
     */
    public int creditsSpent(UUID taskId, UUID userId) {
        if (userId == null) return 0;
        String sql = """
            SELECT COALESCE(SUM(CASE WHEN hint_level >= 2 THEN 2 ELSE 1 END), 0)
            FROM hint_usage
            WHERE user_id = ? AND task_id = ?
            """;
        Integer spent = jdbc.queryForObject(sql, Integer.class, userId, taskId);
        return spent == null ? 0 : spent;
    }

    /**
     * Everything the client needs to render the hint buttons: whether the
     * teacher enabled hints at all, the budget, and what is left of it.
     */
    public HintStatus getStatus(UUID taskId, UUID userId) {
        boolean enabled = isHintsEnabled(taskId);
        Integer perTask = resolveCreditsPerTask(taskId);
        int spent = creditsSpent(taskId, userId);
        Integer remaining = perTask == null ? null : Math.max(0, perTask - spent);
        return new HintStatus(enabled, perTask, spent, remaining);
    }

    @Transactional
    public HintResponse getHint(UUID taskId, List<String> blockOrder, List<Integer> indentLevels, int hintLevel, UUID userId) {
        return getHint(taskId, blockOrder, indentLevels, hintLevel, userId, HintLocale.DE);
    }

    /**
     * @param locale the UI language the hint text should be written in; hints
     *               used to be German-only, so a client that sends nothing keeps
     *               German.
     */
    @Transactional
    public HintResponse getHint(UUID taskId, List<String> blockOrder, List<Integer> indentLevels, int hintLevel,
                                UUID userId, HintLocale locale) {
        // Guard against null inputs from the client — treat as empty lists rather than crashing.
        if (blockOrder == null) blockOrder = List.of();
        if (indentLevels == null) indentLevels = List.of();

        // 0. Authorisation, before any analysis runs.
        //
        // Both checks live here rather than in the controller so there is a single
        // place to bypass. Hiding the buttons in the UI is a convenience; this is
        // what actually enforces the teacher's setting against a replayed request.
        if (!isHintsEnabled(taskId)) {
            throw new AccessDeniedException("Hints are disabled for this worksheet.");
        }

        // A closed worksheet may be read but not worked on, and asking for a hint
        // is part of working on it.
        worksheetAccessService.requireSolvable(taskId);

        Integer creditsPerTask = resolveCreditsPerTask(taskId);
        if (creditsPerTask != null && userId != null) {
            int spent = creditsSpent(taskId, userId);
            int cost = creditCost(hintLevel);
            if (spent + cost > creditsPerTask) {
                throw new AccessDeniedException(
                        "No hint credits left for this task (" + spent + "/" + creditsPerTask + " used).");
            }
        }

        // 1. Load task to determine language + distractors
        TaskDTO task = new TaskDTO();
        task.setId(taskId);
        taskRepository.loadById(task);
        String language = task.getProgrammingLanguage() != null ? task.getProgrammingLanguage().toLowerCase() : "python";

        // AP15: Identify which blocks in the solution space are distractors
        Set<String> distractorLines = parseDistractorLines(task.getDistractors());
        List<Integer> distractorBlockIndices = new ArrayList<>();
        for (int i = 0; i < blockOrder.size(); i++) {
            String blockText = blockOrder.get(i).trim();
            if (distractorLines.contains(blockText)) {
                distractorBlockIndices.add(i);
            }
        }
        boolean hasDistractorsInSolution = !distractorBlockIndices.isEmpty();
        if (hasDistractorsInSolution) {
            logger.info("AP15: Distractors detected in solution space at block indices: {}", distractorBlockIndices);
        }

        // 2. Assemble code from block order with indentation (excluding distractors for clean analysis)
        CodeAssembler.CodeAssembly assembly = codeAssembler.assemble(blockOrder, indentLevels);
        logger.info("Hint request for task={}, language={}, hintLevel={}", taskId, language, hintLevel);
        logger.info("Block order: {}", blockOrder);
        logger.info("Indent levels: {}", indentLevels);
        logger.info("Assembled code:\n{}", assembly.code());

        // 3. Run static analysis based on language
        List<AnalysisFinding> findings = runAnalysis(assembly.code(), language);
        logger.info("Analysis findings ({}): {}", findings.size(), findings);

        // 4. Map findings to blocks (max 2 most important)
        List<BlockMapper.BlockFinding> blockFindings = blockMapper.map(findings, assembly.lineToBlockIndex());
        if (blockFindings.size() > 2) {
            blockFindings = blockFindings.subList(0, 2);
        }

        // 5. Detect whether student has placed enough blocks in the solution area.
        //    The correct block count is approximated from non-empty lines in the solution.
        //    The student's effective count excludes distractors they accidentally placed.
        int correctBlockCount = countSolutionBlocks(GapMarkers.strip(task.getSolution())); // AP30: ignore gap markers
        int studentNonDistractorCount = blockOrder.size() - distractorBlockIndices.size();
        boolean tooFewBlocks = correctBlockCount > 0 && studentNonDistractorCount < correctBlockCount;
        if (tooFewBlocks) {
            logger.info("Block count mismatch: student placed {} non-distractor blocks, correct solution has {}",
                    studentNonDistractorCount, correctBlockCount);
        }

        // 6. Generate hint/explanation
        String hintText = null;
        String explanation = null;

        if (hintLevel == 1) {
            hintText = hintGenerator.generate(blockFindings, language, distractorBlockIndices, tooFewBlocks, studentNonDistractorCount, correctBlockCount, locale);
        } else if (hintLevel == 2) {
            hintText = hintGenerator.generate(blockFindings, language, distractorBlockIndices, tooFewBlocks, studentNonDistractorCount, correctBlockCount, locale);
            explanation = hintExplainer.explain(blockFindings, assembly.code(), GapMarkers.strip(task.getSolution()), language,
                    distractorBlockIndices, tooFewBlocks, studentNonDistractorCount, correctBlockCount, locale);
        }

        // 7. Get affected block indices (include distractor blocks as affected)
        List<Integer> affectedBlocks = new ArrayList<>(blockFindings.stream()
                .map(BlockMapper.BlockFinding::blockIndex)
                .toList());
        // Add distractor blocks to affected list if they aren't already there
        for (Integer idx : distractorBlockIndices) {
            if (!affectedBlocks.contains(idx)) {
                affectedBlocks.add(idx);
            }
        }

        // 8. Log usage
        saveHintUsage(taskId, userId, hintLevel, blockOrder, findings, hintText, explanation, affectedBlocks);

        return new HintResponse(hintText, affectedBlocks, explanation, findings);
    }

    /**
     * Counts the number of non-empty lines in the correct solution.
     * Used to detect when the student has placed too few blocks in the solution area.
     * This is an approximation — accurate enough to flag "you're missing blocks" without
     * needing the actual block list (which is assembled at the frontend, not stored in DB).
     */
    private int countSolutionBlocks(String solution) {
        if (solution == null || solution.isBlank()) return 0;
        return (int) Arrays.stream(solution.split("\n"))
                .filter(line -> !line.isBlank())
                .count();
    }

    /**
     * AP15: Parses the distractor lines from the DB field into a set of trimmed code strings
     * (without the #distractor marker) for comparison against block text.
     */
    private Set<String> parseDistractorLines(String distractors) {
        if (distractors == null || distractors.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String line : distractors.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // Remove #distractor suffix to match against block text (widget strips it)
            String clean = trimmed.replaceAll("\\s*#distractor\\s*$", "").trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }

    private List<AnalysisFinding> runAnalysis(String code, String language) {
        if (SupportedLanguage.PYTHON.matches(language)) {
            return ruffAnalyzer.analyze(code);
        } else if (SupportedLanguage.JAVA.matches(language)) {
            JavacAnalyzer.JavacResult javacResult = javacAnalyzer.analyze(code);
            List<AnalysisFinding> findings = new ArrayList<>(javacResult.findings());

            if (javacResult.compiled() && javacResult.classOutputDir() != null) {
                try {
                    findings.addAll(spotBugsAnalyzer.analyze(javacResult.classOutputDir()));
                } finally {
                    javacAnalyzer.cleanupTempDir(javacResult.classOutputDir().getParent());
                }
            } else if (javacResult.classOutputDir() != null) {
                javacAnalyzer.cleanupTempDir(javacResult.classOutputDir().getParent());
            }

            return findings;
        }
        // Not python and not java: there is no analyzer for this, so the hint is built from
        // no findings at all. That is a silent quality loss for the student, and the cause is
        // always bad data rather than an unsupported language -- the column is free text
        // filled from the language-detection LLM, and a typo ("Pyhton") lands here.
        logger.warn("No static analysis for programming language '{}' -- hint will be generated "
                + "without findings. Check tiered_task_sequences.programming_language.", language);
        return List.of();
    }

    private void saveHintUsage(UUID taskId, UUID userId, int hintLevel,
                               List<String> blockOrder, List<AnalysisFinding> findings,
                               String hintText, String explanation, List<Integer> affectedBlocks) {
        try {
            HintUsageDTO usage = HintUsageDTO.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .hintLevel(hintLevel)
                    .blockOrderAtRequest(objectMapper.writeValueAsString(blockOrder))
                    .findings(objectMapper.writeValueAsString(findings))
                    .hintText(hintText)
                    .llmExplanation(explanation)
                    .affectedBlockIds(objectMapper.writeValueAsString(affectedBlocks))
                    .build();
            hintUsageRepository.save(usage);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize hint usage data", e);
        }
    }

    /** Hint availability for one student on one task. */
    public record HintStatus(
        boolean hintsEnabled,
        Integer creditsPerTask,  // null = unlimited
        int creditsUsed,
        Integer creditsRemaining // null = unlimited
    ) {}

    // Response record
    public record HintResponse(
        String hintText,
        List<Integer> affectedBlocks,
        String explanation,
        List<AnalysisFinding> findings
    ) {}
}
