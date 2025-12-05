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
package org.deltafi.core.action.slice;

// ABOUTME: Parameters for the Slice transform action.
// ABOUTME: Configures offset, size, and content selection for byte range extraction.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SliceParameters extends ContentSelectionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Starting byte offset (0-based). Negative values count from end (e.g., -100 = last 100 bytes)")
    private long offset;

    @JsonPropertyDescription("Number of bytes to extract. If not specified, extracts from offset to end of content")
    private Long size;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("If true, produce empty content when offset is beyond content size. If false, return an error")
    private boolean allowEmptyResult = false;
}
