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
package org.deltafi.core.delete;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class DeleteRunner {
    private final DeltaFilesService deltaFilesService;
    private final DiskSpaceDelete diskSpaceDelete;
    private final MetadataDelete metadataDelete;
    private final DeletePolicyService deletePolicyService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public void runDeletes() {
        // do not run metadata delete in the loop as metadata statistics may refresh slowly in the db stats
        metadataDelete.run();

        // refresh policies places disk delete policies first
        // disk policies loop and delete files until disk pressure has been relieved
        // timed policies only run the first batch and return true if there's more to be deleted
        // instead of running timed policy batches back-to-back, recheck disk policies first
        // so that timed policies don't loop and block while disk is filling
        boolean rerun;
        do {
            // run this inside the loop in case policies change while deletes are running
            boolean needSpace = true;
            while (needSpace) {
                needSpace = diskSpaceDelete.run();
            }

            List<DeletePolicyWorker> policiesScheduled = refreshPolicies();
            rerun = false;
            for (DeletePolicyWorker policy : policiesScheduled) {
                if (policy.run(deltaFiPropertiesService.getDeltaFiProperties().getDeletePolicyBatchSize())) {
                    rerun = true;
                }
            }
        } while (rerun);
    }

    public List<DeletePolicyWorker> refreshPolicies() {
        List<DeletePolicyWorker> policies = new ArrayList<>();
        for (DeletePolicy policy : deletePolicyService.getEnabledPolicies()) {
           if (policy instanceof TimedDeletePolicy timedDeletePolicy) {
                policies.add(new TimedDelete(deltaFilesService, timedDeletePolicy));
            } else {
                throw new IllegalArgumentException("Unknown delete policy type " + policy.getClass().getSimpleName());
            }

        }
        log.debug("Preparing to execute {}", policies.size());
        return policies;
    }

}
