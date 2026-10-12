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
package de.uni_passau.fim.se2.api.dto;

import lombok.Data;

import java.util.List;

/**
 * A whole class list at once, as read from a teacher's CSV.
 *
 * The rows are the same shape as a single addition, so the two paths cannot
 * drift apart in what they accept.
 *
 * Deliberately carries no bean-validation constraints. There is no handler for
 * {@code MethodArgumentNotValidException}, so a constraint here would be caught
 * by the catch-all and answered with a generic 500; the checks in
 * {@code ClassService.createStudents} come back as a 400 that names the
 * offending row, which is the message a teacher can act on.
 */
@Data
public class BulkCreateStudentsRequest {

    private List<CreateStudentRequest> students;
}
