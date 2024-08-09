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
package org.deltafi.core.action.compress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class DecompressParameters extends ActionParameters {
    @JsonPropertyDescription("Format to decompress, overriding autodetection")
    private Format format;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Retain the existing content")
    private boolean retainExistingContent = false;

    @JsonPropertyDescription("If set, will save a JSON lineage of each file and its parent")
    private String lineageFilename;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("Enables recursive decryption/un-archiving of embedded content by checking filename extensions")
    private int maxRecursionLevels = 0;
}
