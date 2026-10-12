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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The label a hint uses for a block has to be the one the solve page badges it
 * with. lib/blockLabel.ts on the frontend implements the same scheme; if either
 * side changes, a hint starts pointing at the wrong block.
 */
class BlockLabelsTest {

    @ParameterizedTest
    @CsvSource({"0,A", "1,B", "25,Z", "26,AA", "27,AB", "51,AZ", "52,BA", "701,ZZ", "702,AAA"})
    @DisplayName("counts like spreadsheet columns")
    void labels(int index, String expected) {
        assertEquals(expected, BlockLabels.of(index));
    }

    @Test
    @DisplayName("labels are unique, so two blocks are never named the same")
    void unique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(BlockLabels.of(i)), "duplicate label at index " + i);
        }
    }

    @Test
    @DisplayName("a negative index yields a placeholder rather than throwing")
    void negative() {
        // Losing a hint to an exception would be worse than an odd-looking label.
        assertEquals("?", BlockLabels.of(-1));
    }
}
