package org.deltafi.dgs.configuration;

import java.util.ArrayList;
import java.util.List;

public class EnrichActionConfiguration {
    private List<String> requiresDomains = new ArrayList<>();
    private List<String> requiresEnrichment = new ArrayList<>();

    public List<String> getRequiresDomains() {
        return requiresDomains;
    }

    @SuppressWarnings("unused")
    public void setRequiresDomains(List<String> requiresDomains) {
        this.requiresDomains = requiresDomains;
    }

    public List<String> getRequiresEnrichment() {
        return requiresEnrichment;
    }

    @SuppressWarnings("unused")
    public void setRequiresEnrichment(List<String> requiresEnrichment) {
        this.requiresEnrichment = requiresEnrichment;
    }
}
