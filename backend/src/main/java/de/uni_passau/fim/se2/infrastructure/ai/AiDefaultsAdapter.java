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

import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.entities.AiProviderKey;
import de.uni_passau.fim.se2.domain.entities.OpenAiModel;
import org.springframework.stereotype.Component;

@Component
public class AiDefaultsAdapter implements AiDefaults {
    private final AiProps props;
    public AiDefaultsAdapter(AiProps props) { this.props = props; }

    @Override public AiProviderKey defaultProvider() { return props.getDefaultProvider(); }
    @Override public OpenAiModel defaultOpenAiModel() { return props.getOpenai().getDefaultModel(); }
}