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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JavacAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JavacAnalyzer.class);

    /**
     * Runs javac on the given Java code and returns compiler diagnostics as findings.
     * Also returns the path to compiled .class files if compilation succeeded.
     */
    public JavacResult analyze(String javaCode) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("codeowl-javac-");

            // Detect class name from code
            String className = extractClassName(javaCode);
            Path sourceFile = tempDir.resolve(className + ".java");
            Files.writeString(sourceFile, javaCode);

            Path outputDir = tempDir.resolve("out");
            Files.createDirectories(outputDir);

            // Run javac
            ProcessBuilder pb = new ProcessBuilder(
                "javac", "-d", outputDir.toString(), sourceFile.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("javac process timed out");
                return new JavacResult(List.of(), false, null);
            }

            int exitCode = process.exitValue();
            List<AnalysisFinding> findings = parseJavacOutput(output);
            boolean compiled = (exitCode == 0);

            return new JavacResult(findings, compiled, compiled ? outputDir : null);

        } catch (Exception e) {
            logger.error("javac analysis failed", e);
            return new JavacResult(List.of(), false, null);
        }
        // Note: don't clean up tempDir here if compiled - SpotBugs might need the .class files
    }

    /**
     * Parse javac error/warning output into AnalysisFindings.
     * Format: "FileName.java:LINE: error: message"
     */
    private List<AnalysisFinding> parseJavacOutput(String output) {
        List<AnalysisFinding> findings = new ArrayList<>();
        if (output == null || output.isBlank()) return findings;

        // Pattern: Solution.java:5: error: variable x might not have been initialized
        Pattern pattern = Pattern.compile(".*\\.java:(\\d+):\\s*(error|warning):\\s*(.+)");

        for (String line : output.split("\n")) {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches()) {
                int lineNum = Integer.parseInt(matcher.group(1));
                String severity = matcher.group(2);
                String message = matcher.group(3);

                String ruleId = categorizeJavacError(message);
                String category = categorizeJavacCategory(message);

                findings.add(new AnalysisFinding(
                    ruleId, message, severity, lineNum, lineNum, "javac", category
                ));
            }
        }
        return findings;
    }

    private String extractClassName(String code) {
        // Try to find "class ClassName"
        Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
        Matcher matcher = classPattern.matcher(code);
        if (matcher.find()) return matcher.group(1);
        return "Solution"; // fallback
    }

    private String categorizeJavacError(String message) {
        if (message.contains("might not have been initialized")) return "compiler:not-initialized";
        if (message.contains("cannot find symbol")) return "compiler:undefined-symbol";
        if (message.contains("incompatible types")) return "compiler:type-mismatch";
        if (message.contains("unreachable statement")) return "compiler:unreachable";
        if (message.contains("missing return")) return "compiler:missing-return";
        return "compiler:general";
    }

    private String categorizeJavacCategory(String message) {
        if (message.contains("might not have been initialized")) return "undefined_variable";
        if (message.contains("cannot find symbol")) return "undefined_variable";
        if (message.contains("incompatible types")) return "type_error";
        if (message.contains("unreachable statement")) return "dead_code";
        return "syntax";
    }

    public record JavacResult(
        List<AnalysisFinding> findings,
        boolean compiled,
        Path classOutputDir  // null if compilation failed
    ) {}

    public void cleanupTempDir(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
