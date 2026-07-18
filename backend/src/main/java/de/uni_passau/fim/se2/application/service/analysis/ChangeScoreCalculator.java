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
package de.uni_passau.fim.se2.application.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * AP12 — Change-Analysis-Engine: Composite score computation and categorization.
 * Combines text, code, and AST-level metrics into a single overall score
 * and maps it to a human-readable change category.
 */
@Service
public class ChangeScoreCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ChangeScoreCalculator.class);

    /** Weight for text-level Levenshtein ratio. */
    private static final double W_TEXT = 0.25;

    /** Weight for code-level Levenshtein ratio. */
    private static final double W_CODE = 0.35;

    /** Weight for AST structural score. */
    private static final double W_AST = 0.40;

    // ── Categories ──

    public static final String NONE     = "NONE";
    public static final String COSMETIC = "COSMETIC";
    public static final String MINOR    = "MINOR";
    public static final String MAJOR    = "MAJOR";
    public static final String REWRITE  = "REWRITE";

    /** Change types that indicate purely cosmetic edits (whitespace, comments). */
    private static final Set<String> COSMETIC_CHANGE_TYPES = Set.of(
            "MODIFY_CONTENT", "ADD_LITERALS", "REMOVE_LITERALS",
            "ADD_COMMENTS", "REMOVE_COMMENTS"
    );

    // ── Public API ──

    /**
     * Computes a weighted overall change score from three component metrics.
     *
     * @param textLevenshtein   normalized Levenshtein ratio of the problem statement (0..1)
     * @param codeLevenshtein   normalized Levenshtein ratio of the code (0..1)
     * @param astStructuralScore AST structural change score (0..1)
     * @return composite score in [0.0, 1.0]
     */
    public double computeOverallScore(double textLevenshtein, double codeLevenshtein, double astStructuralScore) {
        return W_TEXT * textLevenshtein + W_CODE * codeLevenshtein + W_AST * astStructuralScore;
    }

    /**
     * Maps an overall score, code-level metric, and AST change types to a category.
     *
     * @param overallScore     composite score (0..1)
     * @param codeLevenshtein  code-level Levenshtein ratio (0..1)
     * @param astChangeTypes   list of AST change type labels (e.g., "ADD_METHODS")
     * @return one of NONE, COSMETIC, MINOR, MAJOR, REWRITE
     */
    public String categorize(double overallScore, double codeLevenshtein, List<String> astChangeTypes) {
        if (overallScore == 0.0) {
            return NONE;
        }

        // Cosmetic: very low score OR only whitespace/comment changes
        if (overallScore < 0.05 || isOnlyCosmetic(astChangeTypes)) {
            return COSMETIC;
        }

        if (overallScore < 0.25) {
            return MINOR;
        }

        if (overallScore < 0.70) {
            return MAJOR;
        }

        return REWRITE;
    }

    // ── Helpers ──

    private boolean isOnlyCosmetic(List<String> astChangeTypes) {
        if (astChangeTypes == null || astChangeTypes.isEmpty()) {
            return true; // no structural changes at all
        }
        return astChangeTypes.stream().allMatch(COSMETIC_CHANGE_TYPES::contains);
    }
}
