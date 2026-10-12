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

import com.fasterxml.jackson.annotation.*;
import lombok.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a complete sequence of tiered programming tasks.
 * Typically, includes one task per tier level (e.g., 1–3).
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TieredTaskSequenceDTO {

    /**
     * Unique identifier for the task sequence.
     *
     * @return the UUID of the sequence
     */
    private UUID id;

    /**
     * The programming language associated with this task sequence.
     *
     * @return the language name
     */
    private String programmingLanguage;

    /**
     * The list of programming concepts covered in the task sequence.
     *
     * @return a list of concepts
     */
    private List<String> concepts;

    /**
     * The list of individual tasks in the sequence.
     *
     * @return a list of task DTOs
     */
    @Builder.Default
    private List<TaskDTO> tasks = new ArrayList<>();

    /**
     * A sample code snippet optionally used to derive or support the tasks.
     *
     * @return the code snippet
     */
    private String codeSnippet;

    /**
     * A textual description of the task sequence.
     *
     * @return the task description
     */
    private String taskDescription;

    /**
     * A name the sequence owner gave this sequence, e.g. "Loops for beginners".
     * NULL when none was given; clients then show a generic heading, or "Sequence N" on a
     * worksheet, in the viewer's language.
     */
    private String title;

    /**
     * The timestamp indicating when the task sequence was created.
     *
     * @return the timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Version of the program that created this Sequence.
     */
    private UUID versionId;

    /**
     * Strategy used to generate Sequence.
     */
    private String strategyName;

    /**
     * The owner (teacher/admin) who created this sequence.
     * NULL for legacy/anonymous sequences.
     */
    private UUID ownerId;

    /**
     * The human language chosen for problem-statement generation (e.g. "de", "en").
     * NULL for legacy sequences; defaults to "de" for new ones.
     */
    @Builder.Default
    private String targetLanguage = "de";

    /**
     * AP20: Generation constraints (blacklist, whitelist, difficulty slope, etc.).
     * Stored as JSONB in the database. NULL for unconstrained sequences.
     */
    private GenerationConstraints constraints;

    /**
     * AP21: Number of tiers in this sequence (3 or 5). Default: 3.
     */
    @Builder.Default
    private int tierCount = 3;

    /**
     * AP19: Curriculum topic this sequence was generated from (optional).
     * NULL for non-curriculum sequences.
     */
    private UUID curriculumTopicId;

    /**
     * The sequence this one was copied from, if it was.
     *
     * <p>Never displayed. It is there so the library search can tell an original from a clone:
     * copying a worksheet duplicates its sequences, and a marketplace that makes copying routine
     * would otherwise fill the library with near-identical entries nobody can tell apart.
     */
    private UUID copiedFrom;

    /**
     * When this copy was first edited, or {@code null} if it never was.
     *
     * <p>Only ever set on a copy. {@link #copiedFrom} alone cannot distinguish a clone nobody
     * has touched from one its new owner has reworked into different material, because it is
     * written once and never cleared — so an edited copy stayed out of the library for good.
     * The library lists a sequence when it is an original <em>or</em> has diverged.
     *
     * <p>A timestamp rather than a flag: it is the same cost to store and it answers "since
     * when", which a boolean throws away.
     */
    private Instant divergedAt;

    /**
     * Returns the task for a given tier level, if present.
     *
     * @param tierLevel the tier level (e.g. 1, 2, 3)
     * @return an optional task with that level
     */
    public Optional<TaskDTO> getTaskByTierLevel(final int tierLevel) {
        if (tasks == null) return Optional.empty();
        return tasks.stream().filter(task -> task.getTierLevel() == tierLevel).findFirst();
    }

    /**
     * Returns the tasks ordered by tier level in ascending order.
     *
     * @return a sorted list of tasks
     */
    public List<TaskDTO> getOrderedTasks() {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .sorted(Comparator.comparingInt(TaskDTO::getTierLevel))
                .collect(Collectors.toList());
    }

    /**
     * Checks whether the sequence is complete — i.e., it has exactly 3 tasks,
     * and each tier level is unique.
     *
     * @return true if a sequence is complete, false otherwise
     */
    @JsonIgnore
    public boolean isComplete() {
        return tasks != null && !tasks.isEmpty() && tasks.stream().allMatch(Objects::nonNull);
    }

    /**
     * A detached copy of this sequence, belonging to a different owner.
     *
     * <p>Built from {@code toBuilder()} for the same reason as {@link TaskDTO#copyFor(UUID)}:
     * a field added later travels with the copy without anyone having to remember it. Naming
     * fields by hand is how {@code tierCount}, {@code targetLanguage}, {@code constraints} and
     * {@code curriculumTopicId} were lost by the worksheet copy — and silently, because the
     * {@code @Builder.Default} values stepped in and made a five-tier sequence look like a
     * perfectly ordinary three-tier one.
     *
     * <p>Reset here is only what must not be shared:
     * <ul>
     *   <li>{@code id} — a copy is a new row</li>
     *   <li>{@code ownerId} — the copy belongs to whoever made it</li>
     *   <li>{@code createdAt} — the copy is new; the database stamps it</li>
     *   <li>{@code divergedAt} — the copy has not been edited yet, however much the sequence
     *       it came from had been; inheriting it would put an untouched clone straight into
     *       the library, which is the listing rule inverted</li>
     *   <li>{@code tasks} — emptied, because the caller refills it with task copies rather
     *       than letting both sequences point at the same task objects</li>
     * </ul>
     *
     * <p>{@code concepts} is defensively copied for the same aliasing reason.
     *
     * @param newOwnerId the user the copy belongs to
     * @return a new sequence with no tasks, ready to be persisted and refilled
     */
    public TieredTaskSequenceDTO copyFor(UUID newOwnerId) {
        return toBuilder()
                .id(null)
                .ownerId(newOwnerId)
                .createdAt(null)
                .divergedAt(null)
                // Provenance points at the immediate source, not at the root of a copy chain.
                // A copy of a copy came from the copy; that is the honest answer, and following
                // the chain back is the reader's business, not this method's.
                .copiedFrom(id)
                .tasks(new ArrayList<>())
                .concepts(concepts == null ? null : new ArrayList<>(concepts))
                .build();
    }
}