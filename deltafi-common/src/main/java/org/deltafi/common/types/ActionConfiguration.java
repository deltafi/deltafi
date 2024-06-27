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

import java.util.*;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TimedIngressActionConfiguration.class, name = "TimedIngressActionConfiguration"),
        @JsonSubTypes.Type(value = TransformActionConfiguration.class, name = "TransformActionConfiguration"),
        @JsonSubTypes.Type(value = EgressActionConfiguration.class, name = "EgressActionConfiguration")
})
@EqualsAndHashCode
@ToString(callSuper = true, exclude = "internalParameters")
public abstract class ActionConfiguration {
    protected String name;
    protected String apiVersion;
    @JsonIgnore
    @Transient
    protected final ActionType actionType;
    protected final String type;

    @JsonIgnore
    protected Map<String, Object> internalParameters;
    protected Map<String, Object> parameters;

    protected JoinConfiguration join;

    protected ActionConfiguration(String name, ActionType actionType, String type) {
        this.name = name;
        this.actionType = actionType;
        this.type = type;
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
        if (join != null) {
            errors.addAll(join.validate());
        }
        return errors;
    }

    public Map<String, Object> getInternalParameters() {
        if (Objects.isNull(internalParameters)) {
            // fall back to using parameters if internalParameters do not exist yet
            setInternalParameters(Objects.requireNonNullElse(parameters, Collections.emptyMap()));
        }

        return internalParameters;
    }
}
