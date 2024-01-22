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
import lombok.Builder;
import lombok.Data;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFiConfiguration;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.IngressStatus;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.TimedDataSourceConfiguration;
import org.deltafi.common.types.TimedIngressActionConfiguration;
import org.deltafi.core.generated.types.ActionFamily;
import org.springframework.data.annotation.PersistenceCreator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Data
public class TimedDataSource extends DataSource {
    private static final Duration TASKING_TIMEOUT = Duration.ofSeconds(30);
    private static final String TYPE = "TIMED_DATA_SOURCE";

    private TimedIngressActionConfiguration timedIngressAction;
    private String cronSchedule;

    private OffsetDateTime lastRun;
    private OffsetDateTime nextRun;
    private String memo;
    private String currentDid;
    private boolean executeImmediate = false;
    private IngressStatus ingressStatus = IngressStatus.HEALTHY;
    private String ingressStatusMessage;

    /**
     * Schema versions:
     * 2 - original
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    @JsonCreator
    @PersistenceCreator
    public TimedDataSource() {
        super(TYPE);
    }

    @Builder
    public TimedDataSource(String name, String topic, String description, TimedIngressActionConfiguration timedIngressAction, String cronSchedule) {
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

    @Override
    public boolean migrate() {
        if (getSchemaVersion() < CURRENT_SCHEMA_VERSION) {
            setSchemaVersion(CURRENT_SCHEMA_VERSION);
            return true;
        }

        return false;
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

    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        return switch (configType) {
            case TIMED_INGRESS_FLOW -> List.of(asFlowConfiguration());
            case TIMED_INGRESS_ACTION -> timedIngressAction != null ? List.of(timedIngressAction) : Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        if (timedIngressAction != null) {
            updateActionNamesByFamily(actionFamilyMap, ActionType.TIMED_INGRESS, timedIngressAction.getName());
        }
    }

    public DeltaFiConfiguration asFlowConfiguration() {
        TimedDataSourceConfiguration dataSourceConfiguration = new TimedDataSourceConfiguration(name);
        dataSourceConfiguration.setTopic(getTopic());
        if (timedIngressAction != null) {
            dataSourceConfiguration.setTimedIngressAction(timedIngressAction.getName());
        }
        dataSourceConfiguration.setCronSchedule(cronSchedule);
        return dataSourceConfiguration;
    }

    public boolean due(ActionEventQueue actionEventQueue, OffsetDateTime now) {
        if (executeImmediate) {
            return true;
        }

        if (currentDid != null && (lastRun.plus(TASKING_TIMEOUT).isAfter(now) ||
                actionEventQueue.longRunningTaskExists(timedIngressAction.getType(), timedIngressAction.getName(), currentDid))) {
            return false;
        }

        return nextRun == null || nextRun.isBefore(now);
    }

    /**
     * Create the ActionInput that should be sent to an Action
     * @param systemName system name to set in context
     * @return ActionInput containing the ActionConfiguration
     */
    public ActionInput buildActionInput(String systemName) {
        DeltaFile deltaFile = DeltaFile.builder()
                .did(UUID.randomUUID().toString())
                .sourceInfo(SourceInfo.builder().flow(name).build())
                .build();
        return timedIngressAction.buildActionInput(name, deltaFile, systemName, null, null, OffsetDateTime.now(), null, memo);
    }
}
