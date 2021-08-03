package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class EnrichActionConfiguration extends org.deltafi.dgs.generated.types.EnrichActionConfiguration implements ActionConfiguration {

    public EnrichActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
    }

}
