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

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class HintExplainer {

    private static final Logger logger = LoggerFactory.getLogger(HintExplainer.class);
    private final AiModelClient aiModelClient;

    public HintExplainer(AiModelClient aiModelClient) {
        this.aiModelClient = aiModelClient;
    }

    /** Convenience overload without block-count context (no tooFewBlocks detection). */
    public String explain(List<BlockMapper.BlockFinding> blockFindings, String studentCode,
                          String correctSolution, String language) {
        return explain(blockFindings, studentCode, correctSolution, language,
                List.of(), false, 0, 0);
    }

    /** AP15-aware overload without block-count context. */
    public String explain(List<BlockMapper.BlockFinding> blockFindings, String studentCode,
                          String correctSolution, String language,
                          List<Integer> distractorBlockIndices) {
        return explain(blockFindings, studentCode, correctSolution, language,
                distractorBlockIndices, false, 0, 0);
    }

    /**
     * Full overload: uses the LLM to explain the findings pedagogically.
     *
     * The prompt is designed to be:
     * - Direct: max 3 sentences, no padding
     * - Language-aware: the exact programming language (e.g. "Java") is stated twice
     *   so the LLM cannot confuse it with another language
     * - Block-count-aware: if the student placed fewer blocks than needed, the LLM is
     *   told this explicitly — without this context the explanation makes no sense
     * - Solution-aware: the correct solution is always shown so the LLM can reason
     *   about what is wrong without hallucinating
     *
     * @param tooFewBlocks             true if the student placed fewer non-distractor blocks
     *                                 than the correct solution requires
     * @param studentNonDistractorCount how many non-distractor blocks the student placed
     * @param correctBlockCount        how many blocks the correct solution has
     */
    public String explain(List<BlockMapper.BlockFinding> blockFindings, String studentCode,
                          String correctSolution, String language,
                          List<Integer> distractorBlockIndices,
                          boolean tooFewBlocks, int studentNonDistractorCount, int correctBlockCount) {
        try {
            // Capitalise language for the prompt (e.g. "java" → "Java", "python" → "Python")
            String lang = language == null ? "Java"
                    : language.substring(0, 1).toUpperCase(Locale.ROOT) + language.substring(1).toLowerCase(Locale.ROOT);

            StringBuilder prompt = new StringBuilder();

            // --- Context ---
            prompt.append("Du bist ein Programmier-Tutor. ")
                  .append("Ein Schueler loest eine ").append(lang).append("-Parsons-Aufgabe ")
                  .append("(Code-Bloecke in die richtige Reihenfolge bringen).\n\n");

            prompt.append("Programmiersprache: ").append(lang).append("\n\n");

            prompt.append("Korrekte ").append(lang).append("-Loesung (").append(correctBlockCount).append(" Bloecke):\n")
                  .append("```").append(lang.toLowerCase(Locale.ROOT)).append("\n")
                  .append(correctSolution != null ? correctSolution : "(nicht verfuegbar)")
                  .append("\n```\n\n");

            prompt.append("Aktueller Code des Schuelers (").append(studentNonDistractorCount).append(" Bloecke platziert):\n")
                  .append("```").append(lang.toLowerCase(Locale.ROOT)).append("\n")
                  .append(studentCode)
                  .append("\n```\n\n");

            // --- Problems to explain ---
            prompt.append("Gefundene Probleme:\n");

            // Block-count issue — most critical, list first
            if (tooFewBlocks) {
                prompt.append("- FEHLENDE BLOECKE: Der Schueler hat nur ")
                      .append(studentNonDistractorCount).append(" von ").append(correctBlockCount)
                      .append(" benoetigten Bloecken platziert. Es fehlen noch Bloecke im Loesungsbereich.\n");
            }

            // Distractors in solution space
            if (!distractorBlockIndices.isEmpty()) {
                prompt.append("- DISTRAKTOR");
                if (distractorBlockIndices.size() > 1) prompt.append("EN");
                prompt.append(" IM LOESUNGSBEREICH: Block");
                if (distractorBlockIndices.size() > 1) prompt.append("e");
                prompt.append(" ");
                for (int i = 0; i < distractorBlockIndices.size(); i++) {
                    if (i > 0) prompt.append(", ");
                    prompt.append(distractorBlockIndices.get(i) + 1);
                }
                prompt.append(" gehoer");
                prompt.append(distractorBlockIndices.size() == 1 ? "t" : "en");
                prompt.append(" nicht zur Loesung und mus");
                prompt.append(distractorBlockIndices.size() == 1 ? "s" : "sen");
                prompt.append(" zurueck in den Quellbereich.\n");
            }

            // Static analysis findings
            if (!blockFindings.isEmpty()) {
                for (BlockMapper.BlockFinding bf : blockFindings) {
                    AnalysisFinding f = bf.finding();
                    prompt.append("- Block ").append(bf.blockIndex() + 1).append(": ")
                          .append(f.message()).append(" [").append(f.ruleId()).append("]\n");
                }
            }

            if (!tooFewBlocks && distractorBlockIndices.isEmpty() && blockFindings.isEmpty()) {
                prompt.append("- Keine statischen Fehler, aber die Reihenfolge ist noch nicht korrekt.\n");
            }

            // --- Instruction ---
            prompt.append("\nErklaere dem Schueler auf Deutsch, was falsch ist. ")
                  .append("Sei direkt und konkret — nenne die betroffenen Bloecke (z.B. \"Block 3\"). ")
                  .append("Verrate nicht die vollstaendige Loesung. ")
                  .append("Keine technischen Rule-IDs. ")
                  .append("Maximal 3 Saetze. Kein einleitender Satz wie \"Natuerlich\" oder \"Gerne\".");

            logger.info("HintExplainer: lang={}, tooFewBlocks={} ({}/{}), distractors={}, findings={}",
                    lang, tooFewBlocks, studentNonDistractorCount, correctBlockCount,
                    distractorBlockIndices.size(), blockFindings.size());
            logger.debug("LLM prompt:\n{}", prompt);

            String response = aiModelClient.generateTaskDescription(studentCode, prompt.toString());
            logger.info("LLM response: {}", response);
            return response;

        } catch (Exception e) {
            logger.error("LLM explanation failed", e);
            return "Erklaerung konnte nicht generiert werden. Schau dir die markierten Bloecke genauer an.";
        }
    }
}
