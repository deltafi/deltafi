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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EgressFlowConfiguration extends DeltaFiConfiguration {
    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;

    private final String formatAction;
    private List<String> validateActions;
    private final String egressAction;

    public EgressFlowConfiguration(String name, String formatAction, String egressAction) {
        super(name);
        this.formatAction = formatAction;
        this.egressAction = egressAction;
    }
}
