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
package de.uni_passau.fim.se2.infrastructure.ai;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public interface AiTransport {
    /**
     * Sends a JSON-formatted GPT request to the OpenAI API.
     *
     * <p>This method uses the configured HTTP client to send the request and
     * returns the full HTTP response body.
     *
     * @param request GPT request body as JSON
     * @return the HTTP response from OpenAI
     * @throws IOException if the request fails
     * @throws InterruptedException if the sending is interrupted
     */
    HttpResponse<String> sendRequest(HttpRequest request)
            throws IOException, InterruptedException;
}