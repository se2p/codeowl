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

import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A concept typed by hand — in {@code ConceptPicker}, or as a curriculum topic's mapping — used
 * to be invisible to the LLM classifier forever: {@code getCatalogForPrompt}/{@code normalize}
 * read only the static {@code concept-catalog.json}, never the {@code concepts} table the typed
 * concept was actually saved to. This pins down the fix: anything in that table beyond the
 * file's own slugs must be offered to the LLM and survive validating its response, without
 * duplicating a slug the file already has.
 */
class ConceptCatalogTest {

    private static final ConceptDTO IF_ELSE_IN_DB = concept("if-else", null);
    private static final ConceptDTO QUANTUM_COMPUTING = concept("quantum-computing", "A made-up test concept");

    private ConceptCatalog catalog;

    @BeforeEach
    void setUp() {
        ConceptRepository repo = mock(ConceptRepository.class);
        // if-else is already a file slug (seeded that way); quantum-computing is not — exactly
        // the shape a real database has: the curated catalog plus whatever got typed by hand.
        when(repo.loadAll()).thenReturn(List.of(IF_ELSE_IN_DB, QUANTUM_COMPUTING));

        catalog = new ConceptCatalog(repo);
        catalog.init(); // @PostConstruct isn't run outside a Spring context
    }

    @Test
    @DisplayName("a concept added beyond the file is offered to the LLM for every language")
    void addedConceptAppearsInPrompt() {
        String prompt = catalog.getCatalogForPrompt("java");
        assertTrue(prompt.contains("quantum-computing"),
                "a concept only in the database, not the file, must still reach the prompt");
    }

    @Test
    @DisplayName("a file slug that also exists in the database is not duplicated in the prompt")
    void fileSlugIsNotDuplicated() {
        String prompt = catalog.getCatalogForPrompt("java");
        long occurrences = prompt.lines().filter(line -> line.startsWith("if-else ")).count();
        assertEquals(1, occurrences, "if-else must come from the file once, not the database too");
    }

    @Test
    @DisplayName("normalize resolves a concept added beyond the file, case-insensitively")
    void normalizeResolvesAddedConcept() {
        assertEquals("quantum-computing", catalog.normalize("quantum-computing").orElseThrow());
        assertEquals("quantum-computing", catalog.normalize("Quantum-Computing").orElseThrow());
    }

    @Test
    @DisplayName("isValidSlug is true for a concept added beyond the file")
    void isValidSlugRecognizesAddedConcept() {
        assertTrue(catalog.isValidSlug("quantum-computing"));
    }

    @Test
    @DisplayName("a name in neither the file nor the database is still rejected")
    void unknownConceptIsRejected() {
        assertTrue(catalog.normalize("totally-made-up-concept-xyz").isEmpty());
        assertFalse(catalog.isValidSlug("totally-made-up-concept-xyz"));
    }

    private static ConceptDTO concept(String name, String description) {
        ConceptDTO dto = new ConceptDTO();
        dto.setName(name);
        dto.setDescription(description);
        dto.setGenerated(true);
        return dto;
    }
}
