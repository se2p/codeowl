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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceTaskDTO;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceTaskRepository;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public class PostgreSequenceTaskDAO implements TieredTaskSequenceTaskRepository {
    /**
     * Persists the provided TieredTaskSequenceTaskDTO instance.
     * Typically used to associate a task with a specific tiered task sequence.
     *
     * @param dto the TieredTaskSequenceTaskDTO object containing the mapping of a task
     *            to a tiered task sequence, including taskId and sequenceId
     */
    @Override
    public void save(TieredTaskSequenceTaskDTO dto) {

    }

    /**
     * Retrieves all tasks associated with the given tiered task sequence.
     *
     * @param sequence the tiered task sequence for which tasks are to be retrieved
     * @return a list of tasks that belong to the specified sequence
     */
    @Override
    public List<TaskDTO> findAllBySequenceId(TieredTaskSequenceDTO sequence) {
        return List.of();
    }

    /**
     * Deletes the specified mapping between a tiered task sequence and a task.
     *
     * @param dto the data transfer object containing the task ID and sequence ID
     *            of the mapping to be deleted
     */
    @Override
    public void delete(TieredTaskSequenceTaskDTO dto) {

    }
}