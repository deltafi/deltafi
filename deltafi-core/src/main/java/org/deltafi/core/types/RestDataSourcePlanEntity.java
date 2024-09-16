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

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("REST_DATA_SOURCE")
@Data
public class RestDataSourcePlanEntity extends DataSourcePlanEntity {
    public RestDataSourcePlanEntity() {
        super(null, FlowType.REST_DATA_SOURCE, null, null, null);
    }

    public RestDataSourcePlanEntity(String name, String description, PluginCoordinates sourcePlugin, String topic) {
        super(name, FlowType.REST_DATA_SOURCE, description, sourcePlugin, topic);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        return List.of();
    }

    @Override
    public FlowPlan toFlowPlan() {
        RestDataSourcePlan flowPlan = new RestDataSourcePlan(getName(), FlowType.REST_DATA_SOURCE, getDescription(), getTopic());
        flowPlan.setSourcePlugin(getSourcePlugin());
        return flowPlan;
    }

    public static RestDataSourcePlanEntity fromFlowPlan(FlowPlan flowPlan) {
        if (!(flowPlan instanceof RestDataSourcePlan restDataSourcePlan)) {
            throw new IllegalArgumentException("Incorrect flow plan type");
        }
        RestDataSourcePlanEntity flowPlanEntity = new RestDataSourcePlanEntity();
        flowPlanEntity.setName(restDataSourcePlan.getName());
        flowPlanEntity.setDescription(restDataSourcePlan.getDescription());
        flowPlanEntity.setSourcePlugin(restDataSourcePlan.getSourcePlugin());
        flowPlanEntity.setTopic(restDataSourcePlan.getTopic());

        return flowPlanEntity;
    }
}