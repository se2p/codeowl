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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import java.util.List;
import java.util.UUID;

public interface CurriculumCodeExampleRepository {
    List<CurriculumCodeExampleDTO> findByTopicId(UUID topicId);
    List<CurriculumCodeExampleDTO> findByTopicIdAndLanguage(UUID topicId, String language);

    /** AP32 admin: load a single example by id, or {@code null} if none. */
    CurriculumCodeExampleDTO findById(UUID id);
    /** AP32 admin: insert a new example; returns the generated id. */
    UUID insert(CurriculumCodeExampleDTO example);
    /** AP32 admin: update an existing example (by {@code example.id}). */
    void update(CurriculumCodeExampleDTO example);
    /** AP32 admin: delete an example by id. */
    void deleteById(UUID id);
}
