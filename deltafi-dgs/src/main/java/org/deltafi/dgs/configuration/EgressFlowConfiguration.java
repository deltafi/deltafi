package org.deltafi.dgs.configuration;

import java.util.ArrayList;
import java.util.List;

public class EgressFlowConfiguration {
    private String formatAction;
    private List<String> validateActions = new ArrayList<>();
    private List<String> includeIngressFlows = new ArrayList<>();
    private List<String> excludeIngressFlows = new ArrayList<>();

    public String getFormatAction() {
        return formatAction;
    }

    @SuppressWarnings("unused")
    public void setFormatAction(String formatAction) {
        this.formatAction = formatAction;
    }

    @SuppressWarnings("unused")
    public List<String> getValidateActions() {
        return validateActions;
    }

    @SuppressWarnings("unused")
    public void setValidateActions(List<String> validateActions) {
        this.validateActions = validateActions;
    }

    public List<String> getIncludeIngressFlows() {
        return includeIngressFlows;
    }

    public void setIncludeIngressFlows(List<String> includeIngressFlows) {
        this.includeIngressFlows = includeIngressFlows;
    }

    public List<String> getExcludeIngressFlows() {
        return excludeIngressFlows;
    }

    public void setExcludeIngressFlows(List<String> excludeIngressFlows) {
        this.excludeIngressFlows = excludeIngressFlows;
    }
}
