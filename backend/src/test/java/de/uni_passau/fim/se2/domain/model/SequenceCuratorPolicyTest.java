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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every combination of owner, role and curator, walked exhaustively.
 *
 * <p>The rule decides two different things — who may look, and whose edits land where — and the
 * dangerous mistakes are the ones where those two come apart: a curator allowed to edit in
 * place would silently rewrite a sequence inside other teachers' worksheets, and a student
 * granted inspection would be handed the distractor plans for tasks they are about to solve.
 */
class SequenceCuratorPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CURATOR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Nested
    @DisplayName("the owner")
    class Owner {

        @Test
        @DisplayName("inspects and edits in place, and is never asked to fork")
        void ownerEditsDirectly() {
            assertAll(
                    () -> assertTrue(SequenceCuratorPolicy.canInspect(OWNER, OWNER, UserRole.TEACHER, false)),
                    () -> assertTrue(SequenceCuratorPolicy.canEditInPlace(OWNER, OWNER, UserRole.TEACHER)),
                    () -> assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, OWNER, UserRole.TEACHER, false)));
        }

        @Test
        @DisplayName("is still the owner when they also curate it into a worksheet")
        void ownershipWinsOverCuratorship() {
            // The common case: you generated the sequence and put it in your own worksheet.
            // Being both must not downgrade you into forking copies of your own material.
            assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, OWNER, UserRole.TEACHER, true));
        }
    }

    @Nested
    @DisplayName("the curator")
    class Curator {

        @Test
        @DisplayName("may look at what they put in their worksheet")
        void curatorInspects() {
            // The whole point: a teacher who assembled a worksheet was being treated as a
            // stranger to the material they had chosen.
            assertTrue(SequenceCuratorPolicy.canInspect(OWNER, CURATOR, UserRole.TEACHER, true));
        }

        @Test
        @DisplayName("may not edit in place, because the sequence is in other worksheets too")
        void curatorDoesNotEditInPlace() {
            assertAll(
                    () -> assertFalse(SequenceCuratorPolicy.canEditInPlace(OWNER, CURATOR, UserRole.TEACHER)),
                    () -> assertTrue(SequenceCuratorPolicy.mustForkToEdit(OWNER, CURATOR, UserRole.TEACHER, true)));
        }

        @Test
        @DisplayName("curates seed material nobody owns just the same")
        void ownerlessSequences() {
            // Most of the library predates ownership and has owner_id NULL. If that counted as
            // "not yours", the feature would miss the case it was reported for.
            assertAll(
                    () -> assertTrue(SequenceCuratorPolicy.canInspect(null, CURATOR, UserRole.TEACHER, true)),
                    () -> assertFalse(SequenceCuratorPolicy.canEditInPlace(null, CURATOR, UserRole.TEACHER)),
                    () -> assertTrue(SequenceCuratorPolicy.mustForkToEdit(null, CURATOR, UserRole.TEACHER, true)));
        }
    }

    @Nested
    @DisplayName("everyone else")
    class Others {

        @Test
        @DisplayName("a teacher with no claim on the sequence sees nothing and is offered no fork")
        void strangerGetsNothing() {
            // No fork offer either: forking writes into a worksheet, and a stranger has none
            // here to write into.
            assertAll(
                    () -> assertFalse(SequenceCuratorPolicy.canInspect(OWNER, STRANGER, UserRole.TEACHER, false)),
                    () -> assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, STRANGER, UserRole.TEACHER, false)));
        }

        @Test
        @DisplayName("a student never inspects, not even one whose class uses the worksheet")
        void studentsExcluded() {
            assertAll(
                    () -> assertFalse(SequenceCuratorPolicy.canInspect(OWNER, CURATOR, UserRole.STUDENT, true)),
                    () -> assertFalse(SequenceCuratorPolicy.canEditInPlace(CURATOR, CURATOR, UserRole.STUDENT)),
                    () -> assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, CURATOR, UserRole.STUDENT, true)));
        }

        @Test
        @DisplayName("an anonymous visitor inspects nothing")
        void anonymousExcluded() {
            assertAll(
                    () -> assertFalse(SequenceCuratorPolicy.canInspect(OWNER, null, null, true)),
                    () -> assertFalse(SequenceCuratorPolicy.canEditInPlace(OWNER, null, null)),
                    () -> assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, null, null, true)));
        }

        @Test
        @DisplayName("an admin edits anything in place")
        void adminEditsDirectly() {
            assertAll(
                    () -> assertTrue(SequenceCuratorPolicy.canInspect(OWNER, STRANGER, UserRole.ADMIN, false)),
                    () -> assertTrue(SequenceCuratorPolicy.canEditInPlace(OWNER, STRANGER, UserRole.ADMIN)),
                    () -> assertFalse(SequenceCuratorPolicy.mustForkToEdit(OWNER, STRANGER, UserRole.ADMIN, true)));
        }
    }

    @Test
    @DisplayName("inspecting and forking never disagree: a fork is only ever offered to someone who may look")
    void forkImpliesInspect() {
        for (UUID owner : new UUID[]{OWNER, null}) {
            for (UUID viewer : new UUID[]{OWNER, CURATOR, STRANGER, null}) {
                for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.ADMIN, UserRole.STUDENT, null}) {
                    for (boolean curator : new boolean[]{true, false}) {
                        if (SequenceCuratorPolicy.mustForkToEdit(owner, viewer, role, curator)) {
                            assertTrue(SequenceCuratorPolicy.canInspect(owner, viewer, role, curator),
                                    "fork offered without inspection rights: owner=" + owner
                                            + " viewer=" + viewer + " role=" + role + " curator=" + curator);
                        }
                    }
                }
            }
        }
    }
}
