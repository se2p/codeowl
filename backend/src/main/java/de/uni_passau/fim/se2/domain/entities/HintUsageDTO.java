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
package de.uni_passau.fim.se2.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @ToString @EqualsAndHashCode(of = "id") @Builder @NoArgsConstructor @AllArgsConstructor
public class HintUsageDTO {
    private UUID id;
    private UUID userId;
    private UUID taskId;
    private int hintLevel;
    private String blockOrderAtRequest; // JSON string
    private String findings;            // JSON string
    private String hintText;
    private String llmExplanation;
    private String affectedBlockIds;    // JSON string
    private LocalDateTime createdAt;
}
