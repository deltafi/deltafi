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
package org.deltafi.core.configuration;

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.types.ActionSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface ActionConfiguration extends DeltaFiConfiguration {
    static boolean equalOrAny(List<String> schemaList, List<String> configList) {
        List<String> expected = Objects.isNull(schemaList) ? Collections.emptyList() : schemaList;
        List<String> actual = Objects.isNull(configList) ? Collections.emptyList() : configList;

        if (expected.contains(DeltaFiConstants.MATCHES_ANY)) {
            return true;
        }

        if (expected.isEmpty()) {
            return actual.isEmpty();
        }

        return expected.stream().allMatch(item -> actual.stream().anyMatch(i -> i.equals(item)));
    }

    String getType();

    Map<String, Object> getParameters();

    void setParameters(Map<String, Object> parameters);

    /**
     * Validates this action configuration.
     *
     * @return a List of validation errors or an empty list if there are no errors
     */
    List<String> validate(ActionSchema actionSchema);

    /**
     * Create the ActionInput that should be sent to an Action
     * @param deltaFile DeltaFile that will be acted upon
     * @param systemName system name to set in context
     * @return ActionInput containing the ActionConfiguration
     */
    default ActionInput buildActionInput(DeltaFile deltaFile, String systemName, String egressFlow) {
        if (Objects.isNull(getParameters())) {
            setParameters(Collections.emptyMap());
        }

        return ActionInput.builder()
                .queueName(getType())
                .actionContext(ActionContext.builder()
                        .did(deltaFile.getDid())
                        .name(getName())
                        .ingressFlow(deltaFile.getSourceInfo().getFlow())
                        .egressFlow(egressFlow)
                        .systemName(systemName)
                        .build())
                .actionParams(getParameters())
                .deltaFile(deltaFile.forQueue(getName()))
                .build();
    }
}
