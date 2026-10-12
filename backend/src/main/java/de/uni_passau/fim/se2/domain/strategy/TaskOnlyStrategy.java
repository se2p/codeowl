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
package de.uni_passau.fim.se2.domain.strategy;

import de.uni_passau.fim.se2.application.mapper.TaskSequenceMapper;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * Strategy for generating a task sequence based only on a provided sample task.
 * Concepts are inferred by GPT and then used with the sample task to generate
 * the full sequence.
 */
public class TaskOnlyStrategy implements TaskGenerationStrategy {

    /**
     * String representing the problem statement.
     */
    private final String taskDescription;

    /**
     * String representing the desired programming language.
     */
    private final String language;

    /** Human language for problem statements (e.g. "de", "en"). */
    private final String targetLanguage;

    /**
     * Constructs a new instance of the TaskOnlyStrategy.
     * @param problemStatement String representing the problem statement.
     * @param programmingLanguage String representing the desired language.
     * @param targetLanguage Human language for generated statements.
     */
    public TaskOnlyStrategy(final String problemStatement, final String programmingLanguage,
                            final String targetLanguage) {
        this.taskDescription = problemStatement;
        this.language = programmingLanguage;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Generates a Tiered Task Sequence based on the TaskOnlyStrategy.
     * @param client The ChatGPTClient responsible for API communication.
     * @return a TieredTaskSequence instance.
     * @throws IOException throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client) throws IOException, InterruptedException {
        return generate(client, false);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled) throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, null);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled,
                                          GenerationConstraints constraints) throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, constraints, 3);
    }

    @Override
    public TieredTaskSequenceDTO generate(final OpenAiClient client, boolean distractorsEnabled,
                                          GenerationConstraints constraints, int tierCount) throws IOException, InterruptedException {
        String codeSnippet = client.solveProblemSimple(taskDescription, language).asText();
        List<String> concepts = client.generateConcepts(codeSnippet, language);
        JsonNode json = client.generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints, tierCount);
        return TaskSequenceMapper.mapToIncompleteSequence(json, concepts, language, codeSnippet, taskDescription, tierCount);
    }
}