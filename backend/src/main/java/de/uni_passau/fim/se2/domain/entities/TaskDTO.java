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
package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.util.*;

/**
 * Represents a task with a statement, solution, and tier level.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    /**
     * The problem statement of the task.
     *
     * @return the task statement
     */
    private String statement;

    private SolveContract contract;

    /**
     * The solution or answer to the task.
     *
     * @return the solution string
     */
    private String solution;

    /**
     * The tier level of the task, indicating its difficulty.
     *
     * @return the tier level as an integer
     */
    private int tierLevel;

    /**
     * A name the sequence owner gave this task, e.g. "Warm-up: summing a list".
     * NULL when none was given; clients then show a numbered default derived from
     * {@link #tierLevel}, in the viewer's language.
     */
    private String title;

    /**
     * The cyclomatic complexity of the task's solution.
     *
     * @return the cyclomatic complexity as an integer
     */
    private int cyclomaticComplexity;

    /**
     * The unique identifier of the task.
     *
     * @return the task ID
     */
    private UUID id;

    /**
     * The unique identifier of the sequence.
     *
     * @return the sequence ID
     */
    private UUID sequenceId;

    /**
     * The number of lines of code in the task's solution.
     *
     * @return the lines of code.
     */
    private int linesOfCode;

    /**
     * Represents the maximum level of nesting in the solution code for the task.*
     * The nesting depth refers to the deepest level of code blocks (e.g., loops,
     * conditionals) present in the task's solution, which can be an indicator
     * of complexity.
     */
    private int maximalNestingDepth;

    /**
     * The number of method calls made within the task's solution implementation.*
     * This value quantifies the total count of distinct method calls present in
     * the provided solution code for the task. It is used as a metric to analyze
     * the complexity and modularity of the solution.
     */
    private int methodCallCount;

    /**
     * The number of distinct identifiers used in the task's solution.*
     * This metric counts the unique variable names, functions, classes, or other identifiers
     * present in the task's solution code. It is used to measure the semantic diversity
     * and complexity of the code.
     */
    private int distinctIdentifierCount;

    /**
     * the Programming language the task is in.
     */
    private String programmingLanguage;

    /**
     * Distractor lines separated by \n. Each line ends with #distractor.
     * NULL when distractors are not enabled or not yet generated.
     */
    private String distractors;

    /**
     * JSON string with the distractor plan from sequence generation.
     * Format: {"type":"LOGIC_FLIP","rationale":"..."}
     * NULL when distractors are not enabled.
     */
    private String distractorPlan;

    /**
     * Whether distractors should be delivered to students.
     * When false, distractors are kept in DB but not sent to non-owners.
     * Default: true (distractors active if present).
     */
    @Builder.Default
    private boolean distractorsEnabled = true;

    /**
     * AP22: Read-only context lines displayed BEFORE the puzzle (imports, class decl, etc.).
     * Stored separately from solution so parsons.js never sees them.
     */
    private String contextBefore;

    /**
     * AP22: Read-only context lines displayed AFTER the puzzle (main method, closing braces, etc.).
     */
    private String contextAfter;

    /**
     * AP31: JSON string with the inline-split plan (fragment decomposition of selected lines).
     * Format: {"splits":[{"lineIndex":4,"fragments":["int i = 0;","i < arr.length;","i++"],
     *          "prefix":"for (","suffix":") {","distractors":[{"text":"i <= arr.length;","type":"OFF_BY_ONE"}]}]}
     * NULL when no split lines are defined. Fragment distractors are intentionally visible
     * (no answer-stripping on delivery).
     */
    private String splitPlan;

    /**
     * A detached copy of this task, belonging to a different sequence.
     *
     * <p>Built from {@code toBuilder()} rather than by naming fields one by one. That is the
     * whole point: every field travels with the copy automatically, so a column added later
     * cannot silently fall out of it. Naming fields is how the distractors, the context blocks
     * and the AP31 split plan were lost by the worksheet copy for as long as they existed.
     *
     * <p>Only identity is reset — the id because a copy is a new row, and the sequence id
     * because the copy belongs to a different parent. Everything else, including the
     * {@code distractorsEnabled} flag whose {@code @Builder.Default} would otherwise silently
     * flip a disabled task back to enabled, is carried over as-is.
     *
     * @param newSequenceId the sequence the copy belongs to
     * @return a new task, ready to be persisted
     */
    public TaskDTO copyFor(UUID newSequenceId) {
        return toBuilder()
                .id(null)
                .sequenceId(newSequenceId)
                .build();
    }

}