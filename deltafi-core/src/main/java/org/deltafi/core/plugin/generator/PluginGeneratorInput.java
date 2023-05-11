/**
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
package org.deltafi.core.plugin.generator;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.ProcessingType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class PluginGeneratorInput {

    private static final Set<ActionType> NORMALIZATION_FLOW_ACTION = Set.of(ActionType.LOAD, ActionType.ENRICH, ActionType.DOMAIN, ActionType.FORMAT, ActionType.VALIDATE);

    private String groupId;
    private String artifactId;
    private String description;
    private PluginLanguage pluginLanguage;
    private ProcessingType processingType;
    private Set<ActionGeneratorInput> actions = new HashSet<>();

    private EnumMap<ActionType, List<ActionGeneratorInput>> actionMap;

    public void validate() {
        requireNonBlank(groupId, "The groupId must be set");
        requireNonBlank(artifactId, "The artifactId must be set");
        requireNonBlank(description, "The description must be set");

        if (pluginLanguage == null) {
            throw new IllegalArgumentException("The pluginLanguage must be set");
        }

        actions.forEach(ActionGeneratorInput::validate);
    }

    void requireNonBlank(String field, String message) {
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException(message);
        }
    }

    public ProcessingType getOrInferProcessingType() {
        if (processingType == null) {
            processingType = determineProcessingType();
        }

        return processingType;
    }

    private ProcessingType determineProcessingType() {
        if (actions.stream().anyMatch(this::isNormalizationFlowAction)) {
            return ProcessingType.NORMALIZATION;
        }

        return ProcessingType.TRANSFORMATION;
    }

    private boolean isNormalizationFlowAction(ActionGeneratorInput actionGeneratorInput) {
        return NORMALIZATION_FLOW_ACTION.contains(actionGeneratorInput.getActionType());
    }

    public List<ActionGeneratorInput> getTransformActions() {
        return getActions(ActionType.TRANSFORM);
    }

    public List<ActionGeneratorInput> getLoadActions() {
        return getActions(ActionType.LOAD);
    }

    public List<ActionGeneratorInput> getDomainActions() {
        return getActions(ActionType.DOMAIN);
    }

    public List<ActionGeneratorInput> getEnrichActions() {
        return getActions(ActionType.ENRICH);
    }

    public List<ActionGeneratorInput> getFormatActions() {
        return getActions(ActionType.FORMAT);
    }

    public List<ActionGeneratorInput> getValidateActions() {
        return getActions(ActionType.VALIDATE);
    }

    public List<ActionGeneratorInput> getEgressActions() {
        return getActions(ActionType.EGRESS);
    }

    private List<ActionGeneratorInput> getActions(ActionType actionType) {
        if (actionMap == null) {
            actionMap = actions.stream().collect(Collectors.groupingBy(ActionGeneratorInput::getActionType, () -> new EnumMap<>(ActionType.class), Collectors.toList()));
        }

        return actionMap.containsKey(actionType) ? actionMap.get(actionType) : List.of();
    }
}
