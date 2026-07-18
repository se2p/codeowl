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
package de.uni_passau.fim.se2.domain.entities;

import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a complete sequence of tiered programming tasks.
 * Typically, includes one task per tier level (e.g., 1–3).
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TieredTaskSequenceDTO {

    /**
     * Unique identifier for the task sequence.
     *
     * @return the UUID of the sequence
     */
    private UUID id;

    /**
     * The programming language associated with this task sequence.
     *
     * @return the language name
     */
    private String programmingLanguage;

    /**
     * The list of programming concepts covered in the task sequence.
     *
     * @return a list of concepts
     */
    private List<String> concepts;

    /**
     * The list of individual tasks in the sequence.
     *
     * @return a list of task DTOs
     */
    @Builder.Default
    private List<TaskDTO> tasks = new ArrayList<>();

    /**
     * A sample code snippet optionally used to derive or support the tasks.
     *
     * @return the code snippet
     */
    private String codeSnippet;

    /**
     * A textual description of the task sequence.
     *
     * @return the task description
     */
    private String taskDescription;

    /**
     * The timestamp indicating when the task sequence was created.
     *
     * @return the timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Version of the program that created this Sequence.
     */
    private UUID versionId;

    /**
     * Strategy used to generate Sequence.
     */
    private String strategyName;

    /**
     * The owner (teacher/admin) who created this sequence.
     * NULL for legacy/anonymous sequences.
     */
    private UUID ownerId;

    /**
     * The human language chosen for problem-statement generation (e.g. "de", "en").
     * NULL for legacy sequences; defaults to "de" for new ones.
     */
    @Builder.Default
    private String targetLanguage = "de";

    /**
     * AP20: Generation constraints (blacklist, whitelist, difficulty slope, etc.).
     * Stored as JSONB in the database. NULL for unconstrained sequences.
     */
    private GenerationConstraints constraints;

    /**
     * AP21: Number of tiers in this sequence (3 or 5). Default: 3.
     */
    @Builder.Default
    private int tierCount = 3;

    /**
     * AP19: Curriculum topic this sequence was generated from (optional).
     * NULL for non-curriculum sequences.
     */
    private UUID curriculumTopicId;

    /**
     * Returns the task for a given tier level, if present.
     *
     * @param tierLevel the tier level (e.g. 1, 2, 3)
     * @return an optional task with that level
     */
    public Optional<TaskDTO> getTaskByTierLevel(final int tierLevel) {
        if (tasks == null) return Optional.empty();
        return tasks.stream().filter(task -> task.getTierLevel() == tierLevel).findFirst();
    }

    /**
     * Returns the tasks ordered by tier level in ascending order.
     *
     * @return a sorted list of tasks
     */
    public List<TaskDTO> getOrderedTasks() {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .sorted(Comparator.comparingInt(TaskDTO::getTierLevel))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether the sequence is complete — i.e., it has exactly 3 tasks,
     * and each tier level is unique.
     *
     * @return true if a sequence is complete, false otherwise
     */
    @JsonIgnore
    public boolean isComplete() {
        return tasks != null && !tasks.isEmpty() && tasks.stream().allMatch(Objects::nonNull);
    }
}