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

import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing curriculum topics.
 * Provides methods for persisting, retrieving, and deleting instances
 * of CurriculumTopicDTO, which represent individual curriculum topics.
 */
public interface CurriculumTopicRepository {

    /**
     * Persists the specified curriculum topic data transfer object (CurriculumTopicDTO)
     * into the repository. This method ensures that the details of the provided curriculum topic,
     * such as its unique identifier, name, and description, are saved for future retrieval or updates.
     *
     * @param topic the CurriculumTopicDTO object representing the curriculum topic
     *              to be saved. It includes details like the topic's unique identifier, name,
     *              and description.
     */
    void save(CurriculumTopicDTO topic);

    /**
     * Retrieves all curriculum topics available in the repository.
     * This method provides a list of CurriculumTopicDTO objects,
     * which represent individual curriculum topics, including their
     * unique identifiers, names, and descriptions.
     *
     * @return a list of CurriculumTopicDTO objects representing all
     *         the curriculum topics in the repository, or an empty
     *         list if no topics are found
     */
    List<CurriculumTopicDTO> findAll();

    /**
     * Deletes the specified curriculum topic from the repository.
     * This method removes the given CurriculumTopicDTO instance along with
     * its associated data.
     *
     * @param topic the CurriculumTopicDTO object representing the curriculum topic to be deleted,
     *              including its unique identifier and other related details
     */
    void delete(CurriculumTopicDTO topic);

    Optional<CurriculumTopicDTO> findById(UUID id);

    List<CurriculumTopicDTO> findTopLevel();

    List<CurriculumTopicDTO> findByParentId(UUID parentId);

    /**
     * How many sequences match each topic's mapped concepts (any of them), keyed by topic id.
     * Mirrors the "concepts" match the Curriculum page's "View sequences" button filters the
     * Library by, so the count badge and the button agree on what counts as a match. A topic
     * with zero matches is simply absent from the returned map.
     *
     * @param topicIds the topics to count for; an empty collection short-circuits to an empty map
     */
    Map<UUID, Integer> countSequencesByTopicIds(Collection<UUID> topicIds);
}