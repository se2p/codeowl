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

import de.uni_passau.fim.se2.api.dto.TaskEditRequest;
import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.application.service.analysis.ChangeAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for editing tasks (AP9 — Task-Bearbeitung & Versionierung).
 * On the first edit, the original version is saved to task_originals.
 * Subsequent edits update the task directly without creating another original.
 */
@Service
public class TaskEditService {

    private static final Logger logger = LoggerFactory.getLogger(TaskEditService.class);

    private final TaskRepository taskRepo;
    private final TaskOriginalRepository taskOriginalRepo;
    private final TieredTaskSequenceRepository sequenceRepo;
    private final OwnershipService ownershipService;
    private final ChangeAnalysisService changeAnalysisService;
    private final ObjectMapper objectMapper;

    public TaskEditService(TaskRepository taskRepo,
                           TaskOriginalRepository taskOriginalRepo,
                           TieredTaskSequenceRepository sequenceRepo,
                           OwnershipService ownershipService,
                           ChangeAnalysisService changeAnalysisService,
                           ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.taskOriginalRepo = taskOriginalRepo;
        this.sequenceRepo = sequenceRepo;
        this.ownershipService = ownershipService;
        this.changeAnalysisService = changeAnalysisService;
        this.objectMapper = objectMapper;
    }

    /**
     * Edits a task's problem statement and/or code.
     * <ol>
     *   <li>Loads the current task</li>
     *   <li>Checks ownership (task -> sequence -> owner_id) or admin</li>
     *   <li>If no task_original exists yet, saves the current version as original</li>
     *   <li>Updates the task with new values</li>
     * </ol>
     *
     * @param taskId  the task to edit
     * @param request the edit payload
     * @return the updated TaskDTO
     */
    @Transactional
    public TaskDTO editTask(UUID taskId, TaskEditRequest request) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID must not be null.");
        }
        if (request.getProblemStatement() == null || request.getProblemStatement().isBlank()) {
            throw new DomainValidationException("Problem statement must not be empty.");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new DomainValidationException("Code must not be empty.");
        }

        // 1. Load current task
        TaskDTO task = new TaskDTO();
        task.setId(taskId);
        taskRepo.loadById(task);

        // 2. Check ownership via sequence -> owner_id
        UUID ownerId = sequenceRepo.findOwnerIdByTaskId(taskId);
        ownershipService.checkOwnership(ownerId, "task");

        // 3. If first edit, save original before overwriting
        boolean isFirstEdit = !taskOriginalRepo.existsByTaskId(taskId);
        if (isFirstEdit) {
            if (request.getAdjustmentRating() == null) {
                throw new DomainValidationException(
                        "adjustmentRating is required on the first edit (1-5).");
            }
            if (request.getAdjustmentRating() < 1 || request.getAdjustmentRating() > 5) {
                throw new DomainValidationException(
                        "adjustmentRating must be between 1 and 5.");
            }

            TaskOriginalDTO original = TaskOriginalDTO.builder()
                    .taskId(taskId)
                    .originalProblemStatement(task.getStatement())
                    .originalCode(task.getSolution())
                    .adjustmentRating(request.getAdjustmentRating())
                    .build();
            taskOriginalRepo.save(original);
            logger.info("Saved original for task {} (rating={})", taskId, request.getAdjustmentRating());
        }

        // 4. Update the task
        taskRepo.updateStatementAndSolution(taskId, request.getProblemStatement(), request.getCode());

        // AP15: Update distractors if provided in the request
        if (request.getDistractors() != null) {
            String distractors = request.getDistractors().isBlank() ? null : request.getDistractors();
            taskRepo.updateDistractors(taskId, distractors);
        }

        // AP31: Update the inline-split plan when provided. An empty list clears it;
        // null leaves the existing plan untouched.
        if (request.getSplits() != null) {
            String splitPlanJson = buildSplitPlanJson(request.getSplits(), request.getCode());
            taskRepo.updateSplitPlan(taskId, splitPlanJson);
        }

        logger.info("Updated task {} (firstEdit={})", taskId, isFirstEdit);

        // 5. Run change analysis automatically
        try {
            changeAnalysisService.analyzeTask(taskId);
            logger.info("Change analysis completed for task {}", taskId);
        } catch (Exception e) {
            logger.warn("Change analysis failed for task {} (non-blocking): {}", taskId, e.getMessage());
        }

        // Reload and return
        TaskDTO updated = new TaskDTO();
        updated.setId(taskId);
        taskRepo.loadById(updated);
        return updated;
    }

    /**
     * AP31: Turns the edited split entries into a validated {@code split_plan} JSON string.
     * Returns {@code null} when there are no splits (clearing the plan). Throws
     * {@link DomainValidationException} if any split fails the reconstruction check against
     * {@code solution}. Mirrors {@code ManualTaskService#buildSplitPlanJson}.
     */
    private String buildSplitPlanJson(java.util.List<SplitPlan.SplitEntry> splits, String solution) {
        if (splits == null || splits.isEmpty()) {
            return null;
        }
        SplitPlan plan = SplitPlan.builder().splits(splits).build();
        SequenceValidator.validateSplitPlan(plan, solution);
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize split_plan for task edit: {}", e.getMessage());
            return null;
        }
    }
}
