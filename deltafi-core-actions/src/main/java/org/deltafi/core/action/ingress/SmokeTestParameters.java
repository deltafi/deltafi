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
package org.deltafi.core.action.ingress;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty(defaultValue = "application/text")
    @JsonPropertyDescription("The content's mediaType. If null, the default is application/text.")
    public String mediaType = "application/text";

    @JsonPropertyDescription("The content to attach to the DeltaFile. If null, random data of size contentSize will be added to the deltaFile")
    public String content;

    @JsonProperty(defaultValue = "500")
    @JsonPropertyDescription("The size in bytes of the random content to attach to the DeltaFile. Ignored if content is set")
    public int contentSize = 500;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("An artificial delay will be randomly introduced one in every X times. Set to 0 to never delay or 1 to always delay")
    public int delayChance = 0;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("Amount of time to delay if delayed")
    public int delayMS = 0;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("The next deltaFile will be immediately triggered one in every X times. Set to 0 to never immediately trigger or 1 to always immediately trigger")
    public int triggerImmediateChance = 0;
}
