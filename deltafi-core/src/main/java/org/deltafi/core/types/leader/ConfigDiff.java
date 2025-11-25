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
// ABOUTME: Data record for full configuration diff result between leader and member.
// ABOUTME: Contains all diff sections and metadata for the comparison.
package org.deltafi.core.types.leader;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full configuration difference result between leader and a member.
 *
 * @param leaderName The leader identifier (always "Leader")
 * @param memberName The member being compared
 * @param comparedAt When the comparison was performed
 * @param sections   The diff sections grouped by category
 */
public record ConfigDiff(
        String leaderName,
        String memberName,
        OffsetDateTime comparedAt,
        List<DiffSection> sections
) {
    /**
     * Returns the total number of differences across all sections.
     */
    public int totalDiffCount() {
        return sections.stream().mapToInt(DiffSection::diffCount).sum();
    }
}
