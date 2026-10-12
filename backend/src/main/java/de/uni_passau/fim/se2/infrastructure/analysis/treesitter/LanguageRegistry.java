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
package de.uni_passau.fim.se2.infrastructure.analysis.treesitter;

import ch.usi.si.seart.treesitter.Language;

import java.util.*;

/**
 * AP26 — Maps user-facing language names to tree-sitter {@link Language} enums
 * and provides node-type mappings for each language so that a single traversal
 * algorithm can compute metrics across all supported languages.
 */
public final class LanguageRegistry {

    private LanguageRegistry() {}

    // ── Language resolution ──

    private static final Map<String, Language> LANGUAGE_MAP = new HashMap<>();

    static {
        LANGUAGE_MAP.put("java", Language.JAVA);
        LANGUAGE_MAP.put("python", Language.PYTHON);
        LANGUAGE_MAP.put("javascript", Language.JAVASCRIPT);
        LANGUAGE_MAP.put("js", Language.JAVASCRIPT);
        LANGUAGE_MAP.put("typescript", Language.TYPESCRIPT);
        LANGUAGE_MAP.put("ts", Language.TYPESCRIPT);
        LANGUAGE_MAP.put("c", Language.C);
        LANGUAGE_MAP.put("c++", Language.CPP);
        LANGUAGE_MAP.put("cpp", Language.CPP);
        LANGUAGE_MAP.put("c#", Language.C_SHARP);
        LANGUAGE_MAP.put("csharp", Language.C_SHARP);
        LANGUAGE_MAP.put("cs", Language.C_SHARP);
        LANGUAGE_MAP.put("rust", Language.RUST);
        LANGUAGE_MAP.put("go", Language.GO);
        LANGUAGE_MAP.put("golang", Language.GO);
    }

    public static Optional<Language> resolve(String language) {
        if (language == null) return Optional.empty();
        return Optional.ofNullable(LANGUAGE_MAP.get(language.toLowerCase().trim()));
    }

    public static boolean isSupported(String language) {
        return resolve(language).isPresent();
    }

    // ── Node-type classifications per language ──
    // Each set tells the traversal which AST node types count towards a metric.

    public record LanguageConfig(
            Set<String> functionTypes,
            Set<String> loopTypes,
            Set<String> conditionalTypes,
            Set<String> branchingTypes,   // CC: adds +1 per occurrence
            Set<String> nestingTypes,     // MND: tracked for nesting depth
            Set<String> callTypes,        // MCC: method/function call expressions
            Set<String> identifierTypes,  // DIC: distinct identifier names
            Set<String> catchTypes        // CC: catch clauses
    ) {}

    private static final Map<String, LanguageConfig> CONFIGS = new HashMap<>();

    static {
        // ── Java ──
        CONFIGS.put("java", new LanguageConfig(
                Set.of("method_declaration", "constructor_declaration"),
                Set.of("for_statement", "enhanced_for_statement", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_expression"),
                Set.of("if_statement", "for_statement", "enhanced_for_statement", "while_statement",
                        "do_statement", "switch_expression", "case_clause"),
                Set.of("if_statement", "for_statement", "enhanced_for_statement", "while_statement",
                        "do_statement", "switch_expression", "try_statement", "synchronized_statement"),
                Set.of("method_invocation"),
                Set.of("identifier"),
                Set.of("catch_clause")
        ));

        // ── Python ──
        CONFIGS.put("python", new LanguageConfig(
                Set.of("function_definition"),
                Set.of("for_statement", "while_statement"),
                Set.of("if_statement", "elif_clause"),
                Set.of("if_statement", "elif_clause", "for_statement", "while_statement",
                        "conditional_expression"),
                Set.of("if_statement", "for_statement", "while_statement", "try_statement", "with_statement"),
                Set.of("call"),
                Set.of("identifier"),
                Set.of("except_clause")
        ));

        // ── JavaScript ──
        CONFIGS.put("javascript", new LanguageConfig(
                Set.of("function_declaration", "method_definition", "arrow_function"),
                Set.of("for_statement", "for_in_statement", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_statement"),
                Set.of("if_statement", "for_statement", "for_in_statement", "while_statement",
                        "do_statement", "switch_case", "ternary_expression"),
                Set.of("if_statement", "for_statement", "for_in_statement", "while_statement",
                        "do_statement", "switch_statement", "try_statement"),
                Set.of("call_expression"),
                Set.of("identifier"),
                Set.of("catch_clause")
        ));

        // ── TypeScript (extends JS) ──
        CONFIGS.put("typescript", new LanguageConfig(
                Set.of("function_declaration", "method_definition", "arrow_function"),
                Set.of("for_statement", "for_in_statement", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_statement"),
                Set.of("if_statement", "for_statement", "for_in_statement", "while_statement",
                        "do_statement", "switch_case", "ternary_expression"),
                Set.of("if_statement", "for_statement", "for_in_statement", "while_statement",
                        "do_statement", "switch_statement", "try_statement"),
                Set.of("call_expression"),
                Set.of("identifier", "type_identifier"),
                Set.of("catch_clause")
        ));

        // ── C ──
        CONFIGS.put("c", new LanguageConfig(
                Set.of("function_definition"),
                Set.of("for_statement", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_statement"),
                Set.of("if_statement", "for_statement", "while_statement", "do_statement", "case_statement"),
                Set.of("if_statement", "for_statement", "while_statement", "do_statement", "switch_statement"),
                Set.of("call_expression"),
                Set.of("identifier"),
                Set.of()
        ));

        // ── C++ ──
        CONFIGS.put("cpp", new LanguageConfig(
                Set.of("function_definition"),
                Set.of("for_statement", "for_range_loop", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_statement"),
                Set.of("if_statement", "for_statement", "for_range_loop", "while_statement",
                        "do_statement", "case_statement"),
                Set.of("if_statement", "for_statement", "for_range_loop", "while_statement",
                        "do_statement", "switch_statement", "try_statement"),
                Set.of("call_expression"),
                Set.of("identifier"),
                Set.of("catch_clause")
        ));

        // ── C# ──
        CONFIGS.put("csharp", new LanguageConfig(
                Set.of("method_declaration", "constructor_declaration", "local_function_statement"),
                Set.of("for_statement", "for_each_statement", "while_statement", "do_statement"),
                Set.of("if_statement", "switch_statement"),
                Set.of("if_statement", "for_statement", "for_each_statement", "while_statement",
                        "do_statement", "switch_section"),
                Set.of("if_statement", "for_statement", "for_each_statement", "while_statement",
                        "do_statement", "switch_statement", "try_statement"),
                Set.of("invocation_expression"),
                Set.of("identifier"),
                Set.of("catch_clause")
        ));

        // ── Rust ──
        CONFIGS.put("rust", new LanguageConfig(
                Set.of("function_item"),
                Set.of("for_expression", "while_expression", "loop_expression"),
                Set.of("if_expression", "match_expression"),
                Set.of("if_expression", "for_expression", "while_expression", "loop_expression",
                        "match_arm"),
                Set.of("if_expression", "for_expression", "while_expression", "loop_expression",
                        "match_expression", "unsafe_block"),
                Set.of("call_expression"),
                Set.of("identifier"),
                Set.of()
        ));

        // ── Go ──
        CONFIGS.put("go", new LanguageConfig(
                Set.of("function_declaration", "method_declaration"),
                Set.of("for_statement"),
                Set.of("if_statement", "expression_switch_statement", "type_switch_statement"),
                Set.of("if_statement", "for_statement", "expression_case"),
                Set.of("if_statement", "for_statement", "expression_switch_statement",
                        "type_switch_statement", "select_statement"),
                Set.of("call_expression"),
                Set.of("identifier"),
                Set.of()
        ));

        // aliases
        CONFIGS.put("js", CONFIGS.get("javascript"));
        CONFIGS.put("ts", CONFIGS.get("typescript"));
        CONFIGS.put("c++", CONFIGS.get("cpp"));
        CONFIGS.put("c#", CONFIGS.get("csharp"));
        CONFIGS.put("cs", CONFIGS.get("csharp"));
        CONFIGS.put("golang", CONFIGS.get("go"));
    }

    public static LanguageConfig getConfig(String language) {
        return CONFIGS.get(language.toLowerCase().trim());
    }
}
