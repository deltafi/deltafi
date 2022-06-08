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
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.DiskSpaceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeleteRunner {
    @Getter
    private final List<DeletePolicy> deletePolicies = new ArrayList<>();

    public DeleteRunner(DeltaFilesService deltaFilesService, DiskSpaceService diskSpaceService, DeltaFiProperties deltaFiProperties) {
        deltaFiProperties.getDelete().getPolicies().forEach((name, config) -> {
            switch(config.getType()) {
                case DiskSpaceDelete.TYPE:
                      deletePolicies.add(new DiskSpaceDelete(deltaFilesService, diskSpaceService, name, config.getParameters()));
                    break;
                case TimedDelete.TYPE:
                    deletePolicies.add(new TimedDelete(deltaFilesService, name, config.getParameters()));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown delete policy type " + config.getType() + " configured in policy " + name);
            }
        });
    }

    public void runDeletes() {
        deletePolicies.forEach(DeletePolicy::run);
    }
}