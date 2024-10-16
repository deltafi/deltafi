/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class DeltaFileDeleteDTO {
    private UUID did;
    private OffsetDateTime contentDeleted;
    private long totalBytes;
    private List<UUID> contentObjectIds;

    public DeltaFileDeleteDTO(UUID did, OffsetDateTime contentDeleted, long totalBytes, List<UUID> contentObjectIds) {
        this.did = did;
        this.contentDeleted = contentDeleted;
        this.totalBytes = totalBytes;
        setContent(contentObjectIds);
    }

    public void setContent(List<UUID> contentObjectIds) {
        this.contentObjectIds = contentObjectIds == null ? Collections.emptyList() : contentObjectIds;
    }
}
