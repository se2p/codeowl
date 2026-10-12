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
package de.uni_passau.fim.se2.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A student's arrangement of one task, kept so that reopening the task shows their
 * work rather than a reshuffled puzzle -- whether they solved it or merely started.
 *
 * This is UI state, not research data. The record of how the student got there
 * lives in learning_events, which nulls user_id for classes with tracking
 * disabled and therefore cannot be used to restore a particular student's work.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmissionDTO {

    private UUID userId;

    private UUID taskId;

    /** Visible text of each block in the solution area, top to bottom. */
    private List<String> blockOrder;

    /** Indent level of each block, positionally aligned with {@link #blockOrder}. */
    private List<Integer> indentLevels;

    /**
     * Whether this arrangement is a solved one, as opposed to work in progress.
     *
     * Sticky: once true it stays true, because a student who solves a task and then pulls
     * it apart has still solved it. It is also what keeps "who has solved this task" from
     * counting students who merely opened it.
     */
    private boolean solved;

    /** When the task was solved, or null while the arrangement is still in progress. */
    private LocalDateTime solvedAt;

    private LocalDateTime updatedAt;
}
