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

import java.util.List;
import java.util.Map;

/**
 * Read-only access to research-relevant tables.
 * Each method returns a list of column-name → value maps.
 * Implementations MUST exclude PII columns from the users table.
 */
public interface ResearchDataReader {
    /** users: only user_id, role, is_verified, created_at — NO email/name/hash. */
    List<Map<String, Object>> readUsersPseudonymized();

    List<Map<String, Object>> readLearningEvents();
    List<Map<String, Object>> readFeedbackLogs();
    List<Map<String, Object>> readHintUsage();
    /** The students' saved block arrangements — UI state, but the only record of what they built. */
    List<Map<String, Object>> readTaskSubmissions();
    List<Map<String, Object>> readTaskChangeAnalyses();
    List<Map<String, Object>> readProblemStatementRatings();
    List<Map<String, Object>> readTasks();
    List<Map<String, Object>> readTieredTaskSequences();
    /** worksheets_sequence junction table (worksheet_id, sequence_id, last_used_at). */
    List<Map<String, Object>> readWorksheetsSequence();
    List<Map<String, Object>> readConcepts();
    List<Map<String, Object>> readSequenceConceptJunction();
    List<Map<String, Object>> readWorksheets();
    List<Map<String, Object>> readClasses();
    List<Map<String, Object>> readClassStudents();
    /** Roster of one class, for the per-class export bundle. */
    List<Map<String, Object>> readClassStudents(java.util.UUID classId);
    List<Map<String, Object>> readClassWorksheets();
    List<Map<String, Object>> readSequenceTags();
    List<Map<String, Object>> readCurriculumTopics();
    List<Map<String, Object>> readCurriculumCodeExamples();
    List<Map<String, Object>> readConceptCurriculumTopicJunction();
}
