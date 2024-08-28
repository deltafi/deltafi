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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.converters.RestDataSourcePlanConverter;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.repo.RestDataSourceRepo;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.RestDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.RestDataSourceValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RestDataSourceService extends FlowService<RestDataSourcePlanEntity, RestDataSource, RestDataSourceSnapshot, RestDataSourceRepo> {

    private static final RestDataSourcePlanConverter REST_DATA_SOURCE_FLOW_PLAN_CONVERTER = new RestDataSourcePlanConverter();
    private final ErrorCountService errorCountService;

    public RestDataSourceService(RestDataSourceRepo restDataSourceRepo, PluginVariableService pluginVariableService,
                                 RestDataSourceValidator restDataSourceValidator, BuildProperties buildProperties,
                                 ErrorCountService errorCountService, FlowCacheService flowCacheService) {
        super(FlowType.REST_DATA_SOURCE, restDataSourceRepo, pluginVariableService, REST_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                restDataSourceValidator, buildProperties, flowCacheService, RestDataSource.class, RestDataSourcePlanEntity.class);

        this.errorCountService = errorCountService;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        List<RestDataSourceSnapshot> restDataSourceSnapshots = new ArrayList<>();
        for (RestDataSource dataSource : getAll()) {
            restDataSourceSnapshots.add(new RestDataSourceSnapshot(dataSource));
        }
        systemSnapshot.setRestDataSources(restDataSourceSnapshots);
    }

    @Override
    public List<RestDataSourceSnapshot> getFlowSnapshots(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getRestDataSources();
    }

    @Override
    public boolean flowSpecificUpdateFromSnapshot(RestDataSource flow, RestDataSourceSnapshot dataSourceSnapshot, Result result) {
        boolean changed = false;
        if (!Objects.equals(flow.getTopic(), dataSourceSnapshot.getTopic())) {
            flow.setTopic(dataSourceSnapshot.getTopic());
            changed = true;
        }

        if (flow.getMaxErrors() != dataSourceSnapshot.getMaxErrors()) {
            flow.setMaxErrors(dataSourceSnapshot.getMaxErrors());
            changed = true;
        }

        return changed;
    }

    /**
     * Sets the maximum number of errors allowed for a given flow, identified by its name.
     * If the maximum errors for the flow are already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the flow to update, represented as a {@code String}.
     * @param maxErrors The new maximum number of errors to be set for the specified flow, as an {@code int}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMaxErrors(String flowName, int maxErrors) {
        RestDataSource flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on transform flow {} to {} when already set", flowName, maxErrors);
            return false;
        }

        if (flowRepo.updateMaxErrors(flowName, maxErrors) > 0) {
            flowCacheService.refreshCache();
            return true;
        }

        return false;
    }

    /**
     * Retrieves a map containing the maximum number of errors allowed per flow.
     * This method filters out flows with a maximum error count of 0, only including
     * those with a positive maximum error count.
     *
     * @return A {@code Map<String, Integer>} where each key represents a flow name,
     * and the corresponding value is the maximum number of errors allowed for that flow.
     */
    public Map<String, Integer> maxErrorsPerFlow() {
        return getRunningFlows().stream()
                .filter(e -> e.getMaxErrors() >= 0)
                .collect(Collectors.toMap(Flow::getName, DataSource::getMaxErrors));
    }

    /**
     * Get a list of DataSourceErrorStates for data sources that have
     * exceeded their max allowed errors
     * @return list of IngressFlowErrorStates
     */
    public List<DataSourceErrorState> dataSourceErrorsExceeded() {
        return getRunningFlows().stream()
                .map(f -> new DataSourceErrorState(f.getName(), errorCountService.errorsForFlow(f.getName()), f.getMaxErrors()))
                .filter(s -> s.getMaxErrors() >= 0 && s.getCurrErrors() > s.getMaxErrors())
                .toList();
    }
}
