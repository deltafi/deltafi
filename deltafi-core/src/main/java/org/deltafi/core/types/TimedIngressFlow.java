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

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.ActionFamily;
import org.deltafi.common.types.PublishRules;
import org.deltafi.common.types.Publisher;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Document
@Data
@EqualsAndHashCode(callSuper = true)
public class TimedIngressFlow extends Flow implements Publisher {
    private static final Duration TASKING_TIMEOUT = Duration.ofSeconds(30);

    private TimedIngressActionConfiguration timedIngressAction;
    private String targetFlow;
    private String cronSchedule;

    private OffsetDateTime lastRun;
    private OffsetDateTime nextRun;
    private String memo;
    private String currentDid;
    private boolean executeImmediate = false;
    private IngressStatus ingressStatus = IngressStatus.HEALTHY;
    private String ingressStatusMessage;
    private PublishRules publishRules;

    /**
     * Schema versions:
     * 2 - original
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private int schemaVersion;

    @Override
    public boolean migrate() {
        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            return true;
        }

        return false;
    }

    @Override
    public ActionConfiguration findActionConfigByName(String actionNamed) {
        return ((timedIngressAction != null) && nameMatches(timedIngressAction, actionNamed)) ? timedIngressAction : null;
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
    public List<DeltaFiConfiguration> findByConfigType(ConfigType configType)  {
        return switch (configType) {
            case TIMED_INGRESS_FLOW -> List.of(asFlowConfiguration());
            case TIMED_INGRESS_ACTION -> timedIngressAction != null ? List.of(timedIngressAction) : Collections.emptyList();
            default -> Collections.emptyList();
        };
    }

    @Override
    public void updateActionNamesByFamily(EnumMap<ActionType, ActionFamily> actionFamilyMap) {
        if (timedIngressAction != null) {
            updateActionNamesByFamily(actionFamilyMap, ActionType.TIMED_INGRESS, timedIngressAction.getName());
        }
    }

    @Override
    public DeltaFiConfiguration asFlowConfiguration() {
        TimedIngressFlowConfiguration timedIngressFlowConfiguration = new TimedIngressFlowConfiguration(name);
        if (timedIngressAction != null) {
            timedIngressFlowConfiguration.setTimedIngressAction(timedIngressAction.getName());
        }
        timedIngressFlowConfiguration.setCronSchedule(cronSchedule);
        return timedIngressFlowConfiguration;
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

    @Override
    public PublishRules publishRules() {
        return publishRules;
    }
}
