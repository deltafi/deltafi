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
package org.deltafi.core.action.egress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HttpEgressParameters extends ActionParameters implements IHttpEgressParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to send the DeltaFile to")
    private String url;

    @JsonProperty(defaultValue = "3")
    @JsonPropertyDescription("Number of times to retry a failing HTTP request")
    private Integer retryCount = 3;

    @JsonProperty(defaultValue = "150")
    @JsonPropertyDescription("Number of milliseconds to wait for an HTTP retry")
    private Integer retryDelayMs = 150;
}
