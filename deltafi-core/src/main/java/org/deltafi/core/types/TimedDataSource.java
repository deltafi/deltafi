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

import com.fasterxml.jackson.annotation.JsonCreator;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.core.services.CoreEventQueue;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@DiscriminatorValue("TIMED_DATA_SOURCE")
@EqualsAndHashCode(callSuper = true)
@Data
public class TimedDataSource extends DataSource {
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private ActionConfiguration timedIngressAction;
    private String cronSchedule;

    private OffsetDateTime lastRun;
    private OffsetDateTime nextRun;
    private String memo;
    private UUID currentDid;
    private boolean executeImmediate = false;
    @Enumerated(EnumType.STRING)
    private IngressStatus ingressStatus = IngressStatus.HEALTHY;
    private String ingressStatusMessage;

    private static final Duration TASKING_TIMEOUT = Duration.ofSeconds(30);
    private static final String TYPE = "TIMED_DATA_SOURCE";

    @JsonCreator
    @PersistenceCreator
    public TimedDataSource() {
        super(FlowType.TIMED_DATA_SOURCE);
    }

    @Builder
    public TimedDataSource(String name, String topic, String description, ActionConfiguration timedIngressAction, String cronSchedule) {
        this.setTopic(topic);
        this.setName(name);
        this.setDescription(description);
        this.timedIngressAction = timedIngressAction;
        this.cronSchedule = cronSchedule;
    }

    @Override
    public void copyFields(DataSource sourceFlow) {
        if (sourceFlow instanceof TimedDataSource timedDataSource) {
            setCronSchedule(timedDataSource.getCronSchedule());
            setLastRun(timedDataSource.getLastRun());
            setNextRun(timedDataSource.getNextRun());
            setMemo(timedDataSource.getMemo());
            setCurrentDid(timedDataSource.getCurrentDid());
            setExecuteImmediate(timedDataSource.isExecuteImmediate());
        }
    }

    public ActionConfiguration findActionConfigByName(String actionNamed) {
        return ((timedIngressAction != null) && Objects.equals(timedIngressAction.getName(), actionNamed)) ? timedIngressAction : null;
    }

    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (timedIngressAction != null) {
            actionConfigurations.add(timedIngressAction);
        }
        return actionConfigurations;
    }

    @Override
    public void updateActionNamesByFamily(Map<ActionType, ActionFamily> actionFamilyMap) {
        if (timedIngressAction != null) {
            updateActionNamesByFamily(actionFamilyMap, ActionType.TIMED_INGRESS, timedIngressAction.getName());
        }
    }

    public boolean due(CoreEventQueue coreEventQueue, OffsetDateTime now) {
        if (executeImmediate) {
            return true;
        }

        if (currentDid != null && (lastRun.plus(TASKING_TIMEOUT).isAfter(now) ||
                coreEventQueue.longRunningTaskExists(timedIngressAction.getType(), timedIngressAction.getName(), currentDid))) {
            return false;
        }

        return nextRun == null || nextRun.isBefore(now);
    }

    /**
     * Create the ActionInput that should be sent to an Action
     * @param systemName system name to set in context
     * @return ActionInput containing the ActionConfiguration
     */
    public WrappedActionInput buildActionInput(String systemName, OffsetDateTime now) {
        DeltaFile deltaFile = DeltaFile.builder()
                .did(UUID.randomUUID())
                .dataSource(getName())
                .build();
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .name(getName())
                .number(0)
                .build();
        Action action = Action.builder()
                .name(timedIngressAction.getName())
                .number(0)
                .created(now)
                .state(ActionState.QUEUED)
                .build();
        return deltaFile.buildActionInput(timedIngressAction, flow, action, systemName, null, memo);
    }
}
