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
package de.uni_passau.fim.se2.infrastructure.ai.openai;

import de.uni_passau.fim.se2.application.service.*;
import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.domain.model.ConceptCatalog;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.HttpClientTransport;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiProps.class)
public class OpenAiClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public AiTransport gptTransport(HttpClient httpClient) {
        return new HttpClientTransport(httpClient);
    }

    @Bean
    public OpenAiClient chatGPTClient(
            @Value("${openai.api.key}") String apiKey,
            AiTransport transport,
            PromptBuilder promptBuilder,
            AiProps aiProps,
            ConceptCatalog conceptCatalog,
            CodeSanitizerService sanitizer
    ) {
        return new OpenAiClient(apiKey, transport, promptBuilder, aiProps, conceptCatalog, sanitizer);
    }
}
