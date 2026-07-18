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
package de.uni_passau.fim.se2.application.events;

import de.uni_passau.fim.se2.application.service.SequenceTagService;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;
import de.uni_passau.fim.se2.infrastructure.ai.tagging.LlmSemanticTagger;
import de.uni_passau.fim.se2.infrastructure.ai.tagging.LlmSemanticTagger.SemanticTagResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs the semantic LLM-tagging pass asynchronously after a sequence is generated.
 *
 * <p>Pattern:
 * <ol>
 *   <li>{@link de.uni_passau.fim.se2.application.service.TaskEnricher} (or the persistence service) publishes a
 *       {@link SequenceGeneratedEvent} right after the sequence is committed.</li>
 *   <li>This listener picks it up on the {@code taggingExecutor} virtual-thread pool.</li>
 *   <li>LLM call → {@link SequenceTagService#writeLlmTags(java.util.UUID, java.util.List, java.util.List, Float)}
 *       + semantic summary update.</li>
 * </ol>
 *
 * <p>Any exception is logged and swallowed: the sequence stays untagged and can be
 * recovered later via the admin bulk-retag endpoint.
 */
@Component
public class SemanticTaggingListener {

    private static final Logger logger = LoggerFactory.getLogger(SemanticTaggingListener.class);

    private final LlmSemanticTagger tagger;
    private final SequenceTagService tagService;
    private final TieredTaskSequenceRepository sequenceRepo;

    public SemanticTaggingListener(LlmSemanticTagger tagger,
                                   SequenceTagService tagService,
                                   TieredTaskSequenceRepository sequenceRepo) {
        this.tagger = tagger;
        this.tagService = tagService;
        this.sequenceRepo = sequenceRepo;
    }

    @Async("taggingExecutor")
    @EventListener
    public void onSequenceGenerated(SequenceGeneratedEvent event) {
        MDC.put("sequenceId", event.sequenceId().toString());
        long t0 = System.nanoTime();
        try {
            SemanticTagResult result = tagger.tag(
                    event.programmingLanguage(),
                    event.combinedStatement(),
                    event.combinedCode());

            if (result == null) {
                logger.debug("Semantic tagging returned no result for sequence {}", event.sequenceId());
                return;
            }

            tagService.writeLlmTags(event.sequenceId(), result.primaryTags(), result.aliases());

            if (result.shortDescription() != null && !result.shortDescription().isBlank()) {
                try {
                    sequenceRepo.updateSemanticSummary(event.sequenceId(), result.shortDescription());
                } catch (Exception e) {
                    logger.warn("Could not update semantic_summary for sequence {}: {}",
                            event.sequenceId(), e.getMessage());
                }
            }

            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            logger.debug("Semantic tagging for sequence {} finished in {}ms ({} primary, {} alias)",
                    event.sequenceId(), elapsedMs,
                    result.primaryTags() == null ? 0 : result.primaryTags().size(),
                    result.aliases() == null ? 0 : result.aliases().size());

        } catch (Exception e) {
            logger.warn("Semantic tagging failed for sequence {}: {}", event.sequenceId(), e.getMessage());
        } finally {
            MDC.remove("sequenceId");
        }
    }
}
