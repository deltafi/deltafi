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

import org.assertj.core.api.Assertions;
import org.deltafi.core.configuration.server.repo.StateHolderRepositoryInMemoryImpl;
import org.junit.jupiter.api.Test;

class StateHolderServiceTest {
    StateHolderService stateHolderService = new StateHolderService(new StateHolderRepositoryInMemoryImpl());

    @Test
    void testUpdate() {
        String stateId = stateHolderService.getConfigStateIdString();
        Assertions.assertThat(stateHolderService.getConfigStateIdString()).isNotBlank();

        stateHolderService.updateConfigStateId();

        Assertions.assertThat(stateHolderService.getConfigStateIdString()).isNotBlank();

        Assertions.assertThat(stateId).isNotEqualTo(stateHolderService.getConfigStateIdString());
    }
}