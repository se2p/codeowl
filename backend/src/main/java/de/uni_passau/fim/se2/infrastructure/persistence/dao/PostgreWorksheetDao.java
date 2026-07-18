/*
 * Copyright (C) 2019-2026 CodeOwl contributors
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
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import lombok.extern.slf4j.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * PostgreSQL-based implementation of the {@link WorksheetRepository}.
 * Provides CRUD operations for worksheet persistence using {@link JdbcTemplate}.
 * <p>
 * Each worksheet acts as a container for one or more {@link TieredTaskSequenceDTO} entities.
 * This DAO handles the worksheet metadata itself and the linkage to sequences via
 * the worksheet_sequence join table, but does not eagerly load tasks.
 */
@Slf4j
@Repository
public class PostgreWorksheetDao implements WorksheetRepository {

    private final JdbcTemplate jdbc;

    public PostgreWorksheetDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void createWorksheet(WorksheetDTO worksheet) {
        validateCreate(worksheet);

        String sql = """
                INSERT INTO worksheets (id, name, description, owner_id, hints_enabled)
                VALUES (?, ?, ?, ?, ?)
                """;

        UUID id = UUID.randomUUID();

        jdbc.update(sql,
                    id,
                    worksheet.getName(),
                    worksheet.getDescription(),
                    worksheet.getOwnerId(),
                    worksheet.isHintsEnabled()
        );

        worksheet.setId(id);
        log.info("Created worksheet: id={}, name='{}'", id, worksheet.getName());
    }

    @Override
    public void updateWorksheet(WorksheetDTO worksheet) {
        validateId(worksheet);

        String sql = """
                UPDATE worksheets
                SET name = ?, description = ?, hints_enabled = ?
                WHERE id = ?
                """;

        int rows = jdbc.update(sql,
                               worksheet.getName(),
                               worksheet.getDescription(),
                               worksheet.isHintsEnabled(),
                               worksheet.getId()
        );

        if (rows == 0) {
            throw new DomainValidationException("No worksheet found for id=" + worksheet.getId());
        }

        log.info("Updated worksheet: id={}, name='{}'", worksheet.getId(), worksheet.getName());
    }

    @Override
    public void deleteWorksheet(WorksheetDTO worksheet) {
        validateId(worksheet);

        jdbc.update("DELETE FROM worksheets_sequence WHERE worksheet_id = ?", worksheet.getId());
        int rows = jdbc.update("DELETE FROM worksheets WHERE id = ?", worksheet.getId());

        if (rows == 0) {
            throw new DomainValidationException("No worksheet found for id=" + worksheet.getId());
        }

        log.info("Deleted worksheet: id={}, name='{}'", worksheet.getId(), worksheet.getName());
    }

    /**
     * Loads a worksheet including the identifiers of all associated tiered task sequences.
     * This method retrieves worksheet metadata and the sequence relationships from
     * the {@code worksheet_sequence} join table. It does not load task details.
     *
     * @param worksheet the {@link WorksheetDTO} instance to populate with data
     *                  corresponding to its unique identifier.
     */
    @Override
    public void getWorksheetById(WorksheetDTO worksheet) {
        validateId(worksheet);

        String sql = """
        SELECT
            w.id,
            w.name,
            w.description,
            w.owner_id,
            w.hints_enabled,
            COALESCE(array_agg(ws.sequence_id ORDER BY ws.sequence_id), '{}') AS sequence_ids
        FROM worksheets w
        LEFT JOIN worksheets_sequence ws ON ws.worksheet_id = w.id
        WHERE w.id = ?
        GROUP BY w.id, w.name, w.description, w.owner_id, w.hints_enabled
        """;

        try {
            WorksheetDTO result = jdbc.queryForObject(
                    sql,
                    (rs, rowNum) -> {
                        List<TieredTaskSequenceDTO> sequences = new ArrayList<>();
                        UUID[] seqArray = (UUID[]) rs.getArray("sequence_ids").getArray();
                        for (UUID seqId : seqArray) {
                            if (seqId != null) {
                                sequences.add(TieredTaskSequenceDTO.builder().id(seqId).build());
                            }
                        }

                        return WorksheetDTO.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .name(rs.getString("name"))
                                .description(rs.getString("description"))
                                .ownerId(rs.getObject("owner_id", UUID.class))
                                .hintsEnabled(rs.getBoolean("hints_enabled"))
                                .sequenceDTOS(sequences)
                                .build();
                    },
                    worksheet.getId()
            );

            if (result == null) {
                throw new DomainValidationException("Worksheet not found for id=" + worksheet.getId());
            }

            worksheet.setName(result.getName());
            worksheet.setDescription(result.getDescription());
            worksheet.setOwnerId(result.getOwnerId());
            worksheet.setHintsEnabled(result.isHintsEnabled());
            worksheet.setSequenceDTOS(result.getSequenceDTOS());

            log.debug("Loaded worksheet id={} with {} sequence references",
                      worksheet.getId(), result.getSequenceDTOS().size());

        } catch (EmptyResultDataAccessException e) {
            throw new DomainValidationException("Worksheet not found for id=" + worksheet.getId());
        }
    }

    /**
     * Associates a single {@link TieredTaskSequenceDTO} with the specified {@link WorksheetDTO}.
     * <p>
     * This operation creates a new entry in the {@code worksheets_sequence} join table,
     * effectively linking the given worksheet and sequence.
     * If the association already exists, implementations should ensure idempotency
     * (i.e., not duplicate the relationship).
     *
     * @param worksheet the worksheet to which the sequence should be added; must not be {@code null}
     * @param sequence  the sequence to associate with the given worksheet; must not be {@code null}
     * @throws DomainValidationException if either the worksheet or sequence does not exist,
     *                                   or if validation of identifiers fails
     */
    @Override
    public void addSequenceToWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence) {
        validateId(worksheet);

        if (sequence == null || sequence.getId() == null) {
            log.warn("Sequence validation failed: null sequence or id");
            throw new DomainValidationException("Sequence or its ID cannot be null");
        }

        // PostgreSQL idiom for idempotent insert
        String sql = """
            INSERT INTO worksheets_sequence (worksheet_id, sequence_id)
            VALUES (?, ?)
            ON CONFLICT (worksheet_id, sequence_id) DO NOTHING
            """;

        try {
            int rows = jdbc.update(sql, worksheet.getId(), sequence.getId());

            if (rows > 0) {
                log.info("Linked sequence id={} to worksheet id={}", sequence.getId(), worksheet.getId());
            } else {
                log.debug("Sequence id={} already linked to worksheet id={} (no-op)",
                          sequence.getId(), worksheet.getId());
            }

        } catch (DataAccessException e) {
            log.error("Error linking sequence id={} to worksheet id={}", sequence.getId(), worksheet.getId(), e);
            throw new DomainValidationException("Failed to add sequence to worksheet: " + e.getMessage());
        }
    }

    /**
     * Associates multiple {@link TieredTaskSequenceDTO} objects with the specified {@link WorksheetDTO}.
     * <p>
     * This method performs a batch operation to add multiple sequence references to the worksheet
     * in the {@code worksheets_sequence} join table.
     * Implementations should aim for transactional integrity — all associations are added
     * successfully or none at all.
     *
     * @param worksheet the worksheet to which the sequences should be added; must not be {@code null}
     * @param sequences a list of sequences to associate with the given worksheet; must not be {@code null} or empty
     * @throws DomainValidationException if the worksheet does not exist, any sequence is invalid,
     *                                   or a data integrity violation occurs
     */
    @Override
    public void addSequencesToWorksheet(WorksheetDTO worksheet, List<TieredTaskSequenceDTO> sequences) {
        validateId(worksheet);

        if (sequences == null || sequences.isEmpty()) {
            log.warn("Sequence list is null or empty for worksheet id={}", worksheet.getId());
            throw new DomainValidationException("Sequence list cannot be null or empty");
        }

        // Validate all IDs before attempting batch insert
        for (TieredTaskSequenceDTO seq : sequences) {
            if (seq == null || seq.getId() == null) {
                throw new DomainValidationException("All sequences must have a non-null ID");
            }
        }

        String sql = """
            INSERT INTO worksheets_sequence (worksheet_id, sequence_id)
            VALUES (?, ?)
            ON CONFLICT (worksheet_id, sequence_id) DO NOTHING
            """;

        try {
            jdbc.batchUpdate(sql, sequences, sequences.size(),
                             (ps, seq) -> {
                                 ps.setObject(1, worksheet.getId());
                                 ps.setObject(2, seq.getId());
                             });

            log.info("Linked {} sequences to worksheet id={}", sequences.size(), worksheet.getId());

        } catch (DataAccessException e) {
            log.error("Error batch-linking sequences to worksheet id={}", worksheet.getId(), e);
            throw new DomainValidationException("Failed to add sequences to worksheet: " + e.getMessage());
        }
    }

    @Override
    public void removeSequenceFromWorksheet(WorksheetDTO worksheet, TieredTaskSequenceDTO sequence) {
        validateId(worksheet);

        if (sequence == null || sequence.getId() == null) {
            log.warn("Sequence validation failed: null sequence or id");
            throw new DomainValidationException("Sequence or its ID cannot be null");
        }

        String sql = """
        DELETE FROM worksheets_sequence
        WHERE worksheet_id = ? AND sequence_id = ?
        """;

        try {
            int rows = jdbc.update(sql, worksheet.getId(), sequence.getId());

            if (rows > 0) {
                log.info("Unlinked sequence id={} from worksheet id={}", sequence.getId(), worksheet.getId());
            } else {
                log.debug("No link found for sequence id={} and worksheet id={} (no-op)",
                          sequence.getId(), worksheet.getId());
            }

        } catch (DataAccessException e) {
            log.error("Error unlinking sequence id={} from worksheet id={}", sequence.getId(), worksheet.getId(), e);
            throw new DomainValidationException("Failed to remove sequence from worksheet: " + e.getMessage());
        }
    }

    @Override
    public List<WorksheetDTO> findByOwnerId(UUID ownerId) {
        String sql = """
            SELECT w.id, w.name, w.description, w.owner_id,
                   w.hints_enabled,
                   COUNT(ws.sequence_id) AS sequence_count
            FROM worksheets w
            LEFT JOIN worksheets_sequence ws ON ws.worksheet_id = w.id
            WHERE w.owner_id = ?
            GROUP BY w.id, w.name, w.description, w.owner_id, w.hints_enabled
            ORDER BY w.name
            """;

        return jdbc.query(sql, (rs, rowNum) -> WorksheetDTO.builder()
                .id(UUID.fromString(rs.getString("id")))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .ownerId(rs.getObject("owner_id", UUID.class))
                .hintsEnabled(rs.getBoolean("hints_enabled"))
                .build(), ownerId);
    }

    private void validateId(WorksheetDTO worksheet) {
        if (worksheet == null || worksheet.getId() == null) {
            log.warn("Worksheet validation failed: id=null");
            throw new DomainValidationException("Worksheet or ID cannot be null");
        }
    }

    private void validateCreate(WorksheetDTO worksheet) {
        if (worksheet == null) {
            throw new DomainValidationException("Worksheet cannot be null");
        }

        List<String> problems = new ArrayList<>();

        if (worksheet.getName() == null || worksheet.getName().isBlank()) {
            problems.add("name missing");
        }

        if (worksheet.getDescription() == null || worksheet.getDescription().isBlank()) {
            problems.add("description missing");
        }

        if (!problems.isEmpty()) {
            String msg = "Worksheet validation failed: " + String.join(", ", problems);
            log.warn("{} — worksheet={}", msg, worksheet);
            throw new DomainValidationException(msg);
        }
    }
}