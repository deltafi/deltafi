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
// ABOUTME: Request object for fetching DeltaFile content by location pointer.
// ABOUTME: Identifies content by DeltaFile DID, flow number, action index, and content index.
package org.deltafi.core.types;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ContentRequest(
        UUID did,              // DeltaFile identifier
        int flowNumber,        // Which flow (by number, not index)
        Integer actionIndex,   // Which action (null = flow input, 0+ = action index)
        int contentIndex,      // Which content piece in the list
        Long offset,           // Byte offset for partial reads (null = start from beginning)
        Long size              // Byte count for partial reads (null = read to end)
) {
}
