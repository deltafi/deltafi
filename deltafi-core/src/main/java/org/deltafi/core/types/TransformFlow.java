/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@DiscriminatorValue("TRANSFORM")
@Data
@EqualsAndHashCode(callSuper = true)
public class TransformFlow extends Flow implements Subscriber, Publisher {
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<ActionConfiguration> transformActions = new ArrayList<>();

    @JsonProperty(required = true)
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Set<Rule> subscribe;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private PublishRules publish;

    public TransformFlow() {
        super(null, FlowType.TRANSFORM, null, null);
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        return actionNamed(transformActions, actionNamed);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (transformActions != null) {
            actionConfigurations.addAll(transformActions);
        }
        return actionConfigurations;
    }

    public void updateActionNamesByFamily(Map<ActionType, ActionFamily> actionFamilyMap) {
        updateActionNamesByFamily(actionFamilyMap, ActionType.TRANSFORM, actionNames(transformActions));
    }

    @Override
    public Set<Rule> subscribeRules() {
        return subscribe;
    }

    @Override
    public FlowType flowType() {
        return FlowType.TRANSFORM;
    }

    @Override
    public PublishRules publishRules() {
        return publish;
    }
}
