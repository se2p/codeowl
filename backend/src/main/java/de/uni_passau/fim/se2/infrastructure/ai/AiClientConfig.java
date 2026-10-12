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
package de.uni_passau.fim.se2.infrastructure.ai;

import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.infrastructure.ai.claude.*;
import de.uni_passau.fim.se2.infrastructure.ai.gemini.*;
import de.uni_passau.fim.se2.infrastructure.ai.openai.*;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.infrastructure.ai.claude.ClaudeClient;
import de.uni_passau.fim.se2.infrastructure.ai.gemini.GeminiClient;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;
import org.springframework.boot.context.properties.*;
import org.springframework.context.annotation.*;

// infrastructure/ai/AiClientConfig.java
@Configuration
@EnableConfigurationProperties(AiProps.class)
public class AiClientConfig {

    @Bean
    @Primary
    public AiModelClient aiModelClient(
            AiProps props,
            OpenAiClient openai,
            ClaudeClient claude,
            GeminiClient gemini
    ) {
        return switch (props.getDefaultProvider()) {
            case OPENAI -> openai;
            case CLAUDE -> claude;
            case GEMINI -> gemini;
        };
    }
}