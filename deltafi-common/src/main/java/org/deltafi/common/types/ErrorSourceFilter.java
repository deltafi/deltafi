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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ErrorSourceFilter {
    private FlowType flowType;
    private String flowName;
    private String actionName;
    private String actionClass;

    @JsonCreator
    public ErrorSourceFilter(
            @JsonProperty("flowType") FlowType flowType,
            @JsonProperty("flowName") String flowName,
            @JsonProperty("actionName") String actionName,
            @JsonProperty("actionClass") String actionClass) {
        this.flowType = flowType;
        this.flowName = flowName;
        this.actionName = actionName;
        this.actionClass = actionClass;
    }
}