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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumCodeExampleRepository;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumTopicRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * AP32 admin use case: create / update / delete curated curriculum code examples.
 * Validation lives here (not in the controller or DAO). Not-found conditions throw
 * {@link IllegalArgumentException} (mapped to 404 by the controller); invalid input
 * throws {@link DomainValidationException} (mapped to 400 by the global handler).
 */
@Service
public class CurriculumExampleAdminService {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private final CurriculumCodeExampleRepository exampleRepo;
    private final CurriculumTopicRepository topicRepo;

    public CurriculumExampleAdminService(CurriculumCodeExampleRepository exampleRepo,
                                         CurriculumTopicRepository topicRepo) {
        this.exampleRepo = exampleRepo;
        this.topicRepo = topicRepo;
    }

    /** Creates a new example under an existing topic. */
    public CurriculumCodeExampleDTO create(CurriculumCodeExampleDTO dto) {
        if (dto.getTopicId() == null) {
            throw new DomainValidationException("topicId is required.");
        }
        if (topicRepo.findById(dto.getTopicId()).isEmpty()) {
            throw new IllegalArgumentException("Curriculum topic not found: " + dto.getTopicId());
        }
        normalizeAndValidate(dto);
        UUID id = exampleRepo.insert(dto);
        return exampleRepo.findById(id);
    }

    /** Updates an existing example. The topic assignment is immutable. */
    public CurriculumCodeExampleDTO update(UUID id, CurriculumCodeExampleDTO dto) {
        CurriculumCodeExampleDTO existing = exampleRepo.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Curriculum example not found: " + id);
        }
        dto.setId(id);
        dto.setTopicId(existing.getTopicId()); // topic is not moved via edit
        normalizeAndValidate(dto);
        exampleRepo.update(dto);
        return exampleRepo.findById(id);
    }

    /** Deletes an example by id. */
    public void delete(UUID id) {
        CurriculumCodeExampleDTO existing = exampleRepo.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Curriculum example not found: " + id);
        }
        exampleRepo.deleteById(id);
    }

    /** Trims fields, defaults/validates difficulty, and enforces required fields. Mutates {@code dto}. */
    private void normalizeAndValidate(CurriculumCodeExampleDTO dto) {
        requireNonBlank(dto.getLanguage(), "language");
        requireNonBlank(dto.getTitle(), "title");
        requireNonBlank(dto.getCode(), "code");

        dto.setLanguage(dto.getLanguage().trim());
        dto.setTitle(dto.getTitle().trim());

        String difficulty = dto.getDifficulty() == null ? "" : dto.getDifficulty().trim().toLowerCase();
        if (difficulty.isEmpty()) {
            difficulty = "medium";
        }
        if (!DIFFICULTIES.contains(difficulty)) {
            throw new DomainValidationException("difficulty must be one of: easy, medium, hard.");
        }
        dto.setDifficulty(difficulty);

        // Optional fields: blank -> null so the column stores NULL, not an empty string.
        if (dto.getProblemStatement() != null && dto.getProblemStatement().isBlank()) {
            dto.setProblemStatement(null);
        }
        if (dto.getNotes() != null && dto.getNotes().isBlank()) {
            dto.setNotes(null);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " is required.");
        }
    }
}
