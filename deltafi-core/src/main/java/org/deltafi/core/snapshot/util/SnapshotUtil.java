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
import org.deltafi.core.snapshot.types.FlowSnapshot;
import org.deltafi.core.snapshot.types.NormalizeFlowSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.deltafi.core.snapshot.types.EnrichFlowSnapshot;
import org.deltafi.core.snapshot.types.TransformFlowSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SnapshotUtil {
    private SnapshotUtil() {}

    public static void upgrade(SystemSnapshot systemSnapshot) {
        migrateFlowValues(systemSnapshot);
    }

    /**
     * Copy running flow and test flow settings into the flow specific objects
     * @param systemSnapshot system snapshot that will be modified
     */
    static void migrateFlowValues(SystemSnapshot systemSnapshot) {
        systemSnapshot.setNormalizeFlows(updateFlowSnapshotList(systemSnapshot.getNormalizeFlows(), systemSnapshot.getRunningIngressFlows(), systemSnapshot.getTestIngressFlows(), NormalizeFlowSnapshot::new));
        systemSnapshot.setTransformFlows(updateFlowSnapshotList(systemSnapshot.getTransformFlows(), systemSnapshot.getRunningTransformFlows(), systemSnapshot.getTestTransformFlows(), TransformFlowSnapshot::new));
        systemSnapshot.setEnrichFlows(updateFlowSnapshotList(systemSnapshot.getEnrichFlows(), systemSnapshot.getRunningEnrichFlows(), null, EnrichFlowSnapshot::new));
        systemSnapshot.setEgressFlows(updateFlowSnapshotList(systemSnapshot.getEgressFlows(), systemSnapshot.getRunningEgressFlows(), systemSnapshot.getTestEgressFlows(), EgressFlowSnapshot::new));
    }

    static <T extends FlowSnapshot> List<T> updateFlowSnapshotList(List<T> flowSnapshots, List<String> runningFlows, List<String> testModeFlows, Function<String, T> constructor) {
        flowSnapshots = flowSnapshots != null ? flowSnapshots : List.of();
        runningFlows = runningFlows != null ? runningFlows : List.of();
        testModeFlows = testModeFlows != null ? testModeFlows : List.of();
        if (flowSnapshots.isEmpty() && (!runningFlows.isEmpty() || !testModeFlows.isEmpty())) {
            flowSnapshots.addAll(buildFlowSnapshots(runningFlows, testModeFlows, constructor));
        }
        
        return flowSnapshots;
    }

    static <T extends FlowSnapshot> List<T> buildFlowSnapshots(List<String> runningFlows, List<String> testModeFlows, Function<String, T> constructor) {
        Map<String, T> flowSnapshotMap = new HashMap<>();

        for (String runningFlow : runningFlows) {
            FlowSnapshot flowSnapshot = flowSnapshotMap.computeIfAbsent(runningFlow, constructor);
            flowSnapshot.setRunning(true);
        }

        for (String testModeFlow : testModeFlows) {
            FlowSnapshot flowSnapshot = flowSnapshotMap.computeIfAbsent(testModeFlow, constructor);
            flowSnapshot.setTestMode(true);
        }

        return new ArrayList<>(flowSnapshotMap.values());
    }
}
