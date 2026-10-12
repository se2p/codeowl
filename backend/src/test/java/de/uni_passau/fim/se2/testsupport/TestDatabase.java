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
package de.uni_passau.fim.se2.testsupport;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The one real Postgres the {@code *DatabaseTest} classes run against, migrated by the same
 * Flyway chain the backend applies at startup.
 *
 * <p>Where that database comes from depends on where the tests run, and only on that:
 *
 * <ul>
 *   <li><b>CI:</b> {@code SPRING_DATASOURCE_URL} and friends point at a throwaway {@code
 *       services: postgres} container that GitLab starts beside the job and destroys with it.
 *       Same variable names the application itself reads, so there is no test-only spelling to
 *       keep in sync.
 *   <li><b>A developer machine:</b> nothing is set, so Testcontainers starts one instead.
 * </ul>
 *
 * <p>The GitLab service exists because the shared runner is unprivileged: a {@code docker:dind}
 * service cannot start on it, so Testcontainers has no daemon to talk to and these tests used to
 * skip in CI entirely. Handing the tests a database rather than letting them build one is what
 * gets them back into the pipeline.
 *
 * <p>Both sources give a <em>shared</em> database — one per JVM here, one per job in CI — rather
 * than one container per test class. Migrating is the expensive part and it now happens once.
 * The price is that test classes see each other's rows, so each one clears the tables it cares
 * about before it starts rather than assuming it inherited an empty schema.
 */
public final class TestDatabase {

    /** Matches the image the deployment runs; {@code unaccent} (V19) needs contrib, which it has. */
    private static final String IMAGE = "postgres:17-alpine";

    private static DataSource dataSource;
    private static boolean resolved;

    private TestDatabase() {
    }

    /**
     * Whether there is a database to test against at all — an injected one, or a Docker daemon to
     * start one with. False on a developer machine with Docker down, where the database tests
     * abort rather than fail.
     */
    public static synchronized boolean isAvailable() {
        return dataSource() != null;
    }

    /**
     * A {@link JdbcTemplate} on the migrated database. Call only after {@link #isAvailable()} has
     * confirmed there is one.
     */
    public static synchronized JdbcTemplate jdbc() {
        DataSource ds = dataSource();
        if (ds == null) {
            throw new IllegalStateException(
                    "No test database; guard the call with TestDatabase.isAvailable()");
        }
        return new JdbcTemplate(ds);
    }

    private static DataSource dataSource() {
        if (!resolved) {
            resolved = true;
            dataSource = connect();
        }
        return dataSource;
    }

    private static DataSource connect() {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        if (url != null && !url.isBlank()) {
            return migrate(url,
                    orDefault(System.getenv("SPRING_DATASOURCE_USERNAME"), "postgres"),
                    orDefault(System.getenv("SPRING_DATASOURCE_PASSWORD"), "postgres"));
        }
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return null;
        }
        @SuppressWarnings("resource") // torn down with the JVM by Testcontainers' reaper
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("codeowl")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();
        return migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static DataSource migrate(String url, String username, String password) {
        // Same migrations, same way the backend applies them -- default location
        // (classpath:db/migration), nothing to configure. The database is bare either way, so
        // this always runs the full V1...latest chain rather than baselining.
        Flyway.configure()
                .dataSource(url, username, password)
                // See application.yml's spring.flyway.placeholder-replacement: V1 is a pg_dump
                // containing a literal "${function_label}" in the data, which Flyway's
                // placeholder scan otherwise trips over.
                .placeholderReplacement(false)
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(url, username, password);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
