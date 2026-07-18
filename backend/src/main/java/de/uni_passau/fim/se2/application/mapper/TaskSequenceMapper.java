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
package de.uni_passau.fim.se2.application.mapper;

import de.uni_passau.fim.se2.domain.entities.*;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Maps GPT JSON response to domain model objects.
 */
@Slf4j
public final class TaskSequenceMapper {

    /**
     * Number of tasks expected in a sequence.
     */
    public static final int NUMBER_OF_TASKS = 3;

    /**
     * Error message displayed when number of statements is not equal to
     * NUMBER_OF_TASKS int.
     */
    public static final String INVALID_NUM_STATEMENTS
            = "GPT response must contain exactly " + NUMBER_OF_TASKS
            + " problem statements.";

    private TaskSequenceMapper() {
    }

    /**
     * Converts a GPT JSON response into a TieredTaskSequence.
     *
     * @param gptResponse         GPT response containing "problem_statements"
     * @param programmingLanguage the programming language used (e.g., Java)
     * @param concepts            list of programming concepts
     * @param codeSnippet         the codeSnippet.
     * @param taskDescription     the problem Statement.
     * @return the mapped TieredTaskSequence
     */
    public static TieredTaskSequenceDTO map(final JsonNode gptResponse,
                                            final String programmingLanguage,
                                            final List<String> concepts,
                                            final String codeSnippet,
                                            final String taskDescription) {
        log.debug("Mapping GPT response to TieredTaskSequence");
        JsonNode statements = gptResponse.get("problem_statements");
        if (statements == null || !statements.isArray()
                || statements.size() != NUMBER_OF_TASKS) {
            log.error("Invalid number of statements in GPT response");
            throw new IllegalArgumentException(INVALID_NUM_STATEMENTS);
        }

        List<TaskDTO> tasks = new ArrayList<>();
        return getTieredTaskSequenceDTO(concepts, programmingLanguage, codeSnippet, taskDescription, statements, tasks);
    }

    /**
     * Creates an incomplete Sequence with everything that is "given", which
     * will then be enriched and mapped to a complete Sequence.
     *
     * @param json                the gpt response.
     * @param concepts            programming concepts found in the codeSnippet.
     * @param programmingLanguage the programming language.
     * @param codeSnippet         the codeSnippet.
     * @param taskDescription     the problem Statement.
     * @return an incomplete TieredTaskSequence.
     */
    public static TieredTaskSequenceDTO mapToIncompleteSequence(
            final JsonNode json,
            final List<String> concepts,
            final String programmingLanguage,
            final String codeSnippet,
            final String taskDescription) {
        return mapToIncompleteSequence(json, concepts, programmingLanguage, codeSnippet, taskDescription, NUMBER_OF_TASKS);
    }

    /**
     * AP21: Tier-count-aware overload for 5-tier mode.
     */
    public static TieredTaskSequenceDTO mapToIncompleteSequence(
            final JsonNode json,
            final List<String> concepts,
            final String programmingLanguage,
            final String codeSnippet,
            final String taskDescription,
            final int tierCount) {
        log.debug("Mapping to incomplete sequence (tierCount={})", tierCount);
        if (json.has("tasks")) {
            log.info("Using new contract schema");
            return mapFromContracts(json, concepts, programmingLanguage, codeSnippet, taskDescription, tierCount);
        } else if (json.has("problem_statements")) {
            log.info("Using legacy problem statements schema");
            return mapFromLegacyProblemStatements(json, concepts, programmingLanguage, codeSnippet, taskDescription);
        } else {
            log.error("Missing required fields in GPT response");
            throw new IllegalArgumentException(
                    "GPT response missing required field: either 'tasks' (new schema) or 'problem_statements' (legacy).");
        }
    }

    private static TieredTaskSequenceDTO mapFromContracts(
            final JsonNode json,
            final List<String> concepts,
            final String programmingLanguage,
            final String codeSnippet,
            final String taskDescription) {
        return mapFromContracts(json, concepts, programmingLanguage, codeSnippet, taskDescription, NUMBER_OF_TASKS);
    }

    private static TieredTaskSequenceDTO mapFromContracts(
            final JsonNode json,
            final List<String> concepts,
            final String programmingLanguage,
            final String codeSnippet,
            final String taskDescription,
            final int tierCount) {
        log.debug("Mapping from contracts schema (tierCount={})", tierCount);
        JsonNode tasksNode = json.get("tasks");
        if (tasksNode == null || !tasksNode.isArray() || tasksNode.size() != tierCount) {
            log.error("Invalid number of tasks in GPT response: expected={}, got={}", tierCount,
                      tasksNode != null ? tasksNode.size() : 0);
            throw new IllegalArgumentException("GPT response must contain exactly " + tierCount + " tasks.");
        }

        List<TaskDTO> tasks = new ArrayList<>(tierCount);
        for (int i = 0; i < tierCount; i++) {
            JsonNode t = tasksNode.get(i);
            if (t == null || !t.isObject()) {
                log.error("Invalid task object at index {}", i);
                throw new IllegalArgumentException("tasks[" + i + "] must be an object.");
            }

            // tier
            int tier = t.has("tier") && t.get("tier").isInt() ? t.get("tier").asInt() : (i + 1);
            if (tier < 1 || tier > tierCount) {
                log.error("Invalid tier {} for task {} (tierCount={})", tier, i, tierCount);
                throw new IllegalArgumentException("tasks[" + i + "].tier must be 1.." + tierCount + ".");
            }

            // statement
            JsonNode statementNode = t.get("statement");
            if (statementNode == null || !statementNode.isTextual() || statementNode.asText().isBlank()) {
                log.error("Invalid statement for task {}", i);
                throw new IllegalArgumentException("tasks[" + i + "].statement must be a non-empty string.");
            }
            String statement = statementNode.asText().trim();

            // contract
            JsonNode c = t.get("contract");
            if (c == null || !c.isObject()) {
                log.error("Invalid contract for task {}", i);
                throw new IllegalArgumentException("tasks[" + i + "].contract must be an object.");
            }

            String cLanguage = requireText(c, "language", i);
            String cTaskStatement = requireText(c, "taskStatement", i);
            String cFunctionSignature = requireText(c, "functionSignature", i);
            String cPurposeBehavior = requireText(c, "purposeBehavior", i);
            String cPrePostConditions = requireText(c, "prePostConditions", i);

            // statement == contract.taskStatement
            if (!statement.equals(cTaskStatement)) {
                log.error("Statement mismatch for task {}", i);
                throw new IllegalArgumentException("tasks[" + i + "]: statement must equal contract.taskStatement.");
            }

            // examples
            JsonNode exArr = c.get("examples");
            if (exArr == null || !exArr.isArray() || exArr.size() < 1 || exArr.size() > 2) {
                log.error("Invalid examples for task {}", i);
                throw new IllegalArgumentException("tasks[" + i + "].contract.examples must have 1–2 items.");
            }
            List<SolveContract.ExamplePair> examples = new ArrayList<>(exArr.size());
            for (int j = 0; j < exArr.size(); j++) {
                JsonNode ex = exArr.get(j);
                if (ex == null || !ex.isObject()) {
                    log.error("Invalid example object for task {} example {}", i, j);
                    throw new IllegalArgumentException(
                            "tasks[" + i + "].contract.examples[" + j + "] must be an object.");
                }
                String in = requireText(ex, "input", i, j);
                String out = requireText(ex, "output", i, j);
                examples.add(new SolveContract.ExamplePair(in, out));
            }

            // SolveContract bauen
            log.debug("Building contract for task {}", i);
            SolveContract contract = SolveContract.builder()
                    .language(cLanguage)
                    .taskStatement(cTaskStatement)
                    .functionSignature(cFunctionSignature)
                    .purposeBehavior(cPurposeBehavior)
                    .prePostConditions(cPrePostConditions)
                    .examples(examples)
                    .build();

            // Optional: extract distractorPlan (AP15)
            String distractorPlanJson = null;
            JsonNode dpNode = t.get("distractorPlan");
            if (dpNode != null && dpNode.isObject()) {
                distractorPlanJson = dpNode.toString();
            }

            // TaskDTO bauen (solution & metrics werden später befüllt)
            log.debug("Building TaskDTO for task {}", i);
            tasks.add(TaskDTO.builder()
                              .statement(statement)
                              .contract(contract)
                              .solution("")
                              .tierLevel(tier)
                              .cyclomaticComplexity(0)
                              .maximalNestingDepth(0)
                              .methodCallCount(0)
                              .distinctIdentifierCount(0)
                              .linesOfCode(0)
                              .distractorPlan(distractorPlanJson)
                              .build());
        }

        log.debug("Building TieredTaskSequenceDTO (tierCount={})", tierCount);
        return TieredTaskSequenceDTO.builder()
                .programmingLanguage(programmingLanguage)
                .concepts(concepts)
                .tasks(tasks)
                .codeSnippet(codeSnippet)
                .taskDescription(taskDescription)
                .tierCount(tierCount)
                .build();
    }

    private static TieredTaskSequenceDTO mapFromLegacyProblemStatements(
            final JsonNode json,
            final List<String> concepts,
            final String programmingLanguage,
            final String codeSnippet,
            final String taskDescription) {
        log.debug("Mapping from legacy problem statements");
        JsonNode statementsNode = json.get("problem_statements");
        if (statementsNode == null || !statementsNode.isArray() || statementsNode.size() != NUMBER_OF_TASKS) {
            log.error("Invalid number of statements in legacy GPT response");
            throw new IllegalArgumentException(INVALID_NUM_STATEMENTS);
        }

        List<TaskDTO> tasks = new ArrayList<>(NUMBER_OF_TASKS);
        return getTieredTaskSequenceDTO(concepts, programmingLanguage, codeSnippet, taskDescription, statementsNode,
                                        tasks);
    }

    private static TieredTaskSequenceDTO getTieredTaskSequenceDTO(List<String> concepts, String programmingLanguage,
                                                                  String codeSnippet, String taskDescription,
                                                                  JsonNode statementsNode, List<TaskDTO> tasks) {
        log.debug("Building TaskDTOs from statements");
        for (int i = 0; i < NUMBER_OF_TASKS; i++) {
            String statement = statementsNode.get(i).asText();
            tasks.add(TaskDTO.builder()
                              .statement(statement)
                              .solution("")
                              .tierLevel(i + 1)
                              .cyclomaticComplexity(0)
                              .build());
        }

        log.debug("Building TieredTaskSequenceDTO from statements");
        return TieredTaskSequenceDTO.builder()
                .programmingLanguage(programmingLanguage)
                .concepts(concepts)
                .tasks(tasks)
                .codeSnippet(codeSnippet)
                .taskDescription(taskDescription)
                .build();
    }

// ---------- kleine Feld-Helper ----------

    private static String requireText(JsonNode obj, String field, int taskIdx) {
        JsonNode n = obj.get(field);
        if (n == null || !n.isTextual()) {
            log.error("Missing or invalid field {} for task {}", field, taskIdx);
            throw new IllegalArgumentException("tasks[" + taskIdx + "].contract." + field + " must be a string.");
        }
        String s = n.asText().trim();
        if (s.isEmpty()) {
            log.error("Empty field {} for task {}", field, taskIdx);
            throw new IllegalArgumentException("tasks[" + taskIdx + "].contract." + field + " must not be blank.");
        }
        return s;
    }

    private static String requireText(JsonNode obj, String field, int taskIdx, int exIdx) {
        JsonNode n = obj.get(field);
        if (n == null || !n.isTextual()) {
            log.error("Missing or invalid field {} for task {} example {}", field, taskIdx, exIdx);
            throw new IllegalArgumentException(
                    "tasks[" + taskIdx + "].contract.examples[" + exIdx + "]." + field + " must be a string.");
        }
        String s = n.asText().trim();
        if (s.isEmpty()) {
            log.warn("Empty field {} for task {} example {}, filled it with 'None'", field, taskIdx, exIdx);
            s = "None";
        }
        return s;
    }
}