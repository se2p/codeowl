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

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the change-analysis results for a task (AP12 — Change-Analysis-Engine).
 * One analysis per task, comparing current version against the original.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskChangeAnalysisDTO {

    private UUID id;
    private UUID taskId;
    private UUID taskOriginalId;
    private LocalDateTime analyzedAt;

    // ── Text-level metrics ──
    private Double textLevenshteinRatio;
    private Double textJaccardSimilarity;
    private Integer textWordsAdded;
    private Integer textWordsRemoved;
    private Integer textWordsModified;
    private Integer textLengthDelta;

    // ── Code-level metrics ──
    private Double codeLevenshteinRatio;
    private Integer codeLinesAdded;
    private Integer codeLinesRemoved;
    private Integer codeLinesModified;
    private Integer codeLinesReordered;
    private Integer codeLocDelta;
    private Integer codeComplexityDelta;
    private Double codeTokenChangeRatio;

    // ── AST-level metrics ──
    private Integer astNodesAdded;
    private Integer astNodesRemoved;
    private Integer astNodesModified;
    private Double astStructuralScore;
    private String astChangeTypes;       // JSONB stored as String
    private String astDistractorChanges; // JSONB stored as String

    // ── Overall ──
    private Double overallChangeScore;
    private String changeCategory;
    private Integer teacherRating;
}
