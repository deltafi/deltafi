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
package org.deltafi.core.delete;

import lombok.Getter;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.types.TimedDeletePolicy;

import java.time.Duration;
import java.time.OffsetDateTime;

@Getter
public class TimedDelete extends DeletePolicyWorker {
    private final Duration afterCreate;
    private final Duration afterComplete;
    private final long minBytes;
    private final String flow;
    private final boolean deleteMetadata;

    public TimedDelete(DeltaFilesService deltaFilesService, TimedDeletePolicy policy) {
        super(deltaFilesService, policy.getName());

        afterCreate = policy.getAfterCreate() != null ? Duration.parse(policy.getAfterCreate()) : null;
        afterComplete = policy.getAfterComplete() != null ? Duration.parse(policy.getAfterComplete()) : null;
        minBytes = policy.getMinBytes() != null ? policy.getMinBytes() : 0;
        deleteMetadata = policy.isDeleteMetadata();

        if ((minBytes == 0 && afterCreate == null && afterComplete == null) || (afterCreate != null && afterComplete != null)) {
            throw new IllegalArgumentException("Timed delete policy " + name + " must specify exactly one of afterCreate or afterComplete and/or minBytes");
        }

        flow = policy.getFlow();
    }

    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdBefore = afterCreate == null ? null : now.minus(afterCreate);
        OffsetDateTime completedBefore = afterComplete == null ? null : now.minus(afterComplete);
        deltaFilesService.delete(createdBefore, completedBefore, minBytes, flow, name, deleteMetadata);
    }
}
