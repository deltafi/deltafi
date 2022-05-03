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
package org.deltafi.core.domain.api.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {

    private String did;
    private String name;
    private String ingressFlow;
    private String egressFlow;
    private String hostname;
    private String actionVersion;
    private OffsetDateTime startTime;

    public ActionContext(String did, String name, String ingressFlow, String egressFlow, String hostname, String actionVersion) {
        this.did = did;
        this.name = name;
        this.ingressFlow = ingressFlow;
        this.egressFlow = egressFlow;
        this.hostname = hostname;
        this.actionVersion = actionVersion;
    }
}
