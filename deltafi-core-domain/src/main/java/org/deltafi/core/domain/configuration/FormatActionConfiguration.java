package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class FormatActionConfiguration extends org.deltafi.core.domain.generated.types.FormatActionConfiguration implements ActionConfiguration {

    public FormatActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
    }

    @Override
    public List<String> validate() {
        return ActionConfiguration.missingRequiredList(this.getRequiresDomains()) ?
                List.of("Required property requiresDomain is not set") : Collections.emptyList();
    }

}