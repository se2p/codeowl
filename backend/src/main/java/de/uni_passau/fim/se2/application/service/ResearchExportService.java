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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.application.util.CsvWriter;
import de.uni_passau.fim.se2.domain.interfaces.ResearchDataReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a ZIP file containing CSVs of all research-relevant tables.
 * Streams directly to the provided OutputStream (HTTP response).
 *
 * The result is a fully PII-stripped, UUID-pseudonymized data dump.
 * TODO GDPR: define formal data retention/deletion policy before publication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchExportService {

    private final ResearchDataReader reader;
    private final ObjectMapper jsonMapper;

    /**
     * Streams a ZIP of CSV exports + a metadata file to the given output.
     *
     * @return total number of rows written across all tables
     */
    public long streamExport(OutputStream output, String adminIdShort) throws Exception {
        long totalRows = 0;
        Map<String, Long> rowCounts = new LinkedHashMap<>();

        try (ZipOutputStream zip = new ZipOutputStream(output)) {

            totalRows += writeTable(zip, "users_pseudonymized.csv", reader.readUsersPseudonymized(), rowCounts);
            totalRows += writeTable(zip, "learning_events.csv", reader.readLearningEvents(), rowCounts);
            totalRows += writeTable(zip, "feedback_logs.csv", reader.readFeedbackLogs(), rowCounts);
            totalRows += writeTable(zip, "hint_usage.csv", reader.readHintUsage(), rowCounts);
            totalRows += writeTable(zip, "task_change_analyses.csv", reader.readTaskChangeAnalyses(), rowCounts);
            totalRows += writeTable(zip, "problem_statement_ratings.csv", reader.readProblemStatementRatings(), rowCounts);
            totalRows += writeTable(zip, "tasks.csv", reader.readTasks(), rowCounts);
            totalRows += writeTable(zip, "tiered_task_sequences.csv", reader.readTieredTaskSequences(), rowCounts);
            totalRows += writeTable(zip, "worksheets_sequence.csv", reader.readWorksheetsSequence(), rowCounts);
            totalRows += writeTable(zip, "concepts.csv", reader.readConcepts(), rowCounts);
            totalRows += writeTable(zip, "tiered_task_sequence_concepts.csv", reader.readSequenceConceptJunction(), rowCounts);
            totalRows += writeTable(zip, "worksheets.csv", reader.readWorksheets(), rowCounts);
            totalRows += writeTable(zip, "classes.csv", reader.readClasses(), rowCounts);
            totalRows += writeTable(zip, "class_students.csv", reader.readClassStudents(), rowCounts);
            totalRows += writeTable(zip, "class_worksheets.csv", reader.readClassWorksheets(), rowCounts);
            totalRows += writeTable(zip, "sequence_tags.csv", reader.readSequenceTags(), rowCounts);
            totalRows += writeTable(zip, "curriculum_topics.csv", reader.readCurriculumTopics(), rowCounts);
            totalRows += writeTable(zip, "curriculum_code_examples.csv", reader.readCurriculumCodeExamples(), rowCounts);
            totalRows += writeTable(zip, "concept_curriculum_topic.csv", reader.readConceptCurriculumTopicJunction(), rowCounts);

            // _metadata.json — written last so rowCounts is complete
            zip.putNextEntry(new ZipEntry("_metadata.json"));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            meta.put("adminIdShort", adminIdShort);
            meta.put("totalRows", totalRows);
            meta.put("perTableRowCounts", rowCounts);
            meta.put("schemaVersion", 1);
            meta.put("piiPolicy", "users table: only user_id/role/is_verified/created_at exported. UUIDs as stable pseudonyms.");
            zip.write(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
            zip.closeEntry();
        }

        log.info("Research export complete: {} total rows, admin={}", totalRows, adminIdShort);
        return totalRows;
    }

    private long writeTable(ZipOutputStream zip, String filename, List<Map<String, Object>> rows,
                            Map<String, Long> rowCounts) throws Exception {
        zip.putNextEntry(new ZipEntry(filename));
        try {
            CsvWriter csv = new CsvWriter(zip);
            if (rows.isEmpty()) {
                csv.flush();
                rowCounts.put(filename, 0L);
                return 0L;
            }
            // Header row — key order is preserved via LinkedHashMap from JdbcTemplate
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            csv.writeRow(columns);
            for (Map<String, Object> row : rows) {
                List<Object> values = new ArrayList<>(columns.size());
                for (String col : columns) values.add(stringify(row.get(col)));
                csv.writeRow(values);
            }
            csv.flush();
            rowCounts.put(filename, (long) rows.size());
            return rows.size();
        } finally {
            zip.closeEntry();
        }
    }

    /**
     * Convert JDBC values to CSV-friendly strings.
     * JSONB (PGobject) → raw JSON string via toString().
     * byte[] → Base64.
     * Timestamps → ISO string.
     */
    private String stringify(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        // PGobject (jsonb) and other DB-specific types expose their value via toString()
        return value.toString();
    }
}
