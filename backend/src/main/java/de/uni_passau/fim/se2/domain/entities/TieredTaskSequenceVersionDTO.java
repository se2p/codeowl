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

import java.util.*;

/**
 * Represents a data transfer object (DTO) that links a version
 * to a tiered task sequence.
 *
 * This class is typically used to manage the relationship between
 * a specific version of a task sequence and the sequence itself.
 */
@Data
public class TieredTaskSequenceVersionDTO {

    /**
     * The unique identifier for a version in the context of a tiered task sequence.
     * This ID is used to associate a specific version of a task sequence with its related data.
     */
    UUID versionId;

    /**
     * Represents the unique identifier of a tiered task sequence
     * associated with a specific version in the context of mapping
     * versioned sequences to their corresponding tasks.
     */
    UUID sequenceId;
}