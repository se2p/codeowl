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
 * Data transfer object representing a worksheet within the system.
 * A worksheet acts as a collection of tiered task sequences, typically used
 * to group related programming exercises or assignments under a common theme.
 * <p>
 * Each worksheet contains a unique identifier, a descriptive name and text,
 * and an associated list of {@link TieredTaskSequenceDTO} objects.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorksheetDTO {

    /**
     * The unique identifier of the worksheet.
     * Used to distinguish worksheets in persistence and during repository operations.
     */
    private UUID id;

    /**
     * The display name of the worksheet.
     * This value is not required to be unique and can be shared among multiple users.
     */
    private String name;

    /**
     * A brief description providing context or details about
     * the worksheet’s purpose or content.
     */
    private String description;

    /**
     * The list of tiered task sequences associated with this worksheet.
     * Each sequence represents a grouped set of tiered programming tasks.
     */
    @Builder.Default
    private List<TieredTaskSequenceDTO> sequenceDTOS = new ArrayList<>();

    /**
     * The owner (teacher/admin) who created this worksheet.
     * NULL for legacy/anonymous worksheets.
     */
    private UUID ownerId;

    /**
     * Whether hints are enabled for tasks in this worksheet (AP13).
     */
    @Builder.Default
    private boolean hintsEnabled = true;
}