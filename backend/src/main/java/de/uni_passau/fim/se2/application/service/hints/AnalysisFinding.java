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

public record AnalysisFinding(
    String ruleId,       // "F821", "NP_NULL_ON_SOME_PATH", "compiler:not-initialized"
    String message,      // "Undefined name 'x'"
    String severity,     // "error", "warning", "info"
    int startLine,       // Line number in assembled code
    int endLine,
    String tool,         // "ruff", "javac", "spotbugs"
    String category      // "undefined_variable", "null_safety", "dead_code", "syntax"
) {}
