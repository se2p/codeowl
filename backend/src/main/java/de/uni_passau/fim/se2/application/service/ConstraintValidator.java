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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AP20: Post-generation validator that checks whether generated code
 * adheres to the teacher's constraint configuration (blacklist / whitelist).
 *
 * Uses regex-based construct detection. Violations are logged as warnings
 * but do not block persistence — the LLM prompt injection handles most cases,
 * and this validator provides an additional safety net for audit/logging.
 */
@Service
public class ConstraintValidator {

    private static final Logger logger = LoggerFactory.getLogger(ConstraintValidator.class);

    /**
     * Mapping of common construct keywords to regex patterns that detect them in code.
     * Covers the most common programming constructs across Python, Java, JavaScript.
     */
    private static final Map<String, Pattern> CONSTRUCT_PATTERNS = Map.ofEntries(
            Map.entry("for-loop",       Pattern.compile("\\bfor\\s*\\(")),
            Map.entry("for",            Pattern.compile("\\bfor\\b")),
            Map.entry("while",          Pattern.compile("\\bwhile\\s*\\(")),
            Map.entry("while-loop",     Pattern.compile("\\bwhile\\b")),
            Map.entry("recursion",      Pattern.compile("\\b(\\w+)\\s*\\([^)]*\\)[^{]*\\{[^}]*\\1\\s*\\(", Pattern.DOTALL)),
            Map.entry("if-else",        Pattern.compile("\\bif\\b.*\\belse\\b", Pattern.DOTALL)),
            Map.entry("if",             Pattern.compile("\\bif\\s*\\(")),
            Map.entry("switch",         Pattern.compile("\\bswitch\\s*\\(")),
            Map.entry("try-catch",      Pattern.compile("\\btry\\s*\\{.*\\bcatch\\b", Pattern.DOTALL)),
            Map.entry("lambda",         Pattern.compile("(\\blambda\\b|->|=>)")),
            Map.entry("list-comprehension", Pattern.compile("\\[.*\\bfor\\b.*\\bin\\b.*\\]")),
            Map.entry("array",          Pattern.compile("\\[\\s*\\]|new\\s+\\w+\\s*\\[")),
            Map.entry("map",            Pattern.compile("\\b(HashMap|Map|dict|\\{\\s*\\w+\\s*:)")),
            Map.entry("class",          Pattern.compile("\\bclass\\s+\\w+")),
            Map.entry("inheritance",    Pattern.compile("\\b(extends|super\\()")),
            Map.entry("interface",      Pattern.compile("\\binterface\\s+\\w+")),
            Map.entry("stream",         Pattern.compile("\\.stream\\(\\)")),
            Map.entry("exception",      Pattern.compile("\\b(throw|raise)\\b")),
            Map.entry("import",         Pattern.compile("\\b(import|from\\s+\\w+\\s+import)\\b")),
            Map.entry("print",          Pattern.compile("\\b(print|System\\.out\\.print|console\\.log)\\b")),
            Map.entry("string-formatting", Pattern.compile("(%[sdf]|\\bf['\"]|\\.format\\(|String\\.format)")),
            Map.entry("ternary",        Pattern.compile("\\?.*:")),
            Map.entry("nested-loop",    Pattern.compile("\\bfor\\b[^}]*\\bfor\\b", Pattern.DOTALL))
    );

    /**
     * Validates all tasks in a sequence against the given constraints.
     * Returns a list of violation descriptions (empty if all pass).
     */
    public List<String> validate(TieredTaskSequenceDTO sequence, GenerationConstraints constraints) {
        if (constraints == null || !constraints.hasAny()) return List.of();
        if (sequence.getTasks() == null) return List.of();

        List<String> violations = new ArrayList<>();

        for (TaskDTO task : sequence.getTasks()) {
            String code = task.getSolution();
            if (code == null || code.isBlank()) continue;

            // Check blacklist
            if (constraints.getBlacklist() != null) {
                for (String construct : constraints.getBlacklist()) {
                    if (detectConstruct(code, construct)) {
                        String msg = String.format("Tier %d: Blacklisted construct '%s' detected in solution",
                                task.getTierLevel(), construct);
                        violations.add(msg);
                        logger.warn("AP20 constraint violation — {}", msg);
                    }
                }
            }

            // Check whitelist (every construct in code must be in the whitelist)
            if (constraints.getWhitelist() != null && !constraints.getWhitelist().isEmpty()) {
                for (Map.Entry<String, Pattern> entry : CONSTRUCT_PATTERNS.entrySet()) {
                    String construct = entry.getKey();
                    if (!constraints.getWhitelist().contains(construct) && detectConstruct(code, construct)) {
                        String msg = String.format("Tier %d: Construct '%s' found but not in whitelist",
                                task.getTierLevel(), construct);
                        violations.add(msg);
                        logger.warn("AP20 constraint violation — {}", msg);
                    }
                }
            }
        }

        return violations;
    }

    /**
     * Checks if a code string contains a given construct using known patterns.
     * Falls back to simple substring match for unknown constructs.
     */
    private boolean detectConstruct(String code, String construct) {
        String key = construct.toLowerCase().trim();
        Pattern pattern = CONSTRUCT_PATTERNS.get(key);
        if (pattern != null) {
            return pattern.matcher(code).find();
        }
        // Fallback: simple keyword match
        return code.toLowerCase().contains(key);
    }
}
