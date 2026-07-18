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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.api.dto.LibraryItemDTO;
import de.uni_passau.fim.se2.api.dto.LibrarySearchResponse;
import de.uni_passau.fim.se2.api.dto.LibrarySearchResponse.FacetBucket;
import de.uni_passau.fim.se2.api.dto.LibrarySearchResponse.Facets;
import de.uni_passau.fim.se2.api.dto.LibrarySearchResponse.Ownership;
import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AP19 Teil A: Library search (read-side).
 *
 * <p>One entry point — {@link #search(SearchQuery, UUID)} — returns a page of
 * {@link LibraryItemDTO}s plus facet counts for the sidebar and "did you mean?" suggestions.
 *
 * <p>Why a service, not a repository? The query is dynamic: depending on which filters a teacher
 * ticks, we assemble different WHERE clauses and JOINs. Wrapping that in an interface hides
 * more than it helps — the SQL is the behaviour. Keeps the logic in one readable place.
 *
 * <p>Dynamic SQL correctness rules:
 * <ul>
 *   <li>Every user input is a bind parameter (never concatenated).</li>
 *   <li>The only interpolated values are whitelisted sort columns and tag-count integers.</li>
 *   <li>Tag names are normalized to lowercase + slug-safe before bind.</li>
 * </ul>
 */
@Service
public class SequenceSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SequenceSearchService.class);

    /** Safety cap: never return more than this many rows per page. */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** How many top tags to report in the facet sidebar. */
    private static final int FACET_TOP_TAGS = 30;

    /** Minimum similarity for fuzzy "Meintest du…" tag suggestions. */
    private static final float TRIGRAM_SIMILARITY_THRESHOLD = 0.3f;

    /** Characters allowed in a normalized tag slug — same regex the DAO uses. */
    private static final Pattern SLUG_BAD = Pattern.compile("[^a-z0-9-]");

    /**
     * A14: Control-character markers for ts_headline output. Two single-byte control
     * characters that can never appear in plain-text task descriptions or LLM summaries.
     * The frontend HTML-escapes the full highlight and then swaps these tokens for
     * {@code <mark>} / {@code </mark>} — no XSS risk from underlying content.
     */
    static final String HIGHLIGHT_START_MARKER = "\u0001";
    static final String HIGHLIGHT_STOP_MARKER  = "\u0002";

    private final JdbcTemplate jdbc;
    private final SequenceTagRepository tagRepo;

    public SequenceSearchService(JdbcTemplate jdbc, SequenceTagRepository tagRepo) {
        this.jdbc = jdbc;
        this.tagRepo = tagRepo;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Run a search and return items + facets + suggestions.
     *
     * @param rawQuery      filters from the REST layer (may contain unsafe input — normalized here)
     * @param currentUserId the authenticated user (for {@code owner=me}); nullable for anonymous
     */
    public LibrarySearchResponse search(SearchQuery rawQuery, UUID currentUserId) {
        SearchQuery q = rawQuery == null ? new SearchQuery() : rawQuery.normalized();
        int page = Math.max(0, q.page());
        int size = clamp(q.size() <= 0 ? DEFAULT_PAGE_SIZE : q.size(), 1, MAX_PAGE_SIZE);

        // Build the shared WHERE clause once. Used by count / page / facet queries so they all see
        // exactly the same filter set.
        WhereClause where = buildWhere(q, currentUserId);

        long total = fetchTotal(where);
        List<LibraryItemDTO> items = total == 0 ? List.of()
                : fetchPage(q, where, page, size, currentUserId);

        Facets facets = fetchFacets(where, currentUserId);

        // Only compute fuzzy suggestions when FTS returned nothing — it's an expensive call.
        List<String> suggestions = (total == 0 && q.q() != null && !q.q().isBlank())
                ? fetchSuggestions(q.q()) : List.of();

        return new LibrarySearchResponse(items, page, size, total, facets, suggestions);
    }

    // ─────────────────────────────────────────────────────────────────────
    // WHERE builder
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Shared WHERE fragment. Both the page query and the facet queries use the <i>same</i> filters
     * except that facets skip the filter they're counting (so "languages" facet ignores the
     * language filter, "topTags" facet ignores the tag filter). We model that with {@link #rebuild(boolean, boolean)}.
     */
    private WhereClause buildWhere(SearchQuery q, UUID currentUserId) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        // Language filter
        if (q.lang() != null && !q.lang().isBlank()) {
            sb.append(" AND s.programming_language = ? ");
            params.add(q.lang().trim());
        }

        // Ownership filter (me)
        if ("me".equalsIgnoreCase(q.owner())) {
            if (currentUserId == null) {
                // Authenticated-only filter: short-circuit to "no results" rather than silently widening.
                sb.append(" AND 1=0 ");
            } else {
                sb.append(" AND s.owner_id = ? ");
                params.add(currentUserId);
            }
        }

        // Tier count
        if (q.tierCount() != null && q.tierCount() > 0) {
            sb.append(" AND s.tier_count = ? ");
            params.add(q.tierCount());
        }

        // Tag filter
        List<String> tags = q.tags();
        if (tags != null && !tags.isEmpty()) {
            if (q.tagModeAll()) {
                sb.append("""
        AND NOT EXISTS (
            SELECT 1
            FROM unnest(?::text[]) t(tag)
            WHERE NOT EXISTS (
                SELECT 1
                FROM sequence_tags tg
                WHERE tg.sequence_id = s.id
                  AND tg.tag_name = t.tag
            )
        )
    """);
                params.add(tags.toArray(new String[0]));
            } else {
                // ANY: sequence must have at least one requested tag.
                sb.append(" AND EXISTS (SELECT 1 FROM sequence_tags tg ")
                  .append("     WHERE tg.sequence_id = s.id AND tg.tag_name IN (")
                  .append(placeholders(tags.size()))
                  .append(")) ");
                params.addAll(tags);
            }
        }

        // A14: Full-text search matches a query against EITHER the sequence-level vector
        // (task_description + semantic_summary, so domain concepts like "recursion" land
        // even when the task statements don't use the word) OR a task's vector (statement +
        // solution). `websearch_to_tsquery` gives teachers real operators — "exact phrase",
        // `-exclude`, and `or` — while still being safe on malformed input (it silently
        // tolerates unbalanced quotes etc).
        //
        // NOTE: websearch_to_tsquery('simple', ?) is used directly (no CTE) so this
        // WHERE fragment can be embedded in fetchTotal, fetchFacets, and fetchPage alike
        // without each caller having to inject a surrounding CTE. Each usage is a bind
        // parameter — never string-interpolated.
        if (q.q() != null && !q.q().isBlank()) {
            sb.append(" AND ( s.search_vector @@ websearch_to_tsquery('simple', ?) ")
              .append("       OR EXISTS (SELECT 1 FROM tasks t ")
              .append("                    WHERE t.sequence_id = s.id ")
              .append("                      AND t.search_vector @@ websearch_to_tsquery('simple', ?))) ");
            params.add(q.q().trim());
            params.add(q.q().trim());
        }

        return new WhereClause(sb.toString(), params, q, currentUserId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Count / page
    // ─────────────────────────────────────────────────────────────────────

    private long fetchTotal(WhereClause w) {
        String sql = "SELECT COUNT(*) FROM tiered_task_sequences s " + w.sql;
        Long total = jdbc.queryForObject(sql, Long.class, w.params.toArray());
        return total == null ? 0L : total;
    }

    private List<LibraryItemDTO> fetchPage(SearchQuery q, WhereClause w, int page, int size, UUID currentUserId) {
        StringBuilder sql = new StringBuilder();

        // ts_headline is only computed when q is present; otherwise NULL.
        boolean hasQuery = q.q() != null && !q.q().isBlank();

        // A15: "Already in my library" — computed in the same SELECT so the card can render a
        // badge without a follow-up fetch. Uses EXISTS for an index-friendly short-circuit:
        // PostgreSQL stops scanning worksheets_sequence as soon as one match is found.
        boolean hasUser = currentUserId != null;

        sql.append("""
                SELECT s.id                   AS id,
                       s.programming_language AS programming_language,
                       s.task_description     AS task_description,
                       s.semantic_summary     AS semantic_summary,
                       s.created_at           AS created_at,
                       s.owner_id             AS owner_id,
                       s.tier_count           AS tier_count,
                       (SELECT COUNT(*) FROM worksheets_sequence ws WHERE ws.sequence_id = s.id) AS usage_count,
                """);
        if (hasUser) {
            sql.append("""
                       EXISTS (
                           SELECT 1 FROM worksheets_sequence ws2
                             JOIN worksheets wk ON wk.id = ws2.worksheet_id
                            WHERE ws2.sequence_id = s.id AND wk.owner_id = ?
                       ) AS already_in_my_worksheet,
                    """);
        } else {
            sql.append(" FALSE AS already_in_my_worksheet, ");
        }
        if (hasQuery) {
            // A14: Highlight the richest text we have — LLM summary first (most informative
            // one-liner), then the teacher-crafted task_description, then the first task
            // statement as a fallback. COALESCE(NULLIF(...)) makes empty strings skip a
            // candidate rather than emit an empty highlight.
            //
            // Markers are control characters (\u0001/\u0002) — never appear in natural text,
            // so after the frontend HTML-escapes the response it can safely swap them for
            // <mark>/</mark> without risking XSS from the underlying content.
            //
            // Both the tsquery and the options string are bind parameters (?).
            sql.append("""
                       ts_headline('simple',
                            COALESCE(
                                NULLIF(s.semantic_summary, ''),
                                NULLIF(s.task_description, ''),
                                (SELECT t.statement FROM tasks t
                                   WHERE t.sequence_id = s.id
                                   ORDER BY t.tier_level
                                   LIMIT 1),
                                ''
                            ),
                            websearch_to_tsquery('simple', ?),
                            ?
                         ) AS highlight
                    """);
        } else {
            sql.append("CAST(NULL AS TEXT) AS highlight ");
        }

        sql.append(" FROM tiered_task_sequences s ");
        sql.append(w.sql);

        // A14: Relevance sort now combines the sequence vector rank (dominant — teachers care
        // about the curated description) with the best task vector rank. COALESCE to 0 so a
        // missing side doesn't zero the whole score. ts_rank_cd prefers proximity/density,
        // which usually matches a teacher's intuitive "which one looks most like what I asked".
        // websearch_to_tsquery('simple', ?) is used directly here too (same pattern as WHERE).
        String sort = q.sort();
        if ("relevance".equals(sort) && hasQuery) {
            sql.append(" ORDER BY ( ")
               .append("     COALESCE(ts_rank_cd(s.search_vector, websearch_to_tsquery('simple', ?)), 0) ")
               .append("     + COALESCE((SELECT MAX(ts_rank_cd(t.search_vector, websearch_to_tsquery('simple', ?))) ")
               .append("                   FROM tasks t WHERE t.sequence_id = s.id), 0) ")
               .append(" ) DESC NULLS LAST, s.created_at DESC, s.id ");
        } else if ("popularity".equals(sort)) {
            sql.append(" ORDER BY (SELECT COUNT(*) FROM worksheets_sequence ws WHERE ws.sequence_id = s.id) DESC, ")
               .append("          s.created_at DESC, s.id ");
        } else {
            sql.append(" ORDER BY s.created_at DESC, s.id ");
        }

        sql.append(" LIMIT ? OFFSET ? ");

        // Parameter assembly must match placeholder order in the generated SQL:
        //   1. (optional) currentUserId       — alreadyInMyWorksheet EXISTS subquery in SELECT
        //   2. (optional) q.q()               — websearch_to_tsquery in ts_headline
        //   3. (optional) headlineOptions()   — ts_headline option string (StartSel/StopSel)
        //   4. w.params                       — shared WHERE params (includes 2× q.q() for FTS)
        //   5. (optional) q.q(), q.q()        — websearch_to_tsquery in ORDER BY relevance (×2)
        //   6. size, offset                   — LIMIT / OFFSET
        List<Object> params = new ArrayList<>();
        if (hasUser) params.add(currentUserId);
        if (hasQuery) {
            params.add(q.q().trim());     // ts_headline query param
            params.add(headlineOptions()); // ts_headline options param
        }
        params.addAll(w.params);
        if ("relevance".equals(sort) && hasQuery) {
            params.add(q.q().trim()); // ORDER BY ts_rank_cd — sequence vector
            params.add(q.q().trim()); // ORDER BY ts_rank_cd — task vector
        }
        params.add(size);
        params.add(page * size);

        List<LibraryItemDTO> rows = jdbc.query(sql.toString(), (rs, n) -> {
            UUID seqId = rs.getObject("id", UUID.class);
            int tier = rs.getInt("tier_count");
            if (rs.wasNull() || tier <= 0) tier = 3;
            return new LibraryItemDTO(
                    seqId,
                    rs.getString("programming_language"),
                    rs.getString("task_description"),
                    rs.getString("semantic_summary"),
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                    rs.getObject("owner_id", UUID.class),
                    tier,
                    List.of(), // filled in below with a single batch query
                    rs.getLong("usage_count"),
                    rs.getString("highlight"),
                    rs.getBoolean("already_in_my_worksheet")
            );
        }, params.toArray());

        // Attach tags in one shot via the existing batch lookup.
        if (rows.isEmpty()) return rows;
        List<UUID> ids = rows.stream().map(LibraryItemDTO::sequenceId).toList();
        Map<UUID, List<SequenceTagDTO>> tagMap = tagRepo.findBySequenceIds(ids);
        List<LibraryItemDTO> enriched = new ArrayList<>(rows.size());
        for (LibraryItemDTO r : rows) {
            enriched.add(new LibraryItemDTO(
                    r.sequenceId(), r.programmingLanguage(), r.taskDescription(), r.semanticSummary(),
                    r.createdAt(), r.ownerId(), r.tierCount(),
                    tagMap.getOrDefault(r.sequenceId(), List.of()),
                    r.usageCount(), r.highlight(),
                    r.alreadyInMyWorksheet()));
        }
        return enriched;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Similar sequences (A15)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find sequences that share tags with the given {@code sequenceId}. Used by the
     * "You might also like…" panel on the sequence detail page.
     *
     * <p>Ranking combines three signals, in priority order:
     * <ol>
     *   <li>Tag overlap, weighted by source ({@code manual=3, llm=2, auto=1}) — teachers'
     *       deliberate tags count more than structural auto-tags.</li>
     *   <li>Same programming language tiebreaker — a recursion example in Java is more
     *       useful than the same concept in Python to a Java teacher.</li>
     *   <li>Recency as a final tiebreaker.</li>
     * </ol>
     *
     * <p>Returns an empty list if the source sequence has no tags — there's nothing to
     * overlap against and random picks would feel spammy.
     *
     * @param sourceSequenceId the sequence to find neighbours for; excluded from the result
     * @param limit            max results (clamped to [1, 10])
     * @param currentUserId    for the {@code alreadyInMyWorksheet} badge on each card; nullable
     */
    public List<LibraryItemDTO> findSimilar(UUID sourceSequenceId, int limit, UUID currentUserId) {
        int capped = clamp(limit <= 0 ? 3 : limit, 1, 10);
        boolean hasUser = currentUserId != null;

        StringBuilder sql = new StringBuilder();

        sql.append("""
                WITH source_tags AS (
                    SELECT tag_name FROM sequence_tags WHERE sequence_id = ?
                ),
                tag_overlaps AS (
                    SELECT sg.sequence_id,
                           COUNT(*) AS overlap_count,
                           SUM(CASE sg.source
                                   WHEN 'manual' THEN 3
                                   WHEN 'llm'    THEN 2
                                   ELSE                 1
                               END) AS weighted_overlap
                      FROM sequence_tags sg
                     WHERE sg.tag_name IN (SELECT tag_name FROM source_tags)
                       AND sg.sequence_id <> ?
                  GROUP BY sg.sequence_id
                )
                SELECT s.id                   AS id,
                       s.programming_language AS programming_language,
                       s.task_description     AS task_description,
                       s.semantic_summary     AS semantic_summary,
                       s.created_at           AS created_at,
                       s.owner_id             AS owner_id,
                       s.tier_count           AS tier_count,
                       (SELECT COUNT(*) FROM worksheets_sequence ws WHERE ws.sequence_id = s.id) AS usage_count,
                """);
        if (hasUser) {
            // FIX: Hier auch  hinzugefügt!
            sql.append("""
                       EXISTS (
                           SELECT 1 FROM worksheets_sequence ws2
                             JOIN worksheets wk ON wk.id = ws2.worksheet_id
                            WHERE ws2.sequence_id = s.id AND wk.owner_id = ?
                       ) AS already_in_my_worksheet,
                    """);
        } else {
            sql.append(" FALSE AS already_in_my_worksheet, ");
        }
        sql.append("""
                       ov.overlap_count,
                       ov.weighted_overlap
                  FROM tag_overlaps ov
                  JOIN tiered_task_sequences s ON s.id = ov.sequence_id
              LEFT JOIN tiered_task_sequences src ON src.id = ?
              ORDER BY ov.weighted_overlap DESC,
                       ov.overlap_count    DESC,
                       (s.programming_language = src.programming_language) DESC,
                       s.created_at DESC,
                       s.id
                 LIMIT ?
                """);

        List<Object> params = new ArrayList<>();
        params.add(sourceSequenceId);
        params.add(sourceSequenceId);
        if (hasUser) params.add(currentUserId);
        params.add(sourceSequenceId);
        params.add(capped);

        List<LibraryItemDTO> rows = jdbc.query(sql.toString(), (rs, n) -> {
            UUID seqId = rs.getObject("id", UUID.class);
            int tier = rs.getInt("tier_count");
            if (rs.wasNull() || tier <= 0) tier = 3;
            return new LibraryItemDTO(
                    seqId,
                    rs.getString("programming_language"),
                    rs.getString("task_description"),
                    rs.getString("semantic_summary"),
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                    rs.getObject("owner_id", UUID.class),
                    tier,
                    List.of(),
                    rs.getLong("usage_count"),
                    null, // no highlight for similar-sequences — there's no query
                    rs.getBoolean("already_in_my_worksheet")
            );
        }, params.toArray());

        if (rows.isEmpty()) return rows;
        List<UUID> ids = rows.stream().map(LibraryItemDTO::sequenceId).toList();
        Map<UUID, List<SequenceTagDTO>> tagMap = tagRepo.findBySequenceIds(ids);
        List<LibraryItemDTO> enriched = new ArrayList<>(rows.size());
        for (LibraryItemDTO r : rows) {
            enriched.add(new LibraryItemDTO(
                    r.sequenceId(), r.programmingLanguage(), r.taskDescription(), r.semanticSummary(),
                    r.createdAt(), r.ownerId(), r.tierCount(),
                    tagMap.getOrDefault(r.sequenceId(), List.of()),
                    r.usageCount(), r.highlight(), r.alreadyInMyWorksheet()));
        }
        return enriched;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Facets
    // ─────────────────────────────────────────────────────────────────────

    private Facets fetchFacets(WhereClause base, UUID currentUserId) {
        List<FacetBucket> languages = fetchLanguageFacets(base.withoutLanguage());
        List<FacetBucket> topTags   = fetchTagFacets(base.withoutTags());
        Ownership ownership         = fetchOwnershipFacets(base, currentUserId);
        return new Facets(languages, topTags, ownership);
    }

    private List<FacetBucket> fetchLanguageFacets(WhereClause w) {
        String sql = """
                SELECT s.programming_language AS name, COUNT(*) AS cnt
                FROM tiered_task_sequences s
                """ + w.sql + """
                GROUP BY s.programming_language
                ORDER BY cnt DESC, s.programming_language
                """;
        return jdbc.query(sql, (rs, n) -> new FacetBucket(rs.getString("name"), rs.getLong("cnt")),
                w.params.toArray());
    }

    private List<FacetBucket> fetchTagFacets(WhereClause w) {
        String sql = """
                SELECT tg.tag_name AS name, COUNT(*) AS cnt
                FROM sequence_tags tg
                JOIN tiered_task_sequences s ON s.id = tg.sequence_id
                """ + w.sql + """
                GROUP BY tg.tag_name
                ORDER BY cnt DESC, tg.tag_name
                LIMIT ?
                """;
        List<Object> params = new ArrayList<>(w.params);
        params.add(FACET_TOP_TAGS);
        return jdbc.query(sql, (rs, n) -> new FacetBucket(rs.getString("name"), rs.getLong("cnt")),
                params.toArray());
    }

    private Ownership fetchOwnershipFacets(WhereClause w, UUID currentUserId) {
        if (currentUserId == null) {
            long total = fetchTotal(w);
            return new Ownership(0, total);
        }
        String sql = """
                SELECT SUM(CASE WHEN s.owner_id = ? THEN 1 ELSE 0 END) AS mine,
                       SUM(CASE WHEN s.owner_id IS NULL OR s.owner_id <> ? THEN 1 ELSE 0 END) AS others
                FROM tiered_task_sequences s
                """ + w.sql;

        List<Object> params = new ArrayList<>();
        params.add(currentUserId);
        params.add(currentUserId);
        params.addAll(w.params);

        return jdbc.queryForObject(sql,
                (rs, n) -> new Ownership(rs.getLong("mine"), rs.getLong("others")),
                params.toArray());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fuzzy suggestions ("Meintest du…")
    // ─────────────────────────────────────────────────────────────────────

    /**
     * A14: Fuzzy "Meintest du…" suggestions when the FTS query returned zero rows. We score
     * candidate tag names by trigram similarity against a lowercased version of the query —
     * the tag_name column is already stored in slug form, and the gin_trgm_ops index makes
     * this a fast index lookup. Each tag is returned only once (the DISTINCT wins over
     * duplicate slugs across sequences).
     *
     * <p>Tag-based suggestions (rather than free-form word hits from task descriptions) are
     * the most actionable for the teacher: clicking one in the EmptyState UI plugs directly
     * into the existing filter sidebar.
     */
    private List<String> fetchSuggestions(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return List.of();

        try {
            return jdbc.query("""
                    SELECT DISTINCT tag_name
                    FROM sequence_tags
                    WHERE similarity(tag_name, ?) > ?
                    ORDER BY similarity(tag_name, ?) DESC
                    LIMIT 5
                    """,
                    (rs, n) -> rs.getString("tag_name"),
                    normalized, TRIGRAM_SIMILARITY_THRESHOLD, normalized);
        } catch (Exception e) {
            // pg_trgm might not be installed in every env (dev-sqlite etc). Never fail the response.
            logger.debug("Trigram suggestions failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Build the options string passed to {@code ts_headline}. Using control characters as
     * start/stop markers so the output can be safely HTML-escaped on the frontend before
     * the markers are swapped back to {@code <mark>}/{@code </mark>}. Keeps match fragments
     * short — the highlight is a preview under the card title, not a full excerpt.
     */
    private static String headlineOptions() {
        return "StartSel=" + HIGHLIGHT_START_MARKER
                + ", StopSel=" + HIGHLIGHT_STOP_MARKER
                + ", MaxWords=20, MinWords=5, MaxFragments=1, HighlightAll=false";
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static String placeholders(int n) {
        if (n == 1) return "?";
        StringBuilder sb = new StringBuilder(n * 2 - 1);
        sb.append("?");
        for (int i = 1; i < n; i++) sb.append(",?");
        return sb.toString();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Normalize a tag to the same slug format the DAO stores. */
    static String normalizeTag(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        v = SLUG_BAD.matcher(v).replaceAll("-");
        v = v.replaceAll("-+", "-").replaceAll("^-|-$", "");
        return v.isEmpty() ? null : v;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Input shape for {@link #search(SearchQuery, UUID)}. Built from request params by the
     * controller, normalized in {@link #normalized()} before any SQL work.
     */
    public static final class SearchQuery {
        private String q;
        private List<String> tags;
        private String tagMode = "any"; // "any" | "all"
        private String lang;
        private String owner = "any";   // "any" | "me"
        private Integer tierCount;
        private String sort = "recent"; // "recent" | "relevance" | "popularity"
        private int page = 0;
        private int size = DEFAULT_PAGE_SIZE;

        public SearchQuery() {}

        // Getters
        public String q() { return q; }
        public List<String> tags() { return tags; }
        public boolean tagModeAll() { return "all".equalsIgnoreCase(tagMode); }
        public String lang() { return lang; }
        public String owner() { return owner; }
        public Integer tierCount() { return tierCount; }
        public String sort() { return sort; }
        public int page() { return page; }
        public int size() { return size; }

        // Fluent setters
        public SearchQuery q(String v)           { this.q = v; return this; }
        public SearchQuery tags(List<String> v)  { this.tags = v; return this; }
        public SearchQuery tagMode(String v)     { this.tagMode = v; return this; }
        public SearchQuery lang(String v)        { this.lang = v; return this; }
        public SearchQuery owner(String v)       { this.owner = v; return this; }
        public SearchQuery tierCount(Integer v)  { this.tierCount = v; return this; }
        public SearchQuery sort(String v)        { this.sort = v; return this; }
        public SearchQuery page(int v)           { this.page = v; return this; }
        public SearchQuery size(int v)           { this.size = v; return this; }

        /** Returns a cleaned-up copy: tag slugs normalized, sort/tagMode/owner coerced to the whitelist. */
        SearchQuery normalized() {
            SearchQuery n = new SearchQuery()
                    .q(q == null ? null : q.trim())
                    .lang(lang == null ? null : lang.trim())
                    .owner("me".equalsIgnoreCase(owner) ? "me" : "any")
                    .tagMode("all".equalsIgnoreCase(tagMode) ? "all" : "any")
                    .sort(switch (sort == null ? "recent" : sort.toLowerCase(Locale.ROOT)) {
                        case "relevance", "popularity" -> sort.toLowerCase(Locale.ROOT);
                        default -> "recent";
                    })
                    .tierCount(tierCount)
                    .page(page)
                    .size(size);

            if (tags != null && !tags.isEmpty()) {
                Set<String> clean = new LinkedHashSet<>();
                for (String t : tags) {
                    String slug = normalizeTag(t);
                    if (slug != null) clean.add(slug);
                }
                n.tags(clean.isEmpty() ? null : new ArrayList<>(clean));
            }
            return n;
        }
    }

    /**
     * A prepared WHERE fragment + its parameter list. Also carries the original {@link SearchQuery}
     * so {@link #withoutLanguage()} / {@link #withoutTags()} can rebuild a version of the fragment
     * minus one specific filter (for facet queries).
     */
    private final class WhereClause {
        final String sql;
        final List<Object> params;
        private final SearchQuery query;
        private final UUID currentUserId;

        WhereClause(String sql, List<Object> params, SearchQuery query, UUID currentUserId) {
            this.sql = sql;
            this.params = params;
            this.query = query;
            this.currentUserId = currentUserId;
        }

        WhereClause rebuild(boolean includeLang, boolean includeTags) {
            SearchQuery copy = new SearchQuery()
                    .q(query.q())
                    .lang(includeLang ? query.lang() : null)
                    .owner(query.owner())
                    .tagMode(query.tagMode)
                    .tierCount(query.tierCount())
                    .sort(query.sort())
                    .page(query.page())
                    .size(query.size());
            copy.tags(includeTags ? query.tags() : null);
            return buildWhere(copy, currentUserId);
        }

        WhereClause withoutLanguage() { return rebuild(false, true); }
        WhereClause withoutTags()     { return rebuild(true, false); }
    }
}