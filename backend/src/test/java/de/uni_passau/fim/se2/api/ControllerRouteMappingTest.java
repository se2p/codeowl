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
package de.uni_passau.fim.se2.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which controller answers which URL.
 *
 * <p>Written when the worksheet endpoints moved out of {@code TasksController}. Moving a
 * {@code @RequestMapping} has two failure modes that compile perfectly well: a path can go
 * missing, and two controllers can end up claiming the same one — which Spring only notices
 * when the application starts, in production, with a stack trace nobody wanted at that moment.
 *
 * <p>The check builds the same {@link RequestMappingHandlerMapping} Spring builds at startup
 * and lets it register the controllers, so an ambiguous mapping fails here instead. The
 * controllers are constructed with {@code null} dependencies on purpose: registration reads
 * annotations and never calls a method, so there is nothing to stand in for — and it keeps this
 * test free of mocking a concrete class, which current JVMs no longer allow.
 */
@DisplayName("controller route mapping")
class ControllerRouteMappingTest {

    private static Map<RequestMappingInfo, java.lang.reflect.Method> handlers;
    private static RequestMappingHandlerMapping mapping;

    @BeforeAll
    static void registerControllers() {
        StaticWebApplicationContext context = new StaticWebApplicationContext();
        context.registerBean("worksheetController", WorksheetController.class,
                () -> new WorksheetController(null, null, null, null, null));
        context.registerBean("tasksController", TasksController.class,
                () -> new TasksController(null, null, null, null, null, null, null, null, null, null));
        context.registerBean("adminResearchController", AdminResearchController.class,
                () -> new AdminResearchController(null, null, null, null));
        context.refresh();

        mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(context);
        // Registration is where Spring raises IllegalStateException on a duplicate mapping, so
        // the ambiguity check is this line rather than an assertion below it.
        mapping.afterPropertiesSet();

        handlers = mapping.getHandlerMethods().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getMethod()));
    }

    @Test
    @DisplayName("every worksheet endpoint is answered, and by the worksheet controller")
    void worksheetEndpointsAreMappedToTheWorksheetController() {
        List<String> expected = List.of(
                "GET /api/worksheets/my",
                "GET /api/worksheets/{sheetId}",
                "PUT /api/worksheets/{sheetId}",
                "DELETE /api/worksheets/{sheetId}",
                "POST /api/worksheets",
                "PUT /api/worksheets/{sheetId}/visibility",
                "POST /api/worksheets/{sheetId}/copy",
                "POST /api/worksheets/{sheetId}/sequences",
                "POST /api/worksheets/{sheetId}/sequences/{sequenceId}",
                "DELETE /api/worksheets/{sheetId}/sequences/{sequenceId}");

        Set<String> actual = routes();
        assertAll(expected.stream().map(route -> () ->
                assertTrue(actual.contains(route), "not mapped: " + route)));
    }

    @Test
    @DisplayName("the task controller no longer answers for worksheets")
    void tasksControllerHasNoWorksheetRoutes() {
        List<String> stragglers = handlers.entrySet().stream()
                .filter(e -> e.getValue().getDeclaringClass().equals(TasksController.class))
                .map(e -> String.valueOf(e.getKey()))
                .filter(route -> route.contains("/worksheets"))
                .toList();

        assertEquals(List.of(), stragglers,
                "a worksheet endpoint was left behind, so the move is half-done");
    }

    @Test
    @DisplayName("the endpoints the task controller does keep are still there")
    void tasksControllerKeepsItsOwn() {
        // The move is only correct if it took nothing else with it.
        Set<String> actual = routes();
        assertAll(
                () -> assertTrue(actual.contains("POST /api/tasks/generate")),
                () -> assertTrue(actual.contains("GET /api/sequence/{id}")),
                () -> assertTrue(actual.contains("GET /api/tasks/{taskId}")),
                () -> assertTrue(actual.contains("PUT /api/tasks/{taskId}")),
                () -> assertTrue(actual.contains("GET /api/sequences/my")));
    }

    @Test
    @DisplayName("the research page's routes are all mapped, download paths included")
    void researchRoutesAreMapped() {
        // /export.zip and /{dataset} overlap: the literal has to win, or the ZIP download is
        // handed to the dataset handler, which rejects "export.zip" as an unknown dataset.
        // Registration above is what would fail on a genuine ambiguity; this pins the set.
        Set<String> actual = routes();
        assertAll(
                () -> assertTrue(actual.contains("GET /api/admin/research/{dataset}")),
                () -> assertTrue(actual.contains("GET /api/admin/research/{dataset}/summary")),
                () -> assertTrue(actual.contains("GET /api/admin/research/{dataset}/export.csv")),
                () -> assertTrue(actual.contains("GET /api/admin/research/export.zip")));
    }

    /** Each mapping as "METHOD /path", the form a reader can compare against a URL. */
    private static Set<String> routes() {
        return handlers.keySet().stream()
                .flatMap(info -> {
                    Set<String> patterns = info.getPatternValues();
                    Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                            info.getMethodsCondition().getMethods();
                    return patterns.stream().flatMap(pattern ->
                            methods.stream().map(method -> method + " " + pattern));
                })
                .collect(Collectors.toSet());
    }
}
