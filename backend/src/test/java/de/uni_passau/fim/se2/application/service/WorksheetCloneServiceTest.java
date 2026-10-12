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

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a teacher gets when they copy someone else's worksheet.
 *
 * <p>The answer used to be "most of it". The copy rebuilt sequences and tasks by naming fields,
 * so the distractors, the AP22 context blocks, the AP31 split plans, the tier count and the
 * generation constraints were all quietly left behind — and the copy still looked plausible,
 * because Lombok's {@code @Builder.Default} filled the gaps with three tiers, German and
 * distractors switched on.
 *
 * <p>{@code CloneCompletenessTest} guards the per-entity copies field by field. This class
 * covers what only the service can get wrong: that the pieces are wired to the right parents,
 * that tags and summaries come along, and that nothing reaches back into the source.
 */
class WorksheetCloneServiceTest {

    private WorksheetRepository worksheetRepo;
    private TieredTaskSequenceRepository sequenceRepo;
    private TaskRepository taskRepo;
    private SequenceTagRepository tagRepo;
    private WorksheetCloneService service;

    private static final UUID SOURCE_WORKSHEET = UUID.randomUUID();
    private static final UUID SOURCE_SEQUENCE = UUID.randomUUID();
    private static final UUID ORIGINAL_OWNER = UUID.randomUUID();
    private static final UUID COPIER = UUID.randomUUID();

    /** Assigned by the stubbed {@code sequenceRepo.save}, standing in for the database. */
    private UUID assignedSequenceId;

    @BeforeEach
    void setUp() {
        worksheetRepo = mock(WorksheetRepository.class);
        sequenceRepo = mock(TieredTaskSequenceRepository.class);
        taskRepo = mock(TaskRepository.class);
        tagRepo = mock(SequenceTagRepository.class);
        service = new WorksheetCloneService(worksheetRepo, sequenceRepo, taskRepo, tagRepo);

        // The real repositories fill the DTO they are handed rather than returning one, so the
        // stubs have to do the same or the service reads back nulls.
        stubWorksheetLoad(List.of(SOURCE_SEQUENCE));
        stubSequenceLoad(richSourceSequence());
        stubSequenceSave();
        when(taskRepo.loadTasksBySequenceId(any())).thenReturn(List.of(richSourceTask()));
        when(sequenceRepo.findConceptIdsBySequenceId(any())).thenReturn(List.of());
        when(tagRepo.findBySequenceId(any())).thenReturn(List.of());
        when(sequenceRepo.findSemanticSummary(any())).thenReturn(null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // The reported bug
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a five-tier sequence stays five-tier — the regression this MR exists for")
    void copyPreservesTierCount() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        assertEquals(5, savedSequence().getTierCount(),
                "the old copy left tierCount unset, so @Builder.Default made every copied "
                        + "sequence a three-tier one");
    }

    @Test
    @DisplayName("everything a Parsons task is made of survives the copy")
    void copyPreservesTaskPayload() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        TaskDTO copy = savedTask();
        assertAll(
                () -> assertEquals("wrong line #distractor", copy.getDistractors()),
                () -> assertEquals("{\"type\":\"LOGIC_FLIP\"}", copy.getDistractorPlan()),
                () -> assertFalse(copy.isDistractorsEnabled(),
                        "a task with distractors switched off must not come back switched on"),
                () -> assertEquals("import java.util.*;", copy.getContextBefore()),
                () -> assertEquals("}", copy.getContextAfter()),
                () -> assertEquals("{\"splits\":[]}", copy.getSplitPlan()),
                () -> assertEquals("public int f() {", copy.getSolution()),
                () -> assertEquals(7, copy.getCyclomaticComplexity()));
    }

    @Test
    @DisplayName("the sequence's generation settings survive the copy")
    void copyPreservesSequenceSettings() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        TieredTaskSequenceDTO copy = savedSequence();
        assertAll(
                () -> assertEquals("en", copy.getTargetLanguage(),
                        "an English sequence used to come back in German"),
                () -> assertEquals("java", copy.getProgrammingLanguage()),
                () -> assertEquals("Reverse an array", copy.getTaskDescription()),
                () -> assertEquals(List.of("recursion"), copy.getConcepts()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identity and ownership
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the copy belongs to whoever made it, not to the original author")
    void copyTransfersOwnership() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        assertAll(
                () -> assertEquals(COPIER, savedWorksheet().getOwnerId()),
                () -> assertEquals(COPIER, savedSequence().getOwnerId()));
    }

    @Test
    @DisplayName("a copy starts private, whatever the source was")
    void copyStartsPrivate() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        assertEquals(WorksheetVisibility.PRIVATE, savedWorksheet().getVisibility(),
                "a half-adapted copy must not inherit its source's place in the marketplace");
    }

    @Test
    @DisplayName("both the worksheet and its sequences record where they came from")
    void copyRecordsProvenance() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        assertAll(
                () -> assertEquals(SOURCE_WORKSHEET, savedWorksheet().getCopiedFrom()),
                () -> assertNotNull(savedWorksheet().getCopiedAt()),
                () -> assertEquals(SOURCE_SEQUENCE, savedSequence().getCopiedFrom(),
                        "the library search needs this to tell a clone from an original"));
    }

    @Test
    @DisplayName("the copied task hangs off the copied sequence, not the original")
    void copiedTaskPointsAtTheCopiedSequence() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        assertAll(
                () -> assertEquals(assignedSequenceId, savedTask().getSequenceId()),
                () -> assertNotEquals(SOURCE_SEQUENCE, savedTask().getSequenceId()));
    }

    @Test
    @DisplayName("the copied sequence is linked to the copied worksheet")
    void copiedSequenceIsLinkedToTheCopy() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        ArgumentCaptor<WorksheetDTO> worksheet = ArgumentCaptor.forClass(WorksheetDTO.class);
        ArgumentCaptor<TieredTaskSequenceDTO> sequence =
                ArgumentCaptor.forClass(TieredTaskSequenceDTO.class);
        verify(worksheetRepo).addSequenceToWorksheet(worksheet.capture(), sequence.capture());

        assertAll(
                () -> assertNotEquals(SOURCE_WORKSHEET, worksheet.getValue().getId()),
                () -> assertEquals(assignedSequenceId, sequence.getValue().getId()));
    }

    @Test
    @DisplayName("worksheet-level settings survive the copy")
    void copyPreservesWorksheetSettings() {
        service.copy(SOURCE_WORKSHEET, COPIER);

        WorksheetDTO copy = savedWorksheet();
        assertAll(
                () -> assertEquals("Arrays", copy.getName()),
                () -> assertEquals("Week 3", copy.getDescription()),
                () -> assertFalse(copy.isHintsEnabled(), "a worksheet with hints off stays off"),
                () -> assertEquals(4, copy.getHintCreditsPerTask()));
    }

    /**
     * The worksheet shell is copied by naming fields, because two of the four must not come
     * across. That is defensible for a type this small, but it is still a hand-written list, so
     * it gets the same tripwire the DTOs have: add a field to {@link WorksheetDTO} and this
     * fails until someone decides whether a copy should carry it.
     */
    @Test
    @DisplayName("adding a worksheet field forces a decision about copying it")
    void worksheetFieldsAreAccountedFor() {
        Set<String> carriedOver = Set.of("name", "description", "hintsEnabled", "hintCreditsPerTask");
        Set<String> deliberatelyReset = Set.of(
                "id",             // a copy is a new row
                "ownerId",        // the copy belongs to whoever made it
                "sequenceDTOS",   // refilled with copied sequences, not the source's
                "visibility",     // always PRIVATE; publishing a copy is a separate decision
                "createdAt",      // stamped by the database
                "copiedFrom",     // set to the source, not inherited from it
                "copiedAt");      // when this copy was made

        Set<String> declared = java.util.Arrays.stream(WorksheetDTO.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        Set<String> accountedFor = new java.util.HashSet<>(carriedOver);
        accountedFor.addAll(deliberatelyReset);

        assertEquals(accountedFor, declared,
                "WorksheetDTO's fields no longer match what WorksheetCloneService"
                        + ".copyWorksheetShell handles. Should a copy carry the new field? Add it"
                        + " there and to 'carriedOver'; if not, add it to 'deliberatelyReset'.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tags, concepts, summary
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("tags are re-pointed at the copy, keeping source and confidence")
    void copyCarriesTags() {
        when(tagRepo.findBySequenceId(SOURCE_SEQUENCE)).thenReturn(List.of(
                new SequenceTagDTO(SOURCE_SEQUENCE, "rekursion", "llm", 0.9f),
                new SequenceTagDTO(SOURCE_SEQUENCE, "arrays", "manual", null)));

        service.copy(SOURCE_WORKSHEET, COPIER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SequenceTagDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagRepo).upsertAll(captor.capture());
        List<SequenceTagDTO> written = captor.getValue();

        assertAll(
                () -> assertEquals(2, written.size()),
                () -> assertTrue(written.stream().allMatch(t -> assignedSequenceId.equals(t.getSequenceId())),
                        "every copied tag must point at the copy"),
                () -> assertEquals("llm", written.get(0).getSource(),
                        "a hand-corrected tag must not be downgraded to auto"),
                () -> assertEquals(0.9f, written.get(0).getConfidence()));
    }

    @Test
    @DisplayName("an untagged sequence does not trigger an empty tag write")
    void copyDoesNotWriteEmptyTagList() {
        service.copy(SOURCE_WORKSHEET, COPIER);
        verify(tagRepo, never()).upsertAll(anyList());
    }

    @Test
    @DisplayName("the LLM summary is copied rather than regenerated")
    void copyCarriesSemanticSummary() {
        when(sequenceRepo.findSemanticSummary(SOURCE_SEQUENCE)).thenReturn("Rekursive Umkehrung eines Arrays.");

        service.copy(SOURCE_WORKSHEET, COPIER);

        verify(sequenceRepo).updateSemanticSummary(assignedSequenceId, "Rekursive Umkehrung eines Arrays.");
    }

    @Test
    @DisplayName("a blank summary is not written")
    void copyIgnoresBlankSummary() {
        when(sequenceRepo.findSemanticSummary(SOURCE_SEQUENCE)).thenReturn("   ");

        service.copy(SOURCE_WORKSHEET, COPIER);

        verify(sequenceRepo, never()).updateSemanticSummary(any(), any());
    }

    @Test
    @DisplayName("concept links are copied to the new sequence")
    void copyCarriesConcepts() {
        UUID conceptId = UUID.randomUUID();
        when(sequenceRepo.findConceptIdsBySequenceId(SOURCE_SEQUENCE)).thenReturn(List.of(conceptId));

        service.copy(SOURCE_WORKSHEET, COPIER);

        verify(sequenceRepo).linkConceptsBatch(assignedSequenceId, List.of(conceptId));
    }

    @Test
    @DisplayName("a sequence without concepts does not trigger an empty link write")
    void copyDoesNotWriteEmptyConceptList() {
        service.copy(SOURCE_WORKSHEET, COPIER);
        verify(sequenceRepo, never()).linkConceptsBatch(any(), anyList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Edges
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("edge cases")
    class Edges {

        @Test
        @DisplayName("an empty worksheet copies to an empty worksheet")
        void emptyWorksheetCopiesCleanly() {
            stubWorksheetLoad(List.of());

            service.copy(SOURCE_WORKSHEET, COPIER);

            assertAll(
                    () -> verify(worksheetRepo).createWorksheet(any()),
                    () -> verify(sequenceRepo, never()).save(any()),
                    () -> verify(worksheetRepo, never()).addSequenceToWorksheet(any(), any()));
        }

        @Test
        @DisplayName("a sequence with no tasks copies without one")
        void sequenceWithoutTasksCopiesCleanly() {
            when(taskRepo.loadTasksBySequenceId(any())).thenReturn(List.of());

            service.copy(SOURCE_WORKSHEET, COPIER);

            assertAll(
                    () -> verify(sequenceRepo).save(any()),
                    () -> verify(taskRepo, never()).save(any()));
        }

        @Test
        @DisplayName("every task of a multi-tier sequence is copied")
        void allTasksAreCopied() {
            when(taskRepo.loadTasksBySequenceId(any())).thenReturn(List.of(
                    TaskDTO.builder().tierLevel(1).statement("t1").build(),
                    TaskDTO.builder().tierLevel(2).statement("t2").build(),
                    TaskDTO.builder().tierLevel(3).statement("t3").build()));

            service.copy(SOURCE_WORKSHEET, COPIER);

            verify(taskRepo, times(3)).save(any());
        }

        @Test
        @DisplayName("null arguments are rejected before anything is written")
        void nullArgumentsAreRejected() {
            assertAll(
                    () -> assertThrows(DomainValidationException.class,
                            () -> service.copy(null, COPIER)),
                    () -> assertThrows(DomainValidationException.class,
                            () -> service.copy(SOURCE_WORKSHEET, null)),
                    () -> verify(worksheetRepo, never()).createWorksheet(any()));
        }

        @Test
        @DisplayName("a missing source surfaces the repository's own error")
        void missingSourceIsReported() {
            doAnswer(invocation -> {
                throw new DomainValidationException("Worksheet not found for id=" + SOURCE_WORKSHEET);
            }).when(worksheetRepo).getWorksheetById(any());

            assertThrows(DomainValidationException.class, () -> service.copy(SOURCE_WORKSHEET, COPIER));
            verify(worksheetRepo, never()).createWorksheet(any());
        }

        @Test
        @DisplayName("copying does not mutate the source sequence")
        void sourceIsNotMutated() {
            service.copy(SOURCE_WORKSHEET, COPIER);

            ArgumentCaptor<TieredTaskSequenceDTO> loaded =
                    ArgumentCaptor.forClass(TieredTaskSequenceDTO.class);
            verify(sequenceRepo).loadById(loaded.capture());
            assertEquals(ORIGINAL_OWNER, loaded.getValue().getOwnerId(),
                    "the source sequence must still belong to its author");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixtures and stubs
    // ─────────────────────────────────────────────────────────────────────

    /** A source sequence with every setting that the old copy used to drop. */
    private static TieredTaskSequenceDTO richSourceSequence() {
        return TieredTaskSequenceDTO.builder()
                .id(SOURCE_SEQUENCE)
                .programmingLanguage("java")
                .taskDescription("Reverse an array")
                .codeSnippet("int[] f(int[] a) {}")
                .ownerId(ORIGINAL_OWNER)
                .targetLanguage("en")
                .tierCount(5)
                .concepts(new ArrayList<>(List.of("recursion")))
                .build();
    }

    /** A source task carrying every payload the old copy used to drop. */
    private static TaskDTO richSourceTask() {
        return TaskDTO.builder()
                .id(UUID.randomUUID())
                .sequenceId(SOURCE_SEQUENCE)
                .statement("Reverse it")
                .solution("public int f() {")
                .tierLevel(1)
                .cyclomaticComplexity(7)
                .distractors("wrong line #distractor")
                .distractorPlan("{\"type\":\"LOGIC_FLIP\"}")
                .distractorsEnabled(false)
                .contextBefore("import java.util.*;")
                .contextAfter("}")
                .splitPlan("{\"splits\":[]}")
                .build();
    }

    private void stubWorksheetLoad(List<UUID> sequenceIds) {
        doAnswer(invocation -> {
            WorksheetDTO target = invocation.getArgument(0);
            // Only the source is filled in; the freshly created copy is re-read at the end and
            // would otherwise be handed the source's sequence list.
            if (SOURCE_WORKSHEET.equals(target.getId())) {
                target.setName("Arrays");
                target.setDescription("Week 3");
                target.setOwnerId(ORIGINAL_OWNER);
                target.setHintsEnabled(false);
                target.setHintCreditsPerTask(4);
                target.setSequenceDTOS(sequenceIds.stream()
                        .map(id -> TieredTaskSequenceDTO.builder().id(id).build())
                        .toList());
            } else {
                target.setSequenceDTOS(List.of());
            }
            return null;
        }).when(worksheetRepo).getWorksheetById(any());
    }

    private void stubSequenceLoad(TieredTaskSequenceDTO source) {
        doAnswer(invocation -> {
            TieredTaskSequenceDTO target = invocation.getArgument(0);
            target.setProgrammingLanguage(source.getProgrammingLanguage());
            target.setTaskDescription(source.getTaskDescription());
            target.setCodeSnippet(source.getCodeSnippet());
            target.setOwnerId(source.getOwnerId());
            target.setTargetLanguage(source.getTargetLanguage());
            target.setTierCount(source.getTierCount());
            target.setConcepts(source.getConcepts());
            return null;
        }).when(sequenceRepo).loadById(any());
    }

    /** Stands in for the database handing back a generated primary key. */
    private void stubSequenceSave() {
        doAnswer(invocation -> {
            TieredTaskSequenceDTO saved = invocation.getArgument(0);
            assignedSequenceId = UUID.randomUUID();
            saved.setId(assignedSequenceId);
            return null;
        }).when(sequenceRepo).save(any());
    }

    private WorksheetDTO savedWorksheet() {
        ArgumentCaptor<WorksheetDTO> captor = ArgumentCaptor.forClass(WorksheetDTO.class);
        verify(worksheetRepo).createWorksheet(captor.capture());
        return captor.getValue();
    }

    private TieredTaskSequenceDTO savedSequence() {
        ArgumentCaptor<TieredTaskSequenceDTO> captor =
                ArgumentCaptor.forClass(TieredTaskSequenceDTO.class);
        verify(sequenceRepo).save(captor.capture());
        return captor.getValue();
    }

    private TaskDTO savedTask() {
        ArgumentCaptor<TaskDTO> captor = ArgumentCaptor.forClass(TaskDTO.class);
        verify(taskRepo).save(captor.capture());
        return captor.getValue();
    }
}
