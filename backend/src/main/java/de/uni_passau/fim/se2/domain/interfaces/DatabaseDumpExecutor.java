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
package de.uni_passau.fim.se2.domain.interfaces;

import java.io.OutputStream;

/**
 * Executes a full database dump and writes the binary output to the given stream.
 * Implementation may invoke an external tool (e.g. pg_dump).
 */
public interface DatabaseDumpExecutor {
    /**
     * @param output stream to write the dump bytes to (e.g. HTTP response stream)
     * @return number of bytes written
     * @throws DumpException if the dump fails for any reason
     */
    long dump(OutputStream output) throws DumpException;

    class DumpException extends Exception {
        public DumpException(String msg) { super(msg); }
        public DumpException(String msg, Throwable cause) { super(msg, cause); }
    }
}
