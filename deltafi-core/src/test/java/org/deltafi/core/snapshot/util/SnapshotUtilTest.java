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
package org.deltafi.core.snapshot.util;

import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.deltafi.core.snapshot.types.TransformFlowSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotUtilTest {

    @Test
    void upgrade() {
        EgressFlowSnapshot expectedEgress = new EgressFlowSnapshot("egressFlow");
        expectedEgress.setRunning(true);

        TransformFlowSnapshot expectedTransform = new TransformFlowSnapshot("transformFlow");
        expectedTransform.setTestMode(true);

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setRunningEgressFlows(List.of("egressFlow"));
        systemSnapshot.setRunningIngressFlows(List.of("normalizeFlow"));

        systemSnapshot.setTestTransformFlows(List.of("transformFlow"));
        systemSnapshot.setTestIngressFlows(List.of("normalizeFlow"));

        SnapshotUtil.upgrade(systemSnapshot);
        assertThat(systemSnapshot.getEgressFlows()).hasSize(1).contains(expectedEgress);
        assertThat(systemSnapshot.getTransformFlows()).hasSize(1).contains(expectedTransform);
    }
}