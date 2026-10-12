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
 * Represents a data transfer object that associates a programming concept
 * with a specific tiered task sequence.
 * <p>
 * Each instance holds a unique identifier for a concept and a unique identifier
 * for a tiered task sequence. This DTO is commonly used to model the relationship
 * between task sequences and the concepts they cover.
 */
@Data
public class TieredTaskSequenceConceptDTO {

    /**
     * The unique identifier for a programming concept.
     * This identifier is used to associate a specific programming concept
     * with a tiered task sequence in the context of modeling relationships
     * between tasks and the concepts they cover.
     */
    UUID conceptId;

    /**
     * Represents the unique identifier for a specific tiered task sequence.
     * This identifier is used to associate a particular sequence of tasks with related programming concepts.
     */
    UUID sequenceId;
}