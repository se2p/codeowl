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
package de.uni_passau.fim.se2.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads identity prompt resources for GPT from the classpath.
 * <p>
 * The base directory can be configured via the property
 * {@code prompts.identity.base}. If not set, it defaults to
 * {@code classpath:PromptStuff/Identity/}.
 * <p>
 * Note: The file name is resolved exactly as provided. If your file is
 * {@code CodeOnly.txt}, pass {@code "CodeOnly.txt"} as the {@code name}.
 */
@Component
public final class IdentityLoader {

    private static final Logger logger = Logger.getLogger(IdentityLoader.class.getName());

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Base location for identity files. Supports Spring resource prefixes
     * such as {@code classpath:} and {@code file:}.
     * Ensured to end with a trailing slash.
     */
    private final String base;

    /**
     * Creates the loader with a configurable base path.
     *
     * @param base the base resource location (e.g., {@code classpath:PromptStuff/Identity/})
     */
    public IdentityLoader(@Value("${prompts.identity.base:classpath:PromptStuff/Identity/}") String base) {
        this.base = base.endsWith("/") ? base : base + "/";
    }

    /**
     * Loads an identity file by name from the configured base directory.
     * <p>
     * Example: if your file is {@code classpath:PromptStuff/Identity/CodeOnly.txt},
     * call {@code load("CodeOnly.txt")}.
     *
     * @param name the file name (without a path), e.g. {@code "CodeOnly.txt"}
     * @return the file contents as UTF-8 string
     * @throws UncheckedIOException if the resource cannot be found or read
     */
    public String load(final String name) {
        final String resourcePath = base + name;
        try {
            Resource r = resolver.getResource(resourcePath);
            try (InputStream in = r.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Identity resource not found or unreadable: " + resourcePath, e);
            throw new UncheckedIOException("Identity resource not found or unreadable: " + resourcePath, e);
        }
    }
}