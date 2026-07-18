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

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.SequenceTagRowMapper;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC implementation for sequence_tags (AP19 Teil A).
 *
 * <p>Uses batched upserts with ON CONFLICT DO NOTHING so that manual tags set by teachers
 * survive subsequent auto-retagging.
 */
@Repository
public class SequenceTagDAO implements SequenceTagRepository {

    private static final SequenceTagRowMapper ROW_MAPPER = new SequenceTagRowMapper();
    private final JdbcTemplate jdbc;

    public SequenceTagDAO(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SequenceTagDTO> findBySequenceId(UUID sequenceId) {
        // Order: manual first (teacher-set, most trustworthy), then llm, then auto; tag_name as tiebreaker.
        return jdbc.query(
            "SELECT sequence_id, tag_name, source, confidence, created_at "
            + "FROM sequence_tags WHERE sequence_id = ? "
            + "ORDER BY CASE source WHEN 'manual' THEN 0 WHEN 'llm' THEN 1 ELSE 2 END, tag_name",
            ROW_MAPPER, sequenceId);
    }

    @Override
    public Map<UUID, List<SequenceTagDTO>> findBySequenceIds(List<UUID> sequenceIds) {
        if (sequenceIds == null || sequenceIds.isEmpty()) return Map.of();

        String placeholders = String.join(",", Collections.nCopies(sequenceIds.size(), "?"));
        List<SequenceTagDTO> rows = jdbc.query(
            "SELECT sequence_id, tag_name, source, confidence, created_at "
            + "FROM sequence_tags WHERE sequence_id IN (" + placeholders + ") "
            + "ORDER BY CASE source WHEN 'manual' THEN 0 WHEN 'llm' THEN 1 ELSE 2 END, tag_name",
            ROW_MAPPER, sequenceIds.toArray());

        return rows.stream().collect(Collectors.groupingBy(SequenceTagDTO::getSequenceId));
    }

    @Override
    @Transactional
    public void upsertAll(List<SequenceTagDTO> tags) {
        if (tags == null || tags.isEmpty()) return;

        // De-duplicate by composite key (sequence_id, tag_name) to avoid ON CONFLICT double-hits within batch.
        Map<String, SequenceTagDTO> unique = new LinkedHashMap<>();
        for (SequenceTagDTO t : tags) {
            if (t.getSequenceId() == null || t.getTagName() == null || t.getTagName().isBlank()) continue;
            unique.putIfAbsent(t.getSequenceId() + "|" + t.getTagName().trim().toLowerCase(Locale.ROOT), t);
        }
        List<SequenceTagDTO> deduped = List.copyOf(unique.values());
        if (deduped.isEmpty()) return;

        jdbc.batchUpdate(
            "INSERT INTO sequence_tags (sequence_id, tag_name, source, confidence) "
            + "VALUES (?, ?, ?, ?) ON CONFLICT (sequence_id, tag_name) DO NOTHING",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    SequenceTagDTO t = deduped.get(i);
                    ps.setObject(1, t.getSequenceId());
                    ps.setString(2, t.getTagName().trim().toLowerCase(Locale.ROOT));
                    ps.setString(3, t.getSource() != null ? t.getSource() : "auto");
                    if (t.getConfidence() != null) ps.setFloat(4, t.getConfidence());
                    else ps.setNull(4, Types.REAL);
                }

                @Override
                public int getBatchSize() {
                    return deduped.size();
                }
            });
    }

    @Override
    @Transactional
    public void replaceForSource(UUID sequenceId, String source, List<SequenceTagDTO> newTags) {
        if (sequenceId == null || source == null) return;
        jdbc.update("DELETE FROM sequence_tags WHERE sequence_id = ? AND source = ?", sequenceId, source);
        if (newTags != null && !newTags.isEmpty()) {
            upsertAll(newTags);
        }
    }

    @Override
    @Transactional
    public void delete(UUID sequenceId, String tagName) {
        if (sequenceId == null || tagName == null) return;
        jdbc.update("DELETE FROM sequence_tags WHERE sequence_id = ? AND tag_name = ?",
                sequenceId, tagName.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public List<TagCount> findAllTagsWithCounts() {
        return jdbc.query(
            "SELECT tag_name, COUNT(*) AS cnt FROM sequence_tags GROUP BY tag_name ORDER BY cnt DESC, tag_name",
            (rs, n) -> new TagCount(rs.getString("tag_name"), rs.getLong("cnt")));
    }

    @Override
    public List<String> findTagsByPrefix(String prefix, int limit) {
        if (prefix == null) prefix = "";
        String normalized = prefix.trim().toLowerCase(Locale.ROOT) + "%";
        int cap = Math.max(1, Math.min(limit, 50));
        return jdbc.query(
            "SELECT DISTINCT tag_name FROM sequence_tags WHERE tag_name LIKE ? "
            + "ORDER BY tag_name LIMIT ?",
            (rs, n) -> rs.getString("tag_name"), normalized, cap);
    }

    @Override
    public List<NewTopic> findNewTopicsSince(Instant cutoff, int limit) {
        if (cutoff == null) return List.of();
        int cap = Math.max(1, Math.min(limit, 200));
        // GROUP BY tag_name so we see each tag once with its earliest appearance. HAVING filters
        // to "genuinely new" (first appearance after the cutoff) rather than "touched recently".
        return jdbc.query(
            "SELECT tag_name, MIN(created_at) AS first_seen, COUNT(*) AS usage_count "
            + "FROM sequence_tags "
            + "GROUP BY tag_name "
            + "HAVING MIN(created_at) >= ? "
            + "ORDER BY usage_count DESC, first_seen DESC, tag_name "
            + "LIMIT ?",
            (rs, n) -> new NewTopic(
                rs.getString("tag_name"),
                rs.getTimestamp("first_seen").toInstant(),
                rs.getLong("usage_count")),
            Timestamp.from(cutoff), cap);
    }

    @Override
    @Transactional
    public int mergeTags(String fromTagName, String toTagName) {
        if (fromTagName == null || toTagName == null) return 0;
        String from = fromTagName.trim().toLowerCase(Locale.ROOT);
        String to   = toTagName.trim().toLowerCase(Locale.ROOT);
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) return 0;

        // Two-step merge:
        //   1) DELETE the `from` rows on sequences that ALREADY have `to` (would collide with the
        //      unique key (sequence_id, tag_name) otherwise). These are the "duplicate" deletions.
        //   2) UPDATE the remaining `from` rows to `to`.
        // Counts are summed so the caller sees "total rows affected" rather than guessing.
        int deletedDuplicates = jdbc.update(
            "DELETE FROM sequence_tags sg "
            + "WHERE sg.tag_name = ? "
            + "  AND EXISTS (SELECT 1 FROM sequence_tags sg2 "
            + "              WHERE sg2.sequence_id = sg.sequence_id AND sg2.tag_name = ?)",
            from, to);
        int renamed = jdbc.update(
            "UPDATE sequence_tags SET tag_name = ? WHERE tag_name = ?",
            to, from);
        return deletedDuplicates + renamed;
    }
}
