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
package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import de.uni_passau.fim.se2.domain.interfaces.DatabaseDumpExecutor;
import de.uni_passau.fim.se2.domain.interfaces.DatabaseDumpExecutor.DumpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates full database backups via pg_dump.
 * A single-flight semaphore prevents concurrent backups from stressing the DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final DatabaseDumpExecutor dumpExecutor;
    private final AdminAuditService auditService;

    /** Only ONE pg_dump may run at a time. New requests wait up to 30 s, then get a 503. */
    private static final Semaphore BACKUP_LOCK = new Semaphore(1, true);
    private static final long LOCK_TIMEOUT_SECONDS = 30;

    public static class BackupBusyException extends RuntimeException {
        public BackupBusyException() { super("Another backup is already in progress."); }
    }

    /**
     * Streams a full pg_dump to the given output stream.
     * Acquires the single-flight lock first; throws {@link BackupBusyException} after 30 s wait.
     *
     * @return bytes written to output
     * @throws BackupBusyException if another backup is running and the wait timed out
     * @throws DumpException       on pg_dump failure
     */
    public long streamFullBackup(OutputStream output, UUID adminId, String ip, String userAgent)
            throws DumpException, InterruptedException {

        if (!BACKUP_LOCK.tryAcquire(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    "BackupBusy: another backup in progress");
            throw new BackupBusyException();
        }
        try {
            log.info("Full backup started by admin={}", adminId);
            long bytes = dumpExecutor.dump(output);
            auditService.logSuccess(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    bytes, null);
            log.info("Full backup finished: {} bytes, admin={}", bytes, adminId);
            return bytes;
        } catch (DumpException e) {
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.FULL_BACKUP, ip, userAgent,
                    e.getMessage());
            throw e;
        } finally {
            BACKUP_LOCK.release();
        }
    }
}
