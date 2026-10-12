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

import de.uni_passau.fim.se2.domain.entities.VersionDTO;

import java.util.List;

/**
 * Repository interface for managing versions.
 * Provides methods to save, retrieve, and delete instances
 * of VersionDTO.
 */
public interface VersionRepository {

    /**
     * Persists the given version details into the repository.
     * This method saves the provided VersionDTO object, ensuring
     * that the version information, such as ID, name, creation timestamp,
     * and description, is stored for future retrieval or modification.
     *
     * @param version the VersionDTO object representing the version to be saved.
     *                It includes details such as a unique identifier, version name,
     *                creation date, and description.
     */
    void save(VersionDTO version);

    /**
     * Retrieves all version data transfer objects (VersionDTO) stored in the repository.
     * Returns a list of VersionDTO instances representing the available versions,
     * including details such as their unique identifiers, version names, creation dates,
     * and descriptions.
     *
     * @return a list of VersionDTO objects representing all versions in the repository,
     *         or an empty list if no versions are found
     */
    List<VersionDTO> findAll();

    /**
     * Retrieves a specific version from the repository based on the
     * details provided in the given VersionDTO instance.
     *
     * @param version the VersionDTO object representing the version to be retrieved.
     *                It should include the necessary details, such as the unique identifier (id),
     *                to locate the specific version in the repository.
     */
    void findById(VersionDTO version);

    /**
     * Deletes the specified version from the repository.
     * This method removes the VersionDTO instance and its associated data.
     *
     * @param version the VersionDTO object representing the version to be deleted,
     *                including its unique identifier and other related details
     */
    void delete(VersionDTO version);
}