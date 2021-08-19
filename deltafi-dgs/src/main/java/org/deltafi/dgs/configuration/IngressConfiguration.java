package org.deltafi.dgs.configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class IngressConfiguration {
    private Map<String, IngressFlowConfiguration> ingressFlows = new HashMap<>();
}
