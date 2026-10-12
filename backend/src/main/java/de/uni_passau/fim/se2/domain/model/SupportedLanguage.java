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
package de.uni_passau.fim.se2.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * The programming languages CodeOwl can actually work with, and the only values
 * {@code tiered_task_sequences.programming_language} is meant to hold.
 *
 * <p>"Can work with" means there is a static analyzer behind it: Ruff for Python, javac plus
 * SpotBugs for Java. A sequence tagged anything else still generates and still renders, but its
 * hints are built from an empty finding list -- which is why this type exists rather than the
 * string literals that used to be spread across the prompt schema, the client and the hint
 * service.
 *
 * <p>It exists because that spread let a typo through. The language is detected by an LLM, and
 * for one batch on 2026-09-10 the model answered "Pyhton". Nothing compared that against
 * anything: the response schema typed the field as an unconstrained string, the client checked
 * only that it was non-empty, and the column is free text. Six sequences and thirty tasks
 * silently lost static analysis. One list, referenced everywhere, is what stops the next one.
 */
public enum SupportedLanguage {

    /** Analyzed with Ruff. */
    PYTHON("python"),

    /** Analyzed with javac, then SpotBugs on the compiled classes. */
    JAVA("java");

    /**
     * What the language-detection model answers when a snippet is neither of the above.
     *
     * <p>Offered as an explicit choice on purpose. Restricted to {@code python} and {@code java}
     * alone, a model handed C++ has to pick one of them and the sequence is confidently
     * mislabelled; given somewhere to put "neither", it says so and generation fails with a
     * message naming the real problem.
     */
    public static final String UNSUPPORTED = "other";

    private final String canonicalName;

    SupportedLanguage(final String canonicalName) {
        this.canonicalName = canonicalName;
    }

    /** The one spelling this language is stored and compared as: lowercase, no variants. */
    public String canonicalName() {
        return canonicalName;
    }

    /** Whether {@code raw} names this language, ignoring case and surrounding whitespace. */
    public boolean matches(final String raw) {
        return raw != null && canonicalName.equalsIgnoreCase(raw.trim());
    }

    /**
     * Resolves a stored or model-supplied string, or empty if it names no supported language.
     *
     * <p>Deliberately strict: it accepts case and whitespace variation and nothing else. A near
     * miss like "Pyhton" returns empty rather than being guessed at, because a wrong guess is
     * how bad data reaches the database in the first place.
     *
     * @param raw the value to resolve, may be {@code null}
     * @return the matching language, or empty
     */
    public static Optional<SupportedLanguage> fromString(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        for (SupportedLanguage language : values()) {
            if (language.matches(raw)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /** The canonical names, for building the language-detection response schema. */
    public static List<String> canonicalNames() {
        return List.of(values()).stream().map(SupportedLanguage::canonicalName).toList();
    }

    /**
     * Every value the detection model may answer: the supported languages plus
     * {@link #UNSUPPORTED}.
     */
    public static List<String> detectionChoices() {
        return List.of(PYTHON.canonicalName, JAVA.canonicalName, UNSUPPORTED);
    }
}
