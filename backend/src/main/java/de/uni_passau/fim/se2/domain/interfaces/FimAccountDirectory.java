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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.exceptions.DirectoryUnavailableException;
import de.uni_passau.fim.se2.domain.model.FimAccount;

import java.util.Optional;

/**
 * Checks FIM-Account credentials against the FIM directory.
 */
public interface FimAccountDirectory {

    /**
     * @return the account if username and password are valid, empty if they are not
     * @throws DirectoryUnavailableException if the directory could not be asked at all
     */
    Optional<FimAccount> authenticate(String username, String password);
}
