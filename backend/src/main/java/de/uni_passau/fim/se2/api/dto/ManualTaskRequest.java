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
import lombok.*;

import java.util.List;

/**
 * Request body for POST /tasks/manual — creating a task sequence without LLM.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManualTaskRequest {

    /** Programming language (required). */
    private String language;

    /** Concepts covered by this sequence (required, at least one). */
    private List<String> concepts;

    /** The individual tier tasks (1–3 entries expected). */
    private List<ManualTierEntry> tiers;

    /**
     * One tier inside a manual sequence.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualTierEntry {
        /** Tier level (1, 2, or 3). */
        private int tierLevel;
        /** Problem statement / instructions for this tier. */
        private String statement;
        /** The code solution (the lines that become Parsons blocks). */
        private String solution;
        /**
         * AP31: optional inline-split entries for this tier (one per split line).
         * Each must reconstruct its solution line (prefix + join(fragments) + suffix).
         */
        private List<SplitPlan.SplitEntry> splits;
    }
}
