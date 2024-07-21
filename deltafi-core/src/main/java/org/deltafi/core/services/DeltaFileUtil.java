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
package org.deltafi.core.services;

import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.core.types.*;
import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.core.types.JoinEntry;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeltaFileUtil {

    private DeltaFileUtil() {}

    public static WrappedActionInput createAggregateInput(ActionConfiguration joinAction, DeltaFileFlow currentFlow, JoinEntry joinEntry, List<UUID> joinedDids, ActionState actionState, String systemName, String returnAddress) {
        OffsetDateTime now = OffsetDateTime.now();

        DeltaFileFlow aggregateFlow = aggregateDeltaFileFlow(currentFlow, now, joinEntry.getMaxFlowDepth());
        Action aggregateAction = aggregateFlow.addAction(joinEntry.getJoinDefinition().getAction(), joinEntry.getJoinDefinition().getActionType(), actionState, now);

        DeltaFile aggregate = DeltaFile.builder()
                .version(0)
                .schemaVersion(DeltaFile.CURRENT_SCHEMA_VERSION)
                .did(joinEntry.getId())
                .parentDids(joinedDids)
                .stage(DeltaFileStage.IN_FLIGHT)
                .joinId(joinEntry.getId())
                .childDids(List.of())
                .dataSource("multiple")
                .created(now)
                .modified(now)
                .flows(List.of(aggregateFlow)).build();

        aggregate.setName("multiple");

        return aggregate.buildActionInput(joinAction, aggregateFlow, joinedDids, aggregateAction, systemName, returnAddress, null);
    }

    private static DeltaFileFlow aggregateDeltaFileFlow(DeltaFileFlow currentFlow, OffsetDateTime now, int flowDepth) {
        DeltaFileFlow aggregateFlow = new DeltaFileFlow();
        aggregateFlow.setName(currentFlow.getName());
        aggregateFlow.setNumber(0);
        aggregateFlow.setDepth(flowDepth);
        aggregateFlow.setState(DeltaFileFlowState.IN_FLIGHT);
        aggregateFlow.setType(currentFlow.getType());
        aggregateFlow.setCreated(now);
        aggregateFlow.setModified(now);
        aggregateFlow.setFlowPlan(currentFlow.getFlowPlan());
        aggregateFlow.setTestMode(currentFlow.isTestMode());
        String testModeReason = currentFlow.isTestMode() ? currentFlow.getName() : currentFlow.getTestModeReason();
        aggregateFlow.setTestModeReason(testModeReason);
        aggregateFlow.setPendingActions(new ArrayList<>(currentFlow.getPendingActions()));
        currentFlow.setPendingActions(new ArrayList<>());
        return aggregateFlow;
    }
}
