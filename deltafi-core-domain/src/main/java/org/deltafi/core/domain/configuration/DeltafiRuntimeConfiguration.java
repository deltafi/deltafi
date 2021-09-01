package org.deltafi.core.domain.configuration;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.core.domain.api.types.ConfigType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Document("deltafiConfig")
@Getter
@Setter
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
    private Map<String, DeleteActionConfiguration> deleteActions = new HashMap<>();
    private Map<String, LoadActionGroupConfiguration> loadGroups = new HashMap<>();

    public List<DeltaFiConfiguration> allConfigs() {
        return Stream.of(ingressFlows, egressFlows, transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions, deleteActions, loadGroups)
                .map(Map::values).flatMap(Collection::stream).collect(Collectors.toList());
    }

    public Optional<ActionConfiguration> findByActionName(String actionName) {
        return Stream.of(transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions, deleteActions)
                .map(configs -> findByActionName(configs, actionName)).filter(Objects::nonNull).findFirst();
    }

    private ActionConfiguration findByActionName(Map<String, ? extends ActionConfiguration> configs, String actionName) {
        return configs.get(actionName);
    }

    public Stream<Map<String, ? extends DeltaFiConfiguration>> deltafiMaps() {
        return Stream.of(ingressFlows, egressFlows, loadGroups);
    }

    public Stream<Map<String, ? extends DeltaFiConfiguration>> actionMaps() {
        return Stream.of(transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions, deleteActions);
    }

    public Map<String, ? extends DeltaFiConfiguration> getMapByType(ConfigType type) {
        switch (type) {
            case LOAD_ACTION_GROUP:
                return loadGroups;
            case INGRESS_FLOW:
                return ingressFlows;
            case EGRESS_FLOW:
                return egressFlows;
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
            case DELETE_ACTION:
                return deleteActions;
        }
        throw new IllegalArgumentException("Unexpected config type " + type);
    }
}