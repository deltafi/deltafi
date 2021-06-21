package org.deltafi.dgs.configuration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EgressConfiguration {
    private Map<String, EgressFlowConfiguration> egressFlows = new HashMap<>();

    public Map<String, EgressFlowConfiguration> getEgressFlows() {
        return egressFlows;
    }

    @SuppressWarnings("unused")
    public void setEgressFlows(Map<String, EgressFlowConfiguration> egressFlows) {
        this.egressFlows = egressFlows;
    }

    public EgressFlowConfiguration forEgressAction(String egressAction) {
        return egressFlows.get(egressFlows.keySet().stream()
                .filter(k -> egressActionName(k).equals(egressAction))
                .findFirst().orElse(null));
    }

    static public String egressActionName(String flowName) {
        return flowName.substring(0, 1).toUpperCase(Locale.ROOT) +
                (flowName.length() > 1 ? flowName.substring(1) : "") +
                "EgressAction";
    }
}
