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
package org.deltafi.core.action.archive;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class ArchiveParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Archive type: ar, tar, tar.gz, tar.xz, or zip")
    public ArchiveType archiveType;

    @JsonProperty
    @JsonPropertyDescription("Sets the media type of the new content to the specified value. Otherwise, will be based on archiveType")
    public String mediaType;

    @JsonProperty
    @JsonPropertyDescription("Append the archiveType suffix to new content name(s)")
    public Boolean appendSuffix = true;
}
