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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * The last solved arrangement of a task, per student.
 *
 * There is deliberately no delete: redoing a task resets what the student sees
 * but keeps the stored solution, so abandoning a redo halfway leaves their
 * completed work intact. Solving again replaces it.
 */
public interface TaskSubmissionRepository {

    /**
     * Stores the student's solved arrangement, replacing any earlier one for the
     * same student and task.
     */
    void save(TaskSubmissionDTO submission);

    Optional<TaskSubmissionDTO> find(UUID userId, UUID taskId);

    /**
     * Removes a student's stored arrangement for a task, so the next visit deals a fresh
     * puzzle. A no-op when nothing is stored.
     *
     * @param userId the student
     * @param taskId the task
     */
    void delete(UUID userId, UUID taskId);
}
