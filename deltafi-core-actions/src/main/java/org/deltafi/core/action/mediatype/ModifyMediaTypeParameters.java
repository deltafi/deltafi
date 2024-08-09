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
package org.deltafi.core.action.mediatype;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ModifyMediaTypeParameters extends ActionParameters {
    @JsonPropertyDescription("Map of old to new media types, supporting wildcards (*) in the old media types")
    private Map<String, String> mediaTypeMap;

    @JsonPropertyDescription("Map of indexes to media types. Used to update the media type of specific content by index. Overrides mediaTypeMap.")
    private Map<Integer, String> indexMediaTypeMap;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Error if content for any index in indexMediaTypeMap is missing.")
    private boolean errorOnMissingIndex = false;

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Autodetect media type if not found in mediaTypeMap or indexMediaTypeMap.")
    private boolean autodetect = true;
}