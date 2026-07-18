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
package de.uni_passau.fim.se2.infrastructure.ai;

import de.uni_passau.fim.se2.application.service.*;
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import com.fasterxml.jackson.databind.*;

import java.io.*;
import java.util.*;

public class AbstractAiClient implements AiModelClient {

    protected final CodeSanitizerService sanitizer;

    protected AbstractAiClient(CodeSanitizerService sanitizer) {
        this.sanitizer = sanitizer;
    }

    protected String sanitize(String code, String language) {
        return sanitizer.removeComments(code, language);

    }

        /**
         * Sends a single problem statement to the provider and returns a code-only solution.
         *
         * <p>The prompt strictly instructs the provider to return only properly formatted
         * code, without any explanation, comments, or extra output.
         *
         * @param contract@return a {@link JsonNode} containing one field "code" with the solution
         * @throws IOException          if API communication fails
         * @throws InterruptedException if the thread is interrupted during the
         *                              request
         */
    @Override
    public JsonNode solveProblem(SolveContract contract) throws IOException, InterruptedException {
        return null;
    }

    /**
     * @param taskDescription
     * @param language
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public JsonNode solveProblemSimple(String taskDescription, String language)
            throws IOException, InterruptedException {
        return null;
    }

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
    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts,
                                               String codeSnippet, String language)
            throws IOException, InterruptedException {
        return null;
    }

    /**
     * Returns the programming language of the {@param codeSnippet}.
     *
     * @param codeSnippet String containing the code snippet to be analyzed.
     * @return String representing the language detected.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public String getProgrammingLanguage(String codeSnippet) throws IOException, InterruptedException {
        return "";
    }

    /**
     * Generates a problem statement fitting to the {@param concepts}.
     *
     * @param concepts List of concepts to be included in the taskDescription.
     * @param language Desired programming language.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public String generateTaskDescription(List<String> concepts, String language)
            throws IOException, InterruptedException {
        return "";
    }

    /**
     * Generates a problem statement fitting to the {@param codeSnippet}.
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @return String containing the problem statement.
     * @throws IOException          throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    @Override
    public String generateTaskDescription(String codeSnippet) throws IOException, InterruptedException {
        return "";
    }

    /**
     * Generates a List of programming concepts contained in
     * {@param codeSnippet}, constrained to the fixed concept catalog (AP25).
     *
     * @param codeSnippet String representing the codeSnippet to be analyzed.
     * @param language    Programming language of the snippet.
     * @return List of normalized concept slugs.
     * @throws IOException          throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public List<String> generateConcepts(String codeSnippet, String language) throws IOException, InterruptedException {
        return List.of();
    }

    @Override
    public List<String> generateDistractors(String solutionCode, String language,
                                             String taskStatement, int tierLevel,
                                             String distractorPlan, int count)
            throws IOException, InterruptedException {
        return List.of();
    }

    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts,
                                               String codeSnippet, String language,
                                               String targetLanguage, boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage);
    }
}