package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.*;

import java.util.List;

public interface DeltaFiConfigRepoCustom {

    <C extends DeltaFiConfiguration> C upsertConfiguration(C config, Class<C> clazz);

    boolean exists(DeltaFiConfiguration config);

    LoadActionGroupConfiguration findLoadActionGroup(String name);

    IngressFlowConfiguration findIngressFlowConfig(String name);

    EgressFlowConfiguration findEgressFlowConfig(String name);

    EgressFlowConfiguration findEgressFlowByEgressActionName(String actionName);

    List<String> findEgressActionsWithFormatAction(String formatAction);

    List<EgressFlowConfiguration> findAllEgressFlows();

    List<DomainEndpointConfiguration> findAllDomainEndpoints();

    long deleteAllWithCount();

}
