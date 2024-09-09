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
import lombok.*;

import java.util.*;

@Getter
@Setter
@EqualsAndHashCode
@ToString(callSuper = true, exclude = "internalParameters")
@NoArgsConstructor
public class ActionConfiguration {
    private String name;
    private String apiVersion;
    private ActionType actionType;
    private String type;

    @JsonIgnore
    private Map<String, Object> internalParameters;
    private Map<String, Object> parameters;

    protected JoinConfiguration join;

    public ActionConfiguration(String name, ActionType actionType, String type) {
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
