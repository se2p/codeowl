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

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.*;

@Repository
public class PostgreSequenceDAO implements TieredTaskSequenceRepository {


    private static final TieredTaskSequenceRowMapper SEQ_ROW_MAPPER = new TieredTaskSequenceRowMapper();
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PostgreSequenceDAO(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void loadById(TieredTaskSequenceDTO target) {
        if (target == null || target.getId() == null) {
            throw new DomainValidationException("Sequence and ID cannot be null");
        }
        String sql = """
                         SELECT id, programming_language, code_snippet, created_at, task_description, title,
                                version_id, strategyname, owner_id, target_language, constraint_config, tier_count,
                                curriculum_topic_id, copied_from, diverged_at
                         FROM tiered_task_sequences
                         WHERE id = ?
                     """;
        try {
            TieredTaskSequenceDTO db = jdbc.queryForObject(sql, SEQ_ROW_MAPPER, target.getId());
            if (db != null) {
                target.setProgrammingLanguage(db.getProgrammingLanguage());
                target.setCodeSnippet(db.getCodeSnippet());
                target.setCreatedAt(db.getCreatedAt());
                target.setTaskDescription(db.getTaskDescription());
                target.setTitle(db.getTitle());
                target.setVersionId(db.getVersionId());
                target.setStrategyName(db.getStrategyName());
                target.setOwnerId(db.getOwnerId());
                target.setTargetLanguage(db.getTargetLanguage());
                target.setConstraints(db.getConstraints());
                target.setTierCount(db.getTierCount());
                target.setCurriculumTopicId(db.getCurriculumTopicId());
                target.setCopiedFrom(db.getCopiedFrom());
                target.setDivergedAt(db.getDivergedAt());
                target.setConcepts(loadConceptNames(target.getId()));
            }
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Sequence not found: " + target.getId());
        }
    }

    /**
     * Concepts live in a junction table, so they need their own read. Save has
     * always written them; nothing read them back, which is why every sequence
     * arrived at the client with an empty concept list and the page had nothing
     * to show.
     *
     * <p>Deliberately not done in {@link #loadAll()} or {@link #findByOwnerId}:
     * those back list screens that show tags rather than concepts, and a query
     * per row would be an N+1 for something nobody displays there.
     */
    private List<String> loadConceptNames(UUID sequenceId) {
        return jdbc.queryForList("""
                SELECT c.name
                FROM tiered_task_sequence_concepts link
                JOIN concepts c ON c.id = link.concept_id
                WHERE link.sequence_id = ?
                ORDER BY c.name
                """, String.class, sequenceId);
    }

    @Override
    public List<TieredTaskSequenceDTO> loadAll() {
        String sql = """
                         SELECT id, programming_language, code_snippet, created_at, task_description, title,
                                version_id, strategyname, owner_id, target_language, constraint_config, tier_count,
                                curriculum_topic_id
                         FROM tiered_task_sequences
                         ORDER BY created_at DESC, id
                     """;
        return jdbc.query(sql, SEQ_ROW_MAPPER);
    }

    @Override
    public void save(TieredTaskSequenceDTO sequence) {
        if (sequence == null) {
            throw new DomainValidationException("Sequence cannot be null");
        }
        if (sequence.getProgrammingLanguage() == null || sequence.getProgrammingLanguage().isBlank()) {
            throw new DomainValidationException("programming_language is required");
        }
        if (sequence.getVersionId() == null) {
            throw new DomainValidationException("version_id is required");
        }

        // id erzeugen, falls nicht gesetzt
        UUID id = sequence.getId() != null ? sequence.getId() : UUID.randomUUID();
        sequence.setId(id);

        // created_at lassen wir von der DB setzen (DEFAULT)
        String sql = """
                         INSERT INTO tiered_task_sequences
                             (id, programming_language, code_snippet, task_description, version_id, strategyname, owner_id, target_language, constraint_config, tier_count, curriculum_topic_id, copied_from, title)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """;
        jdbc.update(sql,
                    id,
                    sequence.getProgrammingLanguage(),
                    sequence.getCodeSnippet(),
                    sequence.getTaskDescription(),
                    sequence.getVersionId(),
                    sequence.getStrategyName(),
                    sequence.getOwnerId(),
                    sequence.getTargetLanguage(),
                    toJsonb(sequence.getConstraints()),
                    sequence.getTierCount(),
                    sequence.getCurriculumTopicId(),
                    sequence.getCopiedFrom(),
                    sequence.getTitle()
        );
    }

    @Override
    public void deleteById(TieredTaskSequenceDTO delete) {
        if (delete == null || delete.getId() == null) {
            throw new DomainValidationException("Sequence and ID cannot be null");
        }
        String sql = "DELETE FROM tiered_task_sequences WHERE id = ?";
        jdbc.update(sql, delete.getId());
    }

    @Override
    public boolean softDeleteById(UUID sequenceId) {
        if (sequenceId == null) return false;
        return jdbc.update("""
                UPDATE tiered_task_sequences
                   SET deleted_at = now()
                 WHERE id = ? AND deleted_at IS NULL
                """, sequenceId) > 0;
    }

    @Override
    public void linkConcept(TieredTaskSequenceConceptDTO linkDTO) {
        if (linkDTO == null || linkDTO.getSequenceId() == null || linkDTO.getConceptId() == null) {
            throw new DomainValidationException("Sequence and Concept ID cannot be null");
        }
        String sql = """
                         INSERT INTO tiered_task_sequence_concepts (sequence_id, concept_id)
                         VALUES (?, ?)
                     """;
        jdbc.update(sql, linkDTO.getSequenceId(), linkDTO.getConceptId());
    }

    /**
     * OPT-6: Single batch-INSERT to replace N individual link inserts.
     */
    @Override
    public void linkConceptsBatch(UUID sequenceId, List<UUID> conceptIds) {
        if (sequenceId == null) throw new DomainValidationException("Sequence ID cannot be null");
        if (conceptIds == null || conceptIds.isEmpty()) return;

        // ON CONFLICT DO NOTHING in case the PK is (sequence_id, concept_id) — makes the call idempotent.
        final String sql = """
                         INSERT INTO tiered_task_sequence_concepts (sequence_id, concept_id)
                         VALUES (?, ?)
                         ON CONFLICT DO NOTHING
                     """;
        jdbc.batchUpdate(sql, new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                ps.setObject(1, sequenceId);
                ps.setObject(2, conceptIds.get(i));
            }

            @Override
            public int getBatchSize() {
                return conceptIds.size();
            }
        });
    }

    /**
     * Replaces a sequence's concept links wholesale. Editing concepts is a
     * "here is the new set" operation rather than add/remove one at a time, so
     * the delete and the insert belong in one call — and in one transaction, or
     * a failed insert would leave the sequence with no concepts at all.
     */
    @Override
    @Transactional
    public void replaceConcepts(UUID sequenceId, List<UUID> conceptIds) {
        if (sequenceId == null) throw new DomainValidationException("Sequence ID cannot be null");

        jdbc.update("DELETE FROM tiered_task_sequence_concepts WHERE sequence_id = ?", sequenceId);
        linkConceptsBatch(sequenceId, conceptIds);
    }

    @Override
    public void updateTaskDescription(UUID sequenceId, String taskDescription) {
        if (sequenceId == null) throw new DomainValidationException("Sequence ID cannot be null");

        int updated = jdbc.update("UPDATE tiered_task_sequences SET task_description = ? WHERE id = ?",
                                  taskDescription, sequenceId);
        if (updated != 1) {
            throw new DomainValidationException("Sequence not found: " + sequenceId);
        }
    }

    @Override
    public void updateTitle(UUID sequenceId, String title) {
        if (sequenceId == null) throw new DomainValidationException("Sequence ID cannot be null");

        int updated = jdbc.update("UPDATE tiered_task_sequences SET title = ? WHERE id = ?",
                                  title, sequenceId);
        if (updated != 1) {
            throw new DomainValidationException("Sequence not found: " + sequenceId);
        }
    }

    @Override
    public void tagSequence(UUID sequenceId, UUID evalInputId, String mode, UUID runId) {
        if (sequenceId == null) throw new DomainValidationException("sequenceId is required");
        if (runId == null)      throw new DomainValidationException("runId is required");
        if (evalInputId == null)throw new DomainValidationException("evalInputId is required");
        if (mode == null || mode.isBlank())
            throw new DomainValidationException("input_mode is required (full|code|meta)");

        final String m = mode.toLowerCase(Locale.ROOT);
        if (!m.equals("full") && !m.equals("code") && !m.equals("meta")) {
            throw new DomainValidationException("invalid input_mode: " + mode + " (allowed: full, code, meta)");
        }

        // Setzt den Tag + verankert die Sequenz am Run (version_id)
        final String sql = """
        UPDATE tiered_task_sequences
           SET source_eval_input_id = ?,
               input_mode            = ?,
               version_id            = ?
         WHERE id = ?
        """;
        try {
            int updated = jdbc.update(sql, evalInputId, m, runId, sequenceId);
            if (updated != 1) {
                throw new IllegalArgumentException("Sequence not found or not updated: " + sequenceId);
            }
        } catch (DataIntegrityViolationException e) {
            // z.B. UNIQUE-Verletzung (version_id, source_eval_input_id, input_mode)
            throw new DomainValidationException("Tagging failed (likely duplicate per run & mode): " + e.getMessage());
        }
    }

    @Override
    public UUID findOwnerIdByTaskId(UUID taskId) {
        String sql = """
            SELECT s.owner_id
            FROM tiered_task_sequences s
            JOIN tasks t ON t.sequence_id = s.id
            WHERE t.id = ?
        """;
        try {
            return jdbc.queryForObject(sql, UUID.class, taskId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<TieredTaskSequenceDTO> findByOwnerId(UUID ownerId) {
        String sql = """
            SELECT id, programming_language, code_snippet, created_at, task_description, title,
                   version_id, strategyname, owner_id, target_language, constraint_config, tier_count,
                   curriculum_topic_id
            FROM tiered_task_sequences
            WHERE owner_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC, id DESC
            """;

        return jdbc.query(sql, (rs, rowNum) -> {
            var ts = rs.getTimestamp("created_at");
            return TieredTaskSequenceDTO.builder()
                    .id(rs.getObject("id", UUID.class))
                    .programmingLanguage(rs.getString("programming_language"))
                    .codeSnippet(rs.getString("code_snippet"))
                    .createdAt(ts != null ? ts.toLocalDateTime() : null)
                    .taskDescription(rs.getString("task_description"))
                    .title(rs.getString("title"))
                    .versionId(rs.getObject("version_id", UUID.class))
                    .strategyName(rs.getString("strategyname"))
                    .ownerId(rs.getObject("owner_id", UUID.class))
                    .targetLanguage(rs.getString("target_language"))
                    .constraints(fromJsonb(rs.getString("constraint_config")))
                    .tierCount(rs.getInt("tier_count") > 0 ? rs.getInt("tier_count") : 3)
                    .curriculumTopicId(rs.getObject("curriculum_topic_id", UUID.class))
                    .build();
        }, ownerId);
    }

    @Override
    public boolean markDiverged(UUID sequenceId) {
        if (sequenceId == null) return false;
        // copied_from IS NOT NULL: an original is already listed, and stamping one would claim
        // it came from somewhere. diverged_at IS NULL: keep the first edit, not the latest.
        int updated = jdbc.update("""
                UPDATE tiered_task_sequences
                   SET diverged_at = now()
                 WHERE id = ?
                   AND copied_from IS NOT NULL
                   AND diverged_at IS NULL
                """, sequenceId);
        return updated == 1;
    }

    @Override
    public boolean markDivergedByTaskId(UUID taskId) {
        if (taskId == null) return false;
        int updated = jdbc.update("""
                UPDATE tiered_task_sequences s
                   SET diverged_at = now()
                  FROM tasks t
                 WHERE t.id = ?
                   AND s.id = t.sequence_id
                   AND s.copied_from IS NOT NULL
                   AND s.diverged_at IS NULL
                """, taskId);
        return updated == 1;
    }

    @Override
    public void updateSemanticSummary(UUID sequenceId, String summary) {
        if (sequenceId == null) return;
        String trimmed = summary == null ? null : summary.substring(0, Math.min(summary.length(), 500));
        jdbc.update("UPDATE tiered_task_sequences SET semantic_summary = ? WHERE id = ?",
                trimmed, sequenceId);
    }

    @Override
    public String findSemanticSummary(UUID sequenceId) {
        if (sequenceId == null) return null;
        // queryForList rather than queryForObject: an unknown id is an ordinary outcome for a
        // copy operation, not an exception worth unwinding a transaction for.
        List<String> rows = jdbc.queryForList(
                "SELECT semantic_summary FROM tiered_task_sequences WHERE id = ?",
                String.class, sequenceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<UUID> findConceptIdsBySequenceId(UUID sequenceId) {
        if (sequenceId == null) return List.of();
        return jdbc.queryForList(
                "SELECT concept_id FROM tiered_task_sequence_concepts WHERE sequence_id = ?",
                UUID.class, sequenceId);
    }

    @Override
    public Map<UUID, List<String>> findConceptNamesBySequenceIds(List<UUID> sequenceIds) {
        if (sequenceIds == null || sequenceIds.isEmpty()) return Map.of();

        String placeholders = String.join(",", Collections.nCopies(sequenceIds.size(), "?"));
        List<Object[]> rows = jdbc.query("""
                SELECT link.sequence_id AS sequence_id, c.name AS name
                FROM tiered_task_sequence_concepts link
                JOIN concepts c ON c.id = link.concept_id
                WHERE link.sequence_id IN (""" + placeholders + """
                )
                ORDER BY link.sequence_id, c.name
                """,
                (rs, n) -> new Object[]{rs.getObject("sequence_id", UUID.class), rs.getString("name")},
                sequenceIds.toArray());

        Map<UUID, List<String>> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return result;
    }

    /**
     * AP19 Teil A: single-query retag-candidate loader. Uses {@code string_agg} so we don't
     * N+1 over tasks per sequence. Always LEFT JOIN so sequences without tasks still show up
     * (they'll get empty combined strings and the tagger will short-circuit to null).
     */
    @Override
    public List<TieredTaskSequenceRepository.RetagCandidate> findRetagCandidates(
            boolean onlyMissingLlmTags, List<UUID> specificIds, int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT s.id                   AS id,
                       s.programming_language AS programming_language,
                       COALESCE(string_agg(t.statement, E'\n---\n'              ORDER BY t.tier_level), '') AS combined_statement,
                       COALESCE(string_agg(t.solution,  E'\n\n// --- next tier ---\n\n' ORDER BY t.tier_level), '') AS combined_code
                FROM tiered_task_sequences s
                LEFT JOIN tasks t ON t.sequence_id = s.id
                WHERE s.deleted_at IS NULL
                """);

        List<Object> params = new ArrayList<>();

        boolean useSpecific = specificIds != null && !specificIds.isEmpty();
        if (useSpecific) {
            // Safety cap — avoid pathological IN lists from clients.
            List<UUID> ids = specificIds.size() > 10_000 ? specificIds.subList(0, 10_000) : specificIds;
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            sql.append(" AND s.id IN (").append(placeholders).append(") ");
            params.addAll(ids);
        } else if (onlyMissingLlmTags) {
            sql.append("""
                     AND NOT EXISTS (
                         SELECT 1 FROM sequence_tags tg
                         WHERE tg.sequence_id = s.id AND tg.source = 'llm'
                     )
                    """);
        }

        sql.append(" GROUP BY s.id, s.programming_language, s.created_at ");
        sql.append(" ORDER BY s.created_at DESC, s.id ");

        if (limit > 0) {
            sql.append(" LIMIT ? ");
            params.add(limit);
        }

        return jdbc.query(sql.toString(),
                (rs, n) -> new TieredTaskSequenceRepository.RetagCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("programming_language"),
                        rs.getString("combined_statement"),
                        rs.getString("combined_code")),
                params.toArray());
    }

    @Override
    public List<TieredTaskSequenceRepository.RetagCandidate> findConceptReclassifyCandidates(
            boolean onlyMissingConcepts, List<UUID> specificIds, int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT s.id                   AS id,
                       s.programming_language AS programming_language,
                       COALESCE(string_agg(t.statement, E'\n---\n'              ORDER BY t.tier_level), '') AS combined_statement,
                       COALESCE(string_agg(t.solution,  E'\n\n// --- next tier ---\n\n' ORDER BY t.tier_level), '') AS combined_code
                FROM tiered_task_sequences s
                LEFT JOIN tasks t ON t.sequence_id = s.id
                WHERE s.deleted_at IS NULL
                """);

        List<Object> params = new ArrayList<>();

        boolean useSpecific = specificIds != null && !specificIds.isEmpty();
        if (useSpecific) {
            // Safety cap — avoid pathological IN lists from clients.
            List<UUID> ids = specificIds.size() > 10_000 ? specificIds.subList(0, 10_000) : specificIds;
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            sql.append(" AND s.id IN (").append(placeholders).append(") ");
            params.addAll(ids);
        } else if (onlyMissingConcepts) {
            sql.append("""
                     AND NOT EXISTS (
                         SELECT 1 FROM tiered_task_sequence_concepts tsc
                         WHERE tsc.sequence_id = s.id
                     )
                    """);
        }

        sql.append(" GROUP BY s.id, s.programming_language, s.created_at ");
        sql.append(" ORDER BY s.created_at DESC, s.id ");

        if (limit > 0) {
            sql.append(" LIMIT ? ");
            params.add(limit);
        }

        return jdbc.query(sql.toString(),
                (rs, n) -> new TieredTaskSequenceRepository.RetagCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("programming_language"),
                        rs.getString("combined_statement"),
                        rs.getString("combined_code")),
                params.toArray());
    }

    // ── AP20: JSONB helpers ──────────────────────────────────

    private PGobject toJsonb(GenerationConstraints constraints) {
        if (constraints == null || !constraints.hasAny()) return null;
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(objectMapper.writeValueAsString(constraints));
            return pg;
        } catch (JsonProcessingException | SQLException e) {
            throw new DomainValidationException("Failed to serialize constraints: " + e.getMessage());
        }
    }

    private GenerationConstraints fromJsonb(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, GenerationConstraints.class);
        } catch (JsonProcessingException e) {
            throw new DomainValidationException("Failed to deserialize constraints: " + e.getMessage());
        }
    }
}