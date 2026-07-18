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
package de.uni_passau.fim.se2.infrastructure.ai.claude;

import de.uni_passau.fim.se2.application.service.*;
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.infrastructure.ai.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.util.*;

@Component
public class ClaudeClient extends AbstractAiClient {

    protected ClaudeClient(CodeSanitizerService sanitizer) {
        super(sanitizer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode solveProblem(final SolveContract contract) throws IOException, InterruptedException {
        // TODO: echten Aufruf an Claude API (fixes Modell)
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    @Override
    public JsonNode solveProblemSimple(final String taskDescription, final String language) {
        // TODO: echten Aufruf an Claude API (fixes Modell)
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet, String language)
            throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProgrammingLanguage(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(List<String> concepts, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateTaskDescription(String codeSnippet) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> generateConcepts(String codeSnippet, String language) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("ClaudeClient not implemented yet");
    }
}