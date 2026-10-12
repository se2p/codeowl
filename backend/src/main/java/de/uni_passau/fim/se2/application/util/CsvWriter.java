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
package de.uni_passau.fim.se2.application.util;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer.
 * <ul>
 *   <li>UTF-8 encoded (no BOM)</li>
 *   <li>Field separator: comma</li>
 *   <li>Quoting: double-quotes when field contains comma, quote, or newline</li>
 *   <li>Quote escaping: doubled quote ("")</li>
 * </ul>
 *
 * <strong>Important:</strong> {@link #close()} does NOT close the underlying stream.
 * The caller (typically a ZipOutputStream) controls its own lifecycle.
 */
public class CsvWriter implements AutoCloseable {

    private final PrintWriter out;
    private boolean firstFieldOnLine = true;

    public CsvWriter(OutputStream stream) {
        this.out = new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
    }

    public void writeRow(List<?> fields) {
        for (Object f : fields) writeField(f);
        endRow();
    }

    public void writeField(Object value) {
        if (!firstFieldOnLine) out.print(',');
        firstFieldOnLine = false;
        if (value == null) return; // empty field
        String s = value.toString();
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            out.print('"');
            out.print(s.replace("\"", "\"\""));
            out.print('"');
        } else {
            out.print(s);
        }
    }

    public void endRow() {
        out.print("\r\n");
        firstFieldOnLine = true;
    }

    public void flush() {
        out.flush();
    }

    /** Flushes but does NOT close the underlying stream. */
    @Override
    public void close() {
        out.flush();
    }
}
