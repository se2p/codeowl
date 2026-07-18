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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SequenceValidator {

    public static void validateLocLimit(TieredTaskSequenceDTO seq, int maxLoc) {
        boolean tooLarge = seq.getTasks().stream()
                .anyMatch(t -> t.getLinesOfCode() > maxLoc);

        if (tooLarge) {
            throw new IllegalStateException("Sequence invalid: LOC > " + maxLoc);
        }
    }

    // ── AP31: Inline-Split-Blocks reconstruction check ───────────────────────

    /**
     * Validates a split plan against a solution. Each split must:
     * <ul>
     *   <li>reference a valid line index (0-based, split on '\n'), each line at most once;</li>
     *   <li>have 2–4 non-empty fragments;</li>
     *   <li>reconstruct its line exactly: {@code prefix + join("", fragments) + suffix == line}.</li>
     * </ul>
     * A {@code null}/empty plan is valid (no splits). Throws {@link DomainValidationException}
     * on the first violation.
     */
    public static void validateSplitPlan(SplitPlan plan, String solution) {
        if (plan == null || plan.getSplits() == null || plan.getSplits().isEmpty()) {
            return;
        }
        String[] lines = (solution == null ? "" : solution.replace("\r\n", "\n")).split("\n", -1);
        Set<Integer> seenLines = new HashSet<>();

        for (SplitPlan.SplitEntry s : plan.getSplits()) {
            int idx = s.getLineIndex();
            if (idx < 0 || idx >= lines.length) {
                throw new DomainValidationException(
                        "Split lineIndex out of range: " + idx + " (solution has " + lines.length + " lines).");
            }
            if (!seenLines.add(idx)) {
                throw new DomainValidationException("Duplicate split for lineIndex " + idx + ".");
            }
            List<String> frags = s.getFragments();
            if (frags == null || frags.size() < 2 || frags.size() > 4) {
                throw new DomainValidationException(
                        "Split at line " + idx + " must have 2–4 fragments (got "
                                + (frags == null ? 0 : frags.size()) + ").");
            }
            for (String f : frags) {
                if (f == null || f.isEmpty()) {
                    throw new DomainValidationException("Split at line " + idx + " has an empty fragment.");
                }
            }
            String prefix = s.getPrefix() == null ? "" : s.getPrefix();
            String suffix = s.getSuffix() == null ? "" : s.getSuffix();
            String reconstructed = prefix + String.join("", frags) + suffix;
            if (!reconstructed.equals(lines[idx])) {
                throw new DomainValidationException(
                        "Split at line " + idx + " does not reconstruct the line.\n  expected: ["
                                + lines[idx] + "]\n  got:      [" + reconstructed + "]");
            }
        }
    }

    /** Non-throwing variant used by the (fail-open) generation path. */
    public static boolean isValidSplitPlan(SplitPlan plan, String solution) {
        try {
            validateSplitPlan(plan, solution);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
