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

import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the concept catalogue for the pickers.
 *
 * <p>Uses {@link ConceptRepository#loadAll()} rather than {@code loadAllOriginal()}:
 * the latter filters to {@code generated = false}, which would hide every concept
 * the LLM or an admin has added since — exactly the ones someone is most likely
 * to want to reuse.
 */
@Service
public class ConceptQueryService {

    private final ConceptRepository conceptRepo;

    public ConceptQueryService(ConceptRepository conceptRepo) {
        this.conceptRepo = conceptRepo;
    }

    /** Every known concept name, sorted case-insensitively. */
    public List<String> getCatalogue() {
        return conceptRepo.loadAll().stream()
                .map(ConceptDTO::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
