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
package de.uni_passau.fim.se2.domain.exceptions;

/**
 * The submitted code snippet is in a language CodeOwl cannot build a task sequence for.
 *
 * <p>A {@link GenerationException}, but not a server fault: the request was well formed and the
 * model answered correctly, the input simply is not Python or Java. That distinction is the
 * reason this type exists -- the generic generation failure maps to 502, which tells a teacher
 * to try again, and trying again with the same snippet fails identically every time.
 *
 * <p>{@link #CODE} travels to the browser in the error response so the frontend can show a
 * translated message. The exception's own message is the server-side record and the fallback
 * for any client that does not know the code.
 */
public class UnsupportedLanguageException extends GenerationException {

    /** Stable identifier for the frontend to translate against; never shown to a user as-is. */
    public static final String CODE = "UNSUPPORTED_LANGUAGE";

    public UnsupportedLanguageException(String message) {
        super(message);
    }
}
