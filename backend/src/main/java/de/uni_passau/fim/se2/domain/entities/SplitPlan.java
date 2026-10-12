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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * AP31: Inline-Split-Blocks. Describes how selected solution lines are decomposed into
 * 2–4 draggable fragments arranged in inline slots, with an optional fixed display
 * {@code prefix}/{@code suffix} and optional fragment distractors.
 *
 * <p>Canonical model: pure {@code fragments[]} where
 * {@code prefix + String.join("", fragments) + suffix == solution[lineIndex]}.
 * Fragments are exact contiguous substrings of the line (the manual editor produces them
 * by clicking between characters), so the join separator is the empty string.
 *
 * <p>Serialized as JSON into {@code tasks.split_plan} (JSONB).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitPlan {

    private List<SplitEntry> splits;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SplitEntry {
        /** Index (0-based) into the solution lines (split on '\n') that this entry splits. */
        private int lineIndex;
        /** 2–4 draggable fragments, in canonical order. join("") must reconstruct the line body. */
        private List<String> fragments;
        /** Optional fixed display prefix (not draggable). */
        private String prefix;
        /** Optional fixed display suffix (not draggable). */
        private String suffix;
        /** Optional fragment distractors shown in the tray. */
        private List<SplitDistractor> distractors;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SplitDistractor {
        private String text;
        /** Distractor taxonomy label (AP15), e.g. "OFF_BY_ONE". */
        private String type;
    }
}
