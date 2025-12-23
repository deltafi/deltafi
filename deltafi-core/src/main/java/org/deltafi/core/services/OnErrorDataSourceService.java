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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ErrorSourceFilter;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.OnErrorDataSourcePlan;
import org.deltafi.core.converters.OnErrorDataSourcePlanConverter;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.repo.OnErrorDataSourceRepo;
import org.deltafi.core.types.OnErrorDataSource;
import org.deltafi.core.types.snapshot.OnErrorDataSourceSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OnErrorDataSourceService extends DataSourceService<OnErrorDataSourcePlan, OnErrorDataSource, OnErrorDataSourceSnapshot, OnErrorDataSourceRepo> {

    private static final OnErrorDataSourcePlanConverter ON_ERROR_DATA_SOURCE_FLOW_PLAN_CONVERTER = new OnErrorDataSourcePlanConverter();

    public OnErrorDataSourceService(OnErrorDataSourceRepo onErrorDataSourceRepo,
                                   PluginVariableService pluginVariableService,
                                   FlowValidator flowValidator,
                                   BuildProperties buildProperties,
                                   ErrorCountService errorCountService,
                                   FlowCacheService flowCacheService,
                                   EventService eventService) {
        super(FlowType.ON_ERROR_DATA_SOURCE, onErrorDataSourceRepo, pluginVariableService,
              ON_ERROR_DATA_SOURCE_FLOW_PLAN_CONVERTER, flowValidator, buildProperties,
              flowCacheService, eventService, OnErrorDataSource.class, OnErrorDataSourcePlan.class, 
              errorCountService);
    }

    /**
     * Get all active OnError data sources that should be triggered for the given error context
     */
    public List<OnErrorDataSource> getTriggeredDataSources(String flowName, FlowType flowType, String actionName, String actionClass, String errorMessage, Map<String, String> metadata, Map<String, String> annotations) {
        return getRunningFlows().stream()
                .filter(dataSource -> shouldTrigger(dataSource, flowName, flowType, actionName, actionClass, errorMessage, metadata, annotations))
                .toList();
    }

    private boolean shouldTrigger(OnErrorDataSource dataSource, String flowName, FlowType flowType, String actionName, String actionClass, String errorMessage, Map<String, String> metadata, Map<String, String> annotations) {
        // Check error message regex
        if (dataSource.getErrorMessageRegex() != null && !errorMessage.matches(dataSource.getErrorMessageRegex())) {
            return false;
        }

        // Check source filters (OR logic between filters, AND logic within each filter)
        if (dataSource.getSourceFilters() != null && !dataSource.getSourceFilters().isEmpty()) {
            boolean matchesAnyFilter = false;
            for (ErrorSourceFilter filter : dataSource.getSourceFilters()) {
                if (matchesFilter(filter, flowName, flowType, actionName, actionClass)) {
                    matchesAnyFilter = true;
                    break;
                }
            }
            if (!matchesAnyFilter) {
                return false;
            }
        }

        // Check metadata filters
        if (dataSource.getMetadataFilters() != null && !dataSource.getMetadataFilters().isEmpty()) {
            for (var filter : dataSource.getMetadataFilters()) {
                String value = metadata.get(filter.getKey());
                if (!filter.getValue().equals(value)) {
                    return false;
                }
            }
        }

        // Check annotation filters
        if (dataSource.getAnnotationFilters() != null && !dataSource.getAnnotationFilters().isEmpty()) {
            for (var filter : dataSource.getAnnotationFilters()) {
                String value = annotations.get(filter.getKey());
                if (!filter.getValue().equals(value)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchesFilter(ErrorSourceFilter filter, String flowName, FlowType flowType, String actionName, String actionClass) {
        // All specified fields in the filter must match (AND logic)
        if (filter.getFlowType() != null && !filter.getFlowType().equals(flowType)) {
            return false;
        }
        if (filter.getFlowName() != null && !filter.getFlowName().equals(flowName)) {
            return false;
        }
        if (filter.getActionName() != null && !filter.getActionName().equals(actionName)) {
            return false;
        }
        if (filter.getActionClass() != null && !filter.getActionClass().equals(actionClass)) {
            return false;
        }
        return true;
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setOnErrorDataSources(getAll().stream().map(OnErrorDataSourceSnapshot::new).toList());
    }

    @Override
    public List<OnErrorDataSourceSnapshot> getFlowSnapshots(Snapshot snapshot) {
        return snapshot.getOnErrorDataSources();
    }

    @Override
    protected OnErrorDataSource createPlaceholderFlow(OnErrorDataSourceSnapshot snapshot) {
        OnErrorDataSource flow = new OnErrorDataSource();
        flow.setName(snapshot.getName());
        flow.setDescription("Placeholder for " + snapshot.getName() + " - waiting for plugin to install");
        flow.setSourcePlugin(snapshot.getSourcePlugin());
        flow.getFlowStatus().setState(snapshot.isRunning() ? FlowState.RUNNING : FlowState.STOPPED);
        flow.getFlowStatus().setTestMode(snapshot.isTestMode());
        flow.getFlowStatus().setValid(false);
        flow.getFlowStatus().setErrors(new ArrayList<>(List.of(
            FlowConfigError.newBuilder()
                .configName(snapshot.getName())
                .errorType(FlowErrorType.INVALID_CONFIG)
                .message("Waiting for plugin " + snapshot.getSourcePlugin() + " to install")
                .build()
        )));
        flow.getFlowStatus().setPlaceholder(true);
        flow.setTopic(snapshot.getTopic());
        flow.setMaxErrors(snapshot.getMaxErrors());
        return flow;
    }
}
