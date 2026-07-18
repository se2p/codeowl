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

import de.uni_passau.fim.se2.application.mapper.TaskSequenceMapper;
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.interfaces.ConceptCurriculumTopicRepository;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumCodeExampleRepository;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumTopicRepository;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;
import de.uni_passau.fim.se2.domain.exceptions.GenerationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class CurriculumGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(CurriculumGenerationService.class);

    private final CurriculumTopicRepository topicRepo;
    private final CurriculumCodeExampleRepository exampleRepo;
    private final ConceptCurriculumTopicRepository conceptTopicRepo;
    private final OpenAiClient aiClient;
    private final TaskEnricher taskEnricher;

    @Value("${app.version.id:aa09775e-f584-49cd-a0f2-196464f88fa9}")
    private UUID versionId;

    @Value("${app.generation.maxAttempts:3}")
    private int maxAttempts;

    public CurriculumGenerationService(CurriculumTopicRepository topicRepo,
                                       CurriculumCodeExampleRepository exampleRepo,
                                       ConceptCurriculumTopicRepository conceptTopicRepo,
                                       OpenAiClient aiClient,
                                       TaskEnricher taskEnricher) {
        this.topicRepo = topicRepo;
        this.exampleRepo = exampleRepo;
        this.conceptTopicRepo = conceptTopicRepo;
        this.aiClient = aiClient;
        this.taskEnricher = taskEnricher;
    }

    /**
     * Generates a tiered task sequence based on a curriculum topic.
     * Selects seed code examples and concept mappings from the DB, then
     * delegates to the OpenAiClient with the dedicated curriculum prompt.
     */
    public TieredTaskSequenceDTO generate(UUID topicId, String language, String targetLanguage,
                                          int tierCount, boolean distractorsEnabled,
                                          boolean contextModeEnabled, boolean splitEnabled,
                                          GenerationConstraints constraints, UUID ownerId)
            throws IOException, InterruptedException {

        CurriculumTopicDTO topic = topicRepo.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum topic not found: " + topicId));

        // Get seed code examples (prefer matching language, fallback to all)
        List<CurriculumCodeExampleDTO> examples = exampleRepo.findByTopicIdAndLanguage(topicId, language);
        if (examples.isEmpty()) {
            examples = exampleRepo.findByTopicId(topicId);
        }
        if (examples.isEmpty()) {
            // AP32: a topic without curated seed examples is treated as "not yet available".
            // The controller maps this to a 422 { error: "no_examples" } which the frontend
            // renders via the i18n key `curriculum.noExamples`. The literal below is the
            // German source string / fallback.
            logger.warn("No curated seed examples for topic '{}' — curriculum generation unavailable.",
                    topic.getName());
            throw new IllegalStateException(
                    "Das wurde noch nicht eingepflegt und ist deshalb noch nicht verfügbar");
        }
        // Limit to 3 seed examples
        if (examples.size() > 3) {
            examples = examples.subList(0, 3);
        }

        // Get concepts mapped to this topic
        List<ConceptDTO> conceptDTOs = conceptTopicRepo.findAllByCurriculumTopic(topic);
        List<String> concepts = conceptDTOs.stream()
                .map(ConceptDTO::getName)
                .collect(Collectors.toList());
        if (concepts.isEmpty()) {
            concepts = List.of("general-programming");
        }

        logger.info("Curriculum generation: topic='{}', grade={}, language={}, tierCount={}, examples={}, concepts={}",
                topic.getName(), topic.getGradeLevel(), language, tierCount, examples.size(), concepts);

        // Retry-loop analog to TaskGeneratorService — robust against transient LLM failures
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tryGenerateOnce(topic, topicId, examples, concepts, language, targetLanguage,
                                       tierCount, distractorsEnabled, contextModeEnabled, splitEnabled,
                                       constraints, ownerId);
            } catch (IOException | RuntimeException e) {
                lastError = e;
                logger.warn("Curriculum generation attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    // Exponential backoff with jitter
                    long delay = (long) Math.min(30_000L,
                            Math.pow(2, attempt) * 1000L * (0.5 + ThreadLocalRandom.current().nextDouble(0.5)));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        throw new GenerationException("Curriculum generation failed after " + maxAttempts + " attempts", lastError);
    }

    private TieredTaskSequenceDTO tryGenerateOnce(CurriculumTopicDTO topic, UUID topicId,
                                                  List<CurriculumCodeExampleDTO> examples,
                                                  List<String> concepts, String language,
                                                  String targetLanguage, int tierCount,
                                                  boolean distractorsEnabled,
                                                  boolean contextModeEnabled, boolean splitEnabled,
                                                  GenerationConstraints constraints, UUID ownerId)
            throws IOException, InterruptedException {
        // Generate via OpenAI with curriculum-specific prompt
        JsonNode json = aiClient.generateCurriculumSequence(
                topic, examples, concepts, language, targetLanguage,
                tierCount, distractorsEnabled, constraints);

        // Map to DTO
        TieredTaskSequenceDTO sequence = TaskSequenceMapper.mapToIncompleteSequence(
                json, concepts, language, null, topic.getName(), tierCount);

        // Set metadata
        sequence.setOwnerId(ownerId);
        sequence.setVersionId(versionId);
        sequence.setConstraints(constraints);
        sequence.setCurriculumTopicId(topicId);

        // Enrich tasks (complexity analysis, distractors, AP22 context, AP31 splits)
        TieredTaskSequenceDTO enriched = taskEnricher.enrichWithSolutions(
                sequence, distractorsEnabled, contextModeEnabled, splitEnabled);
        // Preserve metadata that enrichWithSolutions does not copy
        enriched.setOwnerId(ownerId);
        enriched.setVersionId(versionId);
        enriched.setConstraints(constraints);
        enriched.setCurriculumTopicId(topicId);
        enriched.setTargetLanguage(targetLanguage);
        enriched.setTierCount(tierCount);
        // AP19/AP32: a curriculum sequence is anchored on curated seed examples rather than
        // a single input snippet, so the mapper leaves codeSnippet null. Persist the first
        // non-blank seed example's code as the sequence's representative snippet, otherwise
        // TaskPersistenceService.validateSequence rejects it ("code snippet must not be empty").
        String seedSnippet = examples.stream()
                .map(CurriculumCodeExampleDTO::getCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(examples.get(0).getCode());
        enriched.setCodeSnippet(seedSnippet);
        return enriched;
    }

    /** Returns all top-level curriculum topics (Jahrgangsstufen). */
    public List<CurriculumTopicDTO> getTopLevelTopics() {
        return topicRepo.findTopLevel();
    }

    /** Returns sub-topics for a given parent topic. */
    public List<CurriculumTopicDTO> getSubTopics(UUID parentId) {
        return topicRepo.findByParentId(parentId);
    }

    /** Returns code examples for a given topic and language. */
    public List<CurriculumCodeExampleDTO> getExamples(UUID topicId, String language) {
        if (language != null && !language.isBlank()) {
            return exampleRepo.findByTopicIdAndLanguage(topicId, language);
        }
        return exampleRepo.findByTopicId(topicId);
    }
}
