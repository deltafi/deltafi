package org.deltafi.types;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DeltaFile extends org.deltafi.dgs.generated.types.DeltaFile {

    Map<String, JsonNode> domainDetails;

    public Map<String, JsonNode> getDomainDetails() {
        return domainDetails;
    }

    public void setDomainDetails(Map<String, JsonNode> domainDetails) {
        this.domainDetails = domainDetails;
    }

    public void addDomainDetails(String key, JsonNode rawDomain) {
        if (Objects.isNull(domainDetails)) {
            this.domainDetails = new HashMap<>();
        }
        this.domainDetails.put(key, rawDomain);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DeltaFile) {
            return super.equals(o) && Objects.equals(domainDetails, ((DeltaFile) o).domainDetails);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), domainDetails);
    }
}