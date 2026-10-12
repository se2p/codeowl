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

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single tag on a TieredTaskSequence (AP19 Teil A).
 *
 * <p>Tags have three sources, stored in the {@code source} column:
 * <ul>
 *   <li>{@code "auto"} &mdash; structural, derived from AP25 Concept Detection (for-loop, recursion, …)</li>
 *   <li>{@code "llm"} &mdash; semantic, derived from an LLM analysis (fibonacci, binary-search, …)</li>
 *   <li>{@code "manual"} &mdash; teacher-set, must never be overwritten by auto-retag</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class SequenceTagDTO {
    private UUID sequenceId;
    private String tagName;
    /** One of "auto", "llm", "manual". */
    private String source;
    /** Only populated for LLM-generated tags (0.0-1.0). */
    private Float confidence;
    private LocalDateTime createdAt;

    public SequenceTagDTO(UUID sequenceId, String tagName, String source) {
        this.sequenceId = sequenceId;
        this.tagName = tagName;
        this.source = source;
    }

    public SequenceTagDTO(UUID sequenceId, String tagName, String source, Float confidence) {
        this.sequenceId = sequenceId;
        this.tagName = tagName;
        this.source = source;
        this.confidence = confidence;
    }
}
