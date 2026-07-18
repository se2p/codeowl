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
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceConceptDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;

import java.util.*;

/**
 * Repository interface for managing tiered task sequences.
 * Provides methods for loading, saving, and deleting instances
 * of TieredTaskSequenceDTO, as well as retrieving all sequences.
 */
public interface TieredTaskSequenceRepository {
    /**
     * Loads the details of the specified tiered task sequence into the provided target instance.
     * This method uses the unique identifier of the target to retrieve its corresponding data
     * from storage and populate the target object with the retrieved information.
     *
     * @param target the TieredTaskSequenceDTO object to be populated with the details of
     *               the tiered task sequence
     */
    void loadById(TieredTaskSequenceDTO target);
    /**
     * Loads all tiered task sequences from the repository.
     * The method retrieves a list of TieredTaskSequenceDTO objects,
     * each representing a complete sequence of tiered programming tasks
     * with associated metadata.
     *
     * @return a list of TieredTaskSequenceDTO objects, or an empty list if no sequences are found
     */
    List<TieredTaskSequenceDTO> loadAll();
    /**
     * Persists the specified tiered task sequence into the repository.
     * This method ensures that the TieredTaskSequenceDTO object, representing
     * a complete sequence of programming tasks, is saved for future retrieval or updates.
     *
     * @param sequence the TieredTaskSequenceDTO object representing the tiered task
     *                 sequence to be saved. It contains details such as the unique
     *                 identifier, associated programming language, concepts, tasks,
     *                 and other metadata.
     */
    void save(TieredTaskSequenceDTO sequence);
    /**
     * Deletes the specified tiered task sequence from the repository.
     * This method removes the sequence identified by the provided TieredTaskSequenceDTO.
     *
     * @param delete the TieredTaskSequenceDTO object representing the task sequence to be deleted,
     *               which includes its unique identifier and associated details
     */
    void deleteById(TieredTaskSequenceDTO delete);

    /**
     * Links a programming concept to a specific tiered task sequence in the repository.
     * This method ensures that the relationship between the given task sequence
     * and the concept is persisted. If the provided data is invalid, an exception is thrown.
     *
     * @param linkDTO the data transfer object representing the association between
     *                a programming concept and a tiered task sequence. It must contain
     *                a non-null sequence ID and concept ID. If either is null,
     *                a {@link DomainValidationException} will be thrown.
     */
    void linkConcept(TieredTaskSequenceConceptDTO linkDTO);

    /**
     * Batch variant of {@link #linkConcept(TieredTaskSequenceConceptDTO)} (OPT-6).
     * Persists all concept links for a single sequence in one JDBC batch.
     *
     * @param sequenceId the sequence to link concepts to
     * @param conceptIds the concept IDs to link (duplicates are ignored by the unique constraint)
     */
    void linkConceptsBatch(UUID sequenceId, List<UUID> conceptIds);

    void tagSequence(UUID sequenceId, UUID evalInputId, String mode, UUID runId);

    /**
     * Finds all sequences owned by the specified user.
     */
    List<TieredTaskSequenceDTO> findByOwnerId(UUID ownerId);

    /**
     * Finds the owner_id of the sequence that contains the given task (AP9).
     * Returns null if the task or sequence doesn't exist or has no owner.
     */
    UUID findOwnerIdByTaskId(UUID taskId);

    /**
     * AP19 Teil A: Updates the semantic_summary column with an LLM-generated one-line description.
     * No-op if the sequence doesn't exist.
     */
    void updateSemanticSummary(UUID sequenceId, String summary);

    /**
     * AP19 Teil A: Loads the minimal payload needed to republish a
     * {@code SequenceGeneratedEvent} for bulk retagging — sequence id, programming language,
     * and combined tier statements + code. Internally joins {@code tasks} and aggregates with
     * {@code string_agg} so no per-sequence N+1 query is needed.
     *
     * @param onlyMissingLlmTags  if true, restricts to sequences with no {@code source='llm'} tag row
     * @param specificIds         if non-null and non-empty, restricts to just these IDs
     *                            (overrides {@code onlyMissingLlmTags})
     * @param limit               safety cap on rows returned; negative/zero → no limit
     * @return list of retag candidates, newest first
     */
    List<RetagCandidate> findRetagCandidates(boolean onlyMissingLlmTags, List<UUID> specificIds, int limit);

    /**
     * Minimal payload for bulk-retagging: everything the LLM tagger needs and nothing else.
     * Deliberately flat (record) so it can cross async boundaries cheaply.
     */
    record RetagCandidate(UUID sequenceId, String programmingLanguage,
                          String combinedStatement, String combinedCode) {}
}