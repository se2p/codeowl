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
     * Hides a sequence from the library, search, suggestions and its owner's list, without
     * removing it. Worksheets that already contain it keep working, because a worksheet loads
     * its sequences by id and the row (with its tasks and submissions) still exists.
     *
     * @return whether this call is what deleted it; false for an unknown or already-deleted id
     */
    boolean softDeleteById(UUID sequenceId);

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

    /**
     * Replaces every concept linked to a sequence with the given set. Used when
     * a teacher edits the concepts on the sequence page, where the edit is a new
     * set rather than a series of add/remove operations.
     *
     * @param sequenceId the sequence whose concepts are being replaced
     * @param conceptIds the concept IDs that should remain linked; an empty list
     *                   leaves the sequence with none
     */
    void replaceConcepts(UUID sequenceId, List<UUID> conceptIds);

    /**
     * Overwrites a sequence's task description, the text shown under "Task Description" on
     * the sequence page and searched by the library.
     *
     * @param taskDescription the new text, or {@code null} to clear it
     */
    void updateTaskDescription(UUID sequenceId, String taskDescription);

    /**
     * Sets or clears the owner-given name of a sequence.
     *
     * @param title the new name, or {@code null} to fall back to the default heading
     */
    void updateTitle(UUID sequenceId, String title);

    /**
     * The concept IDs currently linked to a sequence.
     *
     * <p>The read counterpart to {@link #linkConceptsBatch(UUID, List)}. Copying a sequence
     * needs it: without the links the copy loses its structural concepts, and with them the
     * copy's tags can be reproduced without paying for another LLM pass.
     *
     * @param sequenceId the sequence to read links for
     * @return the linked concept IDs, empty when there are none or the sequence is unknown
     */
    List<UUID> findConceptIdsBySequenceId(UUID sequenceId);

    /**
     * Concept names for many sequences in one round trip — the list-screen counterpart to
     * {@link #findConceptIdsBySequenceId(UUID)}, which only reads for a single sequence.
     *
     * @param sequenceIds the sequences to read concept names for
     * @return sequenceId to its concept names (sorted by name); a sequence with no linked
     *         concepts is simply absent from the map rather than mapped to an empty list
     */
    Map<UUID, List<String>> findConceptNamesBySequenceIds(List<UUID> sequenceIds);

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
     * Records that a copied sequence has been edited, so the library starts listing it.
     *
     * <p>Sets {@code diverged_at} only on a copy that has not already diverged: an original has
     * nothing to record, and a second edit must not move the timestamp — "first edited" is the
     * question it answers. Both conditions live in the statement's WHERE rather than in a
     * read-then-write, so concurrent edits cannot race into two different answers.
     *
     * <p>No-op for an unknown id, an original, or a copy already marked.
     *
     * @return whether this call is what marked it
     */
    boolean markDiverged(UUID sequenceId);

    /**
     * {@link #markDiverged(UUID)} reached through one of the sequence's tasks.
     *
     * <p>Editing a task edits the sequence that holds it — the material a teacher changes is
     * almost always a task's statement, solution or distractors, not a sequence-level field. A
     * single statement rather than a lookup followed by a mark, so the task's parent is resolved
     * and stamped atomically.
     *
     * @return whether this call is what marked it
     */
    boolean markDivergedByTaskId(UUID taskId);

    /**
     * AP19 Teil A: Updates the semantic_summary column with an LLM-generated one-line description.
     * No-op if the sequence doesn't exist.
     */
    void updateSemanticSummary(UUID sequenceId, String summary);

    /**
     * Reads the semantic_summary column.
     *
     * <p>The symmetric partner of {@link #updateSemanticSummary(UUID, String)}, added so a
     * copied sequence can inherit its source's summary instead of waiting on a fresh LLM call
     * that costs money and would drift from the original wording.
     *
     * <p>Deliberately a repository method rather than a field on
     * {@link TieredTaskSequenceDTO}: the summary is not part of what the rest of the
     * application loads and passes around, and widening the main read path for one copy
     * operation would touch every caller of {@code loadById}.
     *
     * @param sequenceId the sequence to read
     * @return the summary, or {@code null} when there is none or the sequence is unknown
     */
    String findSemanticSummary(UUID sequenceId);

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
     * Concept-facet mirror of {@link #findRetagCandidates}, for the bulk concept re-classifier:
     * same payload shape (a {@link RetagCandidate} is reused as-is — {@code combinedStatement}
     * goes unused here, but a second near-identical record would only cost more to keep in
     * step), same {@code specificIds}-overrides-filter rule, same safety cap. "Missing" means no
     * row at all in {@code tiered_task_sequence_concepts} for that sequence, the concept-table
     * analogue of "no {@code source='llm'} tag row".
     *
     * @param onlyMissingConcepts if true, restricts to sequences with no linked concept yet
     * @param specificIds         if non-null and non-empty, restricts to just these IDs
     *                            (overrides {@code onlyMissingConcepts})
     * @param limit                safety cap on rows returned; negative/zero → no limit
     * @return list of candidates, newest first
     */
    List<RetagCandidate> findConceptReclassifyCandidates(boolean onlyMissingConcepts, List<UUID> specificIds, int limit);

    /**
     * Minimal payload for bulk-retagging: everything the LLM tagger needs and nothing else.
     * Deliberately flat (record) so it can cross async boundaries cheaply.
     */
    record RetagCandidate(UUID sequenceId, String programmingLanguage,
                          String combinedStatement, String combinedCode) {}
}