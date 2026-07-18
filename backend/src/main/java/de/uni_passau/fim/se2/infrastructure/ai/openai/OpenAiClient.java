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
import de.uni_passau.fim.se2.domain.entities.*;
import de.uni_passau.fim.se2.domain.interfaces.*;
import de.uni_passau.fim.se2.application.service.CodeSanitizerService;
import de.uni_passau.fim.se2.domain.entities.CurriculumCodeExampleDTO;
import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.OpenAiModel;
import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.domain.model.ConceptCatalog;
import de.uni_passau.fim.se2.infrastructure.ai.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import de.uni_passau.fim.se2.infrastructure.ai.AbstractAiClient;
import de.uni_passau.fim.se2.infrastructure.ai.AiProps;
import de.uni_passau.fim.se2.infrastructure.ai.AiTransport;
import de.uni_passau.fim.se2.infrastructure.ai.PromptBuilder;
import lombok.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;

/**
 * The {@code ChatGPTClient} class is responsible for generating and solving
 * programming-related prompts by interacting with OpenAI's ChatGPT API.
 *
 * <p>It provides methods for:
 * <ul>
 *   <li>Generating tiered tasks from a sample task and/or concept list</li>
 *   <li>Extracting key programming concepts from tasks</li>
 *   <li>Generating new sample tasks based on concepts</li>
 *   <li>Requesting clean code-only solutions from GPT</li>
 * </ul>
 *
 * <p>All requests enforce strict JSON schema validation via OpenAI’s
 * {@code response_format} to ensure parsable output.
 *
 * <p>This class is part of the infrastructure layer and connects the domain
 * logic to the external GPT API.
 *
 * @author Luca Cisternino
 * @version 1.0
 */
public class OpenAiClient extends AbstractAiClient {

    /**
     * String representing the API address to OpenAIs API.
     */
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private static final Double DEFAULT_TEMPERATURE = 1.0;
    private static final Double DEFAULT_TOP_P       = null; // null = omit field (API default)

    /**
     * int representing status code 200 (lower bound for successful status).
     */
    public static final int STATUS_CODE_200 = 200;

    /**
     * int representing status code 300 (upper bound for successful status).
     */
    public static final int STATUS_CODE_300 = 300;
    /**
     * A Jackson {@link ObjectMapper} instance used for JSON processing.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * The API key used to authenticate with the OpenAI API.
     */
    private final String apiKey;

    /**
     * The {@link AiTransport} instance used to send HTTP requests.
     */
    private final AiTransport transport;

    /**
     * The {@link PromptBuilder} instance used to construct GPT prompts.
     */
    private final PromptBuilder promptBuilder;

    /**
     * Fixed concept catalog for catalog-constrained concept detection (AP25).
     */
    private final ConceptCatalog conceptCatalog;

    /**
     * Default model used with OpenAi.
     */
    private final OpenAiModel defaultModel;

    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);

    /**
     * Primary constructor used by Spring.
     *
     * @param apiKey OpenAI API key from application.properties
     * @param transport HTTP transport abstraction
     * @param promptBuilder builder for structured GPT prompts
     */
    public OpenAiClient(
            @Value("${openai.api.key}") final String apiKey,
            final AiTransport transport,
            final PromptBuilder promptBuilder,
            final AiProps aiProps,
            final ConceptCatalog conceptCatalog,
            CodeSanitizerService codeSanitizer
    ) {
        super(codeSanitizer);
        this.apiKey = apiKey;
        this.transport = transport;
        this.promptBuilder = promptBuilder;
        this.defaultModel = aiProps.getOpenai().getDefaultModel();
        this.conceptCatalog = conceptCatalog;
    }

    /**
     * Sends a single problem statement to GPT and returns a code-only solution.
     *
     * <p>The prompt strictly instructs GPT to return only properly formatted
     * code, without any explanation, comments, or extra output.
     *
     * @param contract TODO
     * @return a {@link JsonNode} containing one field "code" with the solution
     * @throws IOException if API communication fails
     * @throws InterruptedException if the thread is interrupted during the
     * request
     */
    @Override
    public JsonNode solveProblem(final SolveContract contract)
            throws IOException, InterruptedException {
        return solveProblem(contract, defaultModel);
    }

    @Override
    public JsonNode solveProblemSimple(final String taskDescription, final String language)
        throws IOException, InterruptedException {
            return solveProblemSimple(taskDescription, language, defaultModel);
        }

        public JsonNode solveProblemSimple(final String taskDescription, final String language, final OpenAiModel model)
        throws IOException, InterruptedException {
            logger.trace("Simple solve: task='{}', lang='{}' (model={})", taskDescription, language, model);
            ObjectNode json = promptBuilder.buildSimpleSolveRequest(taskDescription, language);
            attachModel(json, model);
            // optional: attachSampling(json, DEFAULT_TEMPERATURE, DEFAULT_TOP_P);
            HttpResponse<String> response = sendRequest(json);
            JsonNode parsed = parseResponse(response);
            JsonNode codeNode = validateAndGetField(parsed, "code");
            validateTextField(codeNode, "code");
            return codeNode;
        }

    public JsonNode solveProblem(@NonNull final SolveContract contract, final OpenAiModel model)
            throws IOException, InterruptedException {
        logger.trace("Solving contract: {} (model={})", contract, model);
        ObjectNode json = promptBuilder.buildSolveRequest(contract);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        JsonNode codeNode = validateAndGetField(parsed, "code");
        validateTextField(codeNode, "code");

        String cleanCode = sanitize(codeNode.asText(), contract.language());
        if (parsed.isObject()) {
            ((ObjectNode) parsed).put("code", cleanCode);
            codeNode = parsed.get("code");
        }

        return codeNode;
    }

    /**
     * Sends a JSON-formatted GPT request to the OpenAI API.
     *
     * <p>This method uses the configured HTTP client to send the request and
     * returns the full HTTP response body.
     *
     * @param json the GPT request body as JSON
     * @return the HTTP response from OpenAI
     * @throws IOException if the request fails
     * @throws InterruptedException if the sending is interrupted
     */
    HttpResponse<String> sendRequest(final ObjectNode json)
            throws IOException, InterruptedException {

        String requestBody = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(json);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        int maxRetries = 3;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                logger.trace("Sending request (attempt {}): {}", attempt, requestBody);
                HttpResponse<String> response = transport.sendRequest(request);

                if (response.statusCode() >= STATUS_CODE_200 && response.statusCode() < STATUS_CODE_300) {
                    logger.trace("Response: {}", response.body());
                    return response;
                }

                // OPT-5: Exponential backoff with jitter — prevents thundering-herd
                // when many parallel enrichment threads (OPT-1) all hit 429/503 together.
                if ((response.statusCode() == 503 || response.statusCode() == 429) && attempt < maxRetries) {
                    long delay = backoffDelayMs(attempt);
                    logger.warn("OpenAI API returned {} – retrying in {}ms (attempt {}/{})",
                                response.statusCode(), delay, attempt, maxRetries);
                    Thread.sleep(delay);
                    continue;
                }

                throw new IOException("GPT API returned unexpected status "
                                              + response.statusCode() + ": " + response.body());

            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    logger.error("Request failed after {} attempts: {}", attempt, e.getMessage());
                    throw e;
                }
                long delay = backoffDelayMs(attempt);
                logger.warn("IOException during request (attempt {}/{}): {}, retrying in {}ms",
                            attempt, maxRetries, e.getMessage(), delay);
                Thread.sleep(delay);
            }
        }
    }

    /**
     * Exponential backoff with jitter (OPT-5).
     * attempt=1 → ~1–2s, attempt=2 → ~2–4s, attempt=3 → ~4–8s (capped at 30s).
     * The jitter prevents parallel enrichment threads (OPT-1) from retrying simultaneously
     * after a shared rate-limit/503 event (thundering-herd).
     */
    private static long backoffDelayMs(int attempt) {
        double base = Math.pow(2, attempt) * 1000.0;
        double jitter = 0.5 + java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.5);
        return (long) Math.min(30_000L, base * jitter);
    }


    /**
     * Extracts and parses the inner JSON response from GPT's message field.
     *
     * <p>GPT responses are nested: this method unwraps the outer layers and
     * returns the actual structured JSON object sent via the response_format.
     *
     * @param response the raw HTTP response
     * @return the parsed GPT payload as {@link JsonNode}
     * @throws IOException throws IOException.
     */
    JsonNode parseResponse(final HttpResponse<String> response)
            throws IOException {
        logger.trace("Parsing response: {}", response.body());
        try {
            return mapper.readTree(mapper.readTree(response.body())
                    .get("choices").get(0)
                    .get("message").get("content").asText());
        } catch (NullPointerException e) {
            logger.error("GPT response missing required message{}", String.valueOf(e));
            throw new IOException("GPT response missing required message "
                    + "structure", e);
        }
    }

    /**
     * Validates that the provided JSON object is not null and contains the
     * specified field.
     * Returns the JSON node corresponding to the field.
     *
     * @param parsed the JSON object (GPT response) to validate
     * @param fieldName the name of the required field
     * @return the JSON node for the specified field
     * @throws IOException if the JSON object is null or does not contain the
     * field
     */
    JsonNode validateAndGetField(final JsonNode parsed, final String fieldName)
            throws IOException {
        if (parsed == null) {
            logger.error("GPT response is null, cannot extract field: {}", fieldName);
            throw new IOException("GPT response is null – "
                    + "cannot extract field: '" + fieldName + "'");
        }
        if (!parsed.has(fieldName)) {
            logger.error("GPT response missing required field: {}", fieldName);
            throw new IOException("GPT response missing required field: '"
                    + fieldName + "'");
        }
        return parsed.get(fieldName);
    }

    /**
     * Validates that the provided JSON node is textual and not empty.
     *
     * @param node the JSON node to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IOException if the node is null, not textual, or contains an
     * empty string
     */
    void validateTextField(final JsonNode node, final String fieldName)
            throws IOException {
        if (node.isNull() || !node.isTextual()
                || node.asText().trim().isEmpty()) {
            logger.error("GPT response invalid or empty text field:" + fieldName);
            throw new IOException("GPT response invalid or empty string field:"
                    + " '" + fieldName + "'");
        }
    }

    /**
     * Validates that the provided JSON node is an array and contains at least
     * one element.
     *
     * @param node the JSON node to validate
     * @param fieldName the name of the field (for error messages)
     * @throws IOException if the node is null, not an array, or is empty
     */
    void validateArrayField(final JsonNode node, final String fieldName)
            throws IOException {

        if (node.isNull() || !node.isArray() || node.isEmpty()) {
            logger.error("GPT response invalid or empty array field:" + fieldName);
            throw new IOException("GPT response invalid or empty array field:"
                    + " '" + fieldName + "'");
        }
    }

    /**
     * Generates a TieredTaskSequence.
     *
     * @param taskDescription The problem statement of the sample task.
     * @param concepts Concepts found in the sample task.
     * @param codeSnippet Code snippet serving as a solution to the sample task.
     * @param language The programming language of the code snippet.
     * @return JsonNode representing the TieredTaskSequence.
     * @throws IOException throws IOException.
     * @throws InterruptedException throws InterruptedException.
     */
    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language) throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, (String) null);
    }

    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage) throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage, defaultModel);
    }

    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, OpenAiModel model)
            throws IOException, InterruptedException {
        ObjectNode json = promptBuilder.buildSequenceRequest(taskDescription, concepts, codeSnippet, language, targetLanguage);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed.has("tasks")) {
            validateArrayField(parsed.get("tasks"), "tasks"); // must be an array (your mapper checks size==3 later)
        } else if (parsed.has("problem_statements")) {
            validateArrayField(parsed.get("problem_statements"), "problem_statements");
        } else {
            throw new IOException("GPT response missing required field: 'tasks' or 'problem_statements'");
        }

        logger.debug(parsed.toString());
        return parsed;
    }

    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage,
                                          distractorsEnabled, defaultModel);
    }

    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled,
                                               OpenAiModel model)
            throws IOException, InterruptedException {
        if (!distractorsEnabled) {
            return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage, model);
        }
        ObjectNode json = promptBuilder.buildSequenceRequest(taskDescription, concepts, codeSnippet, language,
                                                              targetLanguage, true);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed.has("tasks")) {
            validateArrayField(parsed.get("tasks"), "tasks");
        } else {
            throw new IOException("GPT response missing required field: 'tasks'");
        }
        logger.debug(parsed.toString());
        return parsed;
    }

    // ── AP20: Constraint-aware overloads ─────────────────────

    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled,
                                               GenerationConstraints constraints)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage,
                                          distractorsEnabled, constraints, defaultModel);
    }

    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled,
                                               GenerationConstraints constraints, OpenAiModel model)
            throws IOException, InterruptedException {
        if (constraints == null || !constraints.hasAny()) {
            return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage,
                                              distractorsEnabled, model);
        }
        ObjectNode json = promptBuilder.buildSequenceRequest(taskDescription, concepts, codeSnippet, language,
                                                              targetLanguage, distractorsEnabled, constraints);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed.has("tasks")) {
            validateArrayField(parsed.get("tasks"), "tasks");
        } else {
            throw new IOException("GPT response missing required field: 'tasks'");
        }
        logger.debug(parsed.toString());
        return parsed;
    }

    // ── AP21: Tier-count-aware overloads ─────────────────────

    @Override
    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled,
                                               GenerationConstraints constraints, int tierCount)
            throws IOException, InterruptedException {
        return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage,
                                          distractorsEnabled, constraints, tierCount, defaultModel);
    }

    public JsonNode generateTieredTaskSequence(String taskDescription, List<String> concepts, String codeSnippet,
                                               String language, String targetLanguage, boolean distractorsEnabled,
                                               GenerationConstraints constraints, int tierCount, OpenAiModel model)
            throws IOException, InterruptedException {
        if (tierCount <= 3) {
            // Delegate to existing 3-tier path
            return generateTieredTaskSequence(taskDescription, concepts, codeSnippet, language, targetLanguage,
                                              distractorsEnabled, constraints, model);
        }
        ObjectNode json = promptBuilder.buildSequenceRequest(taskDescription, concepts, codeSnippet, language,
                                                              targetLanguage, distractorsEnabled, constraints, tierCount);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed.has("tasks")) {
            validateArrayField(parsed.get("tasks"), "tasks");
        } else {
            throw new IOException("GPT response missing required field: 'tasks'");
        }
        logger.debug(parsed.toString());
        return parsed;
    }

    @Override
    public List<String> generateDistractors(String solutionCode, String language,
                                             String taskStatement, int tierLevel,
                                             String distractorPlan, int count)
            throws IOException, InterruptedException {
        logger.debug("Generating {} distractors for tier {} task", count, tierLevel);
        ObjectNode json = promptBuilder.buildDistractorRequest(solutionCode, language, taskStatement,
                                                                tierLevel, distractorPlan, count);
        attachModel(json, defaultModel);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        JsonNode distractorsNode = validateAndGetField(parsed, "distractors");
        validateArrayField(distractorsNode, "distractors");

        List<String> result = new ArrayList<>();
        for (JsonNode node : distractorsNode) {
            String line = node.asText().trim();
            if (!line.isEmpty()) {
                // Ensure each line ends with #distractor
                if (!line.endsWith("#distractor")) {
                    line = line + " #distractor";
                }
                result.add(line);
            }
        }
        return result;
    }

    /**
     * AP31: Generates an inline-split plan for a solved task. Returns the raw split_plan JSON
     * (object with a "splits" array) for downstream validation, or {@code null}.
     */
    @Override
    public String generateSplitPlan(String solutionCode, String language,
                                    String taskStatement, int tierLevel)
            throws IOException, InterruptedException {
        logger.debug("Generating split plan for tier {} task", tierLevel);
        ObjectNode json = promptBuilder.buildSplitPlanRequest(solutionCode, language, taskStatement, tierLevel);
        attachModel(json, defaultModel);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed == null || !parsed.has("splits") || !parsed.get("splits").isArray()) {
            return null;
        }
        return parsed.toString();
    }

    /**
     * AP31: Second pass — generates ONE split-aware fragment distractor. Returns the raw distractor
     * JSON ({@code {"fragmentIndex":..,"text":..,"type":..}}) or {@code null} if the model declines.
     */
    @Override
    public String generateSplitDistractor(List<String> fragments, String prefix, String suffix,
                                          String language, String taskStatement, int tierLevel)
            throws IOException, InterruptedException {
        if (fragments == null || fragments.size() < 2) {
            return null;
        }
        logger.debug("Generating split distractor for tier {} task ({} fragments)", tierLevel, fragments.size());
        ObjectNode json = promptBuilder.buildSplitDistractorRequest(
                fragments, prefix, suffix, language, taskStatement, tierLevel);
        attachModel(json, defaultModel);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        // Accept a FITTING distractor (fragmentIndex >= 0) or a WRONG_SHAPE block (fragmentIndex -1);
        // a blank text is the model's "decline" signal.
        if (parsed == null || !parsed.hasNonNull("text") || parsed.get("text").asText().isBlank()) {
            return null;
        }
        return parsed.toString();
    }

    /**
     * Returns the programming language of the given code snippet,
     * using the default OpenAI model configured in {@code application.yml}.
     *
     * @param codeSnippet the code snippet to be analyzed
     * @return the detected programming language
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    @Override
    public String getProgrammingLanguage(final String codeSnippet)
            throws IOException, InterruptedException {
        return getProgrammingLanguage(codeSnippet, defaultModel);
    }



    /**
     * Returns the programming language of the given code snippet,
     * enforcing the specified {@link OpenAiModel}.
     *
     * <p>This method is identical in behavior to
     * {@link #getProgrammingLanguage(String)}, but instead of
     * relying on the configured default model, it uses the model
     * explicitly provided in the parameters.
     *
     * @param codeSnippet the code snippet to be analyzed
     * @param model       the {@link OpenAiModel} to use for this request
     * @return the detected programming language
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    public String getProgrammingLanguage(final String codeSnippet, final OpenAiModel model)
            throws IOException, InterruptedException {
        logger.trace("Getting programming language for code snippet: {} (model={})", codeSnippet, model);
        ObjectNode json = promptBuilder.buildLanguageDetectionRequest(codeSnippet);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        JsonNode taskNode = validateAndGetField(parsed, "language");
        validateTextField(taskNode, "language");
        return taskNode.asText();
    }


    /**
     * Generates a problem statement fitting the given concepts,
     * using the default OpenAI model configured in {@code application.yml}.
     *
     * @param concepts the list of concepts to be included
     * @param language the desired programming language
     * @return a problem statement that incorporates the given concepts
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    @Override
    public String generateTaskDescription(final List<String> concepts,
                                          final String language)
            throws IOException, InterruptedException {
        return generateTaskDescription(concepts, language, defaultModel);
    }

    /**
     * Generates a problem statement fitting the given concepts,
     * enforcing the specified {@link OpenAiModel}.
     *
     * <p>This method is identical in behavior to
     * {@link #generateTaskDescription(List, String)}, but instead of
     * relying on the configured default model, it uses the model
     * explicitly provided in the parameters.
     *
     * @param concepts the list of concepts to be included
     * @param language the desired programming language
     * @param model    the {@link OpenAiModel} to use for this request
     * @return a problem statement that incorporates the given concepts
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    public String generateTaskDescription(final List<String> concepts,
                                          final String language,
                                          final OpenAiModel model)
            throws IOException, InterruptedException {
        logger.trace("Generating task description for concepts: {} (model={})", concepts, model);
        ObjectNode json = promptBuilder.buildTaskDescriptionRequest(concepts, language);
        attachModel(json, model);
        return prepareRequest(json);
    }


    /**
     * Generates a problem statement fitting the given code snippet,
     * using the default OpenAI model configured in {@code application.yml}.
     *
     * @param codeSnippet the code snippet to be analyzed
     * @return a problem statement fitting the given code snippet
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    @Override
    public String generateTaskDescription(final String codeSnippet)
            throws IOException, InterruptedException {
        return generateTaskDescription(codeSnippet, defaultModel);
    }

    /**
     * Generates a problem statement fitting the given code snippet,
     * enforcing the specified {@link OpenAiModel}.
     *
     * <p>This method is identical in behavior to
     * {@link #generateTaskDescription(String)}, but instead of
     * relying on the configured default model, it uses the model
     * explicitly provided in the parameters.
     *
     * @param codeSnippet the code snippet to be analyzed
     * @param model       the {@link OpenAiModel} to use for this request
     * @return a problem statement fitting the given code snippet
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    public String generateTaskDescription(final String codeSnippet,
                                          final OpenAiModel model)
            throws IOException, InterruptedException {
        logger.trace("Generating task description for code snippet: {} (model={})", codeSnippet, model);
        ObjectNode json = promptBuilder.buildTaskDescriptionRequest(codeSnippet);
        attachModel(json, model);
        return prepareRequest(json);
    }

    // Centralized hook to attach sampling params to the payload
    private static void attachSampling(final ObjectNode json,
                                       final Double temperature,
                                       final Double topP) {
        if (temperature != null) json.put("temperature", temperature);
        if (topP != null)       json.put("top_p",       topP);
    }

// --- Overloads used by the runner (system + user prompt from DB) -----------

    @Override
    public String generateTaskDescription(final String codeSnippet,
                                          final String promptBody)
            throws IOException, InterruptedException {
        // No system prompt; custom user/body prompt only
        ObjectNode json = promptBuilder.buildTaskDescriptionRequest(codeSnippet, null, promptBody);
        attachModel(json, defaultModel);
        attachSampling(json, DEFAULT_TEMPERATURE, DEFAULT_TOP_P);
        return prepareRequest(json);
    }

    @Override
    public String generateTaskDescription(final String codeSnippet,
                                          final String systemPrompt,
                                          final String userPromptBody)
            throws IOException, InterruptedException {
        // System + user messages ({{CODE}} handling happens in the builder)
        ObjectNode json = promptBuilder.buildTaskDescriptionRequest(codeSnippet, systemPrompt, userPromptBody);
        attachModel(json, defaultModel);
        attachSampling(json, DEFAULT_TEMPERATURE, DEFAULT_TOP_P);
        return prepareRequest(json);
    }


    private String prepareRequest(ObjectNode json) throws IOException, InterruptedException {
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        JsonNode taskNode = validateAndGetField(parsed, "task");
        validateTextField(taskNode, "task");
        logger.trace("Task description: {}", taskNode.asText());
        return taskNode.asText();
    }

    /**
     * Generates a list of programming concepts contained in the given code snippet,
     * constrained to the fixed concept catalog (AP25).
     *
     * @param codeSnippet the code snippet to be analyzed
     * @param language    the programming language (e.g. "java", "python")
     * @return a list of normalized concept slugs from the catalog
     * @throws IOException if API communication fails
     * @throws InterruptedException if the request is interrupted
     */
    @Override
    public List<String> generateConcepts(final String codeSnippet, final String language)
            throws IOException, InterruptedException {
        return generateConcepts(codeSnippet, language, defaultModel);
    }

    /**
     * Generates concepts with a specific model override.
     */
    public List<String> generateConcepts(final String codeSnippet, final String language, final OpenAiModel model)
            throws IOException, InterruptedException {

        String catalogPrompt = conceptCatalog.getCatalogForPrompt(language);
        logger.debug("Generating concepts for language={} (model={}, catalog entries={})",
                language, model, catalogPrompt.lines().count());

        ObjectNode json = promptBuilder.buildConceptExtractionRequest(codeSnippet, language, catalogPrompt);
        attachModel(json, model);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        JsonNode conceptsNode = validateAndGetField(parsed, "concepts");
        validateArrayField(conceptsNode, "concepts");

        // Post-processing: normalize and validate against catalog
        List<String> normalized = new ArrayList<>();
        for (JsonNode node : conceptsNode) {
            String raw = node.asText();
            Optional<String> slug = conceptCatalog.normalize(raw);
            if (slug.isPresent()) {
                normalized.add(slug.get());
            } else {
                logger.debug("Discarding non-catalog concept from LLM: '{}'", raw);
            }
        }
        return normalized.stream().distinct().toList();
    }

    /**
     * Maps a given {@link OpenAiModel} enum value to the corresponding
     * model identifier string expected by the OpenAI API.
     *
     * <p>This method provides a central mapping between the application's
     * internal model representation and the actual model IDs used in
     * OpenAI requests (e.g., {@code "gpt-5-mini"}).
     *
     * @param m the {@link OpenAiModel} to be converted
     * @return the OpenAI API model identifier as a string
     * @throws IllegalArgumentException if the provided model is not supported
     */
    private static String toApiModel(OpenAiModel m) {
        if (m == null) throw new IllegalArgumentException("model must not be null");
        return switch (m) {
            case GPT_5      -> "gpt-5";
            case GPT_5_MINI -> "gpt-5-mini";
            case GPT_5_NANO -> "gpt-5-nano";
            case GPT_4_1    -> "gpt-4.1";
        };
    }

    /**
     * Attaches the OpenAI model identifier to a JSON request body.
     *
     * <p>This method adds a {@code "model"} field to the given JSON object,
     * ensuring that the API request explicitly specifies which model to use.
     * The model identifier is resolved from the provided {@link OpenAiModel}
     * using {@link #toApiModel(OpenAiModel)}.
     *
     * @param json  the request body as an {@link ObjectNode}
     * @param model the {@link OpenAiModel} to be attached
     */
    private void attachModel(ObjectNode json, OpenAiModel model) {
        String modelString = toApiModel(model);
        logger.trace("Attaching model: {}", modelString);
        json.put("model", modelString);
    }

    // ── AP19: Curriculum-based generation ──

    /**
     * Generates a tiered task sequence using curriculum context and seed code examples.
     */
    public JsonNode generateCurriculumSequence(
            CurriculumTopicDTO topic,
            List<CurriculumCodeExampleDTO> examples,
            List<String> concepts,
            String language,
            String targetLanguage,
            int tierCount,
            boolean distractorsEnabled,
            GenerationConstraints constraints)
            throws IOException, InterruptedException {

        ObjectNode json = promptBuilder.buildCurriculumSequenceRequest(
                topic, examples, concepts, language, targetLanguage,
                tierCount, distractorsEnabled, constraints);
        attachModel(json, defaultModel);
        HttpResponse<String> response = sendRequest(json);
        JsonNode parsed = parseResponse(response);
        if (parsed.has("tasks")) {
            validateArrayField(parsed.get("tasks"), "tasks");
        } else {
            throw new IOException("GPT response missing required field: 'tasks'");
        }
        logger.debug(parsed.toString());
        return parsed;
    }

}