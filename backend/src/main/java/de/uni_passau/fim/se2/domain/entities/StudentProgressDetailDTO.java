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

import java.util.List;
import java.util.UUID;

/**
 * One student's work across every worksheet assigned to one class: every task
 * they could have attempted, how many times they clicked "Feedback" on it,
 * and whether one of those clicks was correct.
 *
 * <p>Built from {@code feedback_logs} rather than {@code learning_events} so
 * its numbers agree with the class-wide progress table teachers already see
 * on the class page — both are derived from the same feedback-click rows.
 */
public record StudentProgressDetailDTO(
        UUID studentId,
        String studentName,
        List<WorksheetProgress> worksheets
) {
    public record WorksheetProgress(
            UUID worksheetId,
            String name,
            List<SequenceProgress> sequences
    ) {}

    /** {@code index} is the sequence's 1-based position in the worksheet — sequences have no name of their own. */
    public record SequenceProgress(
            UUID sequenceId,
            int index,
            String programmingLanguage,
            List<String> concepts,
            String taskDescription,
            List<TaskProgress> tasks
    ) {}

    public record TaskProgress(
            UUID taskId,
            int tierLevel,
            String statement,
            int attempts,
            int correctAttempts,
            boolean solved
    ) {}
}
