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
package de.uni_passau.fim.se2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async infrastructure for AP19 Teil A: semantic LLM tagging + admin bulk-retag.
 *
 * <p>Uses virtual threads (JDK 21) — same pattern as {@link de.uni_passau.fim.se2.application.service.TaskEnricher},
 * so blocking HTTP calls to the LLM don't consume platform threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Dedicated executor for tag-related background work.
     * Referenced by {@code @Async("taggingExecutor")}.
     */
    @Bean(name = "taggingExecutor")
    public Executor taggingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Default executor for bare {@code @Async} (no name) — falls back to the same pool. */
    @Override
    public Executor getAsyncExecutor() {
        return taggingExecutor();
    }

    /**
     * Log (never propagate) exceptions from fire-and-forget async methods.
     * Tagging failures must never bubble up into a generation rollback.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                logger.warn("Async {} threw: {}", method.getName(), ex.getMessage(), ex);
    }
}
