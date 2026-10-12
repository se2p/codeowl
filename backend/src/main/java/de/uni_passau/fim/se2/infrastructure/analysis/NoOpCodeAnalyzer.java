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
package de.uni_passau.fim.se2.infrastructure.analysis;

import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * No-op fallback when tree-sitter native library is unavailable.
 * Returns zero/empty metrics so the application can still start.
 */
@Component
@ConditionalOnMissingBean(name = "treeSitterCodeAnalyzer")
public class NoOpCodeAnalyzer implements CodeAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(NoOpCodeAnalyzer.class);

    public NoOpCodeAnalyzer() {
        logger.warn("Tree-sitter unavailable — using NoOpCodeAnalyzer (all metrics will be zero)");
    }

    @Override
    public boolean supportsLanguage(String language) {
        return true; // accept everything, return zeros
    }

    @Override
    public ComplexityResult computeComplexity(String code, String language) {
        return ComplexityResult.zero();
    }

    @Override
    public AstAnalysisResult analyzeChanges(String originalCode, String editedCode, String language) {
        return new AstAnalysisResult(0, 0, 0, 0.0, List.of(), Map.of());
    }
}
