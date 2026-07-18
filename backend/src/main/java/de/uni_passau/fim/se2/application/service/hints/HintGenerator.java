/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class HintGenerator {

    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language) {
        return generate(blockFindings, language, List.of(), false, 0, 0);
    }

    /** @deprecated Use the full overload with block-count context. */
    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language,
                           List<Integer> distractorBlockIndices) {
        return generate(blockFindings, language, distractorBlockIndices, false, 0, 0);
    }

    /**
     * Generates hint text for Level-1 hints (static, no LLM).
     *
     * Priority order:
     *   1. Too few blocks placed (student has not moved enough blocks into the solution area)
     *   2. Distractors in the solution space (blocks that must not be there)
     *   3. Static analysis findings (compiler / linter errors per block)
     *   4. Fallback: no issues found, check block order
     *
     * All text is in German to match the UI language.
     */
    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language,
                           List<Integer> distractorBlockIndices,
                           boolean tooFewBlocks, int studentNonDistractorCount, int correctBlockCount) {
        StringBuilder sb = new StringBuilder();

        // 1. Block-count warning — most actionable hint if student has too few blocks
        if (tooFewBlocks) {
            sb.append("• Es wurden nur ").append(studentNonDistractorCount)
              .append(" von ").append(correctBlockCount)
              .append(" benoetigten Bloecken platziert. Ziehe noch fehlende Bloecke in den Loesungsbereich.\n");
        }

        // 2. Distractor warning
        if (!distractorBlockIndices.isEmpty()) {
            if (distractorBlockIndices.size() == 1) {
                sb.append("• Block ").append(distractorBlockIndices.get(0) + 1)
                  .append(" gehoert nicht zur Loesung (Distraktor). Verschiebe ihn zurueck in den Quellbereich.\n");
            } else {
                sb.append("• Bloecke ");
                for (int i = 0; i < distractorBlockIndices.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(distractorBlockIndices.get(i) + 1);
                }
                sb.append(" gehoeren nicht zur Loesung (Distraktoren). Verschiebe sie zurueck in den Quellbereich.\n");
            }
        }

        // 3. Static analysis findings (already limited to max 2 by HintService)
        for (BlockMapper.BlockFinding bf : blockFindings) {
            AnalysisFinding f = bf.finding();
            sb.append("• Block ").append(bf.blockIndex() + 1)
              .append(" [").append(f.ruleId()).append("]: ")
              .append(f.message()).append("\n");
        }

        if (sb.isEmpty()) {
            return "Keine statischen Fehler gefunden. Pruefe die Reihenfolge deiner Bloecke.";
        }

        return sb.toString().trim();
    }
}
