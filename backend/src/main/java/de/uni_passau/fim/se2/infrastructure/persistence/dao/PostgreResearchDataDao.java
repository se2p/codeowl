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

import de.uni_passau.fim.se2.domain.interfaces.ResearchDataReader;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PostgreResearchDataDao implements ResearchDataReader {

    private final JdbcTemplate jdbc;

    @Override
    public List<Map<String, Object>> readUsersPseudonymized() {
        // Whitelist columns explicitly — NEVER SELECT * from users.
        return jdbc.queryForList(
                "SELECT user_id, role, is_verified, created_at FROM users ORDER BY created_at"
        );
    }

    @Override
    public List<Map<String, Object>> readLearningEvents() {
        return jdbc.queryForList("SELECT * FROM learning_events ORDER BY created_at");
    }

    @Override
    public List<Map<String, Object>> readFeedbackLogs() {
        return jdbc.queryForList("SELECT * FROM feedback_logs ORDER BY clicked_at");
    }

    @Override
    public List<Map<String, Object>> readTaskSubmissions() {
        return jdbc.queryForList("SELECT * FROM task_submissions ORDER BY updated_at");
    }

    @Override
    public List<Map<String, Object>> readHintUsage() {
        return jdbc.queryForList("SELECT * FROM hint_usage ORDER BY created_at");
    }

    @Override
    public List<Map<String, Object>> readTaskChangeAnalyses() {
        return jdbc.queryForList("SELECT * FROM task_change_analyses");
    }

    @Override
    public List<Map<String, Object>> readProblemStatementRatings() {
        return jdbc.queryForList("SELECT * FROM problem_statement_ratings");
    }

    @Override
    public List<Map<String, Object>> readTasks() {
        return jdbc.queryForList("SELECT * FROM tasks");
    }

    @Override
    public List<Map<String, Object>> readTieredTaskSequences() {
        return jdbc.queryForList("SELECT * FROM tiered_task_sequences");
    }

    @Override
    public List<Map<String, Object>> readWorksheetsSequence() {
        return jdbc.queryForList("SELECT * FROM worksheets_sequence");
    }

    @Override
    public List<Map<String, Object>> readConcepts() {
        return jdbc.queryForList("SELECT * FROM concepts");
    }

    @Override
    public List<Map<String, Object>> readSequenceConceptJunction() {
        return jdbc.queryForList("SELECT * FROM tiered_task_sequence_concepts");
    }

    @Override
    public List<Map<String, Object>> readWorksheets() {
        return jdbc.queryForList("SELECT * FROM worksheets");
    }

    @Override
    public List<Map<String, Object>> readClasses() {
        return jdbc.queryForList("SELECT * FROM classes");
    }

    @Override
    public List<Map<String, Object>> readClassStudents() {
        return jdbc.queryForList("SELECT * FROM class_students");
    }

    @Override
    public List<Map<String, Object>> readClassStudents(java.util.UUID classId) {
        return jdbc.queryForList(
                "SELECT * FROM class_students WHERE class_id = ? ORDER BY joined_at", classId);
    }

    @Override
    public List<Map<String, Object>> readClassWorksheets() {
        return jdbc.queryForList("SELECT * FROM class_worksheets");
    }

    @Override
    public List<Map<String, Object>> readSequenceTags() {
        return jdbc.queryForList("SELECT * FROM sequence_tags");
    }

    @Override
    public List<Map<String, Object>> readCurriculumTopics() {
        return jdbc.queryForList("SELECT * FROM curriculum_topics");
    }

    @Override
    public List<Map<String, Object>> readCurriculumCodeExamples() {
        return jdbc.queryForList("SELECT * FROM curriculum_code_examples");
    }

    @Override
    public List<Map<String, Object>> readConceptCurriculumTopicJunction() {
        return jdbc.queryForList("SELECT * FROM concept_curriculum_topic");
    }
}
