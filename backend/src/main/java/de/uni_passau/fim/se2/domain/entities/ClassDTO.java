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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassDTO {

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private boolean trackingEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Students in this class (loaded eagerly on detail view) */
    @Builder.Default
    private List<StudentInfo> students = new ArrayList<>();

    /** Worksheets assigned to this class */
    @Builder.Default
    private List<WorksheetInfo> worksheets = new ArrayList<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentInfo {
        private UUID userId;
        private String firstName;
        private String lastName;
        private String email;
        private String inviteToken;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorksheetInfo {
        private UUID worksheetId;
        private String name;
        private String description;
    }
}
