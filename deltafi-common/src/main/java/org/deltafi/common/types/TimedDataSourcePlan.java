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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimedDataSourcePlan extends DataSourcePlan {
    private ActionConfiguration timedIngressAction;
    private String cronSchedule;

    @JsonCreator
    @SuppressWarnings("unused")
    public TimedDataSourcePlan(@JsonProperty(value = "name", required = true) String name,
                               @JsonProperty(value = "type") FlowType type,
                               @JsonProperty(value = "description", required = true) String description,
                               @JsonProperty(value = "metadata") Map<String, String> metadata,
                               @JsonProperty(value = "annotationConfig") AnnotationConfig annotationConfig,
                               @JsonProperty(value = "topic", required = true) String topic,
                               @JsonProperty(value = "timedIngressAction", required = true) ActionConfiguration timedIngressAction,
                               @JsonProperty(value = "cronSchedule", required = true) String cronSchedule) {
        super(name, FlowType.TIMED_DATA_SOURCE, description, metadata, annotationConfig, topic);
        this.timedIngressAction = timedIngressAction;
        this.cronSchedule = cronSchedule;
    }

    public TimedDataSourcePlan(String name, FlowType type, String description, String topic, ActionConfiguration timedIngressAction, String cronSchedule) {
        this(name, FlowType.TIMED_DATA_SOURCE, description, null, null, topic, timedIngressAction, cronSchedule);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (timedIngressAction != null) {
            actionConfigurations.add(timedIngressAction);
        }
        return actionConfigurations;
    }
}
