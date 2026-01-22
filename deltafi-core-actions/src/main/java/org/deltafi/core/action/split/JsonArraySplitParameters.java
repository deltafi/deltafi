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
package org.deltafi.core.action.split;

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
public class JsonArraySplitParameters extends ActionParameters {
    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("If TRUE, pass through content that is not JSON or is invalid JSON. Else error")
    private boolean passThroughNonJson = true;

    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("If TRUE, pass through JSON content that is not an array. Else error")
    private boolean passThroughNonJsonArray = true;

    @JsonPropertyDescription("If not NULL, each split output content name will have the original name followed by this suffix and an index which starts at 0")
    private String contentSuffix;
}
