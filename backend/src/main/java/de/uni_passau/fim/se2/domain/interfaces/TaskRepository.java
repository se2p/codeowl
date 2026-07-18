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

import de.uni_passau.fim.se2.domain.entities.SnippetDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;

import java.util.*;

/**
 * Repository interface for managing task persistence.
 * Defines methods for loading, saving, and deleting tasks, as well as
 * handling bulk operations in connection with task sequences.
 */
public interface TaskRepository {
    /**
     * Loads the details of the specified task into the given TaskDTO instance.
     * Typically retrieves task information from the repository using its unique identifier
     * and populates the target TaskDTO object with the retrieved data.
     *
     * @param target the TaskDTO instance to be populated with the details of the task
     *               identified by its unique identifier
     */
    void loadById(TaskDTO target);
    /**
     * Loads a list of tasks associated with the given tiered task sequence.
     *
     * @param sequence the TieredTaskSequenceDTO representing the task sequence
     *                 whose associated tasks are to be retrieved
     * @return a list of TaskDTO objects corresponding to the tasks in the specified
     *         tiered task sequence, or an empty list if no tasks are found
     */
    List<TaskDTO> loadTasksBySequenceId(TieredTaskSequenceDTO sequence);
    /**
     * Persists the given task data transfer object (TaskDTO) into the repository.
     * This method ensures that the task details, such as statement, solution, and tier level,
     * are stored for future retrieval or updates.
     *
     * @param task the TaskDTO object representing the task to be saved.
     *             It contains details like problem statement, solution, tier level,
     *             cyclomatic complexity, and a unique identifier.
     */
    void save(TaskDTO task);
    /**
     * Persists a list of TaskDTO objects to the repository.
     * This method is used to save multiple tasks at once, ensuring bulk persistence
     * and reducing the overhead of saving each task individually.
     *
     * @param tasks the list of TaskDTO objects to be saved into the repository
     */
    void saveAll(TieredTaskSequenceDTO tasks);
    /**
     * Deletes the specified task from the repository.
     * This method removes the task identified by the provided TaskDTO object.
     *
     * @param target the TaskDTO object representing the task to be deleted,
     *               which includes its unique identifier
     */
    void deleteById(TaskDTO target);

    void deleteBySequenceId(TaskDTO target);

    List<SnippetDTO> findAllWithConcepts();

    /**
     * Updates the statement and solution of an existing task (AP9).
     */
    void updateStatementAndSolution(UUID taskId, String statement, String solution);

    /**
     * Updates only the distractors column of an existing task (AP15).
     */
    void updateDistractors(UUID taskId, String distractors);

    /**
     * Enables or disables distractors for an existing task (AP15).
     * When disabled, distractors are kept in DB but not delivered to students.
     */
    void updateDistractorsEnabled(UUID taskId, boolean enabled);

    /**
     * Updates only the split_plan (JSONB) column of an existing task (AP31).
     *
     * @param splitPlanJson serialized {@link de.uni_passau.fim.se2.domain.entities.SplitPlan},
     *                      or {@code null} to clear the plan.
     */
    void updateSplitPlan(UUID taskId, String splitPlanJson);
}