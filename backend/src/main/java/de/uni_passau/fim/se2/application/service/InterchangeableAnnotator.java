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

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AP16: Pass 2 — Analyzes sanitized code and adds #group:NAME_N and #swap:NAME
 * markers to identify interchangeable lines/blocks for Parsons puzzle grading.
 *
 * Called after CodeSanitizerService and CodeAnalyzer during task enrichment.
 * Uses an LLM call with a few-shot prompt to identify interchangeable lines.
 * On failure or empty result, returns the original solution unchanged (safe fallback).
 */
@Service
public class InterchangeableAnnotator {

    private static final Logger logger = LoggerFactory.getLogger(InterchangeableAnnotator.class);
    private final AiModelClient aiModelClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a code analysis assistant for Parsons programming puzzles.

            Your job is to identify lines in a code solution that are INTERCHANGEABLE in their ordering.
            You must be conservative — only mark lines as interchangeable when swapping them
            produces semantically identical behavior. If in doubt, do NOT mark them.

            Two annotation types exist:

            1. "#swap:NAME" — Individual lines that are independently interchangeable.
               Each line marked with the same swap name can appear in any order relative to the others.
               Use for: independent variable declarations, independent assignments with no
               cross-dependencies, independent function calls with no side-effect ordering.

            2. "#group:NAME_N" — Multi-line blocks that must stay together as a unit.
               Each cluster gets a unique name: same prefix, different number (e.g. base_1, base_2).
               Lines with the SAME full name (e.g. base_1) form one cluster and stay together internally.
               Clusters whose names share the SAME PREFIX (e.g. base_1 and base_2) are interchangeable
               with each other — their relative order does not matter.
               This distinction is necessary because without numbering there is no way to know which
               lines belong to which cluster.
               Use for: independent if-blocks (not elif chains!), independent try-except blocks,
               independent case handlers.

            Rules:
            - Only mark lines where the order is TRULY irrelevant (no data dependency between them).
            - Variables used later must be declared before use — do NOT mark as swappable if one
              depends on another.
            - elif/else chains are NOT interchangeable (they depend on evaluation order).
            - Independent if-statements (no else) that test different conditions ARE interchangeable.
            - Lines without any annotation remain position-fixed (the default).
            - Return ONLY valid JSON matching the schema. No markdown, no explanation.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Analyze this %s code and identify interchangeable lines.

            CODE:
            %s

            Return JSON matching this schema:
            {
              "annotations": [
                {"line": <1-based line number>, "type": "swap"|"group", "name": "<group_name>"}
              ]
            }

            If no lines are interchangeable, return: {"annotations": []}

            EXAMPLES:

            --- Example 1: Independent variable declarations -> #swap ---
            Code:
            def calculate(a, b):
                x = a * 2
                y = b * 3
                return x + y

            Response:
            {"annotations": [
              {"line": 2, "type": "swap", "name": "vars"},
              {"line": 3, "type": "swap", "name": "vars"}
            ]}
            Reason: x and y have no dependency on each other. Either can be computed first.

            --- Example 2: Independent if-blocks -> #group ---
            Code:
            def fibonacci(n):
                if n == 0:
                    return 0
                if n == 1:
                    return 1
                return fibonacci(n-1) + fibonacci(n-2)

            Response:
            {"annotations": [
              {"line": 2, "type": "group", "name": "base_1"},
              {"line": 3, "type": "group", "name": "base_1"},
              {"line": 4, "type": "group", "name": "base_2"},
              {"line": 5, "type": "group", "name": "base_2"}
            ]}
            Reason: Lines 2-3 form cluster base_1 (if n==0 + return 0), lines 4-5 form cluster base_2 (if n==1 + return 1). Same prefix "base" means the clusters are interchangeable as units. Different numbers mean lines within each cluster stay together internally.

            --- Example 3: Dependent variables -> NO annotation ---
            Code:
            def process(n):
                doubled = n * 2
                result = doubled + 1
                return result

            Response:
            {"annotations": []}
            Reason: result depends on doubled. Order matters.

            --- Example 4: Mixed ---
            Code:
            def greet_all(names):
                greeting = "Hello"
                separator = ", "
                results = []
                for name in names:
                    results.append(greeting + separator + name)
                return results

            Response:
            {"annotations": [
              {"line": 2, "type": "swap", "name": "init"},
              {"line": 3, "type": "swap", "name": "init"}
            ]}
            Reason: greeting and separator are independent. results = [] must come before the loop, so it is NOT swappable.

            Now analyze the code above and return the JSON.
            """;

    public InterchangeableAnnotator(AiModelClient aiModelClient) {
        this.aiModelClient = aiModelClient;
    }

    /**
     * Annotates a sanitized solution string with #group:NAME_N and #swap:NAME markers.
     *
     * @param solution the sanitized solution code (no comments, already cleaned)
     * @param language the programming language
     * @return annotated solution string with markers, or original if annotation fails
     */
    public String annotate(String solution, String language) {
        if (solution == null || solution.isBlank()) return solution;

        try {
            String userPrompt = String.format(USER_PROMPT_TEMPLATE, language, solution);

            // Use the 3-arg generateTaskDescription: (codeSnippet, systemPrompt, userPromptBody)
            String response = aiModelClient.generateTaskDescription(solution, SYSTEM_PROMPT, userPrompt);

            logger.debug("InterchangeableAnnotator LLM response: {}", response);

            // Parse the JSON response
            List<Annotation> annotations = parseAnnotations(response);

            if (annotations.isEmpty()) {
                logger.info("No interchangeable annotations found for solution");
                return solution;
            }

            // Apply annotations to solution lines
            return applyAnnotations(solution, annotations);

        } catch (Exception e) {
            logger.warn("InterchangeableAnnotator failed, returning original solution: {}", e.getMessage());
            return solution; // Safe fallback
        }
    }

    private List<Annotation> parseAnnotations(String response) {
        try {
            // The response might be wrapped in markdown code blocks or be raw JSON
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode annotationsNode = root.get("annotations");
            if (annotationsNode == null || !annotationsNode.isArray()) {
                return Collections.emptyList();
            }

            List<Annotation> result = new ArrayList<>();
            for (JsonNode node : annotationsNode) {
                int line = node.get("line").asInt();
                String type = node.get("type").asText();
                String name = node.get("name").asText();

                if (line < 1 || (!type.equals("swap") && !type.equals("group"))) {
                    logger.warn("Skipping invalid annotation: line={}, type={}, name={}", line, type, name);
                    continue;
                }

                result.add(new Annotation(line, type, name));
            }
            return result;

        } catch (Exception e) {
            logger.warn("Failed to parse annotation JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String applyAnnotations(String solution, List<Annotation> annotations) {
        String[] lines = solution.split("\n", -1);

        // Build a map: lineNumber (1-based) → marker string
        Map<Integer, String> lineMarkers = new HashMap<>();
        for (Annotation a : annotations) {
            if (a.line >= 1 && a.line <= lines.length) {
                String marker = "#" + a.type + ":" + a.name;
                lineMarkers.put(a.line, marker);
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            result.append(lines[i]);
            String marker = lineMarkers.get(i + 1);
            if (marker != null) {
                // Append marker with a space separator
                result.append(" ").append(marker);
            }
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    private record Annotation(int line, String type, String name) {}
}
