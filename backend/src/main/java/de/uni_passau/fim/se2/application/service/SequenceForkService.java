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

import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.WorksheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gives a curator their own copy of a sequence so they can edit it without touching the original.
 *
 * <p>Sequences are shared: one library sequence can sit in many teachers' worksheets, which is
 * what makes the library worth having. That sharing is also why a curator must not edit in
 * place — a distractor removed in one worksheet would vanish from every other worksheet using
 * the same sequence, and from its author's copy, with nobody told.
 *
 * <p>Forking resolves the conflict rather than choosing a side. The curator gets a copy that is
 * theirs, their worksheet points at the copy from then on, and the original is left exactly as
 * it was — still shared, still owned by whoever made it, still whole.
 *
 * <p>Deliberately an explicit step rather than something that happens on the first keystroke.
 * A silent fork would leave a teacher editing a duplicate they never asked for while the
 * sequence they thought they were fixing stayed broken. {@link
 * de.uni_passau.fim.se2.domain.model.SequenceCuratorPolicy#mustForkToEdit} is how the UI knows
 * to offer it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceForkService {

    private final WorksheetRepository worksheetRepo;
    private final WorksheetCloneService cloneService;

    /**
     * Replaces a worksheet's link to a foreign sequence with a link to a private copy.
     *
     * <p>The whole thing is one transaction. A fork that copied the sequence and then failed to
     * repoint the worksheet would leave the curator with an orphan copy and an unchanged
     * worksheet — the worst of both, and invisible until they wondered why their edits had no
     * effect.
     *
     * @param worksheetId the curator's worksheet; the caller has already established they own it
     * @param sequenceId  the sequence to fork, which must currently be in that worksheet
     * @param curatorId   the new owner
     * @return the id of the copy, which the caller redirects to
     * @throws DomainValidationException if an argument is null, or if the sequence is not in
     *                                   the worksheet — forking something a worksheet does not
     *                                   contain would silently add material to it
     */
    @Transactional
    public UUID forkForEditing(UUID worksheetId, UUID sequenceId, UUID curatorId) {
        if (worksheetId == null || sequenceId == null || curatorId == null) {
            throw new DomainValidationException(
                    "Worksheet ID, sequence ID and curator ID must not be null.");
        }

        WorksheetDTO worksheet = WorksheetDTO.builder().id(worksheetId).build();
        worksheetRepo.getWorksheetById(worksheet);

        if (!containsSequence(worksheet, sequenceId)) {
            throw new DomainValidationException(
                    "Sequence " + sequenceId + " is not part of worksheet " + worksheetId + ".");
        }

        UUID copyId = cloneService.copySequenceInto(worksheet, sequenceId, curatorId);

        // Only after the copy is linked. Removing first would, on a failed copy, strip the
        // sequence out of the worksheet and give nothing back.
        worksheetRepo.removeSequenceFromWorksheet(
                worksheet, TieredTaskSequenceDTO.builder().id(sequenceId).build());

        log.info("Forked sequence {} -> {} for curator {} in worksheet {}",
                sequenceId, copyId, curatorId, worksheetId);

        return copyId;
    }

    private boolean containsSequence(WorksheetDTO worksheet, UUID sequenceId) {
        if (worksheet.getSequenceDTOS() == null) {
            return false;
        }
        return worksheet.getSequenceDTOS().stream()
                .anyMatch(sequence -> sequenceId.equals(sequence.getId()));
    }
}
