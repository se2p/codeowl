/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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
 * with a curriculum topic.
 *
 * This class is used to establish a relationship between a specific concept
 * and its corresponding curriculum topic within the system.
 */
@Data
public class ConceptCurriculumTopicDTO {

    /**
     * The unique identifier for a programming concept.
     *
     * This identifier is used to associate a specific programming concept
     * with its corresponding curriculum topic in the context of determining
     * the relationship between conceptual knowledge and its placement
     * within the learning structure.
     */
    UUID conceptId;

    /**
     * The unique identifier for the curriculum topic associated with a programming concept.
     *
     * This identifier is used to establish a link between a specific programming concept
     * and its corresponding curriculum topic within the system.
     */
    UUID topicId;
}