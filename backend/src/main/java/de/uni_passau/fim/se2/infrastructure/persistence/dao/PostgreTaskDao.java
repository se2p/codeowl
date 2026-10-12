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

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.*;
import de.uni_passau.fim.se2.domain.entities.SnippetDTO;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.TaskRowMapper;
import lombok.extern.slf4j.*;
import org.postgresql.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.util.*;

@Slf4j
@Repository
public class PostgreTaskDao implements TaskRepository {

    private final JdbcTemplate jdbc;

    public PostgreTaskDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Loads the details of the specified task into the given TaskDTO instance.
     * Typically retrieves task information from the repository using its unique identifier
     * and populates the target TaskDTO object with the retrieved data.
     *
     * @param target the TaskDTO instance to be populated with the details of the task
     *               identified by its unique identifier
     */
    @Override
    public void loadById(TaskDTO target) {
        validateTask(target);

        // JOIN on tiered_task_sequences to get programming_language, which lives at
        // the sequence level (not the task level) — without this join,
        // task.getProgrammingLanguage() would always be null and the hint system
        // would silently fall back to "python" even for Java tasks.
        String sql = """
                         SELECT t.id, t.statement, t.solution, t.tier_level, t.title, t.cyclomatic_complexity,
                                t.sequence_id, t.lines_of_code, t.distinct_identifier_count,
                                t.maximal_nesting_depth, t.method_call_count, t.distractors,
                                t.distractor_plan, t.distractors_enabled,
                                t.context_before, t.context_after, t.split_plan,
                                s.programming_language
                         FROM tasks t
                         JOIN tiered_task_sequences s ON s.id = t.sequence_id
                         WHERE t.id = ?
                     """;
        TaskDTO result = jdbc.queryForObject(sql, (rs, n) -> {
            TaskDTO dto = new TaskRowMapper().mapRow(rs, n);
            dto.setProgrammingLanguage(rs.getString("programming_language"));
            return dto;
        }, target.getId());
        if (result != null) {
            target.setStatement(result.getStatement());
            target.setSolution(result.getSolution());
            target.setTierLevel(result.getTierLevel());
            target.setTitle(result.getTitle());
            target.setCyclomaticComplexity(result.getCyclomaticComplexity());
            target.setSequenceId(result.getSequenceId());
            target.setLinesOfCode(result.getLinesOfCode());
            target.setDistinctIdentifierCount(result.getDistinctIdentifierCount());
            target.setMaximalNestingDepth(result.getMaximalNestingDepth());
            target.setMethodCallCount(result.getMethodCallCount());
            target.setDistractors(result.getDistractors());
            target.setDistractorPlan(result.getDistractorPlan());
            target.setDistractorsEnabled(result.isDistractorsEnabled());
            target.setContextBefore(result.getContextBefore());
            target.setContextAfter(result.getContextAfter());
            target.setSplitPlan(result.getSplitPlan()); // AP31
            target.setProgrammingLanguage(result.getProgrammingLanguage());
        }
    }


    /**
     * Loads a list of tasks associated with the given tiered task sequence.
     *
     * @param sequence the TieredTaskSequenceDTO representing the task sequence
     *                 whose associated tasks are to be retrieved
     * @return a list of TaskDTO objects corresponding to the tasks in the specified
     * tiered task sequence, or an empty list if no tasks are found
     */
    @Override
    public List<TaskDTO> loadTasksBySequenceId(TieredTaskSequenceDTO sequence) {
        if (sequence == null || sequence.getId() == null) {
            throw new DomainValidationException("Sequence and ID cannot be null");
        }

        String sql = """
                         SELECT id, statement, solution, tier_level, title, cyclomatic_complexity,
                                sequence_id, lines_of_code, distinct_identifier_count,
                                maximal_nesting_depth, method_call_count, distractors,
                                distractor_plan, distractors_enabled,
                                context_before, context_after, split_plan
                         FROM tasks
                         WHERE sequence_id = ?
                         ORDER BY tier_level, id
                     """;

        return jdbc.query(sql, new TaskRowMapper(), sequence.getId());
    }

    /**
     * Persists the given task data transfer object (TaskDTO) into the repository.
     * This method ensures that the task details, such as statement, solution, and tier level,
     * are stored for future retrieval or updates.
     *
     * @param task the TaskDTO object representing the task to be saved.
     *             It contains details like problem statement, solution, tier level,
     *             cyclomatic complexity, and a unique identifier.
     */
    @Override
    public void save(TaskDTO task) {
        validateTaskCreate(task);

        String sql = """
                         INSERT INTO tasks (
                             id, statement, solution, tier_level, cyclomatic_complexity,
                             sequence_id, lines_of_code, distinct_identifier_count,
                             maximal_nesting_depth, method_call_count, distractors, distractor_plan,
                             context_before, context_after, split_plan, distractors_enabled, title
                         )
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?)
                     """;

        UUID id = UUID.randomUUID();

        jdbc.update(sql,
                    id,
                    task.getStatement(),
                    task.getSolution(),
                    task.getTierLevel(),
                    task.getCyclomaticComplexity(),
                    task.getSequenceId(),
                    task.getLinesOfCode(),
                    task.getDistinctIdentifierCount(),
                    task.getMaximalNestingDepth(),
                    task.getMethodCallCount(),
                    task.getDistractors(),
                    task.getDistractorPlan(),
                    task.getContextBefore(),
                    task.getContextAfter(),
                    task.getSplitPlan(), // AP31
                    // Without this the column falls back to its DEFAULT true, so a task with
                    // distractors switched off came back on again the moment it was saved.
                    task.isDistractorsEnabled(),
                    task.getTitle()
        );

        task.setId(id);
    }

    /**
     * Persists a list of TaskDTO objects to the repository.
     * This method is used to save multiple tasks at once, ensuring bulk persistence
     * and reducing the overhead of saving each task individually.
     *
     * @param sequence SequenceDTO wrapping the list of TaskDTO objects to be saved into the repository
     */
    @Override
    public void saveAll(TieredTaskSequenceDTO sequence) {
        List<TaskDTO> tasks = sequence.getTasks();

        String sql = """
                         INSERT INTO tasks (
                             id, statement, solution, tier_level, cyclomatic_complexity,
                             sequence_id, lines_of_code, distinct_identifier_count,
                             maximal_nesting_depth, method_call_count, distractors, distractor_plan,
                             context_before, context_after, split_plan, distractors_enabled, title
                         )
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """;

        jdbc.batchUpdate(sql, tasks, tasks.size(), (ps, task) -> {
            validateTaskCreate(task);
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setString(2, task.getStatement());
            ps.setString(3, task.getSolution());
            ps.setInt(4, task.getTierLevel());
            ps.setInt(5, task.getCyclomaticComplexity());
            ps.setObject(6, sequence.getId());
            ps.setObject(7, task.getLinesOfCode());
            ps.setObject(8, task.getDistinctIdentifierCount());
            ps.setObject(9, task.getMaximalNestingDepth());
            ps.setObject(10, task.getMethodCallCount());
            ps.setString(11, task.getDistractors());
            try {
                ps.setObject(12, createJsonbObject(task.getDistractorPlan()));
            } catch (Exception e) {
                log.error("Failed to set distractor_plan as jsonb", e);
                ps.setNull(12, java.sql.Types.OTHER);
            }
            ps.setString(13, task.getContextBefore());
            ps.setString(14, task.getContextAfter());
            try {
                ps.setObject(15, createJsonbObject(task.getSplitPlan())); // AP31
            } catch (Exception e) {
                log.error("Failed to set split_plan as jsonb", e);
                ps.setNull(15, java.sql.Types.OTHER);
            }
            // See save(TaskDTO): the column's DEFAULT true would otherwise re-enable
            // distractors on any task that had them switched off.
            ps.setBoolean(16, task.isDistractorsEnabled());
            ps.setString(17, task.getTitle());
            task.setId(id);
        });
    }

    private PGobject createJsonbObject (String jsonValue) throws Exception {
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        jsonObject.setValue(jsonValue);
        return jsonObject;
    }

    /**
     * Deletes the specified task from the repository.
     * This method removes the task identified by the provided TaskDTO object.
     *
     * @param target the TaskDTO object representing the task to be deleted,
     *               which includes its unique identifier
     */
    @Override
    public void deleteById(TaskDTO target) {
        validateTask(target);
        String sql = "DELETE FROM tasks WHERE id = ?";
        jdbc.update(sql, target.getId());
    }

    /**
     * Deletes the specified task from the repository.
     * This method removes the task identified by the provided TaskDTO object.
     *
     * @param target the TaskDTO object representing the task to be deleted,
     *               which includes its unique identifier
     */
    @Override
    public void deleteBySequenceId(TaskDTO target) {
        if (target == null || target.getSequenceId() == null) {
            //TODO Logging
            throw new DomainValidationException("Task and ID cannot be null");
        }
        String sql = "DELETE FROM tasks WHERE sequence_id = ?";
        jdbc.update(sql, target.getSequenceId());
    }

    @Override
    public List<SnippetDTO> findAllWithConcepts() {
        String sql = """
        SELECT
            t.id,
            ts.id::text                AS sequence_name,
            NULL::text                 AS language,
            t.solution                 AS code,
            array_remove(array_agg(DISTINCT c.name ORDER BY c.name), NULL) AS concepts
        FROM tasks t
        JOIN tiered_task_sequences ts ON t.sequence_id = ts.id
        LEFT JOIN tiered_task_sequence_concepts ttsc ON ttsc.sequence_id = ts.id
        LEFT JOIN concepts c ON c.id = ttsc.concept_id
        GROUP BY t.id, ts.id, t.solution
        ORDER BY ts.id, t.tier_level
        """;

        return jdbc.query(sql, (rs, rowNum) -> new SnippetDTO(
                rs.getString("sequence_name"),
                rs.getString("language"),          // will be null unless you replace the column
                rs.getString("code"),
                Arrays.asList((String[]) rs.getArray("concepts").getArray())
        ));
    }


    @Override
    public void updateStatementAndSolution(UUID taskId, String statement, String solution) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
        String sql = "UPDATE tasks SET statement = ?, solution = ? WHERE id = ?";
        int updated = jdbc.update(sql, statement, solution, taskId);
        if (updated != 1) {
            throw new DomainValidationException("Task not found: " + taskId);
        }
    }

    @Override
    public void updateDistractors(UUID taskId, String distractors) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
        String sql = "UPDATE tasks SET distractors = ? WHERE id = ?";
        int updated = jdbc.update(sql, distractors, taskId);
        if (updated != 1) {
            throw new DomainValidationException("Task not found: " + taskId);
        }
    }

    @Override
    public void updateDistractorsEnabled(UUID taskId, boolean enabled) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
        String sql = "UPDATE tasks SET distractors_enabled = ? WHERE id = ?";
        int updated = jdbc.update(sql, enabled, taskId);
        if (updated != 1) {
            throw new DomainValidationException("Task not found: " + taskId);
        }
    }

    @Override
    public void updateTitle(UUID taskId, String title) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
        String sql = "UPDATE tasks SET title = ? WHERE id = ?";
        int updated = jdbc.update(sql, title, taskId);
        if (updated != 1) {
            throw new DomainValidationException("Task not found: " + taskId);
        }
    }

    @Override
    public void updateSplitPlan(UUID taskId, String splitPlanJson) {
        if (taskId == null) {
            throw new DomainValidationException("Task ID cannot be null");
        }
        // split_plan is JSONB — cast the text (or SQL NULL) so a null clears the plan.
        String sql = "UPDATE tasks SET split_plan = ?::jsonb WHERE id = ?";
        int updated = jdbc.update(sql, splitPlanJson, taskId);
        if (updated != 1) {
            throw new DomainValidationException("Task not found: " + taskId);
        }
    }

    private void validateTask(TaskDTO task) {
        if (task == null || task.getId() == null) {
            //TODO Logging
            throw new DomainValidationException("Task and ID cannot be null");
        }
    }

    private void validateTaskCreate(TaskDTO task) {
        if (task == null) {
            log.warn("Task validation failed: task=null");
            throw new DomainValidationException("Task cannot be null");
        }

        List<String> problems = new ArrayList<>();

        if (task.getStatement() == null) {
            problems.add("statement=null");
        } else if (task.getStatement().isBlank()) {
            problems.add("statement=blank");
        }

        if (task.getSolution() == null) {
            problems.add("solution=null");
        } else if (task.getSolution().isBlank()) {
            problems.add("solution=blank");
        }

        if (task.getTierLevel() == 0) {
            problems.add("tierLevel=0 (invalid)");
        }

        if (!problems.isEmpty()) {
            String msg = "Task validation failed: " + String.join(", ", problems);
            log.warn("{} — taskId={}", msg, task.getId());
            throw new DomainValidationException(msg);
        }
    }
}