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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.HashMap;
import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SmokeTestParameters extends ActionParameters {
    @JsonPropertyDescription("Metadata to add to each smoke-generated DeltaFile")
    public Map<String, String> metadata = new HashMap<>();

    @JsonPropertyDescription("The content's mediaType. If null, the default is application/text.")
    public String mediaType = "application/text";

    @JsonPropertyDescription("The content to attach to the DeltaFile. If null, random data of size contentSize will be added to the deltaFile")
    public String content;

    @JsonPropertyDescription("The size in bytes of the random content to attach to the DeltaFile. Ignored if content is set")
    public int contentSize = 500;
}
