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
package de.uni_passau.fim.se2.api.dto;

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A single card in the Library grid. Flat & JSON-friendly — everything the UI needs to render
 * a {@code LibrarySequenceCard} without a secondary fetch.
 *
 * <p>{@code tags} is ordered with manual first, then LLM, then auto (the DAO layer handles that).
 * {@code highlight} is populated only when the search used a {@code q=…} param — a
 * PostgreSQL {@code ts_headline} snippet with control-character markers that the frontend
 * swaps for {@code <mark>} tags after escaping the payload.
 *
 * <p>{@code alreadyInMyWorksheet} (A15) is a per-row hint computed against the current user's
 * own worksheets so the card can render a "✓ in your library" badge without a second call.
 * Always {@code false} for anonymous requests.
 *
 * <p>{@code concepts} is plain names, not a richer DTO like {@code tags} — concepts have no
 * per-link source/confidence the way a tag does, they're just the sequence's linked entries in
 * the curated {@code concepts} catalogue.
 */
public record LibraryItemDTO(
        UUID sequenceId,
        String title,
        String programmingLanguage,
        String taskDescription,
        String semanticSummary,
        LocalDateTime createdAt,
        UUID ownerId,
        int tierCount,
        List<SequenceTagDTO> tags,
        List<String> concepts,
        long usageCount,
        String highlight,
        boolean alreadyInMyWorksheet
) {}
