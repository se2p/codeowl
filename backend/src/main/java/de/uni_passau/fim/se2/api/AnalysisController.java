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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.application.service.analysis.ChangeAnalysisService;
import de.uni_passau.fim.se2.domain.entities.TaskChangeAnalysisDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final ChangeAnalysisService changeAnalysisService;

    public AnalysisController(ChangeAnalysisService changeAnalysisService) {
        this.changeAnalysisService = changeAnalysisService;
    }

    /**
     * Trigger analysis for a single task (on-demand).
     * Requires authentication — only owner of the task's sequence or admin.
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<TaskChangeAnalysisDTO> analyzeTask(@PathVariable UUID taskId) {
        TaskChangeAnalysisDTO result = changeAnalysisService.analyzeTask(taskId);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch analysis: analyze all tasks that have originals but no analysis yet.
     * Admin only.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<TaskChangeAnalysisDTO>> batchAnalyze() {
        List<TaskChangeAnalysisDTO> results = changeAnalysisService.batchAnalyze();
        return ResponseEntity.ok(results);
    }

    /**
     * Get analysis result for a specific task.
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskChangeAnalysisDTO> getAnalysis(@PathVariable UUID taskId) {
        return changeAnalysisService.getByTaskId(taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Export all analyses as JSON (for Python/R/SPSS).
     * Admin only.
     */
    @GetMapping("/export")
    public ResponseEntity<List<TaskChangeAnalysisDTO>> exportAll() {
        return ResponseEntity.ok(changeAnalysisService.findAll());
    }

    /**
     * Aggregated summary statistics.
     * Admin only.
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(changeAnalysisService.getSummary());
    }
}
