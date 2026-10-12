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

import de.uni_passau.fim.se2.domain.entities.UserDTO.UserRole;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Publishing a worksheet, from the service's side.
 *
 * <p>{@code WorksheetAccessPolicyTest} settles who is allowed to. What is left for the service
 * is gathering the facts the policy needs and refusing before anything is written — in
 * particular recognising the Library Inbox, which the policy cannot do for itself because that
 * means knowing about a sentinel buried in a description.
 */
class WorksheetVisibilityServiceTest {

    private WorksheetRepository worksheetRepo;
    private WorksheetService service;

    private static final UUID SHEET = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        worksheetRepo = mock(WorksheetRepository.class);
        service = new WorksheetService(worksheetRepo, mock(WorksheetCloneService.class));
        stubLoad("An ordinary worksheet");
    }

    @Test
    @DisplayName("the owner publishes their own worksheet")
    void ownerCanPublish() {
        WorksheetDTO result = service.setVisibility(
                SHEET, WorksheetVisibility.PUBLIC, OWNER, UserRole.TEACHER);

        assertAll(
                () -> verify(worksheetRepo).updateVisibility(SHEET, WorksheetVisibility.PUBLIC),
                () -> assertEquals(WorksheetVisibility.PUBLIC, result.getVisibility(),
                        "the returned worksheet must reflect the change, not the stale read"));
    }

    @Test
    @DisplayName("an admin publishes anyone's worksheet")
    void adminCanPublish() {
        service.setVisibility(SHEET, WorksheetVisibility.PUBLIC, OTHER, UserRole.ADMIN);
        verify(worksheetRepo).updateVisibility(SHEET, WorksheetVisibility.PUBLIC);
    }

    @Test
    @DisplayName("unpublishing back to UNLISTED works the same way")
    void ownerCanUnpublish() {
        service.setVisibility(SHEET, WorksheetVisibility.UNLISTED, OWNER, UserRole.TEACHER);
        verify(worksheetRepo).updateVisibility(SHEET, WorksheetVisibility.UNLISTED);
    }

    @Test
    @DisplayName("another teacher is refused, and nothing is written")
    void otherTeacherIsRefused() {
        assertThrows(AccessDeniedException.class, () ->
                service.setVisibility(SHEET, WorksheetVisibility.PUBLIC, OTHER, UserRole.TEACHER));
        verify(worksheetRepo, never()).updateVisibility(any(), any());
    }

    @Test
    @DisplayName("a student is refused, and so is an anonymous caller")
    void studentsAndAnonymousAreRefused() {
        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> service.setVisibility(
                        SHEET, WorksheetVisibility.PUBLIC, OTHER, UserRole.STUDENT)),
                () -> assertThrows(AccessDeniedException.class, () -> service.setVisibility(
                        SHEET, WorksheetVisibility.PUBLIC, null, null)),
                () -> verify(worksheetRepo, never()).updateVisibility(any(), any()));
    }

    @Test
    @DisplayName("the Library Inbox cannot be published, even by its owner")
    void libraryInboxIsRefused() {
        stubLoad("Quick-copied sequences from the Library. "
                + WorksheetService.LIBRARY_INBOX_SENTINEL);

        assertThrows(AccessDeniedException.class, () ->
                service.setVisibility(SHEET, WorksheetVisibility.PUBLIC, OWNER, UserRole.TEACHER));
        verify(worksheetRepo, never()).updateVisibility(any(), any());
    }

    @Test
    @DisplayName("a renamed Library Inbox is still the Library Inbox")
    void renamedInboxIsStillRecognised() {
        // The sentinel lives in the description precisely so renaming cannot smuggle the inbox
        // past this check.
        stubLoad("Meine Sammlung " + WorksheetService.LIBRARY_INBOX_SENTINEL + " bitte nicht loeschen");

        assertThrows(AccessDeniedException.class, () ->
                service.setVisibility(SHEET, WorksheetVisibility.PUBLIC, OWNER, UserRole.TEACHER));
    }

    @Test
    @DisplayName("null arguments are rejected before anything is written")
    void nullArgumentsAreRejected() {
        assertAll(
                () -> assertThrows(DomainValidationException.class, () ->
                        service.setVisibility(SHEET, null, OWNER, UserRole.TEACHER)),
                () -> assertThrows(DomainValidationException.class, () ->
                        service.setVisibility(null, WorksheetVisibility.PUBLIC, OWNER, UserRole.TEACHER)),
                () -> verify(worksheetRepo, never()).updateVisibility(any(), any()));
    }

    /** {@code getWorksheetById} fills the DTO it is handed rather than returning one. */
    private void stubLoad(String description) {
        doAnswer(invocation -> {
            WorksheetDTO target = invocation.getArgument(0);
            target.setName("Arrays");
            target.setDescription(description);
            target.setOwnerId(OWNER);
            target.setVisibility(WorksheetVisibility.UNLISTED);
            return null;
        }).when(worksheetRepo).getWorksheetById(any());
    }
}
