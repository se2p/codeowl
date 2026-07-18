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

import de.uni_passau.fim.se2.application.service.AdminService;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "email") String sort,
            @RequestParam(defaultValue = "asc") String dir) {
        return ResponseEntity.ok(adminService.getUsers(search, page, size, sort, dir));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody AdminUpdateUserRequest body) {
        UserDTO updated = adminService.updateUser(
                id,
                body.firstName(),
                body.lastName(),
                body.email(),
                body.role()
        );
        return ResponseEntity.ok(Map.of(
                "message", "Benutzer aktualisiert.",
                "userId", updated.getUserId().toString(),
                "role", updated.getRole().name()
        ));
    }

    public record AdminUpdateUserRequest(String firstName, String lastName, String email, String role) {}

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Benutzer geloescht."));
    }

    @PostMapping("/users/{id}/make-admin")
    public ResponseEntity<?> toggleAdmin(@PathVariable UUID id) {
        UserDTO updated = adminService.toggleAdmin(id);
        return ResponseEntity.ok(Map.of(
                "message", "Rolle geaendert.",
                "role", updated.getRole().name()
        ));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable UUID id) {
        adminService.resetPassword(id);
        return ResponseEntity.ok(Map.of("message", "Passwort-Reset-Email gesendet."));
    }
}
