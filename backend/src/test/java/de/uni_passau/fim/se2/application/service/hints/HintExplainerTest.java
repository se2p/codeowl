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
package de.uni_passau.fim.se2.application.service.hints;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The level-2 explanation is written by the model, so what can be checked here
 * is the prompt it is given.
 *
 * That has been the source of two bugs already. The prompt used to spell German
 * without umlauts and the model copied it; then it told the model to name blocks
 * "e.g. Block 3" while handing it letters, and the answer disagreed with the
 * badge on the block. Both are a matter of what the prompt says, so the prompt
 * is what these tests hold still.
 */
class HintExplainerTest {

    /**
     * Captures the prompt instead of calling a model.
     *
     * No language model is contacted here, and none can be: AiModelClient is an
     * interface, the only implementation given to HintExplainer is a Mockito
     * mock, and the test constructs the explainer directly rather than through a
     * Spring context that might inject a real provider. The assertions are about
     * the prompt that would have been sent, not about any reply.
     */
    private static final class CapturingClient {
        String prompt;
    }

    private String promptFor(HintLocale locale) throws Exception {
        CapturingClient captured = new CapturingClient();
        AiModelClient client = Mockito.mock(AiModelClient.class);
        Mockito.when(client.generateTaskDescription(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    captured.prompt = invocation.getArgument(1);
                    return "explanation";
                });

        new HintExplainer(client).explain(
                List.of(new BlockMapper.BlockFinding(4,
                        new AnalysisFinding("F821", "Undefined name 'x'", "error",
                                5, 5, "ruff", "undefined_variable"))),
                "print(x)", "x = 1\nprint(x)", "python",
                List.of(2), false, 3, 5, locale);

        return captured.prompt;
    }

    @Test
    @DisplayName("blocks are named by letter, never by number")
    void namesBlocksByLetter() throws Exception {
        String prompt = promptFor(HintLocale.EN);
        // Assert on the data lines by their prefixes. The instruction itself
        // legitimately mentions "Block 3" as the counter-example not to follow,
        // so a bare search for that string would always match.
        assertAll(
                // the finding is on index 4, so the block is "E"
                () -> assertTrue(prompt.contains("- Block E:"), prompt),
                () -> assertFalse(prompt.contains("- Block 5:"), prompt),
                // the distractor is on index 2, so "C"
                () -> assertTrue(prompt.contains("LÖSUNGSBEREICH: Block C"), prompt),
                () -> assertFalse(prompt.contains("LÖSUNGSBEREICH: Block 3"), prompt));
    }

    @Test
    @DisplayName("the instruction tells the model to use letters, not numbers")
    void instructionForbidsNumbers() throws Exception {
        // The example in the instruction is what the model imitates, so it has to
        // be a letter. "e.g. Block 3" there produced numbered answers whatever
        // the data said.
        String en = promptFor(HintLocale.EN);
        String de = promptFor(HintLocale.DE);
        assertAll(
                () -> assertTrue(en.contains("never by a number"), en),
                () -> assertTrue(en.contains("\"Block C\""), en),
                () -> assertTrue(de.contains("nie mit einer Zahl"), de),
                () -> assertTrue(de.contains("\"Block C\""), de));
    }

    @Test
    @DisplayName("exactly one mocked call is made, and nothing else is touched")
    void talksOnlyToTheMock() throws Exception {
        AiModelClient client = Mockito.mock(AiModelClient.class);
        Mockito.when(client.generateTaskDescription(anyString(), anyString()))
                .thenReturn("explanation");

        new HintExplainer(client).explain(List.of(), "code", "solution", "python",
                List.of(), false, 0, 0, HintLocale.EN);

        // Pins that the explainer reaches the model through this one method and
        // nothing else — so the suite can never start making real calls without
        // this failing first.
        Mockito.verify(client).generateTaskDescription(anyString(), anyString());
        Mockito.verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("the answer language follows the locale")
    void answerLanguageFollowsLocale() throws Exception {
        assertAll(
                () -> assertTrue(promptFor(HintLocale.EN).contains("in English"), "EN"),
                () -> assertTrue(promptFor(HintLocale.DE).contains("auf Deutsch"), "DE"));
    }

    @Test
    @DisplayName("the German prompt uses umlauts, so the model mirrors them")
    void germanPromptUsesUmlauts() throws Exception {
        String de = promptFor(HintLocale.DE);
        for (String bad : List.of("Bloeck", "Loesung", "Schueler", "Erklaere", "benoetig")) {
            assertFalse(de.contains(bad), "transliteration '" + bad + "' in prompt");
        }
        assertTrue(de.contains("Umlauten"), "no explicit umlaut instruction");
    }

    @Test
    @DisplayName("a failing model yields a message in the right language, not an exception")
    void failureIsLocalised() throws Exception {
        AiModelClient failing = Mockito.mock(AiModelClient.class);
        Mockito.when(failing.generateTaskDescription(anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream down"));

        String en = new HintExplainer(failing).explain(List.of(), "code", "solution", "python",
                List.of(), false, 0, 0, HintLocale.EN);
        String de = new HintExplainer(failing).explain(List.of(), "code", "solution", "python",
                List.of(), false, 0, 0, HintLocale.DE);

        assertAll(
                () -> assertTrue(en.contains("could not be generated"), en),
                () -> assertTrue(de.contains("Erklärung konnte nicht"), de));
    }
}
