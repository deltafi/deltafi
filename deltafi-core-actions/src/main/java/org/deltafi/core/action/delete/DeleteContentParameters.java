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
package org.deltafi.core.action.delete;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class DeleteContentParameters extends ActionParameters {

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Delete All Content")
    public boolean deleteAllContent = false;

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of indexes in content to keep")
    public List<Integer> allowedIndexes = List.of();

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of indexes in content to remove. Not checked unless allowedIndexes is empty")
    public List<Integer> prohibitedIndexes = List.of();

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of filename patterns to keep")
    public List<String> allowedFilePatterns = List.of();

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of filename patterns to remove. Not checked unless allowedFilePatterns is empty")
    public List<String> prohibitedFilePatterns = List.of();

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of media type patterns to keep")
    public List<String> allowedMediaTypes = List.of();

    @JsonProperty(defaultValue = "[]")
    @JsonPropertyDescription("A List of media type patterns to remove. Not checked unless allowedMediaTypes is empty")
    public List<String> prohibitedMediaTypes = List.of();

}
