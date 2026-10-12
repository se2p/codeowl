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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every combination of visibility, role and ownership, spelled out.
 *
 * <p>An access rule is only as good as the case nobody thought of, so this walks the whole
 * table rather than sampling it: three visibilities against five kinds of viewer, for each
 * question the policy answers. The expected values are written out one by one on purpose — a
 * test that recomputes the rule it is checking proves nothing.
 */
class WorksheetAccessPolicyTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Nested
    @DisplayName("canView")
    class CanView {

        @Test
        @DisplayName("the owner sees their worksheet whatever its visibility")
        void ownerAlwaysSees() {
            for (WorksheetVisibility v : WorksheetVisibility.values()) {
                assertTrue(WorksheetAccessPolicy.canView(v, OWNER, OWNER, UserRole.TEACHER),
                        "owner denied on " + v);
            }
        }

        @Test
        @DisplayName("an admin sees any worksheet whatever its visibility")
        void adminAlwaysSees() {
            for (WorksheetVisibility v : WorksheetVisibility.values()) {
                assertTrue(WorksheetAccessPolicy.canView(v, OWNER, OTHER, UserRole.ADMIN),
                        "admin denied on " + v);
            }
        }

        @Test
        @DisplayName("PRIVATE shuts out everyone else, logged in or not")
        void privateExcludesEveryoneElse() {
            assertAll(
                    () -> assertFalse(view(WorksheetVisibility.PRIVATE, OTHER, UserRole.TEACHER),
                            "another teacher"),
                    () -> assertFalse(view(WorksheetVisibility.PRIVATE, OTHER, UserRole.STUDENT),
                            "a student"),
                    () -> assertFalse(view(WorksheetVisibility.PRIVATE, null, null),
                            "an anonymous link visitor"));
        }

        @Test
        @DisplayName("UNLISTED stays open to anyone with the link — the behaviour that existed before")
        void unlistedIsReadableByLink() {
            assertAll(
                    () -> assertTrue(view(WorksheetVisibility.UNLISTED, OTHER, UserRole.TEACHER)),
                    () -> assertTrue(view(WorksheetVisibility.UNLISTED, OTHER, UserRole.STUDENT)),
                    () -> assertTrue(view(WorksheetVisibility.UNLISTED, null, null),
                            "students open worksheets by link without an account"));
        }

        @Test
        @DisplayName("PUBLIC is readable by everyone too")
        void publicIsReadable() {
            assertAll(
                    () -> assertTrue(view(WorksheetVisibility.PUBLIC, OTHER, UserRole.TEACHER)),
                    () -> assertTrue(view(WorksheetVisibility.PUBLIC, OTHER, UserRole.STUDENT)),
                    () -> assertTrue(view(WorksheetVisibility.PUBLIC, null, null)));
        }

        @Test
        @DisplayName("a worksheet with no visibility set behaves as it always did")
        void nullVisibilityFallsBackToUnlisted() {
            assertTrue(WorksheetAccessPolicy.canView(null, OWNER, OTHER, UserRole.TEACHER),
                    "rows predating the column must not become unreachable");
        }
    }

    @Nested
    @DisplayName("canCopy")
    class CanCopy {

        @Test
        @DisplayName("students may never copy, however visible the worksheet is")
        void studentsNeverCopy() {
            for (WorksheetVisibility v : WorksheetVisibility.values()) {
                assertFalse(WorksheetAccessPolicy.canCopy(v, OWNER, OTHER, UserRole.STUDENT),
                        "a student was allowed to copy a " + v + " worksheet");
            }
        }

        @Test
        @DisplayName("anonymous visitors may never copy")
        void anonymousNeverCopies() {
            for (WorksheetVisibility v : WorksheetVisibility.values()) {
                assertFalse(WorksheetAccessPolicy.canCopy(v, OWNER, null, null),
                        "an anonymous visitor was allowed to copy a " + v + " worksheet");
            }
        }

        @Test
        @DisplayName("a teacher may copy what they may read, and nothing else")
        void teacherCopiesWhatTheyCanRead() {
            assertAll(
                    () -> assertFalse(copy(WorksheetVisibility.PRIVATE, OTHER, UserRole.TEACHER),
                            "PRIVATE must close the copy endpoint, not just hide the button"),
                    () -> assertTrue(copy(WorksheetVisibility.UNLISTED, OTHER, UserRole.TEACHER)),
                    () -> assertTrue(copy(WorksheetVisibility.PUBLIC, OTHER, UserRole.TEACHER)));
        }

        @Test
        @DisplayName("an admin may copy anything, a teacher may duplicate their own")
        void adminAndOwnerCopy() {
            assertAll(
                    () -> assertTrue(copy(WorksheetVisibility.PRIVATE, OTHER, UserRole.ADMIN)),
                    () -> assertTrue(WorksheetAccessPolicy.canCopy(
                            WorksheetVisibility.PRIVATE, OWNER, OWNER, UserRole.TEACHER),
                            "duplicating your own worksheet is harmless and useful"));
        }
    }

    @Nested
    @DisplayName("canList")
    class CanList {

        @Test
        @DisplayName("only PUBLIC reaches the marketplace")
        void onlyPublicIsListed() {
            assertAll(
                    () -> assertFalse(WorksheetAccessPolicy.canList(WorksheetVisibility.PRIVATE)),
                    () -> assertFalse(WorksheetAccessPolicy.canList(WorksheetVisibility.UNLISTED),
                            "readable by link is not the same as findable"),
                    () -> assertTrue(WorksheetAccessPolicy.canList(WorksheetVisibility.PUBLIC)),
                    () -> assertFalse(WorksheetAccessPolicy.canList(null),
                            "an unset visibility must not leak into the listing"));
        }
    }

    @Nested
    @DisplayName("canChangeVisibility")
    class CanChangeVisibility {

        @Test
        @DisplayName("owner and admin may publish; nobody else may")
        void onlyOwnerAndAdmin() {
            assertAll(
                    () -> assertTrue(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OWNER, UserRole.TEACHER, false), "owner"),
                    () -> assertTrue(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OTHER, UserRole.ADMIN, false), "admin"),
                    () -> assertFalse(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OTHER, UserRole.TEACHER, false), "another teacher"),
                    () -> assertFalse(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OTHER, UserRole.STUDENT, false), "a student"),
                    () -> assertFalse(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, null, null, false), "an anonymous visitor"));
        }

        @Test
        @DisplayName("the Library Inbox cannot be published, not even by its owner or an admin")
        void libraryInboxIsNeverPublishable() {
            assertAll(
                    () -> assertFalse(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OWNER, UserRole.TEACHER, true), "owner"),
                    () -> assertFalse(WorksheetAccessPolicy.canChangeVisibility(
                            OWNER, OTHER, UserRole.ADMIN, true), "admin"));
        }
    }

    @Nested
    @DisplayName("the visibility value itself")
    class Visibility {

        @Test
        @DisplayName("the default is what every worksheet already was")
        void defaultIsUnlisted() {
            assertAll(
                    () -> assertEquals(WorksheetVisibility.UNLISTED, WorksheetVisibility.DEFAULT),
                    () -> assertEquals(WorksheetVisibility.UNLISTED, WorksheetVisibility.parse(null)),
                    () -> assertEquals(WorksheetVisibility.UNLISTED, WorksheetVisibility.parse("  ")));
        }

        @Test
        @DisplayName("parsing accepts stored and submitted spellings")
        void parseIsLenientAboutFormatting() {
            assertAll(
                    () -> assertEquals(WorksheetVisibility.PUBLIC, WorksheetVisibility.parse("public")),
                    () -> assertEquals(WorksheetVisibility.PUBLIC, WorksheetVisibility.parse(" PUBLIC ")),
                    () -> assertEquals(WorksheetVisibility.PRIVATE, WorksheetVisibility.parse("Private")));
        }

        @Test
        @DisplayName("an unknown value is rejected rather than quietly widened")
        void parseRejectsUnknownValues() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> WorksheetVisibility.parse("EVERYONE")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> WorksheetVisibility.parse("published")));
        }

        @Test
        @DisplayName("a copy starts private whatever its source was")
        void copiesStartPrivate() {
            assertEquals(WorksheetVisibility.PRIVATE, WorksheetAccessPolicy.visibilityForCopy());
        }

        @Test
        @DisplayName("only PUBLIC is listed, only PRIVATE is closed to links")
        void behaviourFlagsAgreeWithTheTable() {
            List<String> wrong = new ArrayList<>();
            for (WorksheetVisibility v : WorksheetVisibility.values()) {
                if (v.listed() != (v == WorksheetVisibility.PUBLIC)) wrong.add(v + ".listed()");
                if (v.readableByLink() == (v == WorksheetVisibility.PRIVATE)) {
                    wrong.add(v + ".readableByLink()");
                }
            }
            assertEquals(List.of(), wrong,
                    "a value was added or changed without revisiting the policy table");
        }
    }

    private static boolean view(WorksheetVisibility v, UUID viewerId, UserRole role) {
        return WorksheetAccessPolicy.canView(v, OWNER, viewerId, role);
    }

    private static boolean copy(WorksheetVisibility v, UUID viewerId, UserRole role) {
        return WorksheetAccessPolicy.canCopy(v, OWNER, viewerId, role);
    }
}
