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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.domain.entities.ConceptDTO;
import de.uni_passau.fim.se2.domain.interfaces.ConceptRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Singleton component that loads the concept catalog and alias mapping at startup, then extends
 * it live with concepts introduced after the fact — a teacher's free-typed concept in {@code
 * ConceptPicker}, or a curriculum topic mapped to a name nobody had used before.
 *
 * <p>Concept detection ({@link #getCatalogForPrompt}/{@link #normalize}, both called from {@code
 * OpenAiClient.generateConcepts}) used to be closed against only the {@code concept-catalog.json}
 * file: a concept a teacher typed by hand got saved to the {@code concepts} table just fine, but
 * the LLM could never be offered it or have it survive the post-response validation, because
 * nothing here ever looked at that table. A concept invented once stayed invisible to every
 * generation after it, including the teacher's own next one.
 *
 * <p>{@link #loadConceptsBeyondCatalog()} closes that gap: every row in {@code concepts} that
 * is not one of the file's slugs is folded in wherever a slug would be looked up, queried fresh
 * on each call rather than cached at startup — a concept typed five minutes ago has to be
 * classifiable immediately, and detection is already a multi-second LLM round-trip, so one more
 * small query is not the bottleneck. Such a concept carries no language scope (the {@code
 * concepts} table records none), so it is offered for every language rather than silently
 * excluded from all of them.
 */
@Slf4j
@Component
public class ConceptCatalog {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConceptRepository conceptRepo;

    /** slug → CatalogEntry */
    private Map<String, CatalogEntry> bySlug = Map.of();

    /** lowercase alias → slug */
    private Map<String, String> aliasToSlug = Map.of();

    /** All valid slugs from the static file (unmodifiable) — see {@link #getAllValidSlugs()} for
     *  the live set that also includes concepts added after startup. */
    @Getter
    private Set<String> validSlugs = Set.of();

    public ConceptCatalog(ConceptRepository conceptRepo) {
        this.conceptRepo = conceptRepo;
    }

    @PostConstruct
    void init() {
        try {
            loadCatalog();
            loadAliasMapping();
            log.info("ConceptCatalog loaded: {} concepts, {} aliases", bySlug.size(), aliasToSlug.size());
        } catch (IOException e) {
            log.error("Failed to load concept catalog — concept detection will be degraded", e);
        }
    }

    private void loadCatalog() throws IOException {
        try (InputStream is = new ClassPathResource("concepts/concept-catalog.json").getInputStream()) {
            List<CatalogEntry> entries = mapper.readValue(is, new TypeReference<>() {});
            Map<String, CatalogEntry> map = new LinkedHashMap<>();
            for (CatalogEntry entry : entries) {
                map.put(entry.slug, entry);
            }
            this.bySlug = Collections.unmodifiableMap(map);
            this.validSlugs = Collections.unmodifiableSet(map.keySet());
        }
    }

    private void loadAliasMapping() throws IOException {
        try (InputStream is = new ClassPathResource("concepts/alias-mapping.json").getInputStream()) {
            Map<String, String> raw = mapper.readValue(is, new TypeReference<>() {});
            Map<String, String> normalized = new HashMap<>();
            for (var entry : raw.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT).trim();
                if (key.startsWith("_")) continue; // skip _comment etc.
                normalized.put(key, entry.getValue());
            }
            // Also add all aliases from the catalog entries themselves
            for (CatalogEntry ce : bySlug.values()) {
                if (ce.aliases != null) {
                    for (String alias : ce.aliases) {
                        normalized.putIfAbsent(alias.toLowerCase(Locale.ROOT).trim(), ce.slug);
                    }
                }
            }
            this.aliasToSlug = Collections.unmodifiableMap(normalized);
        }
    }

    // ── Public API ────────────────────────────────────────

    /** Returns true if the given string is a valid catalog slug — from the file or added since. */
    public boolean isValidSlug(String slug) {
        if (slug == null) return false;
        String key = slug.toLowerCase(Locale.ROOT).trim();
        return bySlug.containsKey(key) || loadConceptsBeyondCatalog().containsKey(key);
    }

    /**
     * Tries to resolve a free-text string to a catalog slug: a direct slug match, a known alias,
     * or a concept name added to the {@code concepts} table after the file was written.
     */
    public Optional<String> resolveAlias(String input) {
        if (input == null) return Optional.empty();
        String key = input.toLowerCase(Locale.ROOT).trim();
        // Direct slug match first
        if (bySlug.containsKey(key)) return Optional.of(key);
        // Alias lookup
        String alias = aliasToSlug.get(key);
        if (alias != null) return Optional.of(alias);
        // A concept nobody had used when the file was written, but that exists now.
        if (loadConceptsBeyondCatalog().containsKey(key)) return Optional.of(key);
        return Optional.empty();
    }

    /**
     * Normalizes a raw concept string: returns the canonical slug if it's
     * a valid slug or a known alias, empty otherwise.
     */
    public Optional<String> normalize(String raw) {
        return resolveAlias(raw);
    }

    /**
     * Returns the catalog entries filtered for a given programming language, formatted as a
     * prompt-friendly string, plus every concept added to the {@code concepts} table beyond the
     * file — those carry no language of their own, so they are appended for every language
     * rather than excluded from all of them.
     * Format per line: "slug — displayNameEn: description".
     */
    public String getCatalogForPrompt(String language) {
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT).trim();
        String fromFile = bySlug.values().stream()
                .filter(e -> e.languages != null && e.languages.stream()
                        .anyMatch(l -> l.equalsIgnoreCase(lang)))
                .map(e -> "%s — %s: %s".formatted(e.slug, e.displayNameEn, e.description))
                .collect(Collectors.joining("\n"));

        String addedSince = loadConceptsBeyondCatalog().values().stream()
                .map(c -> "%s — %s".formatted(c.getName(),
                        c.getDescription() != null && !c.getDescription().isBlank()
                                ? c.getDescription() : c.getName()))
                .collect(Collectors.joining("\n"));

        if (addedSince.isBlank()) return fromFile;
        if (fromFile.isBlank()) return addedSince;
        return fromFile + "\n" + addedSince;
    }

    /**
     * Returns the set of valid slugs for a specific programming language, plus every concept
     * added beyond the file (which, carrying no language, counts for all of them).
     */
    public Set<String> getValidSlugsForLanguage(String language) {
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT).trim();
        Set<String> fromFile = bySlug.values().stream()
                .filter(e -> e.languages != null && e.languages.stream()
                        .anyMatch(l -> l.equalsIgnoreCase(lang)))
                .map(e -> e.slug)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        fromFile.addAll(loadConceptsBeyondCatalog().keySet());
        return Collections.unmodifiableSet(fromFile);
    }

    /** Every valid slug regardless of language: the file's, plus every concept added since. */
    public Set<String> getAllValidSlugs() {
        Set<String> all = new LinkedHashSet<>(validSlugs);
        all.addAll(loadConceptsBeyondCatalog().keySet());
        return Collections.unmodifiableSet(all);
    }

    public Optional<CatalogEntry> getEntry(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    /**
     * Rows in {@code concepts} that are not one of the static file's slugs, keyed by their
     * normalized name — a teacher's free-typed concept, or a curriculum topic mapped to a name
     * nobody had used before. Queried fresh rather than cached: see the class doc for why.
     */
    private Map<String, ConceptDTO> loadConceptsBeyondCatalog() {
        Map<String, ConceptDTO> extra = new LinkedHashMap<>();
        for (ConceptDTO concept : conceptRepo.loadAll()) {
            String key = concept.getName() == null ? null
                    : concept.getName().toLowerCase(Locale.ROOT).trim();
            if (key != null && !key.isEmpty() && !bySlug.containsKey(key)) {
                extra.put(key, concept);
            }
        }
        return extra;
    }

    // ── Inner record ──────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogEntry(
            String category,
            String slug,
            String displayNameDe,
            String displayNameEn,
            String description,
            List<String> aliases,
            List<String> languages,
            String level
    ) {}
}
