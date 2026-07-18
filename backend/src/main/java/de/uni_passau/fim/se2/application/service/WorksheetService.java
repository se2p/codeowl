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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorksheetService {

    private final WorksheetRepository worksheetRepo;
    private final TieredTaskSequenceRepository sequenceRepo;
    private final TaskRepository taskRepo;

    @Transactional
    public void createWorksheet(WorksheetDTO worksheet) {
        validateWorksheetForCreate(worksheet);
        worksheetRepo.createWorksheet(worksheet);
        log.debug("Created worksheet: {}", worksheet.getId());
    }

    @Transactional
    public void updateWorksheet(WorksheetDTO worksheet) {
        validateWorksheetId(worksheet);
        worksheetRepo.updateWorksheet(worksheet);
        log.debug("Updated worksheet id={}", worksheet.getId());
    }

    @Transactional
    public void deleteWorksheet(UUID id) {
        if (id == null) {
            throw new DomainValidationException("Worksheet ID must not be null.");
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(id).build();
        worksheetRepo.deleteWorksheet(worksheet);
        log.debug("Deleted worksheet id={}", id);
    }

    @Transactional(readOnly = true)
    public WorksheetDTO loadWorksheetById(UUID id) {
        if (id == null) {
            throw new DomainValidationException("Worksheet ID must not be null.");
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(id).build();
        worksheetRepo.getWorksheetById(worksheet);
        return worksheet;
    }

    @Transactional(readOnly = true)
    public List<WorksheetDTO> findByOwnerId(UUID ownerId) {
        return worksheetRepo.findByOwnerId(ownerId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // AP19 Teil A (A12): "Library Inbox" default worksheet
    // ─────────────────────────────────────────────────────────────────────

    /**
     * A sentinel embedded in the description field of the auto-created "Library Inbox" worksheet.
     * Lookup is done by sentinel (not name) so a teacher can rename the worksheet freely without
     * breaking the one-click-copy behavior.
     */
    public static final String LIBRARY_INBOX_SENTINEL = "[codeowl:library-inbox]";

    private static final String LIBRARY_INBOX_DEFAULT_NAME = "Library Inbox";
    private static final String LIBRARY_INBOX_DEFAULT_DESC =
            "Quick-copied sequences from the Library. " + LIBRARY_INBOX_SENTINEL;

    /**
     * Returns the teacher's "Library Inbox" worksheet, creating it on first access.
     *
     * <p>The inbox is identified by a sentinel embedded in the description rather than by name,
     * so renaming the worksheet does not orphan the copy target. If a teacher deletes the inbox,
     * the next one-click copy creates a fresh one.
     */
    @Transactional
    public WorksheetDTO getOrCreateDefaultWorksheet(UUID ownerId) {
        if (ownerId == null) {
            throw new DomainValidationException("Owner ID must not be null.");
        }

        Optional<WorksheetDTO> existing = worksheetRepo.findByOwnerId(ownerId).stream()
                .filter(w -> w.getDescription() != null
                        && w.getDescription().contains(LIBRARY_INBOX_SENTINEL))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        WorksheetDTO fresh = WorksheetDTO.builder()
                .name(LIBRARY_INBOX_DEFAULT_NAME)
                .description(LIBRARY_INBOX_DEFAULT_DESC)
                .ownerId(ownerId)
                .build();
        worksheetRepo.createWorksheet(fresh);
        log.info("Created Library Inbox worksheet {} for owner {}", fresh.getId(), ownerId);
        return fresh;
    }

    @Transactional
    public void addSequenceToWorksheet(UUID worksheetId, UUID sequenceId) {
        if (worksheetId == null || sequenceId == null) {
            throw new DomainValidationException("Worksheet and sequence IDs must not be null.");
        }
        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder().id(sequenceId).build();
        worksheetRepo.addSequenceToWorksheet(worksheet, sequence);
        log.debug("Linked sequence id={} to worksheet id={}", sequenceId, worksheetId);
    }

    @Transactional
    public void addSequencesToWorksheet(UUID worksheetId, List<UUID> sequenceIds) {
        if (worksheetId == null) {
            throw new DomainValidationException("Worksheet ID must not be null.");
        }
        if (sequenceIds == null || sequenceIds.isEmpty()) {
            throw new DomainValidationException("Sequence list must not be null or empty.");
        }

        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        List<TieredTaskSequenceDTO> sequences = sequenceIds.stream()
                .map(id -> TieredTaskSequenceDTO.builder().id(id).build())
                .toList();

        worksheetRepo.addSequencesToWorksheet(worksheet, sequences);
        log.debug("Linked {} sequences to worksheet id={}", sequenceIds.size(), worksheetId);
    }

    @Transactional
    public void removeSequenceFromWorksheet(UUID worksheetId, UUID sequenceId) {
        if (worksheetId == null || sequenceId == null) {
            throw new DomainValidationException("Worksheet and sequence IDs must not be null.");
        }

        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder().id(sequenceId).build();
        worksheetRepo.removeSequenceFromWorksheet(worksheet, sequence);
        log.debug("Unlinked sequence id={} from worksheet id={}", sequenceId, worksheetId);
    }

    /**
     * Deep-copies a worksheet including all sequences and tasks.
     * The copy gets new UUIDs for everything and the specified newOwnerId.
     */
    @Transactional
    public WorksheetDTO copyWorksheet(UUID sourceWorksheetId, UUID newOwnerId) {
        if (sourceWorksheetId == null) {
            throw new DomainValidationException("Source worksheet ID must not be null.");
        }
        if (newOwnerId == null) {
            throw new DomainValidationException("New owner ID must not be null.");
        }

        // 1. Load the source worksheet
        WorksheetDTO source = loadWorksheetById(sourceWorksheetId);
        if (source == null) {
            throw new DomainValidationException("Source worksheet not found: " + sourceWorksheetId);
        }

        // 2. Create the new worksheet (copy metadata)
        WorksheetDTO copy = WorksheetDTO.builder()
                .name(source.getName())
                .description(source.getDescription())
                .ownerId(newOwnerId)
                .build();
        worksheetRepo.createWorksheet(copy);
        log.info("Copied worksheet {} -> {} for owner {}", sourceWorksheetId, copy.getId(), newOwnerId);

        // 3. Deep-copy each sequence and its tasks
        if (source.getSequenceDTOS() != null) {
            for (TieredTaskSequenceDTO seqRef : source.getSequenceDTOS()) {
                TieredTaskSequenceDTO sourceSeq = TieredTaskSequenceDTO.builder().id(seqRef.getId()).build();
                sequenceRepo.loadById(sourceSeq);

                List<TaskDTO> sourceTasks = taskRepo.loadTasksBySequenceId(sourceSeq);

                TieredTaskSequenceDTO seqCopy = TieredTaskSequenceDTO.builder()
                        .programmingLanguage(sourceSeq.getProgrammingLanguage())
                        .codeSnippet(sourceSeq.getCodeSnippet())
                        .taskDescription(sourceSeq.getTaskDescription())
                        .versionId(sourceSeq.getVersionId())
                        .strategyName(sourceSeq.getStrategyName())
                        .ownerId(newOwnerId)
                        .build();
                sequenceRepo.save(seqCopy);
                log.debug("Copied sequence {} -> {}", seqRef.getId(), seqCopy.getId());

                for (TaskDTO sourceTask : sourceTasks) {
                    TaskDTO taskCopy = TaskDTO.builder()
                            .statement(sourceTask.getStatement())
                            .solution(sourceTask.getSolution())
                            .tierLevel(sourceTask.getTierLevel())
                            .cyclomaticComplexity(sourceTask.getCyclomaticComplexity())
                            .linesOfCode(sourceTask.getLinesOfCode())
                            .maximalNestingDepth(sourceTask.getMaximalNestingDepth())
                            .methodCallCount(sourceTask.getMethodCallCount())
                            .distinctIdentifierCount(sourceTask.getDistinctIdentifierCount())
                            .sequenceId(seqCopy.getId())
                            .build();
                    taskRepo.save(taskCopy);
                }

                worksheetRepo.addSequenceToWorksheet(copy,
                        TieredTaskSequenceDTO.builder().id(seqCopy.getId()).build());
            }
        }

        // 4. Reload and return the complete copy
        return loadWorksheetById(copy.getId());
    }

    private void validateWorksheetForCreate(WorksheetDTO worksheet) {
        if (worksheet == null) {
            throw new DomainValidationException("Worksheet must not be null.");
        }
        if (isBlank(worksheet.getName())) {
            throw new DomainValidationException("Worksheet name must not be empty.");
        }
        if (isBlank(worksheet.getDescription())) {
            throw new DomainValidationException("Worksheet description must not be empty.");
        }
    }

    private void validateWorksheetId(WorksheetDTO worksheet) {
        if (worksheet == null || worksheet.getId() == null) {
            throw new DomainValidationException("Worksheet ID must not be null.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
