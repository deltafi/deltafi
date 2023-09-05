/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class EgressFlowTest {

    @Test
    void testFlowIncluded() {
        EgressFlow config = new EgressFlow();
        config.setIncludeNormalizeFlows(Collections.singletonList("includedFlow"));

        Assertions.assertTrue(config.flowMatches("includedFlow"));
    }

    @Test
    void testFlowNotIncluded() {
        EgressFlow config = new EgressFlow();
        config.setIncludeNormalizeFlows(Collections.singletonList("includedFlow"));

        Assertions.assertFalse(config.flowMatches("notIncludedFlow"));
    }

    @Test
    void testFlowExcluded() {
        EgressFlow config = new EgressFlow();
        config.setExcludeNormalizeFlows(Collections.singletonList("excludedFlow"));

        Assertions.assertFalse(config.flowMatches("excludedFlow"));
    }

    @Test
    void testFlowNotExcluded() {
        EgressFlow config = new EgressFlow();
        config.setIncludeNormalizeFlows(null);
        config.setExcludeNormalizeFlows(Collections.singletonList("excludedFlow"));

        Assertions.assertTrue(config.flowMatches("notExcludedFlow"));
    }

    @Test
    void testEmptyInclude() {
        EgressFlow config = new EgressFlow();
        config.setIncludeNormalizeFlows(List.of());
        config.setExcludeNormalizeFlows(Collections.singletonList("excludedFlow"));

        Assertions.assertFalse(config.flowMatches("notExcludedFlow"));
    }

}