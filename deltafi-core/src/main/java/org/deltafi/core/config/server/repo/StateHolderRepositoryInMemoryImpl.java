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
package org.deltafi.core.config.server.repo;

import java.util.UUID;

public class StateHolderRepositoryInMemoryImpl implements StateHolderRepository {

    private UUID stateId;

    @Override
    public UUID getOrInit() {
        if (null == stateId) {
            stateId = UUID.randomUUID();
        }

        return stateId;
    }

    @Override
    public UUID getCurrentState() {
        return stateId;
    }

    @Override
    public void replaceStateHolderUUID(UUID uuid) {
        stateId = uuid;
    }
}
