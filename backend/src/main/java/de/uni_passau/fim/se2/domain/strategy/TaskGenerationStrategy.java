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
package de.uni_passau.fim.se2.domain.strategy;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.infrastructure.ai.openai.OpenAiClient;

import java.io.IOException;

/**
 * Strategy interface for generating tiered task sequences.
 */
public interface TaskGenerationStrategy {

    /**
     * Generates a Tiered Task Sequence based on the Strategy.
     * @param client The ChatGPTCLient responsible for API communication.
     * @return a TieredTaskSequence instance.
     * @throws IOException throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    TieredTaskSequenceDTO generate(OpenAiClient client)
            throws IOException, InterruptedException;

    /**
     * Generates a Tiered Task Sequence with optional distractor planning (AP15).
     * Default delegates to the original method (ignoring the flag).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generate(client);
    }

    /**
     * AP20: Generates a Tiered Task Sequence with constraints + distractors.
     * Default delegates to the distractor-aware overload (ignoring constraints).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled,
                                           GenerationConstraints constraints)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled);
    }

    /**
     * AP21: Generates a Tiered Task Sequence with constraints + distractors + tierCount.
     * Default delegates to the constraint-aware overload (ignoring tierCount).
     */
    default TieredTaskSequenceDTO generate(OpenAiClient client, boolean distractorsEnabled,
                                           GenerationConstraints constraints, int tierCount)
            throws IOException, InterruptedException {
        return generate(client, distractorsEnabled, constraints);
    }
}