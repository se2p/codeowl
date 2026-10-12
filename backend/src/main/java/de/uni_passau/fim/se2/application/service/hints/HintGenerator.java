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

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class HintGenerator {

    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language) {
        return generate(blockFindings, language, List.of(), false, 0, 0, HintLocale.DE);
    }

    /** @deprecated Use the full overload with block-count context. */
    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language,
                           List<Integer> distractorBlockIndices) {
        return generate(blockFindings, language, distractorBlockIndices, false, 0, 0, HintLocale.DE);
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
     * Written in the UI language the client reports. Note that the per-finding
     * messages in step 3 come from Ruff, javac and SpotBugs, which only emit
     * English — so a German hint stays a German frame around an English tool
     * message. Translating those would mean mapping every rule id by hand.
     *
     * @param language the programming language, not the UI language
     * @param locale   the UI language to write the hint in
     */
    public String generate(List<BlockMapper.BlockFinding> blockFindings, String language,
                           List<Integer> distractorBlockIndices,
                           boolean tooFewBlocks, int studentNonDistractorCount, int correctBlockCount,
                           HintLocale locale) {
        boolean en = locale == HintLocale.EN;
        StringBuilder sb = new StringBuilder();

        // 1. Block-count warning — most actionable hint if student has too few blocks
        if (tooFewBlocks) {
            if (en) {
                sb.append("• Only ").append(studentNonDistractorCount)
                  .append(" of ").append(correctBlockCount)
                  .append(" required blocks have been placed. Drag the missing blocks into the solution area.\n");
            } else {
                sb.append("• Es wurden nur ").append(studentNonDistractorCount)
                  .append(" von ").append(correctBlockCount)
                  .append(" benötigten Blöcken platziert. Ziehe noch fehlende Blöcke in den Lösungsbereich.\n");
            }
        }

        // 2. Distractor warning
        if (!distractorBlockIndices.isEmpty()) {
            if (distractorBlockIndices.size() == 1) {
                String block = BlockLabels.of(distractorBlockIndices.get(0));
                if (en) {
                    sb.append("• Block ").append(block)
                      .append(" does not belong in the solution (distractor). Move it back to the source area.\n");
                } else {
                    sb.append("• Block ").append(block)
                      .append(" gehört nicht zur Lösung (Distraktor). Verschiebe ihn zurück in den Quellbereich.\n");
                }
            } else {
                sb.append(en ? "• Blocks " : "• Blöcke ");
                for (int i = 0; i < distractorBlockIndices.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(BlockLabels.of(distractorBlockIndices.get(i)));
                }
                sb.append(en
                        ? " do not belong in the solution (distractors). Move them back to the source area.\n"
                        : " gehören nicht zur Lösung (Distraktoren). Verschiebe sie zurück in den Quellbereich.\n");
            }
        }

        // 3. Static analysis findings (already limited to max 2 by HintService)
        for (BlockMapper.BlockFinding bf : blockFindings) {
            AnalysisFinding f = bf.finding();
            sb.append("• Block ").append(BlockLabels.of(bf.blockIndex()))
              .append(" [").append(f.ruleId()).append("]: ")
              .append(f.message()).append("\n");
        }

        if (sb.isEmpty()) {
            return en
                    ? "No static errors found. Check the order of your blocks."
                    : "Keine statischen Fehler gefunden. Prüfe die Reihenfolge deiner Blöcke.";
        }

        return sb.toString().trim();
    }
}
