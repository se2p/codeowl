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
package de.uni_passau.fim.se2.domain.model;

import de.uni_passau.fim.se2.domain.entities.UserDTO.UserRole;

import java.util.UUID;

/**
 * What a teacher may do with a sequence that sits in their worksheet but belongs to someone else.
 *
 * <p>Sequences are shared, not owned per worksheet: a library sequence can appear in many
 * worksheets at once, which is what {@code usageCount} counts. Ownership therefore stayed with
 * whoever generated it, and the teacher view — tier structure, distractors, tags, generation
 * constraints — was gated on that ownership.
 *
 * <p>The result was that a teacher who had assembled a worksheet could not inspect what they
 * had assembled. They chose the material and were then treated as a stranger to it.
 *
 * <p>The rule here separates two things that used to be one:
 *
 * <ul>
 *   <li><b>Inspecting</b> is granted to the <em>curator</em> — anyone who put the sequence into
 *       a worksheet they own. Looking at material you selected changes nothing for anybody
 *       else.</li>
 *   <li><b>Editing in place</b> stays with the owner. A curator who edits would otherwise
 *       silently rewrite the sequence for every other worksheet using it, including its
 *       author's — the sharing that makes the library useful is exactly what makes editing
 *       someone else's copy dangerous.</li>
 * </ul>
 *
 * <p>So a curator who wants to change something forks first: the sequence is copied to them,
 * their worksheet is repointed at the copy, and they edit that. {@link #mustForkToEdit} is the
 * question the UI asks to know whether to offer editing or offer the fork.
 *
 * <p>Pure functions, no Spring, no database — {@code SequenceCuratorPolicyTest} enumerates the
 * whole table, which is the only way to know a rule of this shape has no hole in it.
 */
public final class SequenceCuratorPolicy {

    private SequenceCuratorPolicy() {
    }

    /**
     * Whether someone may see the teacher view of a sequence.
     *
     * @param ownerId  the sequence's owner; {@code null} for seed material nobody owns
     * @param viewerId {@code null} when not logged in
     * @param role     {@code null} when not logged in
     * @param curator  whether the viewer owns a worksheet containing this sequence
     */
    public static boolean canInspect(UUID ownerId, UUID viewerId, UserRole role, boolean curator) {
        if (viewerId == null || role == null) {
            return false;
        }
        if (role == UserRole.STUDENT) {
            // The teacher view shows distractor plans and solutions-adjacent metadata. Being
            // enrolled in a class that uses the worksheet must not open that.
            return false;
        }
        return isOwnerOrAdmin(ownerId, viewerId, role) || curator;
    }

    /** Whether edits land on this sequence directly. Owners and admins only. */
    public static boolean canEditInPlace(UUID ownerId, UUID viewerId, UserRole role) {
        return viewerId != null && role != null
                && role != UserRole.STUDENT
                && isOwnerOrAdmin(ownerId, viewerId, role);
    }

    /**
     * Whether the viewer may edit, but only after taking a copy.
     *
     * <p>True exactly for the curator of someone else's sequence. Deliberately not true for
     * anyone else: a teacher with no claim on the sequence gets no fork offer, because forking
     * writes to their worksheet and they have none to write to.
     */
    public static boolean mustForkToEdit(UUID ownerId, UUID viewerId, UserRole role, boolean curator) {
        return canInspect(ownerId, viewerId, role, curator)
                && !canEditInPlace(ownerId, viewerId, role);
    }

    private static boolean isOwnerOrAdmin(UUID ownerId, UUID viewerId, UserRole role) {
        if (role == UserRole.ADMIN) {
            return true;
        }
        return ownerId != null && ownerId.equals(viewerId);
    }
}
