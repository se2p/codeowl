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

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AP22: Identifies boilerplate/scaffolding lines in a code solution and separates them
 * into contextBefore / contextAfter blocks stored in dedicated DB columns.
 *
 * The puzzle portion (non-context lines) MUST remain <= 20 lines.
 * On failure, returns the original solution unchanged (safe fallback).
 */
@Service
public class ContextAnnotator {

    private static final Logger logger = LoggerFactory.getLogger(ContextAnnotator.class);
    private final AiModelClient aiModelClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_PUZZLE_LINES = 20;

    private static final String SYSTEM_PROMPT = """
            You are a code analysis assistant for Parsons programming puzzles (Context Mode).

            Your job is to split a code solution into CONTEXT (read-only scaffolding) and
            PUZZLE (the part the student must arrange).

            Rules:
            1. The PUZZLE portion MUST be <= 20 non-empty lines. This is a hard constraint.
            2. CONTEXT = imports, class/module declarations, constructor boilerplate,
               main blocks, test harness, print statements for demonstration, type hints.
            3. PUZZLE = the core algorithm or method body the student should understand and arrange.
            4. Context lines must form CONTIGUOUS blocks — either before the puzzle, after the
               puzzle, or both. Do NOT interleave context and puzzle lines.
            5. If the entire solution is <= 20 lines, return an EMPTY contextLines array
               (no context needed).
            6. Preserve logical grouping — never split a single method between context and puzzle.
               The puzzle should be one coherent unit (e.g., one method body, one algorithm).

            Return ONLY valid JSON:
            {"contextLines": [1, 2, 3, 15, 16, 17]}

            contextLines is a list of 1-based line numbers that should be marked as context.
            All other non-empty lines become puzzle lines.
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            Language: %s

            Code solution:
            ```
            %s
            ```

            Identify which lines are CONTEXT (scaffolding, boilerplate) and which are the
            PUZZLE (core logic the student should arrange).

            Remember: The puzzle portion must be <= 20 non-empty lines.
            Context lines must be contiguous blocks at the top and/or bottom.

            Return JSON: {"contextLines": [line_numbers]}
            """;

    public ContextAnnotator(AiModelClient aiModelClient) {
        this.aiModelClient = aiModelClient;
    }

    /**
     * Result of context annotation: puzzle-only solution + separated context blocks.
     */
    public record ContextResult(String puzzleSolution, String contextBefore, String contextAfter) {}

    /**
     * Analyzes a solution and separates it into puzzle lines and context blocks.
     *
     * @param solution the code solution (may already have #swap/#group markers)
     * @param language the programming language
     * @return ContextResult with separated puzzle/context, or original solution with null context on failure
     */
    public ContextResult annotate(String solution, String language) {
        if (solution == null || solution.isBlank()) {
            return new ContextResult(solution, null, null);
        }

        // Count non-empty lines — if already <= MAX_PUZZLE_LINES, no context needed
        long nonEmptyLines = solution.lines()
                .filter(l -> !l.trim().isEmpty())
                .count();
        if (nonEmptyLines <= MAX_PUZZLE_LINES) {
            logger.debug("Solution has {} non-empty lines (<= {}), no context annotation needed",
                    nonEmptyLines, MAX_PUZZLE_LINES);
            return new ContextResult(solution, null, null);
        }

        try {
            String userPrompt = String.format(USER_PROMPT_TEMPLATE, language, solution);

            String response = aiModelClient.generateTaskDescription(solution, SYSTEM_PROMPT, userPrompt);
            logger.debug("ContextAnnotator LLM response: {}", response);

            List<Integer> contextLines = parseContextLines(response);

            if (contextLines.isEmpty()) {
                logger.info("No context lines identified for solution");
                return new ContextResult(solution, null, null);
            }

            // Validate: puzzle portion must be <= MAX_PUZZLE_LINES
            String[] lines = solution.split("\n", -1);
            Set<Integer> contextSet = new HashSet<>(contextLines);
            long puzzleLineCount = 0;
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty() && !contextSet.contains(i + 1)) {
                    puzzleLineCount++;
                }
            }

            if (puzzleLineCount > MAX_PUZZLE_LINES) {
                logger.warn("Puzzle portion has {} lines (> {}), skipping context annotation",
                        puzzleLineCount, MAX_PUZZLE_LINES);
                return new ContextResult(solution, null, null);
            }

            return separateContext(solution, contextSet);

        } catch (Exception e) {
            logger.warn("ContextAnnotator failed, returning original solution: {}", e.getMessage());
            return new ContextResult(solution, null, null);
        }
    }

    private List<Integer> parseContextLines(String response) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode contextNode = root.get("contextLines");
            if (contextNode == null || !contextNode.isArray()) {
                return Collections.emptyList();
            }

            List<Integer> result = new ArrayList<>();
            for (JsonNode node : contextNode) {
                int line = node.asInt();
                if (line >= 1) {
                    result.add(line);
                }
            }
            return result;

        } catch (Exception e) {
            logger.warn("Failed to parse context lines JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Separates solution into contextBefore, puzzleSolution, contextAfter.
     * Context lines form contiguous blocks at start and/or end.
     * Lines with #swap/#group/#distractor markers are never treated as context.
     */
    private ContextResult separateContext(String solution, Set<Integer> contextLineNumbers) {
        String[] lines = solution.split("\n", -1);
        List<String> beforeLines = new ArrayList<>();
        List<String> puzzleLines = new ArrayList<>();
        List<String> afterLines = new ArrayList<>();
        boolean puzzleStarted = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isContext = contextLineNumbers.contains(i + 1)
                    && !line.trim().isEmpty()
                    && !line.contains("#swap:") && !line.contains("#group:")
                    && !line.contains("#distractor");

            if (isContext) {
                if (!puzzleStarted) {
                    beforeLines.add(line);
                } else {
                    afterLines.add(line);
                }
            } else {
                if (!line.trim().isEmpty()) puzzleStarted = true;
                puzzleLines.add(line);
            }
        }

        String ctxBefore = beforeLines.isEmpty() ? null : String.join("\n", beforeLines);
        String ctxAfter = afterLines.isEmpty() ? null : String.join("\n", afterLines);
        String puzzle = String.join("\n", puzzleLines);

        logger.debug("Context separation: before={} lines, puzzle={} lines, after={} lines",
                beforeLines.size(), puzzleLines.size(), afterLines.size());

        return new ContextResult(puzzle, ctxBefore, ctxAfter);
    }
}
