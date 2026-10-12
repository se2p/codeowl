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
package de.uni_passau.fim.se2.api.dto;

import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request body for PUT /api/tasks/{taskId} (AP9).
 */
@Getter
@Setter
public class TaskEditRequest {
    private String problemStatement;
    private String code;

    /**
     * Required on first edit only (1-5).
     * Rates how much adjustment the generated content needed.
     */
    private Integer adjustmentRating;

    /** AP15: Optional distractor lines (newline-separated, each ending with #distractor). */
    private String distractors;

    /**
     * AP31: Optional inline-split entries. When non-null the split plan is replaced with
     * these (an empty list clears it); when null the existing split plan is left untouched.
     * Each entry must reconstruct its {@code code} line (prefix + join(fragments) + suffix).
     */
    private List<SplitPlan.SplitEntry> splits;
}
