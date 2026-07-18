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

import de.uni_passau.fim.se2.domain.entities.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Responsible for constructing structured GPT request payloads, including
 * appropriate system and user messages and strict JSON response validation
 * schemas.
 */
@Component
public class PromptBuilder {

    /**
     * Object mapper used for constructing prompts.
     */
    private final ObjectMapper mapper;

    /**
     * An instance of {@link IdentityLoader} used to load identity prompt resources required for constructing GPT
     * requests.
     */
    private final IdentityLoader identityLoader;

    /**
     * Constructs a new PromptBuilder instance.
     *
     * @param objectMapper the objectMapper used for building prompts.
     */
    public PromptBuilder(final ObjectMapper objectMapper, final IdentityLoader identityLoader) {
        this.mapper = objectMapper;
        this.identityLoader = identityLoader;
    }

    /**
     * Constructs a GPT request to generate a tiered task sequence based on
     * task description, concepts, reference code, and target language.
     *
     * @param taskDescription a plain English description of the task
     * @param concepts        the core programming concepts involved
     * @param codeSnippet     teacher-provided code snippet
     * @param language        programming language to use
     * @return GPT-compliant JSON request object
     */
    public ObjectNode buildSequenceRequest(final String taskDescription, final List<String> concepts,
                                           final String codeSnippet, final String language) {
        return buildSequenceRequest(taskDescription, concepts, codeSnippet, language, null);
    }

    public ObjectNode buildSequenceRequest(final String taskDescription, final List<String> concepts,
                                           final String codeSnippet, final String language,
                                           final String targetLanguage) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("Sequence")));

        // Resolve human language label for the prompt instruction
        String langLabel;
        if ("en".equalsIgnoreCase(targetLanguage)) {
            langLabel = "English";
        } else {
            langLabel = "German";   // default
        }

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String prompt = """
            Generate exactly 3 tiered programming tasks (tiers 1–3) for the inputs below.
            Increase complexity across tiers. Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put ‘none’.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks’ intended solutions must consist of a single function only, not full programs.
            %s""".formatted(taskDescription, language, String.join(", ", concepts), codeSnippet,
                            targetLanguageInstruction);

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", prompt));

        json.set("messages", messages);
        json.set("response_format", responseFormatTieredContracts());
        return json;
    }

    /**
     * AP20: Builds a sequence request with generation constraints injected into the prompt (3-tier).
     */
    public ObjectNode buildSequenceRequest(final String taskDescription, final List<String> concepts,
                                           final String codeSnippet, final String language,
                                           final String targetLanguage, final boolean distractorsEnabled,
                                           final GenerationConstraints constraints) {
        return buildSequenceRequest(taskDescription, concepts, codeSnippet, language,
                                    targetLanguage, distractorsEnabled, constraints, 3);
    }

    /**
     * AP21: Full overload — tier-count-aware sequence request with constraints + distractors.
     * This is the terminal overload that all other overloads ultimately delegate to for 5-tier.
     */
    public ObjectNode buildSequenceRequest(final String taskDescription, final List<String> concepts,
                                           final String codeSnippet, final String language,
                                           final String targetLanguage, final boolean distractorsEnabled,
                                           final GenerationConstraints constraints, final int tierCount) {
        if (tierCount <= 3) {
            // Standard 3-tier path — delegate to existing distractor-aware overload + patch constraints
            ObjectNode json = buildSequenceRequest(taskDescription, concepts, codeSnippet, language,
                                                    targetLanguage, distractorsEnabled);
            String constraintSection = buildConstraintSection(constraints, 3);
            if (!constraintSection.isEmpty()) {
                var messages = (ArrayNode) json.get("messages");
                var userMsg = (ObjectNode) messages.get(messages.size() - 1);
                userMsg.put("content", userMsg.get("content").asText() + constraintSection);
            }
            return json;
        }

        // ── 5-Tier path: dedicated prompt + schema ──
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System: dedicated 5-tier identity prompt
        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("Sequence5Tier")));

        String langLabel = "en".equalsIgnoreCase(targetLanguage) ? "English" : "German";

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String distractorInstruction = "";
        if (distractorsEnabled) {
            distractorInstruction = """

            DISTRACTOR PLANNING:
            For each task, include a "distractorPlan" object with:
            - "type": one of LOGIC_FLIP, OFF_BY_ONE, VARIABLE_MISMATCH, BOUNDARY_IDENTITY, STRUCTURAL_ILLOGICAL, REDUNDANT_ZOMBIE
            - "rationale": a short sentence explaining why this error type fits the task and tier
            Match distractor difficulty to tier level:
            - Tier 1–2: obvious errors (LOGIC_FLIP, STRUCTURAL_ILLOGICAL)
            - Tier 3: detail errors (OFF_BY_ONE, VARIABLE_MISMATCH)
            - Tier 4: subtle traps (BOUNDARY_IDENTITY, REDUNDANT_ZOMBIE)
            - Tier 5: most deceptive type for the code structure
            """;
        }

        String prompt = """
            Generate exactly %d tiered programming tasks (tiers 1–%d) for the inputs below.
            Follow the 5-tier progression model from your instructions:
              Tier 1 = Recognition, Tier 2 = Single Construct, Tier 3 = Combination,
              Tier 4 = Nested Complexity, Tier 5 = Synthesis.
            Each tier must be qualitatively different from the previous — not just longer.
            Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put 'none'.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks' intended solutions must consist of a single function only, not full programs.
            %s%s""".formatted(tierCount, tierCount,
                              taskDescription, language, String.join(", ", concepts), codeSnippet,
                              targetLanguageInstruction, distractorInstruction);

        // Append constraint section if any
        String constraintSection = buildConstraintSection(constraints, tierCount);
        String finalPrompt = constraintSection.isEmpty() ? prompt : prompt + constraintSection;

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", finalPrompt));

        json.set("messages", messages);
        json.set("response_format", distractorsEnabled
                ? responseFormatTieredContractsWithDistractors(tierCount)
                : responseFormatTieredContracts(tierCount));
        return json;
    }

    /**
     * AP20: Builds the constraint section to inject into generation prompts.
     */
    String buildConstraintSection(final GenerationConstraints constraints) {
        return buildConstraintSection(constraints, 3);
    }

    /**
     * AP20+AP21: Builds the constraint section, tier-count-aware for correct difficulty slope text.
     */
    String buildConstraintSection(final GenerationConstraints constraints, final int tierCount) {
        if (constraints == null || !constraints.hasAny()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n    GENERATION CONSTRAINTS (AP20):\n");

        if (constraints.getBlacklist() != null && !constraints.getBlacklist().isEmpty()) {
            sb.append("    BLACKLIST — The generated code MUST NOT use any of these constructs:\n");
            sb.append("    ").append(constraints.getBlacklist().stream()
                    .map(c -> "\"" + c + "\"")
                    .collect(Collectors.joining(", ")));
            sb.append("\n    If any blacklisted construct appears in the solution, the task is INVALID.\n");
        }

        if (constraints.getWhitelist() != null && !constraints.getWhitelist().isEmpty()) {
            sb.append("    WHITELIST — The generated code may ONLY use these constructs:\n");
            sb.append("    ").append(constraints.getWhitelist().stream()
                    .map(c -> "\"" + c + "\"")
                    .collect(Collectors.joining(", ")));
            sb.append("\n    No other programming constructs beyond basic syntax are allowed.\n");
        }

        if (constraints.getDifficultySlope() != null && !constraints.getDifficultySlope().isBlank()) {
            sb.append("    DIFFICULTY SLOPE: ").append(constraints.getDifficultySlope()).append("\n");
            if (tierCount == 5) {
                switch (constraints.getDifficultySlope().toLowerCase()) {
                    case "gentle" -> sb.append("    → Tiers 1–5 should progress very gently. Even Tier 5 should be approachable for students.\n");
                    case "steep" -> sb.append("    → Tiers 1–2 trivial, Tier 3 moderate, Tiers 4–5 significantly challenging.\n");
                    default -> sb.append("    → Standard moderate difficulty progression across all 5 tiers.\n");
                }
            } else {
                switch (constraints.getDifficultySlope().toLowerCase()) {
                    case "gentle" -> sb.append("    → Tiers 1-3 should have very gradual complexity increase. Tier 3 should still be approachable.\n");
                    case "steep" -> sb.append("    → Tier 1 should be trivial, Tier 3 should be significantly challenging.\n");
                    default -> sb.append("    → Standard moderate difficulty progression across tiers.\n");
                }
            }
        }

        if (constraints.getAdditionalContext() != null && !constraints.getAdditionalContext().isBlank()) {
            sb.append("    TEACHER INSTRUCTIONS:\n    ").append(constraints.getAdditionalContext()).append("\n");
        }

        return sb.toString();
    }

    public ObjectNode buildSequenceRequest(final String taskDescription, final List<String> concepts,
                                           final String codeSnippet, final String language,
                                           final String targetLanguage, final boolean distractorsEnabled) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("Sequence")));

        String langLabel;
        if ("en".equalsIgnoreCase(targetLanguage)) {
            langLabel = "English";
        } else {
            langLabel = "German";
        }

        String targetLanguageInstruction = """

            IMPORTANT — Human Language Requirement:
            All student-facing text (statement, contract.taskStatement, contract.purposeBehavior,
            contract.prePostConditions) MUST be written in %s.
            Code, function signatures, variable names, and concept names remain in English.
            """.formatted(langLabel);

        String distractorInstruction = "";
        if (distractorsEnabled) {
            distractorInstruction = """

            DISTRACTOR PLANNING:
            For each task, include a "distractorPlan" object with:
            - "type": one of LOGIC_FLIP, OFF_BY_ONE, VARIABLE_MISMATCH, BOUNDARY_IDENTITY, STRUCTURAL_ILLOGICAL, REDUNDANT_ZOMBIE
            - "rationale": a short sentence explaining why this error type fits the task and tier
            Match distractor difficulty to tier level (Tier 1 = obvious, Tier 3 = subtle).
            """;
        }

        String prompt = """
            Generate exactly 3 tiered programming tasks (tiers 1–3) for the inputs below.
            Increase complexity across tiers. Statements must be student-facing (one sentence).

            Inputs:
            - Task Description: %s
            - Programming Language: %s
            - Programming Concepts: %s
            - Reference Code Snippet:
            %s

            Output (STRICT):
            Return ONLY JSON matching the provided response schema.
            For each task:
            - Provide: tier, statement, and contract.
            - contract must include ALL fields: language, taskStatement, functionSignature,
              purposeBehavior, prePostConditions, examples[1..2] with {input,output}.
            - statement MUST equal contract.taskStatement exactly.
            Examples must be concrete minimal invocation/result pairs appropriate to the functionSignature.
            No placeholders, no empty strings for any field. If a field cannot be filled logically put 'none'.

            Use the reference snippet only as conceptual context, not as a structural template.
            The generated tasks' intended solutions must consist of a single function only, not full programs.
            %s%s""".formatted(taskDescription, language, String.join(", ", concepts), codeSnippet,
                            targetLanguageInstruction, distractorInstruction);

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", prompt));

        json.set("messages", messages);
        json.set("response_format", distractorsEnabled ? responseFormatTieredContractsWithDistractors() : responseFormatTieredContracts());
        return json;
    }

    // AP21: Tier-count-aware overloads
    private ObjectNode responseFormatTieredContracts(int tierCount) {
        if (tierCount <= 3) return responseFormatTieredContracts();
        return buildTieredContractSchema(tierCount, false);
    }

    private ObjectNode responseFormatTieredContractsWithDistractors(int tierCount) {
        if (tierCount <= 3) return responseFormatTieredContractsWithDistractors();
        return buildTieredContractSchema(tierCount, true);
    }

    /**
     * AP21: Builds a dynamic JSON response schema for N tiers, with optional distractor plan.
     */
    private ObjectNode buildTieredContractSchema(int tierCount, boolean withDistractors) {
        ObjectNode rf = mapper.createObjectNode().put("type", "json_schema");
        ObjectNode js = mapper.createObjectNode();
        js.put("name", withDistractors
                ? "tiered_contracts_with_distractors_response"
                : "tiered_contracts_response");
        js.put("strict", true);

        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = mapper.createObjectNode();

        ObjectNode tasks = mapper.createObjectNode();
        tasks.put("type", "array");
        tasks.put("minItems", tierCount);
        tasks.put("maxItems", tierCount);

        ObjectNode taskItem = mapper.createObjectNode().put("type", "object");
        ObjectNode taskProps = mapper.createObjectNode();

        ObjectNode tier = mapper.createObjectNode();
        tier.put("type", "integer");
        tier.put("minimum", 1);
        tier.put("maximum", tierCount);
        taskProps.set("tier", tier);
        taskProps.set("statement", str());

        // contract
        ObjectNode contract = mapper.createObjectNode().put("type", "object");
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set("language", str());
        cProps.set("taskStatement", str());
        cProps.set("functionSignature", str());
        cProps.set("purposeBehavior", str());
        cProps.set("prePostConditions", str());
        ObjectNode examples = mapper.createObjectNode();
        examples.put("type", "array");
        examples.put("minItems", 1);
        examples.put("maxItems", 2);
        ObjectNode exItem = mapper.createObjectNode().put("type", "object");
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set("input", str());
        exProps.set("output", str());
        exItem.set("properties", exProps);
        exItem.putArray("required").add("input").add("output");
        exItem.put("additionalProperties", false);
        examples.set("items", exItem);
        cProps.set("examples", examples);
        contract.set("properties", cProps);
        contract.putArray("required")
                .add("language").add("taskStatement").add("functionSignature")
                .add("purposeBehavior").add("prePostConditions").add("examples");
        contract.put("additionalProperties", false);
        taskProps.set("contract", contract);

        ArrayNode required = taskItem.putArray("required");
        required.add("tier").add("statement").add("contract");

        if (withDistractors) {
            ObjectNode distractorPlan = mapper.createObjectNode().put("type", "object");
            ObjectNode dpProps = mapper.createObjectNode();
            dpProps.set("type", str());
            dpProps.set("rationale", str());
            distractorPlan.set("properties", dpProps);
            distractorPlan.putArray("required").add("type").add("rationale");
            distractorPlan.put("additionalProperties", false);
            taskProps.set("distractorPlan", distractorPlan);
            required.add("distractorPlan");
        }

        taskItem.set("properties", taskProps);
        taskItem.put("additionalProperties", false);
        tasks.set("items", taskItem);
        props.set("tasks", tasks);

        schema.set("properties", props);
        schema.putArray("required").add("tasks");
        schema.put("additionalProperties", false);
        js.set("schema", schema);
        rf.set("json_schema", js);
        return rf;
    }

    private ObjectNode responseFormatTieredContracts() {
        ObjectNode rf = mapper.createObjectNode().put("type", "json_schema");
        ObjectNode js = mapper.createObjectNode();
        js.put("name", "tiered_contracts_response");
        js.put("strict", true);

        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = mapper.createObjectNode();

        // tasks: array[3] of task objects
        ObjectNode tasks = mapper.createObjectNode();
        tasks.put("type", "array");
        tasks.put("minItems", 3);
        tasks.put("maxItems", 3);

        // task item
        ObjectNode taskItem = mapper.createObjectNode().put("type", "object");
        ObjectNode taskProps = mapper.createObjectNode();

        // tier
        ObjectNode tier = mapper.createObjectNode();
        tier.put("type", "integer");
        tier.put("minimum", 1);
        tier.put("maximum", 3);
        taskProps.set("tier", tier);

        // statement (should equal contract.taskStatement)
        ObjectNode statement = mapper.createObjectNode().put("type", "string");
        taskProps.set("statement", statement);

        // contract
        ObjectNode contract = mapper.createObjectNode().put("type", "object");
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set("language", str());
        cProps.set("taskStatement", str());
        cProps.set("functionSignature", str());
        cProps.set("purposeBehavior", str());
        cProps.set("prePostConditions", str());

        // examples: array of 1..2 {input, output}
        ObjectNode examples = mapper.createObjectNode();
        examples.put("type", "array");
        examples.put("minItems", 1);
        examples.put("maxItems", 2);

        ObjectNode exItem = mapper.createObjectNode().put("type", "object");
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set("input", str());
        exProps.set("output", str());
        exItem.set("properties", exProps);
        exItem.putArray("required").add("input").add("output");
        exItem.put("additionalProperties", false);

        examples.set("items", exItem);
        cProps.set("examples", examples);

        contract.set("properties", cProps);
        contract.putArray("required")
                .add("language")
                .add("taskStatement")
                .add("functionSignature")
                .add("purposeBehavior")
                .add("prePostConditions")
                .add("examples");
        contract.put("additionalProperties", false);

        taskProps.set("contract", contract);

        taskItem.set("properties", taskProps);
        taskItem.putArray("required").add("tier").add("statement").add("contract");
        taskItem.put("additionalProperties", false);

        tasks.set("items", taskItem);
        props.set("tasks", tasks);

        schema.set("properties", props);
        schema.putArray("required").add("tasks");
        schema.put("additionalProperties", false);

        js.set("schema", schema);
        rf.set("json_schema", js);
        return rf;
    }

    private ObjectNode responseFormatTieredContractsWithDistractors() {
        ObjectNode rf = mapper.createObjectNode().put("type", "json_schema");
        ObjectNode js = mapper.createObjectNode();
        js.put("name", "tiered_contracts_with_distractors_response");
        js.put("strict", true);

        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = mapper.createObjectNode();

        ObjectNode tasks = mapper.createObjectNode();
        tasks.put("type", "array");
        tasks.put("minItems", 3);
        tasks.put("maxItems", 3);

        ObjectNode taskItem = mapper.createObjectNode().put("type", "object");
        ObjectNode taskProps = mapper.createObjectNode();

        ObjectNode tier = mapper.createObjectNode();
        tier.put("type", "integer");
        tier.put("minimum", 1);
        tier.put("maximum", 3);
        taskProps.set("tier", tier);

        taskProps.set("statement", str());

        // contract (same as original)
        ObjectNode contract = mapper.createObjectNode().put("type", "object");
        ObjectNode cProps = mapper.createObjectNode();
        cProps.set("language", str());
        cProps.set("taskStatement", str());
        cProps.set("functionSignature", str());
        cProps.set("purposeBehavior", str());
        cProps.set("prePostConditions", str());

        ObjectNode examples = mapper.createObjectNode();
        examples.put("type", "array");
        examples.put("minItems", 1);
        examples.put("maxItems", 2);

        ObjectNode exItem = mapper.createObjectNode().put("type", "object");
        ObjectNode exProps = mapper.createObjectNode();
        exProps.set("input", str());
        exProps.set("output", str());
        exItem.set("properties", exProps);
        exItem.putArray("required").add("input").add("output");
        exItem.put("additionalProperties", false);

        examples.set("items", exItem);
        cProps.set("examples", examples);

        contract.set("properties", cProps);
        contract.putArray("required")
                .add("language")
                .add("taskStatement")
                .add("functionSignature")
                .add("purposeBehavior")
                .add("prePostConditions")
                .add("examples");
        contract.put("additionalProperties", false);

        taskProps.set("contract", contract);

        // distractorPlan
        ObjectNode distractorPlan = mapper.createObjectNode().put("type", "object");
        ObjectNode dpProps = mapper.createObjectNode();
        dpProps.set("type", str());
        dpProps.set("rationale", str());
        distractorPlan.set("properties", dpProps);
        distractorPlan.putArray("required").add("type").add("rationale");
        distractorPlan.put("additionalProperties", false);
        taskProps.set("distractorPlan", distractorPlan);

        taskItem.set("properties", taskProps);
        taskItem.putArray("required").add("tier").add("statement").add("contract").add("distractorPlan");
        taskItem.put("additionalProperties", false);

        tasks.set("items", taskItem);
        props.set("tasks", tasks);

        schema.set("properties", props);
        schema.putArray("required").add("tasks");
        schema.put("additionalProperties", false);

        js.set("schema", schema);
        rf.set("json_schema", js);
        return rf;
    }

    private ObjectNode str() {
        return mapper.createObjectNode().put("type", "string");
    }

    /**
     * Constructs a GPT request to solve a programming task and return only
     * code.
     *
     * @param contract SolveContract composing the input for the request
     * @return structured GPT request JSON
     */
    public ObjectNode buildSolveRequest(final SolveContract contract) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // SYSTEM: jetzt klar auf JSON { "code": "..." } festnageln
        final String systemPrompt =
                "You are CodeOwl (Solver).\n"
                        + "Return only valid JSON matching the response schema: a single string field \"code\".\n"
                        + "No markdown/backticks, no extra keys, no text outside JSON.\n\n"
                        + "Rules:\n"
                        + "- Put the entire solution as the string value of \"code\" (escape as needed).\n"
                        + "- Single file; use only the standard library.\n"
                        + "- No file/network I/O unless shown in the examples.\n"
                        + "- Obey the specified language and any provided function signature.\n"
                        + "- Implement exactly what the spec and examples require; avoid adding features.\n"
                        + "- Prefer clear, minimal, idiomatic code.";

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", systemPrompt));

        // USER: CONTRACT wie gehabt
        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", composeContract(contract)));

        json.set("messages", messages);

        // WICHTIG: wieder JSON-Schema für {"code": "..."} erzwingen
        json.set("response_format", responseFormatSolverResponse());  // <— statt "text"

        return json;
    }
    private static String composeContract(SolveContract c) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTRACT\n");
        sb.append("Language: ").append(c.language()).append('\n');

        sb.append("Task statement (student-facing, one sentence): ")
                .append(c.taskStatement()).append('\n');

        sb.append("Function signature: ").append(c.functionSignature()).append('\n');

        sb.append("Spec:\n\"\"\"\n")
                .append(sanitizeTripleQuotes(c.purposeBehavior())).append('\n')
                .append(sanitizeTripleQuotes(c.prePostConditions())).append('\n')
                .append("\"\"\"\n");

        sb.append("Examples:\nInput -> Output:\n");
        for (SolveContract.ExamplePair ex : c.examples()) {
            sb.append(ex.input()).append(" -> ").append(ex.output()).append('\n');
        }

        sb.append("\nGOAL\n")
                .append("Write the final solution that matches the spec and examples exactly.");
        return sb.toString();
    }

    private static String sanitizeTripleQuotes(String s) {
        if (s == null) {
            return "";
        }
        // Avoid accidental closing of the docstring fence
        return s.replace("\"\"\"", "\\\"\\\"\\\"");
    }


    /**
     * Constructs a GPT request for extracting programming concepts from a code snippet
     * using the fixed concept catalog (AP25).
     *
     * @param codeSnippet code to analyze
     * @param language    programming language (e.g. "java", "python", "javascript")
     * @param catalogPrompt pre-formatted catalog string (slug — name: description) filtered for language
     * @return GPT request JSON with strict response schema
     */
    public ObjectNode buildConceptExtractionRequest(final String codeSnippet,
                                                    final String language,
                                                    final String catalogPrompt) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("ConceptExtraction")));

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", """
                                     Programming language: %s

                                     Available concept catalog (only use slugs from this list, filtered for %s):
                                     %s

                                     ---

                                     Code snippet:
                                     ```%s
                                     %s
                                     ```

                                     ---

                                     Task:
                                     Identify the programming concepts that are directly and explicitly present in the code snippet above.

                                     Requirements:
                                     - Return between 1 and 5 concept slugs.
                                     - Use ONLY slugs from the catalog above.
                                     - Only include concepts actually visible in the code — not assumed or implied.
                                     - Do not return concepts specific to a different programming language.
                                     - Output valid JSON using exactly this schema: {"concepts": ["slug1", "slug2"]}"""
                                     .formatted(language, language, catalogPrompt, language, codeSnippet)));

        json.set("messages", messages);
        json.set("response_format", responseFormatConceptList());
        return json;
    }

    /**
     * Builds a chat.completions request using an explicit system prompt and user prompt body.
     * If the user prompt contains the token "{{CODE}}", it will be replaced with the given codeSnippet.
     * If "{{CODE}}" is absent, the code will be appended as a "Code:" block at the end.
     * The response is constrained to a JSON object with a single "task" field via response_format.
     */
    public ObjectNode buildTaskDescriptionRequest(final String codeSnippet,
                                                  final String systemPrompt,
                                                  final String userPromptBody) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System message: use provided systemPrompt if non-empty; otherwise keep it empty (or you could fall back to an identity if you want)
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(mapper.createObjectNode()
                                 .put("role", "system")
                                 .put("content", systemPrompt));
        }

        // Resolve {{CODE}} placeholder if present; else append code block
        String resolvedBody;
        if (userPromptBody != null && userPromptBody.contains("{{CODE}}")) {
            resolvedBody = userPromptBody.replace("{{CODE}}", codeSnippet);
        } else {
            String safeBody = userPromptBody == null ? "" : userPromptBody;
            resolvedBody = safeBody + "\n\nCode:\n" + codeSnippet;
        }

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", resolvedBody));

        json.set("messages", messages);
        json.set("response_format", responseFormatTask()); // enforce {"task": "..."}
        return json;
    }


    /**
     * Constructs a GPT request to generate a task description from a code
     * snippet.
     *
     * @param codeSnippet the code to summarize
     * @return GPT request JSON with strict task field response
     */
    public ObjectNode buildTaskDescriptionRequest(final String codeSnippet) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("TaskDescription")));

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", "Given the following code, generate"
                                     + " a concise and clear problem statement for it. "
                                     + "Respond in JSON format only.\nCode:\n"
                                     + codeSnippet));

        json.set("messages", messages);
        json.set("response_format", responseFormatTask());
        return json;
    }

    /**
     * Constructs a GPT request to generate a task from a list of concepts in a
     * given language.
     *
     * @param concepts list of programming concepts
     * @param language target programming language
     * @return GPT request JSON with strict task field
     */
    public ObjectNode buildTaskDescriptionRequest(final List<String> concepts,
                                                  final String language) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", "You are an expert in designing"
                                     + " programming exercises for students."));

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", "Generate a single concise"
                                     + " programming task written in " + language
                                     + " that emphasizes the following concepts: "
                                     + String.join(", ", concepts)
                                     + ".\nRespond with only a JSON object like: "
                                     + "{\"task\": \"...\"}"));

        json.set("messages", messages);
        json.set("response_format", responseFormatTask());
        return json;
    }

    /**
     * Constructs a GPT request to detect the programming language used in a
     * code snippet.
     *
     * @param codeSnippet code whose language should be identified
     * @return GPT request JSON
     */
    public ObjectNode buildLanguageDetectionRequest(final String codeSnippet) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", identityLoader.load("LanguageDetection")));

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", "Detect the programming language"
                                     + " used in the following code. Respond only in JSON "
                                     + "format.\nCode:\n" + codeSnippet));

        json.set("messages", messages);
        json.set("response_format", responseFormatLanguage());
        return json;
    }

    // === Response Format Helpers ===

    private ObjectNode responseFormatTieredResponse() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "problem_statements_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode problemStatements = mapper.createObjectNode();
        problemStatements.put("type", "array");

        ObjectNode items = mapper.createObjectNode();
        items.put("type", "string");
        problemStatements.set("items", items);

        properties.set("problem_statements", problemStatements);
        schema.set("properties", properties);
        schema.putArray("required").add("problem_statements");
        schema.put("additionalProperties", false);

        jsonSchema.set("schema", schema);
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }

    public ObjectNode buildSimpleSolveRequest(final String taskDescription, final String language) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System: strikt JSON mit "code"
        final String systemPrompt =
                "You are CodeOwl (Simple Solver).\n"
                        + "Return only valid JSON with a single string field \"code\".\n"
                        + "No markdown/backticks, no explanations, no extra keys, no text outside JSON.\n\n"
                        + "Rules:\n"
                        + "- Put the entire solution as the string value of \"code\" (escape as needed).\n"
                        + "- Single file; use only the standard library.\n"
                        + "- No file/network I/O unless the task explicitly requires it.\n"
                        + "- Obey the specified language.\n"
                        + "- Prefer clear, minimal, idiomatic code.";

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", systemPrompt));

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", composeSimpleSolveContract(taskDescription, language)));

        json.set("messages", messages);
        json.set("response_format", responseFormatSolverResponse()); // erzwinge {"code": "..."}
        return json;
    }

    /**
     * Constructs a GPT request to generate distractor lines for a Parsons problem (AP15).
     * The "Antagonist" prompt takes a correct solution and a distractor plan,
     * then generates plausible but incorrect code lines.
     *
     * @param solutionCode  the correct solution code
     * @param language      programming language
     * @param taskStatement the task description
     * @param tierLevel     difficulty tier (1-3)
     * @param distractorPlan JSON string with type and rationale, or null
     * @param count         number of distractors to generate
     * @return GPT-compliant JSON request object
     */
    public ObjectNode buildDistractorRequest(final String solutionCode,
                                              final String language,
                                              final String taskStatement,
                                              final int tierLevel,
                                              final String distractorPlan,
                                              final int count) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Distractor Expert). Your goal is to generate plausible but \
            incorrect code lines (distractors) for Parsons problems.

            A Parsons problem requires students to arrange code blocks. Distractors are \
            "trap" lines that look correct but introduce logical, boundary, or syntax errors.

            RULES:
            1. Generate distractors highly similar in style and variable naming to the solution.
            2. Each distractor must be a SINGLE line or a small coherent block (max 2 lines).
            3. Every distractor MUST end with the suffix " #distractor".
            4. Do NOT use #swap or #group markers on distractor lines.
            5. Ensure distractors are functionally incorrect for the given task.
            6. The distractors must be valid syntax for the given programming language.
            7. Focus on common student misconceptions matching the requested taxonomy type.
            8. Match the indentation level as if the distractor replaced an existing line.

            TAXONOMY TYPES:
            - LOGIC_FLIP: Invert operators (<→>, ==→!=, &&→||)
            - OFF_BY_ONE: Wrong loop bounds or range limits
            - VARIABLE_MISMATCH: Use existing but wrong variable in expression
            - BOUNDARY_IDENTITY: Wrong initial values or base cases
            - STRUCTURAL_ILLOGICAL: Code illegal in context (break outside loop, etc.)
            - REDUNDANT_ZOMBIE: Plausible but unnecessary/harmful extra lines""";

        messages.add(mapper.createObjectNode()
                             .put("role", "system")
                             .put("content", systemPrompt));

        String strategySection = "";
        if (distractorPlan != null && !distractorPlan.isBlank()) {
            strategySection = "\nDISTRACTOR STRATEGY:\n" + distractorPlan + "\n";
        }

        String userPrompt = """
            TASK CONTEXT:
            Language: %s
            Task: %s
            Tier: %d
            Correct Solution:
            \"\"\"
            %s
            \"\"\"
            %s
            GOAL:
            Generate %d distractors for this task.

            OUTPUT:
            Return ONLY a JSON object with a "distractors" array containing the distractor strings.
            Each string must end with " #distractor".""".formatted(
                language, taskStatement, tierLevel, solutionCode, strategySection, count);

        messages.add(mapper.createObjectNode()
                             .put("role", "user")
                             .put("content", userPrompt));

        json.set("messages", messages);
        json.set("response_format", responseFormatDistractors());
        return json;
    }

    private ObjectNode responseFormatDistractors() {
        ObjectNode rf = mapper.createObjectNode().put("type", "json_schema");
        ObjectNode js = mapper.createObjectNode();
        js.put("name", "distractor_response");
        js.put("strict", true);

        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode properties = mapper.createObjectNode();

        ObjectNode distractors = mapper.createObjectNode();
        distractors.put("type", "array");
        ObjectNode items = mapper.createObjectNode().put("type", "string");
        distractors.set("items", items);
        properties.set("distractors", distractors);

        schema.set("properties", properties);
        schema.putArray("required").add("distractors");
        schema.put("additionalProperties", false);

        js.set("schema", schema);
        rf.set("json_schema", js);
        return rf;
    }

    /**
     * AP31: Builds the request for inline-split generation. Given a solved task, asks the model
     * to pick ONE syntactically complex line and decompose it into 2–4 draggable fragments that
     * are EXACT contiguous substrings (so prefix + concat(fragments) + suffix reconstruct the
     * line verbatim), optionally with an OFF_BY_ONE fragment distractor.
     */
    public ObjectNode buildSplitPlanRequest(final String solutionCode,
                                            final String language,
                                            final String taskStatement,
                                            final int tierLevel) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Split Expert). For Parsons problems you decompose ONE syntactically \
            complex line (e.g. a for-loop header or a compound condition) into 2–4 inline fragments \
            that students re-order in slots.

            HARD RULES:
            1. Pick at most ONE line — the most instructive complex line. If none is suitable, return {"splits": []}.
            2. lineIndex is the 0-based index of that line in the solution EXACTLY as given below \
               (split on newlines; count every line including braces).
            3. fragments must be 2–4 EXACT, contiguous substrings of the line body. Concatenating \
               prefix + fragments (in order, no separator) + suffix MUST reproduce the line \
               character-for-character, including all spaces. Preserve leading indentation inside prefix.
            4. prefix/suffix are fixed, non-draggable display text (may be empty strings).
            5. Do NOT add any distractors here — split distractors are generated in a separate, \
               split-aware pass. Emit fragments/prefix/suffix only.
            6. Do NOT include gap markers, #distractor, #swap or #group.""";

        messages.add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt));

        String userPrompt = """
            TASK CONTEXT:
            Language: %s
            Task: %s
            Tier: %d
            Solution (lineIndex = 0-based line number):
            \"\"\"
            %s
            \"\"\"

            OUTPUT:
            Return ONLY a JSON object of this exact shape:
            {"splits":[{"lineIndex":<int>,"fragments":["...","..."],"prefix":"...","suffix":"..."}]}
            Use an empty array {"splits": []} if no line is suitable.""".formatted(
                language, taskStatement, tierLevel, solutionCode);

        messages.add(mapper.createObjectNode().put("role", "user").put("content", userPrompt));

        json.set("messages", messages);
        json.set("response_format", mapper.createObjectNode().put("type", "json_object"));
        return json;
    }

    /**
     * AP31 (second pass): asks for ONE fragment-level distractor that fits a single slot of an
     * already-decided split. The model sees the exact fragments, so its wrong fragment matches one
     * slot's shape instead of spanning the whole line.
     */
    public ObjectNode buildSplitDistractorRequest(final List<String> fragments,
                                                  final String prefix, final String suffix,
                                                  final String language, final String taskStatement,
                                                  final int tierLevel) {
        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        String systemPrompt = """
            You are CodeOwl (Split-Distractor Expert). A Parsons line was ALREADY split into ordered \
            fragments (each fragment is one draggable slot). Produce ONE wrong chip a student might \
            plausibly reach for. Choose ONE of two kinds:

            A) FITTING (harder): a drop-in alternative for EXACTLY ONE fragment — same role and similar \
               shape/length, so it competes for that single slot. Set fragmentIndex to its 0-based \
               position. type in {OFF_BY_ONE (< vs <=, bounds), LOGIC_FLIP (&&/||, ==/!=), \
               VARIABLE_MISMATCH (wrong but existing identifier), BOUNDARY_IDENTITY (wrong init/base value)}.

            B) WRONG_SHAPE (easier): a plausible-looking block that spans several fragments or reshapes \
               the line, so it obviously cannot fit the per-slot structure once the slots are visible. \
               Set type = WRONG_SHAPE and fragmentIndex = -1.

            RULES:
            1. Either kind must be genuinely WRONG (changes behaviour), not a cosmetic re-spelling of a correct fragment.
            2. Difficulty guide: lower tiers suit WRONG_SHAPE (easier, obvious); higher tiers suit a \
               FITTING distractor (harder). Guidance only — pick whatever is most instructive here.
            3. If nothing instructive applies, decline by returning {"text": ""}.
            4. Output JSON only — no #distractor/#swap/#group, no markdown.""";

        messages.add(mapper.createObjectNode().put("role", "system").put("content", systemPrompt));

        StringBuilder fragList = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            fragList.append("[").append(i).append("] \"").append(fragments.get(i)).append("\"\n");
        }

        String userPrompt = """
            Language: %s
            Task: %s
            Tier: %d
            Fixed prefix (not draggable): "%s"
            Fixed suffix (not draggable): "%s"
            Fragments (0-based; each is one slot):
            %s
            Return ONLY: {"fragmentIndex":<0-based int, or -1 for WRONG_SHAPE>,"text":"<wrong chip>","type":"<TYPE>"}
            Decline with {"text": ""} if nothing instructive applies.""".formatted(
                language, taskStatement, tierLevel,
                prefix == null ? "" : prefix, suffix == null ? "" : suffix, fragList.toString());

        messages.add(mapper.createObjectNode().put("role", "user").put("content", userPrompt));

        json.set("messages", messages);
        json.set("response_format", mapper.createObjectNode().put("type", "json_object"));
        return json;
    }

    private static String composeSimpleSolveContract(final String task, final String language) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONTRACT (simple)\n");
        sb.append("Language: ").append(language).append('\n');
        sb.append("Task statement (one sentence): ").append(task).append('\n');
        sb.append("\nGOAL\nWrite the final solution that matches the task exactly.");
        return sb.toString();
    }

    private ObjectNode responseFormatSolverResponse() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "code_solution_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode code = mapper.createObjectNode();
        code.put("type", "string");
        properties.set("code", code);

        schema.set("properties", properties);
        schema.putArray("required").add("code");
        schema.put("additionalProperties", false);

        jsonSchema.set("schema", schema);
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatConceptList() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "concept_list_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode concepts = mapper.createObjectNode();
        concepts.put("type", "array");
        ObjectNode items = mapper.createObjectNode();
        items.put("type", "string");
        concepts.set("items", items);

        properties.set("concepts", concepts);
        schema.set("properties", properties);
        schema.putArray("required").add("concepts");
        schema.put("additionalProperties", false);

        jsonSchema.set("schema", schema);
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatTask() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "sample_task_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode task = mapper.createObjectNode();
        task.put("type", "string");
        properties.set("task", task);

        schema.set("properties", properties);
        schema.putArray("required").add("task");
        schema.put("additionalProperties", false);

        jsonSchema.set("schema", schema);
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }

    private ObjectNode responseFormatLanguage() {
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");

        ObjectNode jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "language_detection_response");
        jsonSchema.put("strict", true);

        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode language = mapper.createObjectNode();
        language.put("type", "string");
        properties.set("language", language);

        schema.set("properties", properties);
        schema.putArray("required").add("language");
        schema.put("additionalProperties", false);

        jsonSchema.set("schema", schema);
        responseFormat.set("json_schema", jsonSchema);
        return responseFormat;
    }

    // ════════════════════════════════════════════════════════════
    // AP19: Curriculum-based generation
    // ════════════════════════════════════════════════════════════

    /**
     * Builds a curriculum-based sequence generation request with seed code examples.
     *
     * @param topic         the curriculum topic (with grade, track, learning area)
     * @param examples      curated code examples for this topic
     * @param concepts      programming concepts to exercise
     * @param language       programming language
     * @param targetLanguage "de" or "en"
     * @param tierCount      3 or 5
     * @param distractorsEnabled whether to include distractor plans
     * @param constraints   optional AP20 constraints
     */
    public ObjectNode buildCurriculumSequenceRequest(
            final CurriculumTopicDTO topic,
            final List<CurriculumCodeExampleDTO> examples,
            final List<String> concepts,
            final String language,
            final String targetLanguage,
            final int tierCount,
            final boolean distractorsEnabled,
            final GenerationConstraints constraints) {

        ObjectNode json = mapper.createObjectNode();
        ArrayNode messages = mapper.createArrayNode();

        // System prompt: dedicated curriculum identity
        messages.add(mapper.createObjectNode()
                         .put("role", "system")
                         .put("content", identityLoader.load("SequenceCurriculum")));

        // Build seed examples block (AP32: few-shot). A topic without curated examples is
        // rejected upstream (CurriculumGenerationService throws), so the list is non-empty here.
        StringBuilder seedBlock = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            var ex = examples.get(i);
            seedBlock.append("--- Example %d: \"%s\" ---\n".formatted(i + 1, ex.getTitle()));
            seedBlock.append("Language: %s\n".formatted(ex.getLanguage()));
            if (ex.getDifficulty() != null && !ex.getDifficulty().isBlank()) {
                seedBlock.append("Difficulty: %s\n".formatted(ex.getDifficulty()));
            }
            seedBlock.append("Code:\n%s\n".formatted(ex.getCode()));
            if (ex.getProblemStatement() != null && !ex.getProblemStatement().isBlank()) {
                seedBlock.append("Description: %s\n".formatted(ex.getProblemStatement()));
            }
            if (ex.getNotes() != null && !ex.getNotes().isBlank()) {
                seedBlock.append("Didactic notes from the teacher: %s\n".formatted(ex.getNotes()));
            }
            seedBlock.append("\n");
        }

        String langLabel = "en".equalsIgnoreCase(targetLanguage) ? "English" : "German";
        String track = topic.getTrack() != null ? topic.getTrack() : "NTG";
        String learningArea = topic.getLearningArea() != null ? topic.getLearningArea() : "";

        String distractorLine = distractorsEnabled
                ? "Include distractor plans."
                : "Do NOT include distractor plans.";

        String prompt = """
            Generate a %d-tier Parsons puzzle task sequence for the following curriculum context.

            ── CURRICULUM CONTEXT ──
            Topic: %s
            Grade: Jahrgangsstufe %d
            Track: %s
            Learning Area: %s
            Programming Language: %s
            Target Concepts: %s
            Task Language: %s

            ── SEED CODE EXAMPLES ──
            Use these curated examples as stylistic and conceptual anchors. Build related tasks — do not copy them.

            %s
            %s

            Output (STRICT): For each task, the top-level "statement" MUST equal contract.taskStatement exactly (byte-for-byte identical) — write the student-facing statement, then copy it verbatim into taskStatement.

            Generate %d tasks (tiers 1–%d) as specified. %s
            """.formatted(
                tierCount,
                topic.getName(),
                topic.getGradeLevel() != null ? topic.getGradeLevel() : 9,
                track,
                learningArea,
                language,
                String.join(", ", concepts),
                langLabel,
                seedBlock.toString(),
                buildConstraintSection(constraints, tierCount),
                tierCount, tierCount,
                distractorLine);

        messages.add(mapper.createObjectNode()
                         .put("role", "user")
                         .put("content", prompt));

        json.set("messages", messages);
        json.set("response_format", distractorsEnabled
                ? responseFormatTieredContractsWithDistractors(tierCount)
                : responseFormatTieredContracts(tierCount));
        return json;
    }
}