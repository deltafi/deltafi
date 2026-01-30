/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PostgresIdentifierValidator {

    private static final int MAX_IDENTIFIER_LENGTH = 63;
    private static final Pattern VALID_IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_]\\w*$");
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "asymmetric",
            "authorization", "binary", "both", "case", "cast", "check", "collate", "collation",
            "column", "concurrently", "constraint", "create", "cross", "current_catalog",
            "current_date", "current_role", "current_schema", "current_time", "current_timestamp",
            "current_user", "default", "deferrable", "desc", "distinct", "do", "else", "end",
            "except", "false", "fetch", "for", "foreign", "freeze", "from", "full", "grant",
            "group", "having", "ilike", "in", "initially", "inner", "intersect", "into", "is",
            "isnull", "join", "lateral", "leading", "left", "like", "limit", "localtime",
            "localtimestamp", "natural", "not", "notnull", "null", "offset", "on", "only", "or",
            "order", "outer", "overlaps", "placing", "primary", "references", "returning", "right",
            "select", "session_user", "similar", "some", "symmetric", "table", "tablesample",
            "then", "to", "trailing", "true", "union", "unique", "user", "using", "variadic",
            "verbose", "when", "where", "window", "with"
    );

    /**
     * Validates a column name and returns all validation errors found.
     *
     * @param columnName the column name to validate
     * @return list of validation errors (empty if valid)
     */
    public static List<String> validateColumnName(String columnName) {
        return validateIdentifier(columnName, "Column name");
    }

    /**
     * Validates a table name and returns all validation errors found.
     *
     * @param tableName the table name to validate
     * @return list of validation errors (empty if valid)
     */
    public static List<String> validateTableName(String tableName) {
        return validateIdentifier(tableName, "Table name");
    }

    /**
     * Core validation logic for any PostgreSQL identifier.
     * Runs all applicable checks and returns all errors found.
     */
    private static List<String> validateIdentifier(String identifier, String identifierType) {
        List<String> errors = new ArrayList<>();

        if (identifier == null || identifier.isBlank()) {
            errors.add("%s cannot be empty. Please provide a non-empty name.".formatted(identifierType));
            return errors; // Can't run other checks on null/blank
        }

        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            errors.add(
                    "%s '%s' is too long (%d characters). PostgreSQL limits identifiers to %d characters. Please shorten the name."
                            .formatted(identifierType, identifier, identifier.length(), MAX_IDENTIFIER_LENGTH)
            );
        }

        if (Character.isDigit(identifier.charAt(0))) {
            errors.add(
                    "%s '%s' cannot start with a digit. Please start with a letter or underscore (e.g., '_%s' or 'col_%s')."
                            .formatted(identifierType, identifier, identifier, identifier)
            );
        }

        if (!VALID_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            String invalidChars = identifier.chars()
                    .filter(c -> !Character.isLetterOrDigit(c) && c != '_')
                    .distinct()
                    .mapToObj(c -> String.valueOf((char) c))
                    .collect(Collectors.joining());

            if (!invalidChars.isEmpty()) {
                errors.add(
                        "%s '%s' contains invalid characters: '%s'. Only letters (a-z, A-Z), digits (0-9), and underscores (_) are allowed. Please rename using only these characters."
                                .formatted(identifierType, identifier, invalidChars)
                );
            }
        }

        if (RESERVED_KEYWORDS.contains(identifier.toLowerCase())) {
            errors.add(
                    "%s '%s' is a PostgreSQL reserved keyword. Please choose a different name (e.g., '%s_col' or '_%s')."
                            .formatted(identifierType, identifier, identifier, identifier)
            );
        }

        return errors;
    }
}