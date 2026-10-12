#
# Copyright (C) 2026 CodeOwl contributors
#
# This file is part of CodeOwl.
#
# CodeOwl is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or (at
# your option) any later version.
#
# CodeOwl is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
#

# ===== Build stage (Debian-based, includes JDK) =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Prime the dependency cache
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Build the app
COPY src ./src
RUN mvn -q -Dmaven.test.skip=true package spring-boot:repackage

# ===== Runtime stage (Debian/Ubuntu JDK – needed for javac at runtime) =====
FROM eclipse-temurin:21-jdk
WORKDIR /app

# ── AP26: musl compatibility for tree-sitter native library ──
# The java-tree-sitter JNI lib is compiled against musl libc.
RUN apt-get update && \
    apt-get install -y --no-install-recommends musl && \
    ln -sf /usr/lib/x86_64-linux-musl/libc.so /lib/libc.musl-x86_64.so.1 && \
    rm -rf /var/lib/apt/lists/*

# ── AP13: Install Ruff (Python linter for hint system) ──
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip pipx && \
    pipx install ruff && \
    ln -s /root/.local/bin/ruff /usr/local/bin/ruff && \
    rm -rf /var/lib/apt/lists/*

# ── Admin DB backup: Install postgresql-client for pg_dump ──
# Uses the official PGDG apt repo to ensure the client matches Postgres 17 on the server.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl gnupg lsb-release && \
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /usr/share/keyrings/postgresql.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
        > /etc/apt/sources.list.d/pgdg.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends postgresql-client-17 && \
    rm -rf /var/lib/apt/lists/*

# ── AP13: Install SpotBugs (Java bytecode analyzer for hint system) ──
RUN curl -L -o /tmp/spotbugs.tgz \
        https://github.com/spotbugs/spotbugs/releases/download/4.8.6/spotbugs-4.8.6.tgz && \
    tar -xzf /tmp/spotbugs.tgz -C /opt && \
    ln -s /opt/spotbugs-4.8.6/lib/spotbugs.jar /opt/spotbugs.jar && \
    rm /tmp/spotbugs.tgz

# Copy fat jar
COPY --from=build /app/target/*.jar /app/app.jar

# Container-aware JVM memory settings (21 auto-detects cgroups)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
