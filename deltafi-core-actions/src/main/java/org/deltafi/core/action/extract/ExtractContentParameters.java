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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.action.ContentSelectionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ExtractContentParameters extends ContentSelectionParameters {
    @JsonProperty(defaultValue = "METADATA")
    @JsonPropertyDescription("Extract to metadata or annotation.")
    private ExtractTarget extractTarget = ExtractTarget.METADATA;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Key to use for metadata or annotation")
    private String key;

    @JsonProperty(defaultValue = "512")
    @JsonPropertyDescription("Maximum number of characters in the key's value")
    private int maxSize = 512;

    @JsonProperty(defaultValue = ",")
    @JsonPropertyDescription("Delimiter to use if multiple content matched")
    private String multiValueDelimiter = ",";
}