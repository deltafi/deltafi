/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
                .did(joinEntry.getId())
                .name("joined:%s".formatted(currentFlow.getName()))
                .parentDids(joinedDids)
                .stage(DeltaFileStage.IN_FLIGHT)
                .joinId(joinEntry.getId())
                .childDids(List.of())
                .dataSource("joined:%s".formatted(currentFlow.getName()))
                .created(now)
                .modified(now)
                .flows(List.of(aggregateFlow)).build();
        aggregateFlow.setDeltaFile(aggregate);

        return aggregate.buildActionInput(joinAction, aggregateFlow, joinedDids, aggregateAction, systemName, returnAddress, null);
    }

    private static DeltaFileFlow aggregateDeltaFileFlow(DeltaFileFlow currentFlow, OffsetDateTime now, int flowDepth) {
        return DeltaFileFlow.builder()
                .name(currentFlow.getName())
                .number(0)
                .depth(flowDepth)
                .state(DeltaFileFlowState.IN_FLIGHT)
                .type(currentFlow.getType())
                .created(now)
                .modified(now)
                .flowPlan(currentFlow.getFlowPlan())
                .testMode(currentFlow.isTestMode())
                .testModeReason(currentFlow.isTestMode() ? currentFlow.getName() : currentFlow.getTestModeReason())
                .pendingActions(new ArrayList<>(currentFlow.getPendingActions()))
                .build();
    }
}
