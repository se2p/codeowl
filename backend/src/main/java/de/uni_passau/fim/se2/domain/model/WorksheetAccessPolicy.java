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
 * Who may do what with a worksheet.
 *
 * <p>These rules were previously spread across the controller, the service and the React page,
 * each expressing part of the answer and none of them the whole one. Introducing
 * {@link WorksheetVisibility} would have added a fourth place to keep in step, so they are
 * gathered here instead: pure functions over the few facts that decide the outcome, with no
 * Spring, no database and no security context.
 *
 * <p>That makes the rules enumerable. {@code WorksheetAccessPolicyTest} walks every
 * combination of visibility, role and ownership, which is the only way to be sure a table this
 * shape has no hole in it.
 *
 * <p>Callers pass {@code null} for {@code viewerId} and {@code viewerRole} to mean "not logged
 * in". Anonymous access is a real case here: students open worksheets by link without an
 * account.
 */
public final class WorksheetAccessPolicy {

    private WorksheetAccessPolicy() {
    }

    /**
     * Whether someone may open the worksheet.
     *
     * <p>Owners and admins always may. Beyond that a link is enough, which is how sharing has
     * always worked — {@link WorksheetVisibility#PRIVATE} is the new ability to say no.
     */
    public static boolean canView(WorksheetVisibility visibility, UUID ownerId,
                                  UUID viewerId, UserRole viewerRole) {
        if (isOwnerOrAdmin(ownerId, viewerId, viewerRole)) {
            return true;
        }
        return visibility(visibility).readableByLink();
    }

    /**
     * Whether someone may copy the worksheet into their own namespace.
     *
     * <p>Teachers and admins only: a copy creates worksheets and sequences owned by the copier,
     * which is not something a student account should be able to do. Beyond that, copying
     * requires being able to view — a worksheet you may not open is not one you may duplicate.
     *
     * <p>Copying your own worksheet is allowed and simply duplicates it. The button is hidden
     * for that case, but the rule does not forbid it; there is nothing to protect against, and
     * an endpoint that rejected it would be surprising.
     */
    public static boolean canCopy(WorksheetVisibility visibility, UUID ownerId,
                                  UUID viewerId, UserRole viewerRole) {
        if (viewerRole != UserRole.TEACHER && viewerRole != UserRole.ADMIN) {
            return false;
        }
        return canView(visibility, ownerId, viewerId, viewerRole);
    }

    /**
     * Whether the marketplace lists the worksheet.
     *
     * <p>Only visibility decides this, deliberately. The listing is the same for everyone who
     * can see the marketplace at all, so it can be a plain {@code WHERE} clause on the search
     * query rather than a per-row check the database cannot express.
     */
    public static boolean canList(WorksheetVisibility visibility) {
        return visibility(visibility).listed();
    }

    /**
     * Whether someone may change the worksheet's visibility.
     *
     * <p>Owners and admins, except for the Library Inbox. That worksheet is created by the
     * system as a landing spot for one-click copies from the sequence library, so it is a
     * scratch pile rather than teaching material — publishing it would put a teacher's
     * unsorted clippings in front of their colleagues.
     *
     * @param isLibraryInbox whether this is the auto-created inbox; the caller decides, because
     *                       recognising it means knowing about a sentinel in the description
     *                       and that is not a domain rule
     */
    public static boolean canChangeVisibility(UUID ownerId, UUID actorId, UserRole actorRole,
                                              boolean isLibraryInbox) {
        if (isLibraryInbox) {
            return false;
        }
        return isOwnerOrAdmin(ownerId, actorId, actorRole);
    }

    /**
     * What a copy starts out as.
     *
     * <p>Always {@link WorksheetVisibility#PRIVATE}, whatever the source was. Copying is how you
     * take someone else's material to adapt it, and a half-adapted worksheet appearing in the
     * marketplace under your name is not what anyone meant by pressing the button. Publishing
     * it is then a separate, deliberate act.
     */
    public static WorksheetVisibility visibilityForCopy() {
        return WorksheetVisibility.PRIVATE;
    }

    private static boolean isOwnerOrAdmin(UUID ownerId, UUID actorId, UserRole actorRole) {
        if (actorRole == UserRole.ADMIN) {
            return true;
        }
        return actorId != null && actorId.equals(ownerId);
    }

    /** Rows written before the column existed carry no visibility; they behave as they always did. */
    private static WorksheetVisibility visibility(WorksheetVisibility visibility) {
        return visibility == null ? WorksheetVisibility.DEFAULT : visibility;
    }
}
