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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.application.service.ConceptQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The concept catalogue, for every screen that lets someone pick concepts:
 * curriculum learning areas, the generator on the home page, and the manual
 * task creator. It is not curriculum-specific, hence its own controller rather
 * than a corner of {@link CurriculumController}.
 */
@CrossOrigin(origins = "http://localhost:5175")
@RestController
@RequestMapping("/api/concepts")
@RequiredArgsConstructor
public class ConceptsController {

    private final ConceptQueryService conceptQueryService;

    /** Every known concept name, sorted, for autocomplete. */
    @GetMapping
    public ResponseEntity<List<String>> getCatalogue() {
        return ResponseEntity.ok(conceptQueryService.getCatalogue());
    }
}
