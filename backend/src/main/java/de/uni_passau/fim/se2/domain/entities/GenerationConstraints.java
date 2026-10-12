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
package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.util.List;

/**
 * AP20: Constraints that control what programming constructs are allowed/forbidden
 * in generated task sequences. Stored as JSONB in the sequences table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationConstraints {

    /**
     * Constructs that MUST NOT appear in generated code.
     * e.g. ["ternary", "lambda", "list comprehension"]
     */
    private List<String> blacklist;

    /**
     * If non-empty, ONLY these constructs may appear.
     * e.g. ["if/else", "for", "while", "print"]
     */
    private List<String> whitelist;

    /**
     * Controls how steeply difficulty increases across tiers.
     * Values: "gentle", "moderate", "steep"
     */
    private String difficultySlope;

    /**
     * Free-text teacher instructions injected into the generation prompt.
     * e.g. "Use German variable names", "Focus on string manipulation"
     */
    private String additionalContext;

    /**
     * Returns true if any constraint is actually set.
     */
    public boolean hasAny() {
        return (blacklist != null && !blacklist.isEmpty())
                || (whitelist != null && !whitelist.isEmpty())
                || (difficultySlope != null && !difficultySlope.isBlank())
                || (additionalContext != null && !additionalContext.isBlank());
    }
}
