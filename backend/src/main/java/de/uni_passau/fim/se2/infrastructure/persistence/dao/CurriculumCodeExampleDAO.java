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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.*;
import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumCodeExampleRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.CurriculumCodeExampleRowMapper;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public class CurriculumCodeExampleDAO implements CurriculumCodeExampleRepository {

    private static final CurriculumCodeExampleRowMapper ROW_MAPPER = new CurriculumCodeExampleRowMapper();
    private final JdbcTemplate jdbc;

    public CurriculumCodeExampleDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CurriculumCodeExampleDTO> findByTopicId(UUID topicId) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE topic_id = ? ORDER BY difficulty, title",
                ROW_MAPPER, topicId);
    }

    @Override
    public List<CurriculumCodeExampleDTO> findByTopicIdAndLanguage(UUID topicId, String language) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE topic_id = ? AND language = ? ORDER BY difficulty, title",
                ROW_MAPPER, topicId, language);
    }

    @Override
    public CurriculumCodeExampleDTO findById(UUID id) {
        return jdbc.query(
                "SELECT id, topic_id, language, title, code, problem_statement, difficulty, notes, created_at FROM curriculum_code_examples WHERE id = ?",
                ROW_MAPPER, id).stream().findFirst().orElse(null);
    }

    @Override
    public UUID insert(CurriculumCodeExampleDTO ex) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_code_examples (id, topic_id, language, title, code, problem_statement, difficulty, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, ex.getTopicId(), ex.getLanguage(), ex.getTitle(), ex.getCode(),
                ex.getProblemStatement(), ex.getDifficulty(), ex.getNotes());
        return id;
    }

    @Override
    public void update(CurriculumCodeExampleDTO ex) {
        int updated = jdbc.update(
                "UPDATE curriculum_code_examples SET language = ?, title = ?, code = ?, problem_statement = ?, difficulty = ?, notes = ? WHERE id = ?",
                ex.getLanguage(), ex.getTitle(), ex.getCode(), ex.getProblemStatement(),
                ex.getDifficulty(), ex.getNotes(), ex.getId());
        if (updated != 1) {
            throw new DomainValidationException("Curriculum example not found: " + ex.getId());
        }
    }

    @Override
    public void deleteById(UUID id) {
        jdbc.update("DELETE FROM curriculum_code_examples WHERE id = ?", id);
    }
}