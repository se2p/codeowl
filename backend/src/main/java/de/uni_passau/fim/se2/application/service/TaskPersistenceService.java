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

import de.uni_passau.fim.se2.application.events.SequenceGeneratedEvent;
import de.uni_passau.fim.se2.domain.entities.*;

import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(TaskPersistenceService.class);

    private final TaskRepository taskRepo;

    private final TieredTaskSequenceRepository sequenceRepo;

    private final ConceptRepository conceptRepo;

    /** AP19 Teil A: for synchronous structural auto-tagging. */
    private final SequenceTagService sequenceTagService;

    /** AP19 Teil A: for async semantic LLM tagging via {@link SequenceGeneratedEvent}. */
    private final ApplicationEventPublisher eventPublisher;

    public TaskPersistenceService(TaskRepository taskRepository, TieredTaskSequenceRepository sequenceRepo,
                                  ConceptRepository conceptRepository,
                                  SequenceTagService sequenceTagService,
                                  ApplicationEventPublisher eventPublisher) {
        this.taskRepo = taskRepository;
        this.sequenceRepo = sequenceRepo;
        this.conceptRepo = conceptRepository;
        this.sequenceTagService = sequenceTagService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Persists a newly generated task after validation.
     *
     * @param task the generated task
     */
    @Transactional
    public void saveGeneratedTask(TaskDTO task) {
        try {
            logger.debug("Saving generated task: {}", task);
            validateTask(task);
            taskRepo.save(task);
            logger.debug("Successfully saved task with ID: {}", task.getId());
        } catch (Exception e) {
            logger.error("Error saving generated task: {}", task, e);
            throw e;
        }
    }

    /**
     * Persists a list of generated tasks after validating each.
     *
     * @param tasks the list of tasks to save
     */
    private void saveAllGeneratedTasks(TieredTaskSequenceDTO sequence) {
        try {
            logger.debug("Saving all tasks for sequence: {}", sequence.getId());
            if (sequence == null) {
                throw new DomainValidationException("Sequence must not be null.");
            }
            List<TaskDTO> tasks = sequence.getTasks();
            if (tasks == null || tasks.isEmpty()) {
                throw new DomainValidationException("Task list must not be null or empty.");
            }

            for (TaskDTO task : tasks) {
                validateTask(task);
                task.setSequenceId(sequence.getId());
            }

            taskRepo.saveAll(sequence);
            logger.debug("Successfully saved {} tasks", tasks.size());
        } catch (Exception e) {
            logger.error("Error saving tasks for sequence: {}", sequence.getId(), e);
            throw e;
        }
    }

    /**
     * Loads a task by its ID.
     *
     * @param id the task ID
     * @return the loaded task
     */
    @Transactional(readOnly = true)
    public TaskDTO loadTaskById(UUID id) {
        try {
            logger.info("Loading task with ID: {}", id);
            if (id == null) {
                throw new DomainValidationException("Task ID must not be null.");
            }

            TaskDTO task = new TaskDTO();
            TieredTaskSequenceDTO sequenceDTO = new TieredTaskSequenceDTO();
            task.setId(id);
            taskRepo.loadById(task);
            sequenceDTO.setId(task.getSequenceId());
            sequenceRepo.loadById(sequenceDTO);
            task.setProgrammingLanguage(sequenceDTO.getProgrammingLanguage());
            logger.debug("Successfully loaded task: {}", task);
            return task;
        } catch (Exception e) {
            logger.error("Error loading task with ID: {}", id, e);
            throw e;
        }
    }

    /**
     * Deletes a task by its ID.
     *
     * @param id the task ID
     */
    @Transactional
    public void deleteTask(UUID id) {
        try {
            logger.debug("Deleting task with ID: {}", id);
            if (id == null) {
                throw new DomainValidationException("Task ID must not be null.");
            }

            TaskDTO task = new TaskDTO();
            task.setId(id);
            taskRepo.deleteById(task);
            logger.debug("Successfully deleted task with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error deleting task with ID: {}", id, e);
            throw e;
        }
    }

    @Transactional
    public TieredTaskSequenceDTO loadSequenceById(UUID id) {
        logger.info("Loading sequence with ID: {}", id);
        if (id == null) {
            throw new DomainValidationException("Sequence ID must not be null.");
        }
        TieredTaskSequenceDTO sequence = new TieredTaskSequenceDTO();
        sequence.setId(id);
        sequenceRepo.loadById(sequence);
        getTasksForSequence(sequence);

        return sequence;
    }

    /**
     * Replaces a sequence's concepts with the given names, and returns them as
     * stored. Names are resolved against the catalogue and created when absent,
     * the same rule the curriculum learning-area form follows;
     * {@code PostgreConceptDAO} lowercases them on the way in, so what comes
     * back may differ in case from what was sent.
     *
     * <p>An empty set is refused rather than accepted: the persistence path
     * has always required a sequence to carry at least one concept, and letting
     * an edit strip the last one would leave a sequence that could never have
     * been created that way.
     *
     * @param sequenceId the sequence to edit
     * @param names      the concept names that should remain
     * @return the sequence's concept names after the change, sorted
     */
    @Transactional
    public List<String> updateSequenceConcepts(UUID sequenceId, List<String> names) {
        if (sequenceId == null) {
            throw new DomainValidationException("Sequence ID must not be null.");
        }
        List<String> cleaned = names == null ? List.of() : names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            throw new DomainValidationException("Sequence concepts must not be empty.");
        }

        List<ConceptDTO> dtos = cleaned.stream()
                .map(name -> {
                    ConceptDTO dto = new ConceptDTO();
                    dto.setName(name);
                    return dto;
                })
                .toList();

        sequenceRepo.replaceConcepts(sequenceId, conceptRepo.saveAll(dtos));
        sequenceRepo.markDiverged(sequenceId);

        TieredTaskSequenceDTO reloaded = new TieredTaskSequenceDTO();
        reloaded.setId(sequenceId);
        sequenceRepo.loadById(reloaded);
        return reloaded.getConcepts();
    }

    /** Longest task description accepted, room for a few paragraphs. */
    public static final int MAX_TASK_DESCRIPTION_LENGTH = 5000;

    /**
     * Replaces a sequence's task description. A blank one clears it.
     *
     * @return the stored description, trimmed, or {@code null} when cleared
     */
    @Transactional
    public String updateTaskDescription(UUID sequenceId, String taskDescription) {
        if (sequenceId == null) {
            throw new DomainValidationException("Sequence ID must not be null.");
        }
        String normalized = (taskDescription == null || taskDescription.isBlank()) ? null : taskDescription.strip();
        if (normalized != null && normalized.length() > MAX_TASK_DESCRIPTION_LENGTH) {
            throw new DomainValidationException(
                    "Task description must be at most " + MAX_TASK_DESCRIPTION_LENGTH + " characters.");
        }
        sequenceRepo.updateTaskDescription(sequenceId, normalized);
        sequenceRepo.markDiverged(sequenceId);
        return normalized;
    }

    /** Longest sequence name accepted; same bound as a task name. */
    public static final int MAX_SEQUENCE_TITLE_LENGTH = 120;

    /**
     * Renames a sequence. A blank name clears it, so the sequence shows its default heading
     * again.
     *
     * @return the stored name, trimmed, or {@code null} when cleared
     */
    @Transactional
    public String updateSequenceTitle(UUID sequenceId, String title) {
        if (sequenceId == null) {
            throw new DomainValidationException("Sequence ID must not be null.");
        }
        String normalized = (title == null || title.isBlank()) ? null : title.strip();
        if (normalized != null && normalized.length() > MAX_SEQUENCE_TITLE_LENGTH) {
            throw new DomainValidationException(
                    "Sequence name must be at most " + MAX_SEQUENCE_TITLE_LENGTH + " characters.");
        }
        sequenceRepo.updateTitle(sequenceId, normalized);
        sequenceRepo.markDiverged(sequenceId);
        return normalized;
    }

    /**
     * Loads all tasks for the given sequence.
     *
     * @param sequence the task sequence
     * @return the list of associated tasks
     */
    @Transactional(readOnly = true)
    public void getTasksForSequence(TieredTaskSequenceDTO sequence) {
        try {
            logger.info("Loading tasks for sequence: {}", sequence.getId());
            if (sequence == null || sequence.getId() == null) {
                throw new DomainValidationException("Sequence ID must not be null.");
            }

            List<TaskDTO> tasks = taskRepo.loadTasksBySequenceId(sequence);
            for (TaskDTO task : tasks) {
                logger.debug("Loaded task: {}", task);
                logger.debug("Setting programming language for task: {}", sequence.getProgrammingLanguage());
                task.setProgrammingLanguage(sequence.getProgrammingLanguage());
            }
            logger.debug("Loaded {} tasks for sequence {}", tasks.size(), sequence.getId());
            sequence.setTasks(tasks);
        } catch (Exception e) {
            logger.error("Error loading tasks for sequence: {}", sequence.getId(), e);
            throw e;
        }
    }

    private void validateTask(TaskDTO task) {
        try {
            logger.trace("Validating task: {}", task);
            if (task == null) {
                throw new DomainValidationException("Task must not be null.");
            }

            if (isBlank(task.getStatement())) {
                throw new DomainValidationException("Task statement must not be empty.");
            }

            if (isBlank(task.getSolution())) {
                throw new DomainValidationException("Task solution must not be empty.");
            }

            if (task.getTierLevel() < 1 || task.getTierLevel() > 5) {
                throw new DomainValidationException("Tier level must be between 1 and 5.");
            }

            if (task.getCyclomaticComplexity() < 0) {
                throw new DomainValidationException("Cyclomatic complexity must not be negative.");
            }
            logger.trace("Task validation successful");
        } catch (Exception e) {
            logger.error("Error validating task: {}", task, e);
            throw e;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Transactional
    public void persistSequence(TieredTaskSequenceDTO sequence) {
        try {
            logger.debug("Persisting sequence: {}", sequence);
            saveSequence(sequence);
            saveAllGeneratedTasks(sequence);
            linkConcepts(sequence);
            logger.debug("Successfully persisted sequence with ID: {}", sequence.getId());

            // AP19 Teil A: structural auto-tagging (synchronous, ~50ms DB INSERT).
            // Errors are caught inside SequenceTagService — persistence is authoritative.
            sequenceTagService.autoTagStructural(sequence.getId(), sequence.getConcepts());

            // AP19 Teil A: fire event for async LLM semantic tagging (3–10s background work).
            try {
                eventPublisher.publishEvent(buildGeneratedEvent(sequence));
            } catch (Exception evtEx) {
                // Event publish should never fail the caller, but defend just in case.
                logger.warn("Failed to publish SequenceGeneratedEvent for {}: {}",
                        sequence.getId(), evtEx.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error persisting sequence: {}", sequence.getId(), e);
            throw e;
        }
    }

    /**
     * AP19 Teil A: assembles the payload for {@link SequenceGeneratedEvent}.
     * Concatenates statements and code across all tiers so the LLM tagger sees the full picture.
     */
    private SequenceGeneratedEvent buildGeneratedEvent(TieredTaskSequenceDTO sequence) {
        List<TaskDTO> tasks = sequence.getTasks() == null ? List.of() : sequence.getTasks();

        String combinedStatement = tasks.stream()
                .map(t -> t.getStatement() == null ? "" : t.getStatement())
                .collect(Collectors.joining("\n---\n"));

        String combinedCode = tasks.stream()
                .map(t -> t.getSolution() == null ? "" : t.getSolution())
                .collect(Collectors.joining("\n\n// ─── next tier ───\n\n"));

        return new SequenceGeneratedEvent(
                sequence.getId(),
                sequence.getProgrammingLanguage(),
                combinedStatement,
                combinedCode);
    }

    /**
     * OPT-6: Batched concept linking.
     * Was: 2 round-trips per concept (INSERT + SELECT) + 1 round-trip per link = 3N round-trips.
     * Now: 1 batch INSERT + 1 WHERE-IN SELECT + 1 batch link INSERT = 3 round-trips regardless of N.
     */
    private void linkConcepts(TieredTaskSequenceDTO sequence) {
        try {
            logger.debug("Linking concepts for sequence: {}", sequence.getId());
            List<String> conceptNames = sequence.getConcepts();
            if (conceptNames == null || conceptNames.isEmpty()) {
                throw new DomainValidationException("Sequence concepts must not be empty.");
            }

            List<ConceptDTO> dtos = conceptNames.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> {
                        ConceptDTO d = new ConceptDTO();
                        d.setName(n);
                        return d;
                    })
                    .toList();

            List<UUID> conceptIds = conceptRepo.saveAll(dtos);
            sequenceRepo.linkConceptsBatch(sequence.getId(), conceptIds);

            logger.debug("Successfully linked {} concepts (batched)", conceptIds.size());
        } catch (Exception e) {
            logger.error("Error linking concepts for sequence: {}", sequence.getId(), e);
            throw e;
        }
    }

    private void saveSequence(TieredTaskSequenceDTO sequence) {
        try {
            logger.debug("Saving sequence: {}", sequence);
            validateSequence(sequence);
            sequenceRepo.save(sequence);
            logger.debug("Successfully saved sequence with ID: {}", sequence.getId());
        } catch (Exception e) {
            logger.error("Error saving sequence: {}", sequence.getId(), e);
            throw e;
        }
    }

    private void validateSequence(TieredTaskSequenceDTO sequence) {
        try {
            logger.trace("Validating sequence: {}", sequence);
            if (sequence == null) {
                throw new DomainValidationException("Sequence must not be null.");
            }
            if (isBlank(sequence.getProgrammingLanguage())) {
                throw new DomainValidationException("Sequence programming language must not be empty.");
            }
            if (isBlank(sequence.getTaskDescription())) {
                throw new DomainValidationException("Sequence task description must not be empty.");
            }
            if (isBlank(sequence.getCodeSnippet())) {
                throw new DomainValidationException("Sequence code snippet must not be empty.");
            }
            if (sequence.getConcepts() == null || sequence.getConcepts().isEmpty()) {
                throw new DomainValidationException("Sequence concepts must not be empty.");
            }
            if (sequence.getVersionId() == null) {
                throw new DomainValidationException("Sequence version must not be null.");
            }
            logger.trace("Sequence validation successful");
        } catch (Exception e) {
            logger.error("Error validating sequence: {}", sequence, e);
            throw e;
        }
    }

    /**
     * AP15b: Update only the distractors column for an existing task.
     */
    @Transactional
    public void updateDistractors(UUID taskId, String distractors) {
        if (taskId == null) throw new DomainValidationException("Task ID must not be null.");
        taskRepo.updateDistractors(taskId, distractors);
        sequenceRepo.markDivergedByTaskId(taskId);
    }

    /** Longest task name accepted: room for a descriptive heading, short enough to stay one. */
    public static final int MAX_TITLE_LENGTH = 120;

    /**
     * Renames a task. A blank name clears it, so the task shows its numbered default again.
     *
     * @return the stored name, trimmed, or {@code null} when cleared
     */
    @Transactional
    public String updateTitle(UUID taskId, String title) {
        if (taskId == null) throw new DomainValidationException("Task ID must not be null.");
        String normalized = (title == null || title.isBlank()) ? null : title.strip();
        if (normalized != null && normalized.length() > MAX_TITLE_LENGTH) {
            throw new DomainValidationException(
                    "Task name must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        taskRepo.updateTitle(taskId, normalized);
        sequenceRepo.markDivergedByTaskId(taskId);
        return normalized;
    }

    /**
     * AP15: Toggle distractors enabled/disabled for a task.
     */
    @Transactional
    public void updateDistractorsEnabled(UUID taskId, boolean enabled) {
        if (taskId == null) throw new DomainValidationException("Task ID must not be null.");
        taskRepo.updateDistractorsEnabled(taskId, enabled);
        sequenceRepo.markDivergedByTaskId(taskId);
    }

    public void tagSequence(UUID sequenceId, UUID evalInputId, String mode, UUID runId) {
        sequenceRepo.tagSequence(sequenceId, evalInputId, mode, runId);
    }

    /**
     * Finds all sequences owned by a specific user.
     */
    @Transactional(readOnly = true)
    public List<TieredTaskSequenceDTO> findSequencesByOwnerId(UUID ownerId) {
        return sequenceRepo.findByOwnerId(ownerId);
    }

    /** Removes a sequence from the library and the owner's list; worksheets keep it. */
    @Transactional
    public boolean deleteSequence(UUID sequenceId) {
        return sequenceRepo.softDeleteById(sequenceId);
    }
}