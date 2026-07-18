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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates readable temporary passwords of the form "Adjective-Noun-Number"
 * in the teacher's locale (de or en).
 * <p>
 * Word lists live in {@code src/main/resources/temp-passwords/}.
 * Format example: "Froh-Hund-42" (de) / "Happy-Dog-42" (en)
 */
@Service
@Slf4j
public class TempPasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    // locale → word lists
    private final Map<String, List<String>> adjectives = new java.util.HashMap<>();
    private final Map<String, List<String>> nouns      = new java.util.HashMap<>();

    private static final List<String> SUPPORTED_LOCALES = List.of("de", "en");
    private static final String FALLBACK_LOCALE = "en";

    @PostConstruct
    public void init() {
        for (String locale : SUPPORTED_LOCALES) {
            adjectives.put(locale, loadWords("temp-passwords/adjectives-" + locale + ".txt"));
            nouns.put(locale, loadWords("temp-passwords/nouns-" + locale + ".txt"));
            log.info("TempPasswordGenerator: loaded {} adjectives + {} nouns for locale={}",
                    adjectives.get(locale).size(), nouns.get(locale).size(), locale);
        }
    }

    /**
     * Generates a temporary password for the given locale.
     * Falls back to English for unknown locales.
     *
     * @param locale BCP-47 language tag prefix, e.g. "de" or "en"
     * @return readable password, e.g. "Froh-Hund-42"
     */
    public String generate(String locale) {
        String key = resolveLocale(locale);
        List<String> adjs  = adjectives.get(key);
        List<String> nounList = nouns.get(key);

        String adj  = adjs.get(RANDOM.nextInt(adjs.size()));
        String noun = nounList.get(RANDOM.nextInt(nounList.size()));
        int    num  = 10 + RANDOM.nextInt(90); // 10–99

        return adj + "-" + noun + "-" + num;
    }

    private String resolveLocale(String locale) {
        if (locale == null) return FALLBACK_LOCALE;
        // Accept "de", "de-DE", "de_DE" etc.
        String prefix = locale.split("[-_]")[0].toLowerCase();
        return SUPPORTED_LOCALES.contains(prefix) ? prefix : FALLBACK_LOCALE;
    }

    private List<String> loadWords(String resourcePath) {
        List<String> words = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty()) words.add(word);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load word list from {}: {}", resourcePath, e.getMessage());
        }
        return words;
    }
}
