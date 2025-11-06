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
package org.deltafi.actionkit.lookup;

import lombok.Builder;
import org.deltafi.common.types.SortDirection;

import java.util.Map;
import java.util.Set;

/**
 * The options used to perform a table lookup.
 *
 * @param matchingColumnValues an optional map of column/value pairs to match. If null, all rows are matched.
 * @param resultColumns an optional set of result columns to return. If null, all columns are returned.
 * @param sortColumn the column to sort results by
 * @param sortDirection the direction to sort results by
 * @param offset the offset from the first sorted result to return results (for paging)
 * @param limit the maximum amount of results to return (for paging)
 */
@Builder
public record LookupOptions(Map<String, Set<String>> matchingColumnValues, Set<String> resultColumns,
        String sortColumn, SortDirection sortDirection, Integer offset, Integer limit) {
    public static LookupOptions defaultLookupOptions() {
        return new LookupOptions(null, null, null, null, null, null);
    }
}
