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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.*;
import com.fasterxml.jackson.databind.*;

import java.io.*;
import java.util.*;

public interface AiModelClient {

    /**
     * Sends a single problem statement to the provider and returns a code-only solution.
     *
     * <p>The prompt strictly instructs the provider to return only properly formatted
     * code, without any explanation, comments, or extra output.
     *
     * @param problem  the problem description to solve
     * @param language TODO
     * @return a {@link JsonNode} containing one field "code" with the solution
     * @throws IOException          if API communication fails
     * @throws InterruptedException if the thread is interrupted during the
     *                              request
     */
    JsonNode solveProblem(final SolveContract contract) throws IOException, InterruptedException;

    JsonNode solveProblemSimple(final String taskDescription, final String language) throws IOException, InterruptedException;

    /**
     * Generates a TieredTaskSequence.
     *
     * @param taskDescription The problem statement of the sample task.
     * @param concepts        Concepts found in the sample task.
     * @param codeSnippet     Code snippet serving as a solution to the sample task.
     * @param language        The programming language of the code snippet.
     * @return JsonNode representing the TieredTaskSequence.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    JsonNode generateTieredTaskSequence(final String taskDescription,
                                        final List<String> concepts,
                                        final String codeSnippet,
                                        final String language) throws IOException, InterruptedException;

    /**
     * Generates a TieredTaskSequence with a specified target language for problem statements.
     *
     * @param targetLanguage Human language code for statements (e.g. "de", "en").
     */
    default JsonNode generateTieredTaskSequence(final String taskDescription,
                                                final List<String> concepts,
                                                final String codeSnippet,
                                                final String language,
                                                final String targetLanguage) throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language);
    }

    /**
     * Generates a problem statement for a given code snippet using a provided prompt body.
     * Default implementation falls back to the single-argument variant,
     * so existing clients keep working until they implement the override.
     */
    default String generateTaskDescription(final String codeSnippet, final String userPromptBody)
            throws IOException, InterruptedException {
        return generateTaskDescription(codeSnippet);
    }

    /**
     * Generate using explicit system prompt + user prompt/body.
     * Providers that support roles should use both; others may concatenate.
     */
    default String generateTaskDescription(final String codeSnippet,
                                           final String systemPrompt,
                                           final String userPromptBody)
            throws IOException, InterruptedException {
        // Fallback: concatenate if provider lacks role support
        String merged = (systemPrompt == null || systemPrompt.isBlank())
                ? userPromptBody
                : ("SYSTEM:\n" + systemPrompt + "\n\nUSER:\n" + userPromptBody);
        return generateTaskDescription(codeSnippet, merged);
    }

    /**
     * Returns the programming language of the {@param codeSnippet}.
     *
     * @param codeSnippet String containing the code snippet to be analyzed.
     * @return String representing the language detected.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    String getProgrammingLanguage(final String codeSnippet)
            throws IOException, InterruptedException;

    /**
     * Generates a problem statement fitting to the {@param concepts}.
     *
     * @param concepts List of concepts to be included in the taskDescription.
     * @param language Desired programming language.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    String generateTaskDescription(final List<String> concepts,
                                   final String language)
            throws IOException, InterruptedException;

    /**
     * Generates a problem statement fitting to the {@param codeSnippet}.
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    String generateTaskDescription(final String codeSnippet)
            throws IOException, InterruptedException;

    /**
     * Generates a List of programming concepts contained in
     * {@param codeSnippet}, constrained to the fixed concept catalog (AP25).
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @param language    Programming language of the snippet (e.g. "java", "python").
     * @return List of normalized concept slugs from the catalog.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    List<String> generateConcepts(final String codeSnippet, final String language)
            throws IOException, InterruptedException;

    /**
     * Generates distractor lines for a given solution code (AP15 Antagonist-Call).
     *
     * @param solutionCode   the correct solution
     * @param language        programming language
     * @param taskStatement   the task description
     * @param tierLevel       difficulty tier (1-3)
     * @param distractorPlan  JSON string with type and rationale, or null
     * @param count           number of distractors to generate
     * @return list of distractor line strings (each ending with #distractor)
     */
    default List<String> generateDistractors(String solutionCode, String language,
                                              String taskStatement, int tierLevel,
                                              String distractorPlan, int count)
            throws IOException, InterruptedException {
        return List.of();
    }

    /**
     * AP31: Generates an inline-split plan for a (solved) task — one complex line decomposed
     * into 2–4 fragments with optional display prefix/suffix and fragment distractors.
     *
     * @return split_plan JSON string (object with a "splits" array), or {@code null}.
     */
    default String generateSplitPlan(String solutionCode, String language,
                                     String taskStatement, int tierLevel)
            throws IOException, InterruptedException {
        return null;
    }

    /**
     * AP31: Second pass — generates ONE fragment-level distractor for an already-decided split so the
     * wrong fragment is a drop-in for a single slot (shape-matched to one of {@code fragments}).
     *
     * @return JSON {@code {"fragmentIndex":int,"text":"...","type":"..."}}, or {@code null} if none fits.
     */
    default String generateSplitDistractor(List<String> fragments, String prefix, String suffix,
                                           String language, String taskStatement, int tierLevel)
            throws IOException, InterruptedException {
        return null;
    }

    /**
     * Generates a TieredTaskSequence with optional distractor planning (AP15).
     */
    default JsonNode generateTieredTaskSequence(final String taskDescription,
                                                final List<String> concepts,
                                                final String codeSnippet,
                                                final String language,
                                                final String targetLanguage,
                                                boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage);
    }

    /**
     * AP20: Generates a TieredTaskSequence with constraints + distractors.
     */
    default JsonNode generateTieredTaskSequence(final String taskDescription,
                                                final List<String> concepts,
                                                final String codeSnippet,
                                                final String language,
                                                final String targetLanguage,
                                                boolean distractorsEnabled,
                                                GenerationConstraints constraints)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage, distractorsEnabled);
    }

    /**
     * AP21: Generates a TieredTaskSequence with constraints + distractors + tierCount.
     */
    default JsonNode generateTieredTaskSequence(final String taskDescription,
                                                final List<String> concepts,
                                                final String codeSnippet,
                                                final String language,
                                                final String targetLanguage,
                                                boolean distractorsEnabled,
                                                GenerationConstraints constraints,
                                                int tierCount)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage, distractorsEnabled, constraints);
    }
}