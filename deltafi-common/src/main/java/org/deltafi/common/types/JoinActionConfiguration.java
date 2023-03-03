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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.PersistenceCreator;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@Getter
@Setter
public class JoinActionConfiguration extends ActionConfiguration {
    private String maxAge;
    private Integer maxNum;
    private String metadataKey;
    private String metadataIndexKey;

    public JoinActionConfiguration(String name, String type, String maxAge) {
        super(name, ActionType.JOIN, type);
        this.maxAge = maxAge;
    }

    @PersistenceCreator
    @JsonCreator
    @SuppressWarnings("unused")
    public JoinActionConfiguration(@JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "actionType") ActionType actionType,
            @JsonProperty(value = "type", required = true) String type,
            @JsonProperty(value = "maxAge", required = true) String maxAge) {
        this(name, type, maxAge);
    }

    public ActionInput buildActionInput(String flow, DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles,
            String systemName, String egressFlow) {
        if (Objects.isNull(parameters)) {
            setParameters(Collections.emptyMap());
        }

        return ActionInput.builder()
                .queueName(type)
                .actionContext(ActionContext.builder()
                        .did(deltaFile.getDid())
                        .name(name)
                        .ingressFlow(flow)
                        .egressFlow(egressFlow)
                        .systemName(systemName)
                        .build())
                .actionParams(parameters)
                .deltaFile(deltaFile.forQueue(name))
                .joinedDeltaFiles(joinedDeltaFiles.stream()
                        .map(joinedDeltaFile -> joinedDeltaFile.forQueue(name)).collect(Collectors.toList()))
                .build();
    }
}