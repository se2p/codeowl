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
package de.uni_passau.fim.se2.domain.entities;

/**
 * Enum representing different types of prompts used for distinguishing between
 * programming concepts and sample tasks.
 * <p>
 * The prompts follow a structured naming convention:
 * <ul>
 *   <li>CS - Both Concepts and Sample Task are provided.</li>
 *   <li>NOT_CS - Concepts are NOT given, but a Sample Task is provided.</li>
 *   <li>C_NOT_S - Concepts are given, but a Sample Task is NOT provided.</li>
 *   <li>SOLVE - Represents a solving prompt.</li>
 * </ul>
 * </p>
 * @author Luca Cisternino
 * @version 1.0
 */
public enum PromptType {

    /** Both Concepts and Sample Task are provided. */
    CS,

    /** Concepts are NOT given, but a Sample Task is provided. */
    NOT_CS,

    /** Concepts are given, but a Sample Task is NOT provided. */
    C_NOT_S,

    /** Represents a solving prompt. */
    SOLVE
}