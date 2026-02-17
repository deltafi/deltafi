/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types.leader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ABOUTME: Request for executing error actions across fleet members.
 * ABOUTME: Specifies filter criteria and action parameters like acknowledge reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FederatedActionRequest {
    private FederatedErrorFilter filter;
    private String reason;
    private List<String> memberNames;
    private Integer limit;
    /** For by-flow operations: list of flows to act on (flow name and type) */
    private List<FlowSelection> flows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowSelection {
        private String name;
        private String flowType;
    }
}
