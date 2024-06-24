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
package org.deltafi.core.integration.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExpectedFlows {
    private String flow;
    private FlowType type;
    private DeltaFileFlowState state;
    private List<String> actions;

    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isEmpty(flow)) {
            errors.add("ExpectedFlows missing flow");
        }

        if (type == null) {
            errors.add("ExpectedFlows missing type");
        }

        if (state == null) {
            state = DeltaFileFlowState.COMPLETE;
        }

        if (actions == null) {
            actions = new ArrayList<>();
        }

        return errors;
    }
}
