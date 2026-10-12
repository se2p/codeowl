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
import java.util.ArrayList;
import java.util.List;
import de.uni_passau.fim.se2.domain.model.WorksheetAvailability;

import java.time.Instant;
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
    /** Who created the class. Only filled for the admin overview (findAll); null elsewhere. */
    private String ownerName;
    private String ownerEmail;
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
        /** FIM username if the student logs in with a FIM-Account, otherwise null. */
        private String fimUid;
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

        /**
         * Schedule for this worksheet in this class. Each is optional and null
         * means unconstrained: no release is immediately available, no deadline
         * stays open, no grace makes the deadline sharp.
         */
        /** Set by the teacher's publish button; a new assignment starts false. */
        private boolean published;

        private Instant releaseAt;
        private Instant deadlineAt;
        private Integer graceMinutes;

        /**
         * Where the assignment currently sits — SCHEDULED, OPEN or CLOSED.
         * Computed server-side so the client cannot disagree about the time.
         */
        private WorksheetAvailability availability;
    }
}
