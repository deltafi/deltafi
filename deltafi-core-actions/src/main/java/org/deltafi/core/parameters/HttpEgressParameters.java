/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.egress.EgressActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HttpEgressParameters extends EgressActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("Number of times to retry a failing HTTP request")
    public Integer retryCount = 3;

    @JsonPropertyDescription("Number milliseconds to wait for an HTTP retry")
    public Integer retryDelayMs = 150;

    public HttpEgressParameters(String egressFlow, String url, Integer retryCount, Integer retryDelayMs) {
        super(egressFlow);

        this.url = url;
        this.retryCount = retryCount;
        this.retryDelayMs = retryDelayMs;
    }
}
