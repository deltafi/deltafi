package org.deltafi.dgs.configuration;

import java.util.HashMap;
import java.util.Map;

public class IngressConfiguration {
    private Map<String, IngressFlowConfiguration> ingressFlows = new HashMap<>();

    public Map<String, IngressFlowConfiguration> getIngressFlows() {
        return ingressFlows;
    }

    @SuppressWarnings("unused")
    public void setIngressFlows(Map<String, IngressFlowConfiguration> ingressFlows) {
        this.ingressFlows = ingressFlows;
    }
}
