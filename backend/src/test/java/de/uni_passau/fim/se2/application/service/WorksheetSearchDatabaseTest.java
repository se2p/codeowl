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

import de.uni_passau.fim.se2.application.query.WorksheetSearchQuery;
import de.uni_passau.fim.se2.application.query.WorksheetSearchResult;
import de.uni_passau.fim.se2.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreSequenceDAO;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreWorksheetDao;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.SequenceTagDAO;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The marketplace search against a real Postgres.
 *
 * <p>The one part of this feature that no mock can speak for. Full-text vectors, {@code
 * websearch_to_tsquery}, prefix matching, {@code ts_headline} and the GIN index are database
 * behaviour; a unit test asserting on the SQL string can only confirm we wrote what we wrote.
 * Everything here would have passed a mock and still been wrong in production.
 *
 * <p>The database starts bare and {@link TestDatabase} runs the real Flyway migrations against
 * it (see {@code backend/src/main/resources/db/migration}), so the schema under test is the
 * schema that ships — including the migrations added by this feature.
 *
 * <p>This class used to skip in CI: the shared runner is unprivileged, a {@code docker:dind}
 * service dies on {@code mount: permission denied} before it ever listens, and Testcontainers
 * then has no daemon to build a database with. It now runs there against the throwaway {@code
 * services: postgres} container the {@code backend-test} job starts beside itself, injected
 * through {@code SPRING_DATASOURCE_URL}; Testcontainers is still what supplies one locally.
 *
 * <p>What remains skipped is a developer machine with Docker down and no database injected. That
 * is an abort with a stated reason rather than a silent pass, and it no longer hides anything
 * from the pipeline.
 */
@DisplayName("marketplace search against real Postgres")
class WorksheetSearchDatabaseTest {

    private static JdbcTemplate jdbc;
    private static WorksheetSearchService service;

    private static UUID teacher;
    private static UUID otherTeacher;
    private static UUID versionId;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(TestDatabase.isAvailable(),
                "no test database: set SPRING_DATASOURCE_URL or start Docker");
        jdbc = TestDatabase.jdbc();
        service = new WorksheetSearchService(jdbc);

        // Fixtures this class needs, not data the migrated schema happens to already carry --
        // the schema is what V1...latest guarantees, not the pg_dump's row contents.
        versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO versions (id, version_name) VALUES (?, ?)",
                versionId, "database-test-version");
        teacher = newTeacher("teacher@example.invalid");
        otherTeacher = newTeacher("other@example.invalid");
    }

    @AfterAll
    static void cleanup() {
        clearFixtures();
        jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", teacher, otherTeacher);
        jdbc.update("DELETE FROM versions WHERE id = ?", versionId);
    }

    private static UUID newTeacher(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, email, first_name, last_name, password_hash,"
                        + " role, is_verified, is_active) VALUES (?,?,?,?,?,CAST(? AS user_role),?,?)",
                id, email, "T", "T", "unused", "TEACHER", true, true);
        return id;
    }

    @BeforeEach
    void resetFixtures() {
        clearFixtures();
    }

    private static void clearFixtures() {
        jdbc.update("DELETE FROM worksheets_sequence");
        jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM tiered_task_sequences");
        jdbc.update("DELETE FROM worksheets");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Visibility — the reason the endpoint is safe to serve
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("visibility")
    class Visibility {

        @Test
        @DisplayName("only PUBLIC worksheets are listed")
        void onlyPublicIsListed() {
            worksheet("Public one", "", teacher, "PUBLIC");
            worksheet("Unlisted one", "", teacher, "UNLISTED");
            worksheet("Private one", "", teacher, "PRIVATE");

            List<String> names = search(query(null)).items().stream()
                    .map(WorksheetSearchResult.Item::name).toList();

            assertEquals(List.of("Public one"), names,
                    "a worksheet readable by link is not thereby findable");
        }

        @Test
        @DisplayName("the Library Inbox never shows up, even if it somehow got published")
        void libraryInboxIsExcluded() {
            worksheet("Library Inbox",
                    "Quick-copied sequences. " + WorksheetService.LIBRARY_INBOX_SENTINEL,
                    teacher, "PUBLIC");
            worksheet("Real worksheet", "", teacher, "PUBLIC");

            List<String> names = search(query(null)).items().stream()
                    .map(WorksheetSearchResult.Item::name).toList();

            assertEquals(List.of("Real worksheet"), names);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Full text — what only a database can answer
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("full-text search")
    class FullText {

        @Test
        @DisplayName("matches the worksheet's own name and description")
        void matchesOwnText() {
            worksheet("Rekursion", "", teacher, "PUBLIC");
            worksheet("Blatt 2", "Uebungen zur Rekursion", teacher, "PUBLIC");
            worksheet("Arrays", "nichts damit zu tun", teacher, "PUBLIC");

            assertEquals(2, search(query("rekursion")).total());
        }

        @Test
        @DisplayName("finds a worksheet by what is inside it, not just by its title")
        void matchesSequenceText() {
            // The point of the EXISTS arm: "Blatt 3" says nothing, its contents say everything,
            // and this is how a teacher actually looks for material.
            UUID sheet = worksheet("Blatt 3", "", teacher, "PUBLIC");
            UUID sequence = sequence("java", "Binaere Suche im sortierten Array");
            link(sheet, sequence);

            List<WorksheetSearchResult.Item> items = search(query("suche")).items();
            assertAll(
                    () -> assertEquals(1, items.size()),
                    () -> assertEquals("Blatt 3", items.get(0).name()));
        }

        @Test
        @DisplayName("finds a worksheet by the text of a task inside one of its sequences")
        void matchesTaskText() {
            UUID sheet = worksheet("Blatt 4", "", teacher, "PUBLIC");
            UUID sequence = sequence("java", "irrelevant");
            task(sequence, "Vertausche zwei Elemente", "return null;");
            link(sheet, sequence);

            assertEquals(1, search(query("vertausche")).total());
        }

        @Test
        @DisplayName("a search typed with umlauts finds rows spelled with digraphs")
        void umlautsFoldBothWays() {
            // Rows written before the tagger prompt was fixed spell it "Pruefung". Without the
            // fold, a teacher typing the word correctly finds nothing.
            worksheet("Pruefungsvorbereitung", "", teacher, "PUBLIC");

            assertAll(
                    () -> assertEquals(1, search(query("Prüfungsvorbereitung")).total(),
                            "typed with umlauts, stored with digraphs"),
                    () -> assertEquals(1, search(query("Pruefungsvorbereitung")).total(),
                            "and the plain spelling still works"));
        }

        @Test
        @DisplayName("a search typed without umlauts finds and highlights a row spelled with them")
        void umlautsHighlightBothWays() {
            // The reverse of umlautsFoldBothWays: the tagger now writes real umlauts, and a
            // teacher typing the ASCII spelling must both find and see it marked -- ts_headline
            // re-tokenizes the raw name on every call rather than reading search_vector, so
            // getting the row back is not enough on its own to prove the highlight works too.
            worksheet("Prüfungsvorbereitung", "", teacher, "PUBLIC");

            String highlight = search(query("Pruefungsvorbereitung")).items().get(0).highlight();
            assertAll(
                    () -> assertEquals(1, search(query("Pruefungsvorbereitung")).total()),
                    () -> assertNotNull(highlight),
                    () -> assertTrue(highlight.contains(SequenceSearchService.HIGHLIGHT_START_MARKER
                                    + "Prüfungsvorbereitung" + SequenceSearchService.HIGHLIGHT_STOP_MARKER),
                            "the umlaut spelling itself must be marked, not merely present: " + highlight));
        }

        @Test
        @DisplayName("a half-typed word still matches, because the search box is live")
        void prefixMatchingWorks() {
            worksheet("Rekursion", "", teacher, "PUBLIC");

            assertAll(
                    () -> assertEquals(1, search(query("rek")).total()),
                    () -> assertEquals(1, search(query("rekur")).total()),
                    () -> assertEquals(0, search(query("zzz")).total()));
        }

        @Test
        @DisplayName("the match is highlighted with the markers the frontend expects")
        void highlightUsesSharedMarkers() {
            worksheet("Rekursion in Java", "", teacher, "PUBLIC");

            String highlight = search(query("rekursion")).items().get(0).highlight();
            assertAll(
                    () -> assertNotNull(highlight),
                    () -> assertTrue(highlight.contains(SequenceSearchService.HIGHLIGHT_START_MARKER),
                            "one frontend helper renders both searches; the markers must agree"),
                    () -> assertTrue(highlight.contains(SequenceSearchService.HIGHLIGHT_STOP_MARKER)));
        }

        @Test
        @DisplayName("no query means no highlight rather than an empty one")
        void noQueryNoHighlight() {
            worksheet("Rekursion", "", teacher, "PUBLIC");
            assertEquals(null, search(query(null)).items().get(0).highlight());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // What a card shows
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("card data")
    class CardData {

        @Test
        @DisplayName("a card carries its contents, so a teacher can judge it without opening it")
        void cardCarriesContents() {
            UUID sheet = worksheet("Gemischt", "", teacher, "PUBLIC");
            link(sheet, sequence("java", "a"));
            link(sheet, sequence("python", "b"));
            link(sheet, sequence("java", "c"));

            WorksheetSearchResult.Item item = search(query(null)).items().get(0);
            assertAll(
                    () -> assertEquals(3, item.sequenceCount()),
                    () -> assertEquals(List.of("java", "python"), item.languages(),
                            "distinct and sorted, so the card reads the same every time"),
                    () -> assertNotNull(item.createdAt()));
        }

        @Test
        @DisplayName("copies are counted, and the caller is told they already have one")
        void copyCountAndAlreadyCopied() {
            UUID original = worksheet("Original", "", teacher, "PUBLIC");
            copyOf(original, "Copy A", otherTeacher);
            copyOf(original, "Copy B", teacher);

            WorksheetSearchResult.Item asTeacher = search(query(null), teacher).items().get(0);
            WorksheetSearchResult.Item asStranger = search(query(null), UUID.randomUUID()).items().get(0);

            assertAll(
                    () -> assertEquals(2, asTeacher.copyCount()),
                    () -> assertTrue(asTeacher.alreadyCopied(),
                            "so nobody makes a second copy of something they already took"),
                    () -> assertFalse(asStranger.alreadyCopied()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sorting, paging, facets
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ordering and paging")
    class OrderingAndPaging {

        @Test
        @DisplayName("newest first by default")
        void recentIsTheDefault() {
            UUID older = worksheet("Older", "", teacher, "PUBLIC");
            worksheet("Newer", "", teacher, "PUBLIC");
            jdbc.update("UPDATE worksheets SET created_at = now() - interval '10 days' WHERE id = ?", older);

            assertEquals("Newer", search(query(null)).items().get(0).name());
        }

        @Test
        @DisplayName("popularity orders by how often a worksheet was copied")
        void popularitySort() {
            UUID quiet = worksheet("Quiet", "", teacher, "PUBLIC");
            UUID popular = worksheet("Popular", "", teacher, "PUBLIC");
            copyOf(popular, "c1", otherTeacher);
            copyOf(popular, "c2", otherTeacher);
            copyOf(quiet, "c3", otherTeacher);

            var result = service.search(
                    WorksheetSearchQuery.of(null, null, false, "popularity", 0, 20), teacher);
            assertEquals(List.of("Popular", "Quiet"),
                    result.items().stream().map(WorksheetSearchResult.Item::name).toList());
        }

        @Test
        @DisplayName("paging past the last page is empty, not an error")
        void pagingPastTheEnd() {
            worksheet("Only one", "", teacher, "PUBLIC");

            var result = service.search(
                    WorksheetSearchQuery.of(null, null, false, null, 47, 20), teacher);
            assertAll(
                    () -> assertTrue(result.items().isEmpty()),
                    () -> assertEquals(1, result.total(), "the total still describes the whole set"));
        }

        @Test
        @DisplayName("pages do not overlap or skip")
        void pagesTile() {
            for (int i = 0; i < 5; i++) {
                worksheet("Sheet " + i, "", teacher, "PUBLIC");
            }
            var first = service.search(WorksheetSearchQuery.of(null, null, false, null, 0, 2), teacher);
            var second = service.search(WorksheetSearchQuery.of(null, null, false, null, 1, 2), teacher);

            assertAll(
                    () -> assertEquals(2, first.items().size()),
                    () -> assertEquals(2, second.items().size()),
                    () -> assertEquals(5, first.total()),
                    () -> assertTrue(first.items().stream().noneMatch(
                            a -> second.items().stream().anyMatch(b -> b.worksheetId().equals(a.worksheetId()))),
                            "a stable total order is what keeps a row from being shown twice or never"));
        }
    }

    @Nested
    @DisplayName("facets and filters")
    class FacetsAndFilters {

        @Test
        @DisplayName("the language filter matches through the worksheet's sequences")
        void languageFilter() {
            UUID javaSheet = worksheet("Java sheet", "", teacher, "PUBLIC");
            link(javaSheet, sequence("java", "a"));
            UUID pythonSheet = worksheet("Python sheet", "", teacher, "PUBLIC");
            link(pythonSheet, sequence("python", "b"));

            var result = service.search(
                    WorksheetSearchQuery.of(null, "java", false, null, 0, 20), teacher);
            assertAll(
                    () -> assertEquals(1, result.total()),
                    () -> assertEquals("Java sheet", result.items().get(0).name()));
        }

        @Test
        @DisplayName("the sidebar still offers the languages you have not picked")
        void facetsIgnoreTheirOwnFilter() {
            UUID javaSheet = worksheet("Java sheet", "", teacher, "PUBLIC");
            link(javaSheet, sequence("java", "a"));
            UUID pythonSheet = worksheet("Python sheet", "", teacher, "PUBLIC");
            link(pythonSheet, sequence("python", "b"));

            var result = service.search(
                    WorksheetSearchQuery.of(null, "java", false, null, 0, 20), teacher);
            List<String> offered = result.facets().languages().stream()
                    .map(WorksheetSearchResult.Bucket::name).sorted().toList();

            assertEquals(List.of("java", "python"), offered,
                    "counting only the language already chosen makes the sidebar a dead end");
        }

        @Test
        @DisplayName("facets are still counted correctly alongside a free-text query")
        void facetsSurviveAFreeTextQuery() {
            // The free-text clause nests EXISTS subqueries that re-use the same table aliases
            // the facet query joins on. Legal SQL, and easy to get subtly wrong.
            UUID javaSheet = worksheet("Rekursion in Java", "", teacher, "PUBLIC");
            link(javaSheet, sequence("java", "a"));
            UUID pythonSheet = worksheet("Rekursion in Python", "", otherTeacher, "PUBLIC");
            link(pythonSheet, sequence("python", "b"));
            worksheet("Arrays", "", teacher, "PUBLIC");

            var result = service.search(
                    WorksheetSearchQuery.of("rekursion", null, false, null, 0, 20), teacher);
            List<String> offered = result.facets().languages().stream()
                    .map(WorksheetSearchResult.Bucket::name).sorted().toList();

            assertAll(
                    () -> assertEquals(2, result.total(), "only the two matching worksheets"),
                    () -> assertEquals(List.of("java", "python"), offered,
                            "and the facets describe those two, not everything published"),
                    () -> assertEquals(1, result.facets().mine()),
                    () -> assertEquals(1, result.facets().others()));
        }

        @Test
        @DisplayName("mine and others add up to the whole result set")
        void ownershipFacets() {
            worksheet("Mine 1", "", teacher, "PUBLIC");
            worksheet("Mine 2", "", teacher, "PUBLIC");
            worksheet("Theirs", "", otherTeacher, "PUBLIC");

            var facets = search(query(null), teacher).facets();
            assertAll(
                    () -> assertEquals(2, facets.mine()),
                    () -> assertEquals(1, facets.others()));
        }

        @Test
        @DisplayName("only mine narrows to the caller; anonymous gets nothing rather than everything")
        void onlyMine() {
            worksheet("Mine", "", teacher, "PUBLIC");
            worksheet("Theirs", "", otherTeacher, "PUBLIC");

            var asTeacher = service.search(
                    WorksheetSearchQuery.of(null, null, true, null, 0, 20), teacher);
            var anonymous = service.search(
                    WorksheetSearchQuery.of(null, null, true, null, 0, 20), null);

            assertAll(
                    () -> assertEquals(1, asTeacher.total()),
                    () -> assertEquals("Mine", asTeacher.items().get(0).name()),
                    () -> assertEquals(0, anonymous.total()));
        }
    }

    @Nested
    @DisplayName("looking a worksheet up")
    class LookingUp {

        @Test
        @DisplayName("a missing worksheet is an empty result, not an exception")
        void missingWorksheetIsEmpty() {
            // What lets the read endpoint answer 404 for "no such worksheet" and "not yours to
            // see" alike. The throwing variant produced a 400 saying "not found", which told an
            // unauthorised caller exactly which ids were real.
            assertTrue(new PostgreWorksheetDao(jdbc).findById(UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("null is an empty result too, rather than something to guard against")
        void nullIsEmpty() {
            assertTrue(new PostgreWorksheetDao(jdbc).findById(null).isEmpty());
        }

        @Test
        @DisplayName("an existing worksheet comes back whole, sequences included")
        void existingWorksheetComesBackWhole() {
            UUID sheet = worksheet("Findable", "with contents", teacher, "PUBLIC");
            link(sheet, sequence("java", "a"));

            var found = new PostgreWorksheetDao(jdbc).findById(sheet);
            assertAll(
                    () -> assertTrue(found.isPresent()),
                    () -> assertEquals("Findable", found.get().getName()),
                    () -> assertEquals(1, found.get().getSequenceDTOS().size(),
                            "the same query the throwing variant uses, so it loads the same thing"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // The library exclusion this feature made necessary
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the sequence library lists originals and hides their clones")
    void libraryHidesClones() {
        UUID original = sequence("java", "Rekursive Fakultaet");
        UUID clone = sequence("java", "Rekursive Fakultaet");
        jdbc.update("UPDATE tiered_task_sequences SET copied_from = ? WHERE id = ?", original, clone);

        SequenceSearchService library = new SequenceSearchService(jdbc, new SequenceTagDAO(jdbc), new PostgreSequenceDAO(jdbc, new ObjectMapper()));
        var found = library.search(new SequenceSearchService.SearchQuery().q("fakultaet"), teacher);

        // Both rows carry the same text, so without the filter the teacher sees the same
        // sequence twice with no way to tell which is the one worth using.
        assertAll(
                () -> assertEquals(1, found.total()),
                () -> assertEquals(original, found.items().get(0).sequenceId()));
    }

    @Test
    @DisplayName("a library search without umlauts still highlights the umlaut spelling stored in the row")
    void libraryHighlightsTheStoredSpellingRegardlessOfHowTheQueryWasTyped() {
        // ts_headline re-tokenizes the raw task_description on every call rather than reading
        // search_vector, so the same search_normalize'd query that correctly finds this row
        // would never match its raw "Fakultät" lexeme for highlighting -- the row would come
        // back in the result list with nothing marked in its summary. HIGHLIGHT_TSQUERY_EXPR
        // exists specifically to keep that from happening.
        sequence("java", "Rekursive Berechnung der Fakultät.");

        SequenceSearchService library = new SequenceSearchService(jdbc, new SequenceTagDAO(jdbc), new PostgreSequenceDAO(jdbc, new ObjectMapper()));
        String highlight = library.search(new SequenceSearchService.SearchQuery().q("fakultaet"), teacher)
                .items().get(0).highlight();

        assertAll(
                () -> assertNotNull(highlight),
                () -> assertTrue(highlight.contains(SequenceSearchService.HIGHLIGHT_START_MARKER
                                + "Fakultät" + SequenceSearchService.HIGHLIGHT_STOP_MARKER),
                        "the umlaut spelling itself must be marked, not merely present: " + highlight));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────

    private WorksheetSearchResult search(WorksheetSearchQuery q) {
        return service.search(q, teacher);
    }

    private WorksheetSearchResult search(WorksheetSearchQuery q, UUID user) {
        return service.search(q, user);
    }

    private static WorksheetSearchQuery query(String text) {
        return WorksheetSearchQuery.of(text, null, false, null, 0, 20);
    }

    private static UUID worksheet(String name, String description, UUID owner, String visibility) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO worksheets (id, name, description, owner_id, visibility)"
                + " VALUES (?, ?, ?, ?, ?)", id, name, description, owner, visibility);
        return id;
    }

    private static UUID copyOf(UUID source, String name, UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO worksheets (id, name, description, owner_id, visibility, copied_from)"
                + " VALUES (?, ?, '', ?, 'PRIVATE', ?)", id, name, owner, source);
        return id;
    }

    private static UUID sequence(String language, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tiered_task_sequences (id, programming_language, task_description,"
                        + " version_id, owner_id) VALUES (?, ?, ?, ?, ?)",
                id, language, description, versionId, teacher);
        return id;
    }

    private static void task(UUID sequenceId, String statement, String solution) {
        jdbc.update("INSERT INTO tasks (id, statement, solution, tier_level, cyclomatic_complexity,"
                        + " sequence_id) VALUES (?, ?, ?, 1, 1, ?)",
                UUID.randomUUID(), statement, solution, sequenceId);
    }

    private static void link(UUID worksheetId, UUID sequenceId) {
        // position is NOT NULL; mirror PostgreWorksheetDao's append-at-the-end computation
        // rather than hand-picking a value that would drift from it.
        jdbc.update("""
                INSERT INTO worksheets_sequence (worksheet_id, sequence_id, position)
                SELECT ?, ?, COALESCE(MAX(position) + 1, 0)
                FROM worksheets_sequence WHERE worksheet_id = ?
                """,
                worksheetId, sequenceId, worksheetId);
    }
}
