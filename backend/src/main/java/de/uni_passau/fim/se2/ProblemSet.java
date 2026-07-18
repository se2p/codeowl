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
package de.uni_passau.fim.se2;

import java.util.List;

/**
 * Represents a set of problem statements.
 * <p>
 * This class encapsulates a list of problem statements that can be accessed via a getter method.
 * </p>
 *
 * @author Luca Cisternino
 * @version 1.0
 */
public class ProblemSet {
    
    /**
     * A list containing problem statements.
     */
    private List<String> problem_statements;

    /**
     * Retrieves the list of problem statements.
     *
     * @return a list of problem statements
     */
    public List<String> getProblemStatements() {
        return problem_statements;
    }
}
