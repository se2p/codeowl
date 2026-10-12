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

import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.LearningEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The allowlist, which is the easiest thing in this system to forget.
 *
 * <p>An event type missing from {@code ALLOWED_EVENT_TYPES} is rejected with a 400 that
 * {@code useEventTracker} swallows on purpose, so the client reports success, nothing is
 * stored, and nobody finds out until a researcher goes looking for data that was never there.
 *
 * <p>Four event types the solve page emits were being dropped that way — the gap and
 * inline-split checks — and are now on the list and pinned below. Nothing recovers what was
 * lost before that, so the point of these tests is that it does not happen again: every type
 * {@code SolvePage} emits belongs here, and adding an emit site without adding the type is
 * silent.
 */
class LearningEventTypeTest {

    private LearningEventRepository repository;
    private LearningEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(LearningEventRepository.class);
        service = new LearningEventService(repository);
    }

    private void log(String eventType) {
        service.logEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                eventType, "{\"moves\":[]}");
    }

    @Test
    @DisplayName("arrangement_changed is accepted, so the move-by-move trail records")
    void arrangementChangedIsAllowed() {
        assertDoesNotThrow(() -> log("arrangement_changed"));
        verify(repository).save(any());
    }

    @Test
    @DisplayName("an unknown type is still refused rather than silently stored")
    void unknownTypeIsRejected() {
        assertThrows(DomainValidationException.class, () -> log("arrangement_nudged"));
    }

    /**
     * The four the class comment above was counting.
     *
     * <p>SolvePage emits these from the gap check ({@code :770}) and the inline-split check
     * ({@code :824}), and every one was rejected until they were added to the allowlist. They
     * carry the only record of what a student typed into a gap or how they ordered a split
     * line — {@code task_submissions} stores the visible text of each block, and neither of
     * those contributes any, so nothing else in the system holds them.
     */
    @ParameterizedTest(name = "{0} is recorded rather than dropped")
    @ValueSource(strings = {"gap_correct", "gap_incorrect", "split_line_correct", "split_line_incorrect"})
    void gapAndSplitEventsAreAllowed(String eventType) {
        assertDoesNotThrow(() -> log(eventType));
        verify(repository).save(any());
    }
}
