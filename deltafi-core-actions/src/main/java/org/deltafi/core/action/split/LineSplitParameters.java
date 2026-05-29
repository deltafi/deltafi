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
package org.deltafi.core.action.split;

// ABOUTME: Parameters for the LineSplit transform action.
// ABOUTME: Configures output format and blank line filter.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LineSplitParameters extends ActionParameters {
    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Skip blank lines from output")
    private boolean skipBlankLines = false;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Split into child results")
    private boolean splitIntoChildResults = false;

    @JsonProperty(defaultValue = "1")
    @JsonPropertyDescription("Max content per child DeltaFile when splitIntoChildResults is set")
    private int maxContentPerChild = 1;
}
