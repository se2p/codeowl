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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class HttpClientTransport implements AiTransport {

    /**
     * HttpClient used to send and receive requests.
     */
    private final HttpClient httpClient;

    /**
     * Constructs a new instance of the HttpClientTransport.
     * @param client the HttpClient used to send and receive requests.
     */
    public HttpClientTransport(final HttpClient client) {
        this.httpClient = client;
    }

    /**
     * Sends a request using the HttpClient.
     * @param request GPT request body.
     * @return the HttpResponse.
     * @throws IOException throws IOException
     * @throws InterruptedException throws InterruptedException
     */
    @Override
    public HttpResponse<String> sendRequest(final HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}