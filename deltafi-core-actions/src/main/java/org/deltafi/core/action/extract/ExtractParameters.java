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
package org.deltafi.core.action.extract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.core.action.ContentSelectionParameters;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExtractParameters extends ContentSelectionParameters {
    @JsonProperty(defaultValue = "METADATA")
    @JsonPropertyDescription("Extract to metadata or annotations.")
    public ExtractTarget extractTarget = ExtractTarget.METADATA;

    @JsonProperty(defaultValue = "ALL")
    @JsonPropertyDescription("Concatenate all or distinct values extracted from multiple content, or just use the first or last content.")
    public HandleMultipleKeysType handleMultipleKeys = HandleMultipleKeysType.ALL;

    @JsonProperty(defaultValue = ",")
    @JsonPropertyDescription("Delimiter to use if handleMultipleKeys is ALL or DISTINCT")
    public String allKeysDelimiter = ",";

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Error if a key is not found.")
    public boolean errorOnKeyNotFound = false;

    public ExtractParameters(List<String> mediaTypes) {
        super(mediaTypes);
    }
}
