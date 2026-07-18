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
package de.uni_passau.fim.se2.application.service.hints;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SarifParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse SARIF JSON output from Ruff or SpotBugs into unified findings.
     */
    public List<AnalysisFinding> parse(String sarifJson, String toolName) {
        // Parse the SARIF JSON
        // SARIF structure: runs[0].results[] with each result having:
        //   - ruleId
        //   - message.text
        //   - level (error/warning/note)
        //   - locations[0].physicalLocation.region.startLine/endLine

        List<AnalysisFinding> findings = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(sarifJson);
            JsonNode runs = root.path("runs");
            if (!runs.isArray() || runs.isEmpty()) return findings;

            JsonNode results = runs.get(0).path("results");
            if (!results.isArray()) return findings;

            for (JsonNode result : results) {
                String ruleId = result.path("ruleId").asText("");
                String message = result.path("message").path("text").asText("");
                String level = result.path("level").asText("warning");
                String severity = mapLevel(level);

                int startLine = 1;
                int endLine = 1;
                JsonNode locations = result.path("locations");
                if (locations.isArray() && !locations.isEmpty()) {
                    JsonNode region = locations.get(0).path("physicalLocation").path("region");
                    startLine = region.path("startLine").asInt(1);
                    endLine = region.path("endLine").asInt(startLine);
                }

                String category = categorizeRule(ruleId, toolName);
                findings.add(new AnalysisFinding(ruleId, message, severity, startLine, endLine, toolName, category));
            }
        } catch (Exception e) {
            // Log but don't throw - partial results are fine
        }
        return findings;
    }

    private String mapLevel(String level) {
        return switch (level) {
            case "error" -> "error";
            case "warning" -> "warning";
            default -> "info";
        };
    }

    private String categorizeRule(String ruleId, String tool) {
        if ("ruff".equals(tool)) {
            return switch (ruleId) {
                case "F821", "F823" -> "undefined_variable";
                case "F841" -> "unused_variable";
                case "F811" -> "redefined_variable";
                case "E999", "invalid-syntax" -> "syntax";
                case "E111", "E112", "E113", "E117" -> "indentation";
                default -> "other";
            };
        } else if ("spotbugs".equals(tool)) {
            if (ruleId.startsWith("NP_")) return "null_safety";
            if (ruleId.startsWith("DLS_")) return "dead_code";
            if (ruleId.startsWith("UC_")) return "logic";
            return "other";
        }
        return "other";
    }
}
