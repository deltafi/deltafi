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

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.repo.DeltaFileFlowRepo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ErrorCountService {
    private final DeltaFileFlowRepo deltaFileFlowRepo;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;

    private volatile Map<FlowKey, Integer> errorCounts = Collections.emptyMap();
    private volatile Map<FlowKey, String> exceededFlowErrors = Collections.emptyMap();

    public ErrorCountService(DeltaFileFlowRepo deltaFileFlowRepo, @Lazy RestDataSourceService restDataSourceService,
                             @Lazy TimedDataSourceService timedDataSourceService) {
        this.deltaFileFlowRepo = deltaFileFlowRepo;
        this.restDataSourceService = restDataSourceService;
        this.timedDataSourceService = timedDataSourceService;
    }

    public synchronized void populateErrorCounts(Set<String> dataSourceNames) {
        if (dataSourceNames.isEmpty()) {
            errorCounts = Collections.emptyMap();
            exceededFlowErrors = Collections.emptyMap();
        } else {
            Map<String, Integer> rawErrorCounts = deltaFileFlowRepo.errorCountsByDataSource(dataSourceNames);
            errorCounts = rawErrorCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0) // Skip flows with 0 errors
                    .collect(Collectors.toMap(
                            entry -> new FlowKey(determineFlowType(entry.getKey()), entry.getKey()),
                            Map.Entry::getValue
                    ));
            updateExceededFlows();
        }
    }

    private void updateExceededFlows() {
        Map<FlowKey, String> newExceededFlowErrors = new HashMap<>();
        for (Map.Entry<FlowKey, Integer> entry : errorCounts.entrySet()) {
            FlowKey flowKey = entry.getKey();
            int errorCount = entry.getValue();

            Integer maxErrors = maxErrorsForFlow(flowKey.flowType, flowKey.flowName);
            if (maxErrors != null && errorCount > maxErrors) {
                String errorMessage = "Maximum errors exceeded for dataSource " + flowKey.flowName + ": " + maxErrors + " error" +
                        (maxErrors == 1 ? "" : "s") + " allowed, " + errorCount + " error" +
                        (errorCount == 1 ? "" : "s") + " found";
                newExceededFlowErrors.put(flowKey, errorMessage);
            }
        }
        exceededFlowErrors = Collections.unmodifiableMap(newExceededFlowErrors);
    }

    private FlowType determineFlowType(String flow) {
        if (restDataSourceService.hasRunningFlow(flow)) {
            return FlowType.REST_DATA_SOURCE;
        } else if (timedDataSourceService.hasRunningFlow(flow)) {
            return FlowType.TIMED_DATA_SOURCE;
        }
        return null;
    }

    private Integer maxErrorsForFlow(FlowType flowType, String flow) {
        if (flowType == FlowType.REST_DATA_SOURCE) {
            return restDataSourceService.maxErrorsPerFlow().get(flow);
        } else if (flowType == FlowType.TIMED_DATA_SOURCE) {
            return timedDataSourceService.maxErrorsPerFlow().get(flow);
        }
        return null;
    }

    public void checkErrorsExceeded(FlowType flowType, String flow) throws IngressUnavailableException {
        String errorMessage = exceededFlowErrors.get(new FlowKey(flowType, flow));
        if (errorMessage != null) {
            throw new IngressUnavailableException(errorMessage);
        }
    }

    public int errorsForFlow(FlowType flowType, String flow) {
        return errorCounts.getOrDefault(new FlowKey(flowType, flow), 0);
    }

    private record FlowKey(FlowType flowType, String flowName) {}
}
