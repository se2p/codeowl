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

import de.uni_passau.fim.se2.api.dto.ManualTaskRequest;
import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.application.util.GapMarkers;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.ComplexityResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates a TieredTaskSequence from teacher-supplied code — no LLM involved.
 * Comments are NOT stripped: in manual mode the teacher controls the code as-is.
 * Validates the input, computes complexity metrics, and persists the sequence.
 * The sequence is marked with strategyName="ManualCreation" in the DB.
 */
@Service
public class ManualTaskService {

    private static final Logger logger = LoggerFactory.getLogger(ManualTaskService.class);

    @Value("${app.version.id:aa09775e-f584-49cd-a0f2-196464f88fa9}")
    private UUID versionId;

    private final TaskPersistenceService persistenceService;
    private final CodeAnalyzer codeAnalyzer;
    private final ObjectMapper objectMapper;

    public ManualTaskService(TaskPersistenceService persistenceService,
                             CodeAnalyzer codeAnalyzer,
                             ObjectMapper objectMapper) {
        this.persistenceService = persistenceService;
        this.codeAnalyzer = codeAnalyzer;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates, enriches and persists a manually created task sequence.
     *
     * @param request the teacher-supplied request
     * @param ownerId the authenticated user (nullable for anonymous)
     * @return the persisted sequence with IDs
     */
    public TieredTaskSequenceDTO createManualSequence(ManualTaskRequest request, UUID ownerId) {
        logger.info("Creating manual task sequence for language={}, concepts={}", request.getLanguage(), request.getConcepts());

        // ── Validate request ────────────────────────────────────
        validateRequest(request);

        String language = request.getLanguage().trim();
        UUID sequenceId = UUID.randomUUID();

        // ── Build tasks ─────────────────────────────────────────
        List<TaskDTO> tasks = new ArrayList<>();

        for (ManualTaskRequest.ManualTierEntry tier : request.getTiers()) {
            // No comment-sanitizing — in manual mode the teacher decides what stays
            String code = tier.getSolution().trim();

            // AP30: gap markers ($$blank:…$$) live inline in the solution. Strip them to the
            // expected value before code analysis so metrics run on syntactically valid code;
            // the persisted solution keeps the markers for the Parsons renderer.
            String analyzableCode = GapMarkers.strip(code);

            // Compute complexity metrics via CodeAnalyzer (AP26: all 9 languages)
            ComplexityResult metrics = codeAnalyzer.computeComplexity(analyzableCode, language);

            // AP31: validate + serialize inline-split entries (reconstruction check against the raw
            // solution lines — split markers are not part of the solution text).
            String splitPlanJson = buildSplitPlanJson(tier.getSplits(), code, tier.getTierLevel());

            TaskDTO task = TaskDTO.builder()
                    .id(UUID.randomUUID())
                    .statement(tier.getStatement().trim())
                    .solution(code)
                    .tierLevel(tier.getTierLevel())
                    .cyclomaticComplexity(metrics.cyclomaticComplexity())
                    .linesOfCode(metrics.linesOfCode())
                    .maximalNestingDepth(metrics.maxNestingDepth())
                    .methodCallCount(metrics.methodCallCount())
                    .distinctIdentifierCount(metrics.distinctIdentifierCount())
                    .sequenceId(sequenceId)
                    .splitPlan(splitPlanJson)
                    .build();

            tasks.add(task);
        }

        // ── Build sequence ──────────────────────────────────────
        // taskDescription = first tier's statement (summary)
        String taskDescription = request.getTiers().get(0).getStatement().trim();

        // tierCount comes from the tiers actually built, not the @Builder.Default of 3 — a
        // 1- or 2-tier manual sequence (the UI allows fewer than 3) would otherwise persist as
        // "3 tiers" and both mislabel and drop out of the library's tier-count filter, the same
        // failure TieredTaskSequenceDTO.copyFor() documents for the worksheet-copy path.
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder()
                .id(sequenceId)
                .programmingLanguage(language)
                .concepts(request.getConcepts())
                .tasks(tasks)
                // No input snippet in manual mode, but TaskPersistenceService.validateSequence
                // requires a non-blank one: use the first tier's solution as representative.
                .codeSnippet(tasks.get(0).getSolution())
                .taskDescription(taskDescription)
                .createdAt(LocalDateTime.now())
                .versionId(versionId)
                .strategyName("ManualCreation")
                .ownerId(ownerId)
                .tierCount(tasks.size())
                .build();

        // ── Persist ─────────────────────────────────────────────
        persistenceService.persistSequence(sequence);
        logger.info("Manual sequence persisted: id={}", sequenceId);

        return sequence;
    }

    /**
     * AP31: Turns the tier's split entries into a validated {@code split_plan} JSON string.
     * Returns {@code null} when there are no splits. Throws {@link DomainValidationException}
     * if any split fails the reconstruction check.
     */
    private String buildSplitPlanJson(List<SplitPlan.SplitEntry> splits, String solution, int tierLevel) {
        if (splits == null || splits.isEmpty()) {
            return null;
        }
        SplitPlan plan = SplitPlan.builder().splits(splits).build();
        try {
            SequenceValidator.validateSplitPlan(plan, solution);
        } catch (DomainValidationException e) {
            throw new DomainValidationException("Tier " + tierLevel + " split invalid: " + e.getMessage());
        }
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize split_plan for tier {}: {}", tierLevel, e.getMessage());
            return null;
        }
    }

    // ── Validation ──────────────────────────────────────────────

    private void validateRequest(ManualTaskRequest request) {
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new DomainValidationException("Programming language is required.");
        }
        if (request.getConcepts() == null || request.getConcepts().isEmpty()) {
            throw new DomainValidationException("At least one concept is required.");
        }
        if (request.getTiers() == null || request.getTiers().isEmpty()) {
            throw new DomainValidationException("At least one tier is required.");
        }
        if (request.getTiers().size() > 5) {
            throw new DomainValidationException("A maximum of 5 tiers is allowed.");
        }

        for (ManualTaskRequest.ManualTierEntry tier : request.getTiers()) {
            if (tier.getTierLevel() < 1 || tier.getTierLevel() > 5) {
                throw new DomainValidationException("Tier level must be between 1 and 5.");
            }
            if (tier.getStatement() == null || tier.getStatement().isBlank()) {
                throw new DomainValidationException("Tier " + tier.getTierLevel() + ": statement is required.");
            }
            if (tier.getSolution() == null || tier.getSolution().isBlank()) {
                throw new DomainValidationException("Tier " + tier.getTierLevel() + ": code solution is required.");
            }
        }

        // Check for duplicate tier levels
        long distinctTiers = request.getTiers().stream()
                .mapToInt(ManualTaskRequest.ManualTierEntry::getTierLevel)
                .distinct()
                .count();
        if (distinctTiers != request.getTiers().size()) {
            throw new DomainValidationException("Duplicate tier levels are not allowed.");
        }
    }
}
