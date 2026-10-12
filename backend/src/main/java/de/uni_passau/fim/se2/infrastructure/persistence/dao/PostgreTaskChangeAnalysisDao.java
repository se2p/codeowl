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

import de.uni_passau.fim.se2.domain.entities.TaskChangeAnalysisDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskChangeAnalysisRepository;
import org.postgresql.util.PGobject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgreTaskChangeAnalysisDao implements TaskChangeAnalysisRepository {

    private final JdbcTemplate jdbc;

    public PostgreTaskChangeAnalysisDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TaskChangeAnalysisDTO> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        TaskChangeAnalysisDTO dto = new TaskChangeAnalysisDTO();
        dto.setId(rs.getObject("id", UUID.class));
        dto.setTaskId(rs.getObject("task_id", UUID.class));
        dto.setTaskOriginalId(rs.getObject("task_original_id", UUID.class));
        dto.setAnalyzedAt(rs.getTimestamp("analyzed_at") != null
                ? rs.getTimestamp("analyzed_at").toLocalDateTime() : null);

        // Text-level metrics
        double textLev = rs.getDouble("text_levenshtein_ratio");
        dto.setTextLevenshteinRatio(rs.wasNull() ? null : textLev);
        double textJac = rs.getDouble("text_jaccard_similarity");
        dto.setTextJaccardSimilarity(rs.wasNull() ? null : textJac);
        int twa = rs.getInt("text_words_added");
        dto.setTextWordsAdded(rs.wasNull() ? null : twa);
        int twr = rs.getInt("text_words_removed");
        dto.setTextWordsRemoved(rs.wasNull() ? null : twr);
        int twm = rs.getInt("text_words_modified");
        dto.setTextWordsModified(rs.wasNull() ? null : twm);
        int tld = rs.getInt("text_length_delta");
        dto.setTextLengthDelta(rs.wasNull() ? null : tld);

        // Code-level metrics
        double codeLev = rs.getDouble("code_levenshtein_ratio");
        dto.setCodeLevenshteinRatio(rs.wasNull() ? null : codeLev);
        int cla = rs.getInt("code_lines_added");
        dto.setCodeLinesAdded(rs.wasNull() ? null : cla);
        int clr = rs.getInt("code_lines_removed");
        dto.setCodeLinesRemoved(rs.wasNull() ? null : clr);
        int clm = rs.getInt("code_lines_modified");
        dto.setCodeLinesModified(rs.wasNull() ? null : clm);
        int clre = rs.getInt("code_lines_reordered");
        dto.setCodeLinesReordered(rs.wasNull() ? null : clre);
        int cld = rs.getInt("code_loc_delta");
        dto.setCodeLocDelta(rs.wasNull() ? null : cld);
        int ccd = rs.getInt("code_complexity_delta");
        dto.setCodeComplexityDelta(rs.wasNull() ? null : ccd);
        double ctcr = rs.getDouble("code_token_change_ratio");
        dto.setCodeTokenChangeRatio(rs.wasNull() ? null : ctcr);

        // AST-level metrics
        int ana = rs.getInt("ast_nodes_added");
        dto.setAstNodesAdded(rs.wasNull() ? null : ana);
        int anr = rs.getInt("ast_nodes_removed");
        dto.setAstNodesRemoved(rs.wasNull() ? null : anr);
        int anm = rs.getInt("ast_nodes_modified");
        dto.setAstNodesModified(rs.wasNull() ? null : anm);
        double ass = rs.getDouble("ast_structural_score");
        dto.setAstStructuralScore(rs.wasNull() ? null : ass);
        dto.setAstChangeTypes(rs.getString("ast_change_types"));
        dto.setAstDistractorChanges(rs.getString("ast_distractor_changes"));

        // Overall
        double ocs = rs.getDouble("overall_change_score");
        dto.setOverallChangeScore(rs.wasNull() ? null : ocs);
        dto.setChangeCategory(rs.getString("change_category"));
        int tr = rs.getInt("teacher_rating");
        dto.setTeacherRating(rs.wasNull() ? null : tr);

        return dto;
    };

    @Override
    public void save(TaskChangeAnalysisDTO a) {
        UUID id = a.getId() != null ? a.getId() : UUID.randomUUID();
        a.setId(id);

        String sql = """
            INSERT INTO task_change_analyses (
                id, task_id, task_original_id,
                text_levenshtein_ratio, text_jaccard_similarity,
                text_words_added, text_words_removed, text_words_modified, text_length_delta,
                code_levenshtein_ratio,
                code_lines_added, code_lines_removed, code_lines_modified, code_lines_reordered,
                code_loc_delta, code_complexity_delta, code_token_change_ratio,
                ast_nodes_added, ast_nodes_removed, ast_nodes_modified,
                ast_structural_score, ast_change_types, ast_distractor_changes,
                overall_change_score, change_category, teacher_rating
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (task_id) DO UPDATE SET
                task_original_id        = EXCLUDED.task_original_id,
                analyzed_at             = CURRENT_TIMESTAMP,
                text_levenshtein_ratio  = EXCLUDED.text_levenshtein_ratio,
                text_jaccard_similarity = EXCLUDED.text_jaccard_similarity,
                text_words_added        = EXCLUDED.text_words_added,
                text_words_removed      = EXCLUDED.text_words_removed,
                text_words_modified     = EXCLUDED.text_words_modified,
                text_length_delta       = EXCLUDED.text_length_delta,
                code_levenshtein_ratio  = EXCLUDED.code_levenshtein_ratio,
                code_lines_added        = EXCLUDED.code_lines_added,
                code_lines_removed      = EXCLUDED.code_lines_removed,
                code_lines_modified     = EXCLUDED.code_lines_modified,
                code_lines_reordered    = EXCLUDED.code_lines_reordered,
                code_loc_delta          = EXCLUDED.code_loc_delta,
                code_complexity_delta   = EXCLUDED.code_complexity_delta,
                code_token_change_ratio = EXCLUDED.code_token_change_ratio,
                ast_nodes_added         = EXCLUDED.ast_nodes_added,
                ast_nodes_removed       = EXCLUDED.ast_nodes_removed,
                ast_nodes_modified      = EXCLUDED.ast_nodes_modified,
                ast_structural_score    = EXCLUDED.ast_structural_score,
                ast_change_types        = EXCLUDED.ast_change_types,
                ast_distractor_changes  = EXCLUDED.ast_distractor_changes,
                overall_change_score    = EXCLUDED.overall_change_score,
                change_category         = EXCLUDED.change_category,
                teacher_rating          = EXCLUDED.teacher_rating
        """;

        jdbc.update(sql,
                id,
                a.getTaskId(),
                a.getTaskOriginalId(),
                a.getTextLevenshteinRatio(),
                a.getTextJaccardSimilarity(),
                a.getTextWordsAdded(),
                a.getTextWordsRemoved(),
                a.getTextWordsModified(),
                a.getTextLengthDelta(),
                a.getCodeLevenshteinRatio(),
                a.getCodeLinesAdded(),
                a.getCodeLinesRemoved(),
                a.getCodeLinesModified(),
                a.getCodeLinesReordered(),
                a.getCodeLocDelta(),
                a.getCodeComplexityDelta(),
                a.getCodeTokenChangeRatio(),
                a.getAstNodesAdded(),
                a.getAstNodesRemoved(),
                a.getAstNodesModified(),
                a.getAstStructuralScore(),
                toJsonb(a.getAstChangeTypes()),
                toJsonb(a.getAstDistractorChanges()),
                a.getOverallChangeScore(),
                a.getChangeCategory(),
                a.getTeacherRating()
        );
    }

    @Override
    public Optional<TaskChangeAnalysisDTO> findByTaskId(UUID taskId) {
        String sql = "SELECT * FROM task_change_analyses WHERE task_id = ?";
        try {
            TaskChangeAnalysisDTO result = jdbc.queryForObject(sql, ROW_MAPPER, taskId);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskChangeAnalysisDTO> findAll() {
        String sql = "SELECT * FROM task_change_analyses ORDER BY analyzed_at DESC";
        return jdbc.query(sql, ROW_MAPPER);
    }

    @Override
    public boolean existsByTaskId(UUID taskId) {
        String sql = "SELECT COUNT(*) FROM task_change_analyses WHERE task_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, taskId);
        return count != null && count > 0;
    }

    @Override
    public void deleteByTaskId(UUID taskId) {
        jdbc.update("DELETE FROM task_change_analyses WHERE task_id = ?", taskId);
    }

    // ── Helper ──

    private static PGobject toJsonb(String value) {
        if (value == null) return null;
        try {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(value);
            return jsonb;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create JSONB PGobject", e);
        }
    }
}
