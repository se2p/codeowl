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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.FeedbackLogDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.FeedbackLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FeedbackLogService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackLogService.class);

    private final FeedbackLogRepository feedbackRepo;
    private final ClassRepository classRepository;

    public FeedbackLogService(FeedbackLogRepository feedbackRepository, ClassRepository classRepository) {
        this.feedbackRepo = feedbackRepository;
        this.classRepository = classRepository;
    }

    /**
     * Logs a feedback click with tracking-awareness:
     * resolves the student's class, checks if tracking is enabled,
     * and anonymizes the userId when tracking is off.
     */
    @Transactional
    public void logFeedbackClickWithTracking(UUID taskId, boolean correct, UUID userId) {
        UUID classId = null;
        UUID effectiveUserId = null;

        if (userId != null) {
            classId = classRepository.findClassIdForStudent(userId);
            if (classId != null && classRepository.isTrackingEnabled(classId)) {
                effectiveUserId = userId;
            }
        }

        logFeedbackClick(taskId, correct, effectiveUserId, classId);
    }

    /**
     * Persists a new feedback click event for the given task.
     *
     * @param taskId  the task identifier; must not be {@code null}
     * @param correct {@code true} if the solution was correct at the time of the click
     * @param userId  the user identifier (nullable — NULL if anonymous or tracking disabled)
     * @param classId the class context (nullable)
     */
    @Transactional
    public void logFeedbackClick(UUID taskId, boolean correct, UUID userId, UUID classId) {
        try {
            logger.debug("Logging feedback click for taskId={}, correct={}, userId={}, classId={}",
                    taskId, correct, userId, classId);
            if (taskId == null) {
                throw new DomainValidationException("Task ID must not be null when logging feedback.");
            }

            FeedbackLogDTO logEntry = FeedbackLogDTO.builder()
                    .taskId(taskId)
                    .correct(correct)
                    .userId(userId)
                    .classId(classId)
                    .build();

            feedbackRepo.save(logEntry);
            logger.debug("Saved feedback log for taskId={}, correct={}, userId={}, classId={}",
                    taskId, correct, userId, classId);
        } catch (Exception e) {
            logger.error("Error logging feedback click for taskId={}", taskId, e);
            throw e;
        }
    }

    /**
     * Retrieves all feedback logs for the specified task.
     *
     * @param taskId the task identifier; must not be {@code null}
     * @return a list of feedback log entries sorted by timestamp ascending
     */
    @Transactional(readOnly = true)
    public List<FeedbackLogDTO> getFeedbackLogsForTask(UUID taskId) {
        try {
            logger.debug("Loading feedback logs for taskId={}", taskId);
            if (taskId == null) {
                throw new DomainValidationException("Task ID must not be null.");
            }

            List<FeedbackLogDTO> logs = feedbackRepo.findByTaskId(taskId);
            logger.debug("Loaded {} feedback logs for taskId={}", logs.size(), taskId);
            return logs;
        } catch (Exception e) {
            logger.error("Error loading feedback logs for taskId={}", taskId, e);
            throw e;
        }
    }

    /**
     * Retrieves all feedback logs for the specified class.
     */
    @Transactional(readOnly = true)
    public List<FeedbackLogDTO> getFeedbackLogsForClass(UUID classId) {
        try {
            logger.debug("Loading feedback logs for classId={}", classId);
            if (classId == null) {
                throw new DomainValidationException("Class ID must not be null.");
            }

            List<FeedbackLogDTO> logs = feedbackRepo.findByClassId(classId);
            logger.debug("Loaded {} feedback logs for classId={}", logs.size(), classId);
            return logs;
        } catch (Exception e) {
            logger.error("Error loading feedback logs for classId={}", classId, e);
            throw e;
        }
    }

    /**
     * Deletes all feedback logs associated with a given task.
     *
     * @param taskId the task identifier; must not be {@code null}
     */
    @Transactional
    public void deleteFeedbackLogsForTask(UUID taskId) {
        try {
            logger.debug("Deleting feedback logs for taskId={}", taskId);
            if (taskId == null) {
                throw new DomainValidationException("Task ID must not be null.");
            }

            feedbackRepo.deleteByTaskId(taskId);
            logger.debug("Deleted feedback logs for taskId={}", taskId);
        } catch (Exception e) {
            logger.error("Error deleting feedback logs for taskId={}", taskId, e);
            throw e;
        }
    }
}