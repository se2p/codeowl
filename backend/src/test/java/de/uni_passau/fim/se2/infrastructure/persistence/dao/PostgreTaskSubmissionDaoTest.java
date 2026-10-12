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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskSubmissionRepository;
import de.uni_passau.fim.se2.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upsert's stickiness against a real Postgres, not a mock.
 *
 * <p>{@code TaskSubmissionService}'s own tests mock {@link TaskSubmissionRepository} and say
 * outright that they trust the DAO's upsert to keep a solved arrangement from being overwritten
 * by a later autosave -- they never verify it. This class is that verification: it drives {@link
 * PostgreTaskSubmissionDao} directly, below the service layer, against the same {@code ON
 * CONFLICT DO UPDATE} the service ultimately relies on.
 */
@DisplayName("task submission upsert against real Postgres")
class PostgreTaskSubmissionDaoTest {

    private static JdbcTemplate jdbc;
    private static PostgreTaskSubmissionDao dao;

    private static UUID versionId;
    private static UUID userId;
    private static UUID taskId;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(TestDatabase.isAvailable(),
                "no test database: set SPRING_DATASOURCE_URL or start Docker");
        jdbc = TestDatabase.jdbc();
        dao = new PostgreTaskSubmissionDao(jdbc);

        // Fixtures this class needs, not data the migrated schema happens to already carry --
        // the schema is what V1...latest guarantees, not the pg_dump's row contents.
        versionId = UUID.randomUUID();
        jdbc.update("INSERT INTO versions (id, version_name) VALUES (?, ?)",
                versionId, "database-test-version");

        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, email, first_name, last_name, password_hash,"
                        + " role, is_verified, is_active) VALUES (?,?,?,?,?,CAST(? AS user_role),?,?)",
                userId, "student@example.invalid", "S", "S", "unused", "STUDENT", true, true);

        UUID sequenceId = UUID.randomUUID();
        jdbc.update("INSERT INTO tiered_task_sequences (id, programming_language, task_description,"
                        + " version_id, owner_id) VALUES (?, ?, ?, ?, ?)",
                sequenceId, "python", "database-test-sequence", versionId, userId);

        taskId = UUID.randomUUID();
        jdbc.update("INSERT INTO tasks (id, statement, solution, tier_level, cyclomatic_complexity,"
                        + " sequence_id) VALUES (?, ?, ?, 1, 1, ?)",
                taskId, "Order the lines", "a = 1", sequenceId);
    }

    @AfterAll
    static void cleanup() {
        jdbc.update("DELETE FROM tasks WHERE id = ?", taskId);
        jdbc.update("DELETE FROM tiered_task_sequences WHERE version_id = ?", versionId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM versions WHERE id = ?", versionId);
    }

    @BeforeEach
    void resetSubmission() {
        jdbc.update("DELETE FROM task_submissions WHERE user_id = ? AND task_id = ?", userId, taskId);
    }

    @Test
    @DisplayName("an autosave over an already-solved row keeps the solved arrangement")
    void autosaveDoesNotOverwriteASolvedArrangement() {
        dao.save(submission(true, List.of("a = 1"), List.of(0)));
        LocalDateTime updatedAtAfterSolve = dao.find(userId, taskId).orElseThrow().getUpdatedAt();

        dao.save(submission(false, List.of("half", "finished"), List.of(0, 1)));

        TaskSubmissionDTO found = dao.find(userId, taskId).orElseThrow();
        assertAll(
                () -> assertTrue(found.isSolved(), "solving is sticky"),
                () -> assertEquals(List.of("a = 1"), found.getBlockOrder(),
                        "the graded-correct arrangement must survive an abandoned redo"),
                () -> assertEquals(List.of(0), found.getIndentLevels()),
                () -> assertEquals(updatedAtAfterSolve, found.getUpdatedAt(),
                        "a discarded autosave must not claim the row was updated"));
    }

    @Test
    @DisplayName("autosave over autosave still overwrites when the task was never solved")
    void autosaveOverwritesWhenNotYetSolved() {
        dao.save(submission(false, List.of("first"), List.of(0)));
        dao.save(submission(false, List.of("second", "attempt"), List.of(0, 1)));

        TaskSubmissionDTO found = dao.find(userId, taskId).orElseThrow();
        assertAll(
                () -> assertFalse(found.isSolved()),
                () -> assertEquals(List.of("second", "attempt"), found.getBlockOrder()),
                () -> assertEquals(List.of(0, 1), found.getIndentLevels()));
    }

    @Test
    @DisplayName("re-solving with a different arrangement replaces the stored solve")
    void resolvingReplacesTheSolvedArrangement() {
        dao.save(submission(true, List.of("a = 1"), List.of(0)));
        dao.save(submission(true, List.of("b = 2", "c = 3"), List.of(0, 1)));

        TaskSubmissionDTO found = dao.find(userId, taskId).orElseThrow();
        assertAll(
                () -> assertTrue(found.isSolved()),
                () -> assertEquals(List.of("b = 2", "c = 3"), found.getBlockOrder()),
                () -> assertEquals(List.of(0, 1), found.getIndentLevels()));
    }

    private TaskSubmissionDTO submission(boolean solved, List<String> blockOrder, List<Integer> indentLevels) {
        return TaskSubmissionDTO.builder()
                .userId(userId)
                .taskId(taskId)
                .solved(solved)
                .blockOrder(blockOrder)
                .indentLevels(indentLevels)
                .build();
    }
}
