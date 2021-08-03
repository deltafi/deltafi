package org.deltafi.dgs.configuration;

import org.deltafi.dgs.api.types.ConfigType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Document("deltafiConfig")
public class DeltafiRuntimeConfiguration {

    public static final String ID_CONSTANT = "deltafi-config";

    @Id
    private String id = ID_CONSTANT; // there should only ever be one instance of the config

    private Map<String, IngressFlowConfiguration> ingressFlows = new HashMap<>();
    private Map<String, EgressFlowConfiguration> egressFlows = new HashMap<>();
    private Map<String, TransformActionConfiguration> transformActions = new HashMap<>();
    private Map<String, LoadActionConfiguration> loadActions = new HashMap<>();
    private Map<String, EnrichActionConfiguration> enrichActions = new HashMap<>();
    private Map<String, FormatActionConfiguration> formatActions = new HashMap<>();
    private Map<String, ValidateActionConfiguration> validateActions = new HashMap<>();
    private Map<String, EgressActionConfiguration> egressActions = new HashMap<>();
    private Map<String, LoadActionGroupConfiguration> loadGroups = new HashMap<>();
    private Map<String, DomainEndpointConfiguration> domainEndpoints = new HashMap<>();

    public String getId() {
        return id;
    }

    public Map<String, IngressFlowConfiguration> getIngressFlows() {
        return ingressFlows;
    }

    public void setIngressFlows(Map<String, IngressFlowConfiguration> ingressFlows) {
        this.ingressFlows = ingressFlows;
    }

    public Map<String, EgressFlowConfiguration> getEgressFlows() {
        return egressFlows;
    }

    public void setEgressFlows(Map<String, EgressFlowConfiguration> egressFlows) {
        this.egressFlows = egressFlows;
    }

    public Map<String, TransformActionConfiguration> getTransformActions() {
        return transformActions;
    }

    public void setTransformActions(Map<String, TransformActionConfiguration> transformActions) {
        this.transformActions = transformActions;
    }

    public Map<String, LoadActionConfiguration> getLoadActions() {
        return loadActions;
    }

    public void setLoadActions(Map<String, LoadActionConfiguration> loadActions) {
        this.loadActions = loadActions;
    }

    public Map<String, EnrichActionConfiguration> getEnrichActions() {
        return enrichActions;
    }

    public void setEnrichActions(Map<String, EnrichActionConfiguration> enrichActions) {
        this.enrichActions = enrichActions;
    }

    public Map<String, FormatActionConfiguration> getFormatActions() {
        return formatActions;
    }

    public void setFormatActions(Map<String, FormatActionConfiguration> formatActions) {
        this.formatActions = formatActions;
    }

    public Map<String, ValidateActionConfiguration> getValidateActions() {
        return validateActions;
    }

    public void setValidateActions(Map<String, ValidateActionConfiguration> validateActions) {
        this.validateActions = validateActions;
    }

    public Map<String, EgressActionConfiguration> getEgressActions() {
        return egressActions;
    }

    public void setEgressActions(Map<String, EgressActionConfiguration> egressActions) {
        this.egressActions = egressActions;
    }

    public Map<String, LoadActionGroupConfiguration> getLoadGroups() {
        return loadGroups;
    }

    public void setLoadGroups(Map<String, LoadActionGroupConfiguration> loadGroups) {
        this.loadGroups = loadGroups;
    }

    public Map<String, DomainEndpointConfiguration> getDomainEndpoints() {
        return domainEndpoints;
    }

    public void setDomainEndpoints(Map<String, DomainEndpointConfiguration> domainEndpoints) {
        this.domainEndpoints = domainEndpoints;
    }

    public List<DeltaFiConfiguration> allConfigs() {
        return Stream.of(ingressFlows, egressFlows, transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions, loadGroups, domainEndpoints)
                .map(Map::values).flatMap(Collection::stream).collect(Collectors.toList());
    }

    public Optional<ActionConfiguration> findByActionName(String actionName) {
        return Stream.of(transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions)
                .map(configs -> findByActionName(configs, actionName)).filter(Objects::nonNull).findFirst();
    }

    private ActionConfiguration findByActionName(Map<String, ? extends ActionConfiguration> configs, String actionName) {
        return configs.get(actionName);
    }

    public Stream<Map<String, ? extends DeltaFiConfiguration>> deltafiMaps() {
        return Stream.of(ingressFlows, egressFlows, loadGroups, domainEndpoints);
    }

    public Stream<Map<String, ? extends DeltaFiConfiguration>> actionMaps() {
        return Stream.of(transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions);
    }

    public Map<String, ? extends DeltaFiConfiguration> getMapByType(ConfigType type) {
        switch (type) {
            case LOAD_ACTION_GROUP:
                return loadGroups;
            case INGRESS_FLOW:
                return ingressFlows;
            case EGRESS_FLOW:
                return egressFlows;
            case DOMAIN_ENDPOINT:
                return domainEndpoints;
            case TRANSFORM_ACTION:
                return transformActions;
            case LOAD_ACTION:
                return loadActions;
            case ENRICH_ACTION:
                return enrichActions;
            case FORMAT_ACTION:
                return formatActions;
            case VALIDATE_ACTION:
                 return validateActions;
            case EGRESS_ACTION:
                return egressActions;
        }
        throw new IllegalArgumentException("Unexpected config type " + type);
    }
}
