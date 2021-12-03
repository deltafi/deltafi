package org.deltafi.config.server.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StateHolderService {

    private UUID configStateId;

    public StateHolderService() {
        updateConfigStateId();
    }

    public String getConfigStateIdString() {
        return configStateId.toString();
    }

    public void updateConfigStateId() {
        configStateId = UUID.randomUUID();
    }
}
