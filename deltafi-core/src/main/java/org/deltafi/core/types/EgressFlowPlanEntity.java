/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("EGRESS")
@Data
public class EgressFlowPlanEntity extends FlowPlanEntity {
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private ActionConfiguration egressAction;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Set<Rule> subscribe;

    public EgressFlowPlanEntity() {
        super(null, FlowType.EGRESS, null, null);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (egressAction != null) {
            actionConfigurations.add(egressAction);
        }
        return actionConfigurations;
    }

    public EgressFlowPlanEntity(String name, String description, PluginCoordinates sourcePlugin, ActionConfiguration egressAction) {
        super(name, FlowType.EGRESS, description, sourcePlugin);
        this.egressAction = egressAction;
    }

    @Override
    public FlowPlan toFlowPlan() {
        EgressFlowPlan flowPlan = new EgressFlowPlan(getName(), FlowType.EGRESS, getDescription(), egressAction);
        flowPlan.setSourcePlugin(getSourcePlugin());
        flowPlan.setSubscribe(subscribe);
        return flowPlan;
    }

    public static EgressFlowPlanEntity fromFlowPlan(FlowPlan flowPlan) {
        if (!(flowPlan instanceof EgressFlowPlan egressFlowPlan)) {
            throw new IllegalArgumentException("Incorrect flow plan type");
        }
        EgressFlowPlanEntity flowPlanEntity = new EgressFlowPlanEntity();
        flowPlanEntity.setName(egressFlowPlan.getName());
        flowPlanEntity.setDescription(egressFlowPlan.getDescription());
        flowPlanEntity.setEgressAction(egressFlowPlan.getEgressAction());
        flowPlanEntity.setSourcePlugin(egressFlowPlan.getSourcePlugin());
        flowPlanEntity.setSubscribe(egressFlowPlan.getSubscribe());

        return flowPlanEntity;
    }
}