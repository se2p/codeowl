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
package de.uni_passau.fim.se2.domain.interfaces;

import java.util.List;
import java.util.Map;

/**
 * AP26 — Domain interface for language-agnostic code analysis.
 * Implementations live in the infrastructure layer (e.g. tree-sitter).
 */
public interface CodeAnalyzer {

    /**
     * Computes complexity metrics for a code snippet in the given language.
     */
    ComplexityResult computeComplexity(String code, String language);

    /**
     * Compares two code versions and returns structural change metrics.
     */
    AstAnalysisResult analyzeChanges(String originalCode, String editedCode, String language);

    /**
     * Returns true if this analyzer supports the given language.
     */
    boolean supportsLanguage(String language);

    // ── Result records ──

    record ComplexityResult(
            int linesOfCode,
            int cyclomaticComplexity,
            int maxNestingDepth,
            int methodCallCount,
            int distinctIdentifierCount
    ) {
        public static ComplexityResult zero() {
            return new ComplexityResult(0, 0, 0, 0, 0);
        }
    }

    record AstAnalysisResult(
            int nodesAdded,
            int nodesRemoved,
            int nodesModified,
            double structuralScore,
            List<String> changeTypes,
            Map<String, Object> distractorChanges
    ) {}
}
