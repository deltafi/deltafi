/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

@Data
@NoArgsConstructor
public class LookupTableEvent {
    public static final String PREFIX = "lookup-table-event";

    private static long nextId = 0;

    private String id;
    private String lookupTableName;
    private Map<String, Set<String>> matchingColumnValues;
    private List<String> resultColumns;
    private Map<String, String> variables;

    public static String buildKey(String lookupTableName) {
        return String.join("-", PREFIX, lookupTableName);
    }

    public LookupTableEvent(String uniqueId, String lookupTableName, Map<String, Set<String>> matchingColumnValues,
            List<String> resultColumns, Map<String, String> variables) {
        this.id = String.join("-", PREFIX, uniqueId, Long.toString(nextId++));
        this.lookupTableName = lookupTableName;
        this.matchingColumnValues = matchingColumnValues;
        this.resultColumns = resultColumns;
        this.variables = variables;
    }

    public String getKey() {
        return buildKey(lookupTableName);
    }
}
