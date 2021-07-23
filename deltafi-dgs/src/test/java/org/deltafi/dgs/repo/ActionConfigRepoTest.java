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
class ActionConfigRepoTest {

    @Autowired
    private ActionConfigRepo actionConfigRepo;

    @BeforeEach
    public void setup() {
        actionConfigRepo.deleteAll();
    }

    @Test
    void upsertConfiguration() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("name");
        config.setConsumes("xml");
        config.setProduces("json");
        config.setApiVersion("1");
        TransformActionConfiguration saved = actionConfigRepo.upsertConfiguration(config, TransformActionConfiguration.class);

        assertEquals("name", saved.getName());
        assertEquals("xml", saved.getConsumes());
        assertEquals("json", saved.getProduces());
        assertEquals("1", saved.getApiVersion());
        assertNotNull(saved.getCreated());
        assertNotNull(saved.getModified());

        config.setProduces("csv");
        TransformActionConfiguration updated = actionConfigRepo.upsertConfiguration(config, TransformActionConfiguration.class);

        assertTrue(updated.getModified().compareTo(saved.getModified()) > 0);
        assertEquals(saved.getCreated(), updated.getCreated());
        assertEquals("csv", updated.getProduces());
    }

    @Test
    void exists() {
        TransformActionConfiguration config = new TransformActionConfiguration();
        config.setName("name");

        assertFalse(actionConfigRepo.exists(config));

        actionConfigRepo.save(config);

        assertTrue(actionConfigRepo.exists(config));
    }

    @Test
    void findLoadAction() {
        LoadActionConfiguration config = new LoadActionConfiguration();
        config.setName("name");
        actionConfigRepo.save(config);

        assertEquals(config, actionConfigRepo.findLoadAction("name"));
    }

    @Test
    void findFormatAction() {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName("name");
        actionConfigRepo.save(config);

        assertEquals(config, actionConfigRepo.findFormatAction("name"));
    }

    @Test
    void findEnrichAction() {
        EnrichActionConfiguration config = new EnrichActionConfiguration();
        config.setName("name");
        actionConfigRepo.save(config);

        assertEquals(config, actionConfigRepo.findEnrichAction("name"));
    }


    @Test
    void deleteAllWithCount() {
        EnrichActionConfiguration enrichActionConfig = new EnrichActionConfiguration();
        enrichActionConfig.setName("enrichAction");
        actionConfigRepo.save(enrichActionConfig);

        FormatActionConfiguration formatActionConfig = new FormatActionConfiguration();
        formatActionConfig.setName("formatAction");
        actionConfigRepo.save(formatActionConfig);

        LoadActionConfiguration loadActionConfig = new LoadActionConfiguration();
        loadActionConfig.setName("loadAction");
        actionConfigRepo.save(loadActionConfig);

        assertEquals(3, actionConfigRepo.deleteAllWithCount());
    }
}