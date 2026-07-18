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
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;

import java.util.*;

/**
 * Repository interface for managing worksheet persistence.
 * Defines methods for creating, updating, deleting, and loading worksheet data,
 * ensuring consistent access to worksheet entities and their associated metadata.
 */
public interface WorksheetRepository {

    /**
     * Persists a new worksheet entity into the repository.
     * This method stores the provided {@link WorksheetDTO} object,
     * including its name and description, for later retrieval.
     *
     * @param worksheet the {@link WorksheetDTO} object representing the worksheet to be created.
     *                  It should contain all required information except for the generated identifier.
     */
    void createWorksheet(WorksheetDTO worksheet);

    /**
     * Updates the details of an existing worksheet in the repository.
     * The target worksheet is identified by its unique identifier within the provided DTO.
     *
     * @param worksheet the {@link WorksheetDTO} object containing updated worksheet details.
     *                  The identifier must refer to an existing worksheet in the repository.
     */
    void updateWorksheet(WorksheetDTO worksheet);

    /**
     * Deletes the specified worksheet from the repository.
     * The worksheet to be removed is identified by the unique identifier within the given DTO.
     *
     * @param worksheet the {@link WorksheetDTO} object representing the worksheet to delete,
     *                  including its unique identifier.
     */
    void deleteWorksheet(WorksheetDTO worksheet);

    /**
     * Loads the details of a worksheet based on its unique identifier
     * and populates the provided {@link WorksheetDTO} instance with the retrieved data.
     *
     * @param worksheet the {@link WorksheetDTO} instance to populate with data
     *                  corresponding to its unique identifier.
     */
    void getWorksheetById(WorksheetDTO worksheet);


    /**
     * Associates a single {@link TieredTaskSequenceDTO} with the specified {@link WorksheetDTO}.
     * <p>
     * This operation creates a new entry in the {@code worksheets_sequence} join table,
     * effectively linking the given worksheet and sequence.
     * If the association already exists, implementations should ensure idempotency
     * (i.e., not duplicate the relationship).
     *
     * @param worksheet the worksheet to which the sequence should be added; must not be {@code null}
     * @param sequence  the sequence to associate with the given worksheet; must not be {@code null}
     * @throws DomainValidationException if either the worksheet or sequence does not exist,
     *                                   or if validation of identifiers fails
     */
    void addSequenceToWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence);

    /**
     * Associates multiple {@link TieredTaskSequenceDTO} objects with the specified {@link WorksheetDTO}.
     * <p>
     * This method performs a batch operation to add multiple sequence references to the worksheet
     * in the {@code worksheets_sequence} join table.
     * Implementations should aim for transactional integrity — all associations are added
     * successfully or none at all.
     *
     * @param worksheet the worksheet to which the sequences should be added; must not be {@code null}
     * @param sequences a list of sequences to associate with the given worksheet; must not be {@code null} or empty
     * @throws DomainValidationException if the worksheet does not exist, any sequence is invalid,
     *                                   or a data integrity violation occurs
     */
    void addSequencesToWorksheet(WorksheetDTO worksheet, List<TieredTaskSequenceDTO> sequences);

    void removeSequenceFromWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence);

    /**
     * Finds all worksheets owned by the specified user.
     */
    List<WorksheetDTO> findByOwnerId(UUID ownerId);

}