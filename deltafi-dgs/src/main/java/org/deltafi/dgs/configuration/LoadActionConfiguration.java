package org.deltafi.dgs.configuration;

import java.util.HashMap;
import java.util.Map;

public class LoadActionConfiguration {
    private String consumes;
    private Map<String, String> requiresMetadata = new HashMap<>();

    @SuppressWarnings("unused")
    public String getConsumes() {
        return consumes;
    }

    @SuppressWarnings("unused")
    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public Map<String, String> getRequiresMetadata() {
        return requiresMetadata;
    }

    @SuppressWarnings("unused")
    public void setRequiresMetadata(Map<String, String> requiresMetadata) {
        this.requiresMetadata = requiresMetadata;
    }
}
