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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.springframework.util.unit.DataSize;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class LineSplitterParameters extends ActionParameters {
    @JsonPropertyDescription("Characters that indicate the line is a comment when searching for headers")
    private String commentChars;

    @Builder.Default
    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("True to include the header line (excluding any comments before the header) in all child files")
    private boolean includeHeaderInAllChunks = false;

    @Builder.Default
    @JsonProperty(defaultValue = "2147483647")
    @JsonPropertyDescription("Max number of rows that should be included in each child file")
    private int maxRows = Integer.MAX_VALUE;

    @Builder.Default
    @JsonProperty(defaultValue = "524288000")
    @JsonPropertyDescription("Max size in bytes of each child file (including the header line if includeHeaders is true)")
    private long maxSize = DataSize.ofMegabytes(500).toBytes();

    public boolean hasCommentChars() {
        return commentChars != null && !commentChars.isEmpty();
    }
}
