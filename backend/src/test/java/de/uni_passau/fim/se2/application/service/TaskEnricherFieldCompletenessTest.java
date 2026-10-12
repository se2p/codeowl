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

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A tripwire for the bug class that made a five-tier sequence come back looking like a three-tier
 * one: {@link TaskEnricher#enrichWithSolutions} used to build its result via a fresh
 * {@code TieredTaskSequenceDTO.builder()} that named only a handful of fields, so {@code
 * tierCount}, {@code targetLanguage}, {@code constraints} and {@code curriculumTopicId} fell back
 * to their {@code @Builder.Default} values instead of surviving enrichment — exactly the failure
 * {@link TieredTaskSequenceDTO#copyFor} and {@link TaskDTO#copyFor} already document for the
 * worksheet-copy path, reappearing on the generation path because nothing here guarded it too.
 *
 * <p>Like {@code CloneCompletenessTest}, this does not enumerate the fields it expects to survive:
 * it reflects over every declared field of {@link TieredTaskSequenceDTO} and demands each one
 * came through enrichment unchanged, except the three that legitimately must not ({@code id}
 * — a fresh row; {@code tasks} — replaced by the enriched tasks; {@code createdAt} — stamped
 * fresh). A field added later is covered automatically; the only way to exempt one is to add it
 * to {@link #RESET_BY_ENRICHMENT} with a reason, which shows up as a reviewable line in a diff.
 */
class TaskEnricherFieldCompletenessTest {

    private static final Set<String> RESET_BY_ENRICHMENT = Set.of("id", "tasks", "createdAt");

    @Test
    @DisplayName("enrichWithSolutions carries every sequence field across except id, tasks and createdAt")
    void enrichmentPreservesSequenceMetadata() throws Exception {
        TaskEnricher enricher = newEnricherWithStubbedCollaborators();

        TieredTaskSequenceDTO seed = TieredTaskSequenceDTO.builder()
                .id(UUID.randomUUID())
                .programmingLanguage("Java")
                .concepts(List.of("recursion"))
                .tasks(List.of(distinguishableTask()))
                .codeSnippet("int x = 1;")
                .taskDescription("A regression-test sequence")
                .createdAt(LocalDateTime.of(2020, 1, 1, 0, 0))
                .versionId(UUID.randomUUID())
                .strategyName("CodeOnlyStrategy")
                .ownerId(UUID.randomUUID())
                // Every one of these four is a @Builder.Default field whose default would
                // silently step in if enrichWithSolutions dropped it — that is exactly the bug.
                .targetLanguage("en")
                .constraints(GenerationConstraints.builder().additionalContext("no lambdas").build())
                .tierCount(5)
                .curriculumTopicId(UUID.randomUUID())
                .build();

        TieredTaskSequenceDTO enriched = enricher.enrichWithSolutions(seed, false, false, false);

        for (Field field : TieredTaskSequenceDTO.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) continue;
            if (RESET_BY_ENRICHMENT.contains(field.getName())) continue;

            field.setAccessible(true);
            assertEquals(field.get(seed), field.get(enriched),
                    "field '" + field.getName() + "' was lost by TaskEnricher.enrichWithSolutions()."
                            + " Either carry it over in the result, or add it to RESET_BY_ENRICHMENT"
                            + " in " + TaskEnricherFieldCompletenessTest.class.getSimpleName()
                            + " with a reason.");
        }
    }

    @Test
    @DisplayName("enrichSingleTask carries the source task's own metadata, including a disabled distractors flag")
    void enrichmentPreservesTaskMetadata() throws Exception {
        TaskEnricher enricher = newEnricherWithStubbedCollaborators();
        TaskDTO seedTask = distinguishableTask();

        TieredTaskSequenceDTO enriched = enricher.enrichWithSolutions(
                TieredTaskSequenceDTO.builder()
                        .programmingLanguage("Java")
                        .tasks(List.of(seedTask))
                        .build(),
                false, false, false);

        TaskDTO enrichedTask = enriched.getTasks().get(0);

        // Unchanged pass-through fields.
        assertEquals(seedTask.getStatement(), enrichedTask.getStatement());
        assertEquals(seedTask.getTierLevel(), enrichedTask.getTierLevel());
        assertEquals(seedTask.getContract(), enrichedTask.getContract());
        assertEquals(seedTask.getDistractorPlan(), enrichedTask.getDistractorPlan());

        // The flag TaskDTO.copyFor() already had to defend against on the worksheet-copy path:
        // its @Builder.Default is true, so only seeing false back out proves it actually
        // travelled through enrichment rather than being silently re-defaulted.
        assertFalse(enrichedTask.isDistractorsEnabled(),
                "distractorsEnabled must survive enrichment, not fall back to its default of true");
    }

    // ─────────────────────────────────────────────────────────────────────

    private static TaskEnricher newEnricherWithStubbedCollaborators() throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(client.solveProblem(any())).thenReturn(TextNode.valueOf("int x = 1;"));

        CodeSanitizerService sanitizer = mock(CodeSanitizerService.class);
        when(sanitizer.removeComments(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        InterchangeableAnnotator annotator = mock(InterchangeableAnnotator.class);
        when(annotator.annotate(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        CodeAnalyzer analyzer = mock(CodeAnalyzer.class);
        when(analyzer.computeComplexity(any(), any()))
                .thenReturn(new CodeAnalyzer.ComplexityResult(1, 1, 1, 1, 1));

        // Unused here: distractorsEnabled/contextModeEnabled/splitEnabled are all false in every
        // test, so enrichSingleTask never reaches the context annotator.
        ContextAnnotator contextAnnotator = mock(ContextAnnotator.class);

        return new TaskEnricher(client, annotator, contextAnnotator, sanitizer, analyzer);
    }

    private static TaskDTO distinguishableTask() {
        return TaskDTO.builder()
                .id(UUID.randomUUID())
                .statement("Print the sum of two numbers")
                .contract(SolveContract.builder()
                        .language("Java")
                        .taskStatement("Print the sum of two numbers")
                        .functionSignature("void solve()")
                        .purposeBehavior("Prints a sum")
                        .prePostConditions("None")
                        .example(new SolveContract.ExamplePair("1, 2", "3"))
                        .build())
                .tierLevel(1)
                .distractorPlan("{\"type\":\"OFF_BY_ONE\"}")
                // The @Builder.Default is true; false is what proves it survived rather than
                // being silently re-defaulted.
                .distractorsEnabled(false)
                .build();
    }
}
