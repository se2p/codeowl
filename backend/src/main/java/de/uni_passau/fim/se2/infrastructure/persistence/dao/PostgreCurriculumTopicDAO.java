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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumTopicRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.CurriculumTopicRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class PostgreCurriculumTopicDAO implements CurriculumTopicRepository {

    private static final CurriculumTopicRowMapper ROW_MAPPER = new CurriculumTopicRowMapper();

    /** Every column CurriculumTopicRowMapper reads — keep the two in step. */
    private static final String[] COLUMN_NAMES = {
        "id", "name", "description", "grade_level", "track", "learning_area", "parent_id", "sort_order",
        "name_en", "description_en", "track_en", "education_level"
    };

    static final String COLUMNS = String.join(", ", COLUMN_NAMES);

    /** The same columns qualified with a table alias, for joins. */
    static String columns(String alias) {
        return Arrays.stream(COLUMN_NAMES).map(c -> alias + "." + c).collect(Collectors.joining(", "));
    }

    private final JdbcTemplate jdbc;

    public PostgreCurriculumTopicDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(CurriculumTopicDTO topic) {
        jdbc.update("""
            INSERT INTO curriculum_topics (id, name, description, grade_level, track, learning_area, parent_id, sort_order,
                                           name_en, description_en, track_en, education_level)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                grade_level = EXCLUDED.grade_level, track = EXCLUDED.track,
                learning_area = EXCLUDED.learning_area, parent_id = EXCLUDED.parent_id,
                sort_order = EXCLUDED.sort_order, name_en = EXCLUDED.name_en,
                description_en = EXCLUDED.description_en, track_en = EXCLUDED.track_en,
                education_level = EXCLUDED.education_level
            """,
            topic.getId(), topic.getName(), topic.getDescription(),
            topic.getGradeLevel(), topic.getTrack(), topic.getLearningArea(),
            topic.getParentId(), topic.getSortOrder(),
            topic.getNameEn(), topic.getDescriptionEn(), topic.getTrackEn(),
            topic.getEducationLevel());
    }

    @Override
    public List<CurriculumTopicDTO> findAll() {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM curriculum_topics ORDER BY sort_order, name",
            ROW_MAPPER);
    }

    @Override
    public Optional<CurriculumTopicDTO> findById(UUID id) {
        var results = jdbc.query(
            "SELECT " + COLUMNS + " FROM curriculum_topics WHERE id = ?",
            ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<CurriculumTopicDTO> findTopLevel() {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM curriculum_topics WHERE parent_id IS NULL ORDER BY sort_order, name",
            ROW_MAPPER);
    }

    @Override
    public List<CurriculumTopicDTO> findByParentId(UUID parentId) {
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM curriculum_topics WHERE parent_id = ? ORDER BY sort_order, name",
            ROW_MAPPER, parentId);
    }

    @Override
    public void delete(CurriculumTopicDTO topic) {
        jdbc.update("DELETE FROM curriculum_topics WHERE id = ?", topic.getId());
    }

    @Override
    public Map<UUID, Integer> countSequencesByTopicIds(Collection<UUID> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = new ArrayList<>(topicIds);
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT cct.topic_id AS topic_id, COUNT(DISTINCT tsc.sequence_id) AS cnt
                FROM concept_curriculum_topic cct
                JOIN tiered_task_sequence_concepts tsc ON tsc.concept_id = cct.concept_id
                JOIN tiered_task_sequences s ON s.id = tsc.sequence_id
                WHERE cct.topic_id IN (""" + placeholders + """
                )
                  AND (s.copied_from IS NULL OR s.diverged_at IS NOT NULL)
                  AND s.deleted_at IS NULL
                GROUP BY cct.topic_id
                """;
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.query(sql, rs -> {
            counts.put((UUID) rs.getObject("topic_id"), rs.getInt("cnt"));
        }, ids.toArray());
        return counts;
    }
}
