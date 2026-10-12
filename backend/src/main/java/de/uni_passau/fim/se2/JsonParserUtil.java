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
package de.uni_passau.fim.se2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * Utility class for parsing JSON strings into {@link JsonNode} objects.
 * <p>
 * This class follows the Singleton pattern to ensure a single instance of the JSON parser
 * is used throughout the application.
 * </p>
 *
 * <p>
 * It uses the Jackson {@link ObjectMapper} for parsing JSON.
 * </p>
 *
 * @author Luca Cisternino
 * @version 1.0
 */
public class JsonParserUtil {
    
    /**
     * The single instance of {@code JsonParserUtil}, ensuring only one instance
     * exists throughout the application's lifecycle.
     */
    private static final JsonParserUtil INSTANCE = new JsonParserUtil();

    /**
     * Jackson's {@link ObjectMapper} instance used for parsing JSON.
     */
    private final ObjectMapper mapper;

    /**
     * Private constructor to prevent external instantiation.
     * Initializes the {@link ObjectMapper} instance.
     */
    private JsonParserUtil() {
        mapper = new ObjectMapper();
    }

    /**
     * Retrieves the singleton instance of {@code JsonParserUtil}.
     *
     * @return the single instance of {@code JsonParserUtil}
     */
    public static JsonParserUtil getInstance() {
        return INSTANCE;
    }

    /**
     * Parses a JSON string and returns a {@link JsonNode} representation.
     *
     * @param json the JSON string to parse
     * @return the parsed {@link JsonNode} object
     * @throws IOException if an error occurs while parsing the JSON string
     */
    public JsonNode parseJson(String json) throws IOException {
        return mapper.readTree(json);
    }
}
