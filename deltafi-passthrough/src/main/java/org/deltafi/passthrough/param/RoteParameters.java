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
package org.deltafi.passthrough.param;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoteParameters extends ActionParameters {
    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("Minimum time to delay processing in ms")
    private int minRoteDelayMS = 0;

    @JsonProperty(defaultValue = "0")
    @JsonPropertyDescription("Maximum time to delay processing in ms")
    private int maxRoteDelayMS = 0;
}
