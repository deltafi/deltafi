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
package org.deltafi.common.lookup;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Represents a request to query a lookup table by matching column values.
 */
@Data
@NoArgsConstructor
public class LookupTableEvent {
    /** Key prefix used to build lookup table event identifiers. */
    public static final String PREFIX = "lookup-table-event";

    private static long nextId = 0;

    private String id;
    private String lookupTableName;
    private Map<String, Set<String>> matchingColumnValues;
    private List<String> resultColumns;
    private Map<String, String> variables;

    /**
     * Builds a key for the given lookup table name.
     * @param lookupTableName the name of the lookup table
     * @return the prefixed key string
     */
    public static String buildKey(String lookupTableName) {
        return String.join("-", PREFIX, lookupTableName);
    }

    /**
     * Creates a lookup table event with a generated ID.
     * @param uniqueId a unique identifier used as part of the event ID
     * @param lookupTableName the name of the lookup table to query
     * @param matchingColumnValues column names mapped to their required matching values
     * @param resultColumns the columns to include in the result
     * @param variables variable substitutions for the query
     */
    public LookupTableEvent(String uniqueId, String lookupTableName, Map<String, Set<String>> matchingColumnValues,
            List<String> resultColumns, Map<String, String> variables) {
        this.id = String.join("-", PREFIX, uniqueId, Long.toString(nextId++));
        this.lookupTableName = lookupTableName;
        this.matchingColumnValues = matchingColumnValues;
        this.resultColumns = resultColumns;
        this.variables = variables;
    }

    /**
     * Returns the key for this event based on its lookup table name.
     * @return the event key
     */
    public String getKey() {
        return buildKey(lookupTableName);
    }
}
