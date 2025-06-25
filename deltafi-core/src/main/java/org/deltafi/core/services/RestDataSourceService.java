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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.RestDataSourcePlan;
import org.deltafi.core.converters.RestDataSourcePlanConverter;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.core.repo.RestDataSourceRepo;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.RestDataSourceSnapshot;
import org.deltafi.core.types.*;
import org.deltafi.core.validation.FlowValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RestDataSourceService extends DataSourceService<RestDataSourcePlan, RestDataSource, RestDataSourceSnapshot, RestDataSourceRepo> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final RestDataSourcePlanConverter REST_DATA_SOURCE_FLOW_PLAN_CONVERTER = new RestDataSourcePlanConverter();
    private final RateLimitService rateLimitService;

    public RestDataSourceService(RestDataSourceRepo restDataSourceRepo, PluginVariableService pluginVariableService,
                                 FlowValidator flowValidator, BuildProperties buildProperties,
                                 ErrorCountService errorCountService, FlowCacheService flowCacheService,
                                 EventService eventService, RateLimitService rateLimitService) {
        super(FlowType.REST_DATA_SOURCE, restDataSourceRepo, pluginVariableService, REST_DATA_SOURCE_FLOW_PLAN_CONVERTER,
                flowValidator, buildProperties, flowCacheService, eventService, RestDataSource.class, RestDataSourcePlan.class,
                errorCountService);
        this.rateLimitService = rateLimitService;
    }

    @PostConstruct
    @Override
    public void postConstruct() {
        super.postConstruct();
        initializeRateLimitBuckets();
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setRestDataSources(getAll().stream().map(RestDataSourceSnapshot::new).toList());
    }

    @Override
    public List<RestDataSourceSnapshot> getFlowSnapshots(Snapshot snapshot) {
        return snapshot.getRestDataSources();
    }

    @Override
    protected boolean updateSpecificDataSourceFields(RestDataSource flow, RestDataSourceSnapshot dataSourceSnapshot, Result result) {
        boolean changed = false;

        if (!Objects.equals(flow.getRateLimit(), dataSourceSnapshot.getRateLimit())) {
            RateLimit newRateLimit = dataSourceSnapshot.getRateLimit();

            flow.setRateLimit(newRateLimit);

            if (newRateLimit == null) {
                rateLimitService.removeBucket(flow.getName());
                log.debug("Removed rate limit bucket for '{}' during snapshot restore", flow.getName());
            } else {
                // Rate limit was added or changed
                Duration period = Duration.ofSeconds(newRateLimit.getDurationSeconds());
                rateLimitService.updateLimit(flow.getName(), newRateLimit.getMaxAmount(), period);
                log.debug("Updated rate limit bucket for '{}' during snapshot restore: {} {} per {} seconds", 
                    flow.getName(), newRateLimit.getMaxAmount(), newRateLimit.getUnit(), newRateLimit.getDurationSeconds());
            }

            changed = true;
        }

        return changed;
    }

    /**
     * Sets the rate limit for a given dataSource, identified by its name.
     * If the rate limit for the dataSource is already set to the specified value, the method
     * logs a warning and returns false. If the update is successful, the method refreshes the
     * flow cache and returns true.
     *
     * @param flowName  the name of the dataSource for which to set the rate limit
     * @param rateLimit the rate limit configuration to set
     * @return true if the update was successful, false otherwise
     */
    public boolean setRateLimit(String flowName, RateLimitInput rateLimit) {
        RestDataSource flow = getFlowOrThrow(flowName);

        if (isRateLimitEqual(flow.getRateLimit(), rateLimit)) {
            log.warn("Tried to set rate limit on dataSource {} to {} when already set", flowName, rateLimit);
            return false;
        }

        try {
            String rateLimitJson = OBJECT_MAPPER.writeValueAsString(rateLimit);
            Flow updatedFlow = flowRepo.updateRateLimit(flowName, rateLimitJson);
            if (updatedFlow != null) {
                flowCacheService.updateCache(updatedFlow);
                Duration period = Duration.ofSeconds(rateLimit.getDurationSeconds());
                rateLimitService.updateLimit(flowName, rateLimit.getMaxAmount(), period);
                return true;
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rate limit for dataSource {}: {}", flowName, e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Removes the rate limit for a given dataSource, identified by its name.
     * If the dataSource does not have a rate limit set, the method logs a warning and returns false.
     * If the removal is successful, the method refreshes the flow cache and returns true.
     *
     * @param flowName the name of the dataSource for which to remove the rate limit
     * @return true if the removal was successful, false otherwise
     */
    public boolean removeRateLimit(String flowName) {
        RestDataSource flow = getFlowOrThrow(flowName);

        if (flow.getRateLimit() == null) {
            log.warn("Tried to remove rate limit on dataSource {} when none is set", flowName);
            return false;
        }

        Flow updatedFlow = flowRepo.removeRateLimit(flowName);
        if (updatedFlow != null) {
            flowCacheService.updateCache(updatedFlow);
            rateLimitService.removeBucket(flowName);
            return true;
        }

        return false;
    }


    private boolean isRateLimitEqual(RateLimit existing, RateLimitInput input) {
        if (existing == null && input == null) {
            return true;
        }
        if (existing == null || input == null) {
            return false;
        }
        return existing.getUnit() == input.getUnit() &&
                existing.getMaxAmount() == input.getMaxAmount() &&
                existing.getDurationSeconds() == input.getDurationSeconds();
    }

    /**
     * Initialize rate limit buckets for all existing flows that have rate limits configured.
     */
    private void initializeRateLimitBuckets() {
        log.info("Initializing rate limit buckets for existing REST data sources");

        List<RestDataSource> flowsWithRateLimits = getAll().stream()
                .filter(flow -> flow.getRateLimit() != null)
                .toList();

        for (RestDataSource flow : flowsWithRateLimits) {
            try {
                RateLimit rateLimit = flow.getRateLimit();
                Duration period = Duration.ofSeconds(rateLimit.getDurationSeconds());
                rateLimitService.updateLimit(flow.getName(), rateLimit.getMaxAmount(), period);
                
                log.debug("Initialized rate limit bucket for '{}': {} {} per {} seconds", 
                    flow.getName(), rateLimit.getMaxAmount(), rateLimit.getUnit(), rateLimit.getDurationSeconds());
            } catch (Exception e) {
                log.error("Failed to initialize rate limit bucket for flow '{}': {}", flow.getName(), e.getMessage());
            }
        }

        log.info("Initialized rate limit buckets for {} REST data sources", flowsWithRateLimits.size());
    }
}