package org.deltafi.config.server.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StateHolderServiceTest {

    StateHolderService stateHolderService = new StateHolderService();

    @Test
    void testUpdate() {
        String stateId = stateHolderService.getConfigStateIdString();
        Assertions.assertThat(stateHolderService.getConfigStateIdString()).isNotBlank();

        stateHolderService.updateConfigStateId();

        Assertions.assertThat(stateHolderService.getConfigStateIdString()).isNotBlank();

        Assertions.assertThat(stateId).isNotEqualTo(stateHolderService.getConfigStateIdString());
    }


}