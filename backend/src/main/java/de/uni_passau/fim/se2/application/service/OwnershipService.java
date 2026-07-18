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
package de.uni_passau.fim.se2.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Centralized service for ownership and role-based access checks.
 * Used by controllers and services to enforce AP2 authorization rules.
 */
@Service
public class OwnershipService {

    private static final Logger logger = LoggerFactory.getLogger(OwnershipService.class);

    /**
     * Returns the current authenticated user's ID, or null if anonymous.
     */
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return (UUID) auth.getPrincipal();
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Returns the current authenticated user's role, or null if anonymous.
     */
    public String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if the current user is an admin.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(getCurrentRole());
    }

    /**
     * Checks if the current user is a student.
     */
    public boolean isStudent() {
        return "STUDENT".equals(getCurrentRole());
    }

    /**
     * Checks if the current user is a teacher.
     */
    public boolean isTeacher() {
        return "TEACHER".equals(getCurrentRole());
    }

    /**
     * Checks if the current user is authenticated (not anonymous).
     */
    public boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }

    /**
     * Checks if the current user owns the resource (or is admin).
     *
     * @param resourceOwnerId the owner_id of the resource
     * @return true if the user is the owner or an admin
     */
    public boolean isOwnerOrAdmin(UUID resourceOwnerId) {
        if (isAdmin()) return true;
        UUID currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(resourceOwnerId);
    }

    /**
     * Throws AccessDeniedException if the current user cannot modify the resource.
     * Rules:
     * - Admin: always allowed
     * - Owner: allowed
     * - Everyone else: denied
     *
     * @param resourceOwnerId the owner_id of the resource
     * @param resourceType    human-readable name for error messages
     */
    public void checkOwnership(UUID resourceOwnerId, String resourceType) {
        if (isAdmin()) return;

        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authentication required to modify " + resourceType);
        }
        if (!currentUserId.equals(resourceOwnerId)) {
            logger.warn("User {} tried to modify {} owned by {}",
                    currentUserId, resourceType, resourceOwnerId);
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to modify this " + resourceType);
        }
    }

    /**
     * Throws AccessDeniedException if the current user is a student.
     * Students cannot generate sequences or create worksheets.
     */
    public void denyStudents(String action) {
        if (isStudent()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Students are not allowed to " + action);
        }
    }
}
