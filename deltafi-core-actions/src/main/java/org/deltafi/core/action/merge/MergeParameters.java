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
package org.deltafi.core.action.merge;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MergeParameters extends ActionParameters {
    @JsonPropertyDescription("Name of the merged file. Embed ```{{deltaFileName}}``` to have it replaced with the deltaFileName or content[0].name to get the name of the first content. If not set, will use the filename of the first file merged.")
    private String mergedFilename;

    @JsonPropertyDescription("Media type to apply to the merged file. If not set, will use the media type of the first file merged.")
    private String mediaType;
}
