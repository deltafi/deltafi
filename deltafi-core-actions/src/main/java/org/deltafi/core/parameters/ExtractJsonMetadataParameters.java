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
package org.deltafi.core.parameters;

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
public class ExtractJsonMetadataParameters extends ActionParameters {
    @JsonPropertyDescription("A map of JSONPath expressions to metadata keys. Values will be extracted using JSONPath and added to the corresponding metadata keys.")
    public Map<String, String> jsonPathToMetadataKeysMap = new HashMap<>();

    @JsonPropertyDescription("List of allowed media types. Supports wildcards (*) and defaults to application/json if empty.")
    public List<String> mediaTypes = List.of("application/json");

    @JsonPropertyDescription("List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.")
    public List<String> filePatterns = List.of();

    @JsonPropertyDescription("How to handle multiple occurrences of a key. Can be 'FIRST', 'LAST', or 'ALL'. Defaults to ALL, which writes a delimited list.")
    public HandleMultipleKeysType handleMultipleKeys = HandleMultipleKeysType.ALL;

    @JsonPropertyDescription("The delimiter to use if handleMultipleKeys is set to ALL")
    public String allKeysDelimiter = ",";

    @JsonPropertyDescription("List of content indexes to consider. If empty, all content is considered.")
    public List<Integer> contentIndexes;

    @JsonPropertyDescription("Whether to return an error if a key is not found. Defaults to false.")
    public boolean errorOnKeyNotFound = false;
}