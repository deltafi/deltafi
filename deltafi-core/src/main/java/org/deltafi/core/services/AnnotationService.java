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

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.snapshot.HasExpectedAnnotations;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class AnnotationService implements Snapshotter {

    private final DataSinkService dataSinkService;
    private final DeltaFilesService deltaFilesService;

    public AnnotationService(DataSinkService dataSinkService, DeltaFilesService deltaFilesService) {
        this.dataSinkService = dataSinkService;
        this.deltaFilesService = deltaFilesService;
    }

    /**
     * Find the dataSink with the given name and update the set of expected annotations.
     * If the dataSource no longer has expected annotations remove the dataSource from all DeltaFiles
     * that have the dataSource in their pendingAnnotationsForFlow set.
     * @param flowName name of the dataSource to update
     * @param expectedAnnotations new set of expected annotations
     * @return true if the set of expected annotations changed
     */
    public boolean setExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        if (expectedAnnotations != null && expectedAnnotations.isEmpty()) {
            expectedAnnotations = null;
        }

        boolean updated = dataSinkService.setExpectedAnnotations(flowName, expectedAnnotations);

        if (updated) {
            deltaFilesService.updatePendingAnnotationsForFlows(flowName, expectedAnnotations);
        }

        return updated;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        // Nothing to be done here
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        Result result = new Result();
        if (systemSnapshot.getDataSinks() != null) {
            systemSnapshot.getDataSinks().forEach(dataSinkSnapshot -> resetFromSnapshot(dataSinkSnapshot, result));
        }
        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }

    public void resetFromSnapshot(HasExpectedAnnotations flowSnapshot, Result result) {
        try {
            setExpectedAnnotations(flowSnapshot.getName(), flowSnapshot.getExpectedAnnotations());
        } catch (IllegalArgumentException | DgsEntityNotFoundException e) {
            result.getInfo().add("Flow " + flowSnapshot.getName() + " is no longer installed");
        }
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.EXPECTED_ANNOTATIONS_ORDER;
    }
}
