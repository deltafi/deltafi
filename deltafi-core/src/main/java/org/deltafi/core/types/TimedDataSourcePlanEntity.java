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

@EqualsAndHashCode(callSuper = true)
@Entity
@DiscriminatorValue("TIMED_DATA_SOURCE")
@Data
public class TimedDataSourcePlanEntity extends DataSourcePlanEntity {
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private ActionConfiguration timedIngressAction;

    private String cronSchedule;

    public TimedDataSourcePlanEntity() {
        super(null, FlowType.TIMED_DATA_SOURCE, null, null, null);
    }

    public TimedDataSourcePlanEntity(String name, String description, PluginCoordinates sourcePlugin, String topic, ActionConfiguration timedIngressAction, String cronSchedule) {
        super(name, FlowType.TIMED_DATA_SOURCE, description, sourcePlugin, topic);
        this.timedIngressAction = timedIngressAction;
        this.cronSchedule = cronSchedule;
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (timedIngressAction != null) {
            actionConfigurations.add(timedIngressAction);
        }
        return actionConfigurations;
    }

    @Override
    public FlowPlan toFlowPlan() {
        TimedDataSourcePlan flowPlan = new TimedDataSourcePlan(getName(), FlowType.TIMED_DATA_SOURCE, getDescription(), getTopic(), timedIngressAction, cronSchedule);
        flowPlan.setSourcePlugin(getSourcePlugin());
        return flowPlan;
    }

    public static TimedDataSourcePlanEntity fromFlowPlan(FlowPlan flowPlan) {
        if (!(flowPlan instanceof TimedDataSourcePlan timedDataSourcePlan)) {
            throw new IllegalArgumentException("Incorrect flow plan type");
        }
        TimedDataSourcePlanEntity flowPlanEntity = new TimedDataSourcePlanEntity();
        flowPlanEntity.setName(timedDataSourcePlan.getName());
        flowPlanEntity.setDescription(timedDataSourcePlan.getDescription());
        flowPlanEntity.setTimedIngressAction(timedDataSourcePlan.getTimedIngressAction());
        flowPlanEntity.setCronSchedule(timedDataSourcePlan.getCronSchedule());
        flowPlanEntity.setSourcePlugin(timedDataSourcePlan.getSourcePlugin());
        flowPlanEntity.setTopic(timedDataSourcePlan.getTopic());

        return flowPlanEntity;
    }
}