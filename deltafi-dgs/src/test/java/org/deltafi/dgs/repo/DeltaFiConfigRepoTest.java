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
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("name");
        config.setConsumes("xml");
        config.setProduces("json");
        config.setApiVersion("1");
        TransformActionConfiguration saved = deltaFiConfigRepo.upsertConfiguration(config, TransformActionConfiguration.class);

        assertEquals("name", saved.getName());
        assertEquals("xml", saved.getConsumes());
        assertEquals("json", saved.getProduces());
        assertEquals("1", saved.getApiVersion());
        assertNotNull(saved.getCreated());
        assertNotNull(saved.getModified());

        config.setProduces("csv");
        TransformActionConfiguration updated = deltaFiConfigRepo.upsertConfiguration(config, TransformActionConfiguration.class);

        assertTrue(updated.getModified().compareTo(saved.getModified()) > 0);
        assertEquals(saved.getCreated(), updated.getCreated());
        assertEquals("csv", updated.getProduces());
    }

    @Test
    void exists() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("name");

        assertFalse(deltaFiConfigRepo.exists(config));

        deltaFiConfigRepo.save(config);

        assertTrue(deltaFiConfigRepo.exists(config));
    }

    @Test
    void findLoadAction() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findLoadAction("name"));
    }

    @Test
    void findFormatAction() {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findFormatAction("name"));
    }

    @Test
    void findEnrichAction() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("name");
        deltaFiConfigRepo.save(config);

        assertEquals(config, deltaFiConfigRepo.findEnrichAction("name"));
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

        assertEquals(config, deltaFiConfigRepo.findEgressFlowForAction("NameEgressAction"));
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
    void deleteActionConfigs() {
        EgressFlowConfiguration egressFlow = new EgressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(egressFlow);
        IngressFlowConfiguration ingressFlow = new IngressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(ingressFlow);

        LoadActionGroupConfiguration actionConfig = new LoadActionGroupConfiguration();
        actionConfig.setName("name");
        deltaFiConfigRepo.save(actionConfig);
        assertEquals(3, deltaFiConfigRepo.count());

        deltaFiConfigRepo.deleteActionConfigs();

        assertEquals(2, deltaFiConfigRepo.count());

        assertNull(deltaFiConfigRepo.findLoadActionGroup("name"));
    }

    @Test
    void deleteFlowConfigs() {
        EgressFlowConfiguration egressFlow = new EgressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(egressFlow);
        IngressFlowConfiguration ingressFlow = new IngressFlowConfiguration();
        egressFlow.setName("name");
        deltaFiConfigRepo.save(ingressFlow);

        LoadActionGroupConfiguration actionConfig = new LoadActionGroupConfiguration();
        actionConfig.setName("name");
        deltaFiConfigRepo.save(actionConfig);
        assertEquals(3, deltaFiConfigRepo.count());

        deltaFiConfigRepo.deleteFlowConfigs();

        assertEquals(1, deltaFiConfigRepo.count());
        assertEquals(actionConfig, deltaFiConfigRepo.findAll().get(0));
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