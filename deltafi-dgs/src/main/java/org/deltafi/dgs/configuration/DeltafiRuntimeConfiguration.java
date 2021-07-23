package org.deltafi.dgs.configuration;

import java.util.ArrayList;
import java.util.List;

public class DeltafiRuntimeConfiguration {

    public List<IngressFlowConfiguration> ingressFlows = new ArrayList<>();
    public List<EgressFlowConfiguration> egressFlows = new ArrayList<>();
    public List<TransformActionConfiguration> transformActions = new ArrayList<>();
    public List<LoadActionConfiguration> loadActions = new ArrayList<>();
    public List<EnrichActionConfiguration> enrichActions = new ArrayList<>();
    public List<FormatActionConfiguration> formatActions = new ArrayList<>();
    public List<ValidateActionConfiguration> validateActions = new ArrayList<>();
    public List<EgressActionConfiguration> egressActions = new ArrayList<>();
    public List<LoadActionGroupConfiguration> loadGroups = new ArrayList<>();


    public List<IngressFlowConfiguration> getIngressFlows() {
        return ingressFlows;
    }

    public void setIngressFlows(List<IngressFlowConfiguration> ingressFlows) {
        this.ingressFlows = ingressFlows;
    }

    public List<EgressFlowConfiguration> getEgressFlows() {
        return egressFlows;
    }

    public void setEgressFlows(List<EgressFlowConfiguration> egressFlows) {
        this.egressFlows = egressFlows;
    }

    public List<TransformActionConfiguration> getTransformActions() {
        return transformActions;
    }

    public void setTransformActions(List<TransformActionConfiguration> transformActions) {
        this.transformActions = transformActions;
    }

    public List<LoadActionConfiguration> getLoadActions() {
        return loadActions;
    }

    public void setLoadActions(List<LoadActionConfiguration> loadActions) {
        this.loadActions = loadActions;
    }

    public List<EnrichActionConfiguration> getEnrichActions() {
        return enrichActions;
    }

    public void setEnrichActions(List<EnrichActionConfiguration> enrichActions) {
        this.enrichActions = enrichActions;
    }

    public List<FormatActionConfiguration> getFormatActions() {
        return formatActions;
    }

    public void setFormatActions(List<FormatActionConfiguration> formatActions) {
        this.formatActions = formatActions;
    }

    public List<ValidateActionConfiguration> getValidateActions() {
        return validateActions;
    }

    public void setValidateActions(List<ValidateActionConfiguration> validateActions) {
        this.validateActions = validateActions;
    }

    public List<EgressActionConfiguration> getEgressActions() {
        return egressActions;
    }

    public void setEgressActions(List<EgressActionConfiguration> egressActions) {
        this.egressActions = egressActions;
    }

    public List<LoadActionGroupConfiguration> getLoadGroups() {
        return loadGroups;
    }

    public void setLoadGroups(List<LoadActionGroupConfiguration> loadGroups) {
        this.loadGroups = loadGroups;
    }
}
