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

import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the arrangement a student had when they solved a task, so reopening it
 * shows their work instead of a reshuffled puzzle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskSubmissionService {

    private final TaskSubmissionRepository repository;
    private final WorksheetAccessService worksheetAccessService;

    /**
     * Stores a solved arrangement.
     *
     * Anonymous solvers are skipped rather than rejected: a task can be opened
     * through a shared link without an account, and there is nobody to store the
     * work against. They keep the behaviour they have always had.
     */
    @Transactional
    public void saveSolved(UUID userId, UUID taskId, List<String> blockOrder, List<Integer> indentLevels) {
        if (taskId == null) {
            throw new DomainValidationException("taskId is required");
        }
        if (userId == null) {
            log.debug("Anonymous solve of task {}, nothing to store", taskId);
            return;
        }
        // Recording a solve on a closed worksheet would let a client that ignores
        // the read-only UI carry on regardless.
        worksheetAccessService.requireSolvable(taskId);

        if (blockOrder == null || blockOrder.isEmpty()) {
            // Nothing worth restoring, and storing an empty arrangement would
            // make the task look solved-but-blank on the next visit.
            throw new DomainValidationException("A solved submission needs at least one block.");
        }

        store(userId, taskId, blockOrder, indentLevels, true);
    }

    /**
     * Stores work in progress, so closing the tab or stepping away from the page does not
     * cost the student their arrangement.
     *
     * Unlike {@link #saveSolved}, an empty arrangement is accepted: a student who has pulled
     * every block back out of the solution area is in a real state, and refusing to record it
     * would leave the previous one to be restored over the top of their actual work.
     *
     * Anonymous solvers are skipped for the same reason as above -- no account, nothing to
     * store the work against.
     */
    @Transactional
    public void saveProgress(UUID userId, UUID taskId, List<String> blockOrder, List<Integer> indentLevels) {
        if (taskId == null) {
            throw new DomainValidationException("taskId is required");
        }
        if (userId == null) {
            log.debug("Anonymous work on task {}, nothing to store", taskId);
            return;
        }
        // Autosaving against a closed worksheet would let a client that ignores the
        // read-only UI carry on regardless, exactly as a solve would.
        worksheetAccessService.requireSolvable(taskId);

        store(userId, taskId, blockOrder, indentLevels, false);
    }

    private void store(UUID userId, UUID taskId, List<String> blockOrder,
                       List<Integer> indentLevels, boolean solved) {
        repository.save(TaskSubmissionDTO.builder()
                .userId(userId)
                .taskId(taskId)
                .blockOrder(blockOrder != null ? blockOrder : List.of())
                .indentLevels(indentLevels != null ? indentLevels : List.of())
                .solved(solved)
                .build());
    }

    /**
     * Throws away a student's stored arrangement, so the task deals a fresh puzzle next time.
     *
     * This is the student discarding their own work on purpose, which is why it is allowed on
     * a closed worksheet: there is nothing to gain by forcing them to keep it.
     */
    @Transactional
    public void discard(UUID userId, UUID taskId) {
        if (taskId == null) {
            throw new DomainValidationException("taskId is required");
        }
        if (userId == null) return;
        repository.delete(userId, taskId);
    }

    @Transactional(readOnly = true)
    public Optional<TaskSubmissionDTO> find(UUID userId, UUID taskId) {
        if (userId == null || taskId == null) return Optional.empty();
        return repository.find(userId, taskId);
    }
}
