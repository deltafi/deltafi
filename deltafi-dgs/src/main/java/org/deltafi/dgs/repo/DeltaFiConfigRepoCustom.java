package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.*;

import java.util.List;

public interface DeltaFiConfigRepoCustom {

    <C extends DeltaFiConfiguration> C upsertConfiguration(C config, Class<C> clazz);

    boolean exists(DeltaFiConfiguration config);

    LoadActionConfiguration findLoadAction(String name);

    FormatActionConfiguration findFormatAction(String name);

    EnrichActionConfiguration findEnrichAction(String name);

    LoadActionGroupConfiguration findLoadActionGroup(String name);

    IngressFlowConfiguration findIngressFlowConfig(String name);

    EgressFlowConfiguration findEgressFlowConfig(String name);

    EgressFlowConfiguration findEgressFlowForAction(String actionName);

    List<EgressFlowConfiguration> findAllEgressFlows();

    List<DomainEndpointConfiguration> findAllDomainEndpoints();

    void deleteActionConfigs();

    void deleteFlowConfigs();

    long deleteAllWithCount();

}
