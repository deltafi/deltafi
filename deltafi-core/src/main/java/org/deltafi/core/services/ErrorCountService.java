/**
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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ErrorCountService {
    private final DeltaFilesService deltaFilesService;
    private final IngressFlowService ingressFlowService;

    private volatile Map<String, Integer> errorCounts = Collections.emptyMap();

    public void populateErrorCounts() {
        errorCounts = deltaFilesService.errorCountsByFlow(ingressFlowService.maxErrorsPerFlow().keySet());
    }

    /**
     * Determines if the number of errors for a given flow has exceeded the maximum allowed.
     *
     * @param flow The name of the flow to check, represented as a {@code String}.
     * @return A {@code String} value indicating the issue if the max errors were exceeded or null if the max errors have not been exceeded.
     */
    public String flowErrorsExceeded(String flow) {
        Integer errorCount = errorCounts.get(flow);
        if (errorCount != null && errorCount > 0) {
            Integer maxErrors = ingressFlowService.maxErrorsPerFlow().get(flow);
            String ret = null;
            if (maxErrors != null && maxErrors <= errorCount) {
                ret = "Maximum errors exceeded for flow " + flow + ": " + maxErrors + " errors allowed, " + errorCount + " errors found";
            }
            return ret;
        }
        return null;
    }
}
