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
package de.uni_passau.fim.se2.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogDTO {
    private Long id;
    private UUID adminId;
    private String action;        // "FULL_BACKUP" | "RESEARCH_EXPORT"
    private String ipAddress;
    private String userAgent;
    private Long bytesSent;       // nullable
    private Long rowsSent;        // nullable
    private boolean success;
    private String errorMsg;      // nullable
    private LocalDateTime createdAt;

    /** Action constants — use these instead of magic strings. */
    public static final class Action {
        public static final String FULL_BACKUP     = "FULL_BACKUP";
        public static final String RESEARCH_EXPORT = "RESEARCH_EXPORT";
        private Action() {}
    }
}
