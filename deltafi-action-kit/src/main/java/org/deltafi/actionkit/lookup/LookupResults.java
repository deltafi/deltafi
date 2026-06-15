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
// ABOUTME: Record holding the results of a lookup table query.
// ABOUTME: Contains the total count, timestamps, and the list of matching rows.
package org.deltafi.actionkit.lookup;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Results of a lookup table query.
 * @param totalCount total number of matching rows (before paging)
 * @param created when the lookup table was created
 * @param modified when the lookup table was last modified
 * @param ttlSeconds seconds a caching client may retain this result before re-querying core, or
 *                   {@code null} when the result must not be cached (the table has no refresh schedule)
 * @param results the matching rows as column/value maps
 */
public record LookupResults(int totalCount, OffsetDateTime created, OffsetDateTime modified, Integer ttlSeconds,
        List<Map<String, String>> results) {

    /**
     * Convenience constructor for results with no caching hint (e.g. tests or callers that build a
     * result directly); {@code ttlSeconds} defaults to {@code null}, meaning the result is not cached.
     */
    @SuppressWarnings("unused") // this can be used in plugins for testing
    public LookupResults(int totalCount, OffsetDateTime created, OffsetDateTime modified,
            List<Map<String, String>> results) {
        this(totalCount, created, modified, null, results);
    }
}
