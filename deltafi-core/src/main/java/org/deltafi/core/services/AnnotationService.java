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
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class AnnotationService {

    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final DeltaFilesService deltaFilesService;

    public AnnotationService(TransformFlowService transformFlowService, EgressFlowService egressFlowService, DeltaFilesService deltaFilesService) {
        this.transformFlowService = transformFlowService;
        this.egressFlowService = egressFlowService;
        this.deltaFilesService = deltaFilesService;
    }

    /**
     * Find the transform or egress flow with the given name and update the set of expected annotations.
     * If the flow no longer has expected annotations remove the flow from all DeltaFiles
     * that have the flow in their pendingAnnotationsForFlow set.
     * @param flowName name of the flow to update
     * @param expectedAnnotations new set of expected annotations
     * @return true if the set of expected annotations changed
     */
    public boolean setExpectedAnnotations(FlowType flowType, String flowName, Set<String> expectedAnnotations) {
        if (expectedAnnotations != null && expectedAnnotations.isEmpty()) {
            expectedAnnotations = null;
        }

        boolean updated;
        if (FlowType.TRANSFORM.equals(flowType)) {
            updated = transformFlowService.setExpectedAnnotations(flowName, expectedAnnotations);
        } else if (FlowType.EGRESS.equals(flowType)) {
            updated = egressFlowService.setExpectedAnnotations(flowName, expectedAnnotations);
        } else {
            throw new IllegalArgumentException("Invalid flow type " + flowType + " only transform and egress support expected annotations");
        }

        if (updated) {
            deltaFilesService.asyncUpdatePendingAnnotationsForFlows(flowName, expectedAnnotations);
        }

        return updated;
    }
}
