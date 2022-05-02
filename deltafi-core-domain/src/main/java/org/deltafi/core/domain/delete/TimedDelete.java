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

import lombok.Getter;
import org.deltafi.core.domain.services.DeltaFilesService;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
public class TimedDelete extends DeletePolicy {
    private final Duration afterCreate;
    private final Duration afterComplete;
    private final String flow;

    public TimedDelete(DeltaFilesService deltaFilesService, String name, Map<String, String> parameters) {
        super(deltaFilesService, name, parameters);

        afterCreate = getParameters().containsKey("afterCreate") ? Duration.parse(getParameters().get("afterCreate")) : null;
        afterComplete = getParameters().containsKey("afterComplete") ? Duration.parse(getParameters().get("afterComplete")) : null;

        if ((afterCreate == null && afterComplete == null) || (afterCreate != null && afterComplete != null)) {
            throw new IllegalArgumentException("Timed delete policy " + name + " must specify exactly one afterCreate or afterComplete Durations");
        }

        flow = getParameters().get("flow");
    }

    public void run() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime createdBefore = afterCreate == null ? null : now.minus(afterCreate);
        OffsetDateTime completedBefore = afterComplete == null ? null : now.minus(afterComplete);
        deltaFilesService.markForDelete(createdBefore, completedBefore, flow, name);
    }
}