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
package de.uni_passau.fim.se2.application.service.hints;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class CodeAssembler {

    private static final String INDENT_UNIT = "    "; // 4 spaces per indent level

    /**
     * Assembles block order into complete code with proper indentation.
     * Each block gets prefixed with spaces based on its indent level.
     */
    public CodeAssembly assemble(List<String> blockOrder, List<Integer> indentLevels) {
        StringBuilder code = new StringBuilder();
        Map<Integer, Integer> lineToBlockIndex = new HashMap<>();
        int currentLine = 1;

        for (int blockIdx = 0; blockIdx < blockOrder.size(); blockIdx++) {
            String block = blockOrder.get(blockIdx);
            int indent = (indentLevels != null && blockIdx < indentLevels.size())
                    ? indentLevels.get(blockIdx) : 0;
            String prefix = INDENT_UNIT.repeat(Math.max(0, indent));

            String[] lines = block.split("\n");
            for (String line : lines) {
                code.append(prefix).append(line).append("\n");
                lineToBlockIndex.put(currentLine, blockIdx);
                currentLine++;
            }
        }

        return new CodeAssembly(code.toString(), lineToBlockIndex, blockOrder);
    }

    public record CodeAssembly(
        String code,
        Map<Integer, Integer> lineToBlockIndex, // line number -> block index
        List<String> blockOrder
    ) {}
}
