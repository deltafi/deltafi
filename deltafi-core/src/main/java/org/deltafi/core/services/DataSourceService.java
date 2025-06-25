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
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.converters.FlowPlanConverter;
import org.deltafi.core.generated.types.DataSourceErrorState;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.DataSource;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.snapshot.DataSourceSnapshot;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Abstract base class for data source services providing common functionality
 * for managing data sources including error handling and configuration updates.
 */
@Slf4j
public abstract class DataSourceService<P extends FlowPlan, F extends DataSource, S extends DataSourceSnapshot, R extends FlowRepo>
        extends FlowService<P, F, S, R> {

    protected final ErrorCountService errorCountService;

    public DataSourceService(FlowType flowType, R flowRepo, PluginVariableService pluginVariableService,
                            FlowPlanConverter<P, F> flowPlanConverter, FlowValidator flowValidator,
                            BuildProperties buildProperties, FlowCacheService flowCacheService,
                            EventService eventService, Class<F> flowClass, Class<P> planClass,
                            ErrorCountService errorCountService) {
        super(flowType, flowRepo, pluginVariableService, flowPlanConverter, flowValidator,
              buildProperties, flowCacheService, eventService, flowClass, planClass);
        this.errorCountService = errorCountService;
    }

    /**
     * Sets the maximum number of errors allowed for a given data source, identified by its name.
     * If the maximum errors for the data source are already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * cache and returns true.
     *
     * @param flowName  The name of the data source to update, represented as a {@code String}.
     * @param maxErrors The new maximum number of errors to be set for the specified data source, as an {@code int}.
     * @return A {@code boolean} value indicating whether the update was successful (true) or not (false).
     */
    public boolean setMaxErrors(String flowName, int maxErrors) {
        F flow = getFlowOrThrow(flowName);

        if (flow.getMaxErrors() == maxErrors) {
            log.warn("Tried to set max errors on data source {} to {} when already set", flowName, maxErrors);
            return false;
        }

        Flow updatedFlow = flowRepo.updateMaxErrors(flowName, maxErrors);
        if (updatedFlow != null) {
            flowCacheService.updateCache(updatedFlow);
            return true;
        }

        return false;
    }

    /**
     * Retrieves a map containing the maximum number of errors allowed per data source.
     * This method filters out flows with a maximum error count of 0, only including
     * those with a positive maximum error count.
     *
     * @return A {@code Map<String, Integer>} where each key represents a data source name,
     * and the corresponding value is the maximum number of errors allowed for that data source.
     */
    public Map<String, Integer> maxErrorsPerFlow() {
        return getRunningFlows().stream()
                .filter(e -> e.getMaxErrors() >= 0)
                .collect(Collectors.toMap(Flow::getName, DataSource::getMaxErrors));
    }

    /**
     * Get a list of DataSourceErrorStates for data sources that have
     * exceeded their max allowed errors
     * @return list of DataSourceErrorState
     */
    public List<DataSourceErrorState> dataSourceErrorsExceeded() {
        return getRunningFlows().stream()
                .map(f -> new DataSourceErrorState(f.getName(), errorCountService.errorsForFlow(f.getType(), f.getName()), f.getMaxErrors()))
                .filter(s -> s.getMaxErrors() >= 0 && s.getCurrErrors() > s.getMaxErrors())
                .toList();
    }

    /**
     * Template method that handles common data source field updates and delegates
     * specific field updates to concrete implementations.
     *
     * @param flow the data source flow to update
     * @param dataSourceSnapshot the snapshot containing the updated values
     * @param result the result object
     * @return true if any changes were made, false otherwise
     */
    public final boolean flowSpecificUpdateFromSnapshot(F flow, S dataSourceSnapshot, org.deltafi.core.types.Result result) {
        boolean changed = updateCommonDataSourceFields(flow, dataSourceSnapshot);
        return updateSpecificDataSourceFields(flow, dataSourceSnapshot, result) || changed;
    }

    /**
     * Updates common data source fields (topic and maxErrors) from snapshot.
     * This method handles the common field updates that are shared across all data source types.
     *
     * @param flow the data source flow to update
     * @param dataSourceSnapshot the snapshot containing the updated values
     * @return true if any changes were made, false otherwise
     */
    private boolean updateCommonDataSourceFields(F flow, S dataSourceSnapshot) {
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
     * Template method for concrete services to implement their specific field updates.
     * The base class handles common fields (topic, maxErrors) automatically.
     * Default implementation returns false (no specific fields to update).
     *
     * @param flow the data source flow to update
     * @param dataSourceSnapshot the snapshot containing the updated values
     * @param result the result object
     * @return true if any changes were made, false otherwise
     */
    protected boolean updateSpecificDataSourceFields(F flow, S dataSourceSnapshot, org.deltafi.core.types.Result result) {
        return false;
    }

}