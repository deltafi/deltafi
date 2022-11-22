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
package org.deltafi.core.configuration.server.service;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.configuration.server.repo.StateHolderRepository;

import java.util.UUID;

@Slf4j
public class StateHolderService {

    private final StateHolderRepository stateHolderRepository;
    private UUID configStateId;

    public StateHolderService(StateHolderRepository stateHolderRepository) {
        this.stateHolderRepository = stateHolderRepository;
        configStateId = stateHolderRepository.getOrInit();
    }

    public String getConfigStateIdString() {
        return configStateId.toString();
    }

    public void updateConfigStateId() {
        configStateId = UUID.randomUUID();
        stateHolderRepository.replaceStateHolderUUID(configStateId);
    }

    /**
     * Check the cached ConfigStateId against the latest persisted ConfigStateId.
     * If the ConfigStateId has changed, update the cached id and return true
     * @return true if the ConfigStateId has changed and the properties should be synced
     */
    public boolean needsSynced() {
        UUID latestState = stateHolderRepository.getCurrentState();
        boolean needsSynced = !configStateId.equals(latestState);
        log.debug("App has stateId: {} vs stored stateId of {}: {}", configStateId, latestState, needsSynced);
        configStateId = latestState;
        return needsSynced;
    }
}
