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

import de.uni_passau.fim.se2.domain.entities.ResearchDataset;
import de.uni_passau.fim.se2.domain.entities.ResearchLog;
import de.uni_passau.fim.se2.domain.interfaces.ResearchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Filtered reads over the research tables.
 *
 * <p>Table and column names are never taken from the request — they come from
 * {@link ResearchDataset}, which whitelists them — so the string building below concatenates
 * only names this code controls. Everything the caller supplies is a bound parameter.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostgreResearchLogDao implements ResearchLogRepository {

    private final JdbcTemplate jdbc;

    /** Rows fetched per round trip while streaming an export. */
    private static final int EXPORT_FETCH_SIZE = 500;

    /**
     * Class scoping for the two tables that have no class_id of their own.
     *
     * <p>A row belongs to a class when that class both enrols the student and has the task
     * assigned through one of its worksheets. Membership alone is not enough: a student in two
     * classes would have all their work counted twice, once under each.
     */
    private static final String CLASS_VIA_MEMBERSHIP = """
            EXISTS (
                SELECT 1
                FROM class_students cs
                JOIN class_worksheets cw    ON cw.class_id     = cs.class_id
                JOIN worksheets_sequence ws ON ws.worksheet_id = cw.worksheet_id
                JOIN tasks tk               ON tk.sequence_id  = ws.sequence_id
                WHERE cs.student_id = t.user_id
                  AND tk.id         = t.task_id
                  AND cs.class_id   = ?
            )""";

    private record Where(String sql, List<Object> args) {}

    private Where buildWhere(ResearchDataset dataset, ResearchLog.Filter filter) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (filter.classId() != null) {
            switch (dataset) {
                // learning_events.class_id is resolved at write time (V22); feedback_logs has
                // carried one since V1. Both survive their user_id being anonymised.
                case EVENTS, FEEDBACK -> clauses.add("t.class_id = ?");
                case HINTS, SUBMISSIONS -> clauses.add(CLASS_VIA_MEMBERSHIP);
            }
            args.add(filter.classId());
        }

        if (dataset.isEventTyped() && filter.eventType() != null && !filter.eventType().isBlank()) {
            clauses.add("t.event_type = ?");
            args.add(filter.eventType());
        }

        if (filter.from() != null) {
            clauses.add("t." + dataset.timeColumn() + " >= ?");
            args.add(Timestamp.valueOf(filter.from()));
        }
        if (filter.to() != null) {
            clauses.add("t." + dataset.timeColumn() + " < ?");
            args.add(Timestamp.valueOf(filter.to()));
        }

        String sql = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return new Where(sql, args);
    }

    private String selectFrom(ResearchDataset dataset) {
        String columns = String.join(", ", dataset.columns().stream().map(c -> "t." + c).toList());
        return "SELECT " + columns + " FROM " + dataset.table() + " t";
    }

    /** task_submissions is keyed by (user_id, task_id) and has no id to break ties on. */
    private String tiebreak(ResearchDataset dataset) {
        return dataset == ResearchDataset.SUBMISSIONS ? "t.user_id, t.task_id" : "t.id";
    }

    private String orderBy(ResearchDataset dataset) {
        return " ORDER BY t." + dataset.timeColumn() + " DESC NULLS LAST, " + tiebreak(dataset);
    }

    @Override
    public List<Map<String, Object>> findPage(ResearchDataset dataset, ResearchLog.Filter filter) {
        Where where = buildWhere(dataset, filter);
        String sql = selectFrom(dataset) + where.sql() + orderBy(dataset) + " LIMIT ? OFFSET ?";

        List<Object> args = new ArrayList<>(where.args());
        args.add(filter.size());
        args.add((long) filter.page() * filter.size());

        return jdbc.query(sql, (rs, rowNum) -> toRow(dataset, rs), args.toArray());
    }

    @Override
    public long countMatching(ResearchDataset dataset, ResearchLog.Filter filter) {
        Where where = buildWhere(dataset, filter);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + dataset.table() + " t" + where.sql(),
                Long.class, where.args().toArray()
        );
        return count != null ? count : 0L;
    }

    @Override
    public ResearchLog.Summary summarize(ResearchDataset dataset, ResearchLog.Filter filter) {
        Where where = buildWhere(dataset, filter);

        // One pass for all three counts. COUNT(DISTINCT ...) already ignores NULLs, which is
        // exactly right here: an anonymised row is not a student anything can count.
        String totalsSql = "SELECT COUNT(*) AS total_rows,"
                + " COUNT(DISTINCT t.user_id) AS identified_users,"
                + " COUNT(*) FILTER (WHERE t.user_id IS NULL) AS anonymous_rows"
                + " FROM " + dataset.table() + " t" + where.sql();

        ResearchLog.Summary totals = jdbc.queryForObject(totalsSql,
                (rs, rowNum) -> new ResearchLog.Summary(
                        rs.getLong("total_rows"),
                        rs.getLong("identified_users"),
                        rs.getLong("anonymous_rows"),
                        Map.of()),
                where.args().toArray()
        );
        if (totals == null) {
            totals = new ResearchLog.Summary(0L, 0L, 0L, Map.of());
        }
        if (!dataset.isEventTyped()) {
            return totals;
        }

        // Drives the event-type dropdown as well as the strip, so it lists the types actually
        // present under the current filter rather than all 28 the allowlist permits.
        Map<String, Long> counts = new LinkedHashMap<>();
        RowCallbackHandler collectCounts =
                rs -> counts.put(rs.getString("event_type"), rs.getLong("n"));
        jdbc.query("SELECT t.event_type, COUNT(*) AS n FROM " + dataset.table() + " t" + where.sql()
                        + " GROUP BY t.event_type ORDER BY n DESC, t.event_type",
                collectCounts,
                where.args().toArray()
        );
        return new ResearchLog.Summary(
                totals.totalRows(), totals.identifiedUsers(), totals.anonymousRows(), counts);
    }

    @Override
    public void forEachMatching(ResearchDataset dataset, ResearchLog.Filter filter,
                                Consumer<Map<String, Object>> consumer) {
        Where where = buildWhere(dataset, filter);
        String sql = selectFrom(dataset) + where.sql() + orderBy(dataset);

        // RowCallbackHandler, so an export never builds the whole result as a List first. The
        // fetch size only takes effect inside a transaction with autocommit off, which is why
        // the calling service is @Transactional(readOnly = true).
        RowCallbackHandler emitRow = rs -> consumer.accept(toRow(dataset, rs));
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setFetchSize(EXPORT_FETCH_SIZE);
            List<Object> args = where.args();
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            return ps;
        }, emitRow);
    }

    /**
     * One row as a column map, in the dataset's declared column order.
     *
     * <p>Values are converted the way the research ZIP already converts them, so a row reads
     * the same whether it arrives as JSON on the page or as a field in a CSV: jsonb as its raw
     * JSON text, timestamps as ISO-8601. Anything else is left to Jackson.
     */
    private Map<String, Object> toRow(ResearchDataset dataset, ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : dataset.columns()) {
            row.put(column, toWireValue(rs.getObject(column)));
        }
        return row;
    }

    private Object toWireValue(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (value instanceof PGobject pg) return pg.getValue();
        return value;
    }
}
