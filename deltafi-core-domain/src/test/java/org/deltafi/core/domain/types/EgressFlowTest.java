package org.deltafi.core.domain.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class EgressFlowTest {

    @Test
    void testFlowIncluded() {
        EgressFlow config = new EgressFlow();
        config.setIncludeIngressFlows(Collections.singletonList("includedFlow"));

        Assertions.assertTrue(config.flowMatches("includedFlow"));
    }

    @Test
    void testFlowNotIncluded() {
        EgressFlow config = new EgressFlow();
        config.setIncludeIngressFlows(Collections.singletonList("includedFlow"));

        Assertions.assertFalse(config.flowMatches("notIncludedFlow"));
    }

    @Test
    void testFlowExcluded() {
        EgressFlow config = new EgressFlow();
        config.setExcludeIngressFlows(Collections.singletonList("excludedFlow"));

        Assertions.assertFalse(config.flowMatches("excludedFlow"));
    }

    @Test
    void testFlowNotExcluded() {
        EgressFlow config = new EgressFlow();
        config.setExcludeIngressFlows(Collections.singletonList("excludedFlow"));

        Assertions.assertTrue(config.flowMatches("notExcludedFlow"));
    }

}