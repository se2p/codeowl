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

import org.springframework.stereotype.*;

import java.util.regex.Pattern;

@Service
public class CodeSanitizerService {

    public String removeComments(String code, String language) {
        if (code == null) return "";

        String regex = regexByLanguage(language);

        if (regex.isEmpty()) return code;

        return Pattern.compile(regex, Pattern.MULTILINE)
                .matcher(code)
                .replaceAll("")
                .trim();
    }

    private String regexByLanguage(String language) {
        String regex;
        switch (language.toLowerCase()) {
            case "java", "c", "cpp", "c#", "javascript" ->
                    regex = "(?s)//.*?$|/\\*.*?\\*/";
            case "python" ->
                    regex = "(?m)#.*$";
            case "sql" ->
                    regex = "(?s)--.*?$|/\\*.*?\\*/";
            case "html" ->
                    regex = "(?s)<!--.*?-->";
            default ->
                    regex = "";
        }
        return regex;
    }

    public boolean hasComments(String code, String language) {
        if (code == null) return false;

        String regex = regexByLanguage(language);
        return !regex.isEmpty() && Pattern.compile(regex, Pattern.MULTILINE).matcher(code).find();
    }
}