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

import de.uni_passau.fim.se2.domain.exceptions.*;
import de.uni_passau.fim.se2.domain.strategy.*;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.strategy.CodeAndConceptStrategy;
import de.uni_passau.fim.se2.domain.strategy.CodeOnlyStrategy;
import de.uni_passau.fim.se2.domain.strategy.ConceptOnlyStrategy;
import de.uni_passau.fim.se2.domain.strategy.TaskAndCodeStrategy;
import de.uni_passau.fim.se2.domain.strategy.TaskAndConceptStrategy;
import de.uni_passau.fim.se2.domain.strategy.TaskConceptAndCodeStrategy;
import de.uni_passau.fim.se2.domain.strategy.TaskGenerationStrategy;
import de.uni_passau.fim.se2.domain.strategy.TaskOnlyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Factory that selects the appropriate strategy based on provided input.
 */
public final class TaskGenerationStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(TaskGenerationStrategyFactory.class);

    /**
     * Private constructor for helper class.
     */
    private TaskGenerationStrategyFactory() {
    }

    /**
     * Chooses the Strategy based on the existence of the parameters.
     *
     * @param taskDescription String posing the problem statement.
     * @param codeSnippet     Solution to aforementioned problem statement.
     * @param concepts        List of concepts found in codeSnippet.
     * @param language        Programming language of the codeSnippet.
     * @return A TaskGenerationStrategy fitting for the input.
     */
    public static TaskGenerationStrategy choose(final String taskDescription, final String codeSnippet,
                                                final List<String> concepts, final String language,
                                                final String targetLanguage) {
        boolean hasCode = codeSnippet != null && !codeSnippet.isBlank();
        boolean hasTask = taskDescription != null && !taskDescription.isBlank();
        boolean hasConcepts = concepts != null && !concepts.isEmpty();
        boolean hasLanguage = language != null && !language.isBlank();

        validateInput(hasLanguage, hasCode);
        log.info("Selecting strategy with: hasTask={}, hasCode={}, hasConcepts={}, hasLanguage={}, targetLanguage={}",
                 hasTask, hasCode, hasConcepts, hasLanguage, targetLanguage);
        return chooseStrategy(hasTask, hasConcepts, hasLanguage, hasCode, taskDescription, concepts, language,
                              codeSnippet, targetLanguage);

    }

    private static void validateInput(final boolean hasLanguage, final boolean hasCode) {
        if (!hasLanguage && !hasCode) {
            String e = "Invalid input: language or code snippet are required.";
            log.error(e);
            throw new DomainValidationException(e);
        }
    }

    private static void logDebug(final boolean hasSample, final boolean hasConcepts, final boolean hasLanguage) {
        log.debug("hasSample={}, hasConcepts={}, hasLanguage={}", hasSample, hasConcepts, hasLanguage);
    }

    private static TaskGenerationStrategy chooseStrategy(final boolean hasTask, final boolean hasConcepts,
                                                         final boolean hasLanguage, final boolean hasCode,
                                                         final String taskDescription, final List<String> concepts,
                                                         final String language, final String codeSnippet,
                                                         final String targetLanguage) {
        boolean illegalArguments = (hasLanguage && !hasTask && !hasConcepts && !hasCode);
        boolean conceptOnly = (!hasTask && hasConcepts && hasLanguage && !hasCode);
        boolean taskOnly = (hasTask && !hasConcepts && hasLanguage && !hasCode);
        boolean taskAndConcepts = (hasTask && hasConcepts && hasLanguage && !hasCode);
        boolean taskAndCode = (hasTask && !hasConcepts && hasCode);
        boolean taskConceptsAndCode = (hasTask && hasConcepts && hasCode);
        boolean codeOnly = (!hasTask && !hasConcepts && hasCode);
        @SuppressWarnings("unused") boolean codeAndConcepts = (!hasTask && hasConcepts && hasCode);

        if (illegalArguments) {
            String e = "Invalid input: language alone insufficient";
            log.error(e);
            throw new DomainValidationException(e);
        } else if (conceptOnly) {
            log.info("Selected ConceptOnlyStrategy");
            return new ConceptOnlyStrategy(concepts, language, targetLanguage);
        } else if (taskOnly) {
            log.info("Selected TaskOnlyStrategy");
            return new TaskOnlyStrategy(taskDescription, language, targetLanguage);
        } else if (taskAndConcepts) {
            log.info("Selected TaskAndConceptStrategy");
            return new TaskAndConceptStrategy(taskDescription, concepts, language, targetLanguage);
        } else if (taskAndCode) {
            log.info("Selected TaskAndCodeStrategy");
            return new TaskAndCodeStrategy(taskDescription, codeSnippet, language, targetLanguage);
        } else if (taskConceptsAndCode) {
            log.info("Selected TaskConceptAndCodeStrategy");
            return new TaskConceptAndCodeStrategy(taskDescription, concepts, codeSnippet, language, targetLanguage);
        } else if (codeOnly) {
            log.info("Selected CodeOnlyStrategy");
            return new CodeOnlyStrategy(codeSnippet, language, targetLanguage);
        } else {
            log.info("Selected CodeAndConceptStrategy");
            return new CodeAndConceptStrategy(codeSnippet, concepts, language, targetLanguage);
        }
    }
}