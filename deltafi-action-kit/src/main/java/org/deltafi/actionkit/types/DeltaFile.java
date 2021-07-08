package org.deltafi.actionkit.types;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DeltaFile extends org.deltafi.dgs.generated.types.DeltaFile {

    Map<String, JsonNode> domainDetails;
    Map<String, JsonNode> enrichmentDetails;

    public Map<String, JsonNode> getDomainDetails() {
        return domainDetails;
    }

    public void setDomainDetails(Map<String, JsonNode> domainDetails) {
        this.domainDetails = domainDetails;
    }

    public Map<String, JsonNode> getEnrichmentDetails() {
        return enrichmentDetails;
    }

    public void setEnrichmentDetails(Map<String, JsonNode> enrichmentDetails) { this.enrichmentDetails = enrichmentDetails; }

    public void addDomainDetails(String key, JsonNode rawDomain) {
        if (Objects.isNull(domainDetails)) {
            this.domainDetails = new HashMap<>();
        }
        this.domainDetails.put(key, rawDomain);
    }

    public void addEnrichmentDetails(String key, JsonNode rawEnrichment) {
        if (Objects.isNull(enrichmentDetails)) {
            this.enrichmentDetails = new HashMap<>();
        }
        this.enrichmentDetails.put(key, rawEnrichment);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DeltaFile) {
            return super.equals(o) &&
                    Objects.equals(domainDetails, ((DeltaFile) o).domainDetails) &&
                    Objects.equals(enrichmentDetails, ((DeltaFile) o).enrichmentDetails);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), domainDetails, enrichmentDetails);
    }
}