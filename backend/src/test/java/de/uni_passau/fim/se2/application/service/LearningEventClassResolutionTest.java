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

import de.uni_passau.fim.se2.domain.entities.LearningEventDTO;
import de.uni_passau.fim.se2.domain.interfaces.LearningEventRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.dao.PostgreLearningEventDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which of the two things attached to an event survives, and which does not.
 *
 * <p>V22 gave learning_events a class_id so a per-class research view is answerable at all.
 * The whole point rests on one ordering: the class is resolved <em>before</em> the tracking
 * check nulls the user id, and is kept afterwards. Swap those two and every event from a
 * tracking-disabled class silently loses its class as well as its student — which looks like
 * no data rather than like a bug, the failure mode this codebase keeps running into.
 */
@DisplayName("learning event class resolution")
class LearningEventClassResolutionTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();
    private static final UUID SEQUENCE = UUID.randomUUID();
    private static final UUID CLASS = UUID.randomUUID();

    private PostgreLearningEventDao dao;
    private LearningEventService service;

    @BeforeEach
    void setUp() {
        dao = mock(PostgreLearningEventDao.class);
        service = new LearningEventService(dao);
    }

    private LearningEventDTO logAndCapture(UUID userId) {
        service.logEvent(userId, TASK, SEQUENCE, 1, "arrangement_changed", "{\"moves\":[]}");
        ArgumentCaptor<LearningEventDTO> saved = ArgumentCaptor.forClass(LearningEventDTO.class);
        verify(dao).save(saved.capture());
        return saved.getValue();
    }

    @Nested
    @DisplayName("tracking disabled")
    class TrackingDisabled {

        @BeforeEach
        void trackingOff() {
            when(dao.resolveClassId(USER, TASK)).thenReturn(CLASS);
            when(dao.isTrackingEnabledForStudent(USER)).thenReturn(false);
        }

        @Test
        @DisplayName("the student is dropped but the class is kept, so the work is still countable")
        void classSurvivesAnonymization() {
            LearningEventDTO event = logAndCapture(USER);
            assertAll(
                    () -> assertNull(event.getUserId(), "user id must not survive"),
                    () -> assertEquals(CLASS, event.getClassId(), "class id must survive")
            );
        }
    }

    @Nested
    @DisplayName("tracking enabled")
    class TrackingEnabled {

        @Test
        @DisplayName("both the student and the class are recorded")
        void bothAreKept() {
            when(dao.resolveClassId(USER, TASK)).thenReturn(CLASS);
            when(dao.isTrackingEnabledForStudent(USER)).thenReturn(true);

            LearningEventDTO event = logAndCapture(USER);
            assertAll(
                    () -> assertEquals(USER, event.getUserId()),
                    () -> assertEquals(CLASS, event.getClassId())
            );
        }

        @Test
        @DisplayName("a student in no class at all is recorded without one, not refused")
        void noClassIsFine() {
            when(dao.resolveClassId(USER, TASK)).thenReturn(null);
            when(dao.isTrackingEnabledForStudent(USER)).thenReturn(true);

            LearningEventDTO event = logAndCapture(USER);
            assertAll(
                    () -> assertEquals(USER, event.getUserId()),
                    () -> assertNull(event.getClassId())
            );
        }
    }

    @Test
    @DisplayName("a visitor who is not logged in gets no class, and is not looked up either")
    void anonymousVisitorIsNotResolved() {
        LearningEventDTO event = logAndCapture(null);

        assertAll(
                () -> assertNull(event.getUserId()),
                () -> assertNull(event.getClassId()),
                // There is no student to resolve a class from, so asking the database would be
                // a query per anonymous event for a guaranteed null.
                () -> verify(dao, never()).resolveClassId(any(), any())
        );
    }

    @Test
    @DisplayName("a repository that is not the Postgres DAO still stores the event")
    void nonPostgresRepositoryStillWorks() {
        LearningEventRepository plain = mock(LearningEventRepository.class);
        LearningEventService plainService = new LearningEventService(plain);

        plainService.logEvent(USER, TASK, SEQUENCE, 1, "task_opened", "{}");

        ArgumentCaptor<LearningEventDTO> saved = ArgumentCaptor.forClass(LearningEventDTO.class);
        verify(plain).save(saved.capture());
        assertNull(saved.getValue().getClassId());
    }
}
