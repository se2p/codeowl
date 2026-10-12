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

import de.uni_passau.fim.se2.application.service.CurriculumExampleAdminService;
import de.uni_passau.fim.se2.application.service.CurriculumGenerationService;
import de.uni_passau.fim.se2.application.service.CurriculumTopicAdminService;
import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.TaskPersistenceService;
import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:5175")
@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumGenerationService curriculumService;
    private final TaskPersistenceService taskPersistenceService;
    private final OwnershipService ownershipService;
    private final CurriculumExampleAdminService exampleAdminService;
    private final CurriculumTopicAdminService topicAdminService;

    /**
     * Get all top-level curriculum topics (Jahrgangsstufen).
     * Public endpoint — anyone can browse the curriculum tree.
     */
    @GetMapping("/topics")
    public ResponseEntity<List<CurriculumTopicDTO>> getTopLevelTopics() {
        return ResponseEntity.ok(curriculumService.getTopLevelTopics());
    }

    /**
     * Get sub-topics (Lernbereiche/Themen) for a given parent topic.
     * Public endpoint.
     */
    @GetMapping("/topics/{parentId}/children")
    public ResponseEntity<List<CurriculumTopicDTO>> getSubTopics(@PathVariable UUID parentId) {
        return ResponseEntity.ok(curriculumService.getSubTopics(parentId));
    }

    /**
     * Get curated code examples for a given curriculum topic.
     * Optional query param ?language=java to filter by language.
     * Public endpoint.
     */
    @GetMapping("/topics/{topicId}/examples")
    public ResponseEntity<List<CurriculumCodeExampleDTO>> getExamples(
            @PathVariable UUID topicId,
            @RequestParam(required = false) String language) {
        return ResponseEntity.ok(curriculumService.getExamples(topicId, language));
    }

    /**
     * Generate a tiered task sequence from a curriculum topic (Beta).
     * Uses curated seed code examples and the dedicated curriculum prompt.
     * Allowed: Teacher, Admin
     * Denied: Students, Anonymous
     */
    @PostMapping("/topics/{topicId}/generate")
    public ResponseEntity<?> generateFromCurriculum(
            @PathVariable UUID topicId,
            @RequestBody CurriculumGenerateRequest request) throws IOException, InterruptedException {

        ownershipService.denyStudents("generate curriculum tasks");

        UUID currentUserId = ownershipService.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String language = request.getLanguage();
        if (language == null || language.isBlank()) language = "java";

        String targetLanguage = request.getTargetLanguage();
        if (targetLanguage == null || targetLanguage.isBlank()) targetLanguage = "de";

        int tierCount = request.getTierCount();
        if (tierCount != 3 && tierCount != 5) tierCount = 3;

        GenerationConstraints constraints = GenerationConstraints.builder()
                .blacklist(request.getBlacklist())
                .whitelist(request.getWhitelist())
                .difficultySlope(request.getDifficultySlope())
                .additionalContext(request.getAdditionalContext())
                .build();

        try {
            TieredTaskSequenceDTO seq = curriculumService.generate(
                    topicId, language, targetLanguage, tierCount,
                    request.isDistractorsEnabled(),
                    request.isContextModeEnabled(),
                    request.isSplitEnabled(),
                    constraints.hasAny() ? constraints : null,
                    currentUserId);

            // Persist the generated sequence
            taskPersistenceService.persistSequence(seq);

            var location = java.net.URI.create("/api/sequence/" + seq.getId());
            return ResponseEntity.created(location).body(seq);
        } catch (IllegalArgumentException e) {
            // Topic not found
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            // No seed code examples available yet for this topic
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "no_examples", "message", e.getMessage()));
        }
    }

    /**
     * Admin: add a top-level entry to the curriculum tree — a Jahrgangsstufe
     * when the body carries a grade level, otherwise a named module such as
     * "Programmieren 1". See CurriculumTopicAdminService.
     * Allowed: Admin only.
     */
    @PostMapping("/topics")
    public ResponseEntity<CurriculumTopicDTO> createTopLevelTopic(@RequestBody CurriculumTopicDTO body) {
        requireAdmin();
        CurriculumTopicDTO created = topicAdminService.createTopLevelTopic(body);
        var location = java.net.URI.create("/api/curriculum/topics/" + created.getId() + "/children");
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Admin: add a Lernbereich under an existing Jahrgangsstufe or module.
     * Allowed: Admin only.
     */
    @PostMapping("/topics/{parentId}/children")
    public ResponseEntity<?> createSubTopic(@PathVariable UUID parentId,
                                            @RequestBody CurriculumTopicDTO body) {
        requireAdmin();
        try {
            CurriculumTopicDTO created = topicAdminService.createSubTopic(parentId, body);
            var location = java.net.URI.create("/api/curriculum/topics/" + created.getId() + "/examples");
            return ResponseEntity.created(location).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * The concept names mapped to a topic. Feeds the admin form; the mapping is
     * a relation, so it does not ride along on the topic itself.
     */
    @GetMapping("/topics/{topicId}/concepts")
    public ResponseEntity<?> getTopicConcepts(@PathVariable UUID topicId) {
        try {
            return ResponseEntity.ok(topicAdminService.getConceptNames(topicId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * Admin: edit a curriculum entry, at either level — the endpoint reads the
     * level off the stored topic. A top-level entry may also change shape
     * between Jahrgangsstufe and module; a sub-topic keeps the grade level,
     * track and education level it inherits. See CurriculumTopicAdminService.
     * Allowed: Admin only.
     */
    @PutMapping("/topics/{topicId}")
    public ResponseEntity<?> updateTopic(@PathVariable UUID topicId,
                                         @RequestBody CurriculumTopicDTO body) {
        requireAdmin();
        try {
            return ResponseEntity.ok(topicAdminService.update(topicId, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * AP32 admin: create a curated code example under a topic.
     * Allowed: Admin only.
     */
    @PostMapping("/topics/{topicId}/examples")
    public ResponseEntity<?> createExample(@PathVariable UUID topicId,
                                           @RequestBody CurriculumCodeExampleDTO body) {
        requireAdmin();
        body.setTopicId(topicId);
        try {
            CurriculumCodeExampleDTO created = exampleAdminService.create(body);
            var location = java.net.URI.create("/api/curriculum/examples/" + created.getId());
            return ResponseEntity.created(location).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * AP32 admin: update a curated code example. The topic assignment is immutable.
     * Allowed: Admin only.
     */
    @PutMapping("/examples/{exampleId}")
    public ResponseEntity<?> updateExample(@PathVariable UUID exampleId,
                                           @RequestBody CurriculumCodeExampleDTO body) {
        requireAdmin();
        try {
            return ResponseEntity.ok(exampleAdminService.update(exampleId, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * AP32 admin: delete a curated code example.
     * Allowed: Admin only.
     */
    @DeleteMapping("/examples/{exampleId}")
    public ResponseEntity<?> deleteExample(@PathVariable UUID exampleId) {
        requireAdmin();
        try {
            exampleAdminService.delete(exampleId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /** The curriculum tree and its examples are shared assets — only admins may manage them. */
    private void requireAdmin() {
        if (!ownershipService.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only admins may manage the curriculum.");
        }
    }

    @Setter
    @Getter
    public static class CurriculumGenerateRequest {
        private String language = "java";
        private String targetLanguage = "de";
        private int tierCount = 3;
        private boolean distractorsEnabled;
        /** AP22: Opt-in context mode (Beta) for longer code with scaffolding. Default false. */
        private boolean contextModeEnabled;
        /** AP31: Opt-in inline-split generation for complex lines. Default false. */
        private boolean splitEnabled;

        // AP20: Generation Constraints
        private List<String> blacklist;
        private List<String> whitelist;
        private String difficultySlope;
        private String additionalContext;
    }
}
