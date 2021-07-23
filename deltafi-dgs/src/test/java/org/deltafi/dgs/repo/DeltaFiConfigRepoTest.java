package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltaFiConfigRepoTest {

    @Autowired
    private DeltaFiConfigRepo deltaFiConfigRepo;

    @BeforeEach
    public void setup() {
        deltaFiConfigRepo.deleteAll();
    }

    @Test
    void upsertConfiguration() {
        IngressFlowConfiguration config = new IngressFlowConfiguration();
        config.setName("name");
        config.setType("xml");
        config.setApiVersion("1");
        IngressFlowConfiguration saved = deltaFiConfigRepo.upsertConfiguration(config, IngressFlowConfiguration.class);

        assertEquals("name", saved.getName());
        assertEquals("xml", saved.getType());
        assertEquals("1", saved.getApiVersion());
        assertNotNull(saved.getCreated());
        assertNotNull(saved.getModified());

        config.setType("csv");
        IngressFlowConfiguration updated = deltaFiConfigRepo.upsertConfiguration(config, IngressFlowConfiguration.class);

        assertTrue(updated.getModified().compareTo(saved.getModified()) > 0);
        assertEquals(saved.getCreated(), updated.getCreated());
        assertEquals("csv", updated.getType());
    }

    @Test
    void exists() {
        LoadActionGroupConfiguration config = new LoadActionGroupConfiguration();
        config.setName("name");

        assertFalse(deltaFiConfigRepo.exists(config));

        deltaFiConfigRepo.save(config);

        assertTrue(deltaFiConfigRepo.exists(config));
    }

    @Test
    void findLoadActionGroup() {
        LoadActionGroupConfiguration config = new LoadActionGroupConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findLoadActionGroup("name"));
    }

    @Test
    void findIngressFlowConfig() {
        IngressFlowConfiguration config = new IngressFlowConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findIngressFlowConfig("name"));
    }

    @Test
    void findEgressFlowConfig() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findEgressFlowConfig("name"));
    }

    @Test
    void findEgressFlowForActionConfig() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setName("name");
        config.setEgressAction("NameEgressAction");

        EgressFlowConfiguration config2 = new EgressFlowConfiguration();
        config2.setName("name2");
        config2.setEgressAction("Name2EgressAction");

        deltaFiConfigRepo.save(config);
        deltaFiConfigRepo.save(config2);

        assertEquals(config, deltaFiConfigRepo.findEgressFlowByEgressActionName("NameEgressAction"));
    }

    @Test
    void findAllEgressFlows() {
        EgressFlowConfiguration config = new EgressFlowConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findAllEgressFlows().get(0));
    }

    @Test
    void findAllDomainEndpoints() {
        DomainEndpointConfiguration config = new DomainEndpointConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findAllDomainEndpoints().get(0));
    }

    @Test
    void deleteAllWithCount() {
        EgressFlowConfiguration egressFlow = new EgressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(egressFlow);
        IngressFlowConfiguration ingressFlow = new IngressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(ingressFlow);

        LoadActionGroupConfiguration actionConfig = new LoadActionGroupConfiguration();
        actionConfig.setName("name");
        deltaFiConfigRepo.save(actionConfig);

        assertEquals(3, deltaFiConfigRepo.deleteAllWithCount());
    }
}