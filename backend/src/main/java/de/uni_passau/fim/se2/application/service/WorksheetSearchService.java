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

import de.uni_passau.fim.se2.application.query.WorksheetSearchQuery;
import de.uni_passau.fim.se2.application.query.WorksheetSearchResult;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The marketplace's read side: finding worksheets other teachers have published.
 *
 * <p>A service holding SQL rather than a repository behind an interface, following the reasoning
 * {@link SequenceSearchService} sets out: the query is assembled from whichever filters the
 * teacher ticked, so the SQL <em>is</em> the behaviour and hiding it behind a port would hide
 * the only part worth reading.
 *
 * <p>Where it departs from that service is the vocabulary. Query and result are records in
 * {@code application.query} with no {@code api} or {@code infrastructure} imports, rather than
 * the DTOs the web layer happens to serialise. That keeps the dependency pointing inwards and,
 * more usefully, makes {@link #buildWhere} a pure function returning a value — so the filter
 * logic, which is where the mistakes live, can be tested without a database.
 *
 * <h2>What a worksheet is searchable by</h2>
 *
 * <p>Its own name and description, through the generated {@code search_vector}, and the text of
 * the sequences it holds, through an {@code EXISTS}. The second half matters more than it
 * sounds: a worksheet called "Blatt 3" says nothing, while the sequences inside it are about
 * recursion. A generated column cannot reach across tables, hence the join at query time.
 *
 * <h2>Injection safety</h2>
 *
 * <ul>
 *   <li>Every value the caller supplies is a bind parameter, never concatenated.</li>
 *   <li>The only interpolated fragments are constants in this file: the sort column, chosen
 *       from a whitelist by {@link WorksheetSearchQuery}, and the shared tsquery expression.</li>
 * </ul>
 */
@Service
public class WorksheetSearchService {

    /**
     * Matches the Library Inbox by the sentinel in its description, the same way
     * {@link WorksheetService} finds it. Excluded from the marketplace unconditionally: it is a
     * teacher's pile of one-click clippings, not something anyone means to publish. The backend
     * refuses to publish it at all, so this is belt and braces — but a listing that can only be
     * wrong in one direction is worth the extra clause.
     */
    private static final String LIBRARY_INBOX_PATTERN =
            "%" + WorksheetService.LIBRARY_INBOX_SENTINEL + "%";

    /** Same control-character markers the sequence search emits, so one frontend helper renders both. */
    private static final String HEADLINE_OPTIONS =
            "StartSel=" + SequenceSearchService.HIGHLIGHT_START_MARKER
                    + ", StopSel=" + SequenceSearchService.HIGHLIGHT_STOP_MARKER
                    + ", MaxWords=20, MinWords=5, MaxFragments=1, HighlightAll=false";

    private final JdbcTemplate jdbc;

    public WorksheetSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Runs a marketplace search.
     *
     * @param query         what was asked for, already normalised
     * @param currentUserId the caller, or {@code null} when anonymous
     * @return one page of results plus the facet counts for the sidebar
     */
    public WorksheetSearchResult search(WorksheetSearchQuery query, UUID currentUserId) {
        WorksheetSearchQuery q = query == null
                ? WorksheetSearchQuery.of(null, null, false, null, 0, 0)
                : query;

        WhereClause where = buildWhere(q, currentUserId);

        long total = count(where);
        List<WorksheetSearchResult.Item> items = total == 0
                ? List.of()
                : fetchPage(q, where, currentUserId);

        return new WorksheetSearchResult(items, q.page(), q.size(), total, fetchFacets(q, currentUserId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // WHERE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The filter every one of the three queries below shares.
     *
     * <p>Package-private and returning a value rather than mutating a field, so
     * {@code WorksheetSearchQueryTest} can assert on the generated SQL and its parameters
     * without a database in sight. The count, the page and the facets all take the same clause,
     * which is the only way they can be guaranteed to agree about what matched.
     *
     * @param includeLanguageFilter false when building the facet query for languages, which has
     *                              to count the languages a teacher has <em>not</em> filtered to
     *                              or the sidebar would only ever show the one they picked
     */
    static WhereClause buildWhere(WorksheetSearchQuery q, UUID currentUserId,
                                  boolean includeLanguageFilter) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        // The marketplace lists published worksheets and nothing else. Not a filter the caller
        // can influence: it is the reason the endpoint is safe to expose at all.
        sql.append(" AND w.visibility = ? ");
        params.add(WorksheetVisibility.PUBLIC.name());

        sql.append(" AND COALESCE(w.description, '') NOT LIKE ? ");
        params.add(LIBRARY_INBOX_PATTERN);

        if (q.onlyMine()) {
            if (currentUserId == null) {
                // "Only mine" without knowing who that is cannot mean "everyone". Short-circuit
                // to no results rather than silently widening the search.
                sql.append(" AND 1=0 ");
            } else {
                sql.append(" AND w.owner_id = ? ");
                params.add(currentUserId);
            }
        }

        if (includeLanguageFilter && q.lang() != null) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM worksheets_sequence ws
                                   JOIN tiered_task_sequences s ON s.id = ws.sequence_id
                                  WHERE ws.worksheet_id = w.id
                                    AND s.programming_language = ?)
                    """);
            params.add(q.lang());
        }

        if (q.hasText()) {
            // Either the worksheet's own words, or the words of anything inside it. The inner
            // arm reaches sequences and their tasks, so searching "rekursion" finds a worksheet
            // called "Blatt 3" whose sequences are about recursion — which is how a teacher
            // actually looks for material.
            sql.append(" AND ( w.search_vector @@ ").append(FullTextSearch.TSQUERY_EXPR);
            FullTextSearch.addTsqueryParams(params, q.q());

            sql.append("""
                        OR EXISTS (SELECT 1 FROM worksheets_sequence ws
                                     JOIN tiered_task_sequences s ON s.id = ws.sequence_id
                                    WHERE ws.worksheet_id = w.id
                                      AND (s.search_vector @@\s""");
            sql.append(FullTextSearch.TSQUERY_EXPR);
            FullTextSearch.addTsqueryParams(params, q.q());

            sql.append("""
                             OR EXISTS (SELECT 1 FROM tasks t
                                         WHERE t.sequence_id = s.id
                                           AND t.search_vector @@\s""");
            sql.append(FullTextSearch.TSQUERY_EXPR);
            FullTextSearch.addTsqueryParams(params, q.q());
            // Four closing parens for four opened: the outer AND (, the sequence EXISTS (,
            // the AND ( around the sequence arm, and the task EXISTS (.
            sql.append(")))) ");
        }

        return new WhereClause(sql.toString(), params);
    }

    static WhereClause buildWhere(WorksheetSearchQuery q, UUID currentUserId) {
        return buildWhere(q, currentUserId, true);
    }

    /** A filter and the values it binds, kept together so they cannot drift apart. */
    record WhereClause(String sql, List<Object> params) {
        Object[] args() {
            return params.toArray();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────

    private long count(WhereClause where) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM worksheets w " + where.sql(), Long.class, where.args());
        return total == null ? 0L : total;
    }

    private List<WorksheetSearchResult.Item> fetchPage(WorksheetSearchQuery q, WhereClause where,
                                                       UUID currentUserId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT w.id, w.name, w.description, w.owner_id, w.created_at,
                       (SELECT COUNT(*) FROM worksheets_sequence ws WHERE ws.worksheet_id = w.id)
                           AS sequence_count,
                       (SELECT COUNT(*) FROM worksheets c WHERE c.copied_from = w.id)
                           AS copy_count,
                       (SELECT COALESCE(array_agg(DISTINCT s.programming_language), '{}')
                          FROM worksheets_sequence ws
                          JOIN tiered_task_sequences s ON s.id = ws.sequence_id
                         WHERE ws.worksheet_id = w.id
                           AND s.programming_language IS NOT NULL) AS languages,
                """);

        // "You already have this" — one EXISTS in the same statement rather than a second round
        // trip per card, and the reason worksheets carry copied_from at all.
        if (currentUserId != null) {
            sql.append("""
                           EXISTS (SELECT 1 FROM worksheets c
                                    WHERE c.copied_from = w.id AND c.owner_id = ?) AS already_copied,
                    """);
            params.add(currentUserId);
        } else {
            sql.append(" FALSE AS already_copied, ");
        }

        if (q.hasText()) {
            // Highlight the name when it matched, otherwise the description: a snippet of the
            // title is more use on a card than the first twenty words of a description that
            // happens to mention the term.
            //
            // HIGHLIGHT_TSQUERY_EXPR, not TSQUERY_EXPR: ts_headline re-tokenizes the raw column
            // text on every call rather than reading search_vector, so the search_normalize'd
            // query TSQUERY_EXPR uses would never match a raw umlaut lexeme in either direction.
            // See its Javadoc in FullTextSearch.
            sql.append(" ts_headline('simple', COALESCE(NULLIF(w.name, ''), COALESCE(w.description, '')), ")
               .append(FullTextSearch.HIGHLIGHT_TSQUERY_EXPR)
               .append(", ?) AS highlight ");
            FullTextSearch.addHighlightTsqueryParams(params, q.q());
            params.add(HEADLINE_OPTIONS);
        } else {
            sql.append(" NULL AS highlight ");
        }

        sql.append(" FROM worksheets w ").append(where.sql());
        params.addAll(where.params());

        sql.append(orderBy(q));
        if (WorksheetSearchQuery.SORT_RELEVANCE.equals(q.sort())) {
            FullTextSearch.addTsqueryParams(params, q.q());
        }

        sql.append(" LIMIT ? OFFSET ? ");
        params.add(q.size());
        params.add(q.offset());

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapItem(rs), params.toArray());
    }

    /**
     * The sort clause. Only constants reach the SQL here — {@link WorksheetSearchQuery} has
     * already reduced whatever the client sent to one of three known values.
     *
     * <p>Every sort ends with {@code w.id} so paging is stable: without a total order, two
     * worksheets created in the same second could swap places between page one and page two and
     * one of them would never be seen.
     */
    private static String orderBy(WorksheetSearchQuery q) {
        return switch (q.sort()) {
            case WorksheetSearchQuery.SORT_RELEVANCE ->
                    " ORDER BY ts_rank_cd(w.search_vector, " + FullTextSearch.TSQUERY_EXPR
                            + ") DESC, w.created_at DESC, w.id ";
            case WorksheetSearchQuery.SORT_POPULARITY ->
                    " ORDER BY (SELECT COUNT(*) FROM worksheets c WHERE c.copied_from = w.id) DESC,"
                            + " w.created_at DESC, w.id ";
            default -> " ORDER BY w.created_at DESC, w.id ";
        };
    }

    private WorksheetSearchResult.Facets fetchFacets(WorksheetSearchQuery q, UUID currentUserId) {
        WhereClause withoutLanguage = buildWhere(q, currentUserId, false);

        List<WorksheetSearchResult.Bucket> languages = jdbc.query("""
                SELECT s.programming_language AS name, COUNT(DISTINCT w.id) AS cnt
                  FROM worksheets w
                  JOIN worksheets_sequence ws ON ws.worksheet_id = w.id
                  JOIN tiered_task_sequences s ON s.id = ws.sequence_id
                """ + withoutLanguage.sql() + """
                   AND s.programming_language IS NOT NULL
                 GROUP BY s.programming_language
                 ORDER BY cnt DESC, s.programming_language
                """,
                (rs, rowNum) -> new WorksheetSearchResult.Bucket(rs.getString("name"), rs.getLong("cnt")),
                withoutLanguage.args());

        // Ownership counts ignore the "only mine" filter on purpose: they are what the toggle is
        // labelled with, so counting them through the toggle would make it read "mine (12)"
        // whether or not it was switched on.
        WorksheetSearchQuery bothSides = new WorksheetSearchQuery(
                q.q(), q.lang(), false, q.sort(), q.page(), q.size());
        WhereClause unfiltered = buildWhere(bothSides, currentUserId);

        long mine = 0;
        if (currentUserId != null) {
            List<Object> params = new ArrayList<>(unfiltered.params());
            params.add(currentUserId);
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM worksheets w " + unfiltered.sql() + " AND w.owner_id = ?",
                    Long.class, params.toArray());
            mine = count == null ? 0 : count;
        }
        Long all = jdbc.queryForObject(
                "SELECT COUNT(*) FROM worksheets w " + unfiltered.sql(), Long.class, unfiltered.args());
        long total = all == null ? 0 : all;

        return new WorksheetSearchResult.Facets(languages, mine, total - mine);
    }

    private static WorksheetSearchResult.Item mapItem(ResultSet rs) throws SQLException {
        Array languages = rs.getArray("languages");
        List<String> languageList = languages == null
                ? List.of()
                : Arrays.stream((String[]) languages.getArray()).sorted().toList();

        var createdAt = rs.getTimestamp("created_at");
        return new WorksheetSearchResult.Item(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("owner_id", UUID.class),
                createdAt == null ? null : createdAt.toInstant(),
                rs.getInt("sequence_count"),
                languageList,
                rs.getLong("copy_count"),
                rs.getBoolean("already_copied"),
                rs.getString("highlight"));
    }
}
