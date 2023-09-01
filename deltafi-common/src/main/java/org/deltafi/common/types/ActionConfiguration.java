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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.deltafi.common.constant.DeltaFiConstants;
import org.springframework.data.annotation.Transient;

import java.util.*;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransformActionConfiguration.class, name = "TransformActionConfiguration"),
        @JsonSubTypes.Type(value = LoadActionConfiguration.class, name = "LoadActionConfiguration"),
        @JsonSubTypes.Type(value = DomainActionConfiguration.class, name = "DomainActionConfiguration"),
        @JsonSubTypes.Type(value = EnrichActionConfiguration.class, name = "EnrichActionConfiguration"),
        @JsonSubTypes.Type(value = FormatActionConfiguration.class, name = "FormatActionConfiguration"),
        @JsonSubTypes.Type(value = ValidateActionConfiguration.class, name = "ValidateActionConfiguration"),
        @JsonSubTypes.Type(value = EgressActionConfiguration.class, name = "EgressActionConfiguration")
})
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "internalParameters")
public abstract class ActionConfiguration extends DeltaFiConfiguration {
    @JsonIgnore
    @Transient
    protected final ActionType actionType;
    protected final String type;

    @JsonIgnore
    protected Map<String, Object> internalParameters;
    protected Map<String, Object> parameters;

    protected ActionConfiguration(String name, ActionType actionType, String type) {
        super(name);
        this.actionType = actionType;
        this.type = type;
    }

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

    /**
     * Create the ActionInput that should be sent to an Action
     * @param flow the flow on which the Action is specified
     * @param deltaFile DeltaFile that will be acted upon
     * @param systemName system name to set in context
     * @param egressFlow the egress flow for this action
     * @param returnAddress the unique address of this core instance
     * @return ActionInput containing the ActionConfiguration
     */
    public ActionInput buildActionInput(String flow, DeltaFile deltaFile, String systemName, String egressFlow, String returnAddress) {
        if (Objects.isNull(internalParameters)) {
            // fall back to using parameters if internalParameters do not exist yet
            setInternalParameters(Objects.requireNonNullElse(parameters, Collections.emptyMap()));
        }

        return ActionInput.builder()
                .queueName(type)
                .actionContext(ActionContext.builder()
                        .did(deltaFile.getDid())
                        .flow(flow)
                        .name(name)
                        .sourceFilename(deltaFile.getSourceInfo().getFilename())
                        .ingressFlow(deltaFile.getSourceInfo().getFlow())
                        .egressFlow(egressFlow)
                        .systemName(systemName)
                        .build())
                .actionParams(internalParameters)
                .deltaFileMessages(List.of(deltaFile.forQueue(egressFlow)))
                .returnAddress(returnAddress)
                .build();
    }

    /**
     * Validates this action configuration.
     *
     * @param actionDescriptor action descriptor to be validated against
     *
     * @return a List of validation errors or an empty list if there are no errors
     */
    public List<String> validate(ActionDescriptor actionDescriptor) {
        return validateType(actionDescriptor);
    }

    public List<String> validateType(ActionDescriptor actionDescriptor) {
        List<String> errors = new ArrayList<>();
        if (actionDescriptor.getType() != actionType) {
            errors.add("Action: " + type + " is not registered as an action of type " + actionType);
        }
        return errors;
    }
}
