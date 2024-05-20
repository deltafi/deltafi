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
package org.deltafi.core.action.mediatype;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.HashMap;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ModifyMediaTypeParameters extends ActionParameters {
    @JsonPropertyDescription("A map of old to new media types. Supports wildcards (*) in the old media types. These will be applied before and overridden by the indexMediaTypeMap values, if present.")
    public Map<String, String> mediaTypeMap = new HashMap<>();

    @JsonPropertyDescription("A map of indexes to media types. Used to update the media type of specific content by index.")
    Map<Integer, String> indexMediaTypeMap = new HashMap<>();

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("If true, throw an exception if a content is missing an index specified in indexMediaTypeMap")
    public boolean errorOnMissingIndex = false;
}