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

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A FIM-Account as the FIM directory describes it after a successful login.
 * Everything but {@code uid} may be null if the directory entry lacks it.
 */
public record FimAccount(String uid, String email, String firstName, String lastName) {

    /**
     * What a teacher may type as a FIM username. Stricter than LDAP itself, which
     * is the point: an email address or a name pasted into the wrong column should
     * be refused, not turned into an account nobody can ever log in to.
     */
    private static final Pattern VALID_UID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    /**
     * The one spelling of a uid that is stored and looked up. LDAP matches uids
     * case-insensitively, so a teacher typing "Mustermann" and the student logging
     * in as "mustermann" must arrive at the same account.
     */
    public static String normalizeUid(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether an already normalized uid has the shape of a FIM username. */
    public static boolean isValidUid(String normalizedUid) {
        return VALID_UID.matcher(normalizedUid).matches();
    }
}
