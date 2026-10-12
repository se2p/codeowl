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
package de.uni_passau.fim.se2.application.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AP30 (Fill-in-the-Gaps): helpers for the inline gap marker
 * {@code $$blank:<expected>$$} / {@code $$blank:<expected>|<mode>$$}
 * with {@code mode ∈ {exact, trim, ci}} (default {@code trim}).
 *
 * <p>Gaps are stored inline inside the task {@code solution} (no separate DB column).
 * Wherever the solution is treated as <em>executable code</em> — complexity analysis,
 * distractor generation, hint pipeline — the markers must first be replaced by their
 * expected value so the code stays syntactically usable. This mirrors how parsons.js
 * strips {@code #distractor}/{@code #context}/{@code #group} markers on the client.
 */
public final class GapMarkers {

    /** Matches {@code $$blank$$} and {@code $$blank:<expected>[|<mode>]$$} (expected may not contain '$'). */
    private static final Pattern BLANK = Pattern.compile("\\$\\$blank(?::([^$]*))?\\$\\$");

    private GapMarkers() {
    }

    /**
     * Replaces every gap marker with its expected value (empty string when the marker
     * carries no expected value), reconstructing the intended full line of code.
     *
     * @param code solution code possibly containing gap markers (may be {@code null})
     * @return the code with all gap markers resolved to their expected value
     */
    public static String strip(final String code) {
        if (code == null || !code.contains("$$blank")) {
            return code;
        }
        Matcher m = BLANK.matcher(code);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(expectedOf(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** True if the code contains at least one gap marker. */
    public static boolean hasGaps(final String code) {
        return code != null && BLANK.matcher(code).find();
    }

    /** Extracts the expected value from a marker body ({@code <expected>[|<mode>]}), or "" when absent. */
    private static String expectedOf(final String inner) {
        if (inner == null) {
            return "";
        }
        int sep = inner.lastIndexOf('|');
        if (sep >= 0) {
            String mode = inner.substring(sep + 1);
            if (mode.equals("exact") || mode.equals("trim") || mode.equals("ci")) {
                return inner.substring(0, sep);
            }
        }
        return inner;
    }
}
