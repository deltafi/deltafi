/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.join;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Document
@Data
@NoArgsConstructor
public class JoinEntry {
    @Id
    private JoinEntryId id;

    private boolean locked;
    private OffsetDateTime lockedTime;

    private OffsetDateTime joinDate;
    private Integer maxDeltaFileEntries;

    private List<IndexedDeltaFileEntry> deltaFileEntries;

    public List<IndexedDeltaFileEntry> getSortedDeltaFileEntries() {
        return deltaFileEntries.stream()
                .sorted(Comparator.comparing(IndexedDeltaFileEntry::getIndex))
                .collect(Collectors.toList());
    }
}