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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central service for sequence tags (AP19 Teil A).
 *
 * <p>Three tag sources, all stored in the same table:
 * <ul>
 *   <li><b>auto</b> &mdash; structural tags derived from AP25 Concept Detection. Written synchronously
 *       at the end of task generation (cheap DB INSERT, never blocks meaningfully).</li>
 *   <li><b>llm</b> &mdash; semantic tags derived from an LLM call. Written asynchronously via
 *       {@link de.uni_passau.fim.se2.application.events.SequenceGeneratedEvent} (see AsyncConfig).</li>
 *   <li><b>manual</b> &mdash; teacher-set. Must never be overwritten by auto-retag.</li>
 * </ul>
 *
 * <p>All tag names are normalized to lowercase-trimmed slugs by the DAO layer, so callers
 * can pass user-typed variants ("Fibonacci", " fibonacci ", "FIBONACCI") without worry.
 */
@Service
public class SequenceTagService {

    private static final Logger logger = LoggerFactory.getLogger(SequenceTagService.class);

    private final SequenceTagRepository tagRepo;

    public SequenceTagService(SequenceTagRepository tagRepo) {
        this.tagRepo = tagRepo;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Auto-tagging (AP25 structural concepts)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes structural tags (source='auto') for a freshly generated sequence.
     * Synchronous — this is a single batched INSERT and runs in the same transaction
     * as sequence persist.
     *
     * <p>Manual tags are <b>never</b> overwritten: the DAO uses {@code ON CONFLICT DO NOTHING},
     * so an already-existing (sequence_id, tag_name) row stays untouched.
     *
     * @param sequenceId the freshly-persisted sequence
     * @param conceptSlugs structural concept slugs from AP25 (e.g. "for-loop", "recursion")
     */
    public void autoTagStructural(UUID sequenceId, List<String> conceptSlugs) {
        if (sequenceId == null || conceptSlugs == null || conceptSlugs.isEmpty()) return;

        List<SequenceTagDTO> tags = conceptSlugs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> new SequenceTagDTO(sequenceId, s.trim().toLowerCase(Locale.ROOT), "auto"))
                .toList();

        if (tags.isEmpty()) return;
        try {
            tagRepo.upsertAll(tags);
            logger.debug("Auto-tagged sequence {} with {} structural tags", sequenceId, tags.size());
        } catch (Exception e) {
            // Tagging failures must never block the generation pipeline (see AP19 Teil A spec).
            logger.warn("Structural auto-tagging failed for sequence {}: {}", sequenceId, e.getMessage());
        }
    }

    /**
     * Replaces all structural (auto) tags for a sequence. Used when code is meaningfully edited
     * and concept detection re-runs. Manual + LLM tags survive.
     */
    public void replaceStructuralTags(UUID sequenceId, List<String> newConceptSlugs) {
        if (sequenceId == null) return;
        List<SequenceTagDTO> fresh = newConceptSlugs == null ? List.of() : newConceptSlugs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> new SequenceTagDTO(sequenceId, s.trim().toLowerCase(Locale.ROOT), "auto"))
                .toList();
        tagRepo.replaceForSource(sequenceId, "auto", fresh);
    }

    // ─────────────────────────────────────────────────────────────────────
    // LLM semantic tagging
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes semantic LLM tags (source='llm'). Called by {@link #writeLlmTags(UUID, List, List)}
     * with the primary + alias split.
     */
    public void writeLlmTags(UUID sequenceId, List<String> primaryTags, List<String> aliases) {
        writeLlmTags(sequenceId, primaryTags, aliases, null);
    }

    /**
     * Writes semantic LLM tags. Primary and alias are both stored with source='llm'
     * so they all contribute to search; primaries get a higher confidence.
     */
    public void writeLlmTags(UUID sequenceId, List<String> primaryTags, List<String> aliases, Float confidence) {
        if (sequenceId == null) return;

        List<SequenceTagDTO> tags = new java.util.ArrayList<>();
        if (primaryTags != null) {
            for (String p : primaryTags) {
                if (p == null || p.isBlank()) continue;
                tags.add(new SequenceTagDTO(sequenceId, p.trim().toLowerCase(Locale.ROOT), "llm",
                        confidence != null ? confidence : 0.9f));
            }
        }
        if (aliases != null) {
            for (String a : aliases) {
                if (a == null || a.isBlank()) continue;
                tags.add(new SequenceTagDTO(sequenceId, a.trim().toLowerCase(Locale.ROOT), "llm", 0.6f));
            }
        }
        if (tags.isEmpty()) return;

        try {
            // Replace previous LLM tags so a re-tag doesn't accumulate stale ones.
            tagRepo.replaceForSource(sequenceId, "llm", tags);
            logger.debug("LLM-tagged sequence {} with {} tags ({} primary, {} alias)",
                    sequenceId, tags.size(),
                    primaryTags == null ? 0 : primaryTags.size(),
                    aliases == null ? 0 : aliases.size());
        } catch (Exception e) {
            logger.warn("LLM tag write failed for sequence {}: {}", sequenceId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Manual tag CRUD (teacher-facing)
    // ─────────────────────────────────────────────────────────────────────

    /** Adds a manually-set tag. Idempotent via ON CONFLICT DO NOTHING. */
    public void addManualTag(UUID sequenceId, String tagName) {
        if (sequenceId == null || tagName == null || tagName.isBlank()) return;
        tagRepo.upsertAll(List.of(
                new SequenceTagDTO(sequenceId, tagName.trim().toLowerCase(Locale.ROOT), "manual")));
    }

    /** Removes a tag regardless of source. The teacher is the final authority. */
    public void removeTag(UUID sequenceId, String tagName) {
        tagRepo.delete(sequenceId, tagName);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Read helpers
    // ─────────────────────────────────────────────────────────────────────

    public List<SequenceTagDTO> getTagsFor(UUID sequenceId) {
        return tagRepo.findBySequenceId(sequenceId);
    }

    public Map<UUID, List<SequenceTagDTO>> getTagsForAll(List<UUID> sequenceIds) {
        return tagRepo.findBySequenceIds(sequenceIds);
    }

    public List<SequenceTagRepository.TagCount> getTagCloud() {
        return tagRepo.findAllTagsWithCounts();
    }

    public List<String> autocomplete(String prefix, int limit) {
        return tagRepo.findTagsByPrefix(prefix, limit);
    }

    /**
     * Groups tags by source for a single sequence — convenient for UI rendering
     * which typically shows manual and LLM tags differently from auto ones.
     */
    public Map<String, List<String>> getTagsGroupedBySource(UUID sequenceId) {
        return tagRepo.findBySequenceId(sequenceId).stream()
                .collect(Collectors.groupingBy(
                        SequenceTagDTO::getSource,
                        Collectors.mapping(SequenceTagDTO::getTagName, Collectors.toList())));
    }

    // ─────────────────────────────────────────────────────────────────────
    // AP19 A16: admin — new-topics report + tag merge
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tags that appeared for the first time within the past {@code daysBack} days. Used by the
     * admin dashboard's weekly "new topics" widget — a cheap signal for "the LLM is coining
     * something we should canonicalise" or "teachers are tagging a new recurring theme".
     *
     * @param daysBack how far to look back (clamped to 1..90 days)
     * @param limit    max rows (clamped by the DAO to 1..200)
     */
    public List<SequenceTagRepository.NewTopic> getNewTopics(int daysBack, int limit) {
        int days = Math.max(1, Math.min(daysBack, 90));
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return tagRepo.findNewTopicsSince(cutoff, limit);
    }

    /**
     * Renames a tag across the whole system. Intended for admins cleaning up near-duplicates
     * ("rekursion" → "recursion", "fib-folge" → "fibonacci"). Idempotent — merging a tag into
     * itself or into a non-existent source is a safe no-op.
     *
     * @return total rows affected (duplicates dropped + renames)
     */
    public int mergeTags(String fromTagName, String toTagName) {
        int affected = tagRepo.mergeTags(fromTagName, toTagName);
        logger.info("Merged tag '{}' into '{}' (rows affected: {})", fromTagName, toTagName, affected);
        return affected;
    }
}
