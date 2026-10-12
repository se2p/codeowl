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
package de.uni_passau.fim.se2.application.service.hints;

import java.util.Locale;

/**
 * The language a hint should be written in.
 *
 * Hint text is produced on the server — the level-1 text here, the level-2
 * explanation by the LLM — so the client has to say which language its UI is
 * currently in. Not to be confused with the programming language, which several
 * methods in this package also call "language".
 */
public enum HintLocale {
    DE, EN;

    /**
     * Maps a client locale tag onto a supported language.
     *
     * Anything unrecognised, including null, falls back to German: it is the
     * platform's default UI language and was the only language hints existed in
     * before this, so an older client that sends nothing keeps what it had.
     */
    public static HintLocale from(String tag) {
        if (tag == null) return DE;
        return tag.toLowerCase(Locale.ROOT).startsWith("en") ? EN : DE;
    }

    /** Language name for an LLM instruction, in that language. */
    public String promptName() {
        return this == EN ? "English" : "Deutsch";
    }
}
