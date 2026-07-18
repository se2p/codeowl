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
package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.UserDTO;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    UserDTO save(UserDTO user);
    Optional<UserDTO> findById(UUID userId);
    Optional<UserDTO> findByEmail(String email);
    List<UserDTO> findAll();
    void update(UserDTO user);
    void deleteById(UUID userId);
    boolean existsByEmail(String email);

    void setMustChangePassword(UUID userId, boolean mustChangePassword);

    // Paginated search for admin
    List<UserDTO> findAllPaginated(String search, String sortColumn, String sortDir, int offset, int limit);
    long countAll(String search);
}
