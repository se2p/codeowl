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
package de.uni_passau.fim.se2.application.service.analysis;

import de.uni_passau.fim.se2.application.util.GapMarkers;
import de.uni_passau.fim.se2.domain.entities.TaskChangeAnalysisDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.interfaces.TaskChangeAnalysisRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AP12 — Change-Analysis-Engine: Orchestration service.
 * Coordinates syntactic, AST-level, and scoring analyses for edited tasks,
 * persisting the results into the task_change_analyses table.
 */
@Service
public class ChangeAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ChangeAnalysisService.class);

    private final SyntacticAnalyzer syntacticAnalyzer;
    private final CodeAnalyzer codeAnalyzer;
    private final ChangeScoreCalculator scoreCalculator;
    private final TaskOriginalRepository taskOriginalRepo;
    private final TaskRepository taskRepo;
    private final TaskChangeAnalysisRepository analysisRepo;
    private final ObjectMapper objectMapper;

    public ChangeAnalysisService(SyntacticAnalyzer syntacticAnalyzer,
                                 CodeAnalyzer codeAnalyzer,
                                 ChangeScoreCalculator scoreCalculator,
                                 TaskOriginalRepository taskOriginalRepo,
                                 TaskRepository taskRepo,
                                 TaskChangeAnalysisRepository analysisRepo,
                                 ObjectMapper objectMapper) {
        this.syntacticAnalyzer = syntacticAnalyzer;
        this.codeAnalyzer = codeAnalyzer;
        this.scoreCalculator = scoreCalculator;
        this.taskOriginalRepo = taskOriginalRepo;
        this.taskRepo = taskRepo;
        this.analysisRepo = analysisRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs full change analysis for a single task by comparing its current state
     * against the stored original. Persists results and returns the DTO.
     *
     * @param taskId the ID of the task to analyze
     * @return the populated TaskChangeAnalysisDTO
     * @throws IllegalArgumentException if the task or its original cannot be found
     */
    @Transactional
    public TaskChangeAnalysisDTO analyzeTask(UUID taskId) {
        logger.info("Starting change analysis for task {}", taskId);

        // 1. Load original
        TaskOriginalDTO original = taskOriginalRepo.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No original found for task " + taskId + ". Edit must occur before analysis."));

        // 2. Load current task
        TaskDTO current = new TaskDTO();
        current.setId(taskId);
        taskRepo.loadById(current);

        String origText = safe(original.getOriginalProblemStatement());
        String currText = safe(current.getStatement());

        // Presentation-only overlays must NOT register as a code change:
        //   • Gaps ($$blank:…$$) live inline in the solution — strip them to their expected
        //     value on both sides so wrapping existing tokens as fill-in gaps scores NONE.
        //   • Context blocks (context_before/after) and inline-split plans (split_plan) live
        //     in dedicated DB columns and are never part of the solution string compared here,
        //     so they already don't count — no normalisation needed for them.
        // #group/#swap interchangeable markers are intentionally left in: changing the accepted
        // ordering/grouping of a puzzle IS a real change and must still be counted.
        String origCode = GapMarkers.strip(safe(original.getOriginalCode()));
        String currCode = GapMarkers.strip(safe(current.getSolution()));

        // 3. Text-level syntactic analysis
        double textLevenshtein = syntacticAnalyzer.levenshteinRatio(origText, currText);
        double textJaccard = syntacticAnalyzer.jaccardSimilarity(origText, currText);
        SyntacticAnalyzer.WordDiffResult wordDiff = syntacticAnalyzer.wordDiff(origText, currText);

        // 4. Code-level syntactic analysis
        double codeLevenshtein = syntacticAnalyzer.levenshteinRatio(origCode, currCode);
        SyntacticAnalyzer.LineDiffResult lineDiff = syntacticAnalyzer.lineDiff(origCode, currCode);
        double tokenChangeRatio = syntacticAnalyzer.tokenChangeRatio(origCode, currCode);

        // 5. AST-level analysis (AP26: now supports all 9 languages via tree-sitter)
        String lang = current.getProgrammingLanguage();
        CodeAnalyzer.AstAnalysisResult astResult = codeAnalyzer.analyzeChanges(origCode, currCode, lang);

        // 6. Composite score
        double overallScore = scoreCalculator.computeOverallScore(
                textLevenshtein, codeLevenshtein, astResult.structuralScore());

        // 7. Categorize
        String category = scoreCalculator.categorize(
                overallScore, codeLevenshtein, astResult.changeTypes());

        // 8. Complexity delta
        int complexityDelta = current.getCyclomaticComplexity();
        // If original had complexity info we would subtract it; for now we store current value

        // 9. Build DTO
        TaskChangeAnalysisDTO analysis = TaskChangeAnalysisDTO.builder()
                .taskId(taskId)
                .taskOriginalId(original.getId())
                .analyzedAt(LocalDateTime.now())
                // text metrics
                .textLevenshteinRatio(textLevenshtein)
                .textJaccardSimilarity(textJaccard)
                .textWordsAdded(wordDiff.wordsAdded())
                .textWordsRemoved(wordDiff.wordsRemoved())
                .textWordsModified(wordDiff.wordsModified())
                .textLengthDelta(wordDiff.lengthDelta())
                // code metrics
                .codeLevenshteinRatio(codeLevenshtein)
                .codeLinesAdded(lineDiff.linesAdded())
                .codeLinesRemoved(lineDiff.linesRemoved())
                .codeLinesModified(lineDiff.linesModified())
                .codeLinesReordered(lineDiff.linesReordered())
                .codeLocDelta(lineDiff.locDelta())
                .codeComplexityDelta(complexityDelta)
                .codeTokenChangeRatio(tokenChangeRatio)
                // AST metrics
                .astNodesAdded(astResult.nodesAdded())
                .astNodesRemoved(astResult.nodesRemoved())
                .astNodesModified(astResult.nodesModified())
                .astStructuralScore(astResult.structuralScore())
                .astChangeTypes(toJson(astResult.changeTypes()))
                .astDistractorChanges(toJson(astResult.distractorChanges()))
                // overall
                .overallChangeScore(overallScore)
                .changeCategory(category)
                .teacherRating(original.getAdjustmentRating())
                .build();

        // 10. Persist
        analysisRepo.save(analysis);
        logger.info("Change analysis complete for task {}: category={}, score={}",
                taskId, category, round(overallScore));

        return analysis;
    }

    /**
     * Analyzes all tasks that have originals but no existing analysis record.
     *
     * @return list of newly created analysis DTOs
     */
    @Transactional
    public List<TaskChangeAnalysisDTO> batchAnalyze() {
        logger.info("Starting batch change analysis");
        List<TaskChangeAnalysisDTO> results = new ArrayList<>();

        Set<UUID> alreadyAnalyzed = analysisRepo.findAll().stream()
                .map(TaskChangeAnalysisDTO::getTaskId)
                .collect(Collectors.toSet());

        List<UUID> toAnalyze = taskOriginalRepo.findAll().stream()
                .map(o -> o.getTaskId())
                .filter(id -> !alreadyAnalyzed.contains(id))
                .toList();

        logger.info("Batch analysis: {} already done, {} to analyze", alreadyAnalyzed.size(), toAnalyze.size());

        for (UUID taskId : toAnalyze) {
            try {
                results.add(analyzeTask(taskId));
            } catch (Exception e) {
                logger.warn("Batch analysis failed for task {}: {}", taskId, e.getMessage());
            }
        }

        return results;
    }

    /**
     * Returns aggregate summary statistics across all analyzed tasks.
     *
     * @return map of summary keys to values
     */
    public Map<String, Object> getSummary() {
        List<TaskChangeAnalysisDTO> all = analysisRepo.findAll();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalAnalyzed", all.size());

        if (all.isEmpty()) {
            summary.put("avgOverallScore", 0.0);
            summary.put("categoryBreakdown", Collections.emptyMap());
            return summary;
        }

        double avgScore = all.stream()
                .filter(a -> a.getOverallChangeScore() != null)
                .mapToDouble(TaskChangeAnalysisDTO::getOverallChangeScore)
                .average()
                .orElse(0.0);

        Map<String, Long> categoryBreakdown = all.stream()
                .filter(a -> a.getChangeCategory() != null)
                .collect(Collectors.groupingBy(
                        TaskChangeAnalysisDTO::getChangeCategory,
                        Collectors.counting()));

        double avgTextLev = all.stream()
                .filter(a -> a.getTextLevenshteinRatio() != null)
                .mapToDouble(TaskChangeAnalysisDTO::getTextLevenshteinRatio)
                .average()
                .orElse(0.0);

        double avgCodeLev = all.stream()
                .filter(a -> a.getCodeLevenshteinRatio() != null)
                .mapToDouble(TaskChangeAnalysisDTO::getCodeLevenshteinRatio)
                .average()
                .orElse(0.0);

        double avgAstScore = all.stream()
                .filter(a -> a.getAstStructuralScore() != null)
                .mapToDouble(TaskChangeAnalysisDTO::getAstStructuralScore)
                .average()
                .orElse(0.0);

        summary.put("avgOverallScore", round(avgScore));
        summary.put("avgTextLevenshtein", round(avgTextLev));
        summary.put("avgCodeLevenshtein", round(avgCodeLev));
        summary.put("avgAstStructuralScore", round(avgAstScore));
        summary.put("categoryBreakdown", categoryBreakdown);

        return summary;
    }

    /**
     * Returns the analysis for a specific task, if one exists.
     */
    public Optional<TaskChangeAnalysisDTO> getByTaskId(UUID taskId) {
        return analysisRepo.findByTaskId(taskId);
    }

    /**
     * Returns all analyses (for export).
     */
    public List<TaskChangeAnalysisDTO> findAll() {
        return analysisRepo.findAll();
    }

    // ── Helpers ──

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
