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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What one student last built for one task, for a teacher to look at, next to the solution they
 * were building towards.
 *
 * <p>The arrangement comes from {@code task_submissions}, which the solve page autosaves as the
 * student works. That is a different source from the numbers on the progress page around it:
 * those count Feedback clicks, so a student can have {@code attempts = 0} here and still have
 * left a substantial arrangement behind. Showing this is the point of the feature.
 *
 * <p>Returned even when nothing is stored -- {@link #hasState} is then false and the block lists
 * are empty, while the statement and solution still come back, so the dialog can say "nothing
 * saved yet" rather than failing.
 *
 * <p>Sending the solution to the teacher is not new exposure: {@code GET /api/tasks/{id}} already
 * ships it to every solver, because the Parsons widget builds the puzzle out of it client-side.
 * It is still a value no student may read through this endpoint, which is why the service checks
 * class ownership before building one of these.
 *
 * <p>Not recorded, and so not shown: values typed into gap inputs (AP30) and the placement of
 * inline-split chips (AP31). Both contribute no {@code textContent}, which is what the saved
 * arrangement is made of.
 */
public record StudentTaskStateDTO(
        UUID taskId,
        String statement,
        String programmingLanguage,
        /** The expected solution, with its line annotations intact; the client strips for display. */
        String solution,
        /** False when this student has never arranged anything for this task. */
        boolean hasState,
        List<String> blockOrder,
        List<Integer> indentLevels,
        boolean solved,
        LocalDateTime solvedAt,
        LocalDateTime updatedAt
) {}
