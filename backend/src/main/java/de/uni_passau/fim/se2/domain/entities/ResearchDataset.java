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
package de.uni_passau.fim.se2.domain.entities;

import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;

import java.util.List;

/**
 * The four tables the admin research page can read.
 *
 * <p>Each one carries its own explicit column list rather than relying on {@code SELECT *}.
 * That is a whitelist, not a convenience: these tables sit next to student identities, and a
 * column added to one of them later must not start appearing on an admin screen, or in a CSV
 * download, because nobody thought to look. Adding it here is the deliberate act.
 *
 * <p>{@code timeColumn} is the column a date filter and the default ordering apply to. The
 * four tables disagree on what it is called, and two of them ({@code feedback_logs},
 * {@code task_submissions}) have no {@code created_at} at all.
 */
public enum ResearchDataset {

    EVENTS("events", "learning_events", "created_at", true, List.of(
            "id", "created_at", "class_id", "user_id", "task_id", "sequence_id",
            "tier_level", "event_type", "event_data")),

    FEEDBACK("feedback", "feedback_logs", "clicked_at", false, List.of(
            "id", "clicked_at", "class_id", "user_id", "task_id", "correct")),

    HINTS("hints", "hint_usage", "created_at", false, List.of(
            "id", "created_at", "user_id", "task_id", "hint_level", "hint_text",
            "llm_explanation", "findings", "block_order_at_request", "affected_block_ids")),

    SUBMISSIONS("submissions", "task_submissions", "updated_at", false, List.of(
            "user_id", "task_id", "solved", "solved_at", "updated_at",
            "block_order", "indent_levels"));

    private final String wireName;
    private final String table;
    private final String timeColumn;
    private final boolean eventTyped;
    private final List<String> columns;

    ResearchDataset(String wireName, String table, String timeColumn, boolean eventTyped,
                    List<String> columns) {
        this.wireName = wireName;
        this.table = table;
        this.timeColumn = timeColumn;
        this.eventTyped = eventTyped;
        this.columns = columns;
    }

    public String wireName()        { return wireName; }
    public String table()           { return table; }
    public String timeColumn()      { return timeColumn; }
    /** Only learning_events has an event_type, so only it accepts that filter. */
    public boolean isEventTyped()   { return eventTyped; }
    public List<String> columns()   { return columns; }

    /**
     * Maps the path segment the frontend uses onto a dataset.
     *
     * @throws DomainValidationException on anything else, so an unknown dataset is a 400 and
     *         never reaches a query — the same way logEvent refuses an unknown event type.
     */
    public static ResearchDataset fromWireName(String name) {
        for (ResearchDataset d : values()) {
            if (d.wireName.equals(name)) return d;
        }
        throw new DomainValidationException(
                "Unknown dataset: " + name + ". Allowed: events, feedback, hints, submissions");
    }
}
