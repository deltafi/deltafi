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
package org.deltafi.core.action.extract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ExtractJsonParameters extends ActionParameters {

    @JsonProperty(defaultValue = "{}")
    @JsonPropertyDescription("A map of JSONPath expressions to keys. Values will be extracted using JSONPath and added to the corresponding metadata or annotation keys.")
    public Map<String, String> jsonPathToKeysMap = new HashMap<>();

    @JsonProperty(defaultValue = "METADATA")
    @JsonPropertyDescription("Extract to metadata or annotations.")
    public ExtractTarget extractTarget = ExtractTarget.METADATA;

    @JsonProperty(defaultValue = "[\"application/json\"]")
    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults to application/json.")
    public List<String> mediaTypes = List.of("application/json");

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.")
    public List<String> filePatterns = List.of();

    @JsonProperty(defaultValue = "ALL")
    @JsonPropertyDescription("How to handle multiple occurrences of a key. Can be 'FIRST', 'LAST', 'DISTINCT', or 'ALL'. Defaults to ALL, which writes a delimited list.")
    public HandleMultipleKeysType handleMultipleKeys = HandleMultipleKeysType.ALL;

    @JsonProperty(defaultValue = ",")
    @JsonPropertyDescription("The delimiter to use if handleMultipleKeys is set to DISTINCT or ALL")
    public String allKeysDelimiter = ",";

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("List of content indexes to consider. If empty, all content is considered.")
    public List<Integer> contentIndexes = List.of();

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Whether to return an error if a key is not found. Defaults to false.")
    public boolean errorOnKeyNotFound = false;
}
