/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.delete;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.api.types.DeletePolicy;
import org.deltafi.core.domain.api.types.DiskSpaceDeletePolicy;
import org.deltafi.core.domain.api.types.TimedDeletePolicy;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.services.DeletePolicyService;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.DiskSpaceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class DeleteRunner {
    private final DeltaFilesService deltaFilesService;
    private final DiskSpaceService diskSpaceService;
    private final DeletePolicyService deletePolicyService;
    private final DeltaFiProperties deltaFiProperties;

    public void runDeletes() {
        List<DeletePolicyWorker> policiesScheduled = refreshPolicies();
        policiesScheduled.forEach(DeletePolicyWorker::run);
    }

    public List<DeletePolicyWorker> refreshPolicies() {
        List<DeletePolicyWorker> policies = new ArrayList<>();
        int batchSize = deltaFiProperties.getDelete().getPolicyBatchSize() > 0 ? deltaFiProperties.getDelete().getPolicyBatchSize() : 1000;
        for (DeletePolicy policy : deletePolicyService.getEnabledPolicies()) {
            if (policy instanceof DiskSpaceDeletePolicy) {
                policies.add(new DiskSpaceDelete(batchSize, deltaFilesService, diskSpaceService, (DiskSpaceDeletePolicy) policy));
            } else if (policy instanceof TimedDeletePolicy) {
                policies.add(new TimedDelete(batchSize, deltaFilesService, (TimedDeletePolicy) policy));
            } else {
                throw new IllegalArgumentException("Unknown delete policy type " + policy.getClass().getSimpleName());
            }

        }
        log.debug("Preparing to execute " + policies.size() + " policies using a batch size of " + batchSize);
        return policies;
    }

}
