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

/**
 * How a block is named when a student is told about it.
 *
 * The solve page badges a highlighted block with a letter, so hint text has to
 * use the same letter. It used to say "Block 1" while the badge on that very
 * block read "A", leaving the student to guess what the hint meant.
 *
 * Letters rather than numbers on purpose: a block's position is not its line
 * number, and a bare number invites reading it as one.
 *
 * Beyond Z it continues like spreadsheet columns — AA, AB — so it never runs
 * out however many blocks a task has. The frontend mirrors this in
 * lib/blockLabel.ts; the two have to agree.
 */
public final class BlockLabels {

    private BlockLabels() {
    }

    /**
     * @param index zero-based position of the block in the solution area
     * @return "A" for 0, "Z" for 25, "AA" for 26, and so on
     */
    public static String of(int index) {
        if (index < 0) {
            // Nothing sensible to name; callers should not reach this, and a
            // thrown exception here would cost a student their hint.
            return "?";
        }
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }
}
