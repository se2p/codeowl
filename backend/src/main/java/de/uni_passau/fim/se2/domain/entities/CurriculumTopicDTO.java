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
import java.util.UUID;

@Data
@NoArgsConstructor
public class CurriculumTopicDTO {
    private UUID id;
    private String name;
    private String description;
    // AP19: Curriculum integration fields
    private Integer gradeLevel;
    private String track;
    private String learningArea;
    private UUID parentId;
    private int sortOrder;
    // English translations (11_migration_curriculum_i18n.sql). German is the
    // source language; these are overrides and may be null, in which case
    // consumers fall back to the German column.
    private String nameEn;
    private String descriptionEn;
    private String trackEn;
    /**
     * SCHOOL (Jahrgangsstufen) or UNIVERSITY (modules), from
     * 12_migration_curriculum_education_level.sql. Sub-topics repeat their
     * parent's value, so a row can be classified without looking at its parent.
     */
    private String educationLevel;
    /**
     * Concept names mapped to this topic through concept_curriculum_topic —
     * a relation, not a column, so the row mappers never fill it and it stays
     * null on an ordinary read. Admin requests carry it to set the mapping:
     * null leaves the mapping alone, a list replaces it (an empty one clears
     * it). Read it back through /api/curriculum/topics/{id}/concepts.
     */
    private java.util.List<String> concepts;
    /**
     * How many sequences match this topic's mapped concepts (any of them) — the same set "View
     * sequences" on the Curriculum page opens the Library filtered to. Computed alongside {@link
     * #concepts}, not stored on the row, so it defaults to 0 and is only ever non-zero when a
     * caller explicitly asked for it (currently {@code CurriculumGenerationService.getSubTopics}).
     */
    private int sequenceCount;
}