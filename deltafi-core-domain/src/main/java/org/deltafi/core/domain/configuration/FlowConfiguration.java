/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.domain.configuration;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.deltafi.core.domain.types.ConfigType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Document("flows")
@Getter
@Setter
@ToString
public class FlowConfiguration {


    @Id
    private String flowPlan;

    private Map<String, IngressFlowConfiguration> ingressFlows = new HashMap<>();
    private Map<String, EgressFlowConfiguration> egressFlows = new HashMap<>();
    private Map<String, TransformActionConfiguration> transformActions = new HashMap<>();
    private Map<String, LoadActionConfiguration> loadActions = new HashMap<>();
    private Map<String, EnrichActionConfiguration> enrichActions = new HashMap<>();
    private Map<String, FormatActionConfiguration> formatActions = new HashMap<>();
    private Map<String, ValidateActionConfiguration> validateActions = new HashMap<>();
    private Map<String, EgressActionConfiguration> egressActions = new HashMap<>();

    public List<DeltaFiConfiguration> allConfigs() {
        return Stream.of(ingressFlows, egressFlows, transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions)
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
        return Stream.of(ingressFlows, egressFlows);
    }

    public Stream<Map<String, ? extends ActionConfiguration>> actionMaps() {
        return Stream.of(transformActions, loadActions, enrichActions, formatActions, validateActions, egressActions);
    }

    public Map<String, ? extends DeltaFiConfiguration> getMapByType(ConfigType type) {
        switch (type) {
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
        }
        throw new IllegalArgumentException("Unexpected config type " + type);
    }
}
