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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.interfaces.DatabaseDumpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Invokes {@code pg_dump} via ProcessBuilder and pipes its stdout to the given stream.
 * Requires {@code postgresql-client} to be installed in the backend container.
 *
 * <p>Connection parameters are derived from the Spring datasource URL at startup,
 * so there is a single source of truth for DB credentials.
 */
@Component
@Slf4j
public class PgDumpExecutor implements DatabaseDumpExecutor {

    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public PgDumpExecutor(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String dbUser,
            @Value("${spring.datasource.password}") String dbPassword) {
        // Parse jdbc:postgresql://host:port/dbname
        String stripped = jdbcUrl.replaceFirst("^jdbc:", "");
        URI uri = URI.create(stripped);
        this.dbHost = uri.getHost();
        this.dbPort = String.valueOf(uri.getPort() > 0 ? uri.getPort() : 5432);
        String path = uri.getPath();
        this.dbName = (path != null && path.startsWith("/")) ? path.substring(1) : path;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        log.info("PgDumpExecutor initialized: host={} port={} db={} user={}",
                this.dbHost, this.dbPort, this.dbName, this.dbUser);
    }

    @Override
    public long dump(OutputStream output) throws DumpException {
        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump",
                "-h", dbHost,
                "-p", dbPort,
                "-U", dbUser,
                "-d", dbName,
                "-Fc",             // custom format (binary, compressed) — use pg_restore to restore
                "--no-owner",
                "--no-privileges",
                "--no-acl"
        );
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(false); // keep stderr separate so we can log it

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new DumpException("Failed to start pg_dump (is postgresql-client installed?)", e);
        }

        // Drain stderr in a daemon thread so it never blocks the main stream copy
        Thread stderrLogger = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                byte[] buf = new byte[4096];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = err.read(buf)) != -1) sb.append(new String(buf, 0, n));
                String stderr = sb.toString().trim();
                if (!stderr.isEmpty()) log.warn("pg_dump stderr: {}", stderr);
            } catch (IOException ignored) {}
        });
        stderrLogger.setDaemon(true);
        stderrLogger.start();

        long bytesWritten = 0;
        try (InputStream in = process.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                output.write(buf, 0, n);
                bytesWritten += n;
            }
            output.flush();
        } catch (IOException e) {
            process.destroy();
            throw new DumpException("Error streaming pg_dump output", e);
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DumpException("pg_dump interrupted", e);
        }

        if (exitCode != 0) {
            throw new DumpException("pg_dump exited with code " + exitCode);
        }

        log.info("pg_dump completed successfully: {} bytes", bytesWritten);
        return bytesWritten;
    }
}
