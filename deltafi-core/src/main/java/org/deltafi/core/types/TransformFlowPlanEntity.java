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
package org.deltafi.core.types;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.*;
import org.hibernate.annotations.Type;

import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("TRANSFORM")
@Data
@NoArgsConstructor
public class TransformFlowPlanEntity extends FlowPlanEntity {
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<ActionConfiguration> transformActions;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Set<Rule> subscribe;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private PublishRules publish;

    public TransformFlowPlanEntity(String name, String description) {
        super(name, FlowType.TRANSFORM, description);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return transformActions;
    }

    @Override
    public FlowPlan toFlowPlan() {
        TransformFlowPlan flowPlan = new TransformFlowPlan(getName(), FlowType.TRANSFORM, getDescription());
        flowPlan.setSourcePlugin(getSourcePlugin());
        flowPlan.setTransformActions(transformActions);
        flowPlan.setSubscribe(subscribe);
        flowPlan.setPublish(publish);
        return flowPlan;
    }

    public static TransformFlowPlanEntity fromFlowPlan(FlowPlan flowPlan) {
        if (!(flowPlan instanceof TransformFlowPlan transformFlowPlan)) {
            throw new IllegalArgumentException("Incorrect flow plan type");
        }
        TransformFlowPlanEntity flowPlanEntity = new TransformFlowPlanEntity();
        flowPlanEntity.setName(transformFlowPlan.getName());
        flowPlanEntity.setDescription(transformFlowPlan.getDescription());
        flowPlanEntity.setTransformActions(transformFlowPlan.getTransformActions());
        flowPlanEntity.setSourcePlugin(transformFlowPlan.getSourcePlugin());
        flowPlanEntity.setSubscribe(transformFlowPlan.getSubscribe());
        flowPlanEntity.setPublish(transformFlowPlan.getPublish());

        return flowPlanEntity;
    }
}