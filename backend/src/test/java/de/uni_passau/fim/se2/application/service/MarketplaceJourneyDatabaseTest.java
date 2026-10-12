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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.application.query.WorksheetSearchQuery;
import de.uni_passau.fim.se2.application.query.WorksheetSearchResult;
import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO.UserRole;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.model.SequenceCuratorPolicy;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.model.WorksheetAccessPolicy;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreSequenceDAO;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreTaskDao;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreWorksheetDao;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.SequenceTagDAO;
import de.uni_passau.fim.se2.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One teacher publishes a worksheet, another finds it and takes a copy.
 *
 * <p>Every piece of that journey has its own tests. What none of them cover is the journey
 * itself: the visibility model, the search and the deep copy were built as separate changes
 * against separate mocks, and the interesting failures in a feature assembled that way are the
 * ones that live between the parts. A copy that is complete but published by accident, a
 * worksheet that publishes but cannot be found, a clone that quietly doubles the library — each
 * would pass every unit test in the repository.
 *
 * <p>Ordered, and deliberately stateful: it is a story, and each step depends on the one before.
 * That is unusual for a test class and worth the exception here, because the thing under test is
 * a sequence of events rather than a set of independent behaviours.
 *
 * <p>Runs in CI against the job's own throwaway Postgres, and locally against a Testcontainers
 * one -- see {@link de.uni_passau.fim.se2.testsupport.TestDatabase}. Skipped only where there is
 * neither: a developer machine with Docker down.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("publishing, finding and copying a worksheet")
class MarketplaceJourneyDatabaseTest {

    private static JdbcTemplate jdbc;
    private static PostgreWorksheetDao worksheetDao;
    private static PostgreSequenceDAO sequenceDao;
    private static PostgreTaskDao taskDao;
    private static SequenceTagDAO tagDao;
    private static WorksheetService worksheetService;
    private static WorksheetSearchService marketplace;
    private static SequenceSearchService library;
    private static SequenceForkService forkService;

    /** Anna publishes; Ben finds and copies. */
    private static UUID anna;
    private static UUID ben;
    private static UUID versionId;

    /** Carried between the ordered steps. */
    private static UUID annasWorksheet;
    private static UUID annasSequence;
    private static UUID bensCopy;

    @BeforeAll
    static void wire() {
        Assumptions.assumeTrue(TestDatabase.isAvailable(),
                "no test database: set SPRING_DATASOURCE_URL or start Docker");
        jdbc = TestDatabase.jdbc();

        // The container is bare except for what V1's pg_dump seeds -- a real snapshot, not
        // fixture data. This class's steps depend on being the only worksheets/sequences in
        // play, so start from an empty table rather than the dump's incidental content.
        jdbc.update("DELETE FROM tiered_task_sequences");
        jdbc.update("DELETE FROM worksheets");

        worksheetDao = new PostgreWorksheetDao(jdbc);
        sequenceDao = new PostgreSequenceDAO(jdbc, new ObjectMapper());
        taskDao = new PostgreTaskDao(jdbc);
        tagDao = new SequenceTagDAO(jdbc);

        worksheetService = new WorksheetService(worksheetDao,
                new WorksheetCloneService(worksheetDao, sequenceDao, taskDao, tagDao));
        marketplace = new WorksheetSearchService(jdbc);
        library = new SequenceSearchService(jdbc, tagDao, sequenceDao);
        forkService = new SequenceForkService(worksheetDao,
                new WorksheetCloneService(worksheetDao, sequenceDao, taskDao, tagDao));

        // A fixture this class needs, not data the migrated schema happens to already carry --
        // the schema is what V1...latest guarantees, not the pg_dump's row contents.
        versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO versions (id, version_name) VALUES (?, ?)",
                versionId, "database-test-version");
        anna = teacher("anna@example.invalid");
        ben = teacher("ben@example.invalid");
    }

    @AfterAll
    static void cleanup() {
        // tasks, sequence_tags, tiered_task_sequence_concepts and worksheets_sequence all
        // cascade from these two, so nothing else needs an explicit delete.
        jdbc.update("DELETE FROM tiered_task_sequences");
        jdbc.update("DELETE FROM worksheets");
        jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", anna, ben);
        jdbc.update("DELETE FROM versions WHERE id = ?", versionId);
    }

    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. Anna builds a worksheet, and it is hers alone until she says otherwise")
    void annaBuildsAWorksheet() {
        WorksheetDTO sheet = WorksheetDTO.builder()
                .name("Rekursion, Blatt 3")
                .description("Drei Stufen, aufsteigend")
                .ownerId(anna)
                .hintsEnabled(false)
                .hintCreditsPerTask(4)
                .build();
        worksheetDao.createWorksheet(sheet);
        annasWorksheet = sheet.getId();

        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder()
                .programmingLanguage("java")
                .taskDescription("Berechne die Fakultaet rekursiv")
                .codeSnippet("int fak(int n) { return n <= 1 ? 1 : n * fak(n - 1); }")
                .ownerId(anna)
                .targetLanguage("en")
                .tierCount(5)
                .versionId(versionId)
                .build();
        sequenceDao.save(sequence);
        annasSequence = sequence.getId();

        taskDao.save(TaskDTO.builder()
                .sequenceId(annasSequence)
                .statement("Ordne die Zeilen").solution("return n * fak(n - 1);")
                .tierLevel(1).cyclomaticComplexity(3)
                .distractors("return n * fak(n);  #distractor")
                .distractorsEnabled(false)
                .contextBefore("public class Fakultaet {")
                .contextAfter("}")
                .splitPlan("{\"splits\":[{\"lineIndex\":0}]}")
                .build());

        worksheetDao.addSequenceToWorksheet(sheet, sequence);
        sequenceDao.updateSemanticSummary(annasSequence, "Rekursive Berechnung der Fakultät.");
        tagDao.upsertAll(List.of(new SequenceTagDTO(annasSequence, "fakultaet", "llm", 0.9f)));

        WorksheetDTO stored = load(annasWorksheet);
        assertAll(
                () -> assertEquals(WorksheetVisibility.UNLISTED, stored.getVisibility(),
                        "a new worksheet behaves exactly as worksheets always did"),
                () -> assertFalse(WorksheetAccessPolicy.canList(stored.getVisibility()),
                        "readable by link is not the same as findable"),
                () -> assertEquals(0, marketplace.search(query(null), ben).total(),
                        "and it is not in the marketplace"));
    }

    @Test
    @Order(2)
    @DisplayName("2. Ben cannot find it, but could open it if Anna sent him the link")
    void benCannotFindItYet() {
        WorksheetDTO sheet = load(annasWorksheet);

        assertAll(
                () -> assertEquals(0, marketplace.search(query("fakultaet"), ben).total(),
                        "searching for its contents finds nothing while it is unlisted"),
                () -> assertTrue(WorksheetAccessPolicy.canView(
                        sheet.getVisibility(), anna, ben, UserRole.TEACHER),
                        "the link Anna already shared keeps working — that is the point of UNLISTED"));
    }

    @Test
    @Order(3)
    @DisplayName("3. Anna makes it private, and now not even the link works")
    void annaMakesItPrivate() {
        worksheetService.setVisibility(annasWorksheet, WorksheetVisibility.PRIVATE, anna, UserRole.TEACHER);
        WorksheetDTO sheet = load(annasWorksheet);

        assertAll(
                () -> assertFalse(WorksheetAccessPolicy.canView(
                        sheet.getVisibility(), anna, ben, UserRole.TEACHER), "Ben cannot open it"),
                () -> assertFalse(WorksheetAccessPolicy.canCopy(
                        sheet.getVisibility(), anna, ben, UserRole.TEACHER),
                        "nor read it by copying it, which is the hole PRIVATE would otherwise open"),
                () -> assertTrue(WorksheetAccessPolicy.canView(
                        sheet.getVisibility(), anna, anna, UserRole.TEACHER), "Anna still can"));
    }

    @Test
    @Order(4)
    @DisplayName("4. Anna publishes, and Ben finds it by what is inside it")
    void annaPublishesAndBenFindsIt() {
        worksheetService.setVisibility(annasWorksheet, WorksheetVisibility.PUBLIC, anna, UserRole.TEACHER);

        // Ben does not know the title. He searches for the topic, which lives in the sequence,
        // and he types it with an umlaut while the stored summary has one too.
        WorksheetSearchResult found = marketplace.search(query("Fakultät"), ben);

        assertAll(
                () -> assertEquals(1, found.total()),
                () -> assertEquals("Rekursion, Blatt 3", found.items().get(0).name()),
                () -> assertEquals(annasWorksheet, found.items().get(0).worksheetId()),
                () -> assertEquals(List.of("java"), found.items().get(0).languages()),
                () -> assertEquals(1, found.items().get(0).sequenceCount()),
                () -> assertFalse(found.items().get(0).alreadyCopied()),
                () -> assertNotNull(found.items().get(0).highlight(), "his search term is marked"));
    }

    @Test
    @Order(5)
    @DisplayName("5. a half-typed search still finds it, because the box is live")
    void benSearchesAsHeTypes() {
        assertAll(
                () -> assertEquals(1, marketplace.search(query("fak"), ben).total()),
                () -> assertEquals(1, marketplace.search(query("rekur"), ben).total()),
                () -> assertEquals(0, marketplace.search(query("sortieren"), ben).total()));
    }

    @Test
    @Order(6)
    @DisplayName("6. Ben copies it and gets everything Anna built, owned by him and unpublished")
    void benCopiesIt() {
        WorksheetDTO copy = worksheetService.copyWorksheet(annasWorksheet, ben);
        bensCopy = copy.getId();

        UUID copiedSequence = jdbc.queryForObject(
                "SELECT sequence_id FROM worksheets_sequence WHERE worksheet_id = ?", UUID.class, bensCopy);
        var task = jdbc.queryForMap("SELECT * FROM tasks WHERE sequence_id = ?", copiedSequence);
        var sequence = jdbc.queryForMap(
                "SELECT * FROM tiered_task_sequences WHERE id = ?", copiedSequence);
        WorksheetDTO stored = load(bensCopy);

        assertAll(
                // MR1: the copy is complete
                () -> assertEquals(5, sequence.get("tier_count"), "five tiers stay five"),
                () -> assertEquals("en", sequence.get("target_language")),
                () -> assertEquals("Rekursive Berechnung der Fakultät.", sequence.get("semantic_summary")),
                () -> assertEquals("return n * fak(n);  #distractor", task.get("distractors")),
                () -> assertEquals(false, task.get("distractors_enabled")),
                () -> assertEquals("public class Fakultaet {", task.get("context_before")),
                () -> assertNotNull(task.get("split_plan")),
                () -> assertEquals(false, stored.isHintsEnabled()),
                () -> assertEquals(4, stored.getHintCreditsPerTask()),
                () -> assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sequence_tags WHERE sequence_id = ?", Integer.class,
                        copiedSequence), "and its tags, so it answers to the same searches"),

                // MR2: it is his, and it is not published on his behalf
                () -> assertEquals(ben, stored.getOwnerId()),
                () -> assertEquals(WorksheetVisibility.PRIVATE, stored.getVisibility(),
                        "copying is how you take material to adapt; publishing is a separate act"),
                () -> assertEquals(annasWorksheet, stored.getCopiedFrom()),
                () -> assertNotEquals(annasWorksheet, bensCopy));
    }

    @Test
    @Order(7)
    @DisplayName("7. Ben's copy stays out of the marketplace and out of the library")
    void thecopyDoesNotPollute() {
        WorksheetSearchResult found = marketplace.search(query("fakultaet"), ben);
        var listed = library.search(new SequenceSearchService.SearchQuery().q("fakultaet"), ben);

        assertAll(
                () -> assertEquals(1, found.total(), "only Anna's original is published"),
                () -> assertEquals(annasWorksheet, found.items().get(0).worksheetId()),
                () -> assertTrue(found.items().get(0).alreadyCopied(),
                        "and Ben is told he already has it"),
                () -> assertEquals(1, found.items().get(0).copyCount()),
                () -> assertEquals(1, listed.total(), "only Anna's original is in the library"),
                () -> assertEquals(annasSequence, listed.items().get(0).sequenceId(),
                        "the clone does not appear beside it — the invariant the copied_from"
                                + " filter exists for"));
    }

    @Test
    @Order(8)
    @DisplayName("8. Ben edits his copy and Anna's original is untouched")
    void bensEditsDoNotReachAnna() {
        UUID copiedSequence = jdbc.queryForObject(
                "SELECT sequence_id FROM worksheets_sequence WHERE worksheet_id = ?", UUID.class, bensCopy);
        jdbc.update("UPDATE tiered_task_sequences SET task_description = ? WHERE id = ?",
                "Ben's Fassung", copiedSequence);

        assertEquals("Berechne die Fakultaet rekursiv", jdbc.queryForObject(
                "SELECT task_description FROM tiered_task_sequences WHERE id = ?",
                String.class, annasSequence),
                "a copy in your own namespace is the whole point — it must not write back");
    }

    @Test
    @Order(9)
    @DisplayName("9. Anna unpublishes; Ben keeps his copy")
    void annaUnpublishes() {
        worksheetService.setVisibility(annasWorksheet, WorksheetVisibility.UNLISTED, anna, UserRole.TEACHER);

        assertAll(
                () -> assertEquals(0, marketplace.search(query("fakultaet"), ben).total(),
                        "it leaves the marketplace"),
                () -> assertNotNull(load(bensCopy).getName(),
                        "but what Ben already took is his to keep"),
                () -> assertEquals(annasWorksheet, load(bensCopy).getCopiedFrom(),
                        "including the record of where it came from"));
    }

    @Test
    @Order(10)
    @DisplayName("10. Ben may publish his own copy; Anna may not publish it for him")
    void benCanPublishHisCopy() {
        assertAll(
                () -> assertEquals(WorksheetVisibility.PUBLIC, worksheetService.setVisibility(
                        bensCopy, WorksheetVisibility.PUBLIC, ben, UserRole.TEACHER).getVisibility()),
                () -> assertEquals(1, marketplace.search(query("Fakultät"), anna).total(),
                        "and now Anna can find Ben's version"),
                () -> assertThrowsAccessDenied(() -> worksheetService.setVisibility(
                        bensCopy, WorksheetVisibility.PRIVATE, anna, UserRole.TEACHER)));
    }

    @Test
    @Order(11)
    @DisplayName("11. searching without umlauts finds text written with them")
    void umlautFoldingWorksBothWays() {
        // Used to be recorded rather than fixed: the old fold rewrote only the *query* ä→ae,
        // which made rows written before the tagger prompt was fixed ("Pruefung") findable by
        // someone typing properly, but did nothing for the reverse — the tagger now writes real
        // umlauts, and a teacher who typed "fakultaet" (quicker on many keyboards) found nothing.
        //
        // Closed by search_normalize (db/migration/V19__unaccent_search.sql), built on the
        // unaccent extension and applied to both the generated search_vector columns and the
        // query, so it no longer matters which spelling was stored and which was typed.
        assertAll(
                () -> assertEquals(1, marketplace.search(query("Fakultät"), anna).total(),
                        "typed with the umlaut, as stored"),
                () -> assertEquals(1, marketplace.search(query("Fakultaet"), anna).total(),
                        "and typed without it, too"));
    }

    @Test
    @Order(12)
    @DisplayName("12. Anna forks Ben's sequence to edit it, and Ben's own copy survives untouched")
    void curatorForksBeforeEditing() {
        // Anna curates: she adds a sequence of Ben's into a worksheet of her own. She may look
        // at it -- she chose it -- but editing it in place would rewrite it inside Ben's
        // worksheet too, which is what forking exists to prevent.
        UUID bensSequence = copiedSequenceOf(bensCopy);
        // createWorksheet assigns the id itself, so read it back rather than dictating one.
        WorksheetDTO annasSecond = WorksheetDTO.builder()
                .name("Annas Sammlung").description("kuratiert")
                .ownerId(anna).visibility(WorksheetVisibility.PRIVATE).build();
        worksheetDao.createWorksheet(annasSecond);
        UUID annasSecondSheet = annasSecond.getId();
        worksheetDao.addSequenceToWorksheet(
                load(annasSecondSheet), TieredTaskSequenceDTO.builder().id(bensSequence).build());

        assertAll("before the fork",
                () -> assertTrue(worksheetDao.isCuratedBy(bensSequence, anna),
                        "Anna put it in her worksheet, so she curates it"),
                () -> assertTrue(worksheetDao.isCuratedBy(bensSequence, ben),
                        "and Ben still has it in his"),
                () -> assertTrue(SequenceCuratorPolicy.mustForkToEdit(
                        ben, anna, UserRole.TEACHER, true),
                        "which is exactly the case that must fork rather than edit"));

        UUID fork = forkService.forkForEditing(annasSecondSheet, bensSequence, anna);

        Integer tasksOnOriginal = jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE sequence_id = ?", Integer.class, bensSequence);
        Integer tasksOnFork = jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE sequence_id = ?", Integer.class, fork);

        assertAll("after the fork",
                () -> assertEquals(anna, jdbc.queryForObject(
                        "SELECT owner_id FROM tiered_task_sequences WHERE id = ?", UUID.class, fork),
                        "the copy is Anna's, so her edits land on her own material"),
                () -> assertEquals(bensSequence, jdbc.queryForObject(
                        "SELECT copied_from FROM tiered_task_sequences WHERE id = ?", UUID.class, fork),
                        "and says where it came from"),
                () -> assertEquals(fork, copiedSequenceOfSheet(annasSecondSheet),
                        "Anna's worksheet now points at the copy, and only at the copy"),
                () -> assertEquals(ben, jdbc.queryForObject(
                        "SELECT owner_id FROM tiered_task_sequences WHERE id = ?", UUID.class, bensSequence),
                        "Ben still owns the original"),
                () -> assertEquals(bensSequence, copiedSequenceOf(bensCopy),
                        "and his worksheet still points at it -- the fork touched nobody else"),
                () -> assertEquals(tasksOnOriginal, tasksOnFork,
                        "the copy carries the same tasks; completeness itself is CloneCompletenessTest's job"),
                () -> assertFalse(worksheetDao.isCuratedBy(bensSequence, anna),
                        "and Anna no longer curates the original, having moved off it"));
    }

    @Test
    @Order(12)
    @DisplayName("12b. the worksheet knows who owns each sequence in it")
    void worksheetCarriesSequenceOwners() {
        // The page marks foreign material so that forking one sequence visibly changes the
        // list. That only works if the worksheet query brings the owners along -- it used to
        // return bare ids, and the two arrays it now aggregates have to stay in step.
        WorksheetDTO bensSheet = load(bensCopy);

        assertAll(
                () -> assertEquals(1, bensSheet.getSequenceDTOS().size()),
                () -> assertEquals(ben, bensSheet.getSequenceDTOS().get(0).getOwnerId(),
                        "Ben copied it, so the sequence in his worksheet is his"),
                () -> assertEquals(copiedSequenceOf(bensCopy), bensSheet.getSequenceDTOS().get(0).getId(),
                        "and the id must still line up with the owner beside it"));
    }

    @Test
    @Order(13)
    @DisplayName("13. a teacher who never curated the sequence is offered no fork at all")
    void strangersGetNoFork() {
        // Forking writes into a worksheet. Somebody with no worksheet holding this sequence has
        // nothing to write into, so the offer must not appear -- and the service refuses even
        // if a crafted request reaches it.
        UUID bensSequence = copiedSequenceOf(bensCopy);
        WorksheetDTO strangers = WorksheetDTO.builder()
                .name("Fremdes Blatt").description("ohne die Sequenz")
                .ownerId(anna).visibility(WorksheetVisibility.PRIVATE).build();
        worksheetDao.createWorksheet(strangers);
        UUID strangersSheet = strangers.getId();

        assertAll(
                () -> assertFalse(SequenceCuratorPolicy.canInspect(
                        ben, anna, UserRole.TEACHER, false)),
                () -> assertThrows(DomainValidationException.class,
                        () -> forkService.forkForEditing(strangersSheet, bensSequence, anna),
                        "forking into a worksheet that does not contain the sequence would add material to it"));
    }

    @Test
    @Order(14)
    @DisplayName("14. Ben reworks his copy, and the library lists it as his own material")
    void anEditedCopyIsListedAgain() {
        UUID bensSequence = copiedSequenceOf(bensCopy);

        // Searched by the title step 8 gave it: the sequence vector is generated from
        // task_description, so Ben's copy stopped answering to "fakultaet" the moment he
        // retitled it. Anna's original still does, which is what separates the two here.
        assertFalse(listedInLibrary(bensSequence, "fassung"),
                "an untouched copy stays out of the library");

        assertTrue(sequenceDao.markDiverged(bensSequence),
                "the first edit to a copy is what marks it");
        assertTrue(listedInLibrary(bensSequence, "fassung"),
                "once reworked it is Ben's own material, and he must be able to find it");

        // Anna's original does not lose its place to the copy.
        assertTrue(listedInLibrary(annasSequence, "fakultaet"), "the original stays listed too");
    }

    @Test
    @Order(15)
    @DisplayName("15. marking is idempotent, and an original is never marked at all")
    void divergenceIsRecordedOnce() {
        UUID bensSequence = copiedSequenceOf(bensCopy);
        Instant first = divergedAt(bensSequence);

        assertAll(
                () -> assertNotNull(first, "step 14 marked it"),
                // "First edited" is the question diverged_at answers, so a second edit must not
                // move it -- otherwise every save would rewrite the sequence's own history.
                () -> assertFalse(sequenceDao.markDiverged(bensSequence),
                        "a second edit reports that it changed nothing"),
                () -> assertEquals(first, divergedAt(bensSequence),
                        "and leaves the original timestamp alone"),
                // An original has nothing to diverge from; stamping one would claim it was
                // copied from somewhere.
                () -> assertFalse(sequenceDao.markDiverged(annasSequence),
                        "an original is not a copy and cannot diverge"),
                () -> assertNull(divergedAt(annasSequence)),
                () -> assertFalse(sequenceDao.markDiverged(UUID.randomUUID()),
                        "an unknown id is a no-op, not a failure"));
    }

    // ─────────────────────────────────────────────────────────────────────

    /** Whether the library's own query returns this sequence when searched for {@code text}. */
    private static boolean listedInLibrary(UUID sequenceId, String text) {
        return library.search(new SequenceSearchService.SearchQuery().q(text), ben)
                .items().stream()
                .anyMatch(i -> sequenceId.equals(i.sequenceId()));
    }

    private static Instant divergedAt(UUID sequenceId) {
        var ts = jdbc.queryForObject(
                "SELECT diverged_at FROM tiered_task_sequences WHERE id = ?",
                java.sql.Timestamp.class, sequenceId);
        return ts == null ? null : ts.toInstant();
    }

    private static void assertThrowsAccessDenied(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected the other teacher to be refused");
        } catch (org.springframework.security.access.AccessDeniedException expected) {
            // exactly what should happen
        }
    }

    /** The single sequence a worksheet links to; fails loudly if there is more than one. */
    private static UUID copiedSequenceOfSheet(UUID worksheetId) {
        return copiedSequenceOf(worksheetId);
    }

    private static UUID copiedSequenceOf(UUID worksheetId) {
        return jdbc.queryForObject(
                "SELECT sequence_id FROM worksheets_sequence WHERE worksheet_id = ?",
                UUID.class, worksheetId);
    }

    private static WorksheetSearchQuery query(String text) {
        return WorksheetSearchQuery.of(text, null, false, null, 0, 20);
    }

    private static WorksheetDTO load(UUID id) {
        WorksheetDTO target = WorksheetDTO.builder().id(id).build();
        worksheetDao.getWorksheetById(target);
        return target;
    }

    private static UUID teacher(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, email, first_name, last_name, password_hash,"
                        + " role, is_verified, is_active) VALUES (?,?,?,?,?,CAST(? AS user_role),?,?)",
                id, email, "T", "T", "unused", "TEACHER", true, true);
        return id;
    }
}
