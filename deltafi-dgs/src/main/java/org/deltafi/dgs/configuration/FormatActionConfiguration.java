package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class FormatActionConfiguration extends org.deltafi.dgs.generated.types.FormatActionConfiguration implements ActionConfiguration {

    public FormatActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
    }

}
