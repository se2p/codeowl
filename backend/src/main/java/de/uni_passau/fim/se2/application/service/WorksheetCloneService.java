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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deep-copies a worksheet, with everything the original had.
 *
 * <p>Split out of {@link WorksheetService} because the copy is not a CRUD operation: it spans
 * four repositories, and every field it forgets is data a teacher loses without being told. The
 * previous version lived inline and rebuilt sequences and tasks by naming fields one at a time,
 * which meant every column added after it was written silently stopped being copied — the
 * distractors, the AP22 context blocks, the AP31 split plans, the tier count, the target
 * language and the generation constraints had all fallen out by the time anyone noticed.
 *
 * <p>Two things keep that from happening again:
 *
 * <ol>
 *   <li>The per-entity copies are {@code toBuilder()}-based factories that live next to the
 *       field declarations — {@link TieredTaskSequenceDTO#copyFor(UUID)} and
 *       {@link TaskDTO#copyFor(UUID)}. A new field travels with the copy by default; you have
 *       to go out of your way to exclude one.</li>
 *   <li>{@code CloneCompletenessTest} reflects over both DTOs and fails the build when a field
 *       is neither asserted to survive a copy nor listed as deliberately reset. Java cannot
 *       make a hand-written copy complete, but a test can make an incomplete one loud.</li>
 * </ol>
 *
 * <h2>Why tags are copied rather than regenerated</h2>
 *
 * <p>Sequences are normally tagged by {@link TaskPersistenceService#persistSequence}, which runs
 * the structural tagger and publishes a {@code SequenceGeneratedEvent} for the asynchronous LLM
 * pass. Routing clones through that path would look tidy and be wrong twice over: it bills an
 * LLM call per copied sequence, and the fresh tags would drift from the ones on the original,
 * so a copy would answer to different search terms than the thing it was copied from. Copying
 * the rows across is free, instant and exact.
 *
 * <p>The same reasoning applies to {@code semantic_summary}, which is copied verbatim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorksheetCloneService {

    private final WorksheetRepository worksheetRepo;
    private final TieredTaskSequenceRepository sequenceRepo;
    private final TaskRepository taskRepo;
    private final SequenceTagRepository tagRepo;

    /**
     * Copies a worksheet, its sequences and their tasks to a new owner.
     *
     * <p>Everything gets fresh identifiers; the source is left untouched. Ownership of the copied
     * sequences transfers to {@code newOwnerId} as well, so the copier can edit them without
     * reaching into someone else's material.
     *
     * @param sourceWorksheetId the worksheet to copy
     * @param newOwnerId        the user the copy belongs to
     * @return the freshly loaded copy
     * @throws DomainValidationException if either argument is null, or if the source does not
     *                                   exist — {@link WorksheetRepository#getWorksheetById}
     *                                   raises that itself, so there is no null check here to
     *                                   go stale
     */
    @Transactional
    public WorksheetDTO copy(UUID sourceWorksheetId, UUID newOwnerId) {
        if (sourceWorksheetId == null) {
            throw new DomainValidationException("Source worksheet ID must not be null.");
        }
        if (newOwnerId == null) {
            throw new DomainValidationException("New owner ID must not be null.");
        }

        WorksheetDTO source = loadWorksheet(sourceWorksheetId);

        WorksheetDTO copy = copyWorksheetShell(source, newOwnerId);
        worksheetRepo.createWorksheet(copy);

        List<TieredTaskSequenceDTO> sourceSequences =
                source.getSequenceDTOS() == null ? List.of() : source.getSequenceDTOS();
        for (TieredTaskSequenceDTO reference : sourceSequences) {
            copySequenceInto(copy, reference.getId(), newOwnerId);
        }

        log.info("Copied worksheet {} -> {} for owner {} ({} sequences)",
                sourceWorksheetId, copy.getId(), newOwnerId, sourceSequences.size());

        return loadWorksheet(copy.getId());
    }

    // ─────────────────────────────────────────────────────────────────────

    /**
     * The worksheet row itself.
     *
     * <p>Named explicitly rather than via {@code toBuilder()}, unlike the sequences and tasks:
     * a worksheet has few fields and two of them — the sequence list and the owner — must not
     * be carried over, so an explicit builder reads more honestly here than a copy-then-reset.
     * The completeness test covers this class too, so an added field still surfaces.
     */
    private WorksheetDTO copyWorksheetShell(WorksheetDTO source, UUID newOwnerId) {
        return WorksheetDTO.builder()
                .name(source.getName())
                .description(source.getDescription())
                .ownerId(newOwnerId)
                .hintsEnabled(source.isHintsEnabled())
                .hintCreditsPerTask(source.getHintCreditsPerTask())
                // Never the source's visibility. Copying is how you take someone else's material
                // to adapt it, and a half-adapted worksheet appearing in the marketplace under
                // your name is not what pressing "copy" meant. Publishing it is a separate act.
                .visibility(WorksheetAccessPolicy.visibilityForCopy())
                .copiedFrom(source.getId())
                .copiedAt(Instant.now())
                .build();
    }

    /**
     * Copies one sequence, with its tasks, tags, concepts and summary, and links it to the
     * target worksheet.
     *
     * <p>The reference passed in from the worksheet carries only an id, so the sequence is
     * re-read in full first — otherwise the copy would be made from a shell and lose everything
     * the worksheet listing does not itself select.
     *
     * <p>Public because {@link SequenceForkService} needs exactly this when a curator takes a
     * private copy of someone else's sequence in order to edit it. Sharing the implementation
     * is the point: a fork that copied fewer fields than a worksheet copy would be the same
     * silent data loss this class was written to end, arriving through a second door.
     *
     * @return the id of the new sequence
     */
    @Transactional
    public UUID copySequenceInto(WorksheetDTO targetWorksheet, UUID sourceSequenceId, UUID newOwnerId) {
        TieredTaskSequenceDTO source = TieredTaskSequenceDTO.builder().id(sourceSequenceId).build();
        sequenceRepo.loadById(source);

        List<TaskDTO> sourceTasks = taskRepo.loadTasksBySequenceId(source);

        TieredTaskSequenceDTO sequenceCopy = source.copyFor(newOwnerId);
        sequenceRepo.save(sequenceCopy);
        UUID newSequenceId = sequenceCopy.getId();

        for (TaskDTO sourceTask : sourceTasks) {
            taskRepo.save(sourceTask.copyFor(newSequenceId));
        }

        copyConcepts(sourceSequenceId, newSequenceId);
        copyTags(sourceSequenceId, newSequenceId);
        copySemanticSummary(sourceSequenceId, newSequenceId);

        worksheetRepo.addSequenceToWorksheet(
                targetWorksheet,
                TieredTaskSequenceDTO.builder().id(newSequenceId).build());

        log.debug("Copied sequence {} -> {} ({} tasks)", sourceSequenceId, newSequenceId, sourceTasks.size());
        return newSequenceId;
    }

    /**
     * Concept links drive the structural tags and the concept display, and live in their own
     * junction table rather than on the sequence row, so they need copying separately.
     */
    private void copyConcepts(UUID sourceSequenceId, UUID newSequenceId) {
        List<UUID> conceptIds = sequenceRepo.findConceptIdsBySequenceId(sourceSequenceId);
        if (!conceptIds.isEmpty()) {
            sequenceRepo.linkConceptsBatch(newSequenceId, conceptIds);
        }
    }

    /**
     * Re-points each of the source's tags at the copy, preserving source and confidence so a
     * hand-corrected tag stays hand-corrected and the admin retag view still tells auto,
     * LLM and manual tags apart.
     */
    private void copyTags(UUID sourceSequenceId, UUID newSequenceId) {
        List<SequenceTagDTO> sourceTags = tagRepo.findBySequenceId(sourceSequenceId);
        if (sourceTags == null || sourceTags.isEmpty()) {
            return;
        }
        List<SequenceTagDTO> copies = new ArrayList<>(sourceTags.size());
        for (SequenceTagDTO tag : sourceTags) {
            copies.add(new SequenceTagDTO(newSequenceId, tag.getTagName(), tag.getSource(), tag.getConfidence()));
        }
        tagRepo.upsertAll(copies);
    }

    /** Carries the LLM one-liner over, so the copy is searchable on the same terms as its source. */
    private void copySemanticSummary(UUID sourceSequenceId, UUID newSequenceId) {
        String summary = sequenceRepo.findSemanticSummary(sourceSequenceId);
        if (summary != null && !summary.isBlank()) {
            sequenceRepo.updateSemanticSummary(newSequenceId, summary);
        }
    }

    /** {@link WorksheetRepository#getWorksheetById} fills the DTO it is handed rather than returning one. */
    private WorksheetDTO loadWorksheet(UUID id) {
        WorksheetDTO worksheet = WorksheetDTO.builder().id(id).build();
        worksheetRepo.getWorksheetById(worksheet);
        return worksheet;
    }
}
