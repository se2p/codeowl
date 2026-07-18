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
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.hints.HintService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/hints")
public class HintController {

    private final HintService hintService;
    private final OwnershipService ownershipService;

    public HintController(HintService hintService, OwnershipService ownershipService) {
        this.hintService = hintService;
        this.ownershipService = ownershipService;
    }

    @PostMapping("/{taskId}")
    public ResponseEntity<HintService.HintResponse> getHint(
            @PathVariable UUID taskId,
            @RequestBody HintRequest request) {
        UUID userId = ownershipService.getCurrentUserId();
        HintService.HintResponse response = hintService.getHint(
            taskId, request.blockOrder(), request.indentLevels(), request.hintLevel(), userId
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/config")
    public ResponseEntity<Map<String, Object>> getHintConfig(@PathVariable UUID taskId) {
        boolean enabled = hintService.isHintsEnabled(taskId);
        return ResponseEntity.ok(Map.of("hintsEnabled", enabled));
    }

    public record HintRequest(List<String> blockOrder, List<Integer> indentLevels, int hintLevel) {}
}
