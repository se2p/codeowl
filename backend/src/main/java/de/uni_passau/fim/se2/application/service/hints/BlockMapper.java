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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class BlockMapper {

    /**
     * Maps findings (which have line numbers) back to block indices.
     * Returns findings enriched with block index information.
     */
    public List<BlockFinding> map(List<AnalysisFinding> findings, Map<Integer, Integer> lineToBlockIndex) {
        List<BlockFinding> mapped = new ArrayList<>();
        Set<Integer> seenBlocks = new HashSet<>();

        for (AnalysisFinding finding : findings) {
            Integer blockIndex = lineToBlockIndex.get(finding.startLine());
            if (blockIndex != null && seenBlocks.add(blockIndex)) {
                // Only report each block once (even if multiple findings point to it)
                mapped.add(new BlockFinding(blockIndex, finding));
            }
        }

        return mapped;
    }

    public record BlockFinding(int blockIndex, AnalysisFinding finding) {}
}
