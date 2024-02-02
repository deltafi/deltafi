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
import org.springframework.data.annotation.Transient;

import java.time.OffsetDateTime;
import java.util.*;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TimedIngressActionConfiguration.class, name = "TimedIngressActionConfiguration"),
        @JsonSubTypes.Type(value = TransformActionConfiguration.class, name = "TransformActionConfiguration"),
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

    protected CollectConfiguration collect;

    public ActionConfiguration(String name, ActionType actionType, String type) {
        super(name);
        this.actionType = actionType;
        this.type = type;
    }

    /**
     * Create the ActionInput that should be sent to an Action
     * @param flow the flow on which the Action is specified
     * @param deltaFile DeltaFile that will be acted upon
     * @param systemName system name to set in context
     * @param returnAddress the unique address of this core instance
     * @param action the action
     * @param memo memo to set in the context
     * @return ActionInput containing the ActionConfiguration
     */
    public ActionInput buildActionInput(DeltaFile deltaFile, DeltaFileFlow flow, Action action, String systemName,
                                        String returnAddress, String memo) {
        if (Objects.isNull(internalParameters)) {
            // fall back to using parameters if internalParameters do not exist yet
            setInternalParameters(Objects.requireNonNullElse(parameters, Collections.emptyMap()));
        }

        return ActionInput.builder()
                .queueName(type)
                .actionContext(ActionContext.builder()
                        .flowName(flow.getName())
                        .dataSource(deltaFile.getDataSource())
                        .flowId(flow.getId())
                        .actionName(action.getName())
                        .actionId(action.getId())
                        .did(deltaFile.getDid())
                        .deltaFileName(deltaFile.getName())
                        .systemName(systemName)
                        .memo(memo)
                        .build())
                .actionParams(internalParameters)
                .deltaFileMessages(List.of(new DeltaFileMessage(flow.getMetadata(), flow.lastContent())))
                .returnAddress(returnAddress)
                .actionCreated(action.getCreated())
                .coldQueued(action.getState() == ActionState.COLD_QUEUED)
                .build();
    }

    /**
     * Create the ActionInput that should be sent to an Action with a collect configuration.
     * @param flow the flow on which the Action is specified
     * @param aggregate the aggregate DeltaFile
     * @param collectedDeltaFiles the DeltaFiles that will be acted upon
     * @param systemName system name to set in context
     * @param actionCreated action created datetime
     * @param action the action
     * @return ActionInput containing the ActionConfiguration
     */
    public ActionInput buildCollectingActionInput(DeltaFileFlow flow, DeltaFile aggregate,
                                                  List<DeltaFile> collectedDeltaFiles, String systemName,
                                                  OffsetDateTime actionCreated, Action action) {
        if (Objects.isNull(internalParameters)) {
            setInternalParameters(Collections.emptyMap());
        }

        return ActionInput.builder()
                .queueName(type)
                .actionContext(ActionContext.builder()
                        .flowName(flow.getName())
                        .dataSource(aggregate.getDataSource())
                        .flowId(flow.getId())
                        .actionName(action.getName())
                        .actionId(action.getId())
                        .did(aggregate.getDid())
                        .deltaFileName(aggregate.getName())
                        .systemName(systemName)
                        .collect(collect)
                        .collectedDids(collectedDeltaFiles.stream().map(DeltaFile::getDid).toList())
                        .build())
                .actionParams(internalParameters)
                // TODO: Fix collect. how do we know what flow it's collecting on?
                //.deltaFileMessages(collectedDeltaFiles.stream().map(DeltaFile::forQueue).toList())
                .actionCreated(actionCreated)
                // TODO: ?
                //.coldQueued(false)
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
        List<String> errors = new ArrayList<>();
        if (actionDescriptor.getType() != actionType) {
            errors.add("Action: " + type + " is not registered as an action of type " + actionType);
        }
        if (collect != null) {
            errors.addAll(collect.validate());
        }
        return errors;
    }
}
