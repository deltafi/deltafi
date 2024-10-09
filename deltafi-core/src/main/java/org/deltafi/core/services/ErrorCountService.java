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
package org.deltafi.core.services;

import org.deltafi.core.repo.ActionRepo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
public class ErrorCountService {
    private final ActionRepo actionRepo;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;

    private volatile Map<String, Integer> errorCounts = Collections.emptyMap();

    public ErrorCountService(ActionRepo actionRepo, @Lazy RestDataSourceService restDataSourceService,
                             @Lazy TimedDataSourceService timedDataSourceService) {
        this.actionRepo = actionRepo;
        this.restDataSourceService = restDataSourceService;
        this.timedDataSourceService = timedDataSourceService;
    }

    public synchronized void populateErrorCounts(Set<String> flowNames) {
        if (flowNames.isEmpty()) {
            errorCounts = Collections.emptyMap();
        } else {
            errorCounts = actionRepo.errorCountsByFlow(flowNames);
        }
    }

    /**
     * Return number of errors detected for dataSource
     *
     * @param flow the dataSource name
     * @return number of errors
     */
    public int errorsForFlow(String flow) {
        return errorCounts.getOrDefault(flow, 0);
    }

    private Integer maxErrorsForFlow(String flow) {
        if (restDataSourceService.hasRunningFlow(flow)) {
            return restDataSourceService.maxErrorsPerFlow().get(flow);
        } else if (timedDataSourceService.hasRunningFlow(flow)) {
            return timedDataSourceService.maxErrorsPerFlow().get(flow);
        }

        return null;
    }

    /**
     * Determines if the number of errors for a given dataSource has exceeded the maximum allowed.
     *
     * @param flow The name of the dataSource to check, represented as a {@code String}.
     * @return A {@code boolean} value indicating whether the max errors were exceeded.
     */
    public boolean flowErrorsExceeded(String flow) {
        Integer maxErrors = maxErrorsForFlow(flow);
        if (maxErrors == null) {
            return false;
        }
        return  maxErrors < errorsForFlow(flow);
    }

    /**
     * Generates an error message if the number of errors for a given dataSource has exceeded the maximum allowed.
     *
     * @param flow The name of the dataSource to check, represented as a {@code String}.
     * @return A {@code String} value containing the error message if the max errors were exceeded or {@code null} if the max errors have not been exceeded.
     */
    public String generateErrorMessage(String flow) {
        if (flowErrorsExceeded(flow)) {
            int errorCount = errorsForFlow(flow);
            Integer maxErrors = maxErrorsForFlow(flow);
            return "Maximum errors exceeded for dataSource " + flow + ": " + maxErrors + " error" +
                    (maxErrors == null || maxErrors == 1 ? "" : "s") + " allowed, " + errorCount + " error" +
                    (errorCount == 1 ? "" : "s") + " found";
        }
        return null;
    }
}
