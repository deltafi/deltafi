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
package org.deltafi.core.action.compress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.springframework.util.unit.DataSize;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DecompressParameters extends ActionParameters {
    @JsonPropertyDescription("Format to decompress, overriding autodetection")
    private Format format;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("Retain the existing content")
    private boolean retainExistingContent = false;

    @JsonProperty(defaultValue = "false")
    @JsonPropertyDescription("When auto detecting a single content, if a supported archive/compression format is not detected, then pass the content through instead of generating an error, and set 'decompressPassthrough' metadata key to 'true'")
    private boolean passThroughUnsupported = false;

    @JsonPropertyDescription("If set, will save a JSON lineage of each file and its parent")
    private String lineageFilename;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("Enables recursive decompression/un-archiving of embedded content by checking filename extensions")
    private int maxRecursionLevels = 0;

    @JsonProperty(defaultValue = "8GB")
    @JsonPropertyDescription("Limit the combined total of bytes that can be extracted across all files to protect against filling content storage. Metadata override key is 'disableMaxExtractedBytesCheck'. Defaults to 8GB if not positive")
    private DataSize maxExtractedBytes = DataSize.ofGigabytes(8);
}
