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
package de.uni_passau.fim.se2.domain.model;

import java.util.Locale;

/**
 * How far a worksheet reaches beyond its owner.
 *
 * <p>Three values rather than a published flag, because "shared" already meant two different
 * things before the marketplace existed. Every worksheet was readable by anyone holding its
 * link — that is how a teacher hands one to a colleague, and how a student opens one without
 * an account. Turning that into "not published" would have broken every link already in
 * circulation, so it keeps its own value and stays the default.
 *
 * <p>What is new is a worksheet being <em>findable</em> by someone who was never sent a link,
 * and that is the only thing {@link #PUBLIC} adds.
 */
public enum WorksheetVisibility {

    /** Owner and admins only. Not readable by link. */
    PRIVATE,

    /**
     * Readable by anyone holding the link, listed nowhere. The behaviour every worksheet had
     * before this enum existed, and the default for new ones.
     */
    UNLISTED,

    /** Listed in the marketplace for other teachers to find and copy. */
    PUBLIC;

    /** What a worksheet gets when nobody says otherwise — today's behaviour, unchanged. */
    public static final WorksheetVisibility DEFAULT = UNLISTED;

    /** Whether someone who was handed the link may open it. */
    public boolean readableByLink() {
        return this != PRIVATE;
    }

    /** Whether the marketplace lists it. */
    public boolean listed() {
        return this == PUBLIC;
    }

    /**
     * Parses a stored or submitted value.
     *
     * <p>Null and blank mean "unset", which is {@link #DEFAULT} rather than an error: rows
     * written before the column existed have no opinion, and neither do clients that do not
     * send the field. Anything else that is not a known value <em>is</em> an error — quietly
     * downgrading an unrecognised visibility to a permissive default is how data gets exposed.
     *
     * @param raw the value, in any case, optionally padded
     * @return the parsed visibility
     * @throws IllegalArgumentException if {@code raw} is neither blank nor a known value
     */
    public static WorksheetVisibility parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
